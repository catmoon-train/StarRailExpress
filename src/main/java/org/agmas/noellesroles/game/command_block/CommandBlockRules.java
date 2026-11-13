package org.agmas.noellesroles.game.command_block;

import java.util.List;

import org.agmas.harpymodloader.SREDisableManager;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 命令方块事件的纯规则：杀手位占用、绑定职业互斥、破坏配额。
 * 不碰世界，方便对照数值。
 */
public final class CommandBlockRules {
    private CommandBlockRules() {
    }

    /** n = ceil(原杀手数量 * 1.5)。至少 1，避免 0 杀手配置把事件做成空转。 */
    public static int markedCount(int originalKillerCount) {
        int killers = Math.max(1, originalKillerCount);
        return (int) Math.ceil(killers * 1.5D);
    }

    /** 破坏配额（命令方块血量）= ceil(原杀手数量 * 1.5) + 2。异常人数仍只按 markedCount 算。 */
    public static int destructionQuota(int originalKillerCount) {
        return markedCount(originalKillerCount) + 2;
    }

    public static int destructionStage(int killerDeaths, int quota) {
        if (quota <= 0 || killerDeaths <= 0) {
            return 0;
        }
        if (killerDeaths >= quota) {
            return 3;
        }
        return Math.min(2, killerDeaths * 3 / quota);
    }

    /** 会占杀手名额，或像承太郎一样绑定在杀手职业上。 */
    public static boolean isKillerOccupation(SRERole role) {
        return role != null
                && role.canUseKiller()
                && !role.isInnocent()
                && !role.isNeutrals()
                && !role.isNeutralForKiller();
    }

    public static boolean isExcluded(SRERole role) {
        if (role == null || role == TMMRoles.CIVILIAN) {
            return false;
        }
        if (role == ModRoles.COMMAND_BLOCK || role == ModRoles.ABNORMAL || role == ModRoles.STEVE
                || isPoolRole(role)) {
            return true;
        }
        if (isKillerOccupation(role) || role.isKiller()) {
            return true;
        }
        for (SRERole parent : role.occupationedRoles) {
            if (isKillerOccupation(parent)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 命令方块事件局里<b>绝不允许出现</b>的职业（事件与它们的玩法互斥）。
     *
     * <p>名单不再写死在这里：职业在自己的定义里调用
     * {@link SRERole#setCannotAppearInCommandBlockEvent(boolean)} 声明自己是
     * 「无法在命令方块事件中出现的职业」，这里只统一读取该标记。
     *
     * <p>
     * 与 {@link #isExcluded(SRERole)} 的区别：{@code isExcluded} 描述的是「抽象改写池的候选资格」
     * （杀手/绑定职业/池内职业等），这里是「事件局点名禁用的职业」。
     * 抽取结果里一旦出现这些职业，{@code CommandBlockRolePoolMixin} 会把它替换成平民职业。
     */
    public static boolean isEventBanned(SRERole role) {
        return role != null && role.isCannotAppearInCommandBlockEvent();
    }

    /**
     * 异常改写池。Dream 复用现有杀手（铁斧 / 制酒 / 商店），不另写一套。
     *
     * <p>遵从职业启用管理（enableroles）：被地图、名单或配置 ban 掉的职业不会进池，
     * 也就不会被改写出来。池内职业被全部禁用时返回空列表，由调用方停止改写。</p>
     */
    public static List<SRERole> pool() {
        List<SRERole> all = List.of(ModRoles.STONE_MAN, ModRoles.HIM, ModRoles.ERROR_404, ModRoles.DREAM,
                ModRoles.ENTITY_303, ModRoles.ENTITY_NULL, ModRoles.FARLANDS, ModRoles.WHITE_EYES,
                ModRoles.STRINGER, ModRoles.PAPER_DOLL, ModRoles.MUSTARD, ModRoles.PENDULUM,
                ModRoles.CANDLE_SHADE, ModRoles.DREADLORD);
        return all.stream().filter(role -> !SREDisableManager.isRoleDisabled(role)).toList();
    }

    public static boolean isPoolRole(SRERole role) {
        return role != null && pool().contains(role);
    }
}
