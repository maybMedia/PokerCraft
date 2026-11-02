package com.brait.pokercraft.client;

import com.brait.pokercraft.block.PokerTableBlock;
import com.brait.pokercraft.block.PokerTableGeometry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/** Visual events derive from personalized snapshots; this class never receives a private deck. */
public final class PokerTableEffects {
    private static final Map<BlockPos, Motion> MOTIONS = new HashMap<>();
    private static long lastEffectTick = Long.MIN_VALUE;
    private static final Vector3f[] COLORS = {new Vector3f(1, .75f, .15f), new Vector3f(.95f, .2f, .4f),
            new Vector3f(.2f, .8f, 1), new Vector3f(.3f, 1, .5f)};
    private static final class Motion {
        long dealStart = -1000, winStart = -1000;
        final long[] boardStart = {-1000, -1000, -1000, -1000, -1000};
        final int[] rank = {-1, -1, -1, -1, -1, -1};
        int count;
        int[] winners = new int[0];
    }
    private PokerTableEffects() {}
    public static void clear() { MOTIONS.clear(); lastEffectTick = Long.MIN_VALUE; }
    public static void remove(BlockPos pos) { MOTIONS.remove(pos); }
    public static void snapshot(BlockPos pos, NbtCompound previous, NbtCompound next) {
        var world = MinecraftClient.getInstance().world;
        if (world == null) return;
        long now = world.getTime();
        Motion motion = MOTIONS.computeIfAbsent(pos, ignored -> new Motion());
        if (previous == null) return; // Joining a table midway through a hand does not redeal it.
        if (previous.getLong("HandId") != next.getLong("HandId")) {
            motion.dealStart = now; motion.count = 0;
            java.util.Arrays.fill(motion.rank, -1);
            java.util.Arrays.fill(motion.boardStart, now - 1000);
            for (int i = 1; i <= 6; i++) {
                int seat = Math.floorMod(next.getInt("Dealer") + i, 6);
                for (NbtElement e : next.getList("Players", NbtElement.COMPOUND_TYPE)) {
                    NbtCompound p = (NbtCompound) e;
                    if (p.getInt("Seat") == seat && p.getBoolean("InHand")) motion.rank[seat] = motion.count++;
                }
            }
        }
        int oldBoard = previous.getLong("HandId") == next.getLong("HandId") ? previous.getIntArray("Board").length : 0;
        for (int i = oldBoard; i < next.getIntArray("Board").length; i++) motion.boardStart[i] = now + (i - oldBoard) * 3L;
        if (next.getIntArray("Winners").length > 0 && (previous.getIntArray("Winners").length == 0
                || previous.getLong("HandId") != next.getLong("HandId"))) {
            motion.winStart = now; motion.winners = next.getIntArray("Winners");
        }
    }
    private static float progress(long start, float delta) {
        var world = MinecraftClient.getInstance().world;
        if (world == null) return 1;
        float t = (world.getTime() - start + delta) / 8F;
        if (t < 0) return -1;
        t = Math.min(1, t);
        return t * t * (3 - 2 * t);
    }
    public static float hole(BlockPos pos, int seat, int card, float delta) {
        Motion m = MOTIONS.get(pos);
        return m == null || m.rank[seat] < 0 ? 1 : progress(m.dealStart + ((long) card * m.count + m.rank[seat]) * 2L, delta);
    }
    public static float board(BlockPos pos, int card, float delta) {
        Motion m = MOTIONS.get(pos);
        return m == null ? 1 : progress(m.boardStart[card], delta);
    }
    public static void tick(MinecraftClient client) {
        if (client.world == null || client.player == null) return;
        long now = client.world.getTime();
        if (now == lastEffectTick) return;
        lastEffectTick = now;
        MOTIONS.entrySet().removeIf(e -> client.player.squaredDistanceTo(e.getKey().toCenterPos()) > 16384);
        if (now % 2 != 0) return;
        for (var entry : MOTIONS.entrySet()) {
            Motion m = entry.getValue();
            if (now < m.winStart || now - m.winStart > 45) continue;
            var state = client.world.getBlockState(entry.getKey());
            if (!state.contains(PokerTableBlock.FACING)) continue;
            for (int seat : m.winners) {
                var origin = PokerTableGeometry.seatPosition(entry.getKey(), state.get(PokerTableBlock.FACING), seat);
                for (int i = 0; i < 5; i++) {
                    double angle = (now - m.winStart) * .24 + i * Math.PI * 2 / 5;
                    double x = origin.x + Math.cos(angle) * .65, z = origin.z + Math.sin(angle) * .65;
                    double y = origin.y + .8 + (now - m.winStart) * .018;
                    client.world.addParticle(new DustParticleEffect(COLORS[(i + (int) now / 2) % COLORS.length], .9F),
                            x, y, z, Math.cos(angle) * .03, .05, Math.sin(angle) * .03);
                    client.world.addParticle(ParticleTypes.FIREWORK, x, y + .3, z, Math.cos(angle) * .08, .10, Math.sin(angle) * .08);
                }
            }
        }
    }
}
