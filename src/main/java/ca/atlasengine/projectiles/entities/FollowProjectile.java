package ca.atlasengine.projectiles.entities;

import ca.atlasengine.projectiles.AbstractProjectile;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import org.jetbrains.annotations.NotNull;

/**
 * A configurable, smoothly homing projectile.
 * <p>This is not a shulker bullet; use {@link ShulkerBulletProjectile} for
 * axis-based shulker-bullet steering.</p>
 */
public class FollowProjectile extends AbstractProjectile {
    private final @NotNull Entity target;
    private final float attractionAcceleration;
    private final float attractionVelocity;

    /**
     * Creates an unspawned homing projectile.
     * @param type displayed entity type
     * @param shooter projectile owner
     * @param target entity to follow
     * @param attractionVelocity initial target speed in blocks per tick
     * @param attractionAcceleration target-speed increase per tick
     */
    public FollowProjectile(@NotNull EntityType type, @NotNull Entity shooter, @NotNull Entity target,
                            float attractionVelocity, float attractionAcceleration) {
        super(type, shooter);
        if (!Float.isFinite(attractionVelocity) || attractionVelocity < 0.0f) {
            throw new IllegalArgumentException("attractionVelocity must be finite and non-negative");
        }
        if (!Float.isFinite(attractionAcceleration) || attractionAcceleration < 0.0f) {
            throw new IllegalArgumentException("attractionAcceleration must be finite and non-negative");
        }
        setNoGravity(true);
        this.target = target;
        this.attractionVelocity = attractionVelocity;
        this.attractionAcceleration = attractionAcceleration;
    }

    /**
     * Gets the pursuit target.
     * @return entity being followed
     */
    public @NotNull Entity getTarget() {
        return target;
    }

    @Override
    protected @NotNull Vec beforeMovement(@NotNull Vec movement) {
        Vec toTarget = target.getPosition().add(0.0, target.getEyeHeight() * 0.5, 0.0)
                .sub(getPosition()).asVec();
        if (toTarget.isZero()) return movement;

        double targetSpeed = attractionVelocity + attractionAcceleration * getAliveTicks();
        Vec desiredVelocity = toTarget.normalize().mul(targetSpeed);
        return movement.add(desiredVelocity.sub(movement).mul(0.2));
    }
}
