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

package net.fabricmc.fabric.test.item.gametest;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.test.TestContext;
import net.minecraft.util.collection.DefaultedList;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.test.item.CustomDamageTest;

public class RecipeGameTest {
	@GameTest
	public void vanillaRemainderTest(TestContext context) {
		CraftingRecipeInput inventory = CraftingRecipeInput.create(1, 2, List.of(
				new ItemStack(Items.WATER_BUCKET),
				new ItemStack(Items.DIAMOND)));

		DefaultedList<ItemStack> remainderList = CraftingRecipe.collectRecipeRemainders(inventory);

		assertStackList(remainderList, "Testing vanilla recipe remainder.",
				new ItemStack(Items.BUCKET),
				ItemStack.EMPTY);

		context.complete();
	}

	@GameTest
	public void fabricRemainderTest(TestContext context) {
		CraftingRecipeInput inventory = CraftingRecipeInput.create(1, 4, List.of(
				new ItemStack(CustomDamageTest.WEIRD_PICK),
				withDamage(new ItemStack(CustomDamageTest.WEIRD_PICK), 10),
				withDamage(new ItemStack(CustomDamageTest.WEIRD_PICK), 31),
				new ItemStack(Items.DIAMOND)));

		DefaultedList<ItemStack> remainderList = CraftingRecipe.collectRecipeRemainders(inventory);

		assertStackList(remainderList, "Testing fabric recipe remainder.",
				withDamage(new ItemStack(CustomDamageTest.WEIRD_PICK), 1),
				withDamage(new ItemStack(CustomDamageTest.WEIRD_PICK), 11),
				ItemStack.EMPTY,
				ItemStack.EMPTY);

		context.complete();
	}

	private void assertStackList(DefaultedList<ItemStack> stackList, String extraErrorInfo, ItemStack... stacks) {
		for (int i = 0; i < stackList.size(); i++) {
			ItemStack currentStack = stackList.get(i);
			ItemStack expectedStack = stacks[i];

			assertStacks(currentStack, expectedStack, extraErrorInfo);
		}
	}

	static void assertStacks(ItemStack currentStack, ItemStack expectedStack, String extraErrorInfo) {
		if (currentStack.isEmpty() && expectedStack.isEmpty()) {
			return;
		}

		if (!currentStack.isOf(expectedStack.getItem())) {
			throw new RuntimeException("Item stacks dont match. " + extraErrorInfo);
		}

		if (currentStack.getCount() != expectedStack.getCount()) {
			throw new RuntimeException("Size doesnt match. " + extraErrorInfo);
		}

		if (!ItemStack.areItemsAndComponentsEqual(currentStack, expectedStack)) {
			throw new RuntimeException("Stack doesnt match. " + extraErrorInfo);
		}
	}

	static ItemStack withDamage(ItemStack stack, int damage) {
		stack.setDamage(damage);
		return stack;
	}
}
