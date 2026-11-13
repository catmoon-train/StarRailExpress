package org.agmas.noellesroles.game.minigame.roulette;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;
import java.util.ArrayList;
import java.util.List;

/** 玩家背包是唯一物品来源，桌面道具只是公开镜像。 */
public final class RouletteItems {
    public static final int CAPACITY = 8;
    private RouletteItems() {}
    public static ItemStack stack(RouletteItem kind) { return new ItemStack(ModItems.ROULETTE_TOOLS.get(kind)); }
    public static RouletteItem kind(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.getItem() instanceof RouletteToolItem tool) return tool.kind();
        if (stack.is(ModItems.MAGNIFYING_GLASS)) return RouletteItem.MAGNIFYING_GLASS;
        if (stack.is(ModItems.CHEWING)) return RouletteItem.CIGARETTE;
        if (stack.is(ModItems.CLIP)) return RouletteItem.BEER;
        if (stack.is(ModItems.STEEL_BALL)) return RouletteItem.HANDSAW;
        if (stack.is(ModItems.REVERSING_CARD)) return RouletteItem.INVERTER;
        if (stack.is(ModItems.TELEPHONE)) return RouletteItem.TELEPHONE;
        return null;
    }
    public static int count(Player player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (kind(s) != null) count += s.getCount();
        }
        return count;
    }
    public static List<Integer> visible(Player player) {
        if (player == null) return List.of();
        List<Integer> result = new ArrayList<>(CAPACITY);
        for (int i = 0; i < player.getInventory().getContainerSize() && result.size() < CAPACITY; i++) {
            ItemStack s = player.getInventory().getItem(i);
            RouletteItem kind = kind(s);
            if (kind != null) {
                int amount = Math.min(s.getCount(), CAPACITY - result.size());
                for (int n = 0; n < amount; n++) result.add(kind.ordinal());
            }
        }
        return result;
    }
    public static int grantCount(int carried, int pending, int requested) {
        return Math.max(0, Math.min(requested, CAPACITY - carried - pending));
    }
}
