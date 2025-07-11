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

package net.fabricmc.fabric.mixin.item;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.enchantment.Enchantment;

import net.fabricmc.fabric.impl.item.EnchantmentUtil;

@Mixin(Enchantment.Builder.class)
public class EnchantmentBuilderMixin implements EnchantmentUtil.BuilderExtensions {
	@Unique
	private boolean didModify = false;

	// Target all methods in the builder, but only mark as modified if the return value is the builder itself.
	@Inject(method = "*", at = @At(value = "RETURN"))
	private void markModified(CallbackInfoReturnable<?> cir) {
		if (cir.getReturnValue() == this) {
			didModify = true;
		}
	}

	@Override
	public void fabric$resetModified() {
		didModify = false;
	}

	@Override
	public boolean fabric$didModify() {
		return didModify;
	}
}
