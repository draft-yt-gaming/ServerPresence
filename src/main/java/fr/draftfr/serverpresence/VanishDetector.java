package fr.draftfr.serverpresence;

import fr.draftfr.serverpresence.config.ServerProfile;
import fr.draftfr.serverpresence.mixin.BossHealthOverlayAccessor;
import fr.draftfr.serverpresence.mixin.PlayerTabOverlayAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.jspecify.annotations.Nullable;

/**
 * Guesses whether the player is vanished on the current server, from what a vanish plugin shows to the vanished
 * player: the "you are now invisible / visible" messages, and texts that stay while vanished (sidebar line, own
 * name in the tab list, tab header/footer, boss bar, action bar).
 */
public final class VanishDetector {
	/** An action bar is considered still shown for this long. */
	private static final long ACTION_BAR_MS = 4_000;
	private static final Pattern LEGACY_CODES = Pattern.compile("§.");
	private static final Map<String, @Nullable Pattern> PATTERNS = new HashMap<>();

	private @Nullable ServerProfile profile;
	private long joinedAt;
	private boolean chatVanished;
	private boolean indicatorVanished;
	private boolean manual;
	private String actionBar = "";
	private long actionBarAt;

	/** New connection: everything is forgotten. */
	public void reset(@Nullable ServerProfile profile) {
		this.profile = profile;
		this.joinedAt = System.currentTimeMillis();
		this.chatVanished = false;
		this.indicatorVanished = false;
		this.manual = false;
		this.actionBar = "";
	}

	/** The profile was edited in the settings while connected. */
	public void setProfile(@Nullable ServerProfile profile) {
		this.profile = profile;
	}

	public boolean toggleManual() {
		this.manual = !this.manual;
		return this.manual;
	}

	public boolean manual() {
		return this.manual;
	}

	/** Vanished according to the server (messages or indicators). */
	public boolean detected() {
		return this.profile != null && this.profile.vanishDetection && (this.chatVanished || this.indicatorVanished);
	}

	/** Whether the server must not be revealed: vanished, forced by the player, or just joined a staff server. */
	public boolean hidden() {
		return this.manual || this.detected() || this.inRevealDelay();
	}

	public boolean inRevealDelay() {
		ServerProfile current = this.profile;
		return current != null && current.vanishDetection
			&& System.currentTimeMillis() - this.joinedAt < current.revealDelaySeconds * 1000L;
	}

	/** System messages (chat with {@code overlay == false}, action bar otherwise). Player chat is never used. */
	public void onGameMessage(Component message, boolean overlay) {
		ServerProfile current = this.profile;
		if (current == null || !current.vanishDetection) {
			return;
		}
		String text = plain(message);
		if (overlay) {
			this.actionBar = text;
			this.actionBarAt = System.currentTimeMillis();
			return;
		}
		// "disabled" first: "Vanish désactivé" also contains "activ"
		if (find(current.vanishOff, text)) {
			this.chatVanished = false;
		} else if (find(current.vanishOn, text)) {
			this.chatVanished = true;
		}
	}

	/** Reads the indicators; called a few times per second. */
	public void tick(Minecraft minecraft) {
		ServerProfile current = this.profile;
		if (current == null || !current.vanishDetection || current.vanishIndicator.isBlank()) {
			this.indicatorVanished = false;
			return;
		}
		boolean found = false;
		for (String text : this.indicatorTexts(minecraft)) {
			if (find(current.vanishIndicator, text)) {
				found = true;
				break;
			}
		}
		this.indicatorVanished = found;
	}

	private List<String> indicatorTexts(Minecraft minecraft) {
		List<String> texts = new ArrayList<>();
		if (minecraft.level != null) {
			Scoreboard scoreboard = minecraft.level.getScoreboard();
			Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
			if (sidebar != null) {
				texts.add(plain(sidebar.getDisplayName()));
				for (PlayerScoreEntry entry : scoreboard.listPlayerScores(sidebar)) {
					Component line = PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName());
					texts.add(plain(line));
				}
			}
		}
		ClientPacketListener connection = minecraft.getConnection();
		if (connection != null && minecraft.player != null) {
			PlayerInfo self = connection.getPlayerInfo(minecraft.player.getUUID());
			if (self != null) {
				Component name = self.getTabListDisplayName();
				if (name == null) {
					name = PlayerTeam.formatNameForTeam(self.getTeam(), Component.literal(self.getProfile().name()));
				}
				texts.add(plain(name));
			}
		}
		PlayerTabOverlayAccessor tab = (PlayerTabOverlayAccessor) minecraft.gui.hud.getTabList();
		addLines(texts, tab.serverpresence$header());
		addLines(texts, tab.serverpresence$footer());
		for (LerpingBossEvent bossBar : ((BossHealthOverlayAccessor) minecraft.gui.hud.getBossOverlay()).serverpresence$events().values()) {
			texts.add(plain(bossBar.getName()));
		}
		if (!this.actionBar.isEmpty() && System.currentTimeMillis() - this.actionBarAt < ACTION_BAR_MS) {
			texts.add(this.actionBar);
		}
		return texts;
	}

	private static void addLines(List<String> texts, @Nullable Component component) {
		if (component != null) {
			for (String line : plain(component).split("\n")) {
				texts.add(line);
			}
		}
	}

	private static String plain(Component component) {
		return LEGACY_CODES.matcher(component.getString()).replaceAll("").strip();
	}

	private static boolean find(String regex, String text) {
		Pattern pattern = compile(regex);
		return pattern != null && !text.isEmpty() && pattern.matcher(text).find();
	}

	/** Compiled, case insensitive pattern, or {@code null} when empty or invalid. */
	public static @Nullable Pattern compile(String regex) {
		if (regex == null || regex.isBlank()) {
			return null;
		}
		synchronized (PATTERNS) {
			if (PATTERNS.containsKey(regex)) {
				return PATTERNS.get(regex);
			}
			Pattern pattern;
			try {
				pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
			} catch (PatternSyntaxException e) {
				ServerPresenceClient.LOGGER.warn("Invalid vanish pattern {}: {}", regex, e.getDescription());
				pattern = null;
			}
			PATTERNS.put(regex, pattern);
			return pattern;
		}
	}

	public static boolean isValid(String regex) {
		return regex == null || regex.isBlank() || compile(regex) != null;
	}
}
