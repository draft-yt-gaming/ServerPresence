package fr.draftfr.serverpresence.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Minimal client of the local Discord RPC socket (named pipe on Windows, unix socket elsewhere).
 * Every command is answered by Discord, so frames are exchanged in request/response pairs on one thread:
 * no read is ever pending while writing (a synchronous Windows pipe would block the write).
 */
public final class DiscordIpc implements Closeable {
	private static final int OP_HANDSHAKE = 0;
	private static final int OP_FRAME = 1;
	private static final int OP_CLOSE = 2;
	private static final int OP_PING = 3;
	private static final int OP_PONG = 4;
	private static final int MAX_FRAME = 1 << 20;

	private final Transport transport;
	private final JsonObject ready;

	private DiscordIpc(Transport transport, String clientId) throws IOException {
		this.transport = transport;
		JsonObject handshake = new JsonObject();
		handshake.addProperty("v", 1);
		handshake.addProperty("client_id", clientId);
		this.ready = this.exchange(OP_HANDSHAKE, handshake);
	}

	/** Opens the first Discord socket that accepts the handshake. */
	public static DiscordIpc connect(String clientId) throws IOException {
		IOException last = null;
		for (String path : candidates()) {
			Transport transport;
			try {
				transport = open(path);
			} catch (IOException e) {
				continue;
			}
			try {
				return new DiscordIpc(transport, clientId);
			} catch (IOException e) {
				transport.close();
				if (e instanceof DiscordException) {
					throw e;
				}
				last = e;
			}
		}
		throw last != null ? last : new IOException("Discord is not running");
	}

	/** The READY event of the handshake (contains the Discord user). */
	public JsonObject ready() {
		return this.ready;
	}

	/** Sends a command and returns the answer of Discord. */
	public JsonObject command(JsonObject payload) throws IOException {
		return this.exchange(OP_FRAME, payload);
	}

	private JsonObject exchange(int op, JsonObject payload) throws IOException {
		this.write(op, payload.toString());
		while (true) {
			ByteBuffer header = ByteBuffer.wrap(this.transport.read(8)).order(ByteOrder.LITTLE_ENDIAN);
			int replyOp = header.getInt();
			int length = header.getInt();
			if (length < 0 || length > MAX_FRAME) {
				throw new IOException("Invalid Discord frame length " + length);
			}
			String json = new String(this.transport.read(length), StandardCharsets.UTF_8);
			switch (replyOp) {
				case OP_FRAME -> {
					return JsonParser.parseString(json).getAsJsonObject();
				}
				case OP_PING -> this.write(OP_PONG, json);
				case OP_CLOSE -> {
					JsonObject data = JsonParser.parseString(json).getAsJsonObject();
					int code = data.has("code") ? data.get("code").getAsInt() : -1;
					String message = data.has("message") ? data.get("message").getAsString() : json;
					throw new DiscordException(code, message);
				}
				default -> {
					// OP_PONG or unknown: ignored
				}
			}
		}
	}

	private void write(int op, String json) throws IOException {
		byte[] data = json.getBytes(StandardCharsets.UTF_8);
		ByteBuffer frame = ByteBuffer.allocate(8 + data.length).order(ByteOrder.LITTLE_ENDIAN);
		frame.putInt(op).putInt(data.length).put(data);
		this.transport.write(frame.array());
	}

	@Override
	public void close() {
		try {
			this.write(OP_CLOSE, "{}");
		} catch (IOException ignored) {
		}
		this.transport.close();
	}

	private static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}

	private static List<String> candidates() {
		List<String> paths = new ArrayList<>();
		if (isWindows()) {
			for (int i = 0; i < 10; i++) {
				paths.add("\\\\.\\pipe\\discord-ipc-" + i);
			}
			return paths;
		}
		List<String> roots = new ArrayList<>();
		for (String variable : new String[] {"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
			String value = System.getenv(variable);
			if (value != null && !value.isBlank() && !roots.contains(value)) {
				roots.add(value);
			}
		}
		roots.add("/tmp");
		String[] subdirectories = {"", "app/com.discordapp.Discord", "snap.discord", ".flatpak/dev.vencord.Vesktop/xdg-run"};
		for (int i = 0; i < 10; i++) {
			for (String root : roots) {
				for (String subdirectory : subdirectories) {
					Path path = Path.of(root, subdirectory, "discord-ipc-" + i);
					if (Files.exists(path)) {
						paths.add(path.toString());
					}
				}
			}
		}
		return paths;
	}

	private static Transport open(String path) throws IOException {
		if (isWindows()) {
			RandomAccessFile pipe = new RandomAccessFile(path, "rw");
			return new Transport() {
				@Override
				public void write(byte[] data) throws IOException {
					pipe.write(data);
				}

				@Override
				public byte[] read(int length) throws IOException {
					byte[] data = new byte[length];
					pipe.readFully(data);
					return data;
				}

				@Override
				public void close() {
					try {
						pipe.close();
					} catch (IOException ignored) {
					}
				}
			};
		}
		SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
		try {
			channel.connect(UnixDomainSocketAddress.of(Path.of(path)));
		} catch (IOException e) {
			channel.close();
			throw e;
		}
		return new Transport() {
			@Override
			public void write(byte[] data) throws IOException {
				ByteBuffer buffer = ByteBuffer.wrap(data);
				while (buffer.hasRemaining()) {
					channel.write(buffer);
				}
			}

			@Override
			public byte[] read(int length) throws IOException {
				ByteBuffer buffer = ByteBuffer.allocate(length);
				while (buffer.hasRemaining()) {
					if (channel.read(buffer) < 0) {
						throw new EOFException("Discord closed the connection");
					}
				}
				return buffer.array();
			}

			@Override
			public void close() {
				try {
					channel.close();
				} catch (IOException ignored) {
				}
			}
		};
	}

	private interface Transport {
		void write(byte[] data) throws IOException;

		byte[] read(int length) throws IOException;

		void close();
	}

	/** Discord closed the connection on purpose (invalid application id, rate limit...). */
	public static final class DiscordException extends IOException {
		public final int code;

		DiscordException(int code, String message) {
			super(message + " (" + code + ")");
			this.code = code;
		}
	}
}
