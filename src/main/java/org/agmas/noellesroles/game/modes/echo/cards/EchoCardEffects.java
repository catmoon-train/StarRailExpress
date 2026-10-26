package org.agmas.noellesroles.game.modes.echo.cards;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.modes.echo.inventory.*;
import org.agmas.noellesroles.game.modes.echo.objective.EchoObjectiveService;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoMatch;
import org.agmas.noellesroles.game.modes.echo.state.*;
import java.util.*;
import static org.agmas.noellesroles.game.modes.echo.cards.EchoEffectState.*;

/** 卡牌常量分派；所有入口共用库存所有权校验，无效果成功时不消费实例。 */
public final class EchoCardEffects {
    public static final ResourceLocation SPEED=ResourceLocation.fromNamespaceAndPath("noellesroles","echo_card_speed");
    private final EchoMatch match;
    private final ServerLevel level;
    private final EchoSignalService signals;
    private final Map<UUID,EchoEffectState> states=new HashMap<>();
    private final Map<UUID,UUID> selected=new HashMap<>();
    private final EchoSceneService scenes;
    public EchoCardEffects(EchoMatch match,ServerLevel level) { this.match=match; this.level=level; signals=new EchoSignalService(match,level); scenes=new EchoSceneService(match,level); }
    public EchoSceneService scenes() { return scenes; }
    public EchoEffectState state(UUID owner) { return states.computeIfAbsent(owner,id -> new EchoEffectState()); }
    public EchoSignalService signals() { return signals; }
    private EchoCardDefinition require(ServerPlayer player,UUID instance) {
        if(!match.standing(player)) reject("DOWNED");
        if(!match.phase().acceptsActions()) reject("WRONG_PHASE");
        if(SREGameTimeComponent.KEY.get(level).isTimeFrozen()) reject("PAUSED");
        if(match.bags().busy(player.getUUID()) || match.trades().busy(player.getUUID())) reject("BUSY");
        var inventory=match.bags().inventory(player.getUUID());
        var resource=inventory.find(instance).orElseThrow(() -> new IllegalStateException("NO_RESOURCE"));
        if(resource.kind()==EchoResourceInstance.Kind.PACK) reject("NO_RESOURCE");
        if(inventory.locked(instance)) reject("LOCKED");
        var card=match.rules().card(resource.definitionId());
        if(!card.usableBy(match.faction(player.getUUID()))) reject("WRONG_FACTION");
        if(card.activation()==EchoCardDefinition.Activation.PASSIVE) reject("PASSIVE");
        return card;
    }
    public void equip(ServerPlayer player,UUID instance) {
        var card=require(player,instance);
        if(card.target()==EchoCardDefinition.Target.SELF || card.target()==EchoCardDefinition.Target.DIRECTION) reject("NO_TARGET");
        selected.put(player.getUUID(),instance);
        RoleData.getOptional(EchoRoleData.class,player).ifPresent(d -> d.projectSelected(instance));
    }
    public void use(ServerPlayer player,EchoInventoryPayload request) {
        var card=require(player,request.first());
        var inventorySlot=org.agmas.noellesroles.game.modes.echo.inventory.EchoHotbarSlots.resourceSlot(player.getInventory().selected);
        boolean heldResource=inventorySlot>=0 && match.bags().inventory(player.getUUID()).slot(request.first())==inventorySlot
                && player.getMainHandItem().is(org.agmas.noellesroles.game.modes.echo.content.EchoContent.RESOURCE);
        boolean terminal=request.first().equals(selected.get(player.getUUID())) && player.getInventory().selected==1
                && player.getMainHandItem().is(org.agmas.noellesroles.game.modes.echo.content.EchoContent.CARD_PROXY);
        if(card.target()!=EchoCardDefinition.Target.SELF && card.target()!=EchoCardDefinition.Target.DIRECTION
                && !heldResource && !terminal) reject("NO_TARGET");
        boolean success=apply(player,card,request);
        if(!success) reject("EFFECT_ACTIVE");
        var inventory=match.bags().inventory(player.getUUID());
        inventory.remove(request.first(),EchoResourceLedger.Location.consumed(),inventory.revision(),null);
        if(card.isKey()) match.contracts().keyUsed(player,request.first());
        match.objectives().cancelActor(player); match.activeAction(player.getUUID());
        if(request.first().equals(selected.get(player.getUUID()))) clearSelection(player);
        match.bags().changed(player); updateSpeed(player);
    }
    private boolean apply(ServerPlayer player,EchoCardDefinition c,EchoInventoryPayload request) {
        long now=match.matchTick(); var state=state(player.getUUID()); int rank=c.tier()==EchoCardDefinition.Tier.UPGRADED?2:1;
        return switch(c.shortId()) {
            case "silent_collector","silent_rig" -> state.add(Marker.SILENCE,new Mark(c.id(),now+ticks(c,"durationTicks"),ticks(c,"charges"),0,false,0),now);
            case "trade_offer","trade_pact" -> state.add(Marker.TRADE,new Mark(c.id(),now+ticks(c,"durationTicks"),ticks(c,"charges"),ticks(c,"offerTicks"),false,param(c,"range")),now);
            case "short_dash","long_dash" -> state.boost(new Boost(c.id(),rank,now+ticks(c,"durationTicks"),param(c,"speedBonus")),now);
            case "steady_flow","beacon_boost","overdrive_beacon","stall_pulse","stall_surge","door_wedge","sound_key","master_key"
                    -> match.objectives().cardEffect(player,c,request.point());
            case "cargo_raid" -> {
                var victim=target(player,request.second(),param(c,"range"));
                if(match.faction(victim.getUUID())==EchoFaction.INTERCEPTOR) reject("NO_TARGET");
                if(!match.objectives().dropCarried(victim.getUUID(),false,victim.position())) reject("NO_RESOURCE");
                victim.sendSystemMessage(Component.translatable("echo.signal.cargo_raided")); yield true;
            }
            case "team_radar","team_scan" -> {
                if(!signals.canReplace(player.getUUID(),"radar",ticks(c,"durationTicks"))) yield false;
                var wolves=signals.wolves(player,param(c,"range"));
                if(wolves.isEmpty()) reject("NO_TARGET");
                signals.replace(player.getUUID(),"radar");
                wolves.forEach(p -> signals.outline(player,p,"radar",ticks(c,"durationTicks"))); yield true;
            }
            case "crystal_sniff","global_pulse" -> {
                if(!signals.canReplace(player.getUUID(),"tracking",ticks(c,"durationTicks"))) yield false;
                var carriers=signals.carriers(player,ticks(c,"maxTargets"));
                if(carriers.isEmpty()) reject("NO_TARGET");
                signals.replace(player.getUUID(),"tracking");
                carriers.forEach(p -> signals.lease(player,p,"tracking",ticks(c,"durationTicks"),ticks(c,"intervalTicks"),ticks(c,"intervalTicks"))); yield true;
            }
            case "hunt_mark","hunt_pressure" -> {
                var target=target(player,request.second(),param(c,"range"));
                if(match.faction(target.getUUID())==EchoFaction.INTERCEPTOR) reject("NO_TARGET");
                if(signals.marked(target.getUUID()) || state(target.getUUID()).slow(now).isPresent()) yield false;
                if(c.shortId().equals("hunt_mark")) signals.lease(player,target,"hunt",ticks(c,"durationTicks"),ticks(c,"intervalTicks"),ticks(c,"outlineTicks"));
                else {
                    if(state(target.getUUID()).slow(now).isPresent()) yield false;
                    state(target.getUUID()).slow(new Boost(c.id(),rank,now+ticks(c,"slowTicks"),param(c,"speedPenalty")),now);
                    var wolves=new ArrayList<>(signals.wolves(player,Double.MAX_VALUE)); wolves.remove(player); wolves.addFirst(player);
                    wolves.stream().limit(2).forEach(p -> signals.outline(p,target,"hunt",ticks(c,"outlineTicks")));
                    updateSpeed(target);
                }
                signals.outline(target,player,"reveal",ticks(c,"revealSelfTicks"));
                target.sendSystemMessage(Component.translatable("echo.signal.marked")); yield true;
            }
            case "decoy_dummy","silent_dummy","afterimage","residual_veil","false_echo","false_dispatch","forged_signal","forged_dispatch" -> scene(player,c,request);
            case "faction_mask","double_mask" -> state.disguise(new Mask(c.id(),rank,now+ticks(c,"durationTicks"),ticks(c,"suppressedKeyPulses")),now);
            case "pack_hook" -> {
                var victim=target(player,request.second(),param(c,"range")); String cooldown="hook:"+victim.getUUID();
                if(!state.ready(cooldown,now)) reject("COOLDOWN");
                match.bags().stealPack(player,victim); state.cooldown(cooldown,now+ticks(c,"targetCooldownTicks")); yield true;
            }
            case "key_disruptor" -> {
                var victim=target(player,request.second(),param(c,"range"));
                if(match.faction(victim.getUUID())==EchoFaction.INTERCEPTOR) reject("NO_TARGET");
                UUID key=match.bags().inventory(victim.getUUID()).key().map(EchoResourceInstance::instanceId).orElseThrow(() -> new IllegalStateException("NO_TARGET"));
                match.trades().interrupt(victim.getUUID());
                if(match.bags().inventory(victim.getUUID()).locked(key)) reject("LOCKED");
                if(!match.bags().drop(victim,key,true)) reject("FULL");
                victim.sendSystemMessage(Component.translatable("echo.signal.key_disrupted")); yield true;
            }
            case "black_sniff","salvage_magnet" -> signals.salvage(player,c);
            default -> throw new IllegalStateException("EFFECT_UNAVAILABLE");
        };
    }
    private boolean scene(ServerPlayer player,EchoCardDefinition c,EchoInventoryPayload request) {
        long now=match.matchTick(); var status=state(player.getUUID()); String id=c.shortId();
        boolean image=id.equals("afterimage") || id.equals("residual_veil");
        boolean dummy=id.equals("decoy_dummy") || id.equals("silent_dummy");
        String group=id.startsWith("false_")?"false_submit":"forgery";
        if(!image && !dummy && !status.ready(group,now)) reject("COOLDOWN");
        Vec3 p=image?player.position():scenes.ground(player,request.point(),param(c,"range"));
        var kind=image?EchoSceneState.Kind.IMAGE:id.equals("silent_dummy")?EchoSceneState.Kind.SILENT_DUMMY:dummy?EchoSceneState.Kind.DUMMY:
                id.startsWith("false_")?EchoSceneState.Kind.SUBMISSION:id.equals("forged_dispatch")?EchoSceneState.Kind.DISPATCH:
                request.option()==0?EchoSceneState.Kind.STALL:EchoSceneState.Kind.SUBMISSION;
        if(id.equals("forged_signal") && request.option()!=0 && request.option()!=1) reject("NO_TARGET");
        int duration=image?ticks(c,"imageTicks"):dummy?ticks(c,"durationTicks"):id.startsWith("false_")?ticks(c,"highlightTicks"):ticks(c,"durationTicks");
        List<EchoSceneState.Spec> specs=new ArrayList<>();
        specs.add(new EchoSceneState.Spec(kind,EchoSceneService.point(p),player.getYRot(),0,duration,kind==EchoSceneState.Kind.DUMMY?ticks(c,"soundIntervalTicks"):kind.submission()?0:-1,
                id.equals("silent_dummy")?ticks(c,"deceptionTicks"):0,param(c,"deceptionRange"),param(c,"soundRange")));
        if(id.equals("false_dispatch")) {
            Vec3 second=scenes.ground(player,request.secondPoint(),param(c,"range"));
            if(p.distanceToSqr(second)<Math.pow(param(c,"minSeparation"),2)) reject("OUT_OF_RANGE");
            specs.add(new EchoSceneState.Spec(kind,EchoSceneService.point(second),player.getYRot(),ticks(c,"delayTicks"),ticks(c,"highlightTicks"),0));
        }
        int rank=c.tier()==EchoCardDefinition.Tier.UPGRADED?2:1;
        if(image && !status.canBoost(rank,now) || !scenes.state().canAdd(player.getUUID(),specs,now)) return false;
        if(!scenes.add(player,specs)) return false;
        if(image) status.boost(new Boost(c.id(),rank,now+ticks(c,"speedTicks"),param(c,"speedBonus")),now);
        if(!image && !dummy) status.cooldown(group,now+ticks(c,"cooldownTicks"));
        return true;
    }
    public void breakMask(UUID owner) { state(owner).breakMask(); }
    public boolean suppressKeyPulse(UUID owner) { return state(owner).suppressKeyPulse(match.matchTick()); }
    private ServerPlayer target(ServerPlayer actor,UUID id,double range) {
        var target=level.getServer().getPlayerList().getPlayer(id);
        if(target==null || target==actor || !match.present(target) || !match.life(id).standing()) reject("NO_TARGET");
        if(actor.distanceToSqr(target)>range*range) reject("OUT_OF_RANGE");
        if(!actor.hasLineOfSight(target)) reject("NO_LOS");
        return target;
    }
    public boolean silent(UUID owner) { return state(owner).mark(Marker.SILENCE,match.matchTick()).isPresent(); }
    public boolean consumeSilence(UUID owner) { return state(owner).consume(Marker.SILENCE,match.matchTick()); }
    public void updateSpeed(ServerPlayer player) {
        var data=RoleData.getNullable(EchoRoleData.class,player); var speed=player.getAttribute(Attributes.MOVEMENT_SPEED);
        if(speed==null || data==null) return;
        long now=match.matchTick();
        double amount=state(player.getUUID()).speedDelta(now,data.cargo().isEmpty()?0:data.cargoPenalty(),match.bags().opening(player.getUUID()),match.rules().draft().openingSpeed());
        // 职业增益（追击 / 扛伤）作为独立乘区叠在基线之后，仍然 clamp 到 [0.5,1.5]。
        var clazz=match.classState(player.getUUID());
        if(clazz!=null) {
            double factor=clazz.speedFactor(now);
            if(Math.abs(factor-1D)>0.005D) amount=Math.max(0.5,Math.min(1.5,(1+amount)*factor))-1;
        }
        var old=speed.getModifier(SPEED);
        if(old!=null && Double.compare(old.amount(),amount)!=0) speed.removeModifier(SPEED);
        if(amount!=0 && speed.getModifier(SPEED)==null) speed.addTransientModifier(new AttributeModifier(SPEED,amount,AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }
    public void tick() {
        states.values().forEach(s -> s.expire(match.matchTick()));
        for(ServerPlayer player:match.onlinePlayers()) {
            state(player.getUUID()).expire(match.matchTick()); updateSpeed(player);
            RoleData.getOptional(EchoRoleData.class,player).ifPresent(d -> d.projectTradeRange(state(player.getUUID()).mark(Marker.TRADE,match.matchTick()).map(Mark::range).orElse(match.rules().trade().range())));
            UUID equipped=selected.get(player.getUUID());
            if(equipped!=null && match.bags().inventory(player.getUUID()).find(equipped).isEmpty()) clearSelection(player);
        }
        scenes.tick(); signals.tick();
    }
    private void clearSelection(ServerPlayer player) {
        selected.remove(player.getUUID()); RoleData.getOptional(EchoRoleData.class,player).ifPresent(d -> d.projectSelected(null));
    }
    public void disconnect(ServerPlayer player) {
        scenes.depart(player.getUUID());
        var speed=player.getAttribute(Attributes.MOVEMENT_SPEED); if(speed!=null) speed.removeModifier(SPEED);
    }
    public void restore(ServerPlayer player) {
        state(player.getUUID()).expire(match.matchTick());
        UUID equipped=selected.get(player.getUUID());
        if(equipped!=null && match.bags().inventory(player.getUUID()).find(equipped).isEmpty()) selected.remove(player.getUUID());
        RoleData.getOptional(EchoRoleData.class,player).ifPresent(d -> d.projectSelected(selected.get(player.getUUID())));
        updateSpeed(player); signals.tick(); scenes.tick();
    }
    public void depart(ServerPlayer player) { depart(player.getUUID()); }
    public void depart(UUID owner) {
        state(owner).clear(); scenes.depart(owner); signals.depart(owner); selected.remove(owner);
        var player=level.getServer().getPlayerList().getPlayer(owner);
        if(player!=null) {
            RoleData.getOptional(EchoRoleData.class,player).filter(d -> match.id().equals(d.matchId())).ifPresent(d -> d.projectSelected(null));
            var speed=player.getAttribute(Attributes.MOVEMENT_SPEED); if(speed!=null) speed.removeModifier(SPEED);
        }
    }
    public void cleanup() { for(ServerPlayer player:match.onlinePlayers()) depart(player); scenes.cleanup(); signals.cleanup(); states.clear(); selected.clear(); }
    public static double param(EchoCardDefinition c,String key) { return c.params().getOrDefault(key,0D); }
    public static int ticks(EchoCardDefinition c,String key) { return (int)param(c,key); }
    private static void reject(String reason) { throw new IllegalStateException(reason); }
}
