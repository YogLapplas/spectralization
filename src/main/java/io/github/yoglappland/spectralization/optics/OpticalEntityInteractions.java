package io.github.yoglappland.spectralization.optics;

import io.github.yoglappland.spectralization.registry.SpectralDamageTypes;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class OpticalEntityInteractions {
    private static final double HAZARD_POWER_THRESHOLD = 35.0;
    private static final double LOOKING_INTO_BEAM_DOT = -0.82;
    private static final double ENTITY_TRANSMISSION_FACTOR = 0.35;
    private static final int BLINDNESS_TICKS = 50;

    public static BeamPacket interact(
            Level level,
            BlockPos pos,
            Direction direction,
            BeamPacket beam,
            Set<Integer> affectedEntityIds
    ) {
        if (beam.totalPower() < HAZARD_POWER_THRESHOLD) {
            return beam;
        }

        BeamPacket remainingBeam = beam;
        AABB interactionBounds = new AABB(pos).inflate(0.05D);

        for (LivingEntity livingEntity : level.getEntitiesOfClass(LivingEntity.class, interactionBounds)) {
            if (!livingEntity.isAlive() || livingEntity.isSpectator() || !isLookingIntoBeam(livingEntity, direction)) {
                continue;
            }

            if (!affectedEntityIds.add(livingEntity.getId())) {
                continue;
            }

            applyLaserExposure(level, livingEntity, remainingBeam.totalPower());
            remainingBeam = remainingBeam.scalePower(ENTITY_TRANSMISSION_FACTOR);

            if (remainingBeam.isEmpty()) {
                return remainingBeam;
            }
        }

        return remainingBeam;
    }

    private static boolean isLookingIntoBeam(LivingEntity livingEntity, Direction direction) {
        Vec3 look = livingEntity.getLookAngle().normalize();
        Vec3 beamDirection = new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ()).normalize();

        return look.dot(beamDirection) <= LOOKING_INTO_BEAM_DOT;
    }

    private static void applyLaserExposure(Level level, LivingEntity livingEntity, double power) {
        livingEntity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLINDNESS_TICKS, 0, false, true, true));
        livingEntity.hurt(level.damageSources().source(SpectralDamageTypes.LASER), damageFromPower(power));
    }

    private static float damageFromPower(double power) {
        return Mth.clamp((float) (power / 120.0), 0.5F, 3.0F);
    }

    private OpticalEntityInteractions() {
    }
}
