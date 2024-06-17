package ca.atlasengine.projectiles;

import net.minestom.server.ServerFlag;
import net.minestom.server.collision.Aerodynamics;
import net.minestom.server.collision.CollisionUtils;
import net.minestom.server.collision.PhysicsResult;
import net.minestom.server.collision.ShapeImpl;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.entity.EntityTickEvent;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.block.Block;
import net.minestom.server.utils.chunk.ChunkCache;
import net.minestom.server.utils.chunk.ChunkUtils;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractProjectile extends Entity implements Projectile {
    protected final Entity shooter;
    private PhysicsResult previousPhysicsResult;

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

        final Block.Getter chunkCache = new ChunkCache(instance, currentChunk, Block.STONE);
        PhysicsResult result = computePhysics(
                position, velocity.div(ServerFlag.SERVER_TICKS_PER_SECOND),
                chunkCache, getAerodynamics());

        Chunk finalChunk = ChunkUtils.retrieve(instance, currentChunk, result.newPosition());
        if (!ChunkUtils.isLoaded(finalChunk)) return;

        if (result.hasCollision()) {
            Block hitBlock = null;
            Point hitPoint = null;
            if (result.collisionShapes()[0] instanceof ShapeImpl block) {
                hitBlock = block.block();
                hitPoint = result.collisionPoints()[0];
            }
            if (result.collisionShapes()[1] instanceof ShapeImpl block) {
                hitBlock = block.block();
                hitPoint = result.collisionPoints()[1];
            }
            if (result.collisionShapes()[2] instanceof ShapeImpl block) {
                hitBlock = block.block();
                hitPoint = result.collisionPoints()[2];
            }

            if (hitBlock == null) return;
            handleBlockCollision(hitBlock, hitPoint, position);
        } else {
            velocity = result.newVelocity().mul(ServerFlag.SERVER_TICKS_PER_SECOND).mul(0.99);
        }

        onGround = result.isOnGround();

        refreshPosition(result.newPosition(), true, false);
        if (hasVelocity()) sendPacketToViewers(getVelocityPacket());
    }

    protected void checkEntityCollision(Pos previousPos, Pos currentPos) {
        var diff = currentPos.sub(previousPos).asVec();

        PhysicsResult entityResult = CollisionUtils.checkEntityCollisions(instance, boundingBox, previousPos, diff, diff.length(),
                entity -> entity != shooter && entity != this, previousPhysicsResult);

        if (entityResult.hasCollision()) {
            Entity hitEntity = (Entity) entityResult.collisionShapes()[0];
            handleEntityCollision(hitEntity, entityResult.newPosition(), currentPos);
        }
    }

    protected void updatePosition(long time) {
        if (instance == null || isRemoved() || !ChunkUtils.isLoaded(currentChunk)) return;

        movementTick();
        super.update(time);
        EventDispatcher.call(new EntityTickEvent(this));
    }

    abstract protected void handleBlockCollision(Block hitBlock, Point hitPos, Pos posBefore);
    abstract protected void handleEntityCollision(Entity hitEntity, Point hitPos, Pos posBefore);
    abstract protected @NotNull Vec updateVelocity(@NotNull Pos entityPosition, @NotNull Vec currentVelocity, @NotNull Block.@NotNull Getter blockGetter, @NotNull Aerodynamics aerodynamics, boolean positionChanged, boolean entityFlying, boolean entityOnGround, boolean entityNoGravity);
}
