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
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
        if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
            MinecraftServer s = sp.getEntityWorld().getServer();
            if (s != null) {
                net.minecraft.server.network.ServerPlayerEntity online = s.getPlayerManager().getPlayer(uuid);
                if (online != null) {
                    com.mojang.authlib.GameProfile gp = online.getGameProfile();
                    String n = profileName(gp);
                    if (n != null && !n.isEmpty()) return n;
                }
            }
        }
        return uuid.toString().substring(0, 8);
    }

    private static String profileName(com.mojang.authlib.GameProfile profile) {
        if (profile == null) return null;
        // GameProfile in neueren Authlib-Versionen ist ein Record mit name(),
        // in aelteren eine Klasse mit getName(). Wir versuchen beide reflektiv.
        try {
            return (String) profile.getClass().getMethod("name").invoke(profile);
        } catch (Throwable ignore) { /* fall through */ }
        try {
            return (String) profile.getClass().getMethod("getName").invoke(profile);
        } catch (Throwable ignore) { /* fall through */ }
        return null;
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
