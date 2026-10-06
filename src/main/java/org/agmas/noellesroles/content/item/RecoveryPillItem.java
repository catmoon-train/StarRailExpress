package org.agmas.noellesroles.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.roles.killer.dream.DreamHealthComponent;

import java.util.List;

/**
 * 康复药丸（护士体系）。
 *
 * <p>食用后将自己的虚拟血量条补满（{@link DreamHealthComponent}）。
 * 虚拟血量已满（或已死亡）时不消耗。
 *
 * <p>与「药丸」一致，是<b>食物类</b>物品：注册时通过 {@code Item.Properties#food} 声明，
 * 因此走 Minecraft 原生的食用流程（食用动画、食用时长、饱食度、食用音效等），
 * 实际结算放在 {@link #finishUsingItem}（吃完的那一刻）而不是右键瞬间。
 */
public class RecoveryPillItem extends Item {

    public RecoveryPillItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (level.isClientSide || !(user instanceof Player player)) {
            return super.finishUsingItem(stack, level, user);
        }

        DreamHealthComponent health = DreamHealthComponent.KEY.get(player);
        // 虚拟血量已满 / 已死亡时 restore 会失败：提示并且不消耗
        if (!health.restore(DreamHealthComponent.maxHealth())) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.recovery_pill.full"), true);
            return stack;
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.HONEY_BLOCK_PLACE, SoundSource.PLAYERS, 0.8F, 1.4F);
        player.displayClientMessage(
                Component.translatable("message.noellesroles.recovery_pill.used"), true);
        return super.finishUsingItem(stack, level, user);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
