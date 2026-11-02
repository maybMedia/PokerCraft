package com.brait.pokercraft.client;

import com.brait.pokercraft.block.entity.PokerTableBlockEntity;
import com.brait.pokercraft.block.PokerTableGeometry;
import com.brait.pokercraft.block.PokerTableBlock;
import com.brait.pokercraft.poker.PokerConfig;
import com.brait.pokercraft.poker.PokerStackDisplay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.cache.object.BakedGeoModel;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public final class PokerTableRenderer extends GeoBlockRenderer<PokerTableBlockEntity> {
    private final Function<PokerTableBlockEntity, NbtCompound> snapshots;
    public PokerTableRenderer() { this(table -> table.getWorld() == null ? null : PokerClient.table(table.getPos())); }
    public PokerTableRenderer(Function<PokerTableBlockEntity, NbtCompound> snapshots) {
        super(new PokerTableModel()); this.snapshots = snapshots;
    }

    @Override
    public void preRender(MatrixStack matrices, PokerTableBlockEntity table, BakedGeoModel model,
                          VertexConsumerProvider vertices, VertexConsumer buffer, boolean reRender,
                          float delta, int light, int overlay, int color) {
        super.preRender(matrices, table, model, vertices, buffer, reRender, delta, light, overlay, color);
        NbtCompound state = snapshots.apply(table);
        int[][] visible = new int[6][4];
        if (state != null) {
            PokerConfig config = PokerConfig.fromNbt(state.getCompound("Config"));
            for (NbtElement e : state.getList("Players", NbtElement.COMPOUND_TYPE)) {
                NbtCompound player = (NbtCompound) e;
                for (var pile : PokerStackDisplay.piles(player.getLong("Stack"), config))
                    visible[player.getInt("Seat")][pile.color()] = pile.visibleCount();
            }
        }
        // Models are shared between tables and item previews: reset every chip each render.
        for (int seat = 0; seat < 6; seat++) for (int chipColor = 0; chipColor < 4; chipColor++)
            for (int level = 0; level < PokerStackDisplay.MAX_VISIBLE; level++) {
                var bone = getGeoModel().getBone(PokerStackDisplay.boneName(seat, chipColor, level));
                if (bone.isPresent()) bone.get().setHidden(level >= visible[seat][chipColor]);
            }
    }

    @Override
    public void renderFinal(MatrixStack matrices, PokerTableBlockEntity table, BakedGeoModel model,
                            VertexConsumerProvider vertices, VertexConsumer buffer, float tickDelta,
                            int light, int overlay, int color) {
        if (table.getWorld() == null) return;
        NbtCompound state = snapshots.apply(table);
        if (state == null) return;
        renderCards(table, state, tickDelta, matrices, vertices, light);
        renderChipCounts(table, state, matrices, vertices);
    }

    public void renderCards(PokerTableBlockEntity table, NbtCompound state, float tickDelta,
                            MatrixStack matrices, VertexConsumerProvider vertices, int light) {
        Direction facing = table.getCachedState().get(com.brait.pokercraft.block.PokerTableBlock.FACING);
        for (PokerCardLayout.Card card : PokerCardLayout.cards(table.getPos(), state, facing, tickDelta)) {
            matrices.push();
            matrices.translate(card.center().x - table.getPos().getX(), card.center().y - table.getPos().getY(), card.center().z - table.getPos().getZ());
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(card.yaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(card.tilt()));
            renderCard(card.face(), card.scale(), matrices, vertices, light, table);
            matrices.pop();
        }
    }

    private void renderCard(int face, float scale, MatrixStack matrices, VertexConsumerProvider vertices, int light, PokerTableBlockEntity table) {
        matrices.scale(scale, scale, scale);
        MinecraftClient.getInstance().getItemRenderer().renderItem(PokerScreen.card(face), ModelTransformationMode.NONE,
                light, OverlayTexture.DEFAULT_UV, matrices, vertices, table.getWorld(), face);
    }

    public void renderChipCounts(PokerTableBlockEntity table, NbtCompound state, MatrixStack matrices, VertexConsumerProvider vertices) {
        var client = MinecraftClient.getInstance();
        PokerConfig config = PokerConfig.fromNbt(state.getCompound("Config"));
        Direction facing = table.getCachedState().get(PokerTableBlock.FACING);
        int[] colors = {0xFFF0E5CA, 0xFFFF9292, 0xFF93C6FF, 0xFF96E2AE};
        for (NbtElement e : state.getList("Players", NbtElement.COMPOUND_TYPE)) {
            NbtCompound player = (NbtCompound) e;
            List<PokerStackDisplay.Pile> piles = PokerStackDisplay.piles(player.getLong("Stack"), config);
            if (piles.stream().noneMatch(PokerStackDisplay.Pile::needsCountLabel)) continue;
            var bank = PokerTableGeometry.chipBankOffset(player.getInt("Seat"), facing);
            int height = piles.stream().mapToInt(PokerStackDisplay.Pile::visibleCount).max().orElse(0);
            matrices.push();
            matrices.translate(.5 + bank.x, bank.y + height * .42 / 16 + .14, .5 + bank.z);
            matrices.multiply(client.gameRenderer.getCamera().getRotation());
            matrices.scale(-.014F, -.014F, .014F);
            int line = 0;
            for (var pile : piles) if (pile.needsCountLabel()) {
                String label = String.format(Locale.ROOT, "%,d x %,d", pile.count(), pile.denomination());
                client.textRenderer.draw(label, -client.textRenderer.getWidth(label) / 2F, -line++ * 10,
                        colors[pile.color()], false, matrices.peek().getPositionMatrix(), vertices,
                        TextRenderer.TextLayerType.NORMAL, 0xB014241C, LightmapTextureManager.MAX_LIGHT_COORDINATE);
            }
            matrices.pop();
        }
    }

    @Override
    protected void rotateBlock(Direction facing, MatrixStack matrices) {
        float degrees = switch (facing) {
            case EAST -> -90;
            case SOUTH -> 180;
            case WEST -> 90;
            default -> 0;
        };
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(degrees));
    }

    @Override
    public boolean rendersOutsideBoundingBox(PokerTableBlockEntity table) { return true; }
}
