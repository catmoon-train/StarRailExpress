package org.agmas.noellesroles.game.modes.echo.classes;

import java.util.Objects;

/**
 * 单个玩家的职业运行时：主动技能冷却、职业被动计时与临时增益。
 *
 * <p>纯状态机：不读世界、不发包，全部时间都是「比赛 tick」，暂停时调用方不推进即可冻结。
 * 所有失败路径都必须返回 {@code false} 且不写任何状态，保证「失败不进冷却」。
 */
public final class EchoClassState {
    /** 追捕 · 战斗：三连斩的衔接窗口（0.9 秒）。 */
    public static final int COMBO_WINDOW_TICKS = 18;
    /** 追捕 · 战斗：第二、三段额外判定距离。 */
    public static final double COMBO_REACH_2 = 0.3D, COMBO_REACH_3 = 1.0D;
    /** 追捕 · 战斗：第二、三段额外倒地时长。 */
    public static final int COMBO_BLEED_2 = 40, COMBO_BLEED_3 = 120;

    /** 追捕 · 牵制：封锁钉半径、持续与减速。 */
    public static final double PIN_RADIUS = 3.0D;
    public static final int PIN_TICKS = 160;
    public static final double PIN_SLOW = 0.45D;

    /** 追捕 · 循环：击倒后的追击增益时长与倍率。 */
    public static final int CHASE_TICKS = 120;
    public static final double CHASE_SPEED = 1.12D;

    /** 逃脱 · 战斗：扛伤后获得的短暂加速。 */
    public static final int ENDURE_SPEED_TICKS = 100;
    public static final double ENDURE_SPEED = 1.15D;

    /** 逃脱 · 牵制：干扰装置的半径、持续与对追捕者的减速。 */
    public static final double JAM_RADIUS = 5.0D;
    public static final int JAM_TICKS = 200;
    public static final double JAM_SLOW = 0.35D;

    /** 逃脱 · 循环：修机小游戏判定区加宽与失控概率降低。 */
    public static final int HAULER_ZONE_BONUS = 8;
    public static final double HAULER_ACCIDENT_SCALE = 0.5D;

    /** 六个职业的主动技能冷却（tick）。 */
    public static final int COOLDOWN_RAVAGER = 700;
    public static final int COOLDOWN_WARDEN = 600;
    public static final int COOLDOWN_STALKER = 500;
    public static final int COOLDOWN_VANGUARD = 600;
    public static final int COOLDOWN_TRAPPER = 700;
    public static final int COOLDOWN_HAULER = 400;

    private EchoClass clazz;
    private long skillReadyAt;
    private long skillBuffUntil;
    private int comboStage;
    private long comboUntil;
    private long chaseUntil;
    private long endureUntil;
    private long jammedUntil;
    private long pinnedUntil;

    public EchoClassState() { this(EchoClass.HAULER); }

    public EchoClassState(EchoClass clazz) { this.clazz = Objects.requireNonNull(clazz); }

    public EchoClass clazz() { return clazz; }

    /** 换职业：清掉所有属于上一个职业的运行时，避免状态泄漏。 */
    public void setClass(EchoClass next) {
        if (next == null || next == clazz) return;
        clazz = next;
        clear();
    }

    public long skillReadyAt() { return skillReadyAt; }
    public boolean skillReady(long now) { return now >= skillReadyAt; }

    /**
     * 释放主动技能：只做冷却与窗口记账。
     *
     * @return 冷却中返回 {@code false} 且不改任何状态
     */
    public boolean activate(long now) {
        if (!skillReady(now)) return false;
        skillReadyAt = now + cooldownTicks();
        switch (clazz) {
            case RAVAGER -> { comboStage = 3; comboUntil = now + COMBO_WINDOW_TICKS * 2; }
            case WARDEN -> pinnedUntil = now + PIN_TICKS;
            case STALKER -> chaseUntil = now + CHASE_TICKS;
            case VANGUARD -> endureUntil = now + ENDURE_SPEED_TICKS;
            case TRAPPER -> jammedUntil = now + JAM_TICKS;
            case HAULER -> skillBuffUntil = now + 200;
        }
        return true;
    }

    public int cooldownTicks() {
        return switch (clazz) {
            case RAVAGER -> COOLDOWN_RAVAGER;
            case WARDEN -> COOLDOWN_WARDEN;
            case STALKER -> COOLDOWN_STALKER;
            case VANGUARD -> COOLDOWN_VANGUARD;
            case TRAPPER -> COOLDOWN_TRAPPER;
            case HAULER -> COOLDOWN_HAULER;
        };
    }

