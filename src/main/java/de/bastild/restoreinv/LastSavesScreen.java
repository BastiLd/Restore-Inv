package de.bastild.restoreinv;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class LastSavesScreen extends HandledScreen<LastSavesScreenHandler> {
    private static final Identifier VANILLA_BG = Identifier.of("minecraft", "textures/gui/container/inventory.png");
    private static final int PREVIEW_WIDTH = 176;
    private static final int PREVIEW_HEIGHT = 96;

    public LastSavesScreen(LastSavesScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        super.drawForeground(context, mouseX, mouseY);
        drawOverlay(context, mouseX, mouseY);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawOverlay(context, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        // Hintergrund wird vom HandledScreen gezeichnet.
    }

    private void drawOverlay(DrawContext context, int mouseX, int mouseY) {
        int[][] centeredSlots = {
                { 3, 4, 5 },
                { 12, 13, 14 },
                { 21, 22, 23 }
        };

        boolean previewEnabled = handler.storage.isPreviewEnabled(handler.player.getUuid());

        // Zahnrad-Button oben rechts.
        int gearX = this.x + this.backgroundWidth - 24;
        int gearY = this.y + 6;
        context.drawItem(new ItemStack(Items.COMPARATOR), gearX, gearY);
        if (mouseX >= gearX && mouseX < gearX + 16 && mouseY >= gearY && mouseY < gearY + 16) {
            context.fill(gearX - 2, gearY - 2, gearX + 18, gearY + 18, 0xAA000000);
            context.drawText(this.textRenderer,
                    Text.literal("Preview: " + (previewEnabled ? "ON" : "OFF")),
                    gearX, gearY - 10, 0xFFFFFF, false);
        }

        // Inventar-Vorschau.
        if (previewEnabled) {
            for (int slot = 0; slot < 3; slot++) {
                for (int i = 0; i < 3; i++) {
                    int guiSlot = centeredSlots[slot][i];
                    int col = guiSlot % 9;
                    int row = guiSlot / 9;
                    int x = this.x + 8 + (col * 18);
                    int y = this.y + 18 + (row * 18);
                    if (mouseX < x || mouseX >= x + 16 || mouseY < y || mouseY >= y + 16) {
                        continue;
                    }
                    ItemStack[] inv = handler.previewInventories[slot][i];
                    if (inv != null) {
                        int px = x + 20;
                        int py = y;
                        context.drawTexture(RenderPipelines.GUI_TEXTURED, VANILLA_BG, px, py,
                                0f, 0f, PREVIEW_WIDTH, PREVIEW_HEIGHT, 256, 256);
                        // Armor (oben, 4 Slots).
                        for (int a = 0; a < 4; a++) {
                            context.drawItem(inv[inv.length - 6 + a], px + 7 + a * 18, py + 7);
                        }
                        // Hauptinventar (3x9).
                        int mainEnd = inv.length - 5;
                        for (int m = 0; m < mainEnd; m++) {
                            int gx = m % 9;
                            int gy = m / 9;
                            context.drawItem(inv[m], px + 7 + gx * 18, py + 25 + gy * 18);
                        }
                        // Hotbar.
                        for (int h = 0; h < 9; h++) {
                            context.drawItem(inv[h], px + 7 + h * 18, py + 79);
                        }
                        // Offhand.
                        context.drawItem(inv[inv.length - 1], px + 7 + 8 * 18, py + 79);
                    } else {
                        context.fill(x + 20, y, x + 20 + 162, y + 58, 0xAA000000);
                        context.drawText(this.textRenderer, Text.literal("[Empty]"),
                                x + 25, y + 5, 0xFFFFFF, false);
                    }
                }
            }
        }

        // Hover auf Zurueck-Pfeil-Slot.
        int arrowX = this.x + 8;
        int arrowY = this.y + 18 + (3 * 18);
        if (mouseX >= arrowX && mouseX < arrowX + 16 && mouseY >= arrowY && mouseY < arrowY + 16) {
            context.drawText(this.textRenderer, Text.literal("Zurueck"),
                    arrowX + 20, arrowY + 5, 0xFFFFFF, false);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int gearX = this.x + this.backgroundWidth - 24;
        int gearY = this.y + 6;
        if (mouseX >= gearX && mouseX < gearX + 16 && mouseY >= gearY && mouseY < gearY + 16) {
            boolean current = handler.storage.isPreviewEnabled(handler.player.getUuid());
            handler.storage.setPreviewEnabled(handler.player.getUuid(), !current);
            if (MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().player
                        .sendMessage(Text.literal("Preview " + (!current ? "aktiviert" : "deaktiviert")), false);
            }
            return true;
        }
        return super.mouseClicked(click, doubled);
    }
}
