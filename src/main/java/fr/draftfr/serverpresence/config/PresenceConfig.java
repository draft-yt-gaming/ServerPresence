package fr.draftfr.serverpresence.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import fr.draftfr.serverpresence.ServerPresenceClient;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/** Settings of the mod, stored in {@code config/serverpresence.json}. */
public class PresenceConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static PresenceConfig instance = new PresenceConfig();

	/** What to show instead of the server. */
	public enum Visibility {
		/** Nothing at all in Discord. */
		HIDDEN,
		/** "Playing Minecraft, multiplayer", without the server. */
		GENERIC
	}

	public boolean enabled = true;
	public boolean showInMenus = true;
	public boolean showSingleplayer = true;
	/** A server joined for the first time is added to the list (shown with its name of the multiplayer list). */
	public boolean autoAdd = true;
	/** Servers that are not in the list, or disabled. */
	public Visibility otherServers = Visibility.GENERIC;
	/** While vanished on a server with vanish detection. */
	public Visibility whileVanished = Visibility.GENERIC;
	/** Small image: the head of the player. */
	public boolean showPlayerHead = true;
	/** Image (URL or Discord asset key) used in menus, solo and generic multiplayer; empty = 3D head of the player. */
	public String menuImage = "";
	/** "Playing ..." line in menus, solo and generic mode. */
	public String genericName = "Minecraft";
	public String menuText = "Dans les menus";
	public String singleplayerText = "En solo";
	/** Shown for other servers and while vanished (generic mode). */
	public String multiplayerText = "En multijoueur";
	/** Chat message when the server gets hidden or shown again because of the vanish. */
	public boolean notifyVanish = true;
	public List<ServerProfile> servers = new ArrayList<>();

	public static PresenceConfig get() {
		return instance;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("serverpresence.json");
	}

	public static void load() {
		Path file = file();
		if (Files.isRegularFile(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				PresenceConfig loaded = GSON.fromJson(reader, PresenceConfig.class);
				if (loaded != null) {
					instance = loaded.sanitize();
					return;
				}
			} catch (IOException | JsonParseException e) {
				ServerPresenceClient.LOGGER.error("Unable to read {}, defaults are used", file, e);
				try {
					Files.copy(file, file.resolveSibling("serverpresence.json.broken"), StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException ignored) {
				}
			}
		}
		instance = defaults();
		save();
	}

	public static void save() {
		Path file = file();
		try {
			Files.createDirectories(file.getParent());
			Path temp = file.resolveSibling("serverpresence.json.tmp");
			try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			ServerPresenceClient.LOGGER.error("Unable to save {}", file, e);
		}
	}

	private PresenceConfig sanitize() {
		if (this.menuImage == null) {
			this.menuImage = "";
		}
		if (this.genericName == null) {
			this.genericName = "";
		}
		if (this.menuText == null) {
			this.menuText = "";
		}
		if (this.singleplayerText == null) {
			this.singleplayerText = "";
		}
		if (this.multiplayerText == null) {
			this.multiplayerText = "";
		}
		if (this.otherServers == null) {
			this.otherServers = Visibility.GENERIC;
		}
		if (this.whileVanished == null) {
			this.whileVanished = Visibility.GENERIC;
		}
		if (this.servers == null) {
			this.servers = new ArrayList<>();
		}
		this.servers.removeIf(server -> server == null);
		this.servers.forEach(ServerProfile::sanitize);
		return this;
	}

	// PlatoMine: PlatoCore messages.yml (vanish-on / vanish-off / vanish-restored), scoreboard.yml vanish-line, tab.yml vanish-indicator.
	private static final String PLATOMINE_ON = "^\\[Vanish\\] Vous êtes (désormais|toujours) invisible";
	private static final String PLATOMINE_OFF = "^\\[Vanish\\] Vous êtes désormais visible";
	private static final String PLATOMINE_INDICATOR = "VANISH ACTIVÉ|\\[V\\]";
	// Generic patterns for the usual vanish plugins (SuperVanish/PremiumVanish, EssentialsX, CMI...), French and English.
	public static final String GENERIC_ON = "(vanish|invisib)[^\\n]{0,40}(activ|enabl|\\bon\\b)|(désormais|maintenant|à présent|now) (invisible|vanished|en vanish)|you are vanished";
	public static final String GENERIC_OFF = "(vanish|invisib)[^\\n]{0,40}(désactiv|disabl|\\boff\\b)|(désormais|maintenant|à présent|now|de nouveau) visible"
		+ "|n'êtes plus (invisible|en vanish)|no longer (vanished|invisible)|unvanish|réapparu|reappeared";
	public static final String GENERIC_INDICATOR = "(you are|vous êtes)( currently| actuellement| toujours)? (vanished|invisible|en vanish)|vanish[: ]*(activ|enabl|on\\b)|\\bvanished\\b";

	public static PresenceConfig defaults() {
		PresenceConfig config = new PresenceConfig();

		ServerProfile platomine = new ServerProfile("PlatoMINE", "play.platomine.fr", "platomine.fr, *.platomine.fr");
		platomine.buttonLabel = "Site de PlatoMINE";
		platomine.buttonUrl = "https://platomine.fr";
		staff(platomine, PLATOMINE_ON, PLATOMINE_OFF, PLATOMINE_INDICATOR);
		config.servers.add(platomine);

		ServerProfile chantier = new ServerProfile("LeChantierMC", "play.lechantiermc.fr", "lechantiermc.fr, *.lechantiermc.fr");
		staff(chantier, GENERIC_ON, GENERIC_OFF, GENERIC_INDICATOR);
		config.servers.add(chantier);

		ServerProfile chl = new ServerProfile("CHL SMP", "play.chl-smp.fr", "chl-smp.fr, *.chl-smp.fr");
		staff(chl, GENERIC_ON, GENERIC_OFF, GENERIC_INDICATOR);
		config.servers.add(chl);

		config.servers.add(new ServerProfile("SkillioGames", "play.skilliogames.fr", "skilliogames.fr, *.skilliogames.fr"));
		config.servers.add(new ServerProfile("LuckatTech Survie", "luckattech.playit.game", ""));
		return config;
	}

	private static void staff(ServerProfile profile, String on, String off, String indicator) {
		profile.vanishDetection = true;
		profile.vanishOn = on;
		profile.vanishOff = off;
		profile.vanishIndicator = indicator;
		profile.revealDelaySeconds = 8;
	}
}
