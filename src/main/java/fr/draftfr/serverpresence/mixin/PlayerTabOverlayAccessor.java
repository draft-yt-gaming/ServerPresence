package fr.draftfr.serverpresence.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerTabOverlay.class)
public interface PlayerTabOverlayAccessor {
	@Accessor("header")
	@Nullable Component serverpresence$header();

	@Accessor("footer")
	@Nullable Component serverpresence$footer();
}
