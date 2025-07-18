/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.fabric.test.screenhandler.client;

import java.util.Optional;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import net.fabricmc.fabric.test.screenhandler.screen.PositionedScreenHandler;

public class PositionedScreen extends HandledScreen<ScreenHandler> {
	private static final Identifier TEXTURE = Identifier.ofVanilla("textures/gui/container/dispenser.png");

	public PositionedScreen(ScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, getPositionText(handler).orElse(title));
	}

	private static Optional<Text> getPositionText(ScreenHandler handler) {
		if (handler instanceof PositionedScreenHandler) {
			BlockPos pos = ((PositionedScreenHandler) handler).getPos();
			return pos != null ? Optional.of(Text.literal("(" + pos.toShortString() + ")")) : Optional.empty();
		} else {
			return Optional.empty();
		}
	}

	@Override
	public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
		renderBackground(drawContext, mouseX, mouseY, delta);
		super.render(drawContext, mouseX, mouseY, delta);
		drawMouseoverTooltip(drawContext, mouseX, mouseY);
	}

	@Override
	protected void init() {
		super.init();
		// Center the title
		titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2;
	}

	@Override
	protected void drawBackground(DrawContext drawContext, float delta, int mouseX, int mouseY) {
		int x = (width - backgroundWidth) / 2;
		int y = (height - backgroundHeight) / 2;
		drawContext.drawTexture(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight, backgroundWidth, backgroundHeight);
	}
}
