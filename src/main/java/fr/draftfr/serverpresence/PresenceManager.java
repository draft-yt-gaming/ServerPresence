package fr.draftfr.serverpresence;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.draftfr.serverpresence.config.PresenceConfig;
import fr.draftfr.serverpresence.config.ServerProfile;
import fr.draftfr.serverpresence.discord.DiscordRpc;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** Works out what to show in Discord from the game state, a few times per second. */
public final class PresenceManager {
	private static final int TEXT_MAX = 128;
	private static final String AUTO_ICON = "https://api.mcsrvstat.us/icon/";
	private static final String HEAD = "https://mc-heads.net/avatar/";
	private static final String HEAD_3D = "https://mc-heads.net/head/";
	private static final long SWITCH_GRACE_MS = 20_000;

	public enum Place {
		MENU,
		SINGLEPLAYER,
		LISTED_SERVER,
		OTHER_SERVER
	}

	private final DiscordRpc rpc = new DiscordRpc();
	private final VanishDetector vanish = new VanishDetector();
	private final long gameStart = System.currentTimeMillis() / 1000L;
	private @Nullable Object session;
	private String sessionHost = "";
	private long sessionStart;
	private long lostAt;
	private @Nullable ServerProfile profile;
	private Place place = Place.MENU;
	private boolean lastHidden;
	private boolean lastHiddenKnown;

	public DiscordRpc rpc() {
		return this.rpc;
	}

	public VanishDetector vanish() {
		return this.vanish;
	}

	public Place place() {
		return this.place;
	}

	public @Nullable ServerProfile profile() {
		return this.profile;
	}

	public String host() {
		return this.sessionHost;
	}

	public void onGameMessage(Component message, boolean overlay) {
		if (this.session != null) {
			this.vanish.onGameMessage(message, overlay);
		}
	}

	/** The settings changed: find the profile of the current server again. */
	public void reloadProfile() {
		if (this.session != null && this.place != Place.SINGLEPLAYER) {
			this.profile = find(this.sessionHost);
			this.place = this.profile != null ? Place.LISTED_SERVER : Place.OTHER_SERVER;
			this.vanish.setProfile(this.profile);
		}
		this.rpc.retry();
	}

	public void tick(Minecraft minecraft) {
		this.updateSession(minecraft);
		if (this.session != null) {
			this.vanish.tick(minecraft);
		}
		PresenceConfig config = PresenceConfig.get();
		boolean hidden = this.place == Place.LISTED_SERVER && this.vanish.hidden();
		this.notifyVanish(minecraft, config, hidden);

		String clientId = config.clientId;
		if (this.place == Place.LISTED_SERVER && this.profile != null && !this.profile.clientId.isBlank()) {
			clientId = this.profile.clientId;
		}
		this.rpc.update(clientId, config.enabled ? this.activity(minecraft, config, hidden) : null);
	}

	/**
	 * A session lasts from joining a world to going back to the menus. The key is the integrated server or the
	 * {@link ServerData}, which stays the same while a proxy (Velocity) moves the player between its servers,
	 * even though the game goes through loading screens without player or level in between.
	 */
	private void updateSession(Minecraft minecraft) {
		Object key = minecraft.hasSingleplayerServer() ? minecraft.getSingleplayerServer() : minecraft.getCurrentServer();
		boolean inWorld = minecraft.level != null && minecraft.getConnection() != null;
		if (key == null || !inWorld) {
			key = null;
		}
		if (key == this.session) {
			this.lostAt = 0;
			return;
		}
		if (key == null && this.session instanceof ServerData && !inMenus(minecraft)) {
			// the proxy is moving the player to another server: the same ServerData comes back after the loading screens
			long now = System.currentTimeMillis();
			if (this.lostAt == 0) {
				this.lostAt = now;
			}
			if (now - this.lostAt < SWITCH_GRACE_MS) {
				return;
			}
		}
		this.lostAt = 0;
		this.session = key;
		this.sessionStart = System.currentTimeMillis() / 1000L;
		this.lastHiddenKnown = false;
		ServerData server = minecraft.getCurrentServer();
		if (key == null) {
			this.place = Place.MENU;
			this.sessionHost = "";
			this.profile = null;
		} else if (minecraft.hasSingleplayerServer() || server == null) {
			this.place = Place.SINGLEPLAYER;
			this.sessionHost = "";
			this.profile = null;
		} else {
			this.sessionHost = ServerProfile.normalizeHost(server.ip);
			this.profile = find(this.sessionHost);
			this.place = this.profile != null ? Place.LISTED_SERVER : Place.OTHER_SERVER;
		}
		this.vanish.reset(this.profile);
	}

