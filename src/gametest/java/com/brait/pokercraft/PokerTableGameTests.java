package com.brait.pokercraft;

import com.brait.pokercraft.block.PokerTableBlock;
import com.brait.pokercraft.block.PokerTableGeometry;
import com.brait.pokercraft.entity.PokerSeatEntity;
import com.brait.pokercraft.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;

public class PokerTableGameTests implements FabricGameTest {
    private static final BlockPos CENTER = new BlockPos(3, 1, 3);

    private static void prepare(TestContext context) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) {
            context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
            for (int y = 1; y < 5; y++) context.setBlockState(new BlockPos(x, y, z), Blocks.AIR);
        }
    }

    private static PlayerEntity player(TestContext context, float yaw) {
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        player.setPosition(Vec3d.ofBottomCenter(context.getAbsolutePos(CENTER)).add(4, 0, 4));
        player.setYaw(yaw);
        return player;
    }

    private static ItemPlacementContext placement(TestContext context, PlayerEntity player) {
        BlockPos center = context.getAbsolutePos(CENTER);
        ItemStack stack = new ItemStack(ModBlocks.POKER_TABLE);
        return new ItemPlacementContext(context.getWorld(), player, Hand.MAIN_HAND, stack,
                new BlockHitResult(Vec3d.ofBottomCenter(center), Direction.UP, center.down(), false));
    }

    private static void place(TestContext context, PlayerEntity player) {
        ItemPlacementContext placement = placement(context, player);
        context.assertTrue(((BlockItem) ModBlocks.POKER_TABLE.asItem()).place(placement).isAccepted(), "Table should place");
    }

    @SuppressWarnings("unused") // Minecraft discovers GameTest methods through reflection.
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void placementAndCollisionInEveryDirection(TestContext context) {
        prepare(context);
        BlockPos center = context.getAbsolutePos(CENTER);
        for (float yaw : new float[]{0, 90, 180, 270}) {
            PlayerEntity player = player(context, yaw);
            place(context, player);
            BlockState anchor = context.getWorld().getBlockState(center);
            Direction facing = anchor.get(PokerTableBlock.FACING);
            context.assertEquals(facing, player.getHorizontalFacing().getOpposite(), "Placement facing");
            context.assertTrue(context.getWorld().getBlockEntity(center) != null, "Center must own the renderer");
            for (int x = -3; x <= 3; x++) for (int z = -2; z <= 2; z++) for (int y = 0; y < 3; y++) {
                BlockPos pos = center.add(PokerTableBlock.rotateOffset(x, z, facing)).up(y);
                BlockState part = context.getWorld().getBlockState(pos);
                context.assertTrue(part.isOf(ModBlocks.POKER_TABLE), "Entire footprint and headroom must be reserved");
                context.assertEquals(PokerTableBlock.centerPos(pos, part), center, "All parts point to their table");
            }
            for (int seat = 0; seat < 6; seat++) {
                Vec3d position = PokerTableGeometry.seatPosition(center, facing, seat);
                BlockPos stool = BlockPos.ofFloored(position);
                BlockState part = context.getWorld().getBlockState(stool);
                context.assertFalse(part.getCollisionShape(context.getWorld(), stool).isEmpty(), "Stool must have collision");
                context.assertEquals(PokerTableGeometry.seatAt(center, facing, position), seat, "Stool hit must resolve to its seat");
            }
            context.getWorld().breakBlock(center, false);
            context.assertTrue(context.getWorld().getBlockState(center).isAir(), "Table removal");
        }
        context.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rejectsObstructionsAndMissingFloor(TestContext context) {
        prepare(context);
        PlayerEntity player = player(context, 0);
        PokerTableBlock table = (PokerTableBlock) ModBlocks.POKER_TABLE;
        context.setBlockState(CENTER.add(3, 0, 0), Blocks.STONE);
        context.assertTrue(table.getPlacementState(placement(context, player)) == null, "Reject blocked footprint");
        context.setBlockState(CENTER.add(3, 0, 0), Blocks.AIR);
        context.setBlockState(CENTER.add(0, 2, 2), Blocks.STONE);
        context.assertTrue(table.getPlacementState(placement(context, player)) == null, "Reject blocked headroom");
        context.setBlockState(CENTER.add(0, 2, 2), Blocks.AIR);
        context.setBlockState(CENTER.add(-3, -1, 0), Blocks.AIR);
        context.assertTrue(table.getPlacementState(placement(context, player)) == null, "Reject missing support");
        context.setBlockState(CENTER.add(-3, -1, 0), Blocks.STONE);
        context.setBlockState(CENTER.add(3, 0, 0), Blocks.WATER);
        context.assertTrue(table.getPlacementState(placement(context, player)) == null, "Reject water");
        context.setBlockState(CENTER.add(3, 0, 0), Blocks.AIR);
        context.assertTrue(table.getPlacementState(placement(context, player)) != null, "Accept clear supported space");
        context.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sixIndependentSeatsAndBreakCleanup(TestContext context) {
        prepare(context);
        place(context, player(context, 0));
        BlockPos center = context.getAbsolutePos(CENTER);
        Direction facing = context.getWorld().getBlockState(center).get(PokerTableBlock.FACING);
        PlayerEntity[] players = new PlayerEntity[6];
        for (int i = 0; i < 6; i++) {
            players[i] = player(context, 0);
            players[i].setPosition(PokerTableGeometry.seatPosition(center, facing, i).add(0, 0, .8));
            Vec3d stoolHit = PokerTableGeometry.seatPosition(center, facing, i);
            BlockPos stool = BlockPos.ofFloored(stoolHit);
            context.getWorld().getBlockState(stool).onUse(context.getWorld(), players[i], new BlockHitResult(stoolHit, Direction.UP, stool, false));
            context.assertTrue(players[i].getVehicle() instanceof PokerSeatEntity, "Right-click should seat player " + i);
        }
        PlayerEntity other = player(context, 0);
        other.setPosition(players[0].getPos());
        context.assertFalse(PokerSeatEntity.sit(context.getWorld(), center, facing, 0, other), "Occupied stool must reject another player");
        Entity oldSeat = players[0].getVehicle();
        if (oldSeat == null) throw new AssertionError("First player must still occupy a seat before dismounting");
        players[0].stopRiding();
        context.assertFalse(players[0].hasVehicle(), "Player can dismount");
        oldSeat.tick();
        context.assertTrue(oldSeat.isRemoved(), "Empty seat must clean itself up");
        context.assertTrue(PokerSeatEntity.sit(context.getWorld(), center, facing, 0, other), "Vacated seat can be used again");
        BlockPos stool = BlockPos.ofFloored(PokerTableGeometry.seatPosition(center, facing, 5));
        context.getWorld().breakBlock(stool, true);
        for (PlayerEntity seated : players) context.assertFalse(seated.hasVehicle(), "Breaking table must dismount everyone");
        context.assertFalse(other.hasVehicle(), "Breaking table must dismount replacement player");
        context.assertTrue(context.getWorld().getEntitiesByClass(PokerSeatEntity.class, new Box(center).expand(5), Entity::isAlive).isEmpty(), "No orphan seat entities");
        for (int x = -3; x <= 3; x++) for (int z = -2; z <= 2; z++) for (int y = 0; y < 3; y++) {
            context.assertTrue(context.getWorld().getBlockState(center.add(x, y, z)).isAir(), "No orphan multiblock parts");
        }
        int drops = context.getWorld().getEntitiesByClass(ItemEntity.class, new Box(center).expand(5), Entity::isAlive)
                .stream().filter(item -> item.getStack().isOf(ModBlocks.POKER_TABLE.asItem())).mapToInt(item -> item.getStack().getCount()).sum();
        context.assertEquals(drops, 1, "Breaking a stool drops exactly one table");
        context.complete();
    }
}
