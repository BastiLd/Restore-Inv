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

package net.fabricmc.fabric.test.object.builder;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;

// This test is intentionally not an entrypoint to verify the generics of the entity type builder propagate properly
final class EntityTypeBuilderGenericsTest {
	static RegistryKey<EntityType<?>> DUMMY = RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of("test", "dummy"));
	static EntityType<Entity> ENTITY_1 = FabricEntityTypeBuilder.create().build(DUMMY);
	static EntityType<LivingEntity> LIVING_ENTITY_1 = FabricEntityTypeBuilder.createLiving().build(DUMMY);
	static EntityType<TestEntity> TEST_ENTITY_1 = FabricEntityTypeBuilder.createLiving()
			.entityFactory(TestEntity::new)
			.spawnGroup(SpawnGroup.CREATURE)
			.build(DUMMY);
	static EntityType<TestEntity> OLD_TEST = FabricEntityTypeBuilder.<TestEntity>createLiving()
			.entityFactory(TestEntity::new)
			.spawnGroup(SpawnGroup.CREATURE)
			.build(DUMMY);
	static EntityType<TestMob> OLD_MOB = FabricEntityTypeBuilder.<TestMob>createMob()
			.disableSaving()
			.entityFactory(TestMob::new)
			.build(DUMMY);
	static EntityType<TestMob> MOB_TEST = FabricEntityTypeBuilder.createMob()
			.disableSaving()
			.entityFactory(TestMob::new)
			.build(DUMMY);

	private static class TestEntity extends LivingEntity {
		protected TestEntity(EntityType<? extends LivingEntity> entityType, World world) {
			super(entityType, world);
		}

		@Override
		public ItemStack getEquippedStack(EquipmentSlot slot) {
			return ItemStack.EMPTY;
		}

		@Override
		public void equipStack(EquipmentSlot slot, ItemStack stack) {
		}

		@Override
		public Arm getMainArm() {
			return Arm.RIGHT;
		}
	}

	private static class TestMob extends MobEntity {
		protected TestMob(EntityType<? extends MobEntity> entityType, World world) {
			super(entityType, world);
		}
	}
}
