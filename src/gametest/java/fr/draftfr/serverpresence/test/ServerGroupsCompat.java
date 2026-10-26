package fr.draftfr.serverpresence.test;

import java.lang.reflect.Method;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;

/**
 * When the Server Groups mod is in the test run (put its jar in {@code run-mods/}), the first two servers are
 * put in a group: Server Groups then handles the clicks of the rows itself, the Discord switch must still work.
 */
@SuppressWarnings("UnstableApiUsage")
final class ServerGroupsCompat {
	private ServerGroupsCompat() {
	}

	static void groupFirstTwo(ClientGameTestContext context) {
		if (!FabricLoader.getInstance().isModLoaded("servergroups")) {
			return;
		}
		context.runOnClient(minecraft -> {
			try {
				Class<?> store = Class.forName("fr.draftfr.servergroups.GroupStore");
				Class<?> group = Class.forName("fr.draftfr.servergroups.GroupStore$Group");
				Object survie = store.getMethod("create", String.class).invoke(null, "Survie");
				Method assign = store.getMethod("assign", ServerData.class, group);
				JoinMultiplayerScreen screen = (JoinMultiplayerScreen) minecraft.gui.screen();
				ServerList servers = screen.getServers();
				assign.invoke(null, servers.get(0), survie);
				assign.invoke(null, servers.get(1), survie);
				Class.forName("fr.draftfr.servergroups.gui.ServerGroupsUi").getMethod("refresh", JoinMultiplayerScreen.class).invoke(null, screen);
				System.out.println("[serverpresence-test] Server Groups loaded: servers grouped");
			} catch (ReflectiveOperationException e) {
				throw new AssertionError("Server Groups API changed", e);
			}
		});
	}
}
