package org.agmas.noellesroles.game.modes.echo.economy;

import io.wifi.starrailexpress.data.PlayerSkinEconomyManager;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 对局内经济：回响币。
 *
 * <p>与旧 repair 包一致，货币直接复用仓库已有的金币钱包（{@link PlayerSkinEconomyManager}），
 * 因此跨对局、跨模式都保留；本模式只负责「在对局里怎么赚、在哪花」。
 *
 * <p>赚取来源全部由服务端事件去重：同一目标只结算一次，避免反复触发刷币。
 * 所有金额为整数，不做小数或按比例分成。
 */
public final class EchoEconomy {
    /** 单局可获得的来源。 */
    public enum Source {
        /** 完成一次修机采集。 */
        REPAIR("repair", 6),
        /** 首次向某座信标提交货物。 */
        SUBMIT("submit", 12),
        /** 首次迟滞某座信标（追捕方）。 */
        STALL("stall", 15),
        /** 首次击倒某名逃脱者。 */
        DOWN("down", 20),
        /** 完成一次救援。 */
        RESCUE("rescue", 18),
        /** 完成一次真实交易。 */
        TRADE("trade", 10),
        /** 个人契约锁定成功。 */
        CONTRACT("contract", 40),
        /** 成功撤离。 */
        ESCAPE("escape", 30);

        private final String id;
        private final int coins;

        Source(String id, int coins) { this.id = id; this.coins = coins; }

        public String id() { return id; }
        public int coins() { return coins; }
    }

    /** 对局内已结算过的去重键：{@code source:owner:target}。 */
    private final Map<String, Boolean> settled = new HashMap<>();

    /**
     * 结算一次收入。
     *
     * @param dedupeKey 去重键（同键只结算一次）；{@code null} 表示不去重
     * @return 实际发放的金额（0 表示已结算过或玩家不在线）
     */
    public int award(ServerPlayer player, Source source, String dedupeKey) {
        if (player == null || source == null) return 0;
        String key = dedupeKey == null ? null : source.id() + ":" + player.getUUID() + ":" + dedupeKey;
        if (key != null && settled.putIfAbsent(key, Boolean.TRUE) != null) return 0;
        int amount = source.coins();
        PlayerSkinEconomyManager.addCoinNum(player, amount);
        return amount;
    }

    /** 只查询余额，不写。 */
    public static int balance(ServerPlayer player) {
        return player == null ? 0 : PlayerSkinEconomyManager.getCoinNum(player);
    }

    /** 扣款；余额不足返回 {@code false} 且不改余额。 */
    public static boolean spend(ServerPlayer player, int amount) {
        if (player == null || amount <= 0) return false;
        if (PlayerSkinEconomyManager.getCoinNum(player) < amount) return false;
        PlayerSkinEconomyManager.addCoinNum(player, -amount);
        return true;
    }

    public boolean settled(UUID owner, Source source, String target) {
        return settled.containsKey(source.id() + ":" + owner + ":" + target);
    }

    public void clear() { settled.clear(); }
}
