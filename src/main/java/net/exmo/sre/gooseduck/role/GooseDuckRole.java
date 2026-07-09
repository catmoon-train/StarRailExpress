package net.exmo.sre.gooseduck.role;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * 鹅鸭杀模式的角色基类（鹅 / 鸭）。
 * <p>
 * 该模式「只刷新小游戏任务」：这里恒定屏蔽普通 Mood 任务的刷新
 * （{@link #canRefreshTask} 返回 false），普通任务不再生成，玩家的任务完全来自地图上的
 * 小游戏任务点（由 {@code SREPlayerMinigameTaskComponent} 在开启 {@code minigameQuestEnabled}
 * 后独立计时派发）。
 * <p>
 * 同时标记为「其他模式职业」（{@code setOtherModeRole(true)}）并禁止被其它职业随机到，
 * 避免混入谋杀模式等常规职业池。
 */
public class GooseDuckRole extends NormalRole {

    public GooseDuckRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean hideScoreboard) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, hideScoreboard);
        setCanSeeTime(true);
        setCanSeeCoin(true);
        setCanBeRandomedByOtherRoles(false);
        setOtherModeRole(true);
    }

    /** 只刷新小游戏任务：屏蔽所有普通任务的刷新。 */
    @Override
    public boolean canRefreshTask(Player player, SREPlayerTaskComponent.Task taskType) {
        return false;
    }
}
