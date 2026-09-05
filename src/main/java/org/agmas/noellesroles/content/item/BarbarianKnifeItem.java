package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.api.ChargeableItem;
import io.wifi.starrailexpress.content.item.KnifeItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModEffects;

/**
 * 野人魔了形态专属的刀。
 *
 * <p>它不是制式杀手刀，因此不会被杀手刀的耐久标记或冷却表影响；目标阵营限制由
 * {@code ModRoles.BARBARIAN.onUseKnifeHit} 在服务端统一校验。</p>
 */
public final class BarbarianKnifeItem extends KnifeItem implements ChargeableItem {
    public BarbarianKnifeItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getMaxChargeTime(ItemStack stack, Player player) {
        if (player.hasEffect(ModEffects.TWO_DIMENSIONAL_CAMERA)) {
            return 4;
        }
        return 8;
    }

    @Override
    public float getChargePercentage(ItemStack stack, Player player, int ticksUsingItem) {
        return Math.min((float) ticksUsingItem / getMaxChargeTime(stack, player), 1f);
    }

    @Override
    public float getMaxStamina(ItemStack stack, Player player) {
        return getMaxChargeTime(stack, player);
    }

    @Override
    public boolean hasSpecialVisualEffects(ItemStack stack, Player player) {
        return true;
    }
}
