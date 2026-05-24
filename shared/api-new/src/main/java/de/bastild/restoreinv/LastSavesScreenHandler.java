package de.bastild.restoreinv;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * 9x4 GUI:
 *   Row 0..2 = Save-Slots des Spielers (Reihe = Spiel-Slot 1/2/3, Spalte = Save-Index 0..N).
 *   Row 3    = Steuerung (Back, Pin-Mode-Toggle).
 *
 * Linksklick auf einen Save-Slot oeffnet Vorschau.
 * Rechtsklick togglet "pinned" auf diesem Save (Schutz vor Ueberschreiben).
 */
public class LastSavesScreenHandler extends GenericContainerScreenHandler {
    public final RestoreInvStorage storage;
    public final PlayerEntity player;

    private static final int BACK_SLOT = 27;

    // Vorschau-Cache fuer evtl. zukuenftige Hover-Logik im Screen.
    public final ItemStack[][][] previewInventories = new ItemStack[3][9][];

    public LastSavesScreenHandler(int syncId, PlayerInventory playerInventory, RestoreInvStorage storage,
                                  PlayerEntity player) {
        super(ScreenHandlerType.GENERIC_9X4, syncId, playerInventory, new SimpleInventory(9 * 4), 4);
        this.storage = storage;
        this.player = player;
        populateSaves();
    }

    private void populateSaves() {
        for (int i = 0; i < this.getInventory().size(); i++) {
            this.getInventory().setStack(i, ItemStack.EMPTY);
        }

        List<List<RestoreInvStorage.Save>> saves = storage.getLastSaves(player.getUuid());
        int limit = Math.max(1, Math.min(9, storage.savesPerSlot));

        for (int slot = 0; slot < 3; slot++) {
            List<RestoreInvStorage.Save> savesList = saves != null && slot < saves.size()
                    ? saves.get(slot) : new ArrayList<>();
            for (int i = 0; i < limit; i++) {
                int guiSlot = slot * 9 + i;
                if (i < savesList.size()) {
                    RestoreInvStorage.Save s = savesList.get(i);
                    this.getInventory().setStack(guiSlot, makeSaveIcon(slot, i, s));
                    previewInventories[slot][i] = s.stacks;
                } else {
                    this.getInventory().setStack(guiSlot, makeEmptyIcon(slot, i));
                    previewInventories[slot][i] = null;
                }
            }
        }

        ItemStack back = new ItemStack(Items.ARROW);
        back.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Zurueck"));
        back.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("Zurueck zum Config-Menue"))));
        this.getInventory().setStack(BACK_SLOT, back);

        ItemStack info = new ItemStack(Items.OAK_SIGN);
        info.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Hilfe"));
        info.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("Linksklick: Vorschau anzeigen"),
                Text.literal("Rechtsklick: Save anpinnen / loesen"))));
        this.getInventory().setStack(35, info);
    }

    private ItemStack makeSaveIcon(int slot, int saveIndex, RestoreInvStorage.Save s) {
        ItemStack icon = new ItemStack(s.pinned ? Items.ENDER_CHEST : Items.CHEST);
        ItemStack highlight = RestoreInvStorage.pickHighlight(s.stacks);
        String when = RestoreInvStorage.formatRelativeTime(s.timestampMillis);
        int items = RestoreInvStorage.countNonEmpty(s.stacks);

        icon.set(DataComponentTypes.CUSTOM_NAME, Text.literal(
                "Slot " + (slot + 1) + " - Save " + (saveIndex + 1) + (s.pinned ? " (gepinnt)" : "")));

        List<Text> lore = new ArrayList<>();
        lore.add(Text.literal(when));
        lore.add(Text.literal(items + " Items im Inventar"));
        if (highlight != null && !highlight.isEmpty()) {
            lore.add(Text.literal("Top-Tool: ").append(highlight.getName()));
        }
        if (s.pinned) {
            lore.add(Text.literal("Geschuetzt vor Ueberschreiben"));
        }
        lore.add(Text.literal(""));
        lore.add(Text.literal("Linksklick = Vorschau"));
        lore.add(Text.literal("Rechtsklick = Pin umschalten"));
        icon.set(DataComponentTypes.LORE, new LoreComponent(lore));
        return icon;
    }

    private ItemStack makeEmptyIcon(int slot, int saveIndex) {
        ItemStack icon = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        icon.set(DataComponentTypes.CUSTOM_NAME, Text.literal(
                "Slot " + (slot + 1) + " - Save " + (saveIndex + 1)));
        icon.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("(leer)"))));
        return icon;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex < 0 || slotIndex >= 9 * 4) {
            return; // Nichts ausserhalb der GUI tun lassen.
        }

        // Back-Button
        if (slotIndex == BACK_SLOT) {
            if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                storage.openConfigScreen(sp);
            }
            return;
        }

        // Save-Slots: Reihen 0..2 mit Index 0..(savesPerSlot-1)
        int slot = slotIndex / 9;
        int saveIndex = slotIndex % 9;
        int limit = Math.max(1, Math.min(9, storage.savesPerSlot));
        if (slot < 0 || slot > 2 || saveIndex < 0 || saveIndex >= limit) {
            return;
        }
        if (previewInventories[slot][saveIndex] == null) {
            return;
        }
        if (!(player instanceof net.minecraft.server.network.ServerPlayerEntity sp)) {
            return;
        }

        // Rechtsklick = Pin toggle
        if (button == 1 && actionType == SlotActionType.PICKUP) {
            storage.togglePin(sp, slot, saveIndex);
            populateSaves();
            return;
        }

        // Linksklick (oder default) = Vorschau oeffnen
        final int chosenSlot = slot;
        final int chosenSave = saveIndex;
        sp.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inv, p) -> new PreviewRestoreScreenHandler(syncId, inv, storage, p,
                        chosenSlot, chosenSave, null),
                Text.literal("Vorschau: Slot " + (chosenSlot + 1) + " Save " + (chosenSave + 1))));
    }
}
