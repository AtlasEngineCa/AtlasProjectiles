package ca.atlasengine.projectiles;

import net.minestom.server.ServerFlag;
import net.minestom.server.collision.*;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.entity.EntityTickEvent;
import net.minestom.server.event.entity.projectile.ProjectileCollideWithBlockEvent;
import net.minestom.server.event.entity.projectile.ProjectileCollideWithEntityEvent;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.block.Block;
import net.minestom.server.utils.chunk.ChunkCache;
import net.minestom.server.utils.chunk.ChunkUtils;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractProjectile extends Entity implements Projectile {
    protected final Entity shooter;
    protected PhysicsResult previousPhysicsResult;
    protected Pos previousPosition;

    public AbstractProjectile(EntityType type, Entity shooter) {
        super(type);
        this.shooter = shooter;
    }

    @Override
    public void shoot(Point from, double power, double spread) {
        var to = from.add(shooter.getPosition().direction());
        shoot(from, to, power, spread);
    }

    protected PhysicsResult computePhysics(@NotNull Pos entityPosition, @NotNull Vec currentVelocity, @NotNull Block.Getter blockGetter, @NotNull Aerodynamics aerodynamics) {
        var newVelocity = updateVelocity(entityPosition, currentVelocity, blockGetter, aerodynamics, true, false, onGround, false);

        var newPhysicsResult = CollisionUtils.handlePhysics(
                blockGetter,
                this.boundingBox,
                entityPosition, newVelocity,
                previousPhysicsResult, true
        );

        previousPhysicsResult = newPhysicsResult;
        return newPhysicsResult;
    }

    @Override
    protected void movementTick() {
        this.gravityTickCount = onGround ? 0 : gravityTickCount + 1;
        if (vehicle != null) return;

        this.previousPosition = position;
        final Block.Getter chunkCache = new ChunkCache(instance, currentChunk, Block.STONE);
        PhysicsResult result = computePhysics(
                position, velocity.div(ServerFlag.SERVER_TICKS_PER_SECOND),
                chunkCache, getAerodynamics());

        Chunk finalChunk = ChunkUtils.retrieve(instance, currentChunk, result.newPosition());
        if (!ChunkUtils.isLoaded(finalChunk)) return;

        onGround = result.isOnGround();

        if (!result.hasCollision()) {
            velocity = previousPhysicsResult.newVelocity().mul(ServerFlag.SERVER_TICKS_PER_SECOND).mul(0.99);
        }

        refreshPosition(result.newPosition(), true, false);
        if (hasVelocity()) sendPacketToViewers(getVelocityPacket());
    }

    protected void callBlockCollision() {
        if (previousPhysicsResult.hasCollision()) {
            Block hitBlock = null;
            Point hitPoint = null;
            if (previousPhysicsResult.collisionShapes()[0] instanceof ShapeImpl) {
                hitBlock = instance.getBlock(previousPhysicsResult.collisionPoints()[0].sub(0, Vec.EPSILON, 0), Block.Getter.Condition.TYPE);
                hitPoint = previousPhysicsResult.collisionPoints()[0];
            }
            if (previousPhysicsResult.collisionShapes()[1] instanceof ShapeImpl) {
                hitBlock = instance.getBlock(previousPhysicsResult.collisionPoints()[1].sub(0, Vec.EPSILON, 0), Block.Getter.Condition.TYPE);
                hitPoint = previousPhysicsResult.collisionPoints()[1];
            }
            if (previousPhysicsResult.collisionShapes()[2] instanceof ShapeImpl) {
                hitBlock = instance.getBlock(previousPhysicsResult.collisionPoints()[2].sub(0, Vec.EPSILON, 0), Block.Getter.Condition.TYPE);
                hitPoint = previousPhysicsResult.collisionPoints()[2];
            }

            if (hitBlock == null) return;
            handleBlockCollision(hitBlock, hitPoint, previousPosition);
        }
    }

    protected void callBlockCollisionEvent(@NotNull Pos pos, Block hitBlock) {
        var e = new ProjectileCollideWithBlockEvent(this, pos, hitBlock);
        EventDispatcher.call(e);
    }

    protected boolean callEntityCollisionEvent(@NotNull Pos pos, @NotNull Entity entity) {
        ProjectileCollideWithEntityEvent e = new ProjectileCollideWithEntityEvent(this, pos, entity);
        EventDispatcher.call(e);
        if (!e.isCancelled()) {
            remove();
            return true;
        }

        return false;
    }

    protected boolean callEntityCollision() {
        return callEntityCollision(boundingBox);
    }

    protected boolean callEntityCollision(BoundingBox boundingBox) {
        if (previousPhysicsResult == null) return false;
        var diff = previousPhysicsResult.newPosition().sub(previousPosition).asVec();

        var collidedEntities = CollisionUtils.checkEntityCollisions(instance, boundingBox, previousPosition, diff, diff.length(),
                entity -> entity != shooter && entity != this, previousPhysicsResult);

        var arr = collidedEntities.stream().sorted().toList();
        if (!arr.isEmpty()) {
            for (var collision : arr) {
                if (handleEntityCollision(collision, previousPhysicsResult.newPosition(), previousPosition)) {
                    return true;
                }
            }
        }

        return false;
    }

    protected void updatePosition(long time) {
        if (instance == null || isRemoved() || !ChunkUtils.isLoaded(currentChunk)) return;

        movementTick();
        super.update(time);
        EventDispatcher.call(new EntityTickEvent(this));
    }

    /**
     * Handle entity collision
     * @param hitEntity the entity that was hit
     * @param hitPos the position where the entity was hit
     * @param posBefore the position before the collision
     * @return true if block collisions should be ignored
     */
    protected abstract boolean handleEntityCollision(EntityCollisionResult hitEntity, Point hitPos, Pos posBefore);

    protected abstract void handleBlockCollision(Block hitBlock, Point hitPos, Pos posBefore);

    protected abstract @NotNull Vec updateVelocity(@NotNull Pos entityPosition, @NotNull Vec currentVelocity, @NotNull Block.@NotNull Getter blockGetter, @NotNull Aerodynamics aerodynamics, boolean positionChanged, boolean entityFlying, boolean entityOnGround, boolean entityNoGravity);
}
