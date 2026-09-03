package ca.atlasengine.projectiles;

import net.minestom.server.coordinate.Vec;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectileMathTest {
    private static final double EPSILON = 1.0e-12;

    @Test
    void normalizesAndScalesDirectionWithoutUncertainty() {
        Vec movement = AbstractProjectile.getMovementToShoot(new Vec(3.0, 4.0, 0.0), 2.0, 0.0, new Random(1));

        assertEquals(1.2, movement.x(), EPSILON);
        assertEquals(1.6, movement.y(), EPSILON);
        assertEquals(0.0, movement.z(), EPSILON);
    }

    @Test
    void triangularUncertaintyIsBoundedPerAxis() {
        Vec direction = new Vec(0.0, 0.0, 1.0);
        double uncertainty = 4.0;
        double maximumDeviation = 0.0172275 * uncertainty;

        for (int seed = 0; seed < 1_000; seed++) {
            Vec movement = AbstractProjectile.getMovementToShoot(direction, 1.0, uncertainty, new Random(seed));
            assertTrue(Math.abs(movement.x()) <= maximumDeviation);
            assertTrue(Math.abs(movement.y()) <= maximumDeviation);
            assertTrue(Math.abs(movement.z() - 1.0) <= maximumDeviation);
        }
    }

    @Test
    void bowPowerMatchesExpectedCurve() {
        assertEquals(0.0, BowModule.getPowerForTime(0), EPSILON);
        assertEquals(0.4166666666666667, BowModule.getPowerForTime(10), EPSILON);
        assertEquals(1.0, BowModule.getPowerForTime(20), EPSILON);
        assertEquals(1.0, BowModule.getPowerForTime(200), EPSILON);
    }
}
