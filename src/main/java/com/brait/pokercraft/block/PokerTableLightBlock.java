package com.brait.pokercraft.block;

import com.brait.pokercraft.block.entity.PokerTableLightBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

/** A three-cell-wide pendant with two vertical cells and a single ceiling attachment. */
public final class PokerTableLightBlock extends BlockWithEntity {
    public static final MapCodec<PokerTableLightBlock> CODEC = createCodec(PokerTableLightBlock::new);
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final IntProperty PART_X = IntProperty.of("part_x", 0, 2);
    public static final IntProperty LEVEL = IntProperty.of("level", 0, 1);

    public PokerTableLightBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(PART_X, 1).with(LEVEL, 0));
    }
    @Override public MapCodec<PokerTableLightBlock> getCodec() { return CODEC; }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING, PART_X, LEVEL); }
    public static boolean isAnchor(BlockState state) { return state.get(PART_X) == 1 && state.get(LEVEL) == 0; }
    public static BlockPos anchorPos(BlockPos pos, BlockState state) {
        BlockPos offset = PokerTableBlock.rotateOffset(state.get(PART_X) - 1, 0, state.get(FACING));
        return pos.subtract(offset).up(state.get(LEVEL));
    }
    @Override public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        BlockPos ceiling = anchorPos(pos, state).up();
        return world.getBlockState(ceiling).isSideSolidFullSquare(world, ceiling, Direction.DOWN);
    }
    @Override public BlockState getPlacementState(ItemPlacementContext context) {
        BlockState state = getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite());
        World world = context.getWorld(); BlockPos anchor = context.getBlockPos();
        if (context.getSide() != Direction.DOWN || !canPlaceAt(state, world, anchor)) return invalidPlacement(context);
        for (int x = -1; x <= 1; x++) for (int level = 0; level < 2; level++) {
            BlockPos part = anchor.add(PokerTableBlock.rotateOffset(x, 0, state.get(FACING))).down(level);
            BlockState partState = state.with(PART_X, x + 1).with(LEVEL, level);
            if (!world.isInBuildLimit(part) || !world.getWorldBorder().contains(part)
                    || !world.getBlockState(part).canReplace(context) || !world.getFluidState(part).isEmpty()
                    || !world.doesNotIntersectEntities(null, PokerTableLightGeometry.shape(partState, true).offset(part.getX(), part.getY(), part.getZ())))
                return invalidPlacement(context);
        }
        return state;
    }
    private BlockState invalidPlacement(ItemPlacementContext context) {
        if (context.getPlayer() != null && context.getWorld().isClient)
            context.getPlayer().sendMessage(Text.translatable("message.pokercraft.light_no_room"), true);
        return null;
    }
    @Override public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (world.isClient || !isAnchor(state)) return;
        for (int x = -1; x <= 1; x++) for (int level = 0; level < 2; level++) {
            if (x == 0 && level == 0) continue;
            BlockPos part = pos.add(PokerTableBlock.rotateOffset(x, 0, state.get(FACING))).down(level);
            world.setBlockState(part, state.with(PART_X, x + 1).with(LEVEL, level), Block.NOTIFY_ALL);
        }
    }
    @Override protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (!world.isClient && isAnchor(state) && !canPlaceAt(state, world, pos)) world.breakBlock(pos, true);
    }
    @Override public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient && !isAnchor(state)) {
            BlockPos anchor = anchorPos(pos, state);
            if (world.getBlockState(anchor).isOf(this)) world.breakBlock(anchor, !player.isCreative(), player);
        }
        return super.onBreak(world, pos, state, player);
    }
    @Override protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState next, boolean moved) {
        if (!world.isClient && !state.isOf(next.getBlock())) {
            BlockPos anchor = anchorPos(pos, state);
            if (isAnchor(state)) {
                for (int x = -1; x <= 1; x++) for (int level = 0; level < 2; level++) {
                    BlockPos part = anchor.add(PokerTableBlock.rotateOffset(x, 0, state.get(FACING))).down(level);
                    BlockState other = world.getBlockState(part);
                    if (!part.equals(pos) && other.isOf(this) && anchorPos(part, other).equals(anchor)) world.removeBlock(part, false);
                }
            } else if (world.getBlockState(anchor).isOf(this)) world.breakBlock(anchor, true);
        }
        super.onStateReplaced(state, world, pos, next, moved);
    }
    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return PokerTableLightGeometry.shape(state, false);
    }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return PokerTableLightGeometry.shape(state, true);
    }
    @Override protected BlockRenderType getRenderType(BlockState state) { return BlockRenderType.ENTITYBLOCK_ANIMATED; }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return isAnchor(state) ? new PokerTableLightBlockEntity(pos, state) : null; }
    @Override protected BlockState rotate(BlockState state, BlockRotation rotation) { return state; }
    @Override protected BlockState mirror(BlockState state, BlockMirror mirror) { return state; }
}
