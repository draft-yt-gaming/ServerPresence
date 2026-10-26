package fr.draftfr.serverpresence.discord;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import fr.draftfr.serverpresence.ServerPresenceClient;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Keeps the Discord presence in sync from a background thread: the game thread only hands over the wanted
 * activity, this thread connects (and reconnects when Discord restarts), throttles and sends it.
 */
public final class DiscordRpc {
	/** Discord accepts 5 activity updates every 20 seconds. */
	private static final long MIN_INTERVAL_MS = 4_000;
	/** Same activity sent again from time to time, to notice when Discord was restarted. */
	private static final long REFRESH_MS = 30_000;
	private static final long RETRY_MS = 15_000;

	public enum Status {
		IDLE,
		CONNECTING,
		CONNECTED,
		DISCORD_NOT_FOUND,
		INVALID_CLIENT_ID,
		ERROR
	}

	private final Object lock = new Object();
	private final String clientId;
	private @Nullable JsonObject wantedActivity;
	private boolean changed;
	private volatile Status status = Status.IDLE;
	private volatile String discordUser = "";
	private volatile String lastError = "";
	private volatile @Nullable DiscordIpc ipc;
	private volatile @Nullable String sentActivity;
	private long lastSend;
	private long nextConnectAttempt;
	private boolean running = true;

	/** {@code clientId}: the Discord application of the mod, written in the code only. */
	public DiscordRpc(String clientId) {
		this.clientId = clientId;
		Thread thread = new Thread(this::run, "ServerPresence Discord RPC");
		thread.setDaemon(true);
		thread.start();
	}

	/** Called from the game thread; {@code activity == null} clears the presence. */
	public void update(@Nullable JsonObject activity) {
		synchronized (this.lock) {
			if (Objects.equals(activity, this.wantedActivity)) {
				return;
			}
			this.wantedActivity = activity == null ? null : activity.deepCopy();
			this.changed = true;
			this.lock.notifyAll();
		}
	}

	/** Retries right now (after the settings were changed). */
	public void retry() {
		synchronized (this.lock) {
			this.nextConnectAttempt = 0;
			this.changed = true;
			this.lock.notifyAll();
		}
	}

	public void shutdown() {
		synchronized (this.lock) {
			this.running = false;
			this.lock.notifyAll();
		}
		DiscordIpc current = this.ipc;
		if (current != null) {
			current.close();
		}
	}

	public Status status() {
		return this.status;
	}

	public String discordUser() {
		return this.discordUser;
	}

	/** JSON of the activity Discord accepted last ({@code null} when cleared). */
	public @Nullable String sentActivity() {
		return this.sentActivity;
	}

	public String lastError() {
		return this.lastError;
	}

	private void run() {
		while (true) {
			JsonObject activity;
			synchronized (this.lock) {
				while (this.running && !this.dueLocked()) {
					try {
						this.lock.wait(Math.max(1, this.waitTimeLocked()));
					} catch (InterruptedException e) {
						return;
					}
				}
				if (!this.running) {
					return;
				}
				activity = this.wantedActivity;
				this.changed = false;
			}
			try {
				this.sync(activity);
			} catch (RuntimeException e) {
				ServerPresenceClient.LOGGER.error("Discord presence update failed", e);
				this.disconnect(Status.ERROR);
			}
		}
	}

	private boolean dueLocked() {
		long now = System.currentTimeMillis();
		if (this.changed) {
			return now - this.lastSend >= MIN_INTERVAL_MS && (this.ipc != null || now >= this.nextConnectAttempt || this.wantedActivity == null);
		}
		if (this.ipc != null && this.sentActivity != null) {
			return now - this.lastSend >= REFRESH_MS;
		}
		return this.ipc == null && this.wantedActivity != null && now >= this.nextConnectAttempt;
	}

	private long waitTimeLocked() {
		long now = System.currentTimeMillis();
		long wait = REFRESH_MS;
		if (this.changed) {
			wait = Math.min(wait, this.lastSend + MIN_INTERVAL_MS - now);
		}
		if (this.ipc == null && this.wantedActivity != null) {
			wait = Math.min(wait, this.nextConnectAttempt - now);
		}
		return wait;
	}

