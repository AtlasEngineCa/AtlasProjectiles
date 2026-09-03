package ca.atlasengine.projectiles.entities;

import ca.atlasengine.projectiles.AbstractProjectile;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import org.jetbrains.annotations.NotNull;

/** A snowball, egg, ender pearl, or other thrown item. */
public class ThrownItemProjectile extends AbstractProjectile {
    /**
     * Creates an unspawned thrown item.
     * @param type thrown-item entity type
     * @param shooter projectile owner
     */
    public ThrownItemProjectile(@NotNull EntityType type, @NotNull Entity shooter) {
        super(type, shooter);
    }

    @Override
    protected @NotNull Vec beforeMovement(@NotNull Vec movement) {
        Vec withGravity = hasNoGravity() ? movement : movement.sub(0.0, 0.03, 0.0);
        return withGravity.mul(isInWater() ? 0.8 : 0.99);
    }
}
