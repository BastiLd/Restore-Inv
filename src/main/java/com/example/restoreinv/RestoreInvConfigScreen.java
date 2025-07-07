package com.example.restoreinv;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import com.example.restoreinv.MousePositionHelper;

public class RestoreInvConfigScreen extends HandledScreen<RestoreInvConfigScreenHandler> {
    public RestoreInvConfigScreen(RestoreInvConfigScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
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
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        // Optional: Custom background rendering
    }
}