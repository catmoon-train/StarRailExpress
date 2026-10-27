package org.agmas.noellesroles.game.modes.echo.cards;

import java.util.*;

/** 只接受比赛tick的个人效果台账；取消动作不消费标记，升级不叠强度。 */
public final class EchoEffectState {
    /** 污染标记已随污染机制下线，见 docs/echo-freight/22-pollution-future.md。 */
    public enum Marker { SILENCE, TRADE }
    public record Mark(String source,long until,int charges,int channelTicks,boolean strong,double range) {
        public Mark { if(until<0 || charges<1) throw new IllegalArgumentException("无效标记"); }
    }
    public record Boost(String source,int rank,long until,double amount) { }
    private final EnumMap<Marker,Mark> marks=new EnumMap<>(Marker.class);
    private final Map<String,Long> cooldowns=new HashMap<>();
    public record Mask(String source,int rank,long until,int keyPulses) { }
    private Mask mask;
    private Boost mobility,slow;
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
        double bonus=mobility(now).map(Boost::amount).orElse(0D),penalty=slow(now).map(Boost::amount).orElse(0D);
        double factor=(1-cargoPenalty+bonus-penalty)*(opening?openingSpeed:1);
        return Math.max(0.5,Math.min(1.5,factor))-1;
    }
    public boolean ready(String group,long now) { return now>=cooldowns.getOrDefault(group,0L); }
    public void cooldown(String group,long until) { cooldowns.put(group,until); }
    public void expire(long now) {
        for(Marker kind:Marker.values()) mark(kind,now);
        mobility(now); slow(now); mask(now); cooldowns.values().removeIf(until -> now>=until);
    }
    public void clear() { marks.clear(); cooldowns.clear(); mobility=null; slow=null; mask=null; }
}
