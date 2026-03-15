package io.wifi.starrailexpress.mixin.item;

import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.index.SRECosmetics;
import io.wifi.starrailexpress.item.BatItem;
import io.wifi.starrailexpress.item.Colors;
import io.wifi.starrailexpress.item.GrenadeItem;
import io.wifi.starrailexpress.item.KnifeItem;
import io.wifi.starrailexpress.item.RevolverItem;
import io.wifi.starrailexpress.util.SkinManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.List;

@Mixin(Item.class)
public abstract class SkinTooltipMixin {

    @Overwrite
    public void appendHoverText(ItemStack itemStack, TooltipContext tooltipContext, List<Component> list,
            TooltipFlag tooltipFlag) {
        String itemName = null;
        var it = itemStack.getItem();
        if (it instanceof KnifeItem) {
            itemName = "knife";
        } else if (it instanceof RevolverItem) {
            itemName = "revolver";
        } else if (it instanceof BatItem) {
            itemName = "bat";
        } else if (it instanceof GrenadeItem) {
            itemName = "grenade";
        }

        if (itemName == null)
            return;
        // 从玩家的CCA组件获取皮肤名称
        Player player = null;
        if (tooltipContext instanceof net.minecraft.world.entity.player.Player) {
            player = (Player) tooltipContext;
        } else {
            player = net.minecraft.client.Minecraft.getInstance().player;
        }

        String skinName = "default";
        skinName = SRECosmetics.getSkin(itemName, itemStack);
        if (skinName.equals("default")) {
            if (player != null) {
                SREPlayerSkinsComponent skinsComponent = SREPlayerSkinsComponent.KEY.get(player);
                skinName = skinsComponent.getSkinFromDataSync(itemStack);
            } else {
                skinName = "default";

            }
        }
        SkinManager.Skin skin = SkinManager.Skin.fromString(itemName, skinName);

        if (skin != null) {
            list.add(Component.translatable("tip.skin").withStyle(style -> style.withColor(Colors.GRAY))
                    .append(Component.translatable("screen.sre.skins." + itemName + "." + (skin.tooltipName))
                            .withStyle(style -> style.withColor(skin.getColor()))));
        }
    }
}
