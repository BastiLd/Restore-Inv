package com.example.restoreinv;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.item.ItemStack;
import java.util.List;
import java.util.UUID;
import com.example.restoreinv.MousePositionHelper;

public class PlayerSavesScreen extends HandledScreen<PlayerSavesScreenHandler> {
    public PlayerSavesScreen(PlayerSavesScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        super.drawForeground(context, mouseX, mouseY);
        for (int slot = 0; slot < 3; slot++) {
            int x = this.x + 8 + (slot * 18);
            int y = this.y + 18;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                UUID target = handler.getTargetUuid();
                List<List<ItemStack[]>> saves = handler.storage.getLastSaves(target);
                if (saves != null && !saves.isEmpty()) {
                    List<ItemStack[]> slotSaves = saves.get(slot);
                    if (slotSaves == null || slotSaves.isEmpty())
                        continue;
                    int px = x + 20, py = y;
                    context.fill(px, py, px + 162, py + 58, 0xAA000000);
                    for (int a = 0; a < 4; a++) {
                        context.drawItem(slotSaves.get(slotSaves.size() - 6 + a)[0], px + a * 18, py);
                    }
                    int mainStart = 0;
                    int mainEnd = slotSaves.size() - 5;
                    for (int m = mainStart; m < mainEnd; m++) {
                        int gx = (m - mainStart) % 9, gy = (m - mainStart) / 9;
                        context.drawItem(slotSaves.get(m)[0], px + gx * 18, py + 18 + gy * 18);
                    }
                    context.drawItem(slotSaves.get(slotSaves.size() - 1)[0], px + 8 * 18, py + 3 * 18);
                } else {
                    context.fill(x + 20, y, x + 20 + 162, y + 58, 0xAA000000);
                    context.drawText(this.textRenderer, Text.literal("[Empty]"), x + 25, y + 5, 0xFFFFFF, false);
                }
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        MousePositionHelper.restore();
    }

    @Override
    public void removed() {
        long window = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
        double[] xpos = new double[1];
        double[] ypos = new double[1];
        org.lwjgl.glfw.GLFW.glfwGetCursorPos(window, xpos, ypos);
        MousePositionHelper.save(xpos[0], ypos[0]);
        super.removed();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        // No background
    }
}