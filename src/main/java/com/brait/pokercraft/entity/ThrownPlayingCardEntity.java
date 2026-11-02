package com.brait.pokercraft.entity;

import com.brait.pokercraft.registry.ModItems;
import com.brait.pokercraft.item.PlayingCardItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.RaycastContext;

import java.util.UUID;

/** A harmless projectile. Impacts deliberately bypass vanilla damage and block-hit callbacks. */
public final class ThrownPlayingCardEntity extends ProjectileEntity {
    private static final TrackedData<ItemStack> CARD_STACK = DataTracker.registerData(ThrownPlayingCardEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);
    private static final TrackedData<Boolean> STUCK = DataTracker.registerData(ThrownPlayingCardEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> TARGET = DataTracker.registerData(ThrownPlayingCardEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> OFFSET_X = DataTracker.registerData(ThrownPlayingCardEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> OFFSET_Y = DataTracker.registerData(ThrownPlayingCardEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> OFFSET_Z = DataTracker.registerData(ThrownPlayingCardEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> RELATIVE_YAW = DataTracker.registerData(ThrownPlayingCardEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private BlockPos stuckBlock;
    private UUID targetUuid;
    private int lifetime;
    private boolean recoverable = true;

    public ThrownPlayingCardEntity(EntityType<? extends ThrownPlayingCardEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(CARD_STACK, new ItemStack(ModItems.PLAYING_CARD));
        builder.add(STUCK, false);
        builder.add(TARGET, -1);
        builder.add(OFFSET_X, 0F);
        builder.add(OFFSET_Y, 0F);
        builder.add(OFFSET_Z, 0F);
        builder.add(RELATIVE_YAW, 0F);
    }

    public boolean isStuck() { return dataTracker.get(STUCK); }
    public void setRecoverable(boolean recoverable) { this.recoverable = recoverable; }

    public ItemStack getCardStack() { return dataTracker.get(CARD_STACK); }

    public void setCardStack(ItemStack stack) {
        ItemStack card = stack.copyWithCount(1);
        PlayingCardItem.ensureFace(card, getWorld().random);
        dataTracker.set(CARD_STACK, card);
    }

    private static float bodyYaw(Entity target) {
        return target instanceof LivingEntity living ? living.bodyYaw : target.getYaw();
    }

    @Override
    public void tick() {
        super.tick();
        if (!getWorld().isClient && ++lifetime >= 6000) {
            discard();
            return;
        }
        if (isStuck()) {
            followTarget();
            if (!getWorld().isClient && stuckBlock != null
                    && getWorld().getBlockState(stuckBlock).getCollisionShape(getWorld(), stuckBlock).isEmpty()) {
                releaseCard();
            }
            return;
        }
        Vec3d end = getPos().add(getVelocity());
        HitResult hit = getWorld().raycast(new RaycastContext(getPos(), end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
        // Use the actual entity surface, rather than the enlarged snowball hit box, so cards embed visibly.
        Vec3d collisionEnd = hit.getType() == HitResult.Type.MISS ? end : hit.getPos();
        EntityHitResult entityHit = ProjectileUtil.getEntityCollision(getWorld(), this, getPos(),
                collisionEnd,
                getBoundingBox().stretch(getVelocity()).expand(1), this::canHit, 0F);
        if (entityHit != null) {
            // The vanilla helper discards the intersection and returns the target's feet position.
            // Recover the surface intersection before calculating the attachment offset.
            Entity target = entityHit.getEntity();
            var impact = target.getBoundingBox().raycast(getPos(), collisionEnd);
            if (impact.isPresent()) hit = new EntityHitResult(target, impact.get());
        }
        if (hit.getType() != HitResult.Type.MISS) {
            // The server chooses the impact; clients keep flying until the tracked state arrives.
            if (!getWorld().isClient) stick(hit);
            return;
        }
        setPosition(getPos().add(getVelocity()));
        updateRotation();
        setVelocity(getVelocity().multiply(isTouchingWater() ? .8 : .99).add(0, -.025, 0));
    }

    private void stick(HitResult hit) {
        Vec3d direction = getVelocity().normalize();
        setYaw((float) Math.toDegrees(Math.atan2(direction.x, direction.z)));
        setPitch((float) Math.toDegrees(Math.atan2(direction.y, direction.horizontalLength())));
        // Leave most of the card visible, with its leading edge embedded in the surface.
        setPosition(hit.getPos().subtract(direction.multiply(.12)));
        if (hit instanceof EntityHitResult entityHit) {
            Entity target = entityHit.getEntity();
            float yaw = bodyYaw(target);
            Vec3d offset = getPos().subtract(target.getPos()).rotateY(yaw * MathHelper.RADIANS_PER_DEGREE);
            dataTracker.set(OFFSET_X, (float) offset.x);
            dataTracker.set(OFFSET_Y, (float) offset.y);
            dataTracker.set(OFFSET_Z, (float) offset.z);
            dataTracker.set(RELATIVE_YAW, getYaw() + yaw);
            dataTracker.set(TARGET, target.getId());
            targetUuid = target.getUuid();
        } else if (hit instanceof BlockHitResult blockHit) {
            stuckBlock = blockHit.getBlockPos().toImmutable();
        }
        setVelocity(Vec3d.ZERO);
        setNoGravity(true);
        dataTracker.set(STUCK, true);
        velocityDirty = true;
    }

    private void followTarget() {
        if (targetUuid == null && dataTracker.get(TARGET) < 0) return;
        Entity target = getWorld().getEntityById(dataTracker.get(TARGET));
        if (getWorld() instanceof ServerWorld serverWorld) {
            if (target == null || !target.getUuid().equals(targetUuid)) target = serverWorld.getEntity(targetUuid);
            if (target == null || !target.isAlive()) {
                releaseCard();
                return;
            }
            dataTracker.set(TARGET, target.getId());
        }
        if (target != null) {
            float yaw = bodyYaw(target);
            Vec3d offset = new Vec3d(dataTracker.get(OFFSET_X), dataTracker.get(OFFSET_Y), dataTracker.get(OFFSET_Z))
                    .rotateY(-yaw * MathHelper.RADIANS_PER_DEGREE);
            setPosition(target.getPos().add(offset));
            setYaw(dataTracker.get(RELATIVE_YAW) - yaw);
        }
    }

    private void releaseCard() {
        if (recoverable) dropStack(getCardStack().copy());
        discard();
    }

    @Override
    public boolean canHit() { return isStuck(); }

    @Override
    public boolean canBeHitByProjectile() { return false; }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (!isStuck()) return ActionResult.PASS;
        if (!getWorld().isClient) {
            if (recoverable && !player.getInventory().insertStack(getCardStack().copy())) {
                return ActionResult.PASS;
            }
            discard();
        }
        return ActionResult.success(getWorld().isClient);
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.put("CardStack", getCardStack().encode(getRegistryManager()));
        nbt.putBoolean("Stuck", isStuck());
        nbt.putBoolean("Recoverable", recoverable);
        nbt.putInt("Lifetime", lifetime);
        if (stuckBlock != null) nbt.putLong("StuckBlock", stuckBlock.asLong());
        if (targetUuid != null) nbt.putUuid("Target", targetUuid);
        nbt.putFloat("OffsetX", dataTracker.get(OFFSET_X));
        nbt.putFloat("OffsetY", dataTracker.get(OFFSET_Y));
        nbt.putFloat("OffsetZ", dataTracker.get(OFFSET_Z));
        nbt.putFloat("RelativeYaw", dataTracker.get(RELATIVE_YAW));
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("CardStack")) {
            ItemStack.fromNbt(getRegistryManager(), nbt.getCompound("CardStack"))
                    .filter(stack -> stack.isOf(ModItems.PLAYING_CARD)).ifPresent(this::setCardStack);
        }
        dataTracker.set(STUCK, nbt.getBoolean("Stuck"));
        recoverable = nbt.getBoolean("Recoverable");
        lifetime = nbt.getInt("Lifetime");
        stuckBlock = nbt.contains("StuckBlock") ? BlockPos.fromLong(nbt.getLong("StuckBlock")) : null;
        targetUuid = nbt.containsUuid("Target") ? nbt.getUuid("Target") : null;
        dataTracker.set(OFFSET_X, nbt.getFloat("OffsetX"));
        dataTracker.set(OFFSET_Y, nbt.getFloat("OffsetY"));
        dataTracker.set(OFFSET_Z, nbt.getFloat("OffsetZ"));
        dataTracker.set(RELATIVE_YAW, nbt.getFloat("RelativeYaw"));
        if (isStuck()) setVelocity(Vec3d.ZERO);
    }
}
