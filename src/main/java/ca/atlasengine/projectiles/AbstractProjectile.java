package ca.atlasengine.projectiles;

import net.minestom.server.property.ServerProperties;
import net.minestom.server.collision.*;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.entity.EntityShootEvent;
import net.minestom.server.event.entity.projectile.ProjectileCollideWithBlockEvent;
import net.minestom.server.event.entity.projectile.ProjectileCollideWithEntityEvent;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockHandler;
import net.minestom.server.utils.chunk.ChunkCache;
import net.minestom.server.utils.chunk.ChunkUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.random.RandomGenerator;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Shared launch, movement and collision handling.
 */
public abstract class AbstractProjectile extends Entity implements Projectile {
    private static final double UNCERTAINTY_SCALE = 0.0172275;
    private static final BoundingBox[] ENTITY_COLLISION_BOXES = createEntityCollisionBoxes();

    /** Entity which owns and launched this projectile. */
    protected final @NotNull Entity shooter;
    private PhysicsResult projectilePhysicsResult;
    private Pos tickStartPosition;
    private Vec tickMovement = Vec.ZERO;
    private boolean leftShooter;
    /** Whether movement is paused because the projectile is embedded. */
    protected boolean inBlock = false;

    /**
     * Creates an unspawned projectile.
     * @param type displayed entity type
     * @param shooter projectile owner
     */
    protected AbstractProjectile(@NotNull EntityType type, @NotNull Entity shooter) {
        super(type);
        this.shooter = shooter;
        // Movement and contacts are handled below. This also prevents Minestom's
        // generic touch pass from invoking block handlers a second time.
        this.hasPhysics = false;
        this.collidesWithEntities = false;

        if (getEntityMeta() instanceof net.minestom.server.entity.metadata.projectile.ProjectileMeta meta) {
            meta.setShooter(shooter);
        }
    }

    @Override
    public void tick(long time) {
        if (removed) return;

        // Keep the complete Entity lifecycle: scheduler, update, tick counter,
        // effects and periodic synchronization.
        super.tick(time);
        if (removed || inBlock || projectilePhysicsResult == null) return;

        if (!callEntityCollision()) {
            callBlockCollision();
        }
    }

    @Override
    public final @NotNull Entity getShooter() {
        return shooter;
    }

    @Override
    public void shoot(@NotNull Point from, double power, double uncertainty) {
        shootFromRotation(from, 0.0f, power, uncertainty);
    }

    @Override
    public void shootFromRotation(@NotNull Point from, float pitchOffset, double power, double uncertainty) {
        Pos rotation = shooter.getPosition().withPitch(shooter.getPosition().pitch() + pitchOffset);
        launch(from, from.add(rotation.direction()), power, uncertainty, true);
    }

    @Override
    public void shoot(@NotNull Point from, @NotNull Point to, double power, double uncertainty) {
        launch(from, to, power, uncertainty, false);
    }

