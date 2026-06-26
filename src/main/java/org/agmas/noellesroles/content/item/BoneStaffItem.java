package org.agmas.noellesroles.content.item;

import java.util.List;

import io.wifi.starrailexpress.content.item.api.SREItemProperties;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.config.NoellesRolesConfig;

/**
 * 骨杖（亡灵之主专属近战武器）。
 * <p>
 * - 5 点耐久。
 * - 左键攻击玩家时为目标增加 20% 感染值，每次攻击消耗 1 点耐久。
 * </p>
 * 主要逻辑在服务端攻击回调 {@link BoneStaffHandler} 中实现，本类仅负责物品定义与提示。
 */
public class BoneStaffItem extends Item implements SREItemProperties.LeftClickHurtable {
    public BoneStaffItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        // 仅用于触发挥动动作，攻击逻辑在攻击回调中实现
        return InteractionResultHolder.pass(user.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
        int max = Math.max(1, config.undeadLordBoneStaffDurability);
        tooltip.add(Component.translatable("item.noellesroles.bone_staff.tooltip.durability",
                max - stack.getDamageValue(), max).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.noellesroles.bone_staff.tooltip.infection",
                (int) config.undeadLordBoneStaffInfection).withStyle(ChatFormatting.DARK_PURPLE));
        super.appendHoverText(stack, context, tooltip, type);
    }
}
