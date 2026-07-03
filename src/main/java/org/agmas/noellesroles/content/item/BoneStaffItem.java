package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.content.item.api.SREItemProperties;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.config.NoellesRolesConfig;

import java.util.List;

/**
 * 骨杖（亡灵之主专属近战武器）。
 * <p>
 * - 默认 5 点耐久。
 * - 左键攻击玩家时为目标增加感染值，每次攻击消耗 1 点耐久。
 * - 耐久耗尽后<b>不会消失</b>，而是进入充能冷却；冷却结束后自动恢复满耐久。
 * </p>
 * 攻击逻辑在服务端 {@link BoneStaffHandler}（{@code AttackEntityCallback}）中实现，
 * 本类负责物品定义、耐久自动恢复与提示。
 */
public class BoneStaffItem extends Item implements SREItemProperties.LeftClickHurtable {
    public BoneStaffItem(Properties settings) {
        super(settings);
    }

    /** 骨杖逻辑耐久（攻击次数），与提示及恢复逻辑保持一致。 */
    public static int maxDurability() {
        return Math.max(1, NoellesRolesConfig.HANDLER.instance().undeadLordBoneStaffDurability);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        // 仅用于触发挥动动作，攻击逻辑在攻击回调中实现
        return InteractionResultHolder.pass(user.getItemInHand(hand));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (level.isClientSide() || !(entity instanceof Player player)) {
            return;
        }
        // 充能冷却结束后自动恢复满耐久（冷却中由 BoneStaffHandler 阻止攻击）。
        if (stack.getDamageValue() >= maxDurability() && !player.getCooldowns().isOnCooldown(this)) {
            stack.setDamageValue(0);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
        int max = maxDurability();
        tooltip.add(Component.translatable("item.noellesroles.bone_staff.tooltip.durability",
                max - stack.getDamageValue(), max).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.noellesroles.bone_staff.tooltip.infection",
                (int) config.undeadLordBoneStaffInfection).withStyle(ChatFormatting.DARK_PURPLE));
        super.appendHoverText(stack, context, tooltip, type);
    }
}
