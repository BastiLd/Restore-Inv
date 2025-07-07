package com.example.restoreinv;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import java.util.List;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.text.Text;

public class LastSavesScreenHandler extends GenericContainerScreenHandler {
    public final RestoreInvStorage storage;
    public final PlayerEntity player;

    // Centered 3x3 grid: slots 3,4,5 | 12,13,14 | 21,22,23
    private static final int[][] CENTERED_SLOTS = {
            { 3, 4, 5 }, // row 1
            { 12, 13, 14 }, // row 2
            { 21, 22, 23 } // row 3
    };
    public final ItemStack[][][] previewInventories = new ItemStack[3][3][]; // [slot][saveIndex][]

    public LastSavesScreenHandler(int syncId, PlayerInventory playerInventory, RestoreInvStorage storage,
            PlayerEntity player) {
        super(ScreenHandlerType.GENERIC_9X4, syncId, playerInventory, new SimpleInventory(9 * 4), 4);
        this.storage = storage;
        this.player = player;
        populateSaves();
    }

    private void populateSaves() {
        // Clear all slots
        for (int i = 0; i < this.getInventory().size(); i++) {
            this.getInventory().setStack(i, ItemStack.EMPTY);
        }
        List<List<ItemStack[]>> saves = storage.getLastSaves(player.getUuid());
        for (int slot = 0; slot < 3; slot++) {
            List<ItemStack[]> slotSaves = saves != null ? saves.get(slot) : null;
            for (int i = 0; i < 3; i++) {
                int guiSlot = CENTERED_SLOTS[slot][i];
                ItemStack icon = new ItemStack(Items.CHEST);
                ItemStack[] inv = null;
                if (slotSaves != null && i < slotSaves.size()) {
                    inv = slotSaves.get(i);
                    icon.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Slot " + (slot + 1) + " Save " + (i + 1)));
                } else {
                    icon.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Empty"));
                }
                this.getInventory().setStack(guiSlot, icon);
                previewInventories[slot][i] = inv;
            }
        }
        // Back arrow in last row, leftmost slot
        ItemStack back = new ItemStack(Items.ARROW);
        back.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Back"));
        this.getInventory().setStack(27, back);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        // Block all quick move from GUI slots
        if (isPreviewSlot(slotIndex)) {
            return ItemStack.EMPTY;
        }
        return super.quickMove(player, slotIndex);
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        ItemStack clickedStack = this.getInventory().getStack(slotIndex);
        // Allow clicking on CHEST in preview slots, block other actions
        if (isPreviewSlot(slotIndex) && (clickedStack == null || clickedStack.getItem() != Items.CHEST)) {
            return;
        }
        if (slotIndex == 27) {
            // Go back to config GUI
            if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp)
                storage.openConfigScreen(sp);
            return;
        }
        if (slotIndex >= 0 && slotIndex < 27) {
            if (clickedStack.getItem() == Items.CHEST) {
                int slot = slotIndex / 9;
                int saveIndex = slotIndex % 9;
                if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                    sp.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                            (syncId, inv, p) -> new ConfirmRestoreScreenHandler(syncId, inv, storage, slot,
                                    saveIndex),
                            Text.literal("Bestätigen: Slot " + (slot + 1) + " Save " + (saveIndex + 1))));
                }
                return;
            }
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    private boolean isPreviewSlot(int slotIndex) {
        for (int[] row : CENTERED_SLOTS) {
            for (int s : row) {
                if (slotIndex == s)
                    return true;
            }
        }
        return false;
    }
}