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

package net.fabricmc.fabric.api.registry;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.item.ItemConvertible;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.village.VillagerProfession;

import net.fabricmc.fabric.impl.content.registry.VillagerInteractionRegistriesImpl;
import net.fabricmc.fabric.mixin.content.registry.GiveGiftsToHeroTaskAccessor;

/**
 * Registries for modifying villager interactions that
 * villagers have with the world.
 */
public final class VillagerInteractionRegistries {
	private static final Logger LOGGER = LoggerFactory.getLogger(VillagerInteractionRegistries.class);

	private VillagerInteractionRegistries() {
	}

	/**
	 * Registers an item to be collectable (picked up from item entity)
	 * by any profession villagers.
	 *
	 * @param item the item to register
	 * @deprecated Add items to the {@linkplain net.minecraft.registry.tag.ItemTags#VILLAGER_PICKS_UP {@code minecraft:villager_picks_up} item tag} instead.
	 */
	@Deprecated
	public static void registerCollectable(ItemConvertible item) {
		Objects.requireNonNull(item.asItem(), "Item cannot be null!");
		VillagerInteractionRegistriesImpl.getCollectableRegistry().add(item.asItem());
	}

	/**
	 * Registers an item to be used in a composter by farmer villagers.
	 * @param item the item to register
	 */
	public static void registerCompostable(ItemConvertible item) {
		Objects.requireNonNull(item.asItem(), "Item cannot be null!");
		VillagerInteractionRegistriesImpl.getCompostableRegistry().add(item.asItem());
	}

	/**
	 * Registers an item to be edible by villagers.
	 * @param item      the item to register
	 * @param foodValue the amount of breeding power the item has (1 = normal food item, 4 = bread)
	 */
	public static void registerFood(ItemConvertible item, int foodValue) {
		Objects.requireNonNull(item.asItem(), "Item cannot be null!");
		Integer oldValue = VillagerInteractionRegistriesImpl.getFoodRegistry().put(item.asItem(), foodValue);

		if (oldValue != null) {
			LOGGER.info("Overriding previous food value of {}, was: {}, now: {}", item.asItem().toString(), oldValue, foodValue);
		}
	}

	/**
	 * Registers a hero of the village gifts loot table to a profession.
	 * @param profession the profession to modify
	 * @param lootTable  the loot table to associate with the profession
	 */
	public static void registerGiftLootTable(RegistryKey<VillagerProfession> profession, RegistryKey<LootTable> lootTable) {
		Objects.requireNonNull(profession, "Profession cannot be null!");
		Objects.requireNonNull(lootTable, "Loot table identifier cannot be null!");
		RegistryKey<LootTable> oldValue = GiveGiftsToHeroTaskAccessor.fabric_getGifts().put(profession, lootTable);

		if (oldValue != null) {
			LOGGER.info("Overriding previous gift loot table of {} profession, was: {}, now: {}", profession.getValue(), oldValue, lootTable);
		}
	}
}
