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
import net.minecraft.text.Text;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 9x3 GUI: Slot 0 = Zurueck, Slots 1..26 = bekannte Spieler-UUIDs zum Auswaehlen.
 */
public class AdminPanelScreenHandler extends GenericContainerScreenHandler {
    public final RestoreInvStorage storage;
    public final PlayerEntity player;
    private final List<UUID> slotToUuid = new ArrayList<>();

    public AdminPanelScreenHandler(int syncId, PlayerInventory playerInventory, RestoreInvStorage storage,
            PlayerEntity player) {
        super(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, new SimpleInventory(9 * 3), 3);
        this.storage = storage;
        this.player = player;
        populate();
    }

    private void populate() {
        ItemStack back = new ItemStack(Items.ARROW);
        back.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Zurueck"));
        this.getInventory().setStack(0, back);

        slotToUuid.clear();
        int idx = 1;
        for (UUID uuid : storage.lastSaves.keySet()) {
            if (idx >= this.getInventory().size()) break;
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            String name = resolveDisplayName(uuid);
            head.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
            head.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                    Text.literal("UUID: " + uuid.toString()),
                    Text.literal("Klicke fuer Saves dieses Spielers"))));
            this.getInventory().setStack(idx, head);
            slotToUuid.add(uuid);
            idx++;
        }
    }

    private String resolveDisplayName(UUID uuid) {
        if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp && sp.getServer() != null) {
            net.minecraft.server.network.ServerPlayerEntity online =
                    sp.getServer().getPlayerManager().getPlayer(uuid);
            if (online != null) return online.getGameProfile().getName();
            // UserCache fuer offline-Spieler
            try {
                return sp.getServer().getUserCache().getByUuid(uuid)
                        .map(p -> p.getName())
                        .orElse(uuid.toString().substring(0, 8));
            } catch (Throwable t) {
                return uuid.toString().substring(0, 8);
            }
        }
        return uuid.toString().substring(0, 8);
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex == 0) {
            if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                storage.openConfigScreen(sp);
            }
            return;
        }
        int listIndex = slotIndex - 1;
        if (listIndex < 0 || listIndex >= slotToUuid.size()) return;
        UUID target = slotToUuid.get(listIndex);
        if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
            sp.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                    (syncId, inv, p) -> new PlayerSavesScreenHandler(syncId, inv, storage, p, target),
                    Text.literal("Player Saves")));
        }
    }
}
