package ca.atlasengine.projectiles;

import ca.atlasengine.projectiles.entities.ArrowProjectile;
import ca.atlasengine.projectiles.entities.FireballProjectile;
import ca.atlasengine.projectiles.entities.ShulkerBulletProjectile;
import ca.atlasengine.projectiles.entities.ThrownItemProjectile;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.metadata.projectile.AbstractArrowMeta;
import net.minestom.server.event.entity.projectile.ProjectileCollideWithBlockEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectileFlightTest {
    // Minestom's swept collision result stops one Vec.EPSILON short even when
    // no block is hit.
    private static final double POSITION_EPSILON = 2.0e-6;
    private static final double VELOCITY_EPSILON = 1.0e-9;
    private static InstanceContainer instance;
    private static Entity shooter;

    @BeforeAll
    static void initializeMinestom() {
        MinecraftServer.init();
        instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.loadChunk(0, 0).join();
        instance.loadChunk(1, 0).join();
        shooter = new Entity(EntityType.ZOMBIE);
        shooter.setInstance(instance, new Pos(1.0, 10.0, 1.0)).join();
    }

    @AfterAll
    static void stopMinestom() {
        MinecraftServer.stopCleanly();
    }

    @Test
    void projectileFamiliesUseExpectedTickOrderAndConstants() {
        var arrow = new ArrowProjectile(EntityType.ARROW, shooter);
        arrow.setCritical(true);
        assertTrue(((AbstractArrowMeta) arrow.getEntityMeta()).isCritical());
        arrow.setCritical(false);
        assertFalse(((AbstractArrowMeta) arrow.getEntityMeta()).isCritical());
        arrow.shoot(new Pos(5.0, 10.0, 1.0), new Pos(5.0, 10.0, 2.0), 1.0, 0.0);
        arrow.tick(0);
        assertEquals(2.0, arrow.getPosition().z(), POSITION_EPSILON);
        assertEquals(19.8, arrow.getVelocity().z(), VELOCITY_EPSILON);
        assertEquals(-1.0, arrow.getVelocity().y(), VELOCITY_EPSILON);

        var thrown = new ThrownItemProjectile(EntityType.SNOWBALL, shooter);
        thrown.shoot(new Pos(12.0, 10.0, 1.0), new Pos(12.0, 10.0, 2.0), 1.0, 0.0);
        thrown.tick(0);
        assertEquals(1.99, thrown.getPosition().z(), POSITION_EPSILON);
        assertEquals(19.8, thrown.getVelocity().z(), VELOCITY_EPSILON);
        assertEquals(-0.594, thrown.getVelocity().y(), VELOCITY_EPSILON);

        var fireball = new FireballProjectile(EntityType.FIREBALL, shooter);
        fireball.shoot(new Pos(20.0, 10.0, 1.0), new Pos(20.0, 10.0, 2.0), 0.1, 0.0);
        fireball.tick(0);
        assertEquals(1.19, fireball.getPosition().z(), POSITION_EPSILON);
        assertEquals(3.8, fireball.getVelocity().z(), VELOCITY_EPSILON);
    }

    @Test
    void rotationLaunchInheritsShooterMovementButTargetLaunchDoesNot() {
        shooter.setVelocity(new Vec(20.0, 20.0, 0.0));

        var rotationLaunch = new ArrowProjectile(EntityType.ARROW, shooter);
        rotationLaunch.shoot(new Pos(3.0, 10.0, 1.0), 1.0, 0.0);
        assertEquals(20.0, rotationLaunch.getVelocity().x(), VELOCITY_EPSILON);
        assertEquals(20.0, rotationLaunch.getVelocity().y(), VELOCITY_EPSILON);
        assertEquals(20.0, rotationLaunch.getVelocity().z(), VELOCITY_EPSILON);

        var targetLaunch = new ArrowProjectile(EntityType.ARROW, shooter);
        targetLaunch.shoot(new Pos(4.0, 10.0, 1.0), new Pos(4.0, 10.0, 2.0), 1.0, 0.0);
        assertEquals(0.0, targetLaunch.getVelocity().x(), VELOCITY_EPSILON);
        assertEquals(0.0, targetLaunch.getVelocity().y(), VELOCITY_EPSILON);
        assertEquals(20.0, targetLaunch.getVelocity().z(), VELOCITY_EPSILON);

        shooter.setVelocity(Vec.ZERO);
    }

    @Test
    void shulkerBulletUsesExpectedTargetSpeedAndSteering() {
        var target = new Entity(EntityType.ZOMBIE);
        target.setInstance(instance, new Pos(14.0, 10.0, 8.0)).join();

        var bullet = new ShulkerBulletProjectile(EntityType.SHULKER_BULLET, shooter, target);
        bullet.spawn(new Pos(13.0, 10.0, 8.0));
        bullet.tick(0);

        // target speed (0.15) * acceleration (1.025) * steering (0.2),
        // converted by Minestom from blocks/tick to blocks/second.
        assertEquals(0.615, bullet.getVelocity().length(), VELOCITY_EPSILON);
    }

    @Test
    void blockCollisionCancellationPassesThroughAndAcceptedArrowEmbeds() {
        instance.setBlock(7, 10, 2, Block.STONE);
        var passingArrow = new ArrowProjectile(EntityType.ARROW, shooter);
        AtomicBoolean collisionCalled = new AtomicBoolean();
        passingArrow.eventNode().addListener(ProjectileCollideWithBlockEvent.class, event -> {
            collisionCalled.set(true);
            event.setCancelled(true);
        });
        passingArrow.shoot(new Pos(7.0, 10.0, 1.0), new Pos(7.0, 10.0, 3.0), 2.0, 0.0);
        passingArrow.tick(0);

        assertTrue(collisionCalled.get());
        assertFalse(passingArrow.isRemoved());
        assertFalse(passingArrow.isInBlock());
        assertEquals(3.0, passingArrow.getPosition().z(), POSITION_EPSILON);

        instance.setBlock(9, 10, 2, Block.STONE);
        var embeddedArrow = new ArrowProjectile(EntityType.ARROW, shooter);
        embeddedArrow.shoot(new Pos(9.0, 10.0, 1.0), new Pos(9.0, 10.0, 3.0), 2.0, 0.0);
        embeddedArrow.tick(0);

        assertTrue(embeddedArrow.isInBlock());
        assertTrue(((AbstractArrowMeta) embeddedArrow.getEntityMeta()).isInGround());
        assertEquals(0.0, embeddedArrow.getVelocity().length(), VELOCITY_EPSILON);
    }
}
