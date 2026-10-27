package fr.draftfr.serverpresence;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import fr.draftfr.serverpresence.config.PresenceConfig;
import fr.draftfr.serverpresence.config.ServerProfile;
import fr.draftfr.serverpresence.gui.SettingsScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

public class ServerPresenceClient implements ClientModInitializer {
	public static final String MOD_ID = "serverpresence";
	public static final Logger LOGGER = LogUtils.getLogger();
	private static final int GRAY = 0xFF9AA4B8;
	private static PresenceManager manager;
	private static boolean openSettings;
	private static boolean paused;
	private int ticks;

	@Override
	public void onInitializeClient() {
		PresenceConfig.load();
		manager = new PresenceManager();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		KeyMapping settingsKey = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.serverpresence.settings", InputConstants.KEY_F7, category));
		KeyMapping discreetKey = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.serverpresence.discreet", InputConstants.UNKNOWN.getValue(), category));

		ClientReceiveMessageEvents.GAME.register((message, overlay) -> manager.onGameMessage(message, overlay));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (settingsKey.consumeClick()) {
				openSettings = true;
			}
			while (discreetKey.consumeClick()) {
				toggleDiscreet(minecraft);
			}
			if (openSettings) {
				openSettings = false;
				minecraft.setScreen(new SettingsScreen(minecraft.screen));
			}
			if (++this.ticks % 10 == 0 && !paused) {
				manager.tick(minecraft);
			}
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(minecraft -> manager.rpc().shutdown());

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
			ClientCommandManager.literal("presence")
				.executes(context -> {
					// the chat screen closes after the command: open the settings on the next tick
					openSettings = true;
					return 1;
				})
				.then(ClientCommandManager.literal("discret").executes(context -> {
					toggleDiscreet(context.getSource().getClient());
					return 1;
				}))
				.then(ClientCommandManager.literal("statut").executes(context -> {
					status(context.getSource());
					return 1;
				}))
		));
	}

	/** Tests only: stops the automatic updates. */
	public static void pauseTicks(boolean pause) {
		paused = pause;
	}

	public static PresenceManager manager() {
		return manager;
	}

	/** Hides the current server in Discord until the next disconnection (or until toggled again). */
	private static void toggleDiscreet(Minecraft minecraft) {
		boolean discreet = manager.vanish().toggleManual();
		if (minecraft.player != null) {
			minecraft.gui.getChat().addMessage(
				Component.translatable(discreet ? "serverpresence.discreet.on" : "serverpresence.discreet.off").withColor(GRAY));
		}
		manager.tick(minecraft);
	}

	private static void status(FabricClientCommandSource source) {
		ServerProfile profile = manager.profile();
		Component server = switch (manager.place()) {
			case MENU -> Component.translatable("serverpresence.status.place.menu");
			case SINGLEPLAYER -> Component.translatable("serverpresence.status.place.singleplayer");
			case OTHER_SERVER -> Component.translatable("serverpresence.status.place.other", manager.host());
			case LISTED_SERVER -> Component.literal(profile == null ? manager.host() : profile.name);
		};
		source.sendFeedback(Component.translatable("serverpresence.status.server", server).withColor(GRAY));
		if (profile != null && profile.vanishDetection) {
			VanishDetector vanish = manager.vanish();
			String key = vanish.detected() ? "serverpresence.status.vanish.detected"
				: vanish.inRevealDelay() ? "serverpresence.status.vanish.delay"
				: "serverpresence.status.vanish.none";
			source.sendFeedback(Component.translatable(key).withColor(GRAY));
		}
		if (manager.vanish().manual()) {
			source.sendFeedback(Component.translatable("serverpresence.discreet.on").withColor(GRAY));
		}
		source.sendFeedback(SettingsScreen.discordStatus().copy().withColor(GRAY));
	}
}
