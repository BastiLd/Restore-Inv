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

package net.fabricmc.fabric.mixin.object.builder.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.block.WoodType;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.util.Identifier;

import net.fabricmc.fabric.impl.object.builder.client.SignTypeTextureHelper;

@Mixin(WoodType.class)
abstract class WoodTypeMixin {
	@Inject(method = "register", at = @At("RETURN"))
	private static void onReturnRegister(WoodType type, CallbackInfoReturnable<WoodType> cir) {
		if (SignTypeTextureHelper.shouldAddTextures) {
			final Identifier identifier = Identifier.of(type.name());
			TexturedRenderLayers.SIGN_TYPE_TEXTURES.put(type, TexturedRenderLayers.SIGN_SPRITE_MAPPER.map(identifier));
			TexturedRenderLayers.HANGING_SIGN_TYPE_TEXTURES.put(type, TexturedRenderLayers.HANGING_SIGN_SPRITE_MAPPER.map(identifier));
		}
	}
}
