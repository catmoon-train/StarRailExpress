package org.agmas.noellesroles.game.modes.echo.trade;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.game.modes.echo.cards.EchoEffectState;
import org.agmas.noellesroles.game.modes.echo.inventory.EchoInventoryPayload;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoMatch;
import org.agmas.noellesroles.game.modes.echo.state.*;
import java.util.*;

/** 世界交易编排；每人最多一笔，冻结提案规则，成交事实只在原子提交后产生。 */
public final class EchoTradeService {
    private final EchoMatch match;
    private final ServerLevel level;
    private final Map<UUID,EchoTradeState> active=new HashMap<>();
    private final Map<UUID,Long> ready=new HashMap<>();
    private final Map<UUID,EchoTradeState.Committed> facts=new LinkedHashMap<>();
    public EchoTradeService(EchoMatch match,ServerLevel level) { this.match=match; this.level=level; }
    public boolean busy(UUID owner) { return active.containsKey(owner); }
    public Collection<EchoTradeState.Committed> facts() { return List.copyOf(facts.values()); }
    private ServerPlayer player(UUID id) { return level.getServer().getPlayerList().getPlayer(id); }
    public void propose(ServerPlayer actor,UUID targetId) {
        available(actor); var target=player(targetId);
        if(target==null || target==actor) reject("NO_TARGET");
        available(target);
        long now=match.matchTick();
        if(now<ready.getOrDefault(actor.getUUID(),0L)) reject("COOLDOWN");
        var mark=match.cards().state(actor.getUUID()).mark(EchoEffectState.Marker.TRADE,now);
        var data=RoleData.getNullable(EchoRoleData.class,actor);
        double specRange=data==null?0:data.tradeRangeBonus();
        double range=mark.map(EchoEffectState.Mark::range).orElse(match.rules().trade().range()+specRange);
        int ticks=mark.map(EchoEffectState.Mark::channelTicks).orElse(match.rules().trade().offerTicks());
        spatial(actor,target,range);
        var trade=new EchoTradeState(match.bags().inventory(actor.getUUID()),match.bags().inventory(targetId),now+ticks,range);
        int cooldown=match.rules().trade().proposalCooldownTicks();
        if(data!=null) cooldown=org.agmas.noellesroles.game.modes.echo.state.EchoSpecialization.scaled(cooldown,data.proposalCooldownSpeed());
        active.put(actor.getUUID(),trade); active.put(targetId,trade); ready.put(actor.getUUID(),now+cooldown);
        if(mark.isPresent()) match.cards().state(actor.getUUID()).consume(EchoEffectState.Marker.TRADE,now);
        match.objectives().cancelActor(actor); match.objectives().cancelActor(target);
        actor.stopUsingItem(); target.stopUsingItem(); match.activeAction(actor.getUUID());
        target.sendSystemMessage(Component.translatable("echo.trade.invited",actor.getGameProfile().getName()));
        project(trade);
    }
    private void available(ServerPlayer player) {
        if(!match.standing(player)) reject("DOWNED");
        if(!match.phase().acceptsActions()) reject("WRONG_PHASE");
        if(SREGameTimeComponent.KEY.get(level).isTimeFrozen()) reject("PAUSED");
        if(busy(player.getUUID()) || match.bags().busy(player.getUUID())) reject("BUSY");
    }
    private void spatial(ServerPlayer a,ServerPlayer b,double range) {
        if(a.distanceToSqr(b)>range*range) reject("OUT_OF_RANGE");
        if(!a.hasLineOfSight(b) || !b.hasLineOfSight(a)) reject("NO_LOS");
    }
    private void validate(EchoTradeState trade) {
        var a=player(trade.a()); var b=player(trade.b());
        if(a==null || b==null || !match.standing(a) || !match.standing(b) || match.bags().busy(trade.a()) || match.bags().busy(trade.b())) reject("DOWNED");
        if(!match.phase().acceptsActions()) reject("WRONG_PHASE");
        if(trade.expire(match.matchTick())) reject("INVALID_OFFER");
        spatial(a,b,trade.range());
    }
    public void request(ServerPlayer actor,EchoInventoryPayload request) {
        if(request.operation()==EchoInventoryPayload.Operation.TRADE_PROPOSE) { propose(actor,request.first()); return; }
        var trade=active.get(actor.getUUID());
        if(trade==null || !trade.id().equals(request.tradeId())) {
            if(request.operation()==EchoInventoryPayload.Operation.TRADE_CANCEL) return;
            reject("INVALID_OFFER");
        }
        if(request.operation()==EchoInventoryPayload.Operation.TRADE_CANCEL) { cancel(actor.getUUID(),request.tradeId()); return; }
        try {
            validate(trade);
            switch(request.operation()) {
                case TRADE_ACCEPT -> trade.accept(actor.getUUID(),request.tradeVersion(),match.matchTick());
                case TRADE_OFFER -> trade.offer(actor.getUUID(),request.offer(),request.tradeVersion(),request.revision(),match.matchTick());
                case TRADE_CONFIRM -> {
                    if(trade.confirm(actor.getUUID(),request.tradeVersion(),request.revision(),match.matchTick())) {
                        var event=trade.committed().orElseThrow(); facts.put(trade.id(),event);
                        // 交易成功：双方各得一次收入，按 tradeId 去重。
                        for(UUID side:java.util.List.of(event.a(),event.b())) {
                            var sidePlayer=match.connectedPlayer(side);
                            if(sidePlayer!=null) match.economy().award(sidePlayer,
                                    org.agmas.noellesroles.game.modes.echo.economy.EchoEconomy.Source.TRADE,trade.id().toString());
                        }
                        match.contracts().traded(event); finish(trade,true); return;
                    }
                }
                default -> reject("INVALID_OFFER");
            }
        } catch(IllegalStateException failure) {
            if(!Set.of("REVISION_MISMATCH","LOCKED","NO_RESOURCE").contains(failure.getMessage())) { trade.cancel(trade.id()); finish(trade,false); }
            else project(trade);
            throw failure;
        }
        project(trade);
    }
    public void inventoryChanged(UUID owner) {
        var trade=active.get(owner); if(trade!=null && trade.refresh()) project(trade);
    }
    public boolean cancel(UUID owner,UUID token) {
        var trade=active.get(owner); if(trade==null || !trade.id().equals(token)) return false;
        trade.cancel(token); finish(trade,false); return true;
    }
    public void interrupt(UUID owner) { var trade=active.get(owner); if(trade!=null) cancel(owner,trade.id()); }
    public void tick() {
        for(var trade:new HashSet<>(active.values())) {
            try { validate(trade); if(trade.refresh()) project(trade); }
            catch(IllegalStateException invalid) { trade.cancel(trade.id()); finish(trade,false); }
        }
    }
    private void finish(EchoTradeState trade,boolean success) {
        active.remove(trade.a(),trade); active.remove(trade.b(),trade);
        if(success) {
            var a=player(trade.a()); var b=player(trade.b());
            if(a!=null && b!=null) org.agmas.noellesroles.game.modes.echo.objective.EchoAudio.nearby(match,a.position().add(b.position()).scale(0.5),net.minecraft.sounds.SoundEvents.VILLAGER_YES,16,0.7F);
        }
        for(UUID id:List.of(trade.a(),trade.b())) {
            var player=player(id); if(player==null) continue;
            RoleData.getOptional(EchoRoleData.class,player).ifPresent(d -> d.projectTrade(null));
            if(RoleData.getNullable(EchoRoleData.class,player)!=null) match.bags().changed(player);
            player.sendSystemMessage(Component.translatable(success?"echo.trade.committed":"echo.trade.cancelled"));
        }
    }
    private void project(EchoTradeState trade) {
        if(!trade.active()) return;
        for(UUID owner:List.of(trade.a(),trade.b())) {
            var p=player(owner); var peer=player(trade.peer(owner)); if(p==null || peer==null) continue;
            var view=new EchoTradeView(trade.id(),trade.version(),trade.status().name(),peer.getUUID(),peer.getGameProfile().getName(),owner.equals(trade.a()),
                    trade.resources(owner),trade.resources(peer.getUUID()),trade.confirmed(owner),trade.confirmed(peer.getUUID()),trade.deadline(),trade.range(),trade.layoutError());
            RoleData.getOptional(EchoRoleData.class,p).ifPresent(d -> d.projectTrade(view));
        }
    }
    /** G 快捷只选服务器准星射线上的最近站立玩家，不穿墙、不选择假人。 */
    public void quick(ServerPlayer actor) {
        var mark=match.cards().state(actor.getUUID()).mark(EchoEffectState.Marker.TRADE,match.matchTick());
        var data=RoleData.getNullable(EchoRoleData.class,actor);
        double range=mark.map(EchoEffectState.Mark::range).orElse(match.rules().trade().range()+(data==null?0:data.tradeRangeBonus()));
        var eye=actor.getEyePosition(); var end=eye.add(actor.getLookAngle().scale(range));
        var block=level.clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,actor));
        double limit=block.getType()==HitResult.Type.MISS?range*range:eye.distanceToSqr(block.getLocation());
        ServerPlayer best=null;
        for(var target:match.onlinePlayers()) if(target!=actor && match.standing(target)) {
            var hit=target.getBoundingBox().inflate(0.05).clip(eye,end);
            double distance=hit.map(eye::distanceToSqr).orElse(Double.POSITIVE_INFINITY);
            if(distance<limit) { limit=distance; best=target; }
        }
        try { if(best==null) reject("NO_TARGET"); propose(actor,best.getUUID()); }
        catch(IllegalStateException rejected) { actor.displayClientMessage(Component.translatable("echo.error."+rejected.getMessage().toLowerCase(Locale.ROOT)),true); }
    }
    /** 掮客技能：无视提案冷却、范围 +4 格；失败不消耗冷却。 */
    public boolean skillDeal(ServerPlayer actor) {
        try {
            available(actor);
            double range=match.rules().trade().range()+4;
            var target=pick(actor,range);
            if(target==null) return false;
            spatial(actor,target,range);
            long now=match.matchTick();
            int ticks=match.rules().trade().offerTicks();
            var trade=new EchoTradeState(match.bags().inventory(actor.getUUID()),match.bags().inventory(target.getUUID()),now+ticks,range);
            active.put(actor.getUUID(),trade); active.put(target.getUUID(),trade);
            ready.put(actor.getUUID(),now+match.rules().trade().proposalCooldownTicks());
            match.objectives().cancelActor(actor); match.objectives().cancelActor(target);
            actor.stopUsingItem(); target.stopUsingItem(); match.activeAction(actor.getUUID());
            target.sendSystemMessage(Component.translatable("echo.trade.invited",actor.getGameProfile().getName()));
            project(trade);
            return true;
        } catch(IllegalStateException rejected) { return false; }
    }
    /** 服务器准星射线上的最近站立玩家；不穿墙、不选假人。 */
    private ServerPlayer pick(ServerPlayer actor,double range) {
        var eye=actor.getEyePosition(); var end=eye.add(actor.getLookAngle().scale(range));
        var block=level.clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,actor));
        double limit=block.getType()==HitResult.Type.MISS?range*range:eye.distanceToSqr(block.getLocation());
        ServerPlayer best=null;
        for(var target:match.onlinePlayers()) if(target!=actor && match.standing(target)) {
            var hit=target.getBoundingBox().inflate(0.05).clip(eye,end);
            double distance=hit.map(eye::distanceToSqr).orElse(Double.POSITIVE_INFINITY);
            if(distance<limit) { limit=distance; best=target; }
        }
        return best;
    }
    public void cleanup() { for(var trade:new HashSet<>(active.values())) { trade.cancel(trade.id()); finish(trade,false); } ready.clear(); }
    private static void reject(String error) { throw new IllegalStateException(error); }
}
