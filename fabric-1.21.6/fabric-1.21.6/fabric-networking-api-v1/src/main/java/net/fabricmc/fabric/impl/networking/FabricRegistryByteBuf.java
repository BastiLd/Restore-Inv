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

package net.fabricmc.fabric.impl.networking;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.util.Identifier;

public interface FabricRegistryByteBuf {
	void fabric_setSendableConfigurationChannels(Set<Identifier> globalChannels);

	@Nullable
	Set<Identifier> fabric_getSendableConfigurationChannels();
}
