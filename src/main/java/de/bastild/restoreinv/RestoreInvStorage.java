package de.bastild.restoreinv;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.ErrorReporter;

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

    // Slot-Layout: [main inventory ... | armor (4) | offhand (1)]
    // PlayerInventory.size() in 1.21.11 ist die main-size; Armor und Offhand
    // liegen seit dem 1.21.x EquipmentSlot-Refactor separat. Wir mappen sie
    // weiterhin in ein flaches Array, damit das Save-Format kompatibel bleibt.
    private static final int ARMOR_SLOTS = 4;
    private static final int OFFHAND_SLOT_OFFSET = 36; // Vanilla offhand slot index
    private static final int ARMOR_SLOT_OFFSET = 36;   // Boots start hier - via PlayerInventory.getStack ansteuerbar

    public int autoSaveInterval1 = 1; // Minuten fuer Slot 1
    public int autoSaveInterval2 = 5; // Minuten fuer Slot 2
    public boolean showSaveMessages = true;

    // Letzte 3 Saves pro Slot pro Spieler.
    public final Map<UUID, List<List<ItemStack[]>>> lastSaves = new ConcurrentHashMap<>();

    // Per-player preview-Einstellung.
    private final Map<UUID, Boolean> previewEnabled = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------
    // Helfer fuer das neue 1.21.5+ WriteView/ReadView-API.
    // ------------------------------------------------------------------
    private static NbtCompound writeInventoryToNbt(ItemStack[] inventory, RegistryWrapper.WrapperLookup lookup) {
        DefaultedList<ItemStack> list = DefaultedList.ofSize(inventory.length, ItemStack.EMPTY);
        for (int i = 0; i < inventory.length; i++) {
            list.set(i, inventory[i]);
        }
        NbtWriteView view = NbtWriteView.create(ErrorReporter.EMPTY, lookup);
        Inventories.writeData(view, list);
        return view.getNbt();
    }

    private static ItemStack[] readInventoryFromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        // Bestimme Groesse anhand der Liste im NBT (alter Save) oder des Items-Tags.
        int size = nbt.getList(Inventories.ITEMS_NBT_KEY).map(net.minecraft.nbt.NbtList::size).orElse(0);
        if (size == 0) {
            // Defensive: falls ITEMS_NBT_KEY nicht "Items" heisst, faellt das auf 0 zurueck.
            size = nbt.getList("Items").map(net.minecraft.nbt.NbtList::size).orElse(0);
        }
        DefaultedList<ItemStack> list = DefaultedList.ofSize(size, ItemStack.EMPTY);
        ReadView view = NbtReadView.create(ErrorReporter.EMPTY, lookup, nbt);
        Inventories.readData(view, list);
        return list.toArray(new ItemStack[0]);
    }

    public void saveInventory(ServerPlayerEntity player, int slot) {
        if (slot < 0 || slot >= SLOTS) {
            return;
        }

        UUID playerId = player.getUuid();
        ItemStack[][] inventories = playerInventories.computeIfAbsent(playerId, k -> new ItemStack[SLOTS][]);
        PlayerInventory inv = player.getInventory();

        // Main inventory inkl. Hotbar.
        int mainSize = PlayerInventory.MAIN_SIZE;
        ItemStack[] mainInv = new ItemStack[mainSize];
        for (int i = 0; i < mainSize; i++) {
            mainInv[i] = inv.getStack(i).copy();
        }

        // Armor: Slots 36..39 in der flachen PlayerInventory-Sicht.
        ItemStack[] armor = new ItemStack[ARMOR_SLOTS];
        for (int i = 0; i < ARMOR_SLOTS; i++) {
            armor[i] = inv.getStack(mainSize + i).copy();
        }

        // Offhand: letzter Slot.
        ItemStack offhand = inv.getStack(inv.size() - 1).copy();

        ItemStack[] combined = new ItemStack[mainInv.length + armor.length + 1];
        System.arraycopy(mainInv, 0, combined, 0, mainInv.length);
        System.arraycopy(armor, 0, combined, mainInv.length, armor.length);
        combined[combined.length - 1] = offhand;

        inventories[slot] = combined;

        // last-saves Ringpuffer (max. 3 Eintraege).
        List<List<ItemStack[]>> slotSaves = lastSaves.computeIfAbsent(playerId, k -> new ArrayList<>(SLOTS));
        while (slotSaves.size() <= slot) {
            slotSaves.add(new LinkedList<>());
        }
        List<ItemStack[]> savesList = slotSaves.get(slot);
        savesList.add(0, Arrays.stream(combined).map(ItemStack::copy).toArray(ItemStack[]::new));
        while (savesList.size() > 3) {
            savesList.remove(savesList.size() - 1);
        }

        RegistryWrapper.WrapperLookup lookup = player.getEntityWorld().getRegistryManager();
        saveToFile(playerId, slot, combined, lookup);
        saveLastSavesToFile(playerId, lookup);

        if (showSaveMessages) {
            player.sendMessage(Text.literal("Slot " + (slot + 1) + " gespeichert!"), false);
        }
    }

    public void restoreInventory(ServerPlayerEntity player, int slot) {
        if (slot < 0 || slot >= SLOTS) {
            return;
        }

        UUID playerId = player.getUuid();
        ItemStack[][] inventories = playerInventories.get(playerId);
        if (inventories == null || inventories[slot] == null) {
            ItemStack[] loaded = loadFromFile(playerId, slot, player.getEntityWorld().getRegistryManager());
            if (loaded == null) {
                return;
            }
            inventories = playerInventories.computeIfAbsent(playerId, k -> new ItemStack[SLOTS][]);
            inventories[slot] = loaded;
        }

        ItemStack[] saved = inventories[slot];
        if (saved == null) {
            return;
        }

        applyToPlayer(player, saved);
    }

    private static void applyToPlayer(ServerPlayerEntity player, ItemStack[] saved) {
        PlayerInventory inv = player.getInventory();
        int mainSize = PlayerInventory.MAIN_SIZE;
        int total = inv.size();

        // Main + Hotbar.
        for (int i = 0; i < mainSize && i < saved.length; i++) {
            inv.setStack(i, saved[i].copy());
        }
        // Armor.
        for (int i = 0; i < ARMOR_SLOTS; i++) {
            int target = mainSize + i;
            int sourceIndex = mainSize + i;
            if (target < total && sourceIndex < saved.length) {
                inv.setStack(target, saved[sourceIndex].copy());
            }
        }
        // Offhand.
        if (saved.length > 0 && total > 0) {
            inv.setStack(total - 1, saved[saved.length - 1].copy());
        }
    }

    private void saveToFile(UUID playerId, int slot, ItemStack[] inventory, RegistryWrapper.WrapperLookup lookup) {
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
            NbtCompound nbt = writeInventoryToNbt(inventory, lookup);
            NbtIo.write(nbt, saveFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private ItemStack[] loadFromFile(UUID playerId, int slot, RegistryWrapper.WrapperLookup lookup) {
        try {
            Path saveFile = Paths.get(SAVE_DIR, playerId.toString(), "slot_" + slot + ".dat");
            if (!java.nio.file.Files.exists(saveFile)) {
                return null;
            }
            NbtCompound nbt = NbtIo.read(saveFile);
            if (nbt == null) {
                return null;
            }
            return readInventoryFromNbt(nbt, lookup);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<ServerPlayerEntity> getOnlinePlayers(MinecraftServer server) {
        if (server == null) {
            return Collections.emptyList();
        }
        return server.getPlayerManager().getPlayerList();
    }

    public void openConfigScreen(ServerPlayerEntity player) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inventory, playerEntity) -> new RestoreInvConfigScreenHandler(syncId, inventory, this, playerEntity),
                Text.literal("RestoreInv Config")));

        ScreenHandler screenHandler = player.currentScreenHandler;
        if (screenHandler instanceof GenericContainerScreenHandler container) {
            populateConfigGui(container, player);
        }
    }

    private void populateConfigGui(GenericContainerScreenHandler container, ServerPlayerEntity opOwner) {
        // Slot 1 Intervall
        ItemStack slot1Stack = new ItemStack(Items.CLOCK);
        slot1Stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Slot 1 Interval: " + autoSaveInterval1 + " min"));
        slot1Stack.set(DataComponentTypes.LORE, new LoreComponent(
                List.of(Text.literal("Zeigt das aktuelle Intervall fuer automatisches Speichern in Slot 1"))));
        container.getInventory().setStack(0, slot1Stack);

        // Slot 2 Intervall
        ItemStack slot2Stack = new ItemStack(Items.CLOCK);
        slot2Stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Slot 2 Interval: " + autoSaveInterval2 + " min"));
        slot2Stack.set(DataComponentTypes.LORE, new LoreComponent(
                List.of(Text.literal("Zeigt das aktuelle Intervall fuer automatisches Speichern in Slot 2"))));
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
                List.of(Text.literal("Klicke, um die letzten 3 Speicherstaende pro Slot zu sehen."))));
        container.getInventory().setStack(4, lastSavesIcon);

        // Admin Page Icon (nur OPs)
        MinecraftServer ownerServer = opOwner != null ? opOwner.getEntityWorld().getServer() : null;
        if (ownerServer != null
                && ownerServer.getPlayerManager().isOperator(
                        new net.minecraft.server.PlayerConfigEntry(opOwner.getGameProfile()))) {
            ItemStack adminIcon = new ItemStack(Items.PLAYER_HEAD);
            adminIcon.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Admin Panel"));
            adminIcon.set(DataComponentTypes.LORE,
                    new LoreComponent(List.of(Text.literal("Klicke, um das Admin-Panel zu oeffnen."))));
            container.getInventory().setStack(5, adminIcon);
        }

        // Erhoehen / Verringern Buttons
        ItemStack increase1 = new ItemStack(Items.EMERALD);
        increase1.set(DataComponentTypes.CUSTOM_NAME, Text.literal("+1 min"));
        increase1.set(DataComponentTypes.LORE, new LoreComponent(
                List.of(Text.literal("Klicke, um das Intervall fuer Slot 1 um 1 Minute zu erhoehen"))));
        container.getInventory().setStack(1, increase1);

        ItemStack decrease1 = new ItemStack(Items.REDSTONE);
        decrease1.set(DataComponentTypes.CUSTOM_NAME, Text.literal("-1 min"));
        decrease1.set(DataComponentTypes.LORE, new LoreComponent(
                List.of(Text.literal("Klicke, um das Intervall fuer Slot 1 um 1 Minute zu verringern"))));
        container.getInventory().setStack(2, decrease1);

        ItemStack increase2 = new ItemStack(Items.EMERALD);
        increase2.set(DataComponentTypes.CUSTOM_NAME, Text.literal("+1 min"));
        increase2.set(DataComponentTypes.LORE, new LoreComponent(
                List.of(Text.literal("Klicke, um das Intervall fuer Slot 2 um 1 Minute zu erhoehen"))));
        container.getInventory().setStack(10, increase2);

        ItemStack decrease2 = new ItemStack(Items.REDSTONE);
        decrease2.set(DataComponentTypes.CUSTOM_NAME, Text.literal("-1 min"));
        decrease2.set(DataComponentTypes.LORE, new LoreComponent(
                List.of(Text.literal("Klicke, um das Intervall fuer Slot 2 um 1 Minute zu verringern"))));
        container.getInventory().setStack(11, decrease2);

        // Speichern Button
        ItemStack saveButton = new ItemStack(Items.EMERALD_BLOCK);
        saveButton.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Save Config"));
        saveButton.set(DataComponentTypes.LORE, new LoreComponent(
                List.of(Text.literal("Klicke, um die Konfiguration zu speichern und das Menue zu schliessen"))));
        container.getInventory().setStack(18, saveButton);
    }

    public void updateConfigGUI(GenericContainerScreenHandler container) {
        // Aufruf ohne Spielerkontext: Admin-Slot wird hier nicht aktualisiert,
        // damit man kein OP-Recht versehentlich anzeigt. Der initiale Zustand
        // wird in openConfigScreen mit Spieler gesetzt.
        populateConfigGui(container, null);
    }

    public void saveConfig() {
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
                    autoSaveInterval1 = nbt.getInt("autoSaveInterval1", 1);
                    autoSaveInterval2 = nbt.getInt("autoSaveInterval2", 5);
                    showSaveMessages  = nbt.getBoolean("showSaveMessages", true);
                }
            }
            // last saves fuer alle bekannten Spieler nachladen.
            Path saveDir = Paths.get(SAVE_DIR);
            if (java.nio.file.Files.exists(saveDir)) {
                java.nio.file.Files.list(saveDir).filter(java.nio.file.Files::isDirectory).forEach(playerDir -> {
                    UUID playerId;
                    try {
                        playerId = UUID.fromString(playerDir.getFileName().toString());
                    } catch (Exception e) {
                        return;
                    }
                    loadLastSavesFromFile(playerId, server.getRegistryManager());
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void onPlayerJoin(UUID playerId, MinecraftServer server) {
        loadLastSavesFromFile(playerId, server.getRegistryManager());
    }

    public int getAutoSaveInterval1() {
        return autoSaveInterval1;
    }

    public int getAutoSaveInterval2() {
        return autoSaveInterval2;
    }

    private void saveLastSavesToFile(UUID playerId, RegistryWrapper.WrapperLookup lookup) {
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
                for (int slot = 0; slot < SLOTS && slot < slotSaves.size(); slot++) {
                    NbtCompound slotNbt = new NbtCompound();
                    List<ItemStack[]> savesList = slotSaves.get(slot);
                    for (int i = 0; i < savesList.size(); i++) {
                        ItemStack[] inv = savesList.get(i);
                        slotNbt.put("save_" + i, writeInventoryToNbt(inv, lookup));
                    }
                    nbt.put("slot_" + slot, slotNbt);
                }
            }
            NbtIo.write(nbt, lastSavesFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadLastSavesFromFile(UUID playerId, RegistryWrapper.WrapperLookup lookup) {
        try {
            Path lastSavesFile = Paths.get(SAVE_DIR, playerId.toString(), "last_saves.dat");
            if (!java.nio.file.Files.exists(lastSavesFile)) {
                return;
            }
            NbtCompound nbt = NbtIo.read(lastSavesFile);
            if (nbt == null) {
                return;
            }
            List<List<ItemStack[]>> slotSaves = new ArrayList<>(SLOTS);
            for (int slot = 0; slot < SLOTS; slot++) {
                slotSaves.add(new LinkedList<>());
                NbtCompound slotNbt = nbt.getCompoundOrEmpty("slot_" + slot);
                for (int i = 0; i < 3; i++) {
                    String key = "save_" + i;
                    if (slotNbt.contains(key)) {
                        NbtCompound invNbt = slotNbt.getCompoundOrEmpty(key);
                        slotSaves.get(slot).add(readInventoryFromNbt(invNbt, lookup));
                    }
                }
            }
            lastSaves.put(playerId, slotSaves);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<List<ItemStack[]>> getLastSaves(UUID playerId) {
        List<List<ItemStack[]>> slotSaves = lastSaves.get(playerId);
        if (slotSaves == null) {
            slotSaves = new ArrayList<>(SLOTS);
            for (int i = 0; i < SLOTS; i++) {
                slotSaves.add(new LinkedList<>());
            }
        }
        return slotSaves;
    }

    public void restoreInventoryFromSave(ServerPlayerEntity player, int slot, int saveIndex) {
        UUID playerId = player.getUuid();
        List<List<ItemStack[]>> slotSaves = lastSaves.get(playerId);
        if (slotSaves == null || slot < 0 || slot >= slotSaves.size()) {
            return;
        }
        List<ItemStack[]> savesList = slotSaves.get(slot);
        if (savesList == null || saveIndex < 0 || saveIndex >= savesList.size()) {
            return;
        }
        ItemStack[] saved = savesList.get(saveIndex);
        if (saved == null) {
            return;
        }
        applyToPlayer(player, saved);
    }

    public boolean isPreviewEnabled(UUID playerId) {
        return previewEnabled.getOrDefault(playerId, true);
    }

    public void setPreviewEnabled(UUID playerId, boolean enabled) {
        previewEnabled.put(playerId, enabled);
    }
}
