package net.exmo.sre.sixtyseconds.content.item;

import io.wifi.starrailexpress.content.item.CocktailItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 末日60秒模式的水物品（小/中/高三级）。继承 {@code CocktailItem} 获得饮用动画/音效；
 * 饮用后由 {@code SixtySecondsConsumeMixin → SixtySecondsConsumables.onConsume} 按 {@link #thirstRestore} 恢复口渴值。
 */
public class SixtySecondsWaterItem extends CocktailItem {
    /** 等级：small / medium / high。 */
    public final String tier;
    /** 恢复的口渴值。 */
    public final int thirstRestore;

    public SixtySecondsWaterItem(net.minecraft.world.item.Item.Properties properties, String tier, int thirstRestore) {
        super(properties);
        this.tier = tier;
        this.thirstRestore = thirstRestore;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity user) {
        super.finishUsingItem(stack, world, user);
        // CocktailItem 不消耗、水又无 FOOD 组件（原版也不消耗），须在此扣减，否则可无限饮用
        stack.consume(1, user);
        return stack;
    }
}
