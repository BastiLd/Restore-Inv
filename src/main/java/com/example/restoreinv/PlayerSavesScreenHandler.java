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
import java.util.List;
import java.util.UUID;

public class PlayerSavesScreenHandler extends GenericContainerScreenHandler {
    public final RestoreInvStorage storage;
    public final PlayerEntity player;
    public final UUID targetUuid;

    public PlayerSavesScreenHandler(int syncId, PlayerInventory playerInventory, RestoreInvStorage storage,
            PlayerEntity player, UUID targetUuid) {
        super(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, new SimpleInventory(9 * 3), 3);
        this.storage = storage;
        this.player = player;
        this.targetUuid = targetUuid;
        populate();
    }

    private void populate() {
        // Show the last save for each slot (1,2,3)
        List<List<ItemStack[]>> saves = storage.getLastSaves(targetUuid);
        for (int slot = 0; slot < 3; slot++) {
            ItemStack icon = new ItemStack(Items.CHEST);
            if (saves != null && saves.get(slot) != null && !saves.get(slot).isEmpty()) {
                icon.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Slot " + (slot + 1) + " Last Save"));
            } else {
                icon.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Empty"));
            }
            this.getInventory().setStack(slot, icon);
        }
        // Back arrow
        ItemStack back = new ItemStack(Items.ARROW);
        back.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Back"));
        this.getInventory().setStack(8, back);
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        ItemStack clicked = this.getInventory().getStack(slotIndex);
        if (clicked.getItem() == Items.ARROW) {
            if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                sp.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                        (syncId, inv, p) -> new AdminPanelScreenHandler(syncId, inv, storage, p),
                        Text.literal("Admin Panel")));
            }
            return;
        }
        if (clicked.getItem() == Items.CHEST && slotIndex >= 0 && slotIndex < 3) {
            if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                sp.openHandledScreen(
                        new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                                (syncId, inv, p) -> new ConfirmAdminRestoreScreenHandler(syncId, inv, storage,
                                        targetUuid, slotIndex),
                                Text.literal("Bestätigen: Slot " + (slotIndex + 1) + " für Spieler")));
            }
            return;
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }
}