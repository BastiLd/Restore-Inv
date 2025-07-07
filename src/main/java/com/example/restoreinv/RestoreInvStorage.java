package com.example.restoreinv;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.inventory.Inventories;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.text.Text;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;

public class RestoreInvStorage {
    private final Map<UUID, ItemStack[][]> playerInventories = new ConcurrentHashMap<>();
    private static final int SLOTS = 3;
    private static final String SAVE_DIR = "restoreinv";
    public int autoSaveInterval1 = 1; // Minuten für Slot 1
    public int autoSaveInterval2 = 5; // Minuten für Slot 2
    public boolean showSaveMessages = true; // New field for chat message toggle

    // New: Store last 3 saves per slot per player
    public final Map<UUID, List<List<ItemStack[]>>> lastSaves = new ConcurrentHashMap<>();

    // Per-player preview setting
    private final Map<UUID, Boolean> previewEnabled = new ConcurrentHashMap<>();

    public void saveInventory(ServerPlayerEntity player, int slot) {
        if (slot < 0 || slot >= SLOTS)
            return;

        UUID playerId = player.getUuid();
        ItemStack[][] inventories = playerInventories.computeIfAbsent(playerId, k -> new ItemStack[SLOTS][]);

        // Save main inventory
        ItemStack[] mainInv = new ItemStack[player.getInventory().size()];
        for (int i = 0; i < player.getInventory().size(); i++) {
            mainInv[i] = player.getInventory().getStack(i).copy();
        }

        // Save armor
        ItemStack[] armor = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            armor[i] = player.getInventory().getArmorStack(i).copy();
        }

        // Save offhand
        ItemStack offhand = player.getInventory().getStack(player.getInventory().size() - 1).copy();

        // Combine all inventories
        ItemStack[] combined = new ItemStack[mainInv.length + armor.length + 1];
        System.arraycopy(mainInv, 0, combined, 0, mainInv.length);
        System.arraycopy(armor, 0, combined, mainInv.length, armor.length);
        combined[combined.length - 1] = offhand;

        inventories[slot] = combined;

        // New: Update lastSaves
        List<List<ItemStack[]>> slotSaves = lastSaves.computeIfAbsent(playerId, k -> new LinkedList<>());
        List<ItemStack[]> savesList = slotSaves.get(slot);
        if (savesList == null) {
            savesList = new LinkedList<>();
            slotSaves.add(slot, savesList);
        }
        savesList.add(0, Arrays.stream(combined).map(ItemStack::copy).toArray(ItemStack[]::new)); // Deep copy
        while (savesList.size() > 3)
            savesList.remove(savesList.size() - 1);

        // Save to file
        saveToFile(playerId, slot, combined, (RegistryWrapper.WrapperLookup) player.getServer().getRegistryManager());
        saveLastSavesToFile(playerId, (RegistryWrapper.WrapperLookup) player.getServer().getRegistryManager()); // Persist
                                                                                                                // last
                                                                                                                // saves

