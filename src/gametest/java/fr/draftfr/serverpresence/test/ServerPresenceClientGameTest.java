package fr.draftfr.serverpresence.test;

import fr.draftfr.serverpresence.PresenceManager;
import fr.draftfr.serverpresence.ServerPresenceClient;
import fr.draftfr.serverpresence.VanishDetector;
import fr.draftfr.serverpresence.config.PresenceConfig;
import fr.draftfr.serverpresence.discord.DiscordRpc;
import fr.draftfr.serverpresence.config.ServerProfile;
import fr.draftfr.serverpresence.gui.ServerEditScreen;
import fr.draftfr.serverpresence.gui.SettingsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;

@SuppressWarnings("UnstableApiUsage")
public class ServerPresenceClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		PresenceConfig config = PresenceConfig.get();
		check(config.servers.size() == 5, "5 default servers");
		for (ServerProfile server : config.servers) {
			check(VanishDetector.isValid(server.vanishOn) && VanishDetector.isValid(server.vanishOff) && VanishDetector.isValid(server.vanishIndicator),
				"valid patterns for " + server.name);
		}

		// addresses
		ServerProfile platomine = byName(config, "PlatoMINE");
		check(platomine.matches(ServerProfile.normalizeHost("Play.PlatoMine.fr:25565")), "play.platomine.fr with port");
		check(platomine.matches("platomine.fr") && platomine.matches("mc.platomine.fr"), "platomine domain and subdomains");
		check(!platomine.matches("platomine.fr.evil.com"), "no suffix trick");
		ServerProfile luckat = byName(config, "LuckatTech Survie");
		check(luckat.matches("luckattech.playit.game") && !luckat.matches("other.playit.game"), "playit address is exact");
		check(byName(config, "CHL SMP").matches("play.chl-smp.fr"), "chl");

		// PlatoMine vanish messages (PlatoCore messages.yml) and scoreboard line
		VanishDetector vanish = new VanishDetector();
		vanish.reset(platomine);
		check(vanish.inRevealDelay(), "reveal delay right after joining");
		vanish.onGameMessage(Component.literal("[Vanish] Vous êtes désormais invisible."), false);
		check(vanish.detected(), "platomine vanish on");
		vanish.onGameMessage(Component.literal("Draft_fr » je suis désormais visible lol"), false);
		check(vanish.detected(), "platomine: a player message does not match the anchored pattern");
		vanish.onGameMessage(Component.literal("[Vanish] Vous êtes désormais visible."), false);
		check(!vanish.detected(), "platomine vanish off");
		vanish.onGameMessage(Component.literal("[Vanish] Vous êtes toujours invisible sur ce serveur. (/vanish pour réapparaître)"), false);
		check(vanish.detected(), "platomine vanish restored");

		// generic patterns, messages of common vanish plugins
		ServerProfile generic = byName(config, "LeChantierMC");
		String[][] samples = {
			{"Mode invisible Draft_fr : activé", "Mode invisible Draft_fr : désactivé"},
			{"Vanish for Draft_fr: enabled", "Vanish for Draft_fr: disabled"},
			{"You are now invisible!", "You are now visible!"},
			{"Vous êtes maintenant invisible.", "Vous êtes de nouveau visible."},
			{"Vanish enabled", "Vanish disabled"},
			{"[CMI] Vanish activé", "[CMI] Vanish désactivé"},
		};
		for (String[] sample : samples) {
			vanish.reset(generic);
			vanish.onGameMessage(Component.literal(sample[0]), false);
			check(vanish.detected(), "generic on: " + sample[0]);
			vanish.onGameMessage(Component.literal(sample[1]), false);
			check(!vanish.detected(), "generic off: " + sample[1]);
		}
		vanish.reset(generic);
		vanish.onGameMessage(Component.literal("Bienvenue sur le serveur ! Pense à voter."), false);
		vanish.onGameMessage(Component.literal("La potion d'invisibilité est en promotion"), false);
		check(!vanish.detected(), "generic: unrelated messages");
		vanish.reset(byName(config, "SkillioGames"));
		vanish.onGameMessage(Component.literal("You are now invisible!"), false);
		check(!vanish.detected() && !vanish.hidden(), "no vanish detection on SkillioGames");

		// the real Discord of this computer receives the presence of the menus
		waitForActivity(context, "Dans les menus");

		// a listed server, sent as is to Discord (the game sends the menus again on the next tick)
		context.runOnClient(minecraft -> {
			PresenceManager manager = ServerPresenceClient.manager();
			JsonObject activity = manager.preview(minecraft, platomine);
			String json = activity.toString();
			check(json.contains("\"name\":\"PlatoMINE\"") && json.contains("api.mcsrvstat.us/icon/play.platomine.fr")
				&& json.contains("https://platomine.fr"), "PlatoMINE activity " + json);
			ServerPresenceClient.pauseTicks(true);
			manager.rpc().update(PresenceConfig.get().clientId, activity);
		});
		waitForActivity(context, "play.platomine.fr");
		context.runOnClient(minecraft -> ServerPresenceClient.pauseTicks(false));

		// settings screens, from the options of the title screen
		context.setScreen(() -> new OptionsScreen(new TitleScreen(), context.computeOnClient(minecraft -> minecraft.options)));
		context.waitForScreen(OptionsScreen.class);
		context.waitTicks(5);
		context.takeScreenshot("serverpresence-0-options");
		context.clickScreenButton("serverpresence.options.button");
		context.waitForScreen(SettingsScreen.class);
		context.waitTicks(5);
		context.takeScreenshot("serverpresence-1-settings");
		context.runOnClient(minecraft -> minecraft.gui.setScreen(
			new ServerEditScreen((SettingsScreen) minecraft.gui.screen(), PresenceConfig.get().servers.get(0).copy(), 0)));
		context.waitForScreen(ServerEditScreen.class);
		context.waitTicks(5);
		context.takeScreenshot("serverpresence-2-edit");
		context.clickScreenButton("gui.done");
		context.waitForScreen(SettingsScreen.class);
		context.clickScreenButton("gui.done");
		context.waitForScreen(OptionsScreen.class);
		context.clickScreenButton("gui.done");
		context.waitForScreen(TitleScreen.class);

		// singleplayer: the presence follows the game
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			context.waitTicks(20);
			PresenceManager.Place place = context.computeOnClient(minecraft -> ServerPresenceClient.manager().place());
			check(place == PresenceManager.Place.SINGLEPLAYER, "singleplayer detected, got " + place);
			waitForActivity(context, "En solo");
			context.takeScreenshot("serverpresence-3-world");
		}
		context.waitTicks(30);
		PresenceManager.Place place = context.computeOnClient(minecraft -> ServerPresenceClient.manager().place());
		check(place == PresenceManager.Place.MENU, "back in the menus, got " + place);
	}

	/** Skipped when Discord is not running on the test machine. */
	private static void waitForActivity(ClientGameTestContext context, String expected) {
		DiscordRpc rpc = ServerPresenceClient.manager().rpc();
		for (int i = 0; i < 300; i++) {
			if (rpc.status() == DiscordRpc.Status.DISCORD_NOT_FOUND) {
				System.out.println("[serverpresence-test] Discord not running, presence not checked");
				return;
			}
			String sent = rpc.sentActivity();
			if (rpc.status() == DiscordRpc.Status.CONNECTED && sent != null && sent.contains(expected)) {
				check(rpc.lastError().isEmpty(), "Discord refused the presence: " + rpc.lastError());
				System.out.println("[serverpresence-test] Discord (" + rpc.discordUser() + ") accepted " + sent);
				return;
			}
			context.waitTicks(2);
		}
		throw new AssertionError("presence '" + expected + "' not sent to Discord, status " + rpc.status() + " " + rpc.lastError());
	}

	private static ServerProfile byName(PresenceConfig config, String name) {
		for (ServerProfile server : config.servers) {
			if (server.name.equals(name)) {
				return server;
			}
		}
		throw new AssertionError("server not found: " + name);
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError(what);
		}
	}
}
