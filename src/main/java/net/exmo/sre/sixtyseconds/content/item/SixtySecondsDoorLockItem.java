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
 * 门锁：挂在庇护所门上，{@link net.exmo.sre.sixtyseconds.SixtySecondsBalance#DOOR_LOCK_DURATION_TICKS 6 分钟}内
 * 阻断撬棍强闯（提示「门被锁住了」并拒绝传送）。过期自然失效，可重新挂锁续期。
 * <p>
 * ★ 安装入口是 {@link ShelterDoorBlock} 的交互短路（{@link #install}）——门方块的 useItemOn
 * 先于物品 useOn 执行并吞掉交互（开门菜单），这里的 useOn 只是非门方块时的提示兜底。
 */
public class SixtySecondsDoorLockItem extends Item {
    public SixtySecondsDoorLockItem(Properties properties) {
        super(properties);
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

    /** 给本队挂门锁（6 分钟时效）；由 {@link ShelterDoorBlock} 交互短路调用。返回是否安装成功。 */
    public static boolean install(ServerPlayer player, net.minecraft.server.level.ServerLevel level,
            net.minecraft.core.BlockPos pos, ItemStack stack) {
        // 用门所在维度取状态（旧代码误用 overworld——游戏跑在其他维度时 teams 为空，物品永远装不上）
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        SixtySecondsState.TeamData team = data.teams.get(SixtySecondsStatsComponent.KEY.get(player).teamId);
        if (team == null) {
            return false;
        }
        long now = level.getGameTime();
        if (team.doorLockActive(now)) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.door_lock_already"), true);
            return false;
        }
        team.doorLockEndTick = now + net.exmo.sre.sixtyseconds.SixtySecondsBalance.DOOR_LOCK_DURATION_TICKS;
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
        tooltip.add(Component.translatable("tooltip.noellesroles.sixty_seconds.door_lock")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.noellesroles.sixty_seconds.door_lock.desc")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
