package com.example.restoreinv;

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
import net.minecraft.server.network.ServerPlayerEntity;

public class ConfirmRestoreScreenHandler extends GenericContainerScreenHandler {
    private final RestoreInvStorage storage;
    private final int slot;
    private final int saveIndex;

    public ConfirmRestoreScreenHandler(int syncId, PlayerInventory playerInventory, RestoreInvStorage storage,
            int slot, int saveIndex) {
        super(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, new SimpleInventory(9 * 3), 3);
        this.storage = storage;
        this.slot = slot;
        this.saveIndex = saveIndex;
        populate();
    }

    private void populate() {
        // Center: Confirm (green wool), right: Cancel (red wool)
        ItemStack confirm = new ItemStack(Items.LIME_WOOL);
        confirm.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Bestätigen (Restore)"));
        this.getInventory().setStack(13, confirm);
        ItemStack cancel = new ItemStack(Items.RED_WOOL);
        cancel.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Abbrechen"));
        this.getInventory().setStack(15, cancel);
        // Optionally, add a message in the first row
        // ItemStack msg = new ItemStack(Items.PAPER);
        // msg.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Inventar wirklich
        // wiederherstellen?"));
        // this.getInventory().setStack(4, msg);
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        ItemStack clicked = this.getInventory().getStack(slotIndex);
        if (clicked.getItem() == Items.LIME_WOOL) {
            // Confirm: restore
            if (player instanceof ServerPlayerEntity sp) {
                storage.restoreInventoryFromSave(sp, slot, saveIndex);
                sp.closeHandledScreen();
                sp.sendMessage(Text.literal("Inventar wiederhergestellt!"), false);
            }
            return;
        } else if (clicked.getItem() == Items.RED_WOOL) {
            // Cancel: go back
            if (player instanceof ServerPlayerEntity sp) {
                sp.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                        (syncId, inv, p) -> new LastSavesScreenHandler(syncId, inv, storage, p),
                        Text.literal("Last Saves")));
            }
            return;
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }
}