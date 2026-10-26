package fr.draftfr.serverpresence.mixin;

import fr.draftfr.serverpresence.gui.SettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** "Discord presence..." button below the other buttons of the options screen, usable from the title screen. */
@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin {
	/** After every row of the vanilla grid (and of mods adding rows to it); empty rows take no room. */
	private static final int ROW = 64;

	@ModifyArg(
		method = "init",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/HeaderAndFooterLayout;addToContents(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;"),
		require = 0
	)
	private LayoutElement serverpresence$addButton(LayoutElement contents) {
		if (contents instanceof GridLayout grid) {
			Screen options = (Screen) (Object) this;
			grid.addChild(Button.builder(Component.translatable("serverpresence.options.button"),
					button -> Minecraft.getInstance().gui.setScreen(new SettingsScreen(options)))
				.width(308).build(), ROW, 0, 1, 2);
		}
		return contents;
	}
}
