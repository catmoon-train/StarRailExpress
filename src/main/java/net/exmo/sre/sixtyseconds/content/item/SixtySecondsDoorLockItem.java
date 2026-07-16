package net.exmo.sre.sixtyseconds.content.item;

import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.content.block.ShelterDoorBlock;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;

/**
 * 门锁（四级）：挂在庇护所门上按等级阻断闯入，过期自然失效，可挂更高级锁替换。
 * <ul>
 *   <li>门锁（1 级）：仅挡<b>普通撬棍</b>，时效 2 分钟；</li>
 *   <li>强化门锁（2 级）：挡开锁器 + 撬棍 + 强化撬棍，时效 4 分钟；</li>
 *   <li>阻击门锁（3 级）：挡开锁器 + 精致开锁器 + 撬棍 + 强化撬棍 + 液压撬棍，时效 8 分钟；</li>
 *   <li>合金门锁（4 级）：挡所有闯入工具，时效 16 分钟，通电。</li>
 * </ul>
 * ★ 安装入口是 {@link ShelterDoorBlock} 的交互短路（{@link #install}）——门方块的 useItemOn
 * 先于物品 useOn 执行并吞掉交互（开门菜单），这里的 useOn 只是非门方块时的提示兜底。
 */
public class SixtySecondsDoorLockItem extends Item {

    /** 锁等级 1..4。 */
    private final int tier;

    public SixtySecondsDoorLockItem(Properties properties) {
        this(properties, 1);
    }

    public SixtySecondsDoorLockItem(Properties properties, int tier) {
        super(properties);
        this.tier = tier;
    }

    public int tier() {
        return tier;
    }

    /** 各级时效：1→2 分钟，2→4 分钟，3→8 分钟，4→16 分钟。 */
    public static long durationTicks(int tier) {
        return 20L * 60L * (2L << Math.max(0, Math.min(3, tier - 1)));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getLevel().isClientSide() || !(ctx.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        if (!SixtySecondsMod.isActive(ctx.getLevel())) {
            return InteractionResult.PASS;
        }
        if (!(ctx.getLevel().getBlockState(ctx.getClickedPos()).getBlock() instanceof ShelterDoorBlock)) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.door_lock_invalid"), true);
            return InteractionResult.FAIL;
        }
        return install(player, (net.minecraft.server.level.ServerLevel) ctx.getLevel(),
                ctx.getClickedPos(), ctx.getItemInHand()) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    /** 给本队挂门锁（按锁等级 2/4/8 分钟时效）；由 {@link ShelterDoorBlock} 交互短路调用。返回是否安装成功。 */
    public static boolean install(ServerPlayer player, net.minecraft.server.level.ServerLevel level,
            net.minecraft.core.BlockPos pos, ItemStack stack) {
        // 用门所在维度取状态（旧代码误用 overworld——游戏跑在其他维度时 teams 为空，物品永远装不上）
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        SixtySecondsState.TeamData team = data.teams.get(SixtySecondsStatsComponent.KEY.get(player).teamId);
        if (team == null) {
            return false;
        }
        int tier = stack.getItem() instanceof SixtySecondsDoorLockItem lock ? lock.tier() : 1;
        long now = level.getGameTime();
        // 已有锁：只允许换更高级的锁（顶掉旧锁），同级/低级拒绝
        if (team.doorLockActive(now) && tier <= team.doorLockTier) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.door_lock_already"), true);
            return false;
        }
        team.doorLockTier = tier;
        team.doorLockEndTick = now + durationTicks(tier);
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 1.4F);
        player.displayClientMessage(
                Component.translatable("message.noellesroles.sixty_seconds.door_lock_installed")
                        .withStyle(ChatFormatting.GOLD), false);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        String baseKey = switch (tier) {
            case 2 -> "tooltip.noellesroles.sixty_seconds.door_lock_reinforced";
            case 3 -> "tooltip.noellesroles.sixty_seconds.door_lock_ultimate";
            case 4 -> "tooltip.noellesroles.sixty_seconds.door_lock_alloy";
            default -> "tooltip.noellesroles.sixty_seconds.door_lock";
        };
        tooltip.add(Component.translatable(baseKey)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(baseKey + ".desc")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
