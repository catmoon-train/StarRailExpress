package org.agmas.noellesroles.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import org.agmas.noellesroles.spear.SpearCombat;
import org.agmas.noellesroles.spear.SpearConfig;

import java.util.List;

/**
 * 下界合金矛 —— 警长阵营「骑兵」的专属武器。
 *
 * <ul>
 * <li>左键：直刺（穿刺多个目标），需要满蓄力；命中玩家时原版伤害 ×2 转为虚拟伤害。</li>
 * <li>右键：举矛蓄力冲锋，速度越快伤害越高（附带击退 / 击落坐骑）。</li>
 * <li>可附魔「突进」：命中后向前冲刺。</li>
 * </ul>
 */
public class NetheriteSpearItem extends Item
        implements SpearConfig.SpearWeapon, io.wifi.starrailexpress.content.item.api.SREItemProperties.TrainWeapon {

    /** 骑兵矛耐久。 */
    public static final int DURABILITY = 30;

    /** 挥击时长 1.15 秒 → 攻速修正 {@code 1 / 1.15 - 4}。 */
    public static final float ATTACK_SPEED_MODIFIER = 1.0F / 1.15F - 4.0F;

    public NetheriteSpearItem(Properties properties) {
        super(properties);
    }

    /** 注册处使用的物品属性：单持、下界合金耐久、下界合金攻击力、防火。 */
    public static Properties createProperties() {
        return new Item.Properties()
                .stacksTo(1)
                .durability(DURABILITY)
                .attributes(SwordItem.createAttributes(Tiers.NETHERITE, 0, ATTACK_SPEED_MODIFIER))
                .fireResistant();
    }

    // ── 右键：举矛蓄力 ────────────────────────────────────────────

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    /** 每 tick 推进一次蓄力冲锋结算（举着矛右键时才生效）。 */
    /** 矛不能挖方块（创造模式除外）。 */
    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player miner) {
        return !miner.isCreative();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        int lunge = SpearCombat.lungeLevel(stack);
        tooltip.add(Component.translatable("item.noellesroles.netherite_spear.tooltip")
                .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("item.noellesroles.netherite_spear.tooltip.charge")
                .withStyle(ChatFormatting.AQUA));
        if (lunge > 0) {
            tooltip.add(Component.translatable("item.noellesroles.netherite_spear.tooltip.lunge", lunge)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        tooltip.add(Component.translatable("item.noellesroles.netherite_spear.tooltip.durability",
                        Math.max(0, stack.getMaxDamage() - stack.getDamageValue()), stack.getMaxDamage())
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
