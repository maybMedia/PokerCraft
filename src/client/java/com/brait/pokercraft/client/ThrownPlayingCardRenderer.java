package com.brait.pokercraft.client;

import com.brait.pokercraft.entity.ThrownPlayingCardEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public final class ThrownPlayingCardRenderer extends EntityRenderer<ThrownPlayingCardEntity> {
    private final ItemRenderer itemRenderer;

    public ThrownPlayingCardRenderer(EntityRendererFactory.Context context) {
        super(context);
        itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(ThrownPlayingCardEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertices, int light) {
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(
                MathHelper.lerpAngleDegrees(tickDelta, entity.prevYaw, entity.getYaw())));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(
                -MathHelper.lerp(tickDelta, entity.prevPitch, entity.getPitch())));
        // The item model lies in X/Z. Spin around its thin Y axis like a thrown card.
        if (!entity.isStuck()) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((entity.age + tickDelta) * 42F));
        itemRenderer.renderItem(entity.getCardStack(), ModelTransformationMode.NONE, light, OverlayTexture.DEFAULT_UV,
                matrices, vertices, entity.getWorld(), entity.getId());
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertices, light);
    }

    @Override
    public Identifier getTexture(ThrownPlayingCardEntity entity) {
        return PlayerScreenHandler.BLOCK_ATLAS_TEXTURE;
    }
}
