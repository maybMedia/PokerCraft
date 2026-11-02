package com.brait.pokercraft.entity;

import com.brait.pokercraft.block.PokerTableBlock;
import com.brait.pokercraft.block.PokerTableGeometry;
import com.brait.pokercraft.registry.ModBlocks;
import com.brait.pokercraft.registry.ModEntities;
import net.minecraft.block.BlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** An invisible, temporary vehicle; Minecraft handles sitting, syncing and sneak-to-dismount. */
public final class PokerSeatEntity extends Entity {
    private BlockPos tablePos = BlockPos.ORIGIN;
    private int seatIndex;

    public BlockPos getTablePos() { return tablePos; }
    public int getSeatIndex() { return seatIndex; }

    public PokerSeatEntity(EntityType<? extends PokerSeatEntity> type, World world) {
        super(type, world);
        noClip = true;
        setNoGravity(true);
    }

    public static boolean sit(World world, BlockPos tablePos, Direction facing, int index, PlayerEntity player) {
        if (player.isSneaking() || player.hasVehicle() || !player.isAlive()) return false;
        if (world.getBlockEntity(tablePos) instanceof com.brait.pokercraft.block.entity.PokerTableBlockEntity table
                && !table.canUseSeat(index, player.getUuid())) {
            player.sendMessage(Text.literal("That stool is reserved for a registered player."), true);
            return false;
        }
        Vec3d position = PokerTableGeometry.seatPosition(tablePos, facing, index);
        if (player.squaredDistanceTo(position) > 36) return false;
        for (PokerSeatEntity existing : world.getEntitiesByClass(PokerSeatEntity.class, new Box(tablePos).expand(5), Entity::isAlive)) {
            if (existing.tablePos.equals(tablePos) && existing.seatIndex == index) {
                if (existing.hasPassengers()) {
                    player.sendMessage(Text.translatable("message.pokercraft.seat_occupied"), true);
                    return false;
                }
                existing.discard();
            }
        }
        // Keep the seated player's head clear if something was added above the table later.
        if (!world.isSpaceEmpty(player, new Box(position.x - .3, position.y + .05, position.z - .3,
                position.x + .3, position.y + 1.8, position.z + .3))) {
            player.sendMessage(Text.translatable("message.pokercraft.seat_blocked"), true);
            return false;
        }
        PokerSeatEntity seat = new PokerSeatEntity(ModEntities.POKER_SEAT, world);
        seat.tablePos = tablePos.toImmutable();
        seat.seatIndex = index;
        seat.setPosition(position);
        Vec3d inward = Vec3d.ofBottomCenter(tablePos).subtract(position);
        float yaw = (float) Math.toDegrees(Math.atan2(-inward.x, inward.z));
        seat.setYaw(yaw);
        if (!world.spawnEntity(seat)) return false;
        if (!player.startRiding(seat, true)) {
            seat.discard();
            return false;
        }
        player.setYaw(yaw);
        player.setHeadYaw(yaw);
        player.setBodyYaw(yaw);
        player.sendMessage(Text.translatable("message.pokercraft.seated"), true);
        return true;
    }

    public static void removeSeats(World world, BlockPos tablePos) {
        for (PokerSeatEntity seat : world.getEntitiesByClass(PokerSeatEntity.class, new Box(tablePos).expand(5), Entity::isAlive)) {
            if (seat.tablePos.equals(tablePos)) {
                seat.removeAllPassengers();
                seat.discard();
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!getWorld().isClient) {
            BlockState state = getWorld().getBlockState(tablePos);
            if (!hasPassengers() || !state.isOf(ModBlocks.POKER_TABLE) || !PokerTableBlock.isCenter(state)) {
                removeAllPassengers();
                discard();
            }
        }
    }

    @Override
    protected Vec3d getPassengerAttachmentPos(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
        return Vec3d.ZERO;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) { return !hasPassengers() && passenger instanceof PlayerEntity; }

    @Override
    public Vec3d updatePassengerForDismount(LivingEntity passenger) {
        Vec3d outward = getPos().subtract(Vec3d.ofBottomCenter(tablePos)).multiply(1, 0, 1).normalize();
        for (double distance : new double[]{.85, 1.25, 1.75}) {
            Vec3d target = getPos().add(outward.multiply(distance));
            for (double y : new double[]{tablePos.getY(), tablePos.getY() + 1.0}) {
                Vec3d candidate = new Vec3d(target.x, y, target.z);
                BlockPos floor = BlockPos.ofFloored(candidate).down();
                if (getWorld().getBlockState(floor).isSideSolidFullSquare(getWorld(), floor, Direction.UP)
                        && getWorld().isSpaceEmpty(passenger, passenger.getDimensions(EntityPose.STANDING).getBoxAt(candidate))) return candidate;
            }
        }
        // The stool top remains a safe fallback when surrounding exits have been blocked.
        return getPos().add(0, .05, 0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {}

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        tablePos = BlockPos.fromLong(nbt.getLong("TablePos"));
        seatIndex = nbt.getInt("SeatIndex");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putLong("TablePos", tablePos.asLong());
        nbt.putInt("SeatIndex", seatIndex);
    }
}
