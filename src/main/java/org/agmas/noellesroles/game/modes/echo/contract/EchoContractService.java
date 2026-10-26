package org.agmas.noellesroles.game.modes.echo.contract;

import io.wifi.starrailexpress.api.data.RoleData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.modes.echo.inventory.*;
import org.agmas.noellesroles.game.modes.echo.map.EchoMapConfig;
import org.agmas.noellesroles.game.modes.echo.objective.*;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoMatch;
import org.agmas.noellesroles.game.modes.echo.state.*;
import org.agmas.noellesroles.game.modes.echo.trade.EchoTradeState;
import java.util.*;

/** 契约权威常驻会话；职业数据只是本人投影，事实不能由客户端或伪装效果写入。 */
public final class EchoContractService {
    private record Stay(long start,Vec3 anchor) { }
    private final EchoMatch match;
    private final ServerLevel level;
    private final EchoMapConfig map;
    private final Map<UUID,EchoContractState> states=new LinkedHashMap<>();
    private final Map<UUID,String> dropoffs=new HashMap<>();
    private final Map<UUID,Stay> stays=new HashMap<>();
    private final Map<UUID,Long> nextGlint=new HashMap<>();
    private final Set<UUID> notified=new HashSet<>(),seenTrades=new HashSet<>();
    private final EchoKeyProvenance keys=new EchoKeyProvenance();
    public EchoContractService(EchoMatch match,ServerLevel level,EchoMapConfig map) {
        this.match=match; this.level=level; this.map=map;
        int next=0;
        for(UUID owner:match.participants()) if(match.contract(owner)!=null) {
            states.put(owner,new EchoContractState(match.contract(owner)));
            if(match.contract(owner)==EchoContract.SMUGGLER) dropoffs.put(owner,map.blackDropoffs().get(next++%map.blackDropoffs().size()).id());
        }
        states.keySet().forEach(this::project);
    }
    public EchoContractState state(UUID owner) { return states.get(owner); }
    public boolean smuggler(UUID owner) { var s=state(owner); return s!=null && s.contract()==EchoContract.SMUGGLER && s.active(); }
    public boolean dropoff(UUID owner,String id) { return smuggler(owner) && Objects.equals(dropoffs.get(owner),id); }
    public void delivered(UUID owner,UUID instance) { var s=state(owner); if(s!=null) { s.delivered(instance,match.matchTick()); project(owner); } }
    public void resolved(UUID owner,UUID pack) {
        var s=state(owner); if(s!=null) { s.resolved(pack); if(s.armed()) nextGlint.putIfAbsent(owner,match.matchTick()); project(owner); }
    }
    public void invalidate(UUID instance) { keys.invalidate(instance); }
    public void traded(EchoTradeState.Committed event) {
        if(!seenTrades.add(event.tradeId())) return;
        event.fromA().forEach(r -> invalidate(r.instanceId())); event.fromB().forEach(r -> invalidate(r.instanceId()));
        tradedSide(event.tradeId(),event.a(),event.b(),event.fromA(),event.fromB());
        tradedSide(event.tradeId(),event.b(),event.a(),event.fromB(),event.fromA());
        var a=level.getServer().getPlayerList().getPlayer(event.a());
        if(a!=null) for(var p:match.onlinePlayers()) if(match.present(p) && p.distanceToSqr(a)<=256) p.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP,SoundSource.PLAYERS,0.7F,1);
    }
    private void tradedSide(UUID trade,UUID owner,UUID peer,List<EchoResourceInstance> outgoing,List<EchoResourceInstance> incoming) {
        var s=state(owner); if(s==null || !s.active()) return;
        s.traded(trade,peer,match.faction(peer),outgoing.stream().map(EchoResourceInstance::instanceId).toList(),incoming.stream().map(EchoResourceInstance::instanceId).toList(),match.matchTick());
        if(s.contract()==EchoContract.KEY_DEALER && match.faction(peer)==EchoFaction.CARRIER)
            outgoing.stream().filter(EchoResourceInstance::key).forEach(r -> keys.traded(r.instanceId(),trade,owner,peer));
        project(owner);
    }
    /** 只能在真实控制器成功打开且该实例消费之后调用。 */
    public void keyUsed(ServerPlayer actor,UUID instance) {
        keys.consume(instance,actor.getUUID()).ifPresent(chain -> {
            var s=state(chain.dealer());
            if(s==null || !s.active() || match.faction(actor.getUUID())!=EchoFaction.CARRIER) return;
            s.keyConsumed(match.matchTick()); project(chain.dealer());
            for(var wolf:match.cards().signals().wolves(actor,48)) match.cards().signals().lease(wolf,actor,"key_contract",40,20,40);
        });
    }
    public boolean retireReady(UUID owner) {
        var s=state(owner); var stay=stays.get(owner);
        return s!=null && s.mayRetire() && stay!=null && match.matchTick()-stay.start()>=60;
    }
    private boolean exitEligible(ServerPlayer p) {
        var s=state(p.getUUID());
        return s!=null && s.mayRetire() && match.standing(p) && !match.bags().busy(p.getUUID()) && !match.trades().busy(p.getUUID())
                && RoleData.getOptional(EchoRoleData.class,p).map(d -> d.actionId()==null).orElse(false)
                && EchoObjectiveService.reachable(p,EchoGateService.vec(map.brokerExit().pos()),3);
    }
    public void retire(ServerPlayer player,boolean confirmed) {
        var stay=stays.get(player.getUUID());
        if(!confirmed || !exitEligible(player) || !retireReady(player.getUUID()) || stay==null || stay.anchor().distanceToSqr(player.position())>0.04) throw new IllegalStateException("NO_TARGET");
        match.retire(player); stays.remove(player.getUUID()); project(player.getUUID());
    }
    public void tick() {
        for(var chain:keys.all()) if(match.objectives().ledger().find(chain.instance())
                .filter(p -> p.kind()==EchoResourceLedger.Kind.INVENTORY && chain.recipient().equals(p.owner())).isEmpty()) keys.invalidate(chain.instance());
        for(var p:match.onlinePlayers()) {
            var s=state(p.getUUID()); if(s==null) continue;
            if(exitEligible(p)) {
                var stay=stays.get(p.getUUID());
                if(stay==null || stay.anchor().distanceToSqr(p.position())>0.04) stays.put(p.getUUID(),new Stay(match.matchTick(),p.position()));
            } else stays.remove(p.getUUID());
            if(s.armed() && match.standing(p) && match.matchTick()>=nextGlint.getOrDefault(p.getUUID(),Long.MAX_VALUE)) {
                nextGlint.put(p.getUUID(),match.matchTick()+300);
                for(var wolf:match.cards().signals().wolves(p,32)) match.cards().signals().outline(wolf,p,"contract",40);
            }
            project(p.getUUID());
        }
    }
    public void depart(UUID owner) { var s=state(owner); if(s!=null) s.depart(); stays.remove(owner); project(owner); }
    public void suspend(UUID owner) { stays.remove(owner); }
    public void restore(UUID owner) { stays.remove(owner); project(owner); }
    public void finish(boolean normal) {
        for(var e:states.entrySet()) {
            var p=level.getServer().getPlayerList().getPlayer(e.getKey());
            e.getValue().finish(normal,p!=null && match.present(p),match.life(e.getKey()),match.matchTick()); project(e.getKey());
        }
    }
    public boolean succeeded(UUID owner) { var s=state(owner); return s!=null && s.succeeded(); }
    private void project(UUID owner) {
        var s=state(owner); var p=match.connectedPlayer(owner); if(s==null || p==null) return;
        RoleData.getOptional(EchoRoleData.class,p).ifPresent(d -> d.projectContract(s.snapshot(dropoffs.getOrDefault(owner,""),retireReady(owner))));
        if(s.succeeded() && notified.add(owner)) {
            p.sendSystemMessage(Component.translatable("echo.contract.succeeded"));
            match.economy().award(p,org.agmas.noellesroles.game.modes.echo.economy.EchoEconomy.Source.CONTRACT,null);
        }
    }
    public void cleanup() { stays.clear(); keys.clear(); }
}
