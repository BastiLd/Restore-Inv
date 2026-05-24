package de.bastild.restoreinv;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class RestoreInvConfigScreenHandler extends GenericContainerScreenHandler {
    private final RestoreInvStorage storage;
    private final PlayerEntity player;

    public RestoreInvConfigScreenHandler(int syncId, PlayerInventory playerInventory, RestoreInvStorage storage,
            PlayerEntity player) {
        super(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, new SimpleInventory(9 * 3), 3);
        this.storage = storage;
        this.player = player;
        storage.updateConfigGUI(this);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        // Verhindere, dass jemand die Config-Items aus der GUI rausholt.
        if (slotIndex >= 0 && slotIndex < this.getInventory().size()) {
            return ItemStack.EMPTY;
        }
        return super.quickMove(player, slotIndex);
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex < 0 || slotIndex >= this.getInventory().size()) {
            return;
        }
        if (!(player instanceof ServerPlayerEntity sp)) {
            return;
        }

        boolean isLeft  = button == 0 && actionType == SlotActionType.PICKUP;
        boolean isRight = button == 1 && actionType == SlotActionType.PICKUP;

        switch (slotIndex) {
            case 1: // Slot 1 +1
                if (isLeft) {
                    storage.autoSaveInterval1 = Math.min(60, storage.autoSaveInterval1 + 1);
                    redraw(sp, "Slot 1 Intervall: " + storage.autoSaveInterval1 + " min");
                }
                return;
            case 2: // Slot 1 -1
                if (isLeft) {
                    storage.autoSaveInterval1 = Math.max(1, storage.autoSaveInterval1 - 1);
                    redraw(sp, "Slot 1 Intervall: " + storage.autoSaveInterval1 + " min");
                }
                return;
            case 10: // Slot 2 +1
                if (isLeft) {
                    storage.autoSaveInterval2 = Math.min(60, storage.autoSaveInterval2 + 1);
                    redraw(sp, "Slot 2 Intervall: " + storage.autoSaveInterval2 + " min");
                }
                return;
            case 11: // Slot 2 -1
                if (isLeft) {
                    storage.autoSaveInterval2 = Math.max(1, storage.autoSaveInterval2 - 1);
                    redraw(sp, "Slot 2 Intervall: " + storage.autoSaveInterval2 + " min");
                }
                return;
            case 3: // Chat-Toggle
                if (isLeft) {
                    storage.showSaveMessages = !storage.showSaveMessages;
                    redraw(sp, "Chat-Benachrichtigungen " + (storage.showSaveMessages ? "AN" : "AUS"));
                }
                return;
            case 4: // Last Saves
                if (isLeft) {
                    sp.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                            (syncId, inv, p) -> new LastSavesScreenHandler(syncId, inv, storage, p),
                            Text.literal("Last Saves")));
                }
                return;
            case 5: // Admin Panel (nur OP)
                if (isLeft) {
                    sp.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                            (syncId, inv, p) -> new AdminPanelScreenHandler(syncId, inv, storage, p),
                            Text.literal("Admin Panel")));
                }
                return;
            case 7: // Sound-Toggle
                if (isLeft) {
                    storage.playRestoreSound = !storage.playRestoreSound;
                    redraw(sp, "Restore-Sound " + (storage.playRestoreSound ? "AN" : "AUS"));
                }
                return;
            case 8: // Death-Save-Toggle
                if (isLeft) {
                    storage.autoSaveOnDeath = !storage.autoSaveOnDeath;
                    redraw(sp, "Auto-Save bei Tod " + (storage.autoSaveOnDeath ? "AN" : "AUS"));
                }
                return;
            case 13: // Saves pro Slot
                if (isLeft) {
                    storage.savesPerSlot = Math.min(RestoreInvStorage.MAX_SAVES_PER_SLOT, storage.savesPerSlot + 1);
                    redraw(sp, "Saves pro Slot: " + storage.savesPerSlot);
                } else if (isRight) {
                    storage.savesPerSlot = Math.max(1, storage.savesPerSlot - 1);
                    redraw(sp, "Saves pro Slot: " + storage.savesPerSlot);
                }
                return;
            case 14: // OP-Restore-Toggle
                if (isLeft) {
                    storage.requireOpForRestore = !storage.requireOpForRestore;
                    redraw(sp, "Restore nur fuer OPs: " + (storage.requireOpForRestore ? "AN" : "AUS"));
                }
                return;
            case 18: // Save Config
                if (isLeft) {
                    storage.saveConfig();
                    sp.closeHandledScreen();
                    sp.sendMessage(Text.literal("Konfiguration gespeichert!"), false);
                }
                return;
            default:
                // Unbenutzte Slots: einfach ignorieren.
                return;
        }
    }

    private void redraw(ServerPlayerEntity sp, String msg) {
        storage.updateConfigGUI(this);
        sp.sendMessage(Text.literal(msg), false);
    }

    public void updateConfigGUI(GenericContainerScreenHandler container) {
        storage.updateConfigGUI(container);
    }
}
