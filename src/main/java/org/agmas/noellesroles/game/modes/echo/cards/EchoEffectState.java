package org.agmas.noellesroles.game.modes.echo.cards;

import java.util.*;

/** 只接受比赛tick的个人效果台账；取消动作不消费标记，升级不叠强度。 */
public final class EchoEffectState {
    /** 污染标记已随污染机制下线，见 docs/echo-freight/22-pollution-future.md。 */
    /** HOLD：假动作/临时禁令，按通道类型消费次数，不改任务台账。 */
    public enum Marker { SILENCE, TRADE, HOLD }
    public record Mark(String source,long until,int charges,int channelTicks,boolean strong,double range) {
        public Mark { if(until<0 || charges<1) throw new IllegalArgumentException("无效标记"); }
    }
    public record Boost(String source,int rank,long until,double amount) { }
    private final EnumMap<Marker,Mark> marks=new EnumMap<>(Marker.class);
    private final Map<String,Long> cooldowns=new HashMap<>();
    public record Mask(String source,int rank,long until,int keyPulses) { }
    private Mask mask;
    private Boost mobility,slow;
    // 定身：不进入原版控制链，只在服务端锁定锚点与移速，避免与倒地共用控制状态。
    private long heldUntil,holdImmuneUntil;
    // 护甲：一次伤害完全免疫，成功免疫后清零。
    private int armor;
    /** 定身截止；被定身期间可行动但位移被锁。 */
    public boolean held(long now) { return now<heldUntil; }
    public long heldUntil() { return heldUntil; }
    /**
     * 施加定身；已有定身、免疫窗口或时长为零时拒绝，不刷新旧截止。
     * @return 是否真正施加
     */
    public boolean hold(long now,int duration,int immunity) {
        if(duration<=0 || now<holdImmuneUntil || held(now)) return false;
        heldUntil=now+duration; holdImmuneUntil=heldUntil+Math.max(0,immunity); return true;
    }
    public void clearHold() { heldUntil=0; holdImmuneUntil=0; }
    /** 施加护甲；已有护甲时拒绝，不叠层。 */
    public boolean armor(long now,int charges) { if(charges<=0 || armor>0) return false; armor=charges; return true; }
    public int armor() { return armor; }
    /** 消耗一层护甲并返回是否免疫本次伤害。 */
    public boolean consumeArmor() { if(armor<1) return false; armor--; return true; }
    public void clearArmor() { armor=0; }
    /** 为下一次指定通道设置临时禁令；同通道已有禁令时拒绝。 */
    public boolean holdChannel(String channel,int ticks,long now) { return add(Marker.HOLD,new Mark(channel,now+ticks,1,ticks,false,0),now); }
    public boolean channelHeld(String channel,long now) { return mark(Marker.HOLD,now).filter(m -> m.source().equals(channel)).isPresent(); }
    public boolean consumeChannel(String channel,long now) { return mark(Marker.HOLD,now).filter(m -> m.source().equals(channel)).isPresent() && consume(Marker.HOLD,now); }
    public Optional<Mask> mask(long now) { if(mask!=null && now>=mask.until()) mask=null; return Optional.ofNullable(mask); }
    public boolean disguise(Mask next,long now) {
        if(next.until()<=now || mask(now).map(m -> m.rank()>=next.rank()).orElse(false)) return false;
        mask=next; return true;
    }
    public boolean suppressKeyPulse(long now) {
        var current=mask(now).orElse(null); if(current==null || current.keyPulses()<1) return false;
        mask=new Mask(current.source(),current.rank(),current.until(),current.keyPulses()-1); return true;
    }
    public void breakMask() { mask=null; }
    public Optional<Mark> mark(Marker kind,long now) {
        Mark mark=marks.get(kind);
        if(mark!=null && now>=mark.until()) { marks.remove(kind); mark=null; }
        return Optional.ofNullable(mark);
    }
    public boolean canMark(Marker kind,long now) { return mark(kind,now).isEmpty(); }
    public boolean add(Marker kind,Mark mark,long now) {
        if(mark.until()<=now || !canMark(kind,now)) return false;
        marks.put(kind,mark); return true;
    }
    public boolean consume(Marker kind,long now) {
        Mark mark=mark(kind,now).orElse(null); if(mark==null) return false;
        if(mark.charges()==1) marks.remove(kind);
        else marks.put(kind,new Mark(mark.source(),mark.until(),mark.charges()-1,mark.channelTicks(),mark.strong(),mark.range()));
        return true;
    }
    public Optional<Boost> mobility(long now) { if(mobility!=null && now>=mobility.until()) mobility=null; return Optional.ofNullable(mobility); }
    public Optional<Boost> slow(long now) { if(slow!=null && now>=slow.until()) slow=null; return Optional.ofNullable(slow); }
    public boolean canBoost(int rank,long now) { return mobility(now).map(b -> rank>b.rank()).orElse(true); }
    public boolean boost(Boost next,long now) {
        if(next.until()<=now || next.amount()<=0 || !canBoost(next.rank(),now)) return false;
        mobility=next; return true;
    }
    public boolean slow(Boost next,long now) {
        if(next.until()<=now || slow(now).isPresent()) return false;
        slow=next; return true;
    }
    public double speedDelta(long now,double cargoPenalty) { return speedDelta(now,cargoPenalty,false); }
    public double speedDelta(long now,double cargoPenalty,boolean opening) {
        return speedDelta(now,cargoPenalty,opening,0.5);
    }
    public double speedDelta(long now,double cargoPenalty,boolean opening,double openingSpeed) {
        // 定身不是减速而是锁位移：这里保持基线，避免与倒地共用 -1 速度修正。
        if(held(now)) return 0D;
        double bonus=mobility(now).map(Boost::amount).orElse(0D),penalty=slow(now).map(Boost::amount).orElse(0D);
        double factor=(1-cargoPenalty+bonus-penalty)*(opening?openingSpeed:1);
        return Math.max(0.5,Math.min(1.5,factor))-1;
    }
    public boolean ready(String group,long now) { return now>=cooldowns.getOrDefault(group,0L); }
    public void cooldown(String group,long until) { cooldowns.put(group,until); }
    public void expire(long now) {
        for(Marker kind:Marker.values()) mark(kind,now);
        mobility(now); slow(now); mask(now); cooldowns.values().removeIf(until -> now>=until);
        if(now>=heldUntil) heldUntil=0;
    }
    public void clear() { marks.clear(); cooldowns.clear(); mobility=null; slow=null; mask=null; heldUntil=0; holdImmuneUntil=0; armor=0; }
}
