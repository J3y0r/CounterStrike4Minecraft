package me.jeyor.cs4m.weapon;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class Hitscan {
    private Hitscan() {
    }

    public static HitResult trace(ServerPlayer player, Vec3 direction, double range) {
        ServerLevel level = player.level();
        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(direction.scale(range));
        BlockHitResult blockHit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 end = blockHit.getType() == HitResult.Type.MISS ? to : blockHit.getLocation();
        AABB box = player.getBoundingBox().expandTowards(end.subtract(from)).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                player,
                from,
                end,
                box,
                entity -> entity.isAlive() && entity.isPickable() && !entity.isSpectator() && entity != player,
                from.distanceToSqr(end)
        );
        if (entityHit != null && from.distanceToSqr(entityHit.getLocation()) < from.distanceToSqr(end)) {
            return entityHit;
        }
        return blockHit;
    }

    public static Vec3 spread(Vec3 look, float degrees, net.minecraft.util.RandomSource random) {
        if (degrees <= 0.0F) {
            return look.normalize();
        }
        double yaw = Math.toRadians((random.nextDouble() * 2.0 - 1.0) * degrees);
        double pitch = Math.toRadians((random.nextDouble() * 2.0 - 1.0) * degrees);
        Vec3 direction = look.normalize();
        Vec3 up = Math.abs(direction.y) > 0.99 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 right = direction.cross(up).normalize();
        Vec3 actualUp = right.cross(direction).normalize();
        return direction.add(right.scale(Math.tan(yaw))).add(actualUp.scale(Math.tan(pitch))).normalize();
    }

    public static boolean headshot(LivingEntity target, Vec3 hit) {
        return hit.y >= target.getEyeY() - 0.35;
    }

    public static void trail(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length < 0.1) {
            return;
        }
        Vec3 step = delta.scale(1.0 / Math.max(1.0, length / 1.5));
        Vec3 point = from.add(step.scale(0.5));
        for (double travelled = 0.5; travelled < length; travelled += 1.5) {
            level.sendParticles(ParticleTypes.CRIT, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
            point = point.add(step);
        }
    }

    public static void impact(ServerLevel level, Vec3 point, Entity hit) {
        if (hit != null) {
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, point.x, point.y, point.z, 4, 0.1, 0.1, 0.1, 0.02);
        } else {
            level.sendParticles(ParticleTypes.SMOKE, point.x, point.y, point.z, 3, 0.05, 0.05, 0.05, 0.01);
        }
    }
}
