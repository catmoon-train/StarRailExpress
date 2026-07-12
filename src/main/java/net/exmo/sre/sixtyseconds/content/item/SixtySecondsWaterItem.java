package net.exmo.sre.sixtyseconds.content.item;

import io.wifi.starrailexpress.content.item.CocktailItem;

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
}
