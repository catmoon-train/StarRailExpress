package net.exmo.sre.gooseduck;

import io.wifi.StarRailExpressID;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import net.exmo.sre.gooseduck.role.GooseDuckRoles;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 鸭的主动「破坏」技能（狼人破坏任务的主动版）。
 * <p>
 * 通过统一技能系统（{@link RoleSkill}）注册在 G 键：触发一次全图关灯
 * （{@link SREWorldBlackoutComponent#triggerBlackout(boolean, int)}），扰乱鹅完成小游戏任务的节奏，
 * 90 秒冷却、HUD 显示。仅在成功触发时才进入冷却（{@code handler} 返回 true）。
 * <p>
 * 除此之外，鸭作为杀手还会自动获得地图配置的「破坏小游戏任务」
 * （{@code SREPlayerMinigameTaskComponent.refreshSabotageTask} 依据 {@code role.isKiller()} 派发）。
 */
public final class GooseDuckSabotage {
    public static final ResourceLocation SABOTAGE_ID = StarRailExpressID.canyueId("duck_sabotage");
    private static final int BLACKOUT_TICKS = 20 * 20;

    private GooseDuckSabotage() {
    }

    public static void register() {
        RoleSkill.register(GooseDuckRoles.DUCK,
                RoleSkill.skill(SABOTAGE_ID, "skill.noellesroles.gooseduck.sabotage", ctx -> {
                    ServerPlayer player = ctx.player();
                    if (player.isSpectator() || !(player.level() instanceof ServerLevel level)) {
                        return false;
                    }
                    boolean triggered = SREWorldBlackoutComponent.KEY.get(level).triggerBlackout(true, BLACKOUT_TICKS);
                    if (triggered) {
                        player.displayClientMessage(
                                Component.translatable("message.gooseduck.sabotage.triggered")
                                        .withStyle(ChatFormatting.GOLD),
                                true);
                    }
                    return triggered;
                }).cooldownSeconds(90).showOnHud(true).announceToSelf(true).build());
    }
}
