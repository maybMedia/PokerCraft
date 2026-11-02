package com.brait.pokercraft.client.mixin;

import com.brait.pokercraft.client.PokerControls;
import net.minecraft.entity.player.PlayerInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerInventory.class)
public abstract class PokerScrollMixin {
    @Inject(method = "scrollInHotbar", at = @At("HEAD"), cancellable = true)
    private void pokercraft$scroll(double amount, CallbackInfo ci) {
        if (PokerControls.active()) { PokerControls.scroll(amount); ci.cancel(); }
    }
}
