package ca.atlasengine.projectiles.entities;

import ca.atlasengine.projectiles.AbstractProjectile;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.metadata.projectile.AbstractArrowMeta;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.NotNull;

/** An arrow with air/water drag and 0.05 gravity per tick. */
public class ArrowProjectile extends AbstractProjectile {
    private boolean critical = false;
    private long stuckAtTick = -1;

    /**
     * Gets the critical flag.
     * @return whether critical metadata is enabled
     */
    public boolean isCritical() {
        return critical;
    }

    /**
     * Updates critical metadata.
     * @param critical whether critical metadata should be enabled
     */
    public void setCritical(boolean critical) {
        this.critical = critical;
        if (getEntityMeta() instanceof AbstractArrowMeta arrowMeta) {
            arrowMeta.setCritical(critical);
        }
    }

    /**
     * Creates an unspawned arrow.
     * @param type arrow-compatible entity type
     * @param shooter projectile owner
     */
    public ArrowProjectile(@NotNull EntityType type, @NotNull Entity shooter) {
        super(type, shooter);
        // A near-point sweep keeps block impacts precise with Minestom's
        // collision engine.
        setBoundingBox(0.01, 0.01, 0.01);
    }

    @Override
    public void tick(long time) {
        super.tick(time);
        if (inBlock && stuckAtTick >= 0 && getAliveTicks() - stuckAtTick >= 1_200) {
            remove();
        }
    }

    @Override
    protected @NotNull Vec afterMovement(@NotNull Vec movement) {
        Vec dragged = movement.mul(isInWater() ? 0.6 : 0.99);
        return hasNoGravity() ? dragged : dragged.sub(0.0, 0.05, 0.0);
    }

    @Override
    protected void handleBlockCollision(Block hitBlock, Point hitPos, Pos posBefore) {
        if (!callBlockCollisionEvent(hitPos.asPos(), hitBlock)) {
            passThroughBlockCollision();
            return;
        }
        touchBlock(hitBlock, hitPos);
        if (isRemoved()) return;

        velocity = Vec.ZERO;
        inBlock = true;
        stuckAtTick = getAliveTicks();
        position = hitPos.asPos().withView(position);
        if (getEntityMeta() instanceof AbstractArrowMeta arrowMeta) {
            arrowMeta.setInGround(true);
        }
        synchronizeNextTick();
    }
}
