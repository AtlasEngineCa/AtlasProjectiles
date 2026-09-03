package ca.atlasengine.projectiles.entities;

import ca.atlasengine.projectiles.AbstractProjectile;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.utils.Direction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A shulker bullet using axis-by-axis path selection and steering.
 */
public final class ShulkerBulletProjectile extends AbstractProjectile {
    private static final double TARGET_SPEED = 0.15;
    private static final double STEERING = 0.2;
    private static final double ACCELERATION = 1.025;
    private static final double GRAVITY = 0.04;

    private final @NotNull Entity target;
    private @Nullable Direction currentDirection;
    private Vec targetVelocity = Vec.ZERO;
    private int flightSteps;

    /**
     * Creates an unspawned shulker bullet.
     * @param type shulker-bullet-compatible entity type
     * @param shooter projectile owner
     * @param target entity to pursue
     */
    public ShulkerBulletProjectile(@NotNull EntityType type, @NotNull Entity shooter, @NotNull Entity target) {
        super(type, shooter);
        this.target = target;
        setNoGravity(true);
    }

    /**
     * Spawns the bullet at rest; steering supplies its initial movement.
     * @param from spawn position
     */
    public void spawn(@NotNull Point from) {
        shoot(from, from.add(0.0, 1.0, 0.0), 0.0, 0.0);
    }

    /**
     * Gets the pursuit target.
     * @return entity being pursued
     */
    public @NotNull Entity getTarget() {
        return target;
    }

    @Override
    protected @NotNull Vec beforeMovement(@NotNull Vec movement) {
        if (target.isRemoved() || target.getInstance() != getInstance()) {
            return movement.sub(0.0, GRAVITY, 0.0);
        }

        if (targetVelocity.isZero() || shouldSelectNewDirection()) {
            selectNextMoveDirection(axisOf(currentDirection));
        }

        targetVelocity = new Vec(
                Math.clamp(targetVelocity.x() * ACCELERATION, -1.0, 1.0),
                Math.clamp(targetVelocity.y() * ACCELERATION, -1.0, 1.0),
                Math.clamp(targetVelocity.z() * ACCELERATION, -1.0, 1.0)
        );
        return movement.add(targetVelocity.sub(movement).mul(STEERING));
    }

    private boolean shouldSelectNewDirection() {
        if (--flightSteps <= 0) return true;
        if (currentDirection == null) return false;

        Point nextBlock = getPosition().add(currentDirection.vec());
        if (!getInstance().getBlock(nextBlock).isAir()) return true;

        int currentX = getPosition().blockX();
        int currentY = getPosition().blockY();
        int currentZ = getPosition().blockZ();
        int targetX = target.getPosition().blockX();
        int targetY = target.getPosition().blockY();
        int targetZ = target.getPosition().blockZ();
        return switch (axisOf(currentDirection)) {
            case X -> currentX == targetX;
            case Y -> currentY == targetY;
            case Z -> currentZ == targetZ;
            case NONE -> false;
        };
    }

    private void selectNextMoveDirection(Axis avoidedAxis) {
        int currentX = getPosition().blockX();
        int currentY = getPosition().blockY();
        int currentZ = getPosition().blockZ();
        int targetX = target.getPosition().blockX();
        double targetHeightOffset = target.getBoundingBox().height() * 0.5;
        int targetY = (int) Math.floor(target.getPosition().y() + targetHeightOffset);
        int targetZ = target.getPosition().blockZ();

        Vec targetPoint = new Vec(
                targetX + 0.5,
                targetY + targetHeightOffset,
                targetZ + 0.5
        );
        Vec toTarget = targetPoint.sub(getPosition()).asVec();
        Vec targetBlockCenter = new Vec(targetX + 0.5, targetY + 0.5, targetZ + 0.5);
        if (targetBlockCenter.sub(getPosition()).asVec().lengthSquared() < 4.0) {
            currentDirection = null;
            targetVelocity = toTarget.isZero() ? Vec.ZERO : toTarget.normalize().mul(TARGET_SPEED);
            flightSteps = 10 + ThreadLocalRandom.current().nextInt(5) * 10;
            return;
        }

        List<Direction> choices = new ArrayList<>(3);
        addChoice(choices, avoidedAxis, Axis.X, currentX, targetX, Direction.WEST, Direction.EAST);
        addChoice(choices, avoidedAxis, Axis.Y, currentY, targetY, Direction.DOWN, Direction.UP);
        addChoice(choices, avoidedAxis, Axis.Z, currentZ, targetZ, Direction.NORTH, Direction.SOUTH);

        if (choices.isEmpty()) {
            List<Direction> openDirections = new ArrayList<>(6);
            for (Direction direction : Direction.values()) {
                if (getInstance().getBlock(getPosition().add(direction.vec())).isAir()) {
                    openDirections.add(direction);
                }
            }
            choices = openDirections;
        }

        currentDirection = choices.isEmpty()
                ? Direction.values()[ThreadLocalRandom.current().nextInt(Direction.values().length)]
                : choices.get(ThreadLocalRandom.current().nextInt(choices.size()));

        Vec destination = getPosition().add(currentDirection.vec()).asVec();
        Vec delta = destination.sub(getPosition()).asVec();
        targetVelocity = delta.isZero() ? Vec.ZERO : delta.normalize().mul(TARGET_SPEED);
        flightSteps = 10 + ThreadLocalRandom.current().nextInt(5) * 10;
    }

    private void addChoice(List<Direction> choices, Axis avoidedAxis, Axis axis,
                           int current, int destination, Direction negative, Direction positive) {
        if (avoidedAxis == axis || current == destination) return;
        Direction direction = current < destination ? positive : negative;
        if (getInstance().getBlock(getPosition().add(direction.vec())).isAir()) {
            choices.add(direction);
        }
    }

    private static Axis axisOf(@Nullable Direction direction) {
        if (direction == null) return Axis.NONE;
        if (direction.vertical()) return Axis.Y;
        return direction == Direction.EAST || direction == Direction.WEST ? Axis.X : Axis.Z;
    }

    private enum Axis { X, Y, Z, NONE }
}
