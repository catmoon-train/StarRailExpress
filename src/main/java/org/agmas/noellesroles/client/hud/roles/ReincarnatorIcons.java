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
