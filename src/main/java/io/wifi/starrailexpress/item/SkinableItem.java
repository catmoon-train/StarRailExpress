package io.wifi.starrailexpress.item;

import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.util.SkinManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 可切换皮肤的物品
 * 实现此接口的物品可以在皮肤管理界面中进行皮肤更换
 */
public abstract class SkinableItem extends Item {
    public SkinableItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack itemStack, TooltipContext tooltipContext, List<Component> list,
            TooltipFlag tooltipFlag) {
        String itemName = this.getItemSkinType();
        if (itemName == null)
            return;
        // 从玩家的CCA组件获取皮肤名称
        Player player = null;
        if (tooltipContext instanceof net.minecraft.world.entity.player.Player p) {
            player = p;
        } else {
            player = null;
        }
        if (player == null) {
            super.appendHoverText(itemStack, tooltipContext, list, tooltipFlag);
            return;
        }

        String skinName = "default";
        SREPlayerSkinsComponent skinsComponent = SREPlayerSkinsComponent.KEY.get(player);
        skinName = skinsComponent.getSkinFromDataSync(itemStack);
        if (skinName == null) {
            skinName = "default";
        }
        SkinManager.Skin skin = SkinManager.Skin.fromString(itemName, skinName);

        if (skin != null) {
            list.add(Component.translatable("tip.skin").withStyle(style -> style.withColor(Colors.GRAY))
                    .append(Component.translatableWithFallback(
                            "screen.sre.skins." + itemName + "." + (skin.tooltipName) + ".name", skin.tooltipName)
                            .withStyle(style -> style.withColor(skin.getColor()))));
        } else if (skinName.equals("default") || skinName == null) {
            list.add(Component.translatable("tip.skin").withStyle(style -> style.withColor(Colors.GRAY))
                    .append(Component.translatableWithFallback("screen.sre.skins.default", "Default"))
                    .withStyle(ChatFormatting.GRAY));
        }
        super.appendHoverText(itemStack, tooltipContext, list, tooltipFlag);
    }

    public abstract String getItemSkinType();

    /**
     * 获取物品的默认皮肤名称
     * 
     * @return 默认皮肤名称
     */
    public String getDefaultSkin() {
        return "default";
    }

    @Override
    public void inventoryTick(ItemStack itemStack, Level level, Entity entity, int i, boolean bl) {
        if (entity instanceof Player player) {
            if (itemStack.get(SREDataComponentTypes.SKIN) == null) {
                itemStack.set(SREDataComponentTypes.SKIN,
                        SREPlayerSkinsComponent.KEY.get(player).getEquippedSkin(getItemSkinType()));
            }
        }
    }

    /**
     * 获取物品支持的皮肤列表
     * 
     * @return 皮肤名称数组
     */
    public String[] getAvailableSkins() {
        return new String[] { "default" };
    }
}