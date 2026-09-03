package ca.atlasengine.projectiles;

import ca.atlasengine.projectiles.entities.ArrowProjectile;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.item.PlayerCancelItemUseEvent;
import net.minestom.server.event.trait.EntityEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.sound.SoundEvent;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;

/** Installs bow charge and launch behavior on an event node. */
public final class BowModule {
    /**
     * Installs bow-release behavior.
     * @param node node on which player item-use events are delivered
     * @param arrowSupplier factory receiving the player and used bow
     */
    public BowModule(@NotNull EventNode<EntityEvent> node,
                     @NotNull BiFunction<Player, ItemStack, ? extends AbstractProjectile> arrowSupplier) {
        node.addListener(PlayerCancelItemUseEvent.class, event -> {
            if (event.getItemStack().material() != Material.BOW) return;
            Player player = event.getPlayer();
            double power = getPowerForTime(event.getUseDuration());

            if (power < 0.1) return;

            var projectile = arrowSupplier.apply(player, event.getItemStack());
            if (projectile instanceof ArrowProjectile arrow && power == 1.0) arrow.setCritical(true);

            Pos shootPosition = player.getPosition().add(0.0, player.getEyeHeight() - 0.1, 0.0);
            projectile.shoot(shootPosition, power * 3.0, 1.0);
            player.getInstance().playSound(Sound.sound(
                    SoundEvent.ENTITY_ARROW_SHOOT, Sound.Source.PLAYER,
                    1.0f, getRandomPitchFromPower(power)), player);
        });
    }

    /**
     * Returns the bow power curve for a charge duration in ticks.
     * @param ticks charge duration
     * @return value from zero through one
     */
    public static double getPowerForTime(long ticks) {
        double charge = ticks / 20.0;
        return Math.min((charge * charge + charge * 2.0) / 3.0, 1.0);
    }

    private float getRandomPitchFromPower(double power) {
        return (float) (1.0f / (ThreadLocalRandom.current().nextFloat() * 0.4f + 1.2f) + power * 0.5f);
    }
}
