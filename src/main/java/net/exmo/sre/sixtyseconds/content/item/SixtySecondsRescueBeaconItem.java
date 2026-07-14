package net.exmo.sre.sixtyseconds.content.item;

import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.SixtySecondsPhase;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsRescue;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 隐藏通关 · 救援信标：游戏日在<b>户外</b>（自家住宅/避难所之外）右键激活 →
 * 全服广播救援倒计时，撑到救援抵达即幸存者胜（{@link SixtySecondsRescue}）。激活即消耗，一局仅一次呼叫。
 */
public class SixtySecondsRescueBeaconItem extends Item {

    public SixtySecondsRescueBeaconItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (level.isClientSide || !(user instanceof ServerPlayer player)
                || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.consume(stack);
        }
        if (!SixtySecondsMod.isActive(level) || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return InteractionResultHolder.pass(stack);
        }
        SixtySecondsState.Data data = SixtySecondsState.get(serverLevel);
        if (data.phase != SixtySecondsPhase.DAY) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.rescue_only_day"), true);
            return InteractionResultHolder.consume(stack);
        }
        if (SixtySecondsRescue.isActive()) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.rescue_already_active"), true);
            return InteractionResultHolder.consume(stack);
        }
        // 必须在户外：不在自家住宅/避难所盒内（信号被屋顶遮蔽）
        SixtySecondsState.TeamData team = data.teams.get(SixtySecondsStatsComponent.KEY.get(player).teamId);
        if (team != null
                && ((team.shelterBox != null && team.shelterBox.contains(player.position()))
                        || (team.residentialBox != null && team.residentialBox.contains(player.position())))) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.rescue_indoors"), true);
            return InteractionResultHolder.consume(stack);
        }
        stack.shrink(1);
        SixtySecondsRescue.activate(serverLevel, player);
        return InteractionResultHolder.consume(stack);
    }
}