	private static boolean inMenus(Minecraft minecraft) {
		Screen screen = minecraft.gui.screen();
		return screen instanceof TitleScreen || screen instanceof JoinMultiplayerScreen || screen instanceof DisconnectedScreen;
	}

	private static @Nullable ServerProfile find(String host) {
		for (ServerProfile server : PresenceConfig.get().servers) {
			if (server.enabled && server.matches(host)) {
				return server;
			}
		}
		return null;
	}

	/** Tells the player in the chat when the server gets hidden or shown again in Discord. */
	private void notifyVanish(Minecraft minecraft, PresenceConfig config, boolean hidden) {
		boolean inDelay = this.vanish.inRevealDelay() && !this.vanish.detected() && !this.vanish.manual();
		if (inDelay || this.place != Place.LISTED_SERVER) {
			return;
		}
		if ((this.lastHiddenKnown ? hidden != this.lastHidden : hidden) && config.enabled && config.notifyVanish && minecraft.player != null) {
			minecraft.gui.hud.getChat().addClientSystemMessage(Component.translatable(
				hidden ? "serverpresence.notify.hidden" : "serverpresence.notify.shown",
				this.profile == null ? "" : this.profile.name
			).withColor(0xFF9AA4B8));
		}
		this.lastHidden = hidden;
		this.lastHiddenKnown = true;
	}

	private @Nullable JsonObject activity(Minecraft minecraft, PresenceConfig config, boolean hidden) {
		Place shown = this.place;
		if (shown == Place.LISTED_SERVER && hidden) {
			if (config.whileVanished == PresenceConfig.Visibility.HIDDEN) {
				return null;
			}
			shown = Place.OTHER_SERVER;
		}
		return switch (shown) {
			case MENU -> config.showInMenus ? this.generic(minecraft, config, config.menuText, this.gameStart) : null;
			case SINGLEPLAYER -> config.showSingleplayer ? this.generic(minecraft, config, config.singleplayerText, this.sessionStart) : null;
			case OTHER_SERVER -> config.otherServers == PresenceConfig.Visibility.GENERIC
				? this.generic(minecraft, config, config.multiplayerText, this.sessionStart)
				: null;
			case LISTED_SERVER -> this.server(minecraft, config, Objects.requireNonNull(this.profile));
		};
	}

	private JsonObject generic(Minecraft minecraft, PresenceConfig config, String details, long start) {
		JsonObject activity = new JsonObject();
		putText(activity, "name", config.genericName);
		putText(activity, "details", details);
		JsonObject assets = new JsonObject();
		putText(assets, "large_text", "Minecraft " + SharedConstants.getCurrentVersion().name());
		if (config.menuImage.isBlank()) {
			putImage(assets, "large_image", HEAD_3D + uuid(minecraft) + "/128");
		} else {
			putImage(assets, "large_image", config.menuImage);
			this.putHead(minecraft, config, assets);
		}
		activity.add("assets", assets);
		putStart(activity, start);
		return activity;
	}

	/** What Discord shows for {@code server} (used by the tests). */
	public JsonObject preview(Minecraft minecraft, ServerProfile server) {
		return this.server(minecraft, PresenceConfig.get(), server);
	}

