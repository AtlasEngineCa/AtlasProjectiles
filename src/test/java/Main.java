import ca.atlasengine.projectiles.BowModule;
import ca.atlasengine.projectiles.entities.ArrowProjectile;
import ca.atlasengine.projectiles.entities.FollowProjectile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.adventure.audience.Audiences;
import net.minestom.server.command.CommandManager;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.*;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.*;
import net.minestom.server.event.server.ServerTickMonitorEvent;
import net.minestom.server.extras.lan.OpenToLAN;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.monitoring.TickMonitor;
import net.minestom.server.timer.TaskSchedule;
import net.minestom.server.utils.MathUtils;
import net.minestom.server.world.DimensionType;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class Main {
    public static void main(String[] args) {
        MinecraftServer minecraftServer = MinecraftServer.init();

        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        InstanceContainer lobby = instanceManager.createInstanceContainer(DimensionType.OVERWORLD);
        lobby.setChunkSupplier(LightingChunk::new);
        lobby.enableAutoChunkLoad(true);
        lobby.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.STONE));
        lobby.setTimeRate(0);
        instanceManager.registerInstance(lobby);

        Entity zombie = new LivingEntity(EntityType.ZOMBIE);
        zombie.setInstance(lobby, new Pos(0.5, 16, 0.5));

        // Commands
        {
            CommandManager manager = MinecraftServer.getCommandManager();
            manager.setUnknownCommandCallback((sender, c) -> sender.sendMessage("Command not found."));
        }

        // Events
        {
            GlobalEventHandler handler = MinecraftServer.getGlobalEventHandler();

            // Login
            handler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
                final Player player = event.getPlayer();
                player.setRespawnPoint(new Pos(0.5, 16, 0.5));
                event.setSpawningInstance(lobby);
            });

            handler.addListener(PlayerSpawnEvent.class, event -> {
                if (!event.isFirstSpawn()) return;
                final Player player = event.getPlayer();

                var bow = ItemStack.builder(Material.BOW)
                                  .set(DataComponents.CHARGED_PROJECTILES, List.of(ItemStack.of(Material.ARROW)))
                                  .build();

                player.setItemInMainHand(bow);

                player.setGameMode(GameMode.CREATIVE);
                player.setEnableRespawnScreen(false);
                player.getInventory().addItemStack(ItemStack.of(Material.ARROW, 64));
                player.getInventory().addItemStack(ItemStack.of(Material.FIRE_CHARGE, 64));

                Audiences.all().sendMessage(Component.text(
                        player.getUsername() + " has joined",
                        NamedTextColor.GREEN
                ));

                player.eventNode().addListener(PlayerUseItemEvent.class, e -> {
                    if (e.getHand() != PlayerHand.MAIN) return;
                    if (e.getItemStack().material() == Material.FIRE_CHARGE) {
                        new FollowProjectile(EntityType.SNOWBALL, e.getPlayer(), e.getPlayer(), 0.04f, 0.001f).shoot(e.getPlayer().getPosition().add(0, e.getPlayer().getEyeHeight(), 0).asVec(), 1, 1);
                    }
                });

                new BowModule(player.eventNode(), (p, i) -> new ArrowProjectile(EntityType.ARROW, p));
            });

            // Logout
            handler.addListener(PlayerDisconnectEvent.class, event -> Audiences.all().sendMessage(Component.text(
                    event.getPlayer().getUsername() + " has left",
                    NamedTextColor.RED
            )));

            // Chat
            handler.addListener(PlayerChatEvent.class, chatEvent -> {
                chatEvent.setFormattedMessage(Component.text(chatEvent.getEntity().getUsername())
                                                      .append(Component.text(" | ", NamedTextColor.DARK_GRAY)
                                                                      .append(Component.text(chatEvent.getRawMessage(), NamedTextColor.WHITE))));
            });

            // Monitoring
            AtomicReference<TickMonitor> lastTick = new AtomicReference<>();
            handler.addListener(ServerTickMonitorEvent.class, event -> lastTick.set(event.getTickMonitor()));

            // Header/footer
            MinecraftServer.getSchedulerManager().scheduleTask(() -> {
                Collection<Player> players = MinecraftServer.getConnectionManager().getOnlinePlayers();
                if (players.isEmpty()) return;

                final Runtime runtime = Runtime.getRuntime();
                final TickMonitor tickMonitor = lastTick.get();
                final long ramUsage = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;

                final Component header = Component.newline()
                                                 .append(Component.text("RAM USAGE: " + ramUsage + " MB", NamedTextColor.GRAY).append(Component.newline())
                                                                 .append(Component.text("TICK TIME: " + MathUtils.round(tickMonitor.getTickTime(), 2) + "ms", NamedTextColor.GRAY))).append(Component.newline());

                final Component footer = Component.newline()
                                                 .append(Component.text("          Projectile Demo          ")
                                                                 .color(TextColor.color(57, 200, 73))
                                                                 .append(Component.newline()));

                Audiences.players().sendPlayerListHeaderAndFooter(header, footer);
            }, TaskSchedule.tick(10), TaskSchedule.tick(10));
        }

        OpenToLAN.open();

        minecraftServer.start("0.0.0.0", 25565);
        System.out.println("Server startup done!");
    }
}
