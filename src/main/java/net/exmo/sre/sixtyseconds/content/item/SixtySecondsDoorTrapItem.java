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
 * 门陷阱：装在庇护所门上，{@link net.exmo.sre.sixtyseconds.SixtySecondsBalance#DOOR_TRAP_DURATION_TICKS 6 分钟}内
 * 入侵者用开锁器潜行入室会触发警报（开锁器原本不报警），触发即消耗；过期自然失效。
 * <p>
 * ★ 安装入口是 {@link ShelterDoorBlock} 的交互短路（{@link #install}）——门方块的 useItemOn
 * 先于物品 useOn 执行并吞掉交互（开门菜单），这里的 useOn 只是非门方块时的提示兜底。
 */
public class SixtySecondsDoorTrapItem extends Item {
    public SixtySecondsDoorTrapItem(Properties properties) {
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
                    Component.translatable("message.noellesroles.sixty_seconds.door_trap_invalid"), true);
            return InteractionResult.FAIL;
        }
        return install(player, (net.minecraft.server.level.ServerLevel) ctx.getLevel(),
                ctx.getClickedPos(), ctx.getItemInHand()) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    /** 给本队装门陷阱（6 分钟时效）；由 {@link ShelterDoorBlock} 交互短路调用。返回是否安装成功。 */
    public static boolean install(ServerPlayer player, net.minecraft.server.level.ServerLevel level,
            net.minecraft.core.BlockPos pos, ItemStack stack) {
        // 用门所在维度取状态（旧代码误用 overworld——游戏跑在其他维度时 teams 为空，物品永远装不上）
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        SixtySecondsState.TeamData team = data.teams.get(SixtySecondsStatsComponent.KEY.get(player).teamId);
        if (team == null) {
            return false;
        }
        long now = level.getGameTime();
        if (team.doorTrapActive(now)) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.door_trap_already"), true);
            return false;
        }
        team.doorTrapEndTick = now + net.exmo.sre.sixtyseconds.SixtySecondsBalance.DOOR_TRAP_DURATION_TICKS;
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        level.playSound(null, pos, SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.BLOCKS, 0.8F, 1.2F);
        player.displayClientMessage(
                Component.translatable("message.noellesroles.sixty_seconds.door_trap_installed")
                        .withStyle(ChatFormatting.GOLD), false);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.noellesroles.sixty_seconds.door_trap")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.noellesroles.sixty_seconds.door_trap.desc")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
