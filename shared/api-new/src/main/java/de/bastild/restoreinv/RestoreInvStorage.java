package de.bastild.restoreinv;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.storage.ReadView;
import net.minecraft.util.ErrorReporter;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.inventory.Inventories;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zentrale Save-/Restore-Logik. Persistiert pro Spieler:
 *   - aktuelle Inventar-Snapshots fuer 3 Slots (Slot 1 = autosave 1, Slot 2 = autosave 2, Slot 3 = manuell / death),
 *   - Ringpuffer der letzten N Saves pro Slot mit Zeitstempel + Pin-Flag.
 *
 * Globale Konfiguration in restoreinv/config.dat. Pro-Spieler-Preview-Setting
 * ebenfalls in config.dat.
 */
public class RestoreInvStorage {

    public static final int SLOTS = 3;
    public static final int DEFAULT_SAVES_PER_SLOT = 3;
    public static final int MAX_SAVES_PER_SLOT = 9; // GUI-Beschraenkung: 9-breiter Chest
    private static final String SAVE_DIR = "restoreinv";
    private static final int ARMOR_SLOTS = 4;

    // ======== Snapshot-Datenstruktur ============================================
    public static final class Save {
        public final ItemStack[] stacks;
        public final long timestampMillis;
        public boolean pinned;

        public Save(ItemStack[] stacks, long timestampMillis, boolean pinned) {
            this.stacks = stacks;
            this.timestampMillis = timestampMillis;
            this.pinned = pinned;
        }

        public Save copyImmutable() {
            ItemStack[] copy = new ItemStack[stacks.length];
            for (int i = 0; i < stacks.length; i++) {
                copy[i] = stacks[i] == null ? ItemStack.EMPTY : stacks[i].copy();
            }
            return new Save(copy, timestampMillis, pinned);
        }
    }

    // ======== State =============================================================
    private final Map<UUID, ItemStack[][]> playerInventories = new ConcurrentHashMap<>();
    public final Map<UUID, List<List<Save>>> lastSaves = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> previewEnabled = new ConcurrentHashMap<>();

    // ======== Globale Settings ==================================================
    public int autoSaveInterval1 = 1;
    public int autoSaveInterval2 = 5;
    public boolean showSaveMessages = true;
    public boolean playRestoreSound = true;
    public boolean requireOpForRestore = false;
    public boolean autoSaveOnDeath = true;
    public int savesPerSlot = DEFAULT_SAVES_PER_SLOT;

    // ============================================================================
    // NBT-Helfer (1.21.5+ WriteView/ReadView)
    // ============================================================================
    private static NbtCompound writeStacksToNbt(ItemStack[] inv, RegistryWrapper.WrapperLookup lookup) {
        DefaultedList<ItemStack> list = DefaultedList.ofSize(inv.length, ItemStack.EMPTY);
        for (int i = 0; i < inv.length; i++) {
            list.set(i, inv[i]);
        }
        NbtWriteView view = NbtWriteView.create(ErrorReporter.EMPTY, lookup);
        Inventories.writeData(view, list);
        return view.getNbt();
    }

