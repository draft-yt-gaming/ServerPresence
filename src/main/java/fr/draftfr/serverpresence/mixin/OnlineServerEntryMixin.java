package fr.draftfr.serverpresence.mixin;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import fr.draftfr.serverpresence.gui.ServerListToggle;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Discord switch in the bottom right corner of each server row, below the ping icon and with the same size.
 * Priority below the default: its click check is injected last at HEAD, so it runs before the other mods' (Server Groups, Essential).
 */
@Mixin(value = ServerSelectionList.OnlineServerEntry.class, priority = 500)
public abstract class OnlineServerEntryMixin extends ServerSelectionList.Entry {
	@Unique
	private static final int SERVERPRESENCE$GRAY = 0xFFA0A0A0;

	@Shadow
	@Final
	private ServerData serverData;

	@Unique
	private int serverpresence$x() {
		return this.getContentRight() - ServerListToggle.WIDTH - 5;
	}

	@Unique
	private int serverpresence$y() {
		return this.getContentY() + 32 - ServerListToggle.HEIGHT - 1;
	}

	@Unique
	private boolean serverpresence$over(double mouseX, double mouseY) {
		int x = this.serverpresence$x();
		int y = this.serverpresence$y();
		return mouseX >= x - 1 && mouseX <= x + ServerListToggle.WIDTH + 1 && mouseY >= y - 1 && mouseY <= y + ServerListToggle.HEIGHT + 1;
	}

	/** The motd wraps before the switch instead of running under it. */
	@ModifyArg(
		method = "extractContent",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;"),
		index = 1
	)
	private int serverpresence$motdWidth(int width) {
		return ServerListToggle.state(this.serverData) == null ? width : width - ServerListToggle.WIDTH - 5;
	}

	@Inject(method = "extractContent", at = @At("TAIL"))
	private void serverpresence$drawSwitch(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a, CallbackInfo ci) {
		Boolean on = ServerListToggle.state(this.serverData);
		if (on == null) {
			return;
		}
		boolean over = this.serverpresence$over(mouseX, mouseY);
		Identifier sprite = on
			? (over ? ServerListToggle.ON_HIGHLIGHTED : ServerListToggle.ON)
			: (over ? ServerListToggle.OFF_HIGHLIGHTED : ServerListToggle.OFF);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, this.serverpresence$x(), this.serverpresence$y(), ServerListToggle.WIDTH, ServerListToggle.HEIGHT);
		if (over) {
			graphics.requestCursor(CursorTypes.POINTING_HAND);
			graphics.setTooltipForNextFrame(List.of(
				Component.translatable(on ? "serverpresence.serverlist.on" : "serverpresence.serverlist.off").getVisualOrderText(),
				Component.translatable(on ? "serverpresence.serverlist.click_off" : "serverpresence.serverlist.click_on")
					.withColor(SERVERPRESENCE$GRAY).getVisualOrderText()
			), mouseX, mouseY);
		}
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void serverpresence$clickSwitch(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (this.serverpresence$over(event.x(), event.y()) && ServerListToggle.state(this.serverData) != null) {
			ServerListToggle.toggle(this.serverData);
			AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
			cir.setReturnValue(true);
		}
	}
}
