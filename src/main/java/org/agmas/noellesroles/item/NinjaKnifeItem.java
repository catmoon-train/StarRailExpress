package org.agmas.noellesroles.item;

import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.item.KnifeItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModItems;

import java.util.List;

public class NinjaKnifeItem extends KnifeItem {

    public NinjaKnifeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer attacker)) {
            return InteractionResultHolder.pass(stack);
        }

        // 检查物品冷却
        if (attacker.getCooldowns().isOnCooldown(ModItems.NINJA_KNIFE)) {
            return InteractionResultHolder.pass(stack);
        }

        var collision = KnifeItem.getKnifeTarget(attacker);
        if (collision instanceof net.minecraft.world.phys.EntityHitResult entityHitResult) {
            var target = entityHitResult.getEntity();
            if (target instanceof Player victim) {
                GameUtils.killPlayer(victim, true, attacker, Noellesroles.id("ninja_knife_kill"));
                stack.shrink(1);
                // 30秒冷却
                attacker.getCooldowns().addCooldown(ModItems.NINJA_KNIFE, 30 * 20);
                return InteractionResultHolder.consume(stack);
            }
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 0;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return false;
    }

    @Override
    public String getItemSkinType() {
        return "";
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.ninja_knife.desc").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}