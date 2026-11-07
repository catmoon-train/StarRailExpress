package org.agmas.noellesroles.game.command_block;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Steve 的四种虚拟采矿材料；原版物品仅用于图标与名称，不进入物品栏。 */
public enum SteveMaterial {
    PLANKS("planks"), IRON("iron"), GOLD("gold"), DIAMOND("diamond");

    private final String key;

    SteveMaterial(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public Item item() {
        return switch (this) {
            case PLANKS -> Items.OAK_PLANKS;
            case IRON -> Items.IRON_INGOT;
            case GOLD -> Items.GOLD_INGOT;
            case DIAMOND -> Items.DIAMOND;
        };
    }

    public static SteveMaterial forItem(Item item) {
        for (SteveMaterial material : values()) {
            if (material.item() == item) return material;
        }
        return null;
    }
}
