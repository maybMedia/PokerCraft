package com.brait.pokercraft.block;

import com.brait.pokercraft.block.entity.PokerTableBlockEntity;
import com.brait.pokercraft.entity.PokerSeatEntity;
import com.brait.pokercraft.registry.ModBlockEntities;
import com.brait.pokercraft.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public final class PokerTableBlock extends BlockWithEntity {
    public static final MapCodec<PokerTableBlock> CODEC = createCodec(PokerTableBlock::new);
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final IntProperty PART_X = IntProperty.of("part_x", 0, 6);
    public static final IntProperty PART_Z = IntProperty.of("part_z", 0, 4);
    public static final IntProperty LEVEL = IntProperty.of("level", 0, 2);

    public PokerTableBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH)
                .with(PART_X, 3).with(PART_Z, 2).with(LEVEL, 0));
    }

    @Override
    public MapCodec<PokerTableBlock> getCodec() { return CODEC; }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART_X, PART_Z, LEVEL);
    }

    public static boolean isCenter(BlockState state) {
        return state.get(PART_X) == 3 && state.get(PART_Z) == 2 && state.get(LEVEL) == 0;
    }

    public static BlockPos centerPos(BlockPos pos, BlockState state) {
        BlockPos offset = rotateOffset(state.get(PART_X) - 3, state.get(PART_Z) - 2, state.get(FACING));
        return pos.add(-offset.getX(), -state.get(LEVEL), -offset.getZ());
    }

    public static BlockPos rotateOffset(int x, int z, Direction facing) {
        return switch (facing) {
            case SOUTH -> new BlockPos(-x, 0, -z);
            case EAST -> new BlockPos(-z, 0, x);
            case WEST -> new BlockPos(z, 0, -x);
            default -> new BlockPos(x, 0, z);
        };
    }

    public static Vec3d rotateOffset(double x, double y, double z, Direction facing) {
        return switch (facing) {
            case SOUTH -> new Vec3d(-x, y, -z);
            case EAST -> new Vec3d(-z, y, x);
            case WEST -> new Vec3d(z, y, -x);
            default -> new Vec3d(x, y, z);
        };
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        BlockState state = getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite());
        World world = context.getWorld();
        BlockPos center = context.getBlockPos();
        for (int x = -3; x <= 3; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos base = center.add(rotateOffset(x, z, state.get(FACING)));
                if (!world.getBlockState(base.down()).isSideSolidFullSquare(world, base.down(), Direction.UP)) return noRoom(context);
                for (int y = 0; y <= 2; y++) {
                    BlockPos part = base.up(y);
                    if (!world.isInBuildLimit(part) || !world.getWorldBorder().contains(part)
                            || !world.getBlockState(part).canReplace(context) || !world.getFluidState(part).isEmpty()) return noRoom(context);
                    BlockState partState = state.with(PART_X, x + 3).with(PART_Z, z + 2).with(LEVEL, y);
                    if (!world.doesNotIntersectEntities(null, PokerTableGeometry.shape(partState).offset(part.getX(), part.getY(), part.getZ()))) return noRoom(context);
                }
            }
        }
        return state;
    }

    private BlockState noRoom(ItemPlacementContext context) {
        if (context.getPlayer() != null && context.getWorld().isClient) {
            context.getPlayer().sendMessage(Text.translatable("message.pokercraft.no_room"), true);
        }
        return null;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (world.isClient || !isCenter(state)) return;
        for (int x = -3; x <= 3; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = 0; y <= 2; y++) {
                    if (x == 0 && z == 0 && y == 0) continue;
                    BlockPos part = pos.add(rotateOffset(x, z, state.get(FACING))).up(y);
                    world.setBlockState(part, state.with(PART_X, x + 3).with(PART_Z, z + 2).with(LEVEL, y), Block.NOTIFY_ALL);
                }
            }
        }
        if (world.getBlockEntity(pos) instanceof PokerTableBlockEntity table) {
            table.playPlacementAnimation();
        }
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient && !isCenter(state)) {
            BlockPos center = centerPos(pos, state);
            if (world.getBlockState(center).isOf(this)) world.breakBlock(center, !player.isCreative(), player);
        }
        return super.onBreak(world, pos, state, player);
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!world.isClient && !state.isOf(newState.getBlock())) {
            BlockPos center = centerPos(pos, state);
            if (isCenter(state)) {
                if (world.getBlockEntity(center) instanceof PokerTableBlockEntity table) table.onBroken();
                PokerSeatEntity.removeSeats(world, center);
                for (int x = -3; x <= 3; x++) {
                    for (int z = -2; z <= 2; z++) {
                        for (int y = 0; y <= 2; y++) {
                            BlockPos part = center.add(rotateOffset(x, z, state.get(FACING))).up(y);
                            BlockState other = world.getBlockState(part);
                            if (!part.equals(pos) && other.isOf(this) && centerPos(part, other).equals(center)) world.removeBlock(part, false);
                        }
                    }
                }
            } else if (world.getBlockState(center).isOf(this)) {
                world.breakBlock(center, true);
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        BlockPos center = centerPos(pos, state);
        if (player instanceof ServerPlayerEntity serverPlayer && world.getBlockEntity(center) instanceof PokerTableBlockEntity table
                && (player.getMainHandStack().isOf(ModItems.HOST_CHIP) || player.getOffHandStack().isOf(ModItems.HOST_CHIP))) {
            table.open(serverPlayer, true);
            return ActionResult.SUCCESS;
        }
        if (player instanceof ServerPlayerEntity serverPlayer && player.getVehicle() instanceof PokerSeatEntity occupied
                && occupied.getTablePos().equals(center) && world.getBlockEntity(center) instanceof PokerTableBlockEntity table) {
            table.open(serverPlayer, false);
            return ActionResult.SUCCESS;
        }
        int seat = PokerTableGeometry.seatAt(center, state.get(FACING), hit.getPos());
        if (seat < 0) {
            if (player instanceof ServerPlayerEntity serverPlayer && world.getBlockEntity(center) instanceof PokerTableBlockEntity table)
                table.open(serverPlayer, false);
        } else if (!world.isClient) {
            if (PokerSeatEntity.sit(world, center, state.get(FACING), seat, player)
                    && player instanceof ServerPlayerEntity serverPlayer && world.getBlockEntity(center) instanceof PokerTableBlockEntity table)
                table.handle(serverPlayer, "refresh", new net.minecraft.nbt.NbtCompound());
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return PokerTableGeometry.shape(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return PokerTableGeometry.shape(state);
    }

    @Override
    protected boolean canPathfindThrough(BlockState state, net.minecraft.entity.ai.pathing.NavigationType type) { return false; }

    @Override
    protected BlockRenderType getRenderType(BlockState state) { return BlockRenderType.ENTITYBLOCK_ANIMATED; }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return isCenter(state) ? new PokerTableBlockEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : validateTicker(type, ModBlockEntities.POKER_TABLE, PokerTableBlockEntity::serverTick);
    }

    // A multiblock cannot be rotated one cell at a time by structures or commands.
    @Override
    protected BlockState rotate(BlockState state, BlockRotation rotation) { return state; }

    @Override
    protected BlockState mirror(BlockState state, BlockMirror mirror) { return state; }
}
