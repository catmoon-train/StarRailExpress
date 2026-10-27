package org.agmas.noellesroles.game.modes.echo.cards;

import java.util.*;

/** 有界幻象台账；没有实体、碰撞、资源或任务身份，时间只接受比赛tick。 */
public final class EchoSceneState {
    public enum Kind {
        DUMMY, SILENT_DUMMY, IMAGE, SUBMISSION, STALL, DISPATCH;
        public boolean decoy() { return this==DUMMY || this==SILENT_DUMMY || this==IMAGE; }
        public boolean submission() { return this==SUBMISSION || this==DISPATCH; }
        /** 假迟滞光：只影响观感，不改变任何信标状态。 */
        public boolean stall() { return this==STALL || this==DISPATCH; }
    }
    public record Point(double x,double y,double z) {
        public Point { if(!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("无效场景位置"); }
        public double distanceSqr(Point p) { return (x-p.x)*(x-p.x)+(y-p.y)*(y-p.y)+(z-p.z)*(z-p.z); }
    }
    public record Spec(Kind kind,Point point,float yaw,int delay,int duration,int soundInterval,int deceptionTicks,double deceptionRange,double soundRange) {
        public Spec(Kind kind,Point point,float yaw,int delay,int duration,int soundInterval) { this(kind,point,yaw,delay,duration,soundInterval,100,20,24); }
        public Spec { Objects.requireNonNull(kind); Objects.requireNonNull(point); if(!Float.isFinite(yaw) || delay<0 || delay>72000 || duration<1 || duration>72000 || soundInterval< -1 || soundInterval>72000 || deceptionTicks<0 || deceptionTicks>72000 || !Double.isFinite(deceptionRange) || deceptionRange<0 || deceptionRange>128 || !Double.isFinite(soundRange) || soundRange<0 || soundRange>128) throw new IllegalArgumentException("无效场景参数"); }
    }
    public record Scene(UUID id,UUID owner,Kind kind,Point point,float yaw,long start,long end,int soundInterval,int deceptionTicks,double deceptionRange,double soundRange) {
        public Scene(UUID id,UUID owner,Kind kind,Point point,float yaw,long start,long end,int soundInterval) { this(id,owner,kind,point,yaw,start,end,soundInterval,100,20,24); }
        public boolean active(long now) { return now>=start && now<end; }
    }
    private final Map<UUID,Scene> scenes=new LinkedHashMap<>();
    private final Map<UUID,Long> sounded=new HashMap<>();
    public List<Scene> all() { return List.copyOf(scenes.values()); }
    public void expire(long now) {
        scenes.values().removeIf(s -> now>=s.end()); sounded.keySet().retainAll(scenes.keySet());
    }
    public boolean canAdd(UUID owner,List<Spec> specs,long now) {
        expire(now);
        if(specs.isEmpty() || specs.size()>2 || scenes.size()+specs.size()>64) return false;
        long extra=specs.stream().filter(s -> s.kind().decoy()).count();
        return scenes.values().stream().filter(s -> s.kind().decoy()).count()+extra<=24
                && scenes.values().stream().filter(s -> s.kind().decoy() && s.owner().equals(owner)).count()+extra<=2;
    }
    /** 两点场景在任何写入前整体检查，失败不留下第一点。 */
    public boolean add(UUID owner,List<Spec> specs,long now) {
        Objects.requireNonNull(owner);
        if(!canAdd(owner,specs,now)) return false;
        for(var spec:specs) {
            var scene=new Scene(UUID.randomUUID(),owner,spec.kind(),spec.point(),spec.yaw(),now+spec.delay(),now+spec.delay()+spec.duration(),spec.soundInterval(),spec.deceptionTicks(),spec.deceptionRange(),spec.soundRange());
            scenes.put(scene.id(),scene);
        }
        return true;
    }
    public List<Scene> dueSounds(long now) {
        expire(now); List<Scene> due=new ArrayList<>();
        for(var s:scenes.values()) if(s.active(now) && s.soundInterval()>=0) {
            long bucket=s.soundInterval()==0?0:(now-s.start())/s.soundInterval();
            if(!Objects.equals(sounded.get(s.id()),bucket)) { due.add(s); sounded.put(s.id(),bucket); }
        }
        return List.copyOf(due);
    }
    public Optional<Scene> deception(Point observer,long now) {
        return scenes.values().stream().filter(s -> s.kind()==Kind.SILENT_DUMMY && s.active(now) && now-s.start()<s.deceptionTicks() && s.point().distanceSqr(observer)<=s.deceptionRange()*s.deceptionRange())
                .min(Comparator.comparingDouble(s -> s.point().distanceSqr(observer)));
    }
    public boolean dismantle(UUID id,long now) {
        Scene scene=scenes.get(id); if(scene==null || !scene.kind().decoy() || !scene.active(now)) return false;
        scenes.remove(id); sounded.remove(id); return true;
    }
    public void depart(UUID owner) { scenes.values().removeIf(s -> s.owner().equals(owner)); sounded.keySet().retainAll(scenes.keySet()); }
    public void clear() { scenes.clear(); sounded.clear(); }
}
