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
import java.util.UUID;
import java.util.Map;

public class AdminPanelScreenHandler extends GenericContainerScreenHandler {
    public final RestoreInvStorage storage;
    public final PlayerEntity player;

    public AdminPanelScreenHandler(int syncId, PlayerInventory playerInventory, RestoreInvStorage storage,
            PlayerEntity player) {
        super(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, new SimpleInventory(9 * 3), 3);
        this.storage = storage;
        this.player = player;
        populate();
    }

    private void populate() {
        // Back arrow
        ItemStack back = new ItemStack(Items.ARROW);
        back.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Back"));
        this.getInventory().setStack(0, back);
        // Player heads
        int idx = 1;
        for (Map.Entry<UUID, ?> entry : storage.lastSaves.entrySet()) {
            if (idx >= this.getInventory().size())
                break;
            UUID uuid = entry.getKey();
            // Create player head with custom name
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponentTypes.CUSTOM_NAME, Text.literal(uuid.toString()));
            this.getInventory().setStack(idx, head);
            idx++;
        }
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        ItemStack clicked = this.getInventory().getStack(slotIndex);
        if (clicked.getItem() == Items.ARROW) {
            if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                storage.openConfigScreen(sp);
            }
            return;
        }
        if (clicked.getItem() == Items.PLAYER_HEAD) {
            if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                // Find the UUID for this head
                for (Map.Entry<UUID, ?> entry : storage.lastSaves.entrySet()) {
                    int idx = 1;
                    if (this.getInventory().getStack(idx).equals(clicked)) {
                        UUID uuid = entry.getKey();
                        sp.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                                (syncId, inv, p) -> new PlayerSavesScreenHandler(syncId, inv, storage, p, uuid),
                                Text.literal("Player Saves")));
                        break;
                    }
                    idx++;
                }
            }
            return;
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }
}