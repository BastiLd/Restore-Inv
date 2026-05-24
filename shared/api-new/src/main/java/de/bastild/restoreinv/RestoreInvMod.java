package de.bastild.restoreinv;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class RestoreInvMod implements ModInitializer {

    public static final String MOD_ID = "restoreinv";

    private final RestoreInvStorage storage = new RestoreInvStorage();
    private MinecraftServer currentServer;

    // Tick-Counter pro Slot, damit Auto-Save serverseitig laeuft.
    private long ticksUntilSlot1 = 0;
    private long ticksUntilSlot2 = 0;

    @Override
    public void onInitialize() {
        // ===== Server-Start: Config laden, Initial-Saves =====
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            currentServer = server;
            storage.loadConfig(server);
            ticksUntilSlot1 = 20L * 60 * Math.max(1, storage.autoSaveInterval1);
            ticksUntilSlot2 = 20L * 60 * Math.max(1, storage.autoSaveInterval2);
            for (ServerPlayerEntity player : storage.getOnlinePlayers(server)) {
                storage.saveInventory(player, 0);
                storage.saveInventory(player, 1);
                storage.onPlayerJoin(player.getUuid(), server);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            storage.saveConfig();
            currentServer = null;
        });

        // ===== Spieler-Join: Last-Saves laden =====
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            storage.onPlayerJoin(handler.getPlayer().getUuid(), server);
        });

        // ===== Auto-Save bei Tod (Slot 3) =====
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
            if (entity instanceof ServerPlayerEntity sp && storage.autoSaveOnDeath) {
                try {
                    storage.saveInventory(sp, 2);
                } catch (Throwable t) {
                    // Defensive: Save-Fehler darf den Tod nicht blockieren.
                    t.printStackTrace();
                }
            }
            return true; // Tod nicht verhindern.
        });

        // ===== Server-Tick: Auto-Save Slot 1 + 2 =====
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (storage.getOnlinePlayers(server).isEmpty()) {
                return;
            }
            ticksUntilSlot1--;
            ticksUntilSlot2--;
            if (ticksUntilSlot1 <= 0) {
                for (ServerPlayerEntity p : storage.getOnlinePlayers(server)) {
                    storage.saveInventory(p, 0);
                }
                ticksUntilSlot1 = 20L * 60 * Math.max(1, storage.autoSaveInterval1);
            }
            if (ticksUntilSlot2 <= 0) {
                for (ServerPlayerEntity p : storage.getOnlinePlayers(server)) {
                    storage.saveInventory(p, 1);
                }
                ticksUntilSlot2 = 20L * 60 * Math.max(1, storage.autoSaveInterval2);
            }
        });

        // ===== Befehle =====
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("restoreInv")
                    .then(CommandManager.literal("1").executes(ctx -> tryRestore(ctx.getSource(), 0)))
                    .then(CommandManager.literal("2").executes(ctx -> tryRestore(ctx.getSource(), 1)))
                    .then(CommandManager.literal("3").executes(ctx -> tryRestore(ctx.getSource(), 2)))
                    .then(CommandManager.literal("save").executes(ctx -> {
                        ServerPlayerEntity p = ctx.getSource().getPlayer();
                        if (p != null) {
                            storage.saveInventory(p, 2);
                            ctx.getSource().sendMessage(Text.literal("Inventory saved to slot 3"));
                        }
                        return 1;
                    }))
                    .then(CommandManager.literal("config").executes(ctx -> {
                        ServerPlayerEntity p = ctx.getSource().getPlayer();
                        if (p != null) storage.openConfigScreen(p);
                        return 1;
                    }))
                    .then(CommandManager.literal("version").executes(ctx -> {
                        String v = net.fabricmc.loader.api.FabricLoader.getInstance()
                                .getModContainer(MOD_ID)
                                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                                .orElse("?");
                        ctx.getSource().sendMessage(
                                Text.literal("RestoreInventory " + v + " (Minecraft "
                                        + ctx.getSource().getServer().getVersion() + ")"));
                        return 1;
                    })));
        });
    }

    private int tryRestore(ServerCommandSource source, int slot) {
        ServerPlayerEntity p = source.getPlayer();
        if (p == null) return 0;
        if (!storage.canRestore(p)) {
            source.sendError(Text.literal("Du hast keine Rechte zum Wiederherstellen."));
            return 0;
        }
        storage.restoreInventory(p, slot);
        source.sendMessage(Text.literal("Inventory restored from slot " + (slot + 1)));
        return 1;
    }
}
