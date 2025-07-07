package com.example.restoreinv;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import com.mojang.blaze3d.systems.RenderSystem;

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
        int[][] centeredSlots = {
                { 3, 4, 5 }, // row 1
                { 12, 13, 14 }, // row 2
                { 21, 22, 23 } // row 3
        };
        boolean previewEnabled = handler.storage.isPreviewEnabled(handler.player.getUuid());
        // Draw gear/settings icon (use comparator as gear)
        int gearX = this.x + this.backgroundWidth - 24;
        int gearY = this.y + 6;
        context.drawItem(new ItemStack(Items.COMPARATOR), gearX, gearY);
        // Gear hover/tooltip
        if (mouseX >= gearX && mouseX < gearX + 16 && mouseY >= gearY && mouseY < gearY + 16) {
            context.fill(gearX - 2, gearY - 2, gearX + 18, gearY + 18, 0xAA000000);
            context.drawText(this.textRenderer, Text.literal("Preview: " + (previewEnabled ? "ON" : "OFF")), gearX,
                    gearY - 10, 0xFFFFFF, false);
        }
        // Inventory preview overlay
        if (previewEnabled) {
            for (int slot = 0; slot < 3; slot++) {
                for (int i = 0; i < 3; i++) {
                    int guiSlot = centeredSlots[slot][i];
                    int col = guiSlot % 9;
                    int row = guiSlot / 9;
                    int x = this.x + 8 + (col * 18);
                    int y = this.y + 18 + (row * 18);
                    if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                        ItemStack[] inv = handler.previewInventories[slot][i];
                        if (inv != null) {
                            int px = x + 20, py = y;
                            // Draw vanilla inventory background
                            RenderSystem.setShader(GameRenderer::getPositionTexProgram);
                            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                            RenderSystem.setShaderTexture(0, VANILLA_BG);
                            context.drawTexture(VANILLA_BG, px, py, 0, 0, PREVIEW_WIDTH, PREVIEW_HEIGHT, 256, 256);
                            // Armor (top row, 4 slots)
                            for (int a = 0; a < 4; a++) {
                                context.drawItem(inv[inv.length - 6 + a], px + 7 + a * 18, py + 7);
                            }
                            // Main inventory (3 rows x 9 cols)
                            int mainStart = 0;
                            int mainEnd = inv.length - 5;
                            for (int m = mainStart; m < mainEnd; m++) {
                                int gx = (m - mainStart) % 9, gy = (m - mainStart) / 9;
                                context.drawItem(inv[m], px + 7 + gx * 18, py + 25 + gy * 18);
                            }
                            // Hotbar (bottom row)
                            for (int h = 0; h < 9; h++) {
                                context.drawItem(inv[h], px + 7 + h * 18, py + 79);
                            }
                            // Offhand (bottom right)
                            context.drawItem(inv[inv.length - 1], px + 7 + 8 * 18, py + 79);
                        } else {
                            context.fill(x + 20, y, x + 20 + 162, y + 58, 0xAA000000);
                            context.drawText(this.textRenderer, Text.literal("[Empty]"), x + 25, y + 5, 0xFFFFFF,
                                    false);
                        }
                    }
                }
            }
        }
        // Back-Arrow Hover
        int arrowX = this.x + 8;
        int arrowY = this.y + 18 + (3 * 18);
        if (mouseX >= arrowX && mouseX < arrowX + 16 && mouseY >= arrowY && mouseY < arrowY + 16) {
            context.drawText(this.textRenderer, Text.literal("Zurück"), arrowX + 20, arrowY + 5, 0xFFFFFF, false);
        }
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void removed() {
        long window = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
        double[] xpos = new double[1];
        double[] ypos = new double[1];
        org.lwjgl.glfw.GLFW.glfwGetCursorPos(window, xpos, ypos);
        super.removed();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int[][] centeredSlots = {
                { 3, 4, 5 }, // row 1
                { 12, 13, 14 }, // row 2
                { 21, 22, 23 } // row 3
        };
        boolean previewEnabled = handler.storage.isPreviewEnabled(handler.player.getUuid());
        // Draw gear/settings icon (use comparator as gear)
        int gearX = this.x + this.backgroundWidth - 24;
        int gearY = this.y + 6;
        context.drawItem(new ItemStack(Items.COMPARATOR), gearX, gearY);
        // Gear hover/tooltip
        if (mouseX >= gearX && mouseX < gearX + 16 && mouseY >= gearY && mouseY < gearY + 16) {
            context.fill(gearX - 2, gearY - 2, gearX + 18, gearY + 18, 0xAA000000);
            context.drawText(this.textRenderer, Text.literal("Preview: " + (previewEnabled ? "ON" : "OFF")), gearX,
                    gearY - 10, 0xFFFFFF, false);
        }
        // Inventory preview overlay (drawn after everything else)
        if (previewEnabled) {
            for (int slot = 0; slot < 3; slot++) {
                for (int i = 0; i < 3; i++) {
                    int guiSlot = centeredSlots[slot][i];
                    int col = guiSlot % 9;
                    int row = guiSlot / 9;
                    int x = this.x + 8 + (col * 18);
                    int y = this.y + 18 + (row * 18);
                    if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                        ItemStack[] inv = handler.previewInventories[slot][i];
                        if (inv != null) {
                            int px = x + 20, py = y;
                            // Draw vanilla inventory background
                            RenderSystem.setShader(GameRenderer::getPositionTexProgram);
                            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                            RenderSystem.setShaderTexture(0, VANILLA_BG);
                            context.drawTexture(VANILLA_BG, px, py, 0, 0, PREVIEW_WIDTH, PREVIEW_HEIGHT, 256, 256);
                            // Armor (top row, 4 slots)
                            for (int a = 0; a < 4; a++) {
                                context.drawItem(inv[inv.length - 6 + a], px + 7 + a * 18, py + 7);
                            }
                            // Main inventory (3 rows x 9 cols)
                            int mainStart = 0;
                            int mainEnd = inv.length - 5;
                            for (int m = mainStart; m < mainEnd; m++) {
                                int gx = (m - mainStart) % 9, gy = (m - mainStart) / 9;
                                context.drawItem(inv[m], px + 7 + gx * 18, py + 25 + gy * 18);
                            }
                            // Hotbar (bottom row)
                            for (int h = 0; h < 9; h++) {
                                context.drawItem(inv[h], px + 7 + h * 18, py + 79);
                            }
                            // Offhand (bottom right)
                            context.drawItem(inv[inv.length - 1], px + 7 + 8 * 18, py + 79);
                        } else {
                            context.fill(x + 20, y, x + 20 + 162, y + 58, 0xAA000000);
                            context.drawText(this.textRenderer, Text.literal("[Empty]"), x + 25, y + 5, 0xFFFFFF,
                                    false);
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        // Identifier bg = Identifier.of("minecraft",
        // "textures/gui/container/generic_54.png");
        // context.drawTexture(bg, this.x, this.y, 0, 0, this.backgroundWidth,
        // this.backgroundHeight, 256, 256);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
        return super.mouseClicked(mouseX, mouseY, button);
    }
}