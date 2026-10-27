package fr.draftfr.serverpresence.gui;

import fr.draftfr.serverpresence.VanishDetector;
import fr.draftfr.serverpresence.config.PresenceConfig;
import fr.draftfr.serverpresence.config.ServerProfile;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Edits one server: how it is recognized, what Discord shows, how its vanish is detected. */
public class ServerEditScreen extends OptionsSubScreen {
	private static final int TEXT = 0xFFE0E0E0;
	private final SettingsScreen parent;
	private final ServerProfile server;
	private final int index;
	private @Nullable EditBox onBox;
	private @Nullable EditBox offBox;
	private @Nullable EditBox indicatorBox;
	private @Nullable Button doneButton;

	/** {@code index == -1}: new server. */
	public ServerEditScreen(SettingsScreen parent, ServerProfile server, int index) {
		super(parent, Minecraft.getInstance().options, Component.translatable(index < 0 ? "serverpresence.edit.title.new" : "serverpresence.edit.title"));
		this.parent = parent;
		this.server = server;
		this.index = index;
	}

	@Override
	protected void addOptions() {
		if (this.list == null) {
			return;
		}
		ServerProfile s = this.server;

		this.list.addHeader(Component.translatable("serverpresence.edit.server"));
		this.list.addSmall(
			SettingsScreen.toggle("serverpresence.edit.enabled", s.enabled, value -> s.enabled = value),
			SettingsScreen.toggle("serverpresence.edit.timer", s.showTimer, value -> s.showTimer = value)
		);
		this.field("serverpresence.edit.name", s.name, 64, value -> {
			s.name = value.strip();
			this.validate();
		});
		this.field("serverpresence.edit.ip", s.displayIp, 128, value -> s.displayIp = value.strip());
		this.field("serverpresence.edit.addresses", s.addresses, 512, value -> s.addresses = value);

		this.list.addHeader(Component.translatable("serverpresence.edit.discord"));
		this.field("serverpresence.edit.activity_name", s.activityName, 128, value -> s.activityName = value);
		this.field("serverpresence.edit.details", s.details, 128, value -> s.details = value);
		this.field("serverpresence.edit.state", s.state, 128, value -> s.state = value);
		this.field("serverpresence.edit.image", s.largeImage, 256, value -> s.largeImage = value.strip());
		this.field("serverpresence.edit.image_text", s.largeText, 128, value -> s.largeText = value);
		this.field("serverpresence.edit.button_label", s.buttonLabel, 32, value -> s.buttonLabel = value);
		this.field("serverpresence.edit.button_url", s.buttonUrl, 512, value -> s.buttonUrl = value.strip());

		this.list.addHeader(Component.translatable("serverpresence.edit.vanish"));
		EditBox delay = this.box(Integer.toString(s.revealDelaySeconds), 3, null);
		delay.setResponder(value -> {
			boolean number = value.matches("\\d{1,3}");
			delay.setTextColor(number || value.isEmpty() ? TEXT : SettingsScreen.RED);
			if (number) {
				s.revealDelaySeconds = Math.min(120, Integer.parseInt(value));
			} else if (value.isEmpty()) {
				s.revealDelaySeconds = 0;
			}
		});
		delay.setTooltip(Tooltip.create(Component.translatable("serverpresence.edit.delay.tooltip")));
		this.list.addSmall(SettingsScreen.toggle("serverpresence.edit.vanish_detection", s.vanishDetection, value -> s.vanishDetection = value), delay);
		this.onBox = this.regexField("serverpresence.edit.vanish_on", s.vanishOn, value -> s.vanishOn = value);
		this.offBox = this.regexField("serverpresence.edit.vanish_off", s.vanishOff, value -> s.vanishOff = value);
		this.indicatorBox = this.regexField("serverpresence.edit.vanish_indicator", s.vanishIndicator, value -> s.vanishIndicator = value);
		this.list.addSmall(
			Button.builder(Component.translatable("serverpresence.edit.vanish_generic"), button -> this.useGenericPatterns())
				.tooltip(Tooltip.create(Component.translatable("serverpresence.edit.vanish_generic.tooltip"))).width(150).build(),
			Button.builder(Component.translatable("serverpresence.edit.vanish_clear"), button -> this.setPatterns("", "", "")).width(150).build()
		);
		this.validate();
	}

	@Override
	protected void addFooter() {
		LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		Button delete = footer.addChild(Button.builder(Component.translatable("serverpresence.edit.delete"), button -> this.confirmDelete()).width(98).build());
		delete.active = this.index >= 0;
		footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.minecraft.setScreen(this.parent)).width(98).build());
		this.doneButton = footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(98).build());
		this.validate();
	}

	/** Escape and "Done" both save, like the vanilla options; "Cancel" drops the changes. */
	@Override
	public void onClose() {
		if (this.isValid()) {
			this.parent.saveServer(this.server, this.index);
		}
		this.minecraft.setScreen(this.parent);
	}

	private boolean isValid() {
		if (this.server.name.isBlank()) {
			return false;
		}
		return VanishDetector.isValid(this.server.vanishOn) && VanishDetector.isValid(this.server.vanishOff)
			&& VanishDetector.isValid(this.server.vanishIndicator);
	}

	private void validate() {
		if (this.doneButton != null) {
			this.doneButton.active = this.isValid();
		}
	}

	private void useGenericPatterns() {
		this.setPatterns(PresenceConfig.GENERIC_ON, PresenceConfig.GENERIC_OFF, PresenceConfig.GENERIC_INDICATOR);
	}

	private void setPatterns(String on, String off, String indicator) {
		if (this.onBox != null && this.offBox != null && this.indicatorBox != null) {
			// the responders update the profile
			this.onBox.setValue(on);
			this.offBox.setValue(off);
			this.indicatorBox.setValue(indicator);
		}
	}

	private EditBox field(String key, String value, int maxLength, @Nullable Consumer<String> setter) {
		EditBox box = this.box(value, maxLength, setter);
		Tooltip tooltip = SettingsScreen.tooltip(key);
		if (tooltip != null) {
			box.setTooltip(tooltip);
		}
		if (this.list != null) {
			this.list.addSmall(this.parent.label(key), box);
		}
		return box;
	}

	private EditBox regexField(String key, String value, Consumer<String> setter) {
		EditBox box = this.field(key, value, 1024, null);
		box.setResponder(current -> {
			setter.accept(current);
			box.setTextColor(VanishDetector.isValid(current) ? TEXT : SettingsScreen.RED);
			this.validate();
		});
		box.setTextColor(VanishDetector.isValid(value) ? TEXT : SettingsScreen.RED);
		return box;
	}

	private EditBox box(String value, int maxLength, @Nullable Consumer<String> setter) {
		return this.parent.box(value, maxLength, null, setter);
	}

	private void confirmDelete() {
		this.minecraft.setScreen(new ConfirmScreen(
			confirmed -> {
				if (confirmed) {
					this.parent.deleteServer(this.index);
					this.minecraft.setScreen(this.parent);
				} else {
					this.minecraft.setScreen(this);
				}
			},
			Component.translatable("serverpresence.edit.delete.title", this.server.name),
			Component.translatable("serverpresence.edit.delete.message"),
			Component.translatable("serverpresence.edit.delete"),
			CommonComponents.GUI_CANCEL
		));
	}
}
