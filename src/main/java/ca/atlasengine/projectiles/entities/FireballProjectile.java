package ca.atlasengine.projectiles.entities;

import ca.atlasengine.projectiles.AbstractProjectile;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import org.jetbrains.annotations.NotNull;

/** An accelerating fireball projectile. */
public class FireballProjectile extends AbstractProjectile {
    private double accelerationPower = 0.1;
    private double inertia = 0.95;

    /**
     * Creates an unspawned fireball.
     * @param type fireball-compatible entity type
     * @param shooter projectile owner
     */
    public FireballProjectile(@NotNull EntityType type, @NotNull Entity shooter) {
        super(type, shooter);
        setNoGravity(true);
    }

    /**
     * Gets directional acceleration.
     * @return acceleration added per tick in blocks per tick
     */
    public double getAccelerationPower() {
        return accelerationPower;
    }

    /**
     * Sets directional acceleration.
     * @param accelerationPower non-negative acceleration per tick
     */
    public void setAccelerationPower(double accelerationPower) {
        if (!Double.isFinite(accelerationPower) || accelerationPower < 0.0) {
            throw new IllegalArgumentException("accelerationPower must be finite and non-negative");
        }
        this.accelerationPower = accelerationPower;
    }

    /**
     * Gets air inertia.
     * @return air inertia multiplier
     */
    public double getInertia() {
        return inertia;
    }

    /**
     * Sets air inertia.
     * @param inertia air multiplier between zero and one
     */
    public void setInertia(double inertia) {
        if (!Double.isFinite(inertia) || inertia < 0.0 || inertia > 1.0) {
            throw new IllegalArgumentException("inertia must be between 0 and 1");
        }
        this.inertia = inertia;
    }

    @Override
    protected @NotNull Vec beforeMovement(@NotNull Vec movement) {
        Vec acceleration = movement.isZero() ? Vec.ZERO : movement.normalize().mul(accelerationPower);
        return movement.add(acceleration).mul(isInWater() ? 0.8 : inertia);
    }
}
