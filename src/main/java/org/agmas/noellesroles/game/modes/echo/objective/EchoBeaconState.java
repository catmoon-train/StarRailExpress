package org.agmas.noellesroles.game.modes.echo.objective;

import org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig;

/**
 * 单座信标的状态机：投料 → 自动充能 → 已启动。
 *
 * <p>污染（回退进度、封锁、稳定层、污染冷却）已整体下线，见
 * {@code docs/echo-freight/22-pollution-future.md}。截流者现在只能通过
 * {@link #stall(long, int)} 临时迟滞充能，不扣任何已积累进度。</p>
 *
 * <p>充能用百万分之 tick 定点累积，任意受支持的充能时长都不逐 tick 截断。</p>
 */
public final class EchoBeaconState {
    public static final int REQUIRED_CRYSTALS=3, FULL_WORK=6000;
    private static final long SCALE=1_000_000L;
    private final EchoRuleConfig.Beacon rules;
    private final long full;
    private int delivered;
    private long work,stalledUntil;
    public EchoBeaconState() { this(EchoRuleConfig.DEFAULT.beacon()); }
    public EchoBeaconState(EchoRuleConfig.Beacon rules) { this.rules=java.util.Objects.requireNonNull(rules); full=rules.chargeTicks()*SCALE; if(full<=0) throw new IllegalArgumentException("充能时长必须大于0"); }
    public int delivered() { return delivered; }
    public int requiredCrystals() { return rules.requiredCrystals(); }
    public int progressPermille() { return (int)(work*1000/full); }
    public boolean active() { return work>=full; }
    /** 迟滞截止；截止前充能不推进，已积累进度不受影响。 */
    public long stalledUntil() { return stalledUntil; }
    public boolean stalled(long now) { return now<stalledUntil; }
    /** 还能继续投料：未满料且未启动。 */
    public boolean canSubmit() { return !active() && delivered<rules.requiredCrystals(); }
    /** 投一枚晶体；已满料或已启动时返回 false 且不改状态。 */
    public boolean submit() { if(!canSubmit()) return false; delivered++; return true; }
    public boolean tick(long now,int escorts) {
        if(escorts<0) throw new IllegalArgumentException("护送人数不能为负");
        if(canSubmit() || active() || now<stalledUntil) return false;
        work=Math.min(full,work+SCALE+Math.round(rules.escortBonus()*SCALE)*Math.min(rules.maxEscorts(),escorts));
        return active();
    }
    /** 加速卡：直接推进充能千分比，到满即启动；未满料或已启动时无效。 */
    public boolean boost(long now,int permille) {
        if(permille<=0 || canSubmit() || active()) return false;
        work=Math.min(full,work+full*permille/1000); return true;
    }
    /** 截流者迟滞充能：只在充能中可用，不改变任何已积累进度。 */
    public boolean canStall(long now) { return !canSubmit() && !active() && now>=stalledUntil; }
    public boolean stall(long now,int ticks) {
        if(ticks<=0 || !canStall(now)) return false;
        stalledUntil=now+ticks; return true;
    }
}
