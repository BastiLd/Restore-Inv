package de.bastild.restoreinv;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.UUID;

/**
 * Zeigt vor dem Wiederherstellen das komplette Inventar (Hauptinventar +
 * Hotbar + Ruestung + Offhand) zur Vorschau. Die Slots sind read-only, am
 * unteren Rand stehen zwei Buttons:
 *   - Lime Wool  -> Wiederherstellen
 *   - Red  Wool  -> Zurueck zur Liste
 *
 * Optional: targetUuid != null bedeutet Admin-Modus (Wiederherstellen fuer
 * einen anderen Spieler). Ist targetUuid == null, wird der oeffnende Spieler
 * selbst wiederhergestellt.
 */
public class PreviewRestoreScreenHandler extends GenericContainerScreenHandler {
    private final RestoreInvStorage storage;
    private final int slot;
    private final int saveIndex;
    private final UUID targetUuid; // null = aktiver Spieler

    // 6 Reihen x 9 = 54 Slots. Layout:
    //  Row 0  (Slots 0..8 ) : 4 Armor-Slots + Offhand (Slot 8 = Offhand)
    //  Row 1  (Slots 9..17): Hauptinventar Reihe 1
    //  Row 2  (Slots 18..26): Hauptinventar Reihe 2
    //  Row 3  (Slots 27..35): Hauptinventar Reihe 3
    //  Row 4  (Slots 36..44): Hotbar
    //  Row 5  (Slots 45..53): Aktions-Buttons (links 49 = restore, rechts 53 = cancel)
    private static final int CONFIRM_SLOT = 49;
    private static final int CANCEL_SLOT  = 53;
    private static final int HOTBAR_FIRST = 36;
    private static final int MAIN_FIRST   = 9;
    private static final int OFFHAND_SLOT = 8;

    public PreviewRestoreScreenHandler(int syncId, PlayerInventory playerInventory, RestoreInvStorage storage,
            PlayerEntity opener, int slot, int saveIndex, UUID targetUuid) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, new SimpleInventory(9 * 6), 6);
        this.storage = storage;
        this.slot = slot;
        this.saveIndex = saveIndex;
        this.targetUuid = targetUuid;
        populate(opener);
    }

    private void populate(PlayerEntity opener) {
        UUID id = targetUuid != null ? targetUuid : opener.getUuid();
        List<List<ItemStack[]>> slotSaves = storage.getLastSaves(id);
        ItemStack[] inv = (slotSaves != null && slot >= 0 && slot < slotSaves.size()
                && saveIndex >= 0 && saveIndex < slotSaves.get(slot).size())
                ? slotSaves.get(slot).get(saveIndex) : null;

        SimpleInventory grid = (SimpleInventory) this.getInventory();

        if (inv != null) {
            // Erwartetes Layout aus RestoreInvStorage:
            //   [main (mainSize=36) | armor (4) | offhand (1)]
            int armorBase = 36;          // Slots 36..39 = Armor
            int offhandIdx = inv.length - 1;

            // Armor in Row 0 (Slots 0..3).
            for (int a = 0; a < 4 && (armorBase + a) < inv.length; a++) {
                ItemStack stack = inv[armorBase + a];
                if (stack != null && !stack.isEmpty()) {
                    grid.setStack(a, stack.copy());
                }
            }
            // Offhand in Row 0, Slot 8.
            if (offhandIdx >= 0 && offhandIdx < inv.length) {
                ItemStack off = inv[offhandIdx];
                if (off != null && !off.isEmpty()) {
                    grid.setStack(OFFHAND_SLOT, off.copy());
                }
            }
            // Hauptinventar (Slot 9..35 = main rows 1..3 ohne Hotbar).
            // Vanilla-Layout: 0..8 = Hotbar, 9..35 = Main 3x9. Wir invertieren das hier:
            // - Vanilla-Hotbar (0..8 in inv) zeigen wir in Row 4 (HOTBAR_FIRST..)
            // - Vanilla-Main (9..35 in inv) zeigen wir in Row 1..3 (MAIN_FIRST..MAIN_FIRST+27)
            for (int i = 9; i < 36 && i < inv.length; i++) {
                ItemStack stack = inv[i];
                if (stack != null && !stack.isEmpty()) {
                    grid.setStack(MAIN_FIRST + (i - 9), stack.copy());
                }
            }
            for (int i = 0; i < 9 && i < inv.length; i++) {
                ItemStack stack = inv[i];
                if (stack != null && !stack.isEmpty()) {
                    grid.setStack(HOTBAR_FIRST + i, stack.copy());
                }
            }
        }

        // Aktions-Buttons in Row 5.
        ItemStack confirm = new ItemStack(Items.LIME_WOOL);
        confirm.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Inventar wiederherstellen"));
        confirm.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("Klicke, um dieses Inventar zu uebernehmen."))));
        grid.setStack(CONFIRM_SLOT, confirm);

        ItemStack cancel = new ItemStack(Items.RED_WOOL);
        cancel.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Zurueck"));
        cancel.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("Klicke, um zur Liste zurueckzukehren."))));
        grid.setStack(CANCEL_SLOT, cancel);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        // Nichts soll aus der Vorschau wandern.
        return ItemStack.EMPTY;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        // Klicks im Vorschau-Bereich (alle Slots der oberen 5 Reihen) ignorieren.
        if (slotIndex >= 0 && slotIndex < this.getInventory().size()) {
            if (slotIndex == CONFIRM_SLOT) {
                if (player instanceof ServerPlayerEntity sp) {
                    if (targetUuid == null) {
                        storage.restoreInventoryFromSave(sp, slot, saveIndex);
                        sp.sendMessage(Text.literal("Inventar wiederhergestellt!"), false);
                    } else {
                        net.minecraft.server.MinecraftServer server = sp.getEntityWorld().getServer();
                        if (server != null) {
                            ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetUuid);
                            if (target != null) {
                                storage.restoreInventoryFromSave(target, slot, saveIndex);
                                sp.sendMessage(Text.literal("Inventar des Spielers wiederhergestellt!"), false);
                            } else {
                                sp.sendMessage(Text.literal("Spieler nicht online!"), false);
                            }
                        }
                    }
                    sp.closeHandledScreen();
                }
                return;
            }
            if (slotIndex == CANCEL_SLOT) {
                if (player instanceof ServerPlayerEntity sp) {
                    if (targetUuid == null) {
                        sp.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                                (syncId, inv, p) -> new LastSavesScreenHandler(syncId, inv, storage, p),
                                Text.literal("Last Saves")));
                    } else {
                        UUID t = targetUuid;
                        sp.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                                (syncId, inv, p) -> new PlayerSavesScreenHandler(syncId, inv, storage, p, t),
                                Text.literal("Player Saves")));
                    }
                }
                return;
            }
            // Anderer Slot im 9x6-Gitter (alles bis 53): nicht erlauben.
            return;
        }
        // Spieler-Inventar (Slot >= getInventory().size()): wir blockieren auch das,
        // damit niemand seinen aktuellen Inhalt waehrend Vorschau verschiebt.
    }
}
