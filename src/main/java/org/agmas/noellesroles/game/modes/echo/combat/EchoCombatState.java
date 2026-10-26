package org.agmas.noellesroles.game.modes.echo.combat;

import org.agmas.noellesroles.game.modes.echo.state.EchoLifeState;

/** 暂停感知的三心血量、倒地救援、蓄力和控制状态；原版生命值只是投影。 */
public final class EchoCombatState {
    public enum Hit { IGNORED, DAMAGED, DOWNED, ELIMINATED, RESET_INTERCEPTOR }
    public static final float MAX_HEALTH=6F, CUTTER_DAMAGE=3F;
    /** 挥空至少3秒；实际武器命中至少6秒，不受专长和演出后摇缩短。 */
    public static final int MIN_ATTACK_COOLDOWN_TICKS=60, HIT_COOLDOWN_TICKS=120;
    public record Snapshot(EchoLifeState life,long bleedUntil,long protectedUntil,long controlledUntil,
                           long controlImmuneUntil,long cutterReadyAt,long shoveReadyAt,float health) {
        /** 兼容旧投影；新协议显式携带剩余半心单位。 */
        public Snapshot(EchoLifeState life,long bleedUntil,long protectedUntil,long controlledUntil,
                        long controlImmuneUntil,long cutterReadyAt,long shoveReadyAt) {
            this(life,bleedUntil,protectedUntil,controlledUntil,controlImmuneUntil,cutterReadyAt,shoveReadyAt,
                    life==EchoLifeState.HEALTHY?MAX_HEALTH:life==EchoLifeState.WOUNDED?CUTTER_DAMAGE:0F);
        }
    }
    private final org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig.Combat rules;
    public EchoCombatState() { this(org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig.DEFAULT.combat()); }
    public EchoCombatState(org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig.Combat rules) { this.rules=java.util.Objects.requireNonNull(rules); }
    private EchoLifeState life=EchoLifeState.HEALTHY;
    private float health=MAX_HEALTH;
    private long bleedUntil,protectedUntil,controlledUntil,controlImmuneUntil,cutterReadyAt,shoveReadyAt,environmentReadyAt;
    private long chargeStartedAt=-1;
    public EchoLifeState life() { return life; }
    public float health() { return health; }
    public boolean controlled(long now) { return now<controlledUntil; }
    /** 治疗：不超过上限，倒地与淘汰状态不能回复。 */
    public boolean heal(float amount) {
        if(!life.standing() || !Float.isFinite(amount) || amount<=0) return false;
        float before=health;
        health=Math.min(MAX_HEALTH,health+amount);
        return health>before;
    }
    public boolean canAct(long now) { return life.standing() && !controlled(now); }
    public boolean protectedAt(long now) { return now<protectedUntil; }
    public long bleedUntil() { return bleedUntil; }
    public Snapshot snapshot() { return new Snapshot(life,bleedUntil,protectedUntil,controlledUntil,controlImmuneUntil,cutterReadyAt,shoveReadyAt,health); }

