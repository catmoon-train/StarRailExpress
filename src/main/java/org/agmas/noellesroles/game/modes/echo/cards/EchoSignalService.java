package org.agmas.noellesroles.game.modes.echo.cards;

import io.wifi.starrailexpress.api.data.RoleData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.modes.echo.objective.EchoDropService;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoMatch;
import org.agmas.noellesroles.game.modes.echo.state.*;
import java.util.*;

/** 授权集合在使用时冻结；每tick撤销失效目标，只投影给指定的活人接收者。 */
public final class EchoSignalService {
    private record Lease(UUID owner,UUID target,String kind,long end,int interval,int visible,long start) { }
    private record GroundLease(UUID owner,String handle,long start,long end,double range) { }
    private final EchoMatch match;
    private final ServerLevel level;
    private final List<Lease> leases=new ArrayList<>();
    private final List<GroundLease> ground=new ArrayList<>();
    private final Map<Lease,Integer> cachedSectors=new HashMap<>();
    private final Map<GroundLease,Integer> cachedGround=new HashMap<>();
    public EchoSignalService(EchoMatch match,ServerLevel level) { this.match=match; this.level=level; }
    private boolean alive(ServerPlayer p) { return p!=null && match.present(p) && match.life(p.getUUID()).standing(); }
    private ServerPlayer player(UUID id) { return match.connectedPlayer(id); }
    public List<ServerPlayer> wolves(ServerPlayer observer,double range) {
        return match.onlinePlayers().stream().filter(p -> alive(p) && match.faction(p.getUUID())==EchoFaction.INTERCEPTOR && p.distanceToSqr(observer)<=range*range).toList();
    }
    public List<ServerPlayer> carriers(ServerPlayer observer,int limit) {
        return match.onlinePlayers().stream().filter(p -> alive(p) && !p.getUUID().equals(observer.getUUID()))
                .filter(p -> RoleData.getOptional(EchoRoleData.class,p).map(d -> d.cargo().stream().anyMatch(match.objectives()::ordinaryCargo)).orElse(false))
                .sorted(Comparator.comparingDouble(observer::distanceToSqr)).limit(limit).toList();
    }
    public boolean canReplace(UUID owner,String kind,int ticks) {
        return leases.stream().filter(l -> l.owner().equals(owner) && l.kind().equals(kind) && match.matchTick()<l.end()).allMatch(l -> ticks>l.end()-l.start());
    }
    public void replace(UUID owner,String kind) { leases.removeIf(l -> l.owner().equals(owner) && l.kind().equals(kind)); }
    public boolean marked(UUID target) { return leases.stream().anyMatch(l -> l.target().equals(target) && l.kind().equals("hunt") && match.matchTick()<l.end()); }
    public void outline(ServerPlayer receiver,ServerPlayer target,String kind,int ticks) { lease(receiver,target,kind,ticks,ticks,ticks); }
    public void lease(ServerPlayer receiver,ServerPlayer target,String kind,int ticks,int interval,int visible) {
        long now=match.matchTick();
        leases.add(new Lease(receiver.getUUID(),target.getUUID(),kind,now+ticks,interval,visible,now));
    }
    public void pulse(ServerPlayer receiver,ServerPlayer source,String kind) {
        if(!kind.equals("cargo_pulse") && !kind.equals("key_pulse") && !kind.equals("fault_pulse"))
            throw new IllegalArgumentException("无效周期信号");
        leases.removeIf(l -> l.owner().equals(receiver.getUUID()) && l.target().equals(source.getUUID()) && l.kind().equals(kind));
        lease(receiver,source,kind,20,20,20);
        var lease=leases.getLast(); cachedSectors.put(lease,sector(receiver.getX(),receiver.getZ(),source.getX(),source.getZ()));
    }
    public boolean salvage(ServerPlayer actor,EchoCardDefinition card) {
        long now=match.matchTick(); int duration=EchoCardEffects.ticks(card,"durationTicks");
        if(ground.stream().anyMatch(l -> l.owner().equals(actor.getUUID()) && now<l.end() && duration<=l.end()-l.start())) return false;
        boolean magnet=card.shortId().equals("salvage_magnet"); var drops=match.objectives().drops();
        double range=EchoCardEffects.param(card,"range"),pickupRange=EchoCardEffects.param(card,"pickupRange");
        Map<String,Vec3> candidates=new LinkedHashMap<>();
        match.objectives().blackTargets(actor).forEach((id,pos) -> { if(actor.distanceToSqr(pos)<=range*range) candidates.put("black:"+id,pos); });
        for(var d:drops.all()) if(d.kind()==EchoDropService.Kind.PACK && !drops.missing(d) && now<d.expiresAt() && actor.distanceToSqr(d.pos())<=range*range
                && (magnet || match.bags().canPickup(actor,d.instanceId())) && match.bags().resource(d.instanceId()).map(r -> r.quality()==EchoDraftService.PackQuality.BLACK_MARKET).orElse(false)) candidates.put("pack:"+d.dropId(),d.pos());
        var target=candidates.entrySet().stream().min(Comparator.comparingDouble(e -> actor.distanceToSqr(e.getValue())));
        var pickup=magnet?drops.all().stream().filter(d -> d.kind()==EchoDropService.Kind.PACK && !drops.missing(d) && now<d.expiresAt()
                        && actor.distanceToSqr(d.pos())<=pickupRange*pickupRange && match.bags().canPickup(actor,d.instanceId()) && los(actor,d.pos().add(0,0.3,0)))
                .min(Comparator.comparingDouble(d -> actor.distanceToSqr(d.pos()))):Optional.<EchoDropService.Drop>empty();
        if(target.isEmpty() && pickup.isEmpty()) throw new IllegalStateException("NO_TARGET");
        pickup.ifPresent(d -> match.bags().pickup(actor,d)); ground.removeIf(l -> l.owner().equals(actor.getUUID()));
        target.ifPresent(e -> { var lease=new GroundLease(actor.getUUID(),e.getKey(),now,now+duration,range); if(groundPoint(lease).isPresent()) ground.add(lease); });
        return true;
    }
    private Optional<Vec3> groundPoint(GroundLease lease) {
        var owner=player(lease.owner()); if(!alive(owner)) return Optional.empty();
        Optional<Vec3> point;
        if(lease.handle().startsWith("black:")) point=Optional.ofNullable(match.objectives().blackTargets(owner).get(lease.handle().substring(6)));
        else point=match.objectives().drops().byId(UUID.fromString(lease.handle().substring(5)))
                .filter(d -> !match.objectives().drops().missing(d) && match.matchTick()<d.expiresAt()).map(EchoDropService.Drop::pos);
        return point.filter(p -> owner.distanceToSqr(p)<=lease.range()*lease.range());
    }
    private boolean los(ServerPlayer actor,Vec3 point) {
        var hit=level.clip(new ClipContext(actor.getEyePosition(),point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,actor));
        return hit.getType()==HitResult.Type.MISS || hit.getLocation().distanceToSqr(point)<0.04;
    }
    public void tick() {
        long now=match.matchTick();
        leases.removeIf(l -> now>=l.end() || !alive(player(l.owner())) && !match.inGrace(l.owner()) || !alive(player(l.target()))
                || (l.kind().equals("tracking") || l.kind().equals("cargo_pulse")) && RoleData.getOptional(EchoRoleData.class,player(l.target())).map(d -> d.cargo().stream().noneMatch(match.objectives()::ordinaryCargo)).orElse(true)
                || l.kind().equals("key_pulse") && RoleData.getOptional(EchoRoleData.class,player(l.target())).map(d -> d.key()==null).orElse(true));
        ground.removeIf(l -> now>=l.end() || !match.inGrace(l.owner()) && groundPoint(l).isEmpty());
        for(ServerPlayer receiver:match.onlinePlayers()) {
            List<EchoSignalView> views=new ArrayList<>();
            // 队友授权优先放入有界投影，满员时不会被其它信号挤掉；倒地仍属于在场队友。
            if(match.present(receiver) && match.life(receiver.getUUID()).potentiallyEscapable())
                for(ServerPlayer target:match.onlinePlayers())
                    if(target!=receiver && match.present(target) && match.life(target.getUUID()).potentiallyEscapable()
                            && org.agmas.noellesroles.game.modes.echo.visual.EchoVisualState.teammates(
                                    match.faction(receiver.getUUID()),match.faction(target.getUUID())))
                        views.add(new EchoSignalView("teammate",target.getUUID(),0,(now/20+2)*20));
            if(alive(receiver)) {
                for(Lease l:leases) if(l.owner().equals(receiver.getUUID()) && (now-l.start())%l.interval()<l.visible()) {
                    ServerPlayer target=player(l.target());
                    if(EchoSignalView.directionOnly(l.kind())) {
                        Vec3 position=l.kind().equals("tracking")?match.cards().scenes().deception(receiver).orElse(target.position()):target.position();
                        int sector=sector(receiver.getX(),receiver.getZ(),position.x,position.z);
                        if((now-l.start())%l.interval()==0) cachedSectors.put(l,sector);
                        views.add(new EchoSignalView(l.kind(),null,cachedSectors.getOrDefault(l,sector),Math.min(l.end(),l.start()+((now-l.start())/l.interval()+1)*l.interval())));
                    } else views.add(new EchoSignalView(l.kind(),l.target(),0,Math.min(l.end(),now+l.visible()-(now-l.start())%l.interval())));
                }
                for(var l:ground) if(l.owner().equals(receiver.getUUID())) groundPoint(l).ifPresent(point -> {
                    if((now-l.start())%20==0 || !cachedGround.containsKey(l)) cachedGround.put(l,sector(receiver.getX(),receiver.getZ(),point.x,point.z));
                    views.add(new EchoSignalView("salvage",null,cachedGround.get(l),l.end()));
                });
                if(match.faction(receiver.getUUID())==EchoFaction.INTERCEPTOR) for(ServerPlayer wolf:wolves(receiver,Double.MAX_VALUE))
                    if(wolf!=receiver) views.add(new EchoSignalView("ally",wolf.getUUID(),0,Long.MAX_VALUE));
            }
            if(match.present(receiver) && match.life(receiver.getUUID()).potentiallyEscapable()) for(ServerPlayer target:match.onlinePlayers())
                if(alive(target)) match.cards().state(target.getUUID()).mask(now).ifPresent(mask -> views.add(new EchoSignalView("mask",target.getUUID(),0,mask.until())));
            RoleData.getOptional(EchoRoleData.class,receiver).ifPresent(d -> d.projectSignals(views.stream().distinct().limit(32).toList()));
        }
        cachedSectors.keySet().retainAll(leases); cachedGround.keySet().retainAll(ground);
    }
    public static int sector(double ox,double oz,double x,double z) { return Math.floorMod((int)Math.round(Math.atan2(z-oz,x-ox)/(Math.PI/4)),8); }
    public void depart(UUID owner) { leases.removeIf(l -> l.owner().equals(owner) || l.target().equals(owner)); ground.removeIf(l -> l.owner().equals(owner)); }
    public void cleanup() {
        leases.clear(); ground.clear(); cachedSectors.clear(); cachedGround.clear();
        for(ServerPlayer player:match.onlinePlayers()) RoleData.getOptional(EchoRoleData.class,player).ifPresent(d -> d.projectSignals(List.of()));
    }
}
