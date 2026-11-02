package com.brait.pokercraft.client.mixin;

import com.brait.pokercraft.block.PokerTableBlock;
import com.brait.pokercraft.client.PokerExperience;
import com.brait.pokercraft.client.PokerCameraMotion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class PokerCameraMixin {
    @Shadow protected abstract void setPos(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow private boolean thirdPerson;
    @Shadow public abstract Vec3d getPos();
    @Shadow public abstract float getYaw();
    @Shadow public abstract float getPitch();
    @Unique private final PokerCameraMotion pokercraft$motion = new PokerCameraMotion();
    @Unique private BlockView pokercraft$area;

    @Inject(method = "update", at = @At("TAIL"))
    private void pokercraft$overhead(BlockView area, Entity focused, boolean thirdPerson, boolean inverse, float delta, CallbackInfo ci) {
        var pos = PokerExperience.cameraTable();
        var world = MinecraftClient.getInstance().world;
        if (world == null) return;
        if (area != pokercraft$area) { pokercraft$motion.reset(); pokercraft$area = area; }
        PokerCameraMotion.Pose top = null;
        if (pos != null) {
            var state = world.getBlockState(pos);
            if (state.contains(PokerTableBlock.FACING)) {
                float yaw = switch (state.get(PokerTableBlock.FACING)) { case EAST -> -90; case SOUTH -> 180; case WEST -> 90; default -> 0; };
                Vec3d from = Vec3d.ofBottomCenter(pos).add(0, 1.5, 0);
                Vec3d target = Vec3d.ofBottomCenter(pos).add(0, 4, 0);
                var obstruction = world.raycast(new RaycastContext(from, target, RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE, focused));
                double height = obstruction.getType() == HitResult.Type.MISS ? target.y
                        : Math.max(pos.getY() + 1.6, obstruction.getPos().y - .15);
                top = new PokerCameraMotion.Pose(new Vec3d(target.x, height, target.z), yaw, 90);
            }
        }
        var normal = new PokerCameraMotion.Pose(getPos(), getYaw(), getPitch());
        var pose = pokercraft$motion.update(normal, top, System.nanoTime());
        if (pokercraft$motion.isActive()) {
            setPos(pose.position().x, pose.position().y, pose.position().z);
            setRotation(pose.yaw(), pose.pitch());
            // Hide the local player's head until the camera has moved clear of it.
            this.thirdPerson = thirdPerson || pose.position().squaredDistanceTo(normal.position()) > .16;
        }
    }
}
