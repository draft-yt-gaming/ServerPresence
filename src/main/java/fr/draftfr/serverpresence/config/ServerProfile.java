package fr.draftfr.serverpresence.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A server shown in the Discord presence, editable from the settings screen. */
public class ServerProfile {
	public boolean enabled = true;
	public String name = "";
	/** Addresses that identify the server, comma separated; {@code *.example.fr} matches every subdomain. */
	public String addresses = "";
	/** Address shown in the presence ({ip}). */
	public String displayIp = "";
	/** "Playing ..." line of Discord. */
	public String activityName = "{server}";
	public String details = "Joue sur {server}";
	public String state = "{ip}";
	/** Image URL or Discord asset key; empty = icon of the server fetched from its address. */
	public String largeImage = "";
	public String largeText = "{server} • {players} en ligne";
	public boolean showTimer = true;
	public String buttonLabel = "";
	public String buttonUrl = "";
	/** Optional Discord application id used only for this server (its name replaces the global one). */
	public String clientId = "";

	/** Vanish detection: the presence is hidden (or generic) while vanished. */
	public boolean vanishDetection = false;
	/** System chat messages that mean "vanish enabled" (regex, case insensitive). */
	public String vanishOn = "";
	/** System chat messages that mean "vanish disabled". Checked before {@link #vanishOn}. */
	public String vanishOff = "";
	/** Text present while vanished: sidebar, own tab name, tab header/footer, boss bars, action bar. */
	public String vanishIndicator = "";
	/** Seconds after joining during which the server is not revealed, to let the vanish be detected. */
	public int revealDelaySeconds = 0;

	public ServerProfile() {
	}

	public ServerProfile(String name, String ip, String addresses) {
		this.name = name;
		this.displayIp = ip;
		this.addresses = addresses;
	}

	public ServerProfile copy() {
		ServerProfile copy = new ServerProfile();
		copy.enabled = this.enabled;
		copy.name = this.name;
		copy.addresses = this.addresses;
		copy.displayIp = this.displayIp;
		copy.activityName = this.activityName;
		copy.details = this.details;
		copy.state = this.state;
		copy.largeImage = this.largeImage;
		copy.largeText = this.largeText;
		copy.showTimer = this.showTimer;
		copy.buttonLabel = this.buttonLabel;
		copy.buttonUrl = this.buttonUrl;
		copy.clientId = this.clientId;
		copy.vanishDetection = this.vanishDetection;
		copy.vanishOn = this.vanishOn;
		copy.vanishOff = this.vanishOff;
		copy.vanishIndicator = this.vanishIndicator;
		copy.revealDelaySeconds = this.revealDelaySeconds;
		return copy;
	}

	/** Replaces the {@code null} values a hand-edited file may contain. */
	public void sanitize() {
		this.name = orEmpty(this.name);
		this.addresses = orEmpty(this.addresses);
		this.displayIp = orEmpty(this.displayIp);
		this.activityName = orEmpty(this.activityName);
		this.details = orEmpty(this.details);
		this.state = orEmpty(this.state);
		this.largeImage = orEmpty(this.largeImage);
		this.largeText = orEmpty(this.largeText);
		this.buttonLabel = orEmpty(this.buttonLabel);
		this.buttonUrl = orEmpty(this.buttonUrl);
		this.clientId = orEmpty(this.clientId);
		this.vanishOn = orEmpty(this.vanishOn);
		this.vanishOff = orEmpty(this.vanishOff);
		this.vanishIndicator = orEmpty(this.vanishIndicator);
		this.revealDelaySeconds = Math.clamp(this.revealDelaySeconds, 0, 120);
	}

	private static String orEmpty(String value) {
		return value == null ? "" : value;
	}

	/** Address patterns, lower case, without port. */
	public List<String> addressPatterns() {
		List<String> patterns = new ArrayList<>();
		for (String part : (this.addresses + "," + this.displayIp).split("[,;\\s]+")) {
			String host = normalizeHost(part);
			if (!host.isEmpty() && !patterns.contains(host)) {
				patterns.add(host);
			}
		}
		return patterns;
	}

	public boolean matches(String host) {
		for (String pattern : this.addressPatterns()) {
			if (pattern.startsWith("*.")) {
				String domain = pattern.substring(2);
				if (host.equals(domain) || host.endsWith("." + domain)) {
					return true;
				}
			} else if (host.equals(pattern)) {
				return true;
			}
		}
		return false;
	}

	/** {@code "Play.Example.fr.:25565"} becomes {@code "play.example.fr"}. */
	public static String normalizeHost(String address) {
		String host = address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
		if (host.startsWith("[")) {
			int end = host.indexOf(']');
			return end > 0 ? host.substring(1, end) : host;
		}
		int colon = host.lastIndexOf(':');
		if (colon >= 0 && host.indexOf(':') == colon) {
			host = host.substring(0, colon);
		}
		while (host.endsWith(".")) {
			host = host.substring(0, host.length() - 1);
		}
		return host;
	}
}
