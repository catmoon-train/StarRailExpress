package org.agmas.noellesroles.game.modes.echo.repair;

import java.util.Random;

/**
 * 修机小游戏（圆环指针）的权威状态机。
 *
 * <p>流程：玩家对晶簇开始引导 → 服务端生成一轮判定（指针速度、判定区起点与宽度、是否失控），
 * 客户端只负责把指针画出来；按下按钮时把「指针当前角度」交给服务端，由服务端按自己的
 * tick 重算角度并判定，客户端上报的角度只用来识别明显作弊（偏差过大直接判失败）。
 *
 * <p>参考 {@code docs/repair-mode-reference.zip} 的校准条做法，但改为圆环：
 * 指针沿圆周匀速旋转，判定区是一段圆弧；失控（accident）时转速提高、判定区变窄，
 * 玩家必须在更短的反应窗口内停住。
 *
 * <p>纯状态机：不读世界、不发包，全部时间是比赛 tick，暂停时调用方不推进即可冻结。
 */
public final class EchoRepairCheck {
    /** 圆环一圈对应的整数刻度，避免浮点角度在网络与本地之间漂移。 */
    public static final int FULL_CIRCLE = 1000;
    /** 正常转速：每 tick 前进的刻度（0.36 圈/秒，约 2.8 秒转一圈）。 */
    public static final int BASE_SPEED = 18;
    /** 失控时的转速倍率。 */
    public static final double ACCIDENT_SPEED = 2.1D;
    /** 正常判定区宽度（刻度）。 */
    public static final int BASE_ZONE = 120;
    /** 失控判定区宽度（刻度）。 */
    public static final int ACCIDENT_ZONE = 70;
    /** 基础失控概率。 */
    public static final double BASE_ACCIDENT = 0.12D;
    /** 客户端上报角度与服务端重算角度的最大允许偏差（刻度）；超过视为不同步/作弊。 */
    public static final int TOLERANCE = 60;
    /** 一轮判定的最长存活 tick，超时自动失败，避免玩家挂着界面不按。 */
    public static final int TIMEOUT_TICKS = 400;

    /** 判定结果。 */
    public enum Result {
        /** 指针停在判定区内。 */
        HIT,
        /** 指针停在判定区外。 */
        MISS,
        /** 已过期或被取消。 */
        EXPIRED,
        /** 客户端上报与服务端不一致。 */
        DESYNC
    }

    private final int speed;
    private final int zoneStart;
    private final int zoneWidth;
    private final boolean accident;
    private final long startedAt;
    private int position;

    private EchoRepairCheck(int speed, int zoneStart, int zoneWidth, boolean accident, long startedAt) {
        this.speed = speed;
        this.zoneStart = zoneStart;
        this.zoneWidth = zoneWidth;
        this.accident = accident;
        this.startedAt = startedAt;
    }

    /**
     * 开一轮判定。
     *
     * @param zoneBonus       判定区额外宽度（职业加成）
     * @param accidentScale   失控概率倍率（职业减免，1 表示不减免）
     */
    public static EchoRepairCheck begin(Random random, long now, int zoneBonus, double accidentScale) {
        if (random == null) throw new IllegalArgumentException("缺少随机源");
        double chance = Math.max(0D, Math.min(1D, BASE_ACCIDENT * Math.max(0D, accidentScale)));
        boolean accident = random.nextDouble() < chance;
        int width = Math.max(20, (accident ? ACCIDENT_ZONE : BASE_ZONE) + Math.max(0, zoneBonus));
        int start = random.nextInt(FULL_CIRCLE);
        int speed = accident ? (int) Math.round(BASE_SPEED * ACCIDENT_SPEED) : BASE_SPEED;
        return new EchoRepairCheck(speed, start, width, accident, now);
    }

    public int speed() { return speed; }
    public int zoneStart() { return zoneStart; }
    public int zoneWidth() { return zoneWidth; }
    public boolean accident() { return accident; }
    public long startedAt() { return startedAt; }
    public int position() { return position; }

    /** 推进一 tick；返回指针是否已绕圈（仅作表现参考，不改变判定）。 */
    public boolean advance() {
        position = (position + speed) % FULL_CIRCLE;
        return position < speed;
    }

    /** 服务端按自己推进的 tick 数重算指针位置，不信任客户端上报值。 */
    public int positionAt(long now) {
        long ticks = Math.max(0, now - startedAt);
        return (int) ((ticks * speed) % FULL_CIRCLE);
    }

    public boolean expired(long now) { return now - startedAt > TIMEOUT_TICKS; }

    /** 判定区是否覆盖某个刻度（含首尾）。 */
    public boolean inZone(int degree) {
        int normalized = Math.floorMod(degree, FULL_CIRCLE);
        int offset = Math.floorMod(normalized - zoneStart, FULL_CIRCLE);
        return offset <= zoneWidth;
    }

    /**
     * 结算一次按下。
     *
     * @param now              当前比赛 tick
     * @param reportedPosition 客户端上报的指针位置
     */
    public Result submit(long now, int reportedPosition) {
        if (expired(now)) return Result.EXPIRED;
        int authoritative = positionAt(now);
        int delta = Math.abs(Math.floorMod(reportedPosition - authoritative + FULL_CIRCLE / 2, FULL_CIRCLE)
                - FULL_CIRCLE / 2);
        if (delta > TOLERANCE) return Result.DESYNC;
        return inZone(authoritative) ? Result.HIT : Result.MISS;
    }

    /** 供客户端渲染的快照；不包含判定区之外的隐藏信息。 */
    public record View(int speed, int zoneStart, int zoneWidth, boolean accident, long startedAt, int timeoutTicks) { }

    public View view() { return new View(speed, zoneStart, zoneWidth, accident, startedAt, TIMEOUT_TICKS); }
}
