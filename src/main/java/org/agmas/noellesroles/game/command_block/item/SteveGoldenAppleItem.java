package org.agmas.noellesroles.game.command_block.item;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.agmas.noellesroles.game.command_block.SteveMiner;

import io.wifi.starrailexpress.cca.SREWeakArmorPlayerComponent;
import io.wifi.starrailexpress.game.GameConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** 食用金苹果时额外给予与弱效护盾试剂完全相同的限时挡刀护盾。 */
public class SteveGoldenAppleItem extends Item {
    public SteveGoldenAppleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return SteveMiner.canAct(player) ? super.use(level, player, hand)
                : InteractionResultHolder.fail(player.getItemInHand(hand));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof Player player) || !SteveMiner.canAct(player)) {
            return stack;
        }
        if (!level.isClientSide) {
            SREWeakArmorPlayerComponent.KEY.get(player).giveWeakArmor(
                    SREWeakArmorPlayerComponent.VIAL_WEAK_ARMOR_DURATION,
                    new HashSet<>(Set.of(GameConstants.DeathReasons.KNIFE)));
        }
        return super.finishUsingItem(stack, level, entity);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
