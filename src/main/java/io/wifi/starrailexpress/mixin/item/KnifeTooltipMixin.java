package io.wifi.starrailexpress.mixin.item;

import dev.doctor4t.ratatouille.util.TextUtils;
import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.index.TMMCosmetics;
import io.wifi.starrailexpress.item.Colors;
import io.wifi.starrailexpress.item.KnifeItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;

@Mixin(KnifeItem.class)
public abstract class KnifeTooltipMixin extends Item {
    public KnifeTooltipMixin(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack itemStack, TooltipContext tooltipContext, List<Component> list, TooltipFlag tooltipFlag) {
        // 从玩家的CCA组件获取皮肤名称
        Player player = null;
        if (tooltipContext instanceof net.minecraft.world.entity.player.Player) {
            player = (Player) tooltipContext;
        } else {
            player = net.minecraft.client.Minecraft.getInstance().player;
        }

        String skinName = "default";
        skinName = TMMCosmetics.getSkin(itemStack);
        if (skinName.equals("default")) {
            if (player != null) {
                SREPlayerSkinsComponent skinsComponent = SREPlayerSkinsComponent.KEY.get(player);
                skinName = skinsComponent.getSkinFromDataSync(itemStack);
            } else {
                skinName = "default";

            }
        }
        KnifeItem.Skin skin = KnifeItem.Skin.fromString(skinName);

        if (skin != null) {
            list.add(Component.translatable("tip.skin").withStyle(style -> style.withColor(Colors.GRAY))
                    .append(Component.literal(TextUtils.formatValueString(skin.tooltipName)).withStyle(style -> style.withColor(skin.getColor()))));
        }

        super.appendHoverText(itemStack, tooltipContext, list, tooltipFlag);
    }
}
