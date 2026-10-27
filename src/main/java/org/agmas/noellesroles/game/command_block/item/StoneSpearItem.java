package org.agmas.noellesroles.game.command_block.item;

import org.agmas.noellesroles.game.command_block.CommandBlockThrownEntity;

import io.wifi.starrailexpress.api.ChargeableItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/** 按住右键蓄满后投出，贯穿命中的玩家后飞回手里。冷却 20 秒。 */
public class StoneSpearItem extends Item implements ChargeableItem {
    public static final int CHARGE_TICKS = 16;
    public static final int COOLDOWN = 20 * 20;
    private static final int MAX_USE_DURATION = 72000;

    public StoneSpearItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getMaxChargeTime(ItemStack stack, Player player) {
        return CHARGE_TICKS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this) || player.isSpectator()
                || CommandBlockThrownEntity.hasActiveSpear(player)) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingUseTicks) {
        if (!(entity instanceof Player player)) {
            return;
        }
        int charged = this.getUseDuration(stack, entity) - remainingUseTicks;
        if (charged < CHARGE_TICKS || player.getCooldowns().isOnCooldown(this) || player.isSpectator()) {
            return;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!CommandBlockThrownEntity.throwSpear(serverPlayer)) {
                return;
            }
            player.getCooldowns().addCooldown(this, COOLDOWN);
        }
        player.swing(player.getUsedItemHand());
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return MAX_USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity entity,
            InteractionHand hand) {
        return use(player.level(), player, hand).getResult();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }

    public static Item.Properties properties() {
        return new Item.Properties().stacksTo(1).durability(64);
    }
}