	private JsonObject server(Minecraft minecraft, PresenceConfig config, ServerProfile server) {
		JsonObject activity = new JsonObject();
		putText(activity, "name", this.fill(minecraft, server, server.activityName));
		putText(activity, "details", this.fill(minecraft, server, server.details));
		putText(activity, "state", this.fill(minecraft, server, server.state));
		JsonObject assets = new JsonObject();
		String image = server.largeImage.isBlank()
			? AUTO_ICON + URLEncoder.encode(this.ip(server), StandardCharsets.UTF_8)
			: server.largeImage;
		putImage(assets, "large_image", image);
		putText(assets, "large_text", this.fill(minecraft, server, server.largeText));
		this.putHead(minecraft, config, assets);
		activity.add("assets", assets);
		if (server.showTimer) {
			putStart(activity, this.sessionStart);
		}
		String label = this.fill(minecraft, server, server.buttonLabel).strip();
		String url = server.buttonUrl.strip();
		if (!label.isEmpty() && isWebUrl(url)) {
			JsonObject button = new JsonObject();
			button.addProperty("label", truncate(label, 32));
			button.addProperty("url", url);
			JsonArray buttons = new JsonArray();
			buttons.add(button);
			activity.add("buttons", buttons);
		}
		return activity;
	}

	private void putHead(Minecraft minecraft, PresenceConfig config, JsonObject assets) {
		if (config.showPlayerHead) {
			assets.addProperty("small_image", HEAD + uuid(minecraft) + "/64");
			putText(assets, "small_text", minecraft.getUser().getName());
		}
	}

	private static String uuid(Minecraft minecraft) {
		return minecraft.getUser().getProfileId().toString().replace("-", "");
	}

	private String ip(ServerProfile server) {
		return server.displayIp.isBlank() ? this.sessionHost : server.displayIp.strip();
	}

	/** Placeholders: {server}, {ip}, {players}, {player}, {dimension}, {version}. */
	private String fill(Minecraft minecraft, ServerProfile server, String template) {
		if (template.indexOf('{') < 0) {
			return template;
		}
		ClientPacketListener connection = minecraft.getConnection();
		int players = connection == null ? 0 : connection.getListedOnlinePlayers().size();
		return template
			.replace("{server}", server.name)
			.replace("{ip}", this.ip(server))
			.replace("{players}", Integer.toString(players))
			.replace("{player}", minecraft.getUser().getName())
			.replace("{dimension}", dimension(minecraft))
			.replace("{version}", SharedConstants.getCurrentVersion().name());
	}

	private static String dimension(Minecraft minecraft) {
		if (minecraft.level == null) {
			return "";
		}
		if (minecraft.level.dimension() == Level.OVERWORLD) {
			return "Surface";
		}
		if (minecraft.level.dimension() == Level.NETHER) {
			return "Nether";
		}
		if (minecraft.level.dimension() == Level.END) {
			return "End";
		}
		return minecraft.level.dimension().identifier().getPath();
	}

	private static void putStart(JsonObject activity, long start) {
		if (start <= 0) {
			return;
		}
		JsonObject timestamps = new JsonObject();
		timestamps.addProperty("start", start);
		activity.add("timestamps", timestamps);
	}

	/** Discord wants 2 to 128 characters, or no field at all. */
	private static void putText(JsonObject object, String key, String text) {
		String value = text == null ? "" : text.strip();
		if (value.isEmpty()) {
			return;
		}
		if (value.length() < 2) {
			value = value + "⠀";
		}
		object.addProperty(key, truncate(value, TEXT_MAX));
	}

	/** An https URL, or the key of an image uploaded in the Discord application. */
	private static void putImage(JsonObject assets, String key, String image) {
		String value = image == null ? "" : image.strip();
		if (value.isEmpty()) {
			return;
		}
		assets.addProperty(key, isWebUrl(value) ? value : value.toLowerCase(Locale.ROOT));
	}

	private static boolean isWebUrl(String url) {
		return (url.startsWith("https://") || url.startsWith("http://")) && url.length() <= 512 && url.indexOf(' ') < 0;
	}

	private static String truncate(String text, int max) {
		return text.length() <= max ? text : text.substring(0, max - 1) + "…";
	}
}