	private void sync(@Nullable JsonObject activity) {
		if (this.ipc == null) {
			if (activity == null) {
				// nothing to show: no need to talk to Discord, closing the connection already cleared it
				return;
			}
			if (!this.connect()) {
				return;
			}
		}
		this.send(activity);
	}

	private boolean connect() {
		this.status = Status.CONNECTING;
		try {
			DiscordIpc connection = DiscordIpc.connect(this.clientId);
			this.ipc = connection;
			this.sentActivity = null;
			this.discordUser = readUser(connection.ready());
			this.status = Status.CONNECTED;
			this.lastError = "";
			ServerPresenceClient.LOGGER.info("Connected to Discord as {}", this.discordUser);
			return true;
		} catch (DiscordIpc.DiscordException e) {
			this.fail(e.code == 4000 ? Status.INVALID_CLIENT_ID : Status.ERROR, e.getMessage());
		} catch (IOException e) {
			this.fail(Status.DISCORD_NOT_FOUND, e.getMessage());
		}
		return false;
	}

	private void send(@Nullable JsonObject activity) {
		DiscordIpc connection = this.ipc;
		if (connection == null) {
			return;
		}
		JsonObject args = new JsonObject();
		args.addProperty("pid", ProcessHandle.current().pid());
		args.add("activity", activity == null ? JsonNull.INSTANCE : activity);
		JsonObject payload = new JsonObject();
		payload.addProperty("cmd", "SET_ACTIVITY");
		payload.add("args", args);
		payload.addProperty("nonce", UUID.randomUUID().toString());
		this.lastSend = System.currentTimeMillis();
		try {
			JsonObject answer = connection.command(payload);
			this.sentActivity = activity == null ? null : activity.toString();
			if (answer.has("evt") && "ERROR".equals(stringOf(answer.get("evt")))) {
				String message = answer.has("data") && answer.get("data").isJsonObject() && answer.getAsJsonObject("data").has("message")
					? stringOf(answer.getAsJsonObject("data").get("message"))
					: answer.toString();
				if (!message.equals(this.lastError)) {
					ServerPresenceClient.LOGGER.warn("Discord refused the presence: {}", message);
				}
				this.lastError = message;
			} else {
				this.lastError = "";
			}
		} catch (IOException e) {
			// Discord was closed or restarted: reconnect and send it again
			this.disconnect(Status.DISCORD_NOT_FOUND);
			synchronized (this.lock) {
				this.changed = true;
				this.nextConnectAttempt = System.currentTimeMillis() + 2_000;
			}
		}
	}

	private void fail(Status status, String message) {
		this.status = status;
		if (!Objects.equals(message, this.lastError)) {
			ServerPresenceClient.LOGGER.info("Discord presence unavailable: {}", message);
		}
		this.lastError = message == null ? "" : message;
		synchronized (this.lock) {
			this.nextConnectAttempt = System.currentTimeMillis() + (status == Status.INVALID_CLIENT_ID ? 4 * RETRY_MS : RETRY_MS);
		}
	}

	private void disconnect(Status status) {
		DiscordIpc connection = this.ipc;
		this.ipc = null;
		this.sentActivity = null;
		if (connection != null) {
			connection.close();
		}
		this.status = status;
	}

	private static String readUser(JsonObject ready) {
		try {
			JsonObject user = ready.getAsJsonObject("data").getAsJsonObject("user");
			String name = user.has("global_name") && !user.get("global_name").isJsonNull() ? user.get("global_name").getAsString() : "";
			return name.isEmpty() ? user.get("username").getAsString() : name;
		} catch (RuntimeException e) {
			return "?";
		}
	}

	private static String stringOf(JsonElement element) {
		return element == null || element.isJsonNull() ? "" : element.isJsonPrimitive() ? element.getAsString() : element.toString();
	}
}
