package com.brait.pokercraft.block;

import com.google.gson.*;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Collision and seat locations come from the same geometry used by GeckoLib. */
public final class PokerTableGeometry {
    private static final List<Box> BOXES = new ArrayList<>();
    private static final List<Vec3d> SEATS = new ArrayList<>();
    private static final Vec3d[] CHIP_BANKS = new Vec3d[6];
    private static final Map<BlockState, VoxelShape> SHAPES = new java.util.concurrent.ConcurrentHashMap<>();

    static {
        try (InputStream stream = Objects.requireNonNull(PokerTableGeometry.class.getResourceAsStream(
                "/assets/pokercraft/geo/six_seat_poker_table.geo.json"));
             Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonArray bones = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("minecraft:geometry")
                    .get(0).getAsJsonObject().getAsJsonArray("bones");
            for (JsonElement element : bones) {
                JsonObject bone = element.getAsJsonObject();
                String name = bone.get("name").getAsString();
                if (name.startsWith("chip_bank_")) {
                    int seat = Integer.parseInt(name.substring("chip_bank_".length()));
                    JsonArray pivot = bone.getAsJsonArray("pivot");
                    CHIP_BANKS[seat] = new Vec3d(-pivot.get(0).getAsDouble() / 16,
                            pivot.get(1).getAsDouble() / 16, pivot.get(2).getAsDouble() / 16);
                }
                if (name.startsWith("stool_")) {
                    JsonArray pivot = bone.getAsJsonArray("pivot");
                    double top = 0;
                    for (JsonElement cubeElement : bone.getAsJsonArray("cubes")) {
                        JsonObject cube = cubeElement.getAsJsonObject();
                        top = Math.max(top, cube.getAsJsonArray("origin").get(1).getAsDouble()
                                + cube.getAsJsonArray("size").get(1).getAsDouble());
                    }
                    // GeckoLib reflects X when baking Bedrock geometry.
                    SEATS.add(new Vec3d(-pivot.get(0).getAsDouble() / 16, top / 16, pivot.get(2).getAsDouble() / 16));
                }
                if (!name.equals("table") && !name.startsWith("stool_")) continue;
                for (JsonElement cubeElement : bone.getAsJsonArray("cubes")) {
                    JsonObject cube = cubeElement.getAsJsonObject();
                    JsonArray origin = cube.getAsJsonArray("origin"), size = cube.getAsJsonArray("size");
                    double x = origin.get(0).getAsDouble(), y = origin.get(1).getAsDouble(), z = origin.get(2).getAsDouble();
                    BOXES.add(new Box(.5 - (x + size.get(0).getAsDouble()) / 16, y / 16, .5 + z / 16,
                            .5 - x / 16, (y + size.get(1).getAsDouble()) / 16, .5 + (z + size.get(2).getAsDouble()) / 16));
                }
            }
            // Model bone names are not arranged around the table. Seat IDs must be clockwise
            // so the button, blinds, betting order and odd-chip awards follow physical stools.
            Vec3d firstSeat = SEATS.getFirst();
            double startAngle = Math.atan2(firstSeat.x, -firstSeat.z);
            SEATS.sort(Comparator.comparingDouble(seat ->
                    (Math.atan2(seat.x, -seat.z) - startAngle + Math.PI * 2) % (Math.PI * 2)));
        } catch (IOException | RuntimeException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private PokerTableGeometry() {}

    public static Vec3d seatOffset(int index, Direction facing) {
        Vec3d seat = SEATS.get(index);
        return PokerTableBlock.rotateOffset(seat.x, seat.y, seat.z, facing);
    }

    public static Vec3d chipBankOffset(int seat, Direction facing) {
        Vec3d bank = Objects.requireNonNull(CHIP_BANKS[seat], "Missing chip bank for seat " + seat);
        return PokerTableBlock.rotateOffset(bank.x, bank.y, bank.z, facing);
    }

    public static Vec3d seatPosition(BlockPos center, Direction facing, int seat) {
        return Vec3d.ofBottomCenter(center).add(seatOffset(seat, facing));
    }

    public static int seatAt(BlockPos center, Direction facing, Vec3d hit) {
        for (int i = 0; i < SEATS.size(); i++) {
            Vec3d seat = seatPosition(center, facing, i);
            if (Math.abs(hit.x - seat.x) <= .4 && Math.abs(hit.z - seat.z) <= .4
                    && hit.y <= center.getY() + .7) return i;
        }
        return -1;
    }

    public static VoxelShape shape(BlockState state) {
        return SHAPES.computeIfAbsent(state, PokerTableGeometry::buildShape);
    }

    private static VoxelShape buildShape(BlockState state) {
        Direction facing = state.get(PokerTableBlock.FACING);
        BlockPos part = PokerTableBlock.rotateOffset(state.get(PokerTableBlock.PART_X) - 3,
                state.get(PokerTableBlock.PART_Z) - 2, facing).up(state.get(PokerTableBlock.LEVEL));
        Box cell = new Box(part);
        VoxelShape result = VoxelShapes.empty();
        for (Box box : BOXES) {
            Vec3d a = PokerTableBlock.rotateOffset(box.minX - .5, box.minY, box.minZ - .5, facing).add(.5, 0, .5);
            Vec3d b = PokerTableBlock.rotateOffset(box.maxX - .5, box.maxY, box.maxZ - .5, facing).add(.5, 0, .5);
            Box rotated = new Box(a, b);
            if (rotated.intersects(cell)) {
                result = VoxelShapes.union(result, VoxelShapes.cuboid(rotated.intersection(cell).offset(-part.getX(), -part.getY(), -part.getZ())));
            }
        }
        return result.simplify();
    }
}
