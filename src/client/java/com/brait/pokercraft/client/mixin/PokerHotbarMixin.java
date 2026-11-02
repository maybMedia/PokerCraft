package com.brait.pokercraft.client.mixin;

import com.brait.pokercraft.client.PokerControls;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class PokerHotbarMixin {
    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void pokercraft$hotbar(DrawContext context, RenderTickCounter ticks, CallbackInfo ci) {
        if (PokerControls.active()) { PokerControls.render(context); ci.cancel(); }
    }
    @Inject(method = "renderHeldItemTooltip", at = @At("HEAD"), cancellable = true)
    private void pokercraft$tooltip(DrawContext context, CallbackInfo ci) { if (PokerControls.active()) ci.cancel(); }
}