        // Sende Chat-Nachricht
        player.sendMessage(net.minecraft.text.Text.literal("Slot " + (slot + 1) + " gespeichert!"), false);
    }

    public void restoreInventory(ServerPlayerEntity player, int slot) {
        if (slot < 0 || slot >= SLOTS)
            return;

        UUID playerId = player.getUuid();
        ItemStack[][] inventories = playerInventories.get(playerId);
        if (inventories == null || inventories[slot] == null) {
            // Try to load from file
            ItemStack[] loaded = loadFromFile(playerId, slot,
                    (RegistryWrapper.WrapperLookup) player.getServer().getRegistryManager());
            if (loaded == null)
                return;
            inventories = playerInventories.computeIfAbsent(playerId, k -> new ItemStack[SLOTS][]);
            inventories[slot] = loaded;
        }

        ItemStack[] saved = inventories[slot];
        if (saved == null)
            return;

        // Restore main inventory
        int mainInvSize = player.getInventory().size() - 1; // -1 for offhand
        for (int i = 0; i < mainInvSize; i++) {
            player.getInventory().setStack(i, saved[i].copy());
        }

        // Restore armor
        for (int i = 0; i < 4; i++) {
            player.getInventory().setStack(mainInvSize + i, saved[mainInvSize + i].copy());
        }

        // Restore offhand
        player.getInventory().setStack(player.getInventory().size() - 1, saved[saved.length - 1].copy());
    }

    private void saveToFile(UUID playerId, int slot, ItemStack[] inventory,
            RegistryWrapper.WrapperLookup registryLookup) {
        try {
            Path saveDir = Paths.get(SAVE_DIR);
            if (!java.nio.file.Files.exists(saveDir)) {
                java.nio.file.Files.createDirectories(saveDir);
            }

            Path playerDir = saveDir.resolve(playerId.toString());
            if (!java.nio.file.Files.exists(playerDir)) {
                java.nio.file.Files.createDirectories(playerDir);
            }

            Path saveFile = playerDir.resolve("slot_" + slot + ".dat");
            NbtCompound nbt = new NbtCompound();
            DefaultedList<ItemStack> list = DefaultedList.ofSize(inventory.length, ItemStack.EMPTY);
            for (int i = 0; i < inventory.length; i++) {
                list.set(i, inventory[i]);
            }
            Inventories.writeNbt(nbt, list, registryLookup);
            NbtIo.write(nbt, saveFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private ItemStack[] loadFromFile(UUID playerId, int slot, RegistryWrapper.WrapperLookup registryLookup) {
        try {
            Path saveFile = Paths.get(SAVE_DIR, playerId.toString(), "slot_" + slot + ".dat");
            if (!java.nio.file.Files.exists(saveFile))
                return null;

            NbtCompound nbt = NbtIo.read(saveFile);
            if (nbt == null)
                return null;

            int size = nbt.contains("Items") ? nbt.getList("Items", 10).size() : 0;
            DefaultedList<ItemStack> list = DefaultedList.ofSize(size, ItemStack.EMPTY);
            Inventories.readNbt(nbt, list, registryLookup);
            return list.toArray(new ItemStack[0]);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<ServerPlayerEntity> getOnlinePlayers(MinecraftServer server) {
        if (server == null)
            return Collections.emptyList();
        return server.getPlayerManager().getPlayerList();
    }

    public void openConfigScreen(ServerPlayerEntity player) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inventory, playerEntity) -> {
            return new RestoreInvConfigScreenHandler(syncId, inventory, this, playerEntity);
        }, Text.literal("RestoreInv Config")));
        // CLIENT: Open the custom screen after the handler is opened
        // This requires a client-side mod or packet to trigger
        // MinecraftClient.setScreen(new RestoreInvConfigScreen(...))
        // See Fabric API docs for syncing custom screens

        // Fülle die GUI mit Konfigurationsoptionen
        ScreenHandler screenHandler = player.currentScreenHandler;
        if (screenHandler instanceof GenericContainerScreenHandler) {
            GenericContainerScreenHandler container = (GenericContainerScreenHandler) screenHandler;

            // Slot 1 Intervall
            ItemStack slot1Stack = new ItemStack(Items.CLOCK);
            slot1Stack.set(DataComponentTypes.CUSTOM_NAME,
                    Text.literal("Slot 1 Interval: " + autoSaveInterval1 + " min"));
            slot1Stack.set(DataComponentTypes.LORE, new LoreComponent(
                    List.of(Text.literal("Zeigt das aktuelle Intervall für automatisches Speichern in Slot 1"))));
            container.getInventory().setStack(0, slot1Stack);

            // Slot 2 Intervall
            ItemStack slot2Stack = new ItemStack(Items.CLOCK);
            slot2Stack.set(DataComponentTypes.CUSTOM_NAME,
                    Text.literal("Slot 2 Interval: " + autoSaveInterval2 + " min"));
            slot2Stack.set(DataComponentTypes.LORE, new LoreComponent(
                    List.of(Text.literal("Zeigt das aktuelle Intervall für automatisches Speichern in Slot 2"))));
            container.getInventory().setStack(9, slot2Stack);

            // Chat Message Toggle
            ItemStack chatMsgToggle = new ItemStack(showSaveMessages ? Items.LIME_WOOL : Items.RED_WOOL);
            chatMsgToggle.set(DataComponentTypes.CUSTOM_NAME,
                    Text.literal("Chat Messages: " + (showSaveMessages ? "ON" : "OFF")));
            chatMsgToggle.set(DataComponentTypes.LORE,
                    new LoreComponent(List.of(Text.literal("Klicke, um Chat-Benachrichtigungen beim Speichern zu "
                            + (showSaveMessages ? "deaktivieren" : "aktivieren")))));
            container.getInventory().setStack(3, chatMsgToggle);

            // Last Saves Page Icon
            ItemStack lastSavesIcon = new ItemStack(Items.BOOK);
            lastSavesIcon.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Last Saves"));
            lastSavesIcon.set(DataComponentTypes.LORE, new LoreComponent(
                    List.of(Text.literal("Klicke, um die letzten 3 Speicherstände pro Slot zu sehen."))));
            container.getInventory().setStack(4, lastSavesIcon);

            // Admin Page Icon (only for OPs)
            if (player.hasPermissionLevel(4)) {
                ItemStack adminIcon = new ItemStack(Items.PLAYER_HEAD);
                adminIcon.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Admin Panel"));
                adminIcon.set(DataComponentTypes.LORE,
                        new LoreComponent(List.of(Text.literal("Klicke, um das Admin-Panel zu öffnen."))));
                container.getInventory().setStack(5, adminIcon);
            }

            // Erhöhen/Verringern Buttons
            ItemStack increase1 = new ItemStack(Items.EMERALD);
            increase1.set(DataComponentTypes.CUSTOM_NAME, Text.literal("+1 min"));
            increase1.set(DataComponentTypes.LORE, new LoreComponent(
                    List.of(Text.literal("Klicke, um das Intervall für Slot 1 um 1 Minute zu erhöhen"))));
            container.getInventory().setStack(1, increase1);

            ItemStack decrease1 = new ItemStack(Items.REDSTONE);
            decrease1.set(DataComponentTypes.CUSTOM_NAME, Text.literal("-1 min"));
            decrease1.set(DataComponentTypes.LORE, new LoreComponent(
                    List.of(Text.literal("Klicke, um das Intervall für Slot 1 um 1 Minute zu verringern"))));
            container.getInventory().setStack(2, decrease1);

            ItemStack increase2 = new ItemStack(Items.EMERALD);
            increase2.set(DataComponentTypes.CUSTOM_NAME, Text.literal("+1 min"));
            increase2.set(DataComponentTypes.LORE, new LoreComponent(
                    List.of(Text.literal("Klicke, um das Intervall für Slot 2 um 1 Minute zu erhöhen"))));
            container.getInventory().setStack(10, increase2);

            ItemStack decrease2 = new ItemStack(Items.REDSTONE);
            decrease2.set(DataComponentTypes.CUSTOM_NAME, Text.literal("-1 min"));
            decrease2.set(DataComponentTypes.LORE, new LoreComponent(
                    List.of(Text.literal("Klicke, um das Intervall für Slot 2 um 1 Minute zu verringern"))));
            container.getInventory().setStack(11, decrease2);

            // Speichern Button
            ItemStack saveButton = new ItemStack(Items.EMERALD_BLOCK);
            saveButton.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Save Config"));
            saveButton.set(DataComponentTypes.LORE, new LoreComponent(
                    List.of(Text.literal("Klicke, um die Konfiguration zu speichern und das Menü zu schließen"))));
            container.getInventory().setStack(18, saveButton);
        }
    }

    public void updateConfigGUI(GenericContainerScreenHandler container) {
        ItemStack slot1Stack = new ItemStack(Items.CLOCK);
        slot1Stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Slot 1 Interval: " + autoSaveInterval1 + " min"));
        slot1Stack.set(DataComponentTypes.LORE, new LoreComponent(
                List.of(Text.literal("Zeigt das aktuelle Intervall für automatisches Speichern in Slot 1"))));
        container.getInventory().setStack(0, slot1Stack);

        ItemStack slot2Stack = new ItemStack(Items.CLOCK);
        slot2Stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Slot 2 Interval: " + autoSaveInterval2 + " min"));
        slot2Stack.set(DataComponentTypes.LORE, new LoreComponent(
                List.of(Text.literal("Zeigt das aktuelle Intervall für automatisches Speichern in Slot 2"))));
        container.getInventory().setStack(9, slot2Stack);

        // Chat Message Toggle
        ItemStack chatMsgToggle = new ItemStack(showSaveMessages ? Items.LIME_WOOL : Items.RED_WOOL);
        chatMsgToggle.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Chat Messages: " + (showSaveMessages ? "ON" : "OFF")));
        chatMsgToggle.set(DataComponentTypes.LORE,
                new LoreComponent(List.of(Text.literal("Klicke, um Chat-Benachrichtigungen beim Speichern zu "
                        + (showSaveMessages ? "deaktivieren" : "aktivieren")))));
        container.getInventory().setStack(3, chatMsgToggle);

        // Last Saves Page Icon
        ItemStack lastSavesIcon = new ItemStack(Items.BOOK);
        lastSavesIcon.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Last Saves"));
        lastSavesIcon.set(DataComponentTypes.LORE,
                new LoreComponent(List.of(Text.literal("Klicke, um die letzten 3 Speicherstände pro Slot zu sehen."))));
        container.getInventory().setStack(4, lastSavesIcon);

        // Admin Page Icon (only for OPs)
        // (Assume last opened by the same player, or add a player param if needed)
        // This is a limitation; for full correctness, pass the player to
        // updateConfigGUI
        // and check permission again.

        // Erhöhen/Verringern Buttons
        ItemStack increase1 = new ItemStack(Items.EMERALD);
        increase1.set(DataComponentTypes.CUSTOM_NAME, Text.literal("+1 min"));
        increase1.set(DataComponentTypes.LORE, new LoreComponent(
                List.of(Text.literal("Klicke, um das Intervall für Slot 1 um 1 Minute zu erhöhen"))));
        container.getInventory().setStack(1, increase1);

        ItemStack decrease1 = new ItemStack(Items.REDSTONE);
        decrease1.set(DataComponentTypes.CUSTOM_NAME, Text.literal("-1 min"));
        decrease1.set(DataComponentTypes.LORE, new LoreComponent(
                List.of(Text.literal("Klicke, um das Intervall für Slot 1 um 1 Minute zu verringern"))));
        container.getInventory().setStack(2, decrease1);

        ItemStack increase2 = new ItemStack(Items.EMERALD);
        increase2.set(DataComponentTypes.CUSTOM_NAME, Text.literal("+1 min"));
        increase2.set(DataComponentTypes.LORE, new LoreComponent(
                List.of(Text.literal("Klicke, um das Intervall für Slot 2 um 1 Minute zu erhöhen"))));
        container.getInventory().setStack(10, increase2);

        ItemStack decrease2 = new ItemStack(Items.REDSTONE);
        decrease2.set(DataComponentTypes.CUSTOM_NAME, Text.literal("-1 min"));
        decrease2.set(DataComponentTypes.LORE, new LoreComponent(
                List.of(Text.literal("Klicke, um das Intervall für Slot 2 um 1 Minute zu verringern"))));
        container.getInventory().setStack(11, decrease2);

        // Speichern Button
        ItemStack saveButton = new ItemStack(Items.EMERALD_BLOCK);
        saveButton.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Save Config"));
        saveButton.set(DataComponentTypes.LORE, new LoreComponent(
                List.of(Text.literal("Klicke, um die Konfiguration zu speichern und das Menü zu schließen"))));
        container.getInventory().setStack(18, saveButton);
    }

    public void saveConfig() {
        // Speichere die Konfiguration in einer Datei
        try {
            Path configFile = Paths.get(SAVE_DIR, "config.dat");
            NbtCompound nbt = new NbtCompound();
            nbt.putInt("autoSaveInterval1", autoSaveInterval1);
            nbt.putInt("autoSaveInterval2", autoSaveInterval2);
            nbt.putBoolean("showSaveMessages", showSaveMessages);
            NbtIo.write(nbt, configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadConfig(MinecraftServer server) {
        try {
            Path configFile = Paths.get(SAVE_DIR, "config.dat");
            if (java.nio.file.Files.exists(configFile)) {
                NbtCompound nbt = NbtIo.read(configFile);
                if (nbt != null) {
                    autoSaveInterval1 = nbt.contains("autoSaveInterval1") ? nbt.getInt("autoSaveInterval1") : 1; // Standardwert
                                                                                                                 // 1
                    autoSaveInterval2 = nbt.contains("autoSaveInterval2") ? nbt.getInt("autoSaveInterval2") : 5; // Standardwert
                                                                                                                 // 5
                    showSaveMessages = nbt.contains("showSaveMessages") ? nbt.getBoolean("showSaveMessages") : true;
                }
            }
            // New: Load last saves for all known players
            Path saveDir = Paths.get(SAVE_DIR);
            if (java.nio.file.Files.exists(saveDir)) {
                java.nio.file.Files.list(saveDir).filter(java.nio.file.Files::isDirectory).forEach(playerDir -> {
                    UUID playerId;
                    try {
                        playerId = UUID.fromString(playerDir.getFileName().toString());
                    } catch (Exception e) {
                        return;
                    }
                    loadLastSavesFromFile(playerId, (RegistryWrapper.WrapperLookup) server.getRegistryManager());
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Call this when a player joins to ensure their last saves are loaded
    public void onPlayerJoin(UUID playerId, MinecraftServer server) {
        loadLastSavesFromFile(playerId, (RegistryWrapper.WrapperLookup) server.getRegistryManager());
    }

    public int getAutoSaveInterval1() {
        return autoSaveInterval1;
    }

    public int getAutoSaveInterval2() {
        return autoSaveInterval2;
    }

    // New: Save lastSaves to file per player
    private void saveLastSavesToFile(UUID playerId, RegistryWrapper.WrapperLookup registryLookup) {
        try {
            Path saveDir = Paths.get(SAVE_DIR);
            if (!java.nio.file.Files.exists(saveDir)) {
                java.nio.file.Files.createDirectories(saveDir);
            }
            Path playerDir = saveDir.resolve(playerId.toString());
            if (!java.nio.file.Files.exists(playerDir)) {
                java.nio.file.Files.createDirectories(playerDir);
            }
            Path lastSavesFile = playerDir.resolve("last_saves.dat");
            NbtCompound nbt = new NbtCompound();
            List<List<ItemStack[]>> slotSaves = lastSaves.get(playerId);
            if (slotSaves != null) {
                for (int slot = 0; slot < SLOTS; slot++) {
                    NbtCompound slotNbt = new NbtCompound();
                    List<ItemStack[]> savesList = slotSaves.get(slot);
                    for (int i = 0; i < savesList.size(); i++) {
                        ItemStack[] inv = savesList.get(i);
                        NbtCompound invNbt = new NbtCompound();
                        DefaultedList<ItemStack> list = DefaultedList.ofSize(inv.length, ItemStack.EMPTY);
                        for (int j = 0; j < inv.length; j++)
                            list.set(j, inv[j]);
                        Inventories.writeNbt(invNbt, list, registryLookup);
                        slotNbt.put("save_" + i, invNbt);
                    }
                    nbt.put("slot_" + slot, slotNbt);
                }
            }
            NbtIo.write(nbt, lastSavesFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // New: Load lastSaves from file per player
    private void loadLastSavesFromFile(UUID playerId, RegistryWrapper.WrapperLookup registryLookup) {
        try {
            Path lastSavesFile = Paths.get(SAVE_DIR, playerId.toString(), "last_saves.dat");
            if (!java.nio.file.Files.exists(lastSavesFile))
                return;
            NbtCompound nbt = NbtIo.read(lastSavesFile);
            if (nbt == null)
                return;
            List<List<ItemStack[]>> slotSaves = new LinkedList<>();
            for (int slot = 0; slot < SLOTS; slot++) {
                slotSaves.add(new LinkedList<>());
                if (nbt.contains("slot_" + slot)) {
                    NbtCompound slotNbt = nbt.getCompound("slot_" + slot);
                    for (int i = 0; i < 3; i++) {
                        if (slotNbt.contains("save_" + i)) {
                            NbtCompound invNbt = slotNbt.getCompound("save_" + i);
                            int size = invNbt.contains("Items") ? invNbt.getList("Items", 10).size() : 0;
                            DefaultedList<ItemStack> list = DefaultedList.ofSize(size, ItemStack.EMPTY);
                            Inventories.readNbt(invNbt, list, registryLookup);
                            slotSaves.get(slot).add(list.toArray(new ItemStack[0]));
                        }
                    }
                }
            }
            lastSaves.put(playerId, slotSaves);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Returns the last 3 saves per slot for a player
    public List<List<ItemStack[]>> getLastSaves(UUID playerId) {
        List<List<ItemStack[]>> slotSaves = lastSaves.get(playerId);
        if (slotSaves == null) {
            slotSaves = new LinkedList<>();
            for (int i = 0; i < 3; i++) {
                slotSaves.add(new LinkedList<>());
            }
        }
        return slotSaves;
    }

    // Restore inventory from a specific save in lastSaves
    public void restoreInventoryFromSave(ServerPlayerEntity player, int slot, int saveIndex) {
        UUID playerId = player.getUuid();
        List<List<ItemStack[]>> slotSaves = lastSaves.get(playerId);
        if (slotSaves == null || slot < 0 || slot >= SLOTS)
            return;
        List<ItemStack[]> savesList = slotSaves.get(slot);
        if (savesList == null || saveIndex < 0 || saveIndex >= savesList.size())
            return;
        ItemStack[] saved = savesList.get(saveIndex);
        if (saved == null)
            return;
        // Restore main inventory
        int mainInvSize = player.getInventory().size() - 1; // -1 for offhand
        for (int i = 0; i < mainInvSize; i++) {
            player.getInventory().setStack(i, saved[i].copy());
        }
        // Restore armor
        for (int i = 0; i < 4; i++) {
            player.getInventory().setStack(mainInvSize + i, saved[mainInvSize + i].copy());
        }
        // Restore offhand
        player.getInventory().setStack(player.getInventory().size() - 1, saved[saved.length - 1].copy());
    }

    public boolean isPreviewEnabled(UUID playerId) {
        return previewEnabled.getOrDefault(playerId, true); // Default: enabled
    }

    public void setPreviewEnabled(UUID playerId, boolean enabled) {
        previewEnabled.put(playerId, enabled);
    }
}