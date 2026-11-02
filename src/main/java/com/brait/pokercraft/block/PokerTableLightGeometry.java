package com.brait.pokercraft.block;

import com.google.gson.*;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Selection and solid-body collision follow the exported Blockbench model. */
public final class PokerTableLightGeometry {
    private record Piece(Box box, boolean solid) {}
    private static final List<Piece> PIECES = new ArrayList<>();
    private static final Map<BlockState, VoxelShape> OUTLINES = new ConcurrentHashMap<>(), COLLISIONS = new ConcurrentHashMap<>();
    static {
        try (var stream = Objects.requireNonNull(PokerTableLightGeometry.class.getResourceAsStream("/assets/pokercraft/geo/poker_table_light.geo.json"));
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var bones = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
            for (var entry : bones) {
                JsonObject bone = entry.getAsJsonObject();
                if (!bone.has("cubes")) continue;
                boolean solid = !bone.get("name").getAsString().equals("suspension");
                for (var cube : bone.getAsJsonArray("cubes")) PIECES.add(new Piece(bounds(cube.getAsJsonObject()), solid));
            }
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    private PokerTableLightGeometry() {}
    private static Vec3d vector(JsonArray a) { return new Vec3d(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()); }
    private static Box bounds(JsonObject cube) {
        Vec3d origin = vector(cube.getAsJsonArray("origin")), size = vector(cube.getAsJsonArray("size"));
        Vec3d pivot = cube.has("pivot") ? vector(cube.getAsJsonArray("pivot")).multiply(-1, 1, 1) : Vec3d.ZERO;
        Vec3d angles = cube.has("rotation") ? vector(cube.getAsJsonArray("rotation")).multiply(-1, -1, 1).multiply(Math.PI / 180) : Vec3d.ZERO;
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (int mask = 0; mask < 8; mask++) {
            // Match GeckoLib: reflect the positions first, then rotate by (-X, -Y, +Z).
            Vec3d p = origin.add((mask & 1) == 0 ? 0 : size.x, (mask & 2) == 0 ? 0 : size.y, (mask & 4) == 0 ? 0 : size.z)
                    .multiply(-1, 1, 1).subtract(pivot);
            double x = p.x, y = p.y * Math.cos(angles.x) - p.z * Math.sin(angles.x), z = p.y * Math.sin(angles.x) + p.z * Math.cos(angles.x);
            double xx = x * Math.cos(angles.y) + z * Math.sin(angles.y), zz = -x * Math.sin(angles.y) + z * Math.cos(angles.y);
            p = new Vec3d(xx * Math.cos(angles.z) - y * Math.sin(angles.z), xx * Math.sin(angles.z) + y * Math.cos(angles.z), zz).add(pivot);
            p = new Vec3d(.5 + p.x / 16, p.y / 16, .5 + p.z / 16);
            minX = Math.min(minX, p.x); minY = Math.min(minY, p.y); minZ = Math.min(minZ, p.z);
            maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y); maxZ = Math.max(maxZ, p.z);
        }
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }
    public static VoxelShape shape(BlockState state, boolean collision) {
        return (collision ? COLLISIONS : OUTLINES).computeIfAbsent(state, key -> build(key, collision));
    }
    private static VoxelShape build(BlockState state, boolean collision) {
        Direction facing = state.get(PokerTableLightBlock.FACING);
        BlockPos offset = PokerTableBlock.rotateOffset(state.get(PokerTableLightBlock.PART_X) - 1, 0, facing).down(state.get(PokerTableLightBlock.LEVEL));
        Box cell = new Box(offset);
        VoxelShape shape = VoxelShapes.empty();
        for (Piece piece : PIECES) {
            if (collision && !piece.solid) continue;
            Box b = piece.box;
            Vec3d a = PokerTableBlock.rotateOffset(b.minX - .5, b.minY, b.minZ - .5, facing).add(.5, 0, .5);
            Vec3d z = PokerTableBlock.rotateOffset(b.maxX - .5, b.maxY, b.maxZ - .5, facing).add(.5, 0, .5);
            Box rotated = new Box(a, z);
            if (rotated.intersects(cell)) shape = VoxelShapes.union(shape, VoxelShapes.cuboid(rotated.intersection(cell).offset(-offset.getX(), -offset.getY(), -offset.getZ())));
        }
        return shape.simplify();
    }
}
