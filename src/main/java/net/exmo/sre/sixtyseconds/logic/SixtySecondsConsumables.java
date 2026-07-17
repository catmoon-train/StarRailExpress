package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.content.item.CocktailItem;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.content.item.SixtySecondsWaterItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import org.agmas.noellesroles.content.item.ChefWaterItem;

/**
 * 食物/水消耗恢复：
 * <ul>
 *   <li><b>食物</b>（含 {@code FOOD}/foodData 组件，涵盖普通食物等）→ 恢复饱食度，
 *       量 = {@code nutrition × }{@link #HUNGER_PER_NUTRITION}。</li>
 *   <li><b>水</b>（{@link SixtySecondsWaterItem} 小/中/高三级）→ 恢复口渴值 {@code thirstRestore}。</li>
 *   <li><b>饮品</b>（{@link CocktailItem} 及其子类）→ 恢复 15 口渴值。</li>
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
        int hunger = 0;
        if (thirst > 0) {
            stats.thirst = Math.min(SixtySecondsStatsComponent.MAX, stats.thirst + thirst);
            if (!(stack.getItem() instanceof SixtySecondsWaterItem)
                    && !(stack.getItem() instanceof PotionItem)) {
                // 非纯水饮品和药水：同时恢复饱食度
                hunger = hungerRestoreOf(stack);
                if (hunger > 0) {
                    stats.hunger = Math.min(SixtySecondsStatsComponent.MAX, stats.hunger + hunger);
                }
                applyNegativeEffects(player, stack);
            }
            stats.sync();
            return;
        }
        hunger = hungerRestoreOf(stack);
        if (hunger > 0) {
            stats.hunger = Math.min(SixtySecondsStatsComponent.MAX, stats.hunger + hunger);
            stats.sync();
        }
        applyNegativeEffects(player, stack);
    }

    /** 该物品恢复的饱食度（食物按 foodData，纯药水固定值，水/饮品返回 0）。客户端 tooltip 也用它。 */
    public static int hungerRestoreOf(ItemStack stack) {
        if (stack.getItem() instanceof SixtySecondsWaterItem
                || stack.getItem() instanceof CocktailItem
                || stack.getItem() instanceof PotionItem) {
            return 0; // 水、饮品、药水只恢复口渴
        }
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food != null) {
            return Math.max(1, food.nutrition() * HUNGER_PER_NUTRITION);
        }
        // 纯药水（无 food 组件）也恢复饱食度
        return stack.getItem() instanceof PotionItem ? POTION_HUNGER : 0;
    }

    /** 该物品恢复的口渴值。 */
    public static int thirstRestoreOf(ItemStack stack) {
        if (stack.getItem() instanceof SixtySecondsWaterItem water) {
            return water.thirstRestore;
        }
        if (stack.getItem() instanceof CocktailItem) {
            return 15; // 饮品：+15 口渴
        }
        if (stack.is(Items.MILK_BUCKET)) {
            return 15;
        }
        if (stack.getItem() instanceof ChefWaterItem) {
            return 10;
        }
        if (stack.getItem() instanceof PotionItem) {
            return 15; // 原版药水：+15 口渴
        }
        return 0;
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

    /**
     * 对原版负面食物施加 debuff（60s 模式中食用惩罚）。
     * <table>
     *   <tr><th>食物</th><th>效果</th></tr>
     *   <tr><td>腐肉</td><td>饥饿 30s + 20% 中毒 5s</td></tr>
     *   <tr><td>毒马铃薯</td><td>中毒 5s</td></tr>
     *   <tr><td>河豚</td><td>中毒 15s(II) + 反胃 15s + 饥饿 30s(II)</td></tr>
     *   <tr><td>蜘蛛眼</td><td>中毒 8s</td></tr>
     *   <tr><td>生鸡肉</td><td>饥饿 20s + 30% 中毒 5s</td></tr>
     *   <tr><td>生牛肉/猪/羊/兔</td><td>饥饿 20s + 15% 中毒 3s</td></tr>
     * </table>
     * 注：原版已有的食物效果（如腐肉自带饥饿、河豚自带中毒等）会与此叠加，进一步加重惩罚。
     */
    private static void applyNegativeEffects(ServerPlayer player, ItemStack stack) {
        Item item = stack.getItem();
        if (item == Items.ROTTEN_FLESH) {
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 20 * 30, 0,
                    false, true, true));
            if (player.getRandom().nextFloat() < 0.2f) {
                player.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 5, 0,
                        false, true, true));
            }
        } else if (item == Items.POISONOUS_POTATO) {
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 5, 0,
                    false, true, true));
        } else if (item == Items.PUFFERFISH) {
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 15, 1,
                    false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 20 * 15, 0,
                    false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 20 * 30, 1,
                    false, true, true));
        } else if (item == Items.SPIDER_EYE) {
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 8, 0,
                    false, true, true));
        } else if (item == Items.CHICKEN) {
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 20 * 20, 0,
                    false, true, true));
            if (player.getRandom().nextFloat() < 0.3f) {
                player.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 5, 0,
                        false, true, true));
            }
        } else if (item == Items.BEEF || item == Items.PORKCHOP
                || item == Items.MUTTON || item == Items.RABBIT) {
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 20 * 20, 0,
                    false, true, true));
            if (player.getRandom().nextFloat() < 0.15f) {
                player.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 3, 0,
                        false, true, true));
            }
        }
    }
}
