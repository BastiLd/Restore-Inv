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
import java.util.UUID;

public class ConfirmAdminRestoreScreenHandler extends GenericContainerScreenHandler {
    private final RestoreInvStorage storage;
    private final UUID targetUuid;
    private final int slot;

    public ConfirmAdminRestoreScreenHandler(int syncId, PlayerInventory playerInventory, RestoreInvStorage storage,
            UUID targetUuid, int slot) {
        super(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, new SimpleInventory(9 * 3), 3);
        this.storage = storage;
        this.targetUuid = targetUuid;
        this.slot = slot;
        populate();
    }

    private void populate() {
        ItemStack confirm = new ItemStack(Items.LIME_WOOL);
        confirm.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Bestätigen (Restore)"));
        this.getInventory().setStack(13, confirm);
        ItemStack cancel = new ItemStack(Items.RED_WOOL);
        cancel.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Abbrechen"));
        this.getInventory().setStack(15, cancel);
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        ItemStack clicked = this.getInventory().getStack(slotIndex);
        if (clicked.getItem() == Items.LIME_WOOL) {
            // Confirm: restore to target player
            if (player.getServer() != null) {
                ServerPlayerEntity target = player.getServer().getPlayerManager().getPlayer(targetUuid);
                if (target != null) {
                    storage.restoreInventoryFromSave(target, slot, 0);
                    player.sendMessage(Text.literal("Inventar wiederhergestellt!"), false);
                } else {
                    player.sendMessage(Text.literal("Spieler nicht online!"), false);
                }
            }
            if (player instanceof ServerPlayerEntity sp) {
                sp.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                        (syncId, inv, p) -> new PlayerSavesScreenHandler(syncId, inv, storage, p, targetUuid),
                        Text.literal("Player Saves")));
            }
            return;
        } else if (clicked.getItem() == Items.RED_WOOL) {
            // Cancel: go back
            if (player instanceof ServerPlayerEntity sp) {
                sp.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                        (syncId, inv, p) -> new PlayerSavesScreenHandler(syncId, inv, storage, p, targetUuid),
                        Text.literal("Player Saves")));
            }
            return;
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }
}