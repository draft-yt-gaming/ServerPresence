package fr.draftfr.serverpresence.gui;

import fr.draftfr.serverpresence.ServerPresenceClient;
import fr.draftfr.serverpresence.config.PresenceConfig;
import fr.draftfr.serverpresence.config.ServerProfile;
import fr.draftfr.serverpresence.discord.DiscordRpc;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Main settings: global options, Discord application, list of servers. */
public class SettingsScreen extends OptionsSubScreen {
	static final int GRAY = 0xFFA0A0A0;
	static final int RED = 0xFFFF5555;
	private @Nullable StringWidget status;

	public SettingsScreen(@Nullable Screen lastScreen) {
		super(lastScreen, Minecraft.getInstance().options, Component.translatable("serverpresence.settings.title"));
	}

	@Override
	protected void addOptions() {
		if (this.list == null) {
			return;
		}
		PresenceConfig config = PresenceConfig.get();

		this.list.addHeader(Component.translatable("serverpresence.settings.general"));
		this.list.addSmall(
			toggle("serverpresence.settings.enabled", config.enabled, value -> config.enabled = value),
			toggle("serverpresence.settings.notify", config.notifyVanish, value -> config.notifyVanish = value)
		);
		this.list.addSmall(
			toggle("serverpresence.settings.menus", config.showInMenus, value -> config.showInMenus = value),
			toggle("serverpresence.settings.singleplayer", config.showSingleplayer, value -> config.showSingleplayer = value)
		);
		this.list.addSmall(
			visibility("serverpresence.settings.other", config.otherServers, value -> config.otherServers = value),
			visibility("serverpresence.settings.vanished", config.whileVanished, value -> config.whileVanished = value)
		);
		CycleButton<Boolean> discreet = CycleButton.onOffBuilder(ServerPresenceClient.manager().vanish().manual())
			.withTooltip(value -> Tooltip.create(Component.translatable("serverpresence.settings.discreet.tooltip")))
			.create(Component.translatable("serverpresence.settings.discreet"), (button, value) -> {
				if (ServerPresenceClient.manager().vanish().manual() != value) {
					ServerPresenceClient.manager().vanish().toggleManual();
				}
			});
		this.list.addSmall(toggle("serverpresence.settings.head", config.showPlayerHead, value -> config.showPlayerHead = value), discreet);
		this.list.addSmall(toggle("serverpresence.settings.auto_add", config.autoAdd, value -> config.autoAdd = value), null);

		this.list.addHeader(Component.translatable("serverpresence.settings.discord"));
		this.status = new StringWidget(310, 20, discordStatus(), this.font);
		this.list.addSmall(this.status, null);

		this.list.addHeader(Component.translatable("serverpresence.settings.servers"));
		for (int i = 0; i < config.servers.size(); i++) {
			ServerProfile server = config.servers.get(i);
			int index = i;
			CycleButton<Boolean> enabled = CycleButton.onOffBuilder(server.enabled)
				.withTooltip(value -> Tooltip.create(Component.literal(String.join(", ", server.addressPatterns())).withColor(GRAY)))
				.create(Component.literal(server.name.isBlank() ? "?" : server.name), (button, value) -> server.enabled = value);
			Button edit = Button.builder(Component.translatable("serverpresence.settings.edit"),
				button -> this.minecraft.setScreen(new ServerEditScreen(this, server.copy(), index))).width(150).build();
			this.list.addSmall(enabled, edit);
		}
		this.list.addSmall(Button.builder(Component.translatable("serverpresence.settings.add"),
			button -> this.minecraft.setScreen(new ServerEditScreen(this, new ServerProfile(), -1))).width(310).build(), null);

		this.list.addHeader(Component.translatable("serverpresence.settings.texts"));
		this.list.addSmall(label("serverpresence.settings.generic_name"), this.box(config.genericName, 128, null, value -> config.genericName = value));
		this.list.addSmall(label("serverpresence.settings.menu_text"), this.box(config.menuText, 128, null, value -> config.menuText = value));
		this.list.addSmall(label("serverpresence.settings.singleplayer_text"), this.box(config.singleplayerText, 128, null, value -> config.singleplayerText = value));
		this.list.addSmall(label("serverpresence.settings.multiplayer_text"), this.box(config.multiplayerText, 128, null, value -> config.multiplayerText = value));
		EditBox menuImage = this.box(config.menuImage, 256, null, value -> config.menuImage = value);
		menuImage.setTooltip(Tooltip.create(Component.translatable("serverpresence.settings.menu_image.tooltip")));
		this.list.addSmall(label("serverpresence.settings.menu_image"), menuImage);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.status != null) {
			this.status.setMessage(discordStatus());
		}
	}

	@Override
	public void onClose() {
		PresenceConfig.save();
		ServerPresenceClient.manager().reloadProfile();
		super.onClose();
	}

	/** Called by {@link ServerEditScreen} when it is done. */
	void saveServer(ServerProfile server, int index) {
		PresenceConfig config = PresenceConfig.get();
		if (index >= 0 && index < config.servers.size()) {
			config.servers.set(index, server);
		} else {
			config.servers.add(server);
		}
		PresenceConfig.save();
		ServerPresenceClient.manager().reloadProfile();
	}

	void deleteServer(int index) {
		PresenceConfig config = PresenceConfig.get();
		if (index >= 0 && index < config.servers.size()) {
			config.servers.remove(index);
			PresenceConfig.save();
			ServerPresenceClient.manager().reloadProfile();
		}
	}

	public static Component discordStatus() {
		PresenceConfig config = PresenceConfig.get();
		if (!config.enabled) {
			return Component.translatable("serverpresence.discord.disabled").withColor(GRAY);
		}
		DiscordRpc rpc = ServerPresenceClient.manager().rpc();
		DiscordRpc.Status status = rpc.status();
		Component text = switch (status) {
			case CONNECTED -> Component.translatable("serverpresence.discord.connected", rpc.discordUser()).withColor(0xFF66DD66);
			case CONNECTING -> Component.translatable("serverpresence.discord.connecting").withColor(GRAY);
			case DISCORD_NOT_FOUND -> Component.translatable("serverpresence.discord.not_found").withColor(0xFFFFD866);
			case INVALID_CLIENT_ID -> Component.translatable("serverpresence.discord.invalid_client_id").withColor(RED);
			case IDLE -> Component.translatable("serverpresence.discord.idle").withColor(GRAY);
			case ERROR -> Component.translatable("serverpresence.discord.error", rpc.lastError()).withColor(RED);
		};
		if (status == DiscordRpc.Status.CONNECTED && !rpc.lastError().isEmpty()) {
			return Component.translatable("serverpresence.discord.refused", rpc.lastError()).withColor(RED);
		}
		return text;
	}

	static CycleButton<Boolean> toggle(String key, boolean value, Consumer<Boolean> setter) {
		return CycleButton.onOffBuilder(value)
			.withTooltip(current -> tooltip(key))
			.create(Component.translatable(key), (button, current) -> setter.accept(current));
	}

	private static CycleButton<PresenceConfig.Visibility> visibility(String key, PresenceConfig.Visibility value, Consumer<PresenceConfig.Visibility> setter) {
		return CycleButton.builder(
				(PresenceConfig.Visibility current) -> Component.translatable("serverpresence.visibility." + current.name().toLowerCase(Locale.ROOT)), value)
			.withValues(PresenceConfig.Visibility.values())
			.withTooltip(current -> tooltip(key))
			.create(Component.translatable(key), (button, current) -> setter.accept(current));
	}

	/** Tooltip from the {@code <key>.tooltip} translation, when there is one. */
	static @Nullable Tooltip tooltip(String key) {
		String tooltipKey = key + ".tooltip";
		return Language.getInstance().has(tooltipKey) ? Tooltip.create(Component.translatable(tooltipKey)) : null;
	}

	StringWidget label(String key) {
		return new StringWidget(150, 20, Component.translatable(key), this.font);
	}

	EditBox box(String value, int maxLength, @Nullable String hint, @Nullable Consumer<String> setter) {
		EditBox box = new EditBox(this.font, 150, 20, Component.empty());
		box.setMaxLength(maxLength);
		box.setValue(value);
		if (hint != null) {
			box.setHint(Component.translatable(hint).withColor(GRAY));
		}
		if (setter != null) {
			box.setResponder(setter);
		}
		return box;
	}
}
