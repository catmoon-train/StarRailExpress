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

package org.agmas.noellesroles.client.hud.roles;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/**
 * 客户端：死因 → 原版物品图标 的映射（用于 HUD / 背包进度面板展示）。
 */
public final class ReincarnatorIcons {

    private static final Map<String, Item> BY_PATH = new HashMap<>();

    static {
        BY_PATH.put("knife_stab", Items.IRON_SWORD);
        BY_PATH.put("revolver_shot", Items.IRON_NUGGET);
        BY_PATH.put("derringer_shot", Items.GOLD_NUGGET);
        BY_PATH.put("bat_hit", Items.STICK);
        BY_PATH.put("nunchuck_hit", Items.CHAIN);
        BY_PATH.put("sniper_rifle", Items.SPYGLASS);
        BY_PATH.put("zero_one_five_shot", Items.REDSTONE);
        BY_PATH.put("grenade", Items.TNT);
        BY_PATH.put("poison", Items.SPIDER_EYE);
        BY_PATH.put("arrow", Items.ARROW);
        BY_PATH.put("trident", Items.TRIDENT);
    }

    public static Item icon(ResourceLocation cause) {
        return BY_PATH.getOrDefault(cause.getPath(), Items.SKELETON_SKULL);
    }

    private ReincarnatorIcons() {
    }
}
