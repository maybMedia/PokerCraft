package com.brait.pokercraft.client.mixin;

import com.brait.pokercraft.client.PokerControls;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class PokerInputMixin {
    @Shadow private int itemUseCooldown;
    @Inject(method = "handleInputEvents", at = @At("HEAD"))
    private void pokercraft$keys(CallbackInfo ci) { PokerControls.consumeInventoryKeys((MinecraftClient) (Object) this); }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void pokercraft$use(CallbackInfo ci) {
        if (PokerControls.active()) { itemUseCooldown = 4; PokerControls.activate(); ci.cancel(); }
    }
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void pokercraft$attack(CallbackInfoReturnable<Boolean> cir) {
        if (PokerControls.active()) cir.setReturnValue(false);
    }
    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void pokercraft$break(boolean breaking, CallbackInfo ci) { if (PokerControls.active()) ci.cancel(); }
}
