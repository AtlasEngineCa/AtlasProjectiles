package ca.atlasengine.projectiles;

import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * A projectile which can be launched using velocity and triangular uncertainty.
 * <p>
 * Velocity is expressed in blocks per second, as expected by Minestom. Power and
 * uncertainty use consistent projectile units (for example, a fully charged bow uses a
 * power of {@code 3.0} and an uncertainty of {@code 1.0}).
 */
public interface Projectile {
    /**
     * Returns the entity which launched this projectile.
     * @return the shooter
     */
    @NotNull Entity getShooter();

    /**
     * Launches in the direction the shooter is looking and inherits the
     * shooter's movement.
     * @param from spawn position
     * @param power projectile speed
     * @param uncertainty triangular uncertainty multiplier
     */
    void shoot(@NotNull Point from, double power, double uncertainty);

    /**
     * Launches toward an exact point. This overload does not inherit shooter
     * movement.
     * @param from spawn position
     * @param to point to aim toward
     * @param power projectile speed
     * @param uncertainty triangular uncertainty multiplier
     */
    void shoot(@NotNull Point from, @NotNull Point to, double power, double uncertainty);

    /**
     * Launches from the shooter's rotation with an additional pitch offset.
     * Negative offsets throw upward.
     * @param from spawn position
     * @param pitchOffset degrees added to the shooter's pitch
     * @param power projectile speed
     * @param uncertainty triangular uncertainty multiplier
     */
    void shootFromRotation(@NotNull Point from, float pitchOffset, double power, double uncertainty);
}