    /**
     * Shared launch implementation for custom projectile APIs.
     * @param from spawn position
     * @param to aim point
     * @param power projectile speed
     * @param uncertainty triangular uncertainty multiplier
     * @param inheritShooterVelocity whether to add the shooter's movement
     */
    protected final void launch(Point from, Point to, double power, double uncertainty, boolean inheritShooterVelocity) {
        if (!Double.isFinite(power) || power < 0.0) {
            throw new IllegalArgumentException("power must be finite and non-negative");
        }
        if (!Double.isFinite(uncertainty) || uncertainty < 0.0) {
            throw new IllegalArgumentException("uncertainty must be finite and non-negative");
        }
        var instance = shooter.getInstance();
        if (instance == null) {
            throw new IllegalStateException("Cannot shoot a projectile before its shooter is in an instance");
        }

        Vec direction = to.sub(from).asVec();
        if (direction.isZero()) {
            throw new IllegalArgumentException("from and to must be different points");
        }

        EntityShootEvent event = new EntityShootEvent(shooter, this, to, power, uncertainty);
        EventDispatcher.call(event);
        if (event.isCancelled()) {
            remove();
            return;
        }
        if (!Double.isFinite(event.getPower()) || event.getPower() < 0.0
                || !Double.isFinite(event.getSpread()) || event.getSpread() < 0.0) {
            throw new IllegalArgumentException("EntityShootEvent produced invalid power or uncertainty");
        }

        Vec movement = getMovementToShoot(direction, event.getPower(), event.getSpread());
        if (inheritShooterVelocity) {
            Vec shooterMovement = shooter.getVelocity().div(ServerProperties.SERVER_TICKS_PER_SECOND.get());
            movement = movement.add(shooterMovement.x(), shooter.isOnGround() ? 0.0 : shooterMovement.y(), shooterMovement.z());
        }

        Vec velocity = movement.mul(ServerProperties.SERVER_TICKS_PER_SECOND.get());
        Pos spawnPosition = withProjectileView(movement, from);
        setInstance(instance, spawnPosition).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                remove();
                net.minestom.server.MinecraftServer.getExceptionManager().handleException(throwable);
                return;
            }
            synchronizePosition();
            setVelocity(velocity);
        });
    }

    static @NotNull Vec getMovementToShoot(@NotNull Point direction, double power, double uncertainty) {
        return getMovementToShoot(direction, power, uncertainty, ThreadLocalRandom.current());
    }

    static @NotNull Vec getMovementToShoot(@NotNull Point direction, double power, double uncertainty,
                                            @NotNull RandomGenerator random) {
        Vec normalized = direction.asVec().normalize();
        double deviation = UNCERTAINTY_SCALE * uncertainty;
        return normalized.add(
                (random.nextDouble() - random.nextDouble()) * deviation,
                (random.nextDouble() - random.nextDouble()) * deviation,
                (random.nextDouble() - random.nextDouble()) * deviation
        ).mul(power);
    }

    @Override
    protected void movementTick() {
        if (inBlock) {
            velocity = Vec.ZERO;
            return;
        }

        this.gravityTickCount = onGround ? 0 : gravityTickCount + 1;
        if (vehicle != null) return;

        this.tickStartPosition = position;
        Vec currentMovement = velocity.div(ServerProperties.SERVER_TICKS_PER_SECOND.get());
        this.tickMovement = beforeMovement(currentMovement);

        final Block.Getter chunkCache = new ChunkCache(instance, currentChunk, Block.STONE);
        PhysicsResult result = CollisionUtils.handlePhysics(
                chunkCache, getBoundingBox(), position, tickMovement,
                projectilePhysicsResult, true);

        Chunk finalChunk = ChunkUtils.retrieve(instance, currentChunk, result.newPosition());
        if (!ChunkUtils.isLoaded(finalChunk)) {
            projectilePhysicsResult = null;
            return;
        }

        this.projectilePhysicsResult = result;
        onGround = result.isOnGround();
        velocity = afterMovement(tickMovement).mul(ServerProperties.SERVER_TICKS_PER_SECOND.get());
        refreshPosition(result.newPosition(), true, false);
        updateProjectileRotation(tickMovement);
    }

    /**
     * Applies projectile-specific forces before this tick's movement.
     * @param movement current movement in blocks per tick
     * @return movement to sweep this tick
     */
    protected @NotNull Vec beforeMovement(@NotNull Vec movement) {
        return movement;
    }

    /**
     * Applies projectile-specific forces after this tick's movement.
     * @param movement movement used for this tick's sweep
     * @return movement to retain for the next tick
     */
    protected @NotNull Vec afterMovement(@NotNull Vec movement) {
        return movement;
    }

    /**
     * Returns whether the projectile is currently in water or a waterlogged block.
     * @return true when submerged in water
     */
    protected final boolean isInWater() {
        Block block = instance.getBlock(position, Block.Getter.Condition.TYPE);
        return block.compare(Block.WATER)
                || block.compare(Block.BUBBLE_COLUMN)
                || "true".equals(block.getProperty("waterlogged"));
    }

    /**
     * Returns whether this projectile is currently embedded in a block.
     * @return true when embedded
     */
    public final boolean isInBlock() {
        return inBlock;
    }

    private void updateProjectileRotation(Vec movement) {
        if (movement.isZero()) return;
        position = withProjectileView(movement, position);
    }

    private static Pos withProjectileView(Vec movement, Point position) {
        float yaw = (float) Math.toDegrees(Math.atan2(movement.x(), movement.z()));
        float pitch = (float) Math.toDegrees(Math.atan2(movement.y(), Math.hypot(movement.x(), movement.z())));
        return new Pos(position.x(), position.y(), position.z(), yaw, pitch);
    }

    private void callBlockCollision() {
        if (!projectilePhysicsResult.hasCollision()) return;

        for (int axis = 0; axis < 3; axis++) {
            Point hitPoint = projectilePhysicsResult.collisionPoints()[axis];
            Point blockPosition = projectilePhysicsResult.collisionShapePositions()[axis];
            if (hitPoint == null || blockPosition == null) continue;

            Block hitBlock = instance.getBlock(blockPosition, Block.Getter.Condition.TYPE);
            handleBlockCollision(hitBlock, hitPoint, tickStartPosition);
            return;
        }
    }

    /**
     * Dispatches a block collision event.
     * @param pos exact collision position
     * @param hitBlock struck block
     * @return true unless the event was cancelled
     */
    protected final boolean callBlockCollisionEvent(@NotNull Pos pos, @NotNull Block hitBlock) {
        ProjectileCollideWithBlockEvent event = new ProjectileCollideWithBlockEvent(this, pos, hitBlock);
        EventDispatcher.call(event);
        return !event.isCancelled();
    }

    /**
     * Dispatches an entity collision event and applies the default removal behavior.
     * @param pos exact collision position
     * @param entity struck entity
     * @return true when the hit was accepted
     */
    protected boolean callEntityCollisionEvent(@NotNull Pos pos, @NotNull Entity entity) {
        ProjectileCollideWithEntityEvent e = new ProjectileCollideWithEntityEvent(this, pos, entity);
        EventDispatcher.call(e);
        if (!e.isCancelled()) {
            remove();
            return true;
        }

        return false;
    }

    private boolean callEntityCollision() {
        return callEntityCollision(entityCollisionBoundingBox());
    }

    /**
     * Returns the swept hitbox used for entity collisions.
     * @return entity collision hitbox
     */
    protected @NotNull BoundingBox entityCollisionBoundingBox() {
        // Grow the hit margin from zero to 0.3 after an initial grace period.
        int index = (int) Math.clamp(getAliveTicks() - 3, 0, ENTITY_COLLISION_BOXES.length - 1);
        return ENTITY_COLLISION_BOXES[index];
    }

    private static BoundingBox[] createEntityCollisionBoxes() {
        BoundingBox[] boxes = new BoundingBox[7];
        for (int tick = 0; tick < boxes.length; tick++) {
            double margin = tick / 20.0;
            double diameter = Math.max(margin * 2.0, Vec.EPSILON);
            boxes[tick] = new BoundingBox(diameter, diameter, diameter, new Vec(0.0, -margin, 0.0));
        }
        return boxes;
    }

    /**
     * Sweeps a custom hitbox for entity collisions.
     * @param boundingBox projectile hitbox
     * @return true when a hit handler consumed the collision
     */
    protected boolean callEntityCollision(BoundingBox boundingBox) {
        Vec diff = projectilePhysicsResult.newPosition().sub(tickStartPosition).asVec();

        if (!leftShooter) {
            leftShooter = CollisionUtils.checkEntityCollisions(
                    instance, getBoundingBox().growSymmetrically(1.0, 1.0, 1.0),
                    tickStartPosition, diff, 2.0,
                    entity -> entity == shooter, projectilePhysicsResult
            ).isEmpty();
        }

        var collidedEntities = CollisionUtils.checkEntityCollisions(instance, boundingBox, tickStartPosition, diff, 3.0,
                entity -> entity != this && (entity != shooter || leftShooter), projectilePhysicsResult);

        var orderedCollisions = new ArrayList<>(collidedEntities);
        orderedCollisions.sort(null);
        for (var collision : orderedCollisions) {
            if (handleEntityCollision(collision, collision.collisionPoint(), tickStartPosition)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Applies the default block-hit behavior: dispatch, touch, then remove.
     * @param hitBlock struck block
     * @param hitPos exact collision position
     * @param posBefore position at the start of the tick
     */
    protected void handleBlockCollision(Block hitBlock, Point hitPos, Pos posBefore) {
        if (!callBlockCollisionEvent(hitPos.asPos(), hitBlock)) {
            passThroughBlockCollision();
            return;
        }
        touchBlock(hitBlock, hitPos);
        remove();
    }

    /**
     * Invokes the Minestom handler for the block struck by this projectile.
     * @param hitBlock struck block
     * @param hitPos exact collision position
     */
    protected final void touchBlock(@NotNull Block hitBlock, @NotNull Point hitPos) {
        BlockHandler handler = hitBlock.handler();
        if (handler != null) {
            handler.onTouch(new BlockHandler.Touch(hitBlock, instance, hitPos, this));
        }
    }

    /** Continues to the intended end-of-tick position after a cancelled hit. */
    protected final void passThroughBlockCollision() {
        refreshPosition(tickStartPosition.add(tickMovement), true, false);
        projectilePhysicsResult = null;
    }

    /**
     * Handle entity collision
     * @param result the entity that was hit
     * @param hitPos the position where the entity was hit
     * @param posBefore the position before the collision
     * @return true if block collisions should be ignored (i.e. the entity was removed while handling entity collision during the same tick)
     */
    protected boolean handleEntityCollision(EntityCollisionResult result, Point hitPos, Pos posBefore) {
        return callEntityCollisionEvent(hitPos.asPos(), result.entity());
    }
}
