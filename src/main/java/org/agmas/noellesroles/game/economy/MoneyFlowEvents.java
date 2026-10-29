package org.agmas.noellesroles.game.economy;

import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnGameStarted;
import io.wifi.starrailexpress.event.OnRoleSkillUse;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 金币收支账本的埋点注册（仅在事件足够时使用）。
 * <p>
 * 绝大多数分类都在真正改动余额的源码位置直接埋点（击杀 / 任务 / 被动 / 扒窃 / 偷窃 / 掠夺 /
 * 存折 / 商店 / 售货机 / 兜底），本类只补两件靠事件更省事的事：
 * <ul>
 * <li>技能收入 / 支出：{@link OnRoleSkillUse} 前后余额差，一次比对即可覆盖全部职业技能</li>
 * <li>开局 / 结束清账：{@link OnGameStarted} / {@link OnGameEnd}</li>
 * </ul>
 * 没有逐 tick 回调，没有额外对象分配。
 */
public final class MoneyFlowEvents {

    /** 技能释放前的余额快照，用于在 AFTER 计算技能本身的收支 */
    private static final Map<UUID, Integer> SKILL_BALANCE_SNAPSHOT = new HashMap<>();

    private MoneyFlowEvents() {
    }

    public static void register() {
        // 技能收支：用前后余额差判断，避免为每个职业技能单独埋点
        OnRoleSkillUse.BEFORE.register(MoneyFlowEvents::onSkillBefore);
        OnRoleSkillUse.AFTER.register(MoneyFlowEvents::onSkillAfter);

        // 一局一份账本
        OnGameStarted.EVENT.register(serverLevel -> MoneyFlow.resetAll());
        OnGameEnd.EVENT.register((serverLevel, gameWorldComponent) -> MoneyFlow.resetAll());
    }

    private static boolean onSkillBefore(Player player, io.wifi.starrailexpress.api.SRERole role) {
        if (player == null || player.level().isClientSide())
            return true;
        SKILL_BALANCE_SNAPSHOT.put(player.getUUID(), SREPlayerShopComponent.KEY.get(player).balance);
        return true;
    }

    private static boolean onSkillAfter(Player player, io.wifi.starrailexpress.api.SRERole role) {
        if (player == null || player.level().isClientSide())
            return true;
        Integer before = SKILL_BALANCE_SNAPSHOT.remove(player.getUUID());
        if (before == null)
            return true;
        int diff = SREPlayerShopComponent.KEY.get(player).balance - before;
        if (diff < 0) {
            MoneyFlow.recordExpense(player, MoneyFlow.ExpenseType.SKILL, -diff, null);
        } else if (diff > 0) {
            MoneyFlow.recordIncome(player, MoneyFlow.IncomeType.SKILL, diff);
        }
        return true;
    }
}