    public boolean beginCutter(long now) {
        if(!canAct(now) || now<cutterReadyAt) return false;
        chargeStartedAt=now; return true;
    }
    /** 蓄满后松手提交，蓄力取消不消耗冷却。 */
    public boolean releaseCutter(long now) { return releaseCutter(now,EchoCutterStrike.CHARGE_TICKS); }
    public boolean releaseCutter(long now,int requiredChargeTicks) {
        return releaseCutter(now,requiredChargeTicks,now);
    }
    public boolean releaseCutter(long now,int requiredChargeTicks,long releasedAt) {
        long start=chargeStartedAt; chargeStartedAt=-1;
        if(releasedAt>now || (requiredChargeTicks>0 && (start<0 || releasedAt-start<requiredChargeTicks))
                || !canAct(now) || now<cutterReadyAt) return false;
        cutterReadyAt=Math.max(cutterReadyAt,now+Math.max(MIN_ATTACK_COOLDOWN_TICKS,rules.cutterCooldownTicks()));
        activeAction(); return true;
    }
    public int cutterChargeTicks() { return EchoCutterStrike.CHARGE_TICKS; }
    public long cutterRemaining(long now) { return Math.max(0,cutterReadyAt-now); }
    /** 实际受伤/倒地/淘汰均算命中；多目标只刷新截止，不累加惩罚。 */
    public boolean recordCutterHit(long now,Hit hit) {
        if(!EchoCutterStrike.feedbackEligible(hit,false,false)) return false;
        cutterReadyAt=Math.max(cutterReadyAt,now+HIT_COOLDOWN_TICKS); return true;
    }
    public void cancelCharge() { chargeStartedAt=-1; }
    public boolean shoveReady(long now) { return canAct(now) && now>=shoveReadyAt; }
    public void consumeShove(long now) { shoveReadyAt=now+rules.shoveCooldownTicks(); activeAction(); }
    public boolean control(long now,int duration) {
        if(!life.standing() || duration<=0 || now<controlImmuneUntil || controlled(now)) return false;
        controlledUntil=now+duration; controlImmuneUntil=controlledUntil+rules.controlImmunityTicks(); cancelCharge(); return true;
    }
    /** 普通杀手攻击固定扣一心半，不受扑击/连段增幅而变为秒杀。 */
    public Hit hit(long now,boolean force,boolean interceptor) { return damage(now,force,interceptor,CUTTER_DAMAGE); }
    public Hit damage(long now,boolean force,boolean interceptor,float amount) {
        if(!life.potentiallyEscapable()) return Hit.IGNORED;
        if(force) { eliminate(); return Hit.ELIMINATED; }
        if(!Float.isFinite(amount) || amount<=0) return Hit.IGNORED;
        if(interceptor) {
            if(now<environmentReadyAt) return Hit.IGNORED;
            environmentReadyAt=now+rules.environmentProtectionTicks(); controlledUntil=now+rules.environmentControlTicks(); controlImmuneUntil=controlledUntil+rules.controlImmunityTicks();
            health=MAX_HEALTH; cancelCharge(); return Hit.RESET_INTERCEPTOR;
        }
        if(life==EchoLifeState.DOWNED || protectedAt(now)) return Hit.IGNORED;
        cancelCharge(); health=Math.max(0F,health-amount);
        if(health>0F) return Hit.DAMAGED;
        if(life==EchoLifeState.HEALTHY) { life=EchoLifeState.DOWNED; bleedUntil=now+rules.bleedTicks(); return Hit.DOWNED; }
        eliminate(); return Hit.ELIMINATED;
    }
    public boolean expireBleed(long now) {
        if(life!=EchoLifeState.DOWNED || now<bleedUntil) return false;
        eliminate(); return true;
    }
    public boolean canBeRescued(long now) { return life==EchoLifeState.DOWNED && now<bleedUntil; }
    /** 扑击命中延长倒地时间；只对正在流血的倒地者生效。 */
    public boolean extendBleed(long now,int extraTicks) {
        if(life!=EchoLifeState.DOWNED || extraTicks<=0 || now>=bleedUntil) return false;
        bleedUntil+=extraTicks; return true;
    }
    public boolean rescue(long now) {
        if(!canBeRescued(now)) return false;
        life=EchoLifeState.WOUNDED; health=CUTTER_DAMAGE; bleedUntil=0; protectedUntil=now+rules.rescueProtectionTicks(); return true;
    }
    public void activeAction() { protectedUntil=0; }
    public void depart(EchoLifeState terminal) {
        if(terminal!=EchoLifeState.ESCAPED && terminal!=EchoLifeState.RETIRED && terminal!=EchoLifeState.ELIMINATED) throw new IllegalArgumentException("非离场状态");
        life=terminal; health=0; bleedUntil=0; protectedUntil=0; controlledUntil=0; cancelCharge();
    }
    private void eliminate() { depart(EchoLifeState.ELIMINATED); }
}
