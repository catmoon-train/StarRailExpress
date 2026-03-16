package io.wifi.starrailexpress.item;

import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 可切换皮肤的物品
 * 实现此接口的物品可以在皮肤管理界面中进行皮肤更换
 */
public abstract class SkinableItem extends Item {
    public SkinableItem(Properties properties) {
        super(properties);
    }

    public abstract String getItemSkinType();
    /**
     * 获取物品的默认皮肤名称
     * @return 默认皮肤名称
     */
    public String getDefaultSkin() {
        return "default";
    }
    
    @Override
    public void inventoryTick(ItemStack itemStack, Level level, Entity entity, int i, boolean bl) {
        if (entity instanceof Player player) {
            if (itemStack.get(SREDataComponentTypes.SKIN) == null) {
                itemStack.set(SREDataComponentTypes.SKIN, SREPlayerSkinsComponent.KEY.get(player).getEquippedSkin(getItemSkinType()));
            }
        }
    }
    /**
     * 获取物品支持的皮肤列表
     * @return 皮肤名称数组
     */
    public String[] getAvailableSkins() {
        return new String[]{"default"};
    }
}