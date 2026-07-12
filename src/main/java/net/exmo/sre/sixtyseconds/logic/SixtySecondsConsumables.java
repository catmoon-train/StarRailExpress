package net.exmo.sre.sixtyseconds.logic;

import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.content.item.SixtySecondsWaterItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;

/**
 * 食物/水消耗恢复：
 * <ul>
 *   <li><b>食物</b>（含 {@code FOOD}/foodData 组件，涵盖普通食物与药水/蜂蜜等可食物品）→ 恢复饱食度，
 *       量 = {@code nutrition × }{@link #HUNGER_PER_NUTRITION}。</li>
 *   <li><b>水</b>（{@link SixtySecondsWaterItem} 小/中/高三级）→ 恢复口渴值 {@code thirstRestore}。</li>
 * </ul>
 * 由 {@code org.agmas.noellesroles.mixin.SixtySecondsConsumeMixin}（{@code Item.finishUsingItem} HEAD）驱动，
 * 参照 {@code MapStatusBarRuntime.onFinishUsingItem}。
 */
public final class SixtySecondsConsumables {
    public static final int HUNGER_PER_NUTRITION = 5;
    /** 纯药水（无 food 组件）饮用恢复的饱食度（固定值）。 */
    public static final int POTION_HUNGER = 20;

    private SixtySecondsConsumables() {
    }

    public static void onConsume(ServerPlayer player, ItemStack stack) {
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
        int thirst = thirstRestoreOf(stack);
        if (thirst > 0) {
            stats.thirst = Math.min(SixtySecondsStatsComponent.MAX, stats.thirst + thirst);
            stats.sync();
            return;
        }
        int hunger = hungerRestoreOf(stack);
        if (hunger > 0) {
            stats.hunger = Math.min(SixtySecondsStatsComponent.MAX, stats.hunger + hunger);
            stats.sync();
        }
    }

    /** 该物品恢复的饱食度（食物按 foodData，纯药水固定值，水返回 0）。客户端 tooltip 也用它。 */
    public static int hungerRestoreOf(ItemStack stack) {
        if (stack.getItem() instanceof SixtySecondsWaterItem) {
            return 0; // 水只恢复口渴
        }
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food != null) {
            return Math.max(1, food.nutrition() * HUNGER_PER_NUTRITION);
        }
        // 纯药水（无 food 组件）也恢复饱食度
        return stack.getItem() instanceof PotionItem ? POTION_HUNGER : 0;
    }

    /** 该物品恢复的口渴值（非水物品返回 0）。 */
    public static int thirstRestoreOf(ItemStack stack) {
        return stack.getItem() instanceof SixtySecondsWaterItem water ? water.thirstRestore : 0;
    }

    /** 食物等级（low/mid/high，按 nutrition），非食物返回 null。 */
    public static String foodTier(ItemStack stack) {
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null) {
            return null;
        }
        int n = food.nutrition();
        return n <= 3 ? "low" : n <= 6 ? "mid" : "high";
    }
}