    public long skillBuffUntil() { return skillBuffUntil; }
    public boolean skillBuff(long now) { return now < skillBuffUntil; }

    // ---- 追捕 · 战斗：三连斩 ----

    /**
     * 推进连段并返回本次挥击的段数（1..3）。
     * 窗口过期会先回退到第一段，因此不会读到过期状态。
     */
    public int swing(long now) {
        if (now >= comboUntil) comboStage = 0;
        comboStage = Math.min(3, comboStage + 1);
        comboUntil = now + COMBO_WINDOW_TICKS;
        return comboStage;
    }

    public int comboStage(long now) { return now >= comboUntil ? 0 : comboStage; }

    /** 挥空立刻断连，不给下一段加成。 */
    public void miss() { comboStage = 0; comboUntil = 0; }

    /** 终结技打完后回到第一段，不能连续吃终结技。 */
    public void settleCombo(long now) { if (comboStage >= 3) { comboStage = 0; comboUntil = 0; } }

    public static double comboReach(int stage) {
        return stage >= 3 ? COMBO_REACH_3 : stage == 2 ? COMBO_REACH_2 : 0D;
    }

    public static int comboBleed(int stage) {
        return stage >= 3 ? COMBO_BLEED_3 : stage == 2 ? COMBO_BLEED_2 : 0;
    }

    public static int comboRecover(int stage) {
        return stage >= 3 ? 14 : stage == 2 ? 8 : 6;
    }

    // ---- 追捕 · 循环：击倒后的追击增益 ----

    public void onKnockdown(long now) { if (clazz == EchoClass.STALKER) chaseUntil = now + CHASE_TICKS; }
    public boolean chasing(long now) { return now < chaseUntil; }
    public double speedFactor(long now) {
        if (clazz == EchoClass.STALKER && chasing(now)) return CHASE_SPEED;
        if (clazz == EchoClass.VANGUARD && enduring(now)) return ENDURE_SPEED;
        return 1D;
    }

    // ---- 逃脱 · 战斗：扛伤 ----

    public boolean enduring(long now) { return now < endureUntil; }

    // ---- 逃脱 · 牵制：干扰场 ----

    public boolean jamActive(long now) { return now < jammedUntil; }

    // ---- 追捕 · 牵制：封锁钉 ----

    public boolean pinActive(long now) { return now < pinnedUntil; }

    /** 该玩家是否被封锁钉减速；只对逃脱方生效，追捕者不吃自己的钉子。 */
    public boolean slowedByPin(long now) { return pinActive(now); }

    // ---- 逃脱 · 循环：修机小游戏修正 ----

    /** 判定区额外宽度（像素，按圆环直径 0..1000 归一前的逻辑像素）。 */
    public int zoneBonus() { return clazz == EchoClass.HAULER ? HAULER_ZONE_BONUS : 0; }

    /** 失控概率倍率：搬运工把失控压到一半。 */
    public double accidentScale() { return clazz == EchoClass.HAULER ? HAULER_ACCIDENT_SCALE : 1D; }

    public void expire(long now) {
        if (now >= comboUntil) comboStage = 0;
    }

    public void clear() {
        skillReadyAt = 0; skillBuffUntil = 0;
        comboStage = 0; comboUntil = 0;
        chaseUntil = 0; endureUntil = 0; jammedUntil = 0; pinnedUntil = 0;
    }

    public record Snapshot(EchoClass clazz, long skillReadyAt, long skillBuffUntil, int comboStage, long comboUntil,
                           long chaseUntil, long endureUntil, long jammedUntil, long pinnedUntil) { }

    public Snapshot snapshot() {
        return new Snapshot(clazz, skillReadyAt, skillBuffUntil, comboStage, comboUntil,
                chaseUntil, endureUntil, jammedUntil, pinnedUntil);
    }

    public void restore(Snapshot snapshot) {
        if (snapshot == null) return;
        clazz = snapshot.clazz();
        skillReadyAt = snapshot.skillReadyAt(); skillBuffUntil = snapshot.skillBuffUntil();
        comboStage = snapshot.comboStage(); comboUntil = snapshot.comboUntil();
        chaseUntil = snapshot.chaseUntil(); endureUntil = snapshot.endureUntil();
        jammedUntil = snapshot.jammedUntil(); pinnedUntil = snapshot.pinnedUntil();
    }
}
