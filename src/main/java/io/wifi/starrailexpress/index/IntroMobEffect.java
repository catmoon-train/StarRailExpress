/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.index;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

import java.util.Objects;

/**
 * 介绍页中的药水效果条目。
 */
public final class IntroMobEffect {
    public final Holder<MobEffect> holder;

    public IntroMobEffect(Holder<MobEffect> holder) {
        this.holder = holder;
    }

    public MobEffect value() {
        return holder.value();
    }

    public ResourceLocation id() {
        return holder.unwrapKey()
                .map(key -> key.location())
                .orElseGet(() -> BuiltInRegistries.MOB_EFFECT.getKey(holder.value()));
    }

    public Component getDisplayName() {
        return holder.value().getDisplayName();
    }

    public int getColor() {
        return 0xFF000000 | holder.value().getColor();
    }

    public MobEffectCategory getCategory() {
        return holder.value().getCategory();
    }

    public String getDescriptionId() {
        return holder.value().getDescriptionId();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof IntroMobEffect other && Objects.equals(id(), other.id());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id());
    }
}
