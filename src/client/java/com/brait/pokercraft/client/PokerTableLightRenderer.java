package com.brait.pokercraft.client;

import com.brait.pokercraft.block.entity.PokerTableLightBlockEntity;
import com.brait.pokercraft.PokerCraft;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public final class PokerTableLightRenderer extends GeoBlockRenderer<PokerTableLightBlockEntity> {
    // Eyes shading keeps the panel unshaded; opaque depth writes hide the hardware behind it.
    private static final RenderLayer DIFFUSER = RenderLayer.of("pokercraft_light_diffuser",
            VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS, 1536,
            false, false, RenderLayer.MultiPhaseParameters.builder()
                    .program(RenderPhase.EYES_PROGRAM)
                    .texture(new RenderPhase.Texture(PokerCraft.id("textures/block/poker_table_light.png"), false, false))
                    .transparency(RenderPhase.NO_TRANSPARENCY).cull(RenderPhase.DISABLE_CULLING)
                    .depthTest(RenderPhase.LEQUAL_DEPTH_TEST).writeMaskState(RenderPhase.ALL_MASK).build(false));
    public PokerTableLightRenderer() { super(new PokerTableLightModel()); }
    @Override public void renderRecursively(MatrixStack matrices, PokerTableLightBlockEntity light, GeoBone bone,
                                           RenderLayer layer, VertexConsumerProvider vertices, VertexConsumer buffer,
                                           boolean reRender, float delta, int packedLight, int overlay, int color) {
        if (bone.getName().equals("diffuser")) {
            super.renderRecursively(matrices, light, bone, DIFFUSER, vertices, vertices.getBuffer(DIFFUSER), reRender, delta,
                    LightmapTextureManager.MAX_LIGHT_COORDINATE, overlay, color);
        } else super.renderRecursively(matrices, light, bone, layer, vertices, buffer, reRender, delta, packedLight, overlay, color);
    }
    @Override protected void rotateBlock(Direction facing, MatrixStack matrices) {
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(switch (facing) {
            case EAST -> -90; case SOUTH -> 180; case WEST -> 90; default -> 0;
        }));
    }
    @Override public boolean rendersOutsideBoundingBox(PokerTableLightBlockEntity light) { return true; }
}
