package com.brait.pokercraft.client.mixin;

import com.brait.pokercraft.registry.ModItems;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Shadow
    protected abstract void renderArmHoldingItem(MatrixStack matrices, VertexConsumerProvider vertices,
                                                 int light, float equipProgress, float swingProgress, Arm arm);

    @Shadow
    public abstract void renderItem(LivingEntity entity, ItemStack stack, ModelTransformationMode mode,
                                    boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vertices, int light);

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void pokercraft$renderHeldChip(AbstractClientPlayerEntity player, float tickDelta, float pitch,
                                            Hand hand, float swingProgress, ItemStack stack, float equipProgress,
                                            MatrixStack matrices, VertexConsumerProvider vertices, int light,
                                            CallbackInfo ci) {
        if (com.brait.pokercraft.client.PokerControls.active()) { ci.cancel(); return; }
        if (!(stack.isOf(ModItems.WHITE_CHIP) || stack.isOf(ModItems.RED_CHIP)
                || stack.isOf(ModItems.BLUE_CHIP) || stack.isOf(ModItems.GREEN_CHIP)
                || stack.isOf(ModItems.HOST_CHIP))
                || player.isUsingSpyglass()) {
            return;
        }

        Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        boolean leftHanded = arm == Arm.LEFT;
        float side = leftHanded ? -1.0F : 1.0F;
        float swing = MathHelper.sin(MathHelper.sqrt(swingProgress) * MathHelper.PI);
        matrices.push();
        // Apply the empty-hand animation to both the arm and chip around the same grip point.
        matrices.translate(side * (0.64F - 0.3F * swing),
                -0.6F - 0.6F * equipProgress
                        + 0.4F * MathHelper.sin(MathHelper.sqrt(swingProgress) * MathHelper.TAU),
                -0.72F - 0.4F * MathHelper.sin(swingProgress * MathHelper.PI));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 70.0F * swing));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(
                side * -20.0F * MathHelper.sin(swingProgress * swingProgress * MathHelper.PI)));
        matrices.translate(-side * 0.64F, 0.6F, 0.72F);

        if (!player.isInvisible()) {
            matrices.push();
            renderArmHoldingItem(matrices, vertices, light, 0.0F, 0.0F, arm);
            matrices.pop();
        }

        // Position the chip at the fingertips, forward of the wrist and slightly toward the palm.
        matrices.translate(side * 0.58F, -0.35F, -0.95F);
        renderItem(player, stack, leftHanded ? ModelTransformationMode.FIRST_PERSON_LEFT_HAND
                        : ModelTransformationMode.FIRST_PERSON_RIGHT_HAND,
                leftHanded, matrices, vertices, light);
        matrices.pop();
        ci.cancel();
    }
}
