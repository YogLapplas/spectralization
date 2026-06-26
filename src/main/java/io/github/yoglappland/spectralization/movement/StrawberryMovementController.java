package io.github.yoglappland.spectralization.movement;

import io.github.yoglappland.spectralization.Spectralization;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class StrawberryMovementController {
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private static final int COYOTE_TICKS = 4;
    private static final int JUMP_BUFFER_TICKS = 4;
    private static final int DASH_BUFFER_TICKS = 3;
    private static final int DASH_TIME_TICKS = 4;
    private static final int DASH_END_TICKS = 5;
    private static final int DASH_COOLDOWN_TICKS = 5;
    private static final int DASH_JUMP_GRACE_TICKS = 5;
    private static final int HYPER_GRACE_TICKS = 5;
    private static final int VAR_JUMP_TICKS = 7;

    private static final double JUMP_SPEED = 0.60D;
    private static final double JUMP_HORIZONTAL_BOOST = 0.24D;
    private static final double SUPER_HORIZONTAL_SPEED = 1.38D;
    private static final double HYPER_JUMP_SPEED = 0.38D;
    private static final double HYPER_HORIZONTAL_SPEED = 1.55D;
    private static final double DASH_SPEED = 1.38D;
    private static final double DASH_END_SPEED = 0.82D;
    private static final double DASH_END_MIN_SPEED = 0.44D;
    private static final double MAX_RETAINED_AIR_SPEED = 1.35D;
    private static final double APEX_THRESHOLD = 0.08D;
    private static final double APEX_GRAVITY_REFUND = 0.035D;
    private static final double FALL_GRAVITY_REFUND = 0.022D;
    private static final double FALL_GRAVITY_REFUND_THRESHOLD = -0.04D;
    private static final double RELEASE_CUT_MULTIPLIER = 0.58D;
    private static final double DOWN_DASH_THRESHOLD = -0.18D;

    public static void tick(ServerPlayer player) {
        State state = STATES.get(player.getUUID());

        if (!hasRod(player) || !canControl(player)) {
            if (state != null) {
                state.reset();
            }
            STATES.remove(player.getUUID());
            return;
        }

        if (state == null) {
            state = new State();
            STATES.put(player.getUUID(), state);
        }

        state.tick(player);
    }

    public static void requestDash(ServerPlayer player) {
        if (!hasRod(player) || !canControl(player)) {
            return;
        }

        State state = STATES.computeIfAbsent(player.getUUID(), ignored -> new State());
        state.dashBufferTicks = DASH_BUFFER_TICKS;
        state.tryStartDash(player);
    }

    public static void predictDash(Player player) {
        if (!hasRod(player) || !canControl(player)) {
            return;
        }

        Vec3 direction = predictionDashDirection(player);
        player.setDeltaMovement(direction.scale(DASH_SPEED));
        player.hasImpulse = true;
        player.fallDistance = 0.0F;
    }

    public static void predictJump(Player player) {
        if (!hasRod(player) || !canControl(player) || !player.onGround()) {
            return;
        }

        Vec3 movement = player.getDeltaMovement();
        if (movement.y > 0.12D) {
            return;
        }

        player.setDeltaMovement(movement.x, JUMP_SPEED, movement.z);
        player.hasImpulse = true;
        player.fallDistance = 0.0F;
    }

    public static void updateInput(
            ServerPlayer player,
            boolean holdingRod,
            boolean jumpDown,
            boolean jumpPressed,
            float leftImpulse,
            float forwardImpulse
    ) {
        if (!holdingRod || !hasRod(player)) {
            State removed = STATES.remove(player.getUUID());
            if (removed != null) {
                removed.reset();
            }
            return;
        }

        State state = STATES.computeIfAbsent(player.getUUID(), ignored -> new State());
        state.holdingRodOnClient = holdingRod;
        state.jumpDown = jumpDown;
        state.leftImpulse = clampImpulse(leftImpulse);
        state.forwardImpulse = clampImpulse(forwardImpulse);

        if (jumpPressed) {
            state.jumpBufferTicks = JUMP_BUFFER_TICKS;
        }
    }

    public static void clearAll() {
        STATES.clear();
    }

    public static boolean hasRod(Player player) {
        return player.getMainHandItem().is(Spectralization.ROD_OF_STRAWBERRY.get())
                || player.getOffhandItem().is(Spectralization.ROD_OF_STRAWBERRY.get());
    }

    private static boolean canControl(Player player) {
        return !player.isSpectator()
                && !player.getAbilities().flying
                && !player.isFallFlying()
                && !player.isPassenger();
    }

    private static float clampImpulse(float impulse) {
        if (!Float.isFinite(impulse)) {
            return 0.0F;
        }

        return Math.max(-1.0F, Math.min(1.0F, impulse));
    }

    private static Vec3 predictionDashDirection(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 clamped = new Vec3(look.x, Math.max(-0.35D, look.y), look.z);

        if (clamped.lengthSqr() <= 1.0E-6D) {
            return Vec3.directionFromRotation(0.0F, player.getYRot()).normalize();
        }

        return clamped.normalize();
    }

    private static final class State {
        private Mode mode = Mode.NORMAL;
        private int modeTicks;
        private int coyoteTicks;
        private int jumpBufferTicks;
        private int dashBufferTicks;
        private int dashCooldownTicks;
        private int dashJumpGraceTicks;
        private int hyperGraceTicks;
        private int varJumpTicks;
        private int dashes = 1;
        private boolean holdingRodOnClient;
        private boolean jumpDown;
        private float leftImpulse;
        private float forwardImpulse;
        private Vec3 dashDirection = Vec3.ZERO;
        private Vec3 hyperDirection = Vec3.ZERO;

        private void tick(ServerPlayer player) {
            tickTimers();
            updateGroundState(player);

            if (mode != Mode.DASH && player.getDeltaMovement().y > JUMP_SPEED * 0.75D) {
                jumpBufferTicks = 0;
            }

            switch (mode) {
                case NORMAL -> tickNormal(player);
                case DASH -> tickDash(player);
                case DASH_END -> tickDashEnd(player);
            }
        }

        private void tickTimers() {
            if (jumpBufferTicks > 0) {
                jumpBufferTicks--;
            }

            if (dashBufferTicks > 0) {
                dashBufferTicks--;
            }

            if (dashCooldownTicks > 0) {
                dashCooldownTicks--;
            }

            if (dashJumpGraceTicks > 0) {
                dashJumpGraceTicks--;
            }

            if (hyperGraceTicks > 0) {
                hyperGraceTicks--;
            }

            if (varJumpTicks > 0) {
                varJumpTicks--;
            }

            if (coyoteTicks > 0) {
                coyoteTicks--;
            }
        }

        private void updateGroundState(ServerPlayer player) {
            if (player.onGround()) {
                coyoteTicks = COYOTE_TICKS;
                dashes = 1;

                if ((mode == Mode.DASH || mode == Mode.DASH_END) && isDownwardDash()) {
                    hyperGraceTicks = HYPER_GRACE_TICKS;
                    hyperDirection = dashHorizontalDirection(player);
                }
            }
        }

        private void tickNormal(ServerPlayer player) {
            if (tryStartDash(player)) {
                return;
            }

            if (tryBufferedJump(player)) {
                return;
            }

            applyVariableJump(player);
            applyApexForgiveness(player);
            applyFallingGravityRelief(player);
            retainHorizontalAirSpeed(player);
        }

        private void tickDash(ServerPlayer player) {
            player.setDeltaMovement(dashDirection.scale(DASH_SPEED));
            player.hasImpulse = true;
            player.fallDistance = 0.0F;

            if (tryDashJump(player)) {
                return;
            }

            modeTicks--;
            if (modeTicks <= 0) {
                beginDashEnd(player);
            }
        }

        private void tickDashEnd(ServerPlayer player) {
            if (tryBufferedJump(player)) {
                return;
            }

            Vec3 movement = player.getDeltaMovement();
            Vec3 horizontal = new Vec3(movement.x, 0.0D, movement.z);
            double horizontalSpeed = horizontal.length();

            if (horizontalSpeed > DASH_END_SPEED) {
                horizontal = horizontal.normalize().scale(DASH_END_SPEED);
                player.setDeltaMovement(horizontal.x, movement.y, horizontal.z);
                player.hasImpulse = true;
            } else if (horizontalSpeed > 0.01D && horizontalSpeed < DASH_END_MIN_SPEED) {
                horizontal = horizontal.normalize().scale(DASH_END_MIN_SPEED);
                player.setDeltaMovement(horizontal.x, movement.y, horizontal.z);
                player.hasImpulse = true;
            }

            applyVariableJump(player);
            applyApexForgiveness(player);
            applyFallingGravityRelief(player);
            player.fallDistance = 0.0F;

            modeTicks--;
            if (player.onGround() || modeTicks <= 0) {
                mode = Mode.NORMAL;
            }
        }

        private boolean tryStartDash(ServerPlayer player) {
            if (dashBufferTicks <= 0 || dashCooldownTicks > 0 || dashes <= 0) {
                return false;
            }

            dashBufferTicks = 0;
            dashes--;
            mode = Mode.DASH;
            modeTicks = DASH_TIME_TICKS;
            varJumpTicks = 0;
            jumpBufferTicks = 0;
            dashDirection = dashDirection(player);
            dashJumpGraceTicks = player.onGround() || coyoteTicks > 0 ? DASH_JUMP_GRACE_TICKS : 0;
            hyperGraceTicks = 0;
            hyperDirection = Vec3.ZERO;
            player.setDeltaMovement(dashDirection.scale(DASH_SPEED));
            player.hasImpulse = true;
            player.fallDistance = 0.0F;
            playDashSound(player);
            return true;
        }

        private boolean tryDashJump(ServerPlayer player) {
            if (tryHyperJump(player)) {
                return true;
            }

            if (jumpBufferTicks <= 0 || dashJumpGraceTicks <= 0) {
                return false;
            }

            return performMomentumJump(player, JUMP_SPEED, SUPER_HORIZONTAL_SPEED, dashHorizontalDirection(player));
        }

        private boolean tryBufferedJump(ServerPlayer player) {
            if (tryHyperJump(player)) {
                return true;
            }

            if (jumpBufferTicks <= 0 || coyoteTicks <= 0) {
                return false;
            }

            Vec3 movement = player.getDeltaMovement();
            if (movement.y > 0.12D) {
                jumpBufferTicks = 0;
                return false;
            }

            Vec3 input = horizontalInput(player);
            double x = movement.x + input.x * JUMP_HORIZONTAL_BOOST;
            double z = movement.z + input.z * JUMP_HORIZONTAL_BOOST;

            player.setDeltaMovement(x, JUMP_SPEED, z);
            player.hasImpulse = true;
            player.fallDistance = 0.0F;
            jumpBufferTicks = 0;
            coyoteTicks = 0;
            varJumpTicks = VAR_JUMP_TICKS;
            mode = Mode.NORMAL;
            return true;
        }

        private boolean tryHyperJump(ServerPlayer player) {
            boolean activeDownDash = isDownwardDash() && (player.onGround() || coyoteTicks > 0);
            if (jumpBufferTicks <= 0 || (!activeDownDash && hyperGraceTicks <= 0)) {
                return false;
            }

            Vec3 direction = activeDownDash ? dashHorizontalDirection(player) : hyperDirection;
            return performMomentumJump(player, HYPER_JUMP_SPEED, HYPER_HORIZONTAL_SPEED, direction);
        }

        private boolean performMomentumJump(
                ServerPlayer player,
                double jumpSpeed,
                double horizontalSpeed,
                Vec3 preferredDirection
        ) {
            Vec3 direction = preferredDirection;
            if (direction.lengthSqr() <= 1.0E-6D) {
                direction = horizontalInput(player);
            }

            if (direction.lengthSqr() <= 1.0E-6D) {
                direction = Vec3.directionFromRotation(0.0F, player.getYRot()).normalize();
            } else {
                direction = direction.normalize();
            }

            Vec3 movement = player.getDeltaMovement();
            double speed = Math.max(horizontalSpeed, new Vec3(movement.x, 0.0D, movement.z).length());
            Vec3 horizontal = direction.scale(speed);

            player.setDeltaMovement(horizontal.x, jumpSpeed, horizontal.z);
            player.hasImpulse = true;
            player.fallDistance = 0.0F;
            jumpBufferTicks = 0;
            coyoteTicks = 0;
            dashJumpGraceTicks = 0;
            hyperGraceTicks = 0;
            varJumpTicks = VAR_JUMP_TICKS;
            mode = Mode.NORMAL;
            return true;
        }

        private void beginDashEnd(ServerPlayer player) {
            Vec3 movement = player.getDeltaMovement();
            Vec3 horizontal = new Vec3(movement.x, 0.0D, movement.z);

            if (horizontal.lengthSqr() > 1.0E-6D) {
                horizontal = horizontal.normalize().scale(DASH_END_SPEED);
                player.setDeltaMovement(horizontal.x, Math.min(movement.y, 0.0D), horizontal.z);
            }

            player.hasImpulse = true;
            mode = Mode.DASH_END;
            modeTicks = DASH_END_TICKS;
            dashCooldownTicks = DASH_COOLDOWN_TICKS;
        }

        private void applyVariableJump(ServerPlayer player) {
            if (varJumpTicks <= 0) {
                return;
            }

            Vec3 movement = player.getDeltaMovement();
            if (movement.y <= 0.0D) {
                varJumpTicks = 0;
                return;
            }

            if (jumpDown || !holdingRodOnClient) {
                return;
            }

            player.setDeltaMovement(movement.x, movement.y * RELEASE_CUT_MULTIPLIER, movement.z);
            player.hasImpulse = true;
            varJumpTicks = 0;
        }

        private void applyApexForgiveness(ServerPlayer player) {
            if (player.onGround() || jumpDown || Math.abs(player.getDeltaMovement().y) > APEX_THRESHOLD) {
                return;
            }

            Vec3 movement = player.getDeltaMovement();
            player.setDeltaMovement(movement.x, movement.y + APEX_GRAVITY_REFUND, movement.z);
            player.hasImpulse = true;
        }

        private void applyFallingGravityRelief(ServerPlayer player) {
            if (player.onGround() || mode == Mode.DASH_END && isDownwardDash()) {
                return;
            }

            Vec3 movement = player.getDeltaMovement();
            if (movement.y >= FALL_GRAVITY_REFUND_THRESHOLD) {
                return;
            }

            player.setDeltaMovement(movement.x, movement.y + FALL_GRAVITY_REFUND, movement.z);
            player.hasImpulse = true;
        }

        private void retainHorizontalAirSpeed(ServerPlayer player) {
            if (player.onGround()) {
                return;
            }

            Vec3 movement = player.getDeltaMovement();
            double horizontalSpeed = movement.horizontalDistance();
            if (horizontalSpeed <= DASH_END_MIN_SPEED || horizontalSpeed >= MAX_RETAINED_AIR_SPEED) {
                return;
            }

            Vec3 input = horizontalInput(player);
            if (input.lengthSqr() <= 1.0E-6D) {
                return;
            }

            double retained = Math.min(MAX_RETAINED_AIR_SPEED, horizontalSpeed + 0.035D);
            Vec3 horizontal = new Vec3(movement.x, 0.0D, movement.z).normalize().scale(retained);
            player.setDeltaMovement(horizontal.x, movement.y, horizontal.z);
            player.hasImpulse = true;
        }

        private Vec3 dashDirection(ServerPlayer player) {
            Vec3 input = horizontalInput(player);
            Vec3 look = player.getLookAngle();

            if (input.lengthSqr() > 1.0E-6D) {
                double y = Math.max(-0.35D, Math.min(0.7D, look.y));
                return new Vec3(input.x, y, input.z).normalize();
            }

            Vec3 clamped = new Vec3(look.x, Math.max(-0.35D, look.y), look.z);
            if (clamped.lengthSqr() <= 1.0E-6D) {
                return Vec3.directionFromRotation(0.0F, player.getYRot()).normalize();
            }

            return clamped.normalize();
        }

        private boolean isDownwardDash() {
            return dashDirection.y < DOWN_DASH_THRESHOLD;
        }

        private Vec3 dashHorizontalDirection(ServerPlayer player) {
            Vec3 horizontal = new Vec3(dashDirection.x, 0.0D, dashDirection.z);
            if (horizontal.lengthSqr() > 1.0E-6D) {
                return horizontal.normalize();
            }

            Vec3 input = horizontalInput(player);
            if (input.lengthSqr() > 1.0E-6D) {
                return input.normalize();
            }

            return Vec3.directionFromRotation(0.0F, player.getYRot()).normalize();
        }

        private Vec3 horizontalInput(ServerPlayer player) {
            if (Math.abs(leftImpulse) < 0.01F && Math.abs(forwardImpulse) < 0.01F) {
                return Vec3.ZERO;
            }

            double yaw = Math.toRadians(player.getYRot());
            Vec3 forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
            Vec3 left = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw));
            Vec3 input = forward.scale(forwardImpulse).add(left.scale(leftImpulse));

            if (input.lengthSqr() > 1.0D) {
                return input.normalize();
            }

            return input;
        }

        private void reset() {
            mode = Mode.NORMAL;
            modeTicks = 0;
            coyoteTicks = 0;
            jumpBufferTicks = 0;
            dashBufferTicks = 0;
            dashCooldownTicks = 0;
            dashJumpGraceTicks = 0;
            hyperGraceTicks = 0;
            varJumpTicks = 0;
            dashes = 1;
            holdingRodOnClient = false;
            jumpDown = false;
            leftImpulse = 0.0F;
            forwardImpulse = 0.0F;
            dashDirection = Vec3.ZERO;
            hyperDirection = Vec3.ZERO;
        }

        private void playDashSound(ServerPlayer player) {
            player.level().playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES,
                    SoundSource.PLAYERS,
                    0.75F,
                    1.85F
            );
        }
    }

    private enum Mode {
        NORMAL,
        DASH,
        DASH_END
    }

    private StrawberryMovementController() {
    }
}
