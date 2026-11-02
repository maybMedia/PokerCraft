package com.brait.pokercraft;

import com.brait.pokercraft.block.PokerTableBlock;
import com.brait.pokercraft.block.PokerTableLightBlock;
import com.brait.pokercraft.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;

public final class PokerTableLightGameTests implements FabricGameTest {
    private static final BlockPos ANCHOR = new BlockPos(3, 4, 3);
    private static void prepare(TestContext c) {
        for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) for (int y = 1; y < 7; y++) c.setBlockState(new BlockPos(x, y, z), Blocks.AIR);
        c.setBlockState(ANCHOR.up(), Blocks.STONE);
    }
    private static PlayerEntity player(TestContext c, float yaw) {
        var player = c.createMockPlayer(GameMode.SURVIVAL);
        player.setPosition(c.getAbsolutePos(ANCHOR).toCenterPos().add(4, -2, 4)); player.setYaw(yaw); return player;
    }
    private static ItemPlacementContext placement(TestContext c, PlayerEntity player) {
        BlockPos ceiling = c.getAbsolutePos(ANCHOR.up());
        return new ItemPlacementContext(c.getWorld(), player, Hand.MAIN_HAND, new ItemStack(ModBlocks.POKER_TABLE_LIGHT),
                new BlockHitResult(new Vec3d(ceiling.getX() + .5, ceiling.getY(), ceiling.getZ() + .5), Direction.DOWN, ceiling, false));
    }
    private static void place(TestContext c, PlayerEntity player) {
        c.assertTrue(((BlockItem) ModBlocks.POKER_TABLE_LIGHT.asItem()).place(placement(c, player)).isAccepted(), "Pendant should attach beneath its ceiling");
    }
    private static void assertRemoved(TestContext c, BlockPos anchor, Direction facing) {
        for (int x = -1; x <= 1; x++) for (int level = 0; level < 2; level++)
            c.assertTrue(c.getWorld().getBlockState(anchor.add(PokerTableBlock.rotateOffset(x, 0, facing)).down(level)).isAir(), "No orphan light parts remain");
    }
    private static long drops(TestContext c, BlockPos pos) {
        return c.getWorld().getEntitiesByClass(ItemEntity.class, new Box(pos).expand(3), e -> e.getStack().isOf(ModBlocks.POKER_TABLE_LIGHT.asItem()))
                .stream().mapToLong(e -> e.getStack().getCount()).sum();
    }
    @SuppressWarnings("unused") // Minecraft discovers GameTest methods through reflection.
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void ceilingPlacementRotatesAndLightsTheFullDiffuser(TestContext c) {
        prepare(c); BlockPos anchor = c.getAbsolutePos(ANCHOR);
        for (float yaw : new float[]{0, 90, 180, 270}) {
            var player = player(c, yaw); place(c, player);
            var state = c.getWorld().getBlockState(anchor); Direction facing = state.get(PokerTableLightBlock.FACING);
            c.assertEquals(facing, player.getHorizontalFacing().getOpposite(), "Pendant follows placement facing");
            for (int x = -1; x <= 1; x++) for (int level = 0; level < 2; level++) {
                BlockPos pos = anchor.add(PokerTableBlock.rotateOffset(x, 0, facing)).down(level);
                BlockState part = c.getWorld().getBlockState(pos);
                c.assertTrue(part.isOf(ModBlocks.POKER_TABLE_LIGHT), "All six pendant cells are placed");
                c.assertEquals(PokerTableLightBlock.anchorPos(pos, part), anchor, "Every pendant part finds its anchor");
                c.assertEquals(c.getWorld().getBlockEntity(pos) != null, x == 0 && level == 0, "Only the ceiling anchor owns a renderer");
                c.assertFalse(part.getOutlineShape(c.getWorld(), pos).isEmpty(), "Housing and wire selection: part " + x + ", level " + level + ", facing " + facing);
                if (level == 1) {
                    c.assertFalse(part.getCollisionShape(c.getWorld(), pos).isEmpty(), "The housing has real collision");
                    c.assertEquals(part.getLuminance(), 15, "The full diffuser emits maximum block light");
                }
            }
            c.getWorld().breakBlock(anchor, false); assertRemoved(c, anchor, facing);
        }
        c.complete();
    }
    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rejectsMissingCeilingObstructionsAndFluids(TestContext c) {
        prepare(c); var player = player(c, 0); var light = (PokerTableLightBlock) ModBlocks.POKER_TABLE_LIGHT;
        c.setBlockState(ANCHOR.up(), Blocks.AIR);
        c.assertTrue(light.getPlacementState(placement(c, player)) == null, "A pendant needs a solid ceiling");
        c.setBlockState(ANCHOR.up(), Blocks.STONE); c.setBlockState(ANCHOR.down(), Blocks.STONE);
        c.assertTrue(light.getPlacementState(placement(c, player)) == null, "Do not overwrite blocks beneath the ceiling");
        c.setBlockState(ANCHOR.down(), Blocks.WATER);
        c.assertTrue(light.getPlacementState(placement(c, player)) == null, "Do not place into water");
        c.setBlockState(ANCHOR.down(), Blocks.AIR);
        BlockPos floor = c.getAbsolutePos(ANCHOR.down()); c.getWorld().setBlockState(floor, Blocks.STONE.getDefaultState());
        var upward = new ItemPlacementContext(c.getWorld(), player, Hand.MAIN_HAND, new ItemStack(ModBlocks.POKER_TABLE_LIGHT),
                new BlockHitResult(floor.toCenterPos().add(0, .5, 0), Direction.UP, floor, false));
        c.assertTrue(light.getPlacementState(upward) == null, "A pendant cannot be placed on the floor");
        c.complete();
    }
    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void breakingHousingDropsOnlyOneLight(TestContext c) {
        prepare(c); place(c, player(c, 0)); BlockPos anchor = c.getAbsolutePos(ANCHOR);
        Direction facing = c.getWorld().getBlockState(anchor).get(PokerTableLightBlock.FACING);
        c.getWorld().breakBlock(anchor.add(PokerTableBlock.rotateOffset(-1, 0, facing)).down(), true);
        assertRemoved(c, anchor, facing); c.assertEquals(drops(c, anchor), 1L, "A six-part pendant drops a single item"); c.complete();
    }
    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void removingRoofDetachesAndDropsTheLight(TestContext c) {
        prepare(c); place(c, player(c, 0)); BlockPos anchor = c.getAbsolutePos(ANCHOR);
        Direction facing = c.getWorld().getBlockState(anchor).get(PokerTableLightBlock.FACING);
        c.getWorld().setBlockState(anchor.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        assertRemoved(c, anchor, facing); c.assertEquals(drops(c, anchor), 1L, "Lost ceiling support drops the pendant once"); c.complete();
    }
}
