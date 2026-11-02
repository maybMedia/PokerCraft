package com.brait.pokercraft.client;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Frame-rate-independent camera transitions, including reversing a toggle mid-flight. */
public final class PokerCameraMotion {
    private static final long DURATION_NANOS = 450_000_000L;
    public record Pose(Vec3d position, float yaw, float pitch) {}
    private Pose current, from;
    private boolean overhead, transitioning;
    private long started;

    public void reset() { current = from = null; overhead = transitioning = false; }
    public boolean isActive() { return overhead || transitioning; }

    public Pose update(Pose normal, Pose top, long now) {
        if (current == null) current = normal;
        boolean enabled = top != null;
        if (enabled != overhead) {
            overhead = enabled;
            transitioning = true;
            from = current;
            started = now;
        }
        Pose target = enabled ? top : normal;
        double t = MathHelper.clamp((double) (now - started) / DURATION_NANOS, 0, 1);
        if (!transitioning || t >= 1) {
            transitioning = false;
            return current = target;
        }
        float ease = (float) (t * t * (3 - 2 * t));
        current = new Pose(from.position.lerp(target.position, ease),
                from.yaw + MathHelper.wrapDegrees(target.yaw - from.yaw) * ease,
                MathHelper.lerp(ease, from.pitch, target.pitch));
        return current;
    }
}
