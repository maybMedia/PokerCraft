package com.brait.pokercraft.client;

import com.brait.pokercraft.block.PokerTableBlock;
import com.brait.pokercraft.block.PokerTableGeometry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** Tabletop card transforms, including the dealing animation. */
public final class PokerCardLayout {
    private static final int[] SLOT_PIXELS = {-53, -26, 0, 26, 53};
    public record Card(int face, Vec3d center, float yaw, float tilt, float scale,
                       int seat, int index, boolean community, float progress) {
    }
    private PokerCardLayout() {}
    public static List<Card> cards(BlockPos pos, NbtCompound state, Direction facing, float delta) {
        List<Card> cards = new ArrayList<>();
        float tableYaw = switch (facing) { case EAST -> -90; case SOUTH -> 180; case WEST -> 90; default -> 0; };
        int[] board = state.getIntArray("Board");
        for (int i = 0; i < board.length; i++) {
            float p = PokerTableEffects.board(pos, i, delta);
            if (p >= 0) cards.add(new Card(board[i], position(pos, facing, SLOT_PIXELS[i] / 64.0, 0, 0, p),
                    tableYaw + (1 - p) * 90, 0, .9F, -1, i, true, p));
        }
        for (NbtElement e : state.getList("Players", NbtElement.COMPOUND_TYPE)) {
            NbtCompound player = (NbtCompound) e;
            if (!player.getBoolean("InHand")) continue;
            int index = player.getInt("Seat");
            Vec3d seat = PokerTableGeometry.seatOffset(index, Direction.NORTH);
            float yaw = (float) Math.toDegrees(Math.atan2(seat.x, seat.z));
            double angle = Math.toRadians(yaw);
            for (int i = 0; i < 2; i++) {
                float p = PokerTableEffects.hole(pos, index, i, delta);
                if (p < 0) continue;
                double offset = i == 0 ? -.15 : .15;
                cards.add(new Card(player.getInt(i == 0 ? "First" : "Second"),
                        position(pos, facing, seat.x * .52 + Math.cos(angle) * offset, .20,
                                seat.z * .52 - Math.sin(angle) * offset, p),
                        tableYaw + yaw * p, 65 * p, .85F, index, i, false, p));
            }
        }
        return cards;
    }
    private static Vec3d position(BlockPos pos, Direction facing, double x, double y, double z, float p) {
        Vec3d local = PokerTableBlock.rotateOffset(x * p,
                .06 * (1 - p) + y * p + Math.sin(p * Math.PI) * .16, .53 * (1 - p) + z * p, facing);
        return new Vec3d(pos.getX() + .5, pos.getY() + .902, pos.getZ() + .5).add(local);
    }
}
