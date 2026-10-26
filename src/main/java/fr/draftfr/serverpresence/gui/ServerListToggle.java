package fr.draftfr.serverpresence.gui;

import fr.draftfr.serverpresence.PresenceManager;
import fr.draftfr.serverpresence.ServerPresenceClient;
import fr.draftfr.serverpresence.config.PresenceConfig;
import fr.draftfr.serverpresence.config.ServerProfile;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** Discord on/off switch drawn on each server of the multiplayer list. */
public final class ServerListToggle {
	public static final Identifier ON = sprite("discord_on");
	public static final Identifier ON_HIGHLIGHTED = sprite("discord_on_highlighted");
	public static final Identifier OFF = sprite("discord_off");
	public static final Identifier OFF_HIGHLIGHTED = sprite("discord_off_highlighted");
	public static final int WIDTH = 10;
	public static final int HEIGHT = 8;

	private ServerListToggle() {
	}

	private static Identifier sprite(String name) {
		return Identifier.fromNamespaceAndPath(ServerPresenceClient.MOD_ID, "server_list/" + name);
	}

	/**
	 * Whether the server is shown on Discord: its profile is enabled, or it has none yet and will be added
	 * automatically. {@code null} when the server can never be shown (LAN, local address): no switch then.
	 */
	public static @Nullable Boolean state(ServerData data) {
		String host = ServerProfile.normalizeHost(data.ip);
		boolean known = false;
		for (ServerProfile server : PresenceConfig.get().servers) {
			if (server.matches(host)) {
				if (server.enabled) {
					return true;
				}
				known = true;
			}
		}
		if (known) {
			return false;
		}
		return PresenceManager.autoProfile(data) == null ? null : PresenceConfig.get().autoAdd;
	}

	public static void toggle(ServerData data) {
		Boolean state = state(data);
		if (state == null) {
			return;
		}
		PresenceConfig config = PresenceConfig.get();
		String host = ServerProfile.normalizeHost(data.ip);
		boolean found = false;
		for (ServerProfile server : config.servers) {
			if (server.matches(host)) {
				// off: every matching profile; on: the first one is enough
				server.enabled = !state && !found;
				found = true;
			}
		}
		if (!found) {
			ServerProfile added = PresenceManager.autoProfile(data);
			if (added == null) {
				return;
			}
			added.enabled = !state;
			config.servers.add(added);
		}
		PresenceConfig.save();
		ServerPresenceClient.manager().reloadProfile();
	}
}