    private static ItemStack[] readStacksFromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        int size = nbt.getList(Inventories.ITEMS_NBT_KEY).map(NbtList::size).orElse(0);
        if (size == 0) {
            size = nbt.getList("Items").map(NbtList::size).orElse(0);
        }
        DefaultedList<ItemStack> list = DefaultedList.ofSize(size, ItemStack.EMPTY);
        ReadView view = NbtReadView.create(ErrorReporter.EMPTY, lookup, nbt);
        Inventories.readData(view, list);
        return list.toArray(new ItemStack[0]);
    }

    // ============================================================================
    // Inventar-Snapshot machen / wiederherstellen
    // ============================================================================
    public void saveInventory(ServerPlayerEntity player, int slot) {
        if (slot < 0 || slot >= SLOTS) return;

        UUID playerId = player.getUuid();
        ItemStack[][] inventories = playerInventories.computeIfAbsent(playerId, k -> new ItemStack[SLOTS][]);
        PlayerInventory inv = player.getInventory();

        int mainSize = PlayerInventory.MAIN_SIZE;
        ItemStack[] mainInv = new ItemStack[mainSize];
        for (int i = 0; i < mainSize; i++) {
            mainInv[i] = inv.getStack(i).copy();
        }
        ItemStack[] armor = new ItemStack[ARMOR_SLOTS];
        for (int i = 0; i < ARMOR_SLOTS; i++) {
            armor[i] = inv.getStack(mainSize + i).copy();
        }
        ItemStack offhand = inv.getStack(inv.size() - 1).copy();

        ItemStack[] combined = new ItemStack[mainInv.length + armor.length + 1];
        System.arraycopy(mainInv, 0, combined, 0, mainInv.length);
        System.arraycopy(armor,   0, combined, mainInv.length, armor.length);
        combined[combined.length - 1] = offhand;

        inventories[slot] = combined;

        // Ringpuffer: neuesten Save vorne anhaengen, gepinnte Saves NIE rauskippen.
        List<List<Save>> slotSaves = lastSaves.computeIfAbsent(playerId, k -> new ArrayList<>(SLOTS));
        while (slotSaves.size() <= slot) {
            slotSaves.add(new ArrayList<>());
        }
        List<Save> savesList = slotSaves.get(slot);
        ItemStack[] copy = new ItemStack[combined.length];
        for (int i = 0; i < combined.length; i++) copy[i] = combined[i].copy();
        savesList.add(0, new Save(copy, System.currentTimeMillis(), false));
        trimRingBuffer(savesList);

        RegistryWrapper.WrapperLookup lookup = player.getEntityWorld().getRegistryManager();
        saveToFile(playerId, slot, combined, lookup);
        saveLastSavesToFile(playerId, lookup);

        if (showSaveMessages) {
            player.sendMessage(Text.literal("Slot " + (slot + 1) + " gespeichert!"), false);
        }
    }

    private void trimRingBuffer(List<Save> savesList) {
        // Entfernt von hinten (= aelteste) so lange wir ueber dem Limit sind und
        // ueberspringt gepinnte Eintraege. Wenn ALLE gepinnt sind und voll: keine Aenderung.
        int limit = Math.max(1, Math.min(MAX_SAVES_PER_SLOT, savesPerSlot));
        for (int i = savesList.size() - 1; i >= 0 && savesList.size() > limit; i--) {
            Save candidate = savesList.get(i);
            if (!candidate.pinned) {
                savesList.remove(i);
            }
        }
    }

    public void restoreInventory(ServerPlayerEntity player, int slot) {
        if (slot < 0 || slot >= SLOTS) return;
        UUID playerId = player.getUuid();
        ItemStack[][] inventories = playerInventories.get(playerId);
        if (inventories == null || inventories[slot] == null) {
            ItemStack[] loaded = loadFromFile(playerId, slot, player.getEntityWorld().getRegistryManager());
            if (loaded == null) return;
            inventories = playerInventories.computeIfAbsent(playerId, k -> new ItemStack[SLOTS][]);
            inventories[slot] = loaded;
        }
        ItemStack[] saved = inventories[slot];
        if (saved == null) return;
        applyToPlayer(player, saved);
        playRestoreSoundIfEnabled(player);
    }

    public void restoreInventoryFromSave(ServerPlayerEntity player, int slot, int saveIndex) {
        UUID playerId = player.getUuid();
        List<List<Save>> slotSaves = lastSaves.get(playerId);
        if (slotSaves == null || slot < 0 || slot >= slotSaves.size()) return;
        List<Save> savesList = slotSaves.get(slot);
        if (savesList == null || saveIndex < 0 || saveIndex >= savesList.size()) return;
        Save s = savesList.get(saveIndex);
        if (s == null || s.stacks == null) return;
        applyToPlayer(player, s.stacks);
        playRestoreSoundIfEnabled(player);
    }

    private void playRestoreSoundIfEnabled(ServerPlayerEntity player) {
        if (!playRestoreSound) return;
        try {
            player.playSound(net.minecraft.sound.SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        } catch (Throwable t) {
            // Defensive - falls Sound-API spaeter umzieht, keine Crashes.
        }
    }

    private static void applyToPlayer(ServerPlayerEntity player, ItemStack[] saved) {
        PlayerInventory inv = player.getInventory();
        int mainSize = PlayerInventory.MAIN_SIZE;
        int total = inv.size();
        for (int i = 0; i < mainSize && i < saved.length; i++) {
            inv.setStack(i, saved[i].copy());
        }
        for (int i = 0; i < ARMOR_SLOTS; i++) {
            int target = mainSize + i;
            int sourceIndex = mainSize + i;
            if (target < total && sourceIndex < saved.length) {
                inv.setStack(target, saved[sourceIndex].copy());
            }
        }
        if (saved.length > 0 && total > 0) {
            inv.setStack(total - 1, saved[saved.length - 1].copy());
        }
    }

    // ============================================================================
    // Pin / Unpin
    // ============================================================================
    public void togglePin(ServerPlayerEntity player, int slot, int saveIndex) {
        UUID playerId = player.getUuid();
        List<List<Save>> slotSaves = lastSaves.get(playerId);
        if (slotSaves == null || slot < 0 || slot >= slotSaves.size()) return;
        List<Save> savesList = slotSaves.get(slot);
        if (savesList == null || saveIndex < 0 || saveIndex >= savesList.size()) return;
        Save s = savesList.get(saveIndex);
        s.pinned = !s.pinned;
        saveLastSavesToFile(playerId, player.getEntityWorld().getRegistryManager());
        if (showSaveMessages) {
            player.sendMessage(Text.literal(
                    "Save Slot " + (slot + 1) + " #" + (saveIndex + 1) + ": " + (s.pinned ? "gepinnt" : "entpinnt")),
                    false);
        }
    }

    public boolean isPinned(UUID playerId, int slot, int saveIndex) {
        List<List<Save>> slotSaves = lastSaves.get(playerId);
        if (slotSaves == null || slot < 0 || slot >= slotSaves.size()) return false;
        List<Save> savesList = slotSaves.get(slot);
        if (savesList == null || saveIndex < 0 || saveIndex >= savesList.size()) return false;
        return savesList.get(saveIndex).pinned;
    }

    // ============================================================================
    // Persistierung pro Spieler (slot_n.dat + last_saves.dat)
    // ============================================================================
    private void saveToFile(UUID playerId, int slot, ItemStack[] inventory, RegistryWrapper.WrapperLookup lookup) {
        try {
            Path saveDir = Paths.get(SAVE_DIR);
            if (!java.nio.file.Files.exists(saveDir)) java.nio.file.Files.createDirectories(saveDir);
            Path playerDir = saveDir.resolve(playerId.toString());
            if (!java.nio.file.Files.exists(playerDir)) java.nio.file.Files.createDirectories(playerDir);
            Path saveFile = playerDir.resolve("slot_" + slot + ".dat");
            NbtIo.write(writeStacksToNbt(inventory, lookup), saveFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private ItemStack[] loadFromFile(UUID playerId, int slot, RegistryWrapper.WrapperLookup lookup) {
        try {
            Path saveFile = Paths.get(SAVE_DIR, playerId.toString(), "slot_" + slot + ".dat");
            if (!java.nio.file.Files.exists(saveFile)) return null;
            NbtCompound nbt = NbtIo.read(saveFile);
            if (nbt == null) return null;
            return readStacksFromNbt(nbt, lookup);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void saveLastSavesToFile(UUID playerId, RegistryWrapper.WrapperLookup lookup) {
        try {
            Path saveDir = Paths.get(SAVE_DIR);
            if (!java.nio.file.Files.exists(saveDir)) java.nio.file.Files.createDirectories(saveDir);
            Path playerDir = saveDir.resolve(playerId.toString());
            if (!java.nio.file.Files.exists(playerDir)) java.nio.file.Files.createDirectories(playerDir);
            Path lastSavesFile = playerDir.resolve("last_saves.dat");

            NbtCompound nbt = new NbtCompound();
            List<List<Save>> slotSaves = lastSaves.get(playerId);
            if (slotSaves != null) {
                for (int slot = 0; slot < SLOTS && slot < slotSaves.size(); slot++) {
                    NbtCompound slotNbt = new NbtCompound();
                    List<Save> savesList = slotSaves.get(slot);
                    for (int i = 0; i < savesList.size(); i++) {
                        Save s = savesList.get(i);
                        NbtCompound saveNbt = writeStacksToNbt(s.stacks, lookup);
                        saveNbt.putLong("__ts", s.timestampMillis);
                        saveNbt.putBoolean("__pinned", s.pinned);
                        slotNbt.put("save_" + i, saveNbt);
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
            if (!java.nio.file.Files.exists(lastSavesFile)) return;
            NbtCompound nbt = NbtIo.read(lastSavesFile);
            if (nbt == null) return;

            List<List<Save>> slotSaves = new ArrayList<>(SLOTS);
            for (int slot = 0; slot < SLOTS; slot++) {
                slotSaves.add(new ArrayList<>());
                NbtCompound slotNbt = nbt.getCompoundOrEmpty("slot_" + slot);
                for (int i = 0; i < MAX_SAVES_PER_SLOT; i++) {
                    String key = "save_" + i;
                    if (!slotNbt.contains(key)) continue;
                    NbtCompound saveNbt = slotNbt.getCompoundOrEmpty(key);
                    ItemStack[] stacks = readStacksFromNbt(saveNbt, lookup);
                    long ts      = saveNbt.getLong("__ts", 0L);
                    boolean pin  = saveNbt.getBoolean("__pinned", false);
                    slotSaves.get(slot).add(new Save(stacks, ts, pin));
                }
            }
            lastSaves.put(playerId, slotSaves);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ============================================================================
    // Globale Konfiguration
    // ============================================================================
    public void saveConfig() {
        try {
            Path configFile = Paths.get(SAVE_DIR, "config.dat");
            if (!java.nio.file.Files.exists(configFile.getParent())) {
                java.nio.file.Files.createDirectories(configFile.getParent());
            }
            NbtCompound nbt = new NbtCompound();
            nbt.putInt("autoSaveInterval1", autoSaveInterval1);
            nbt.putInt("autoSaveInterval2", autoSaveInterval2);
            nbt.putBoolean("showSaveMessages", showSaveMessages);
            nbt.putBoolean("playRestoreSound", playRestoreSound);
            nbt.putBoolean("requireOpForRestore", requireOpForRestore);
            nbt.putBoolean("autoSaveOnDeath", autoSaveOnDeath);
            nbt.putInt("savesPerSlot", savesPerSlot);

            NbtCompound prev = new NbtCompound();
            for (Map.Entry<UUID, Boolean> e : previewEnabled.entrySet()) {
                prev.putBoolean(e.getKey().toString(), e.getValue());
            }
            nbt.put("previewEnabled", prev);

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
                    autoSaveInterval1   = nbt.getInt("autoSaveInterval1", 1);
                    autoSaveInterval2   = nbt.getInt("autoSaveInterval2", 5);
                    showSaveMessages    = nbt.getBoolean("showSaveMessages", true);
                    playRestoreSound    = nbt.getBoolean("playRestoreSound", true);
                    requireOpForRestore = nbt.getBoolean("requireOpForRestore", false);
                    autoSaveOnDeath     = nbt.getBoolean("autoSaveOnDeath", true);
                    savesPerSlot        = clamp(nbt.getInt("savesPerSlot", DEFAULT_SAVES_PER_SLOT),
                                                1, MAX_SAVES_PER_SLOT);

                    NbtCompound prev = nbt.getCompoundOrEmpty("previewEnabled");
                    previewEnabled.clear();
                    for (String key : prev.getKeys()) {
                        try {
                            previewEnabled.put(UUID.fromString(key), prev.getBoolean(key, true));
                        } catch (IllegalArgumentException ignore) {}
                    }
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

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public void onPlayerJoin(UUID playerId, MinecraftServer server) {
        loadLastSavesFromFile(playerId, server.getRegistryManager());
    }

    public int getAutoSaveInterval1() { return autoSaveInterval1; }
    public int getAutoSaveInterval2() { return autoSaveInterval2; }

    public List<ServerPlayerEntity> getOnlinePlayers(MinecraftServer server) {
        return server == null ? Collections.emptyList() : server.getPlayerManager().getPlayerList();
    }

    public List<List<Save>> getLastSaves(UUID playerId) {
        List<List<Save>> slotSaves = lastSaves.get(playerId);
        if (slotSaves == null) {
            slotSaves = new ArrayList<>(SLOTS);
            for (int i = 0; i < SLOTS; i++) slotSaves.add(new ArrayList<>());
        }
        return slotSaves;
    }

    public boolean isPreviewEnabled(UUID playerId) {
        return previewEnabled.getOrDefault(playerId, true);
    }

    public void setPreviewEnabled(UUID playerId, boolean enabled) {
        previewEnabled.put(playerId, enabled);
        saveConfig();
    }

    // ============================================================================
    // Helfer fuer GUI-Tooltips
    // ============================================================================
    public static String formatRelativeTime(long timestampMillis) {
        if (timestampMillis <= 0) return "?";
        Duration d = Duration.between(Instant.ofEpochMilli(timestampMillis), Instant.now());
        long sec = Math.max(0, d.getSeconds());
        if (sec < 60) return "Gerade eben";
        long min = sec / 60;
        if (min < 60) return "Vor " + min + " Min";
        long hr = min / 60;
        if (hr < 24) return "Vor " + hr + " Std";
        long day = hr / 24;
        return "Vor " + day + " Tagen";
    }

    public static int countNonEmpty(ItemStack[] inv) {
        if (inv == null) return 0;
        int n = 0;
        for (ItemStack s : inv) if (s != null && !s.isEmpty()) n++;
        return n;
    }

    /** Gibt z. B. das Schwert mit hoechstem Schaden o. aehnliches als 'Hauptwaffe' zurueck. */
    public static ItemStack pickHighlight(ItemStack[] inv) {
        if (inv == null) return ItemStack.EMPTY;
        ItemStack best = ItemStack.EMPTY;
        for (ItemStack s : inv) {
            if (s == null || s.isEmpty()) continue;
            if (best.isEmpty()) best = s;
            // Heuristik: Item mit hoechstem maxDamage (Tools/Schwerter) bevorzugen.
            else if (s.getMaxDamage() > best.getMaxDamage()) best = s;
        }
        return best;
    }

    // ============================================================================
    // Permission-Helfer
    // ============================================================================
    public boolean canRestore(ServerPlayerEntity player) {
        if (!requireOpForRestore) return true;
        MinecraftServer s = player.getEntityWorld().getServer();
        if (s == null) return false;
        return s.getPlayerManager().isOperator(new PlayerConfigEntry(player.getGameProfile()));
    }

    // ============================================================================
    // Config-GUI
    // ============================================================================
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
        ItemStack slot1Stack = labeled(Items.CLOCK, "Slot 1 Interval: " + autoSaveInterval1 + " min",
                "Aktuelles Intervall fuer Slot 1");
        container.getInventory().setStack(0, slot1Stack);

        ItemStack slot2Stack = labeled(Items.CLOCK, "Slot 2 Interval: " + autoSaveInterval2 + " min",
                "Aktuelles Intervall fuer Slot 2");
        container.getInventory().setStack(9, slot2Stack);

        container.getInventory().setStack(3, labeled(showSaveMessages ? Items.LIME_WOOL : Items.RED_WOOL,
                "Chat Messages: " + (showSaveMessages ? "ON" : "OFF"),
                "Klicke zum " + (showSaveMessages ? "Deaktivieren" : "Aktivieren") + " der Speicher-Nachrichten"));

        container.getInventory().setStack(4, labeled(Items.BOOK, "Last Saves",
                "Klicke fuer die Liste der letzten Saves pro Slot"));

        container.getInventory().setStack(7, labeled(playRestoreSound ? Items.NOTE_BLOCK : Items.STRUCTURE_VOID,
                "Restore-Sound: " + (playRestoreSound ? "AN" : "AUS"),
                "Klicke zum " + (playRestoreSound ? "Deaktivieren" : "Aktivieren") + " des Sounds beim Wiederherstellen"));

        container.getInventory().setStack(8, labeled(autoSaveOnDeath ? Items.TOTEM_OF_UNDYING : Items.SKELETON_SKULL,
                "Auto-Save bei Tod: " + (autoSaveOnDeath ? "AN" : "AUS"),
                "Wenn AN: bei jedem Tod wird automatisch in Slot 3 gespeichert"));

        container.getInventory().setStack(13, labeled(Items.CHEST,
                "Saves pro Slot: " + savesPerSlot,
                "Linksklick = +1, Rechtsklick = -1 (1.." + MAX_SAVES_PER_SLOT + ")"));

        container.getInventory().setStack(14, labeled(requireOpForRestore ? Items.IRON_BARS : Items.BARRIER,
                "Restore nur OPs: " + (requireOpForRestore ? "AN" : "AUS"),
                "Wenn AN: nur OPs koennen Restore-Befehle ausfuehren"));

        // Admin-Panel-Icon nur fuer OPs
        MinecraftServer ownerServer = opOwner != null ? opOwner.getEntityWorld().getServer() : null;
        if (ownerServer != null
                && ownerServer.getPlayerManager().isOperator(new PlayerConfigEntry(opOwner.getGameProfile()))) {
            container.getInventory().setStack(5, labeled(Items.PLAYER_HEAD, "Admin Panel",
                    "Klicke, um das Admin-Panel zu oeffnen"));
        }

        // +/- Buttons fuer Slot 1/2
        container.getInventory().setStack(1, labeled(Items.EMERALD, "+1 min", "Slot 1 Intervall +1"));
        container.getInventory().setStack(2, labeled(Items.REDSTONE, "-1 min", "Slot 1 Intervall -1"));
        container.getInventory().setStack(10, labeled(Items.EMERALD, "+1 min", "Slot 2 Intervall +1"));
        container.getInventory().setStack(11, labeled(Items.REDSTONE, "-1 min", "Slot 2 Intervall -1"));

        // Speichern Button
        container.getInventory().setStack(18, labeled(Items.EMERALD_BLOCK, "Save Config",
                "Klicke, um Konfiguration zu speichern und Menue zu schliessen"));
    }

    private static ItemStack labeled(net.minecraft.item.Item item, String name, String lore) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
        s.set(DataComponentTypes.LORE, new LoreComponent(List.of(Text.literal(lore))));
        return s;
    }

    public void updateConfigGUI(GenericContainerScreenHandler container) {
        populateConfigGui(container, null);
    }
}
