package org.agmas.noellesroles.game.modes.echo.objective;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.util.ParticleFx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.modes.echo.action.EchoActionService;
import org.agmas.noellesroles.game.modes.echo.cards.EchoDraftService.PackQuality;
import org.agmas.noellesroles.game.modes.echo.inventory.EchoResourceLedger;
import org.agmas.noellesroles.game.modes.echo.inventory.EchoResourceLedger.Location;
import org.agmas.noellesroles.game.modes.echo.map.EchoMapConfig;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoMatch;
import org.agmas.noellesroles.game.modes.echo.state.*;
import org.agmas.noellesroles.game.modes.echo.content.EchoMachineInteractions;
import org.agmas.noellesroles.game.modes.echo.content.EchoMachineTasks;

import java.util.*;

/** 阶段二目标循环；资源移动只走台账，成功后才发奖励和回放事实。 */
public final class EchoObjectiveService {
    private enum Kind { CRYSTAL, BEACON, ALTAR, CORE, FREE_EXIT, CRATE, DROP, RESCUE, BLACK_NODE, BLACK_DROPOFF }
    private record Target(String handle,Kind kind,String id,Vec3 pos) { }
    private static final class Resource {
        final EchoDropService.Kind kind;
        long nextPulse;
        Resource(EchoDropService.Kind kind,long nextPulse) { this.kind=kind; this.nextPulse=nextPulse; }
    }
    private static final ResourceLocation CARGO_SPEED=ResourceLocation.fromNamespaceAndPath("noellesroles","echo_cargo");
    private final EchoMatch match;
    private final ServerLevel level;
    private final EchoMapConfig map;
    private final org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig rules;
    private final EchoResourceLedger ledger;
    private final EchoActionService actions=new EchoActionService();
    private final EchoDropService drops;
    private final EchoGateService gates;
    private final Map<BlockPos,Target> targets=new HashMap<>();
    private final Map<String,Target> handles=new HashMap<>();
    private final Map<String,EchoBeaconState> beacons=new LinkedHashMap<>();
    private final Map<UUID,ServerPlayer> taskRecipients=new HashMap<>();
    private final Map<String,Long> nodeReady=new HashMap<>(),crateReady=new HashMap<>();
    private final Map<String,Long> submissionHighlights=new HashMap<>();
    private final Map<UUID,Resource> resources=new HashMap<>();
    private final Map<UUID,Long> dwell=new HashMap<>();
    /** 采集故障声纹的传播半径（格）。 */
    private static final double FAULT_RANGE=48.0D;
    private final Random random=new Random();
    /** 正在等修机判定的玩家 → 判定 + 目标。同一玩家同时只允许一轮。 */
    private final Map<UUID,PendingRepair> repairs=new java.util.HashMap<>();
    /** 一轮修机判定：权威状态机 + 目标（晶簇或黑晶点）。 */
    private record PendingRepair(org.agmas.noellesroles.game.modes.echo.repair.EchoRepairCheck check,
                                 String checkId, Target target) { }
    private EchoKeySchedule keys;
    private final org.agmas.noellesroles.game.modes.echo.contract.EchoBlackCrystalState black;
    private boolean started,closed;

    public EchoObjectiveService(EchoMatch match,ServerLevel level,EchoMapConfig map,int count) {
        this.match=match; this.level=level; this.map=map; rules=match.rules();
        black=new org.agmas.noellesroles.game.modes.echo.contract.EchoBlackCrystalState(map.blackCrystalNodes().stream().map(EchoMapConfig.Point::id).toList(),rules.logistics().blackRecoveryTicks());
        ledger=new EchoResourceLedger(match.id()); drops=new EchoDropService(level,match.id());
        gates=new EchoGateService(level,match.id(),map,rules.gate());
        for(var node:map.crystalNodes()) if(node.minPlayers()<=count) {
            target(Kind.CRYSTAL,node.id(),node.pos()); nodeReady.put(node.id(),0L);
        }
        for(var beacon:map.beacons()) if(beacon.minPlayers()<=count) {
            target(Kind.BEACON,beacon.id(),beacon.pos()); beacons.put(beacon.id(),new EchoBeaconState(rules.beacon()));
        }
        map.altars().forEach(altar -> target(Kind.ALTAR,altar.id(),altar.pos()));
        map.coreGate().doors().forEach(door -> target(Kind.CORE,door.id(),door.controlPos()));
        target(Kind.FREE_EXIT,map.coreGate().freeExit().id(),map.coreGate().freeExit().controlPos());
        for(var crate:map.supplyCrates()) if(crate.minPlayers()<=count) {
            target(Kind.CRATE,crate.id(),crate.pos()); crateReady.put(crate.id(),0L);
        }
        map.blackCrystalNodes().forEach(p -> target(Kind.BLACK_NODE,p.id(),p.pos()));
        map.blackDropoffs().forEach(p -> target(Kind.BLACK_DROPOFF,p.id(),p.pos()));
    }
    public EchoGateService gates() { return gates; }
    public EchoResourceLedger ledger() { return ledger; }
    public EchoDropService drops() { return drops; }
    public boolean ordinaryCargo(UUID instance) { return resources.containsKey(instance) && resources.get(instance).kind==EchoDropService.Kind.CRYSTAL; }
    public int blackCount(ServerPlayer player) { return (int)data(player).cargo().stream().filter(black::black).count(); }
    public boolean canPickupBlack(ServerPlayer player,UUID id) {
        return match.contracts().smuggler(player.getUUID()) && black.owned(id,player.getUUID()) && data(player).freeCargoSlot()>=0 && blackCount(player)<2;
    }
    public Map<String,Vec3> blackTargets(ServerPlayer player) {
        Map<String,Vec3> result=new LinkedHashMap<>();
        if(!match.contracts().smuggler(player.getUUID()) || data(player).freeCargoSlot()<0 || blackCount(player)>=2) return result;
        for(var p:map.blackCrystalNodes()) if(match.phase()==EchoPhase.OPERATIONS && black.available(p.id(),match.matchTick())) result.put("node:"+p.id(),EchoGateService.vec(p.pos()));
        for(var d:drops.all()) if(d.kind()==EchoDropService.Kind.BLACK_CRYSTAL && !drops.missing(d) && match.matchTick()<d.expiresAt() && canPickupBlack(player,d.instanceId())) result.put("drop:"+d.dropId(),d.pos());
        return result;
    }
    /** 只有每台机器的配置主点，不携带库存、契约分配或个人进度。 */
    public HashMap<BlockPos,Integer> taskPoints() {
        var points=new HashMap<BlockPos,Integer>();
        for(var pos:targets.keySet()) points.put(EchoMachineInteractions.root(level,pos),EchoMachineTasks.TYPE);
        points.put(BlockPos.containing(EchoGateService.vec(map.brokerExit().pos())),EchoMachineTasks.TYPE);
        points.put(BlockPos.containing(EchoGateService.vec(map.evacuation().controlPos())),EchoMachineTasks.TYPE);
        return points;
    }
    public boolean allActive() { return beacons.values().stream().allMatch(EchoBeaconState::active); }
    /** 目标卡只修改既有目标状态；坐标仅用于查找已配置的交互点。 */
    public boolean cardEffect(ServerPlayer player,org.agmas.noellesroles.game.modes.echo.cards.EchoCardDefinition card,Vec3 point) {
        if(match.phase()!=EchoPhase.OPERATIONS) throw new IllegalStateException("WRONG_PHASE");
        if(!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) throw new IllegalStateException("NO_TARGET");
        Target target=targets.get(EchoMachineInteractions.root(level,BlockPos.containing(point)));
        Kind kind=card.target()==org.agmas.noellesroles.game.modes.echo.cards.EchoCardDefinition.Target.BEACON?Kind.BEACON:Kind.CORE;
        if(target==null || target.kind()!=kind) throw new IllegalStateException("NO_TARGET");
        double range=card.params().getOrDefault("range",3D);
        if(player.getEyePosition().distanceToSqr(EchoMachineInteractions.aim(level,target.pos(),player.getEyePosition()))>range*range) throw new IllegalStateException("OUT_OF_RANGE");
        if(!reachable(player,target.pos(),range)) throw new IllegalStateException("NO_LOS");
        long now=match.matchTick();
        if(kind==Kind.CORE) {
            if(card.shortId().equals("door_wedge")) return gates.state().extendCore(now,rules.gate().wedgeExtraTicks(),rules.gate().wedgeMaxTicks());
            if(!gates.openCore(now,card.shortId().equals("master_key")?rules.gate().masterOpenTicks():rules.gate().coreOpenTicks())) return false;
            if(card.shortId().equals("master_key")) for(ServerPlayer wolf:match.onlinePlayers()) {
                if(match.present(wolf) && match.life(wolf.getUUID()).standing() && match.faction(wolf.getUUID())==EchoFaction.INTERCEPTOR
                        && EchoGateService.box(map.coreArea()).contains(wolf.position()) && wolf.position().distanceToSqr(target.pos())<=Math.pow(card.params().get("scanRange"),2))
                    match.cards().signals().outline(player,wolf,"radar",card.params().get("outlineTicks").intValue());
            }
            announce("echo.goal.core_open",target.id()); return true;
        }
        var beacon=beacons.get(target.id());
        return switch(card.shortId()) {
            // 加速卡：立即按百分比推进充能，不改变投料与已启动事实。
            case "beacon_boost","overdrive_beacon" -> beacon.boost(now,(int)card.params().getOrDefault("boostPermille",0D).doubleValue());
            // 迟滞卡：截流者让充能停住一段时间，已积累进度不受影响。
            case "stall_pulse","stall_surge" -> beacon.stall(now,(int)card.params().getOrDefault("stallTicks",0D).doubleValue());
            default -> false;
        };
    }
    /** 卡牌直接迟滞后由卡服务回调，只补经济与播报，不重复改信标状态。 */
    public void cardStalled(ServerPlayer player,EchoBeaconState beacon) {
        long now=match.matchTick();
        beacons.entrySet().stream().filter(e -> e.getValue()==beacon).map(Map.Entry::getKey).findFirst().ifPresent(id -> {
            reward(player,PackQuality.BLACK_MARKET,"stall:"+id);
            match.economy().award(player,org.agmas.noellesroles.game.modes.echo.economy.EchoEconomy.Source.STALL,id);
            announce("echo.goal.stalled",id); submissionHighlights.put(id,now+200);
        });
    }
    /** 按交互点解析信标；未配置的点返回空，卡牌不会因此改状态。 */
    public Optional<EchoBeaconState> beaconAt(Vec3 point) {
        Target target=targets.get(EchoMachineInteractions.root(level,BlockPos.containing(point)));
        if(target==null || target.kind()!=Kind.BEACON) return Optional.empty();
        return Optional.ofNullable(beacons.get(target.id()));
    }
    public void startOperations() {
        if(started) return;
        started=true;
        keys=new EchoKeySchedule(map.altars().stream().map(EchoMapConfig.Point::id).toList(),match.matchTick(),rules.logistics().keySupplyTicks());
        for(String altar:keys.initial(random)) spawnKey(altar);
    }
    private void target(Kind kind,String id,EchoMapConfig.Vec p) {
        Target target=new Target(kind.name()+":"+id,kind,id,EchoGateService.vec(p));
        if(targets.putIfAbsent(BlockPos.containing(target.pos()),target)!=null) throw new IllegalArgumentException("目标交互方块重叠: "+id);
        handles.put(target.handle(),target);
    }
    private EchoRoleData data(ServerPlayer player) {
        EchoRoleData data=RoleData.getNullable(EchoRoleData.class,player);
        if(data==null || !match.id().equals(data.matchId())) throw new IllegalStateException("模式私有数据失配");
        return data;
    }
    private boolean carrier(ServerPlayer player) { return match.faction(player.getUUID())==EchoFaction.CARRIER; }
    public boolean ownsDisplay(UUID entity) { return drops.byEntity(entity).isPresent(); }
    public void cancel(ServerPlayer player,UUID expectedAction) {
        if(actions.cancel(player.getUUID(),expectedAction)) data(player).projectAction(null);
    }
    public void cancelActor(ServerPlayer player) { cancelActor(player.getUUID()); }
    public void cancelActor(UUID owner) {
        actions.cancel(owner); dwell.remove(owner);
        var player=match.connectedPlayer(owner);
        if(player!=null) RoleData.getOptional(EchoRoleData.class,player).filter(d -> match.id().equals(d.matchId())).ifPresent(d -> d.projectAction(null));
    }
    public void grantReward(ServerPlayer player,PackQuality quality,String fact) { reward(player,quality,fact); }
    public void useBlock(ServerPlayer player,BlockPos pos) {
        // 横板优先：放下/拆除都不进入机器点位流程，未命中则原样落到机器交互。
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof org.agmas.noellesroles.game.modes.echo.content.EchoPalletBlock) {
            if (org.agmas.noellesroles.game.modes.echo.content.EchoPalletBlock.breakPallet(level, pos, player)) {
                cancelActor(player);
                return;
            }
            if (org.agmas.noellesroles.game.modes.echo.content.EchoPalletBlock.lower(level, pos, player)) {
                cancelActor(player);
                return;
            }
        }
        Target target=targets.get(EchoMachineInteractions.root(level,pos));
        if(target==null) { cancelActor(player); return; }
        request(player,target);
    }
    public void useEntity(ServerPlayer player,UUID entity) {
        if(match.combat().canRescue(player,entity)) {
            ServerPlayer victim=level.getServer().getPlayerList().getPlayer(entity);
            if(victim!=null && victim.level()==level) request(player,new Target("RESCUE:"+entity,Kind.RESCUE,entity.toString(),victim.position()));
            return;
        }
        drops.byEntity(entity).ifPresent(drop -> request(player,new Target("DROP:"+drop.dropId(),Kind.DROP,drop.dropId().toString(),drop.pos())));
    }
    private void request(ServerPlayer player,Target target) {
        if(!eligible(player,target)) { cancelActor(player); refuse(player,target); return; }
        double range=range(player,target);
        if(!reachable(player,target.pos(),range)) {
            cancelActor(player); data(player).projectInteractionError(player.getEyePosition().distanceToSqr(EchoMachineInteractions.aim(level,target.pos(),player.getEyePosition()))>range*range?"OUT_OF_RANGE":"NO_LOS",match.matchTick()+40); return;
        }
        data(player).projectInteractionError("",0);
        int duration=switch(target.kind()) {
            case CRYSTAL -> match.skillPendingActive(player.getUUID(),org.agmas.noellesroles.game.modes.echo.state.EchoSkill.SWIFT_PICK)
                    ?instant(player.getUUID(),org.agmas.noellesroles.game.modes.echo.state.EchoSkill.SWIFT_PICK)
                    :data(player).collectTicks(rules.logistics().collectTicks());
            case BLACK_NODE -> data(player).blackCollectTicks(rules.logistics().blackCollectTicks());
            case BLACK_DROPOFF -> match.skillPendingActive(player.getUUID(),org.agmas.noellesroles.game.modes.echo.state.EchoSkill.DARK_CACHE)
                    ?instant(player.getUUID(),org.agmas.noellesroles.game.modes.echo.state.EchoSkill.DARK_CACHE)
                    :rules.logistics().blackDeliverTicks();
            case BEACON -> carrier(player)?rules.logistics().submitTicks():stallTicks(player);
            case ALTAR -> rules.logistics().altarTicks();
            case RESCUE -> rules.combat().rescueTicks();
            case CRATE -> crate(target.id()).quality().equals("COMMON")?rules.logistics().crateSearchTicks():rules.logistics().blackMarketSearchTicks();
            case DROP -> drops.byId(UUID.fromString(target.id())).map(d -> d.altar()==null?1:rules.logistics().altarTicks()).orElse(1);
            default -> 1;
        };
        if(duration==1) {
            actions.cancel(player.getUUID()); data(player).projectAction(null);
            if(target.kind()!=Kind.RESCUE) match.activeAction(player.getUUID());
            complete(player,target); return;
        }
        boolean exclusive=target.kind()!=Kind.BEACON || !carrier(player);
        boolean fresh=actions.current(player.getUUID()).isEmpty();
        actions.request(player.getUUID(),target.handle(),duration,match.matchTick(),player.getX(),player.getZ(),exclusive)
                .ifPresentOrElse(action -> {
                    data(player).projectActionView(new org.agmas.noellesroles.game.modes.echo.action.EchoActionView(action.id(),target.kind().name(),action.startedAt(),action.duration()));
                    if(target.kind()!=Kind.RESCUE) match.activeAction(player.getUUID());
                },() -> data(player).projectInteractionError("BUSY",match.matchTick()+40));
        if(fresh && actions.current(player.getUUID()).isPresent() && (target.kind()==Kind.CRYSTAL || target.kind()==Kind.BEACON) && !(carrier(player) && match.cards().silent(player.getUUID()))) {
            nearby(target.pos(),SoundEvents.AMETHYST_BLOCK_CHIME,24);
        }
    }
    private Target resolve(String handle) {
        Target existing=handles.get(handle);
        if(existing!=null) return existing;
        if(handle.startsWith("RESCUE:")) {
            UUID id=UUID.fromString(handle.substring(7)); ServerPlayer victim=level.getServer().getPlayerList().getPlayer(id);
            return victim!=null && victim.level()==level?new Target(handle,Kind.RESCUE,id.toString(),victim.position()):null;
        }
        if(handle.startsWith("DROP:")) return drops.byId(UUID.fromString(handle.substring(5)))
                .map(d -> new Target(handle,Kind.DROP,d.dropId().toString(),d.pos())).orElse(null);
        return null;
    }
    /** 一次性技能把引导压到 1 tick，并在使用时消耗。 */
    private int instant(UUID owner,org.agmas.noellesroles.game.modes.echo.state.EchoSkill skill) {
        match.consumeSkillPending(owner,skill); return 1;
    }
    /** 迟滞引导时长：破坏者专长按倍率缩短；「迟滞脉冲」窗口内下一次更快。 */
    private int stallTicks(ServerPlayer player) {
        int base = data(player).stallTicks(rules.beacon().stallTicks());
        if (match.skillPendingActive(player.getUUID(),org.agmas.noellesroles.game.modes.echo.state.EchoSkill.STALL_BURST))
            return instant(player.getUUID(),org.agmas.noellesroles.game.modes.echo.state.EchoSkill.STALL_BURST);
        return base;
    }
    private void refuse(ServerPlayer player,Target target) {
        var d=data(player); String reason;
        if(!d.life().standing()) reason="DOWNED";
        else if(d.controlled()) reason="CONTROLLED";
        else if(!match.phase().acceptsActions() || target.kind()!=Kind.RESCUE && match.phase()!=EchoPhase.OPERATIONS && target.kind()!=Kind.FREE_EXIT && target.kind()!=Kind.DROP && target.kind()!=Kind.BLACK_DROPOFF) reason="WRONG_PHASE";
        else if(match.bags().busy(player.getUUID()) || match.trades().busy(player.getUUID())) reason="BUSY";
        else reason=switch(target.kind()) {
            case CRYSTAL,BLACK_NODE -> d.freeCargoSlot()<0 || target.kind()==Kind.BLACK_NODE && blackCount(player)>=2?"CARGO_FULL":target.kind()==Kind.CRYSTAL?!carrier(player)?"WRONG_FACTION":"COOLDOWN":!match.contracts().smuggler(player.getUUID())?"WRONG_FACTION":"COOLDOWN";
            case BEACON -> carrier(player)?d.cargo().isEmpty()?"NO_RESOURCE":"EFFECT_ACTIVE":match.faction(player.getUUID())==EchoFaction.INTERCEPTOR?"COOLDOWN":"WRONG_FACTION";
            case ALTAR -> d.key()!=null?"KEY_LIMIT":d.inventory().emptySlot()<0?"FULL":"NO_RESOURCE";
            case CORE -> d.key()==null?"NO_RESOURCE":d.inventory().locked(d.key())?"LOCKED":"EFFECT_ACTIVE";
            case CRATE -> "COOLDOWN";
            case DROP -> d.inventory().emptySlot()<0 && d.freeCargoSlot()<0?"FULL":"NO_TARGET";
            default -> "NO_TARGET";
        };
        d.projectInteractionError(reason,match.matchTick()+40);
    }
    private double range(ServerPlayer player,Target target) {
        if(target.kind()==Kind.CORE) { var d=data(player); if(d.key()!=null) return rules.card(d.inventory().find(d.key()).orElseThrow().definitionId()).params().get("range"); }
        return range(target);
    }
    private boolean eligible(ServerPlayer player,Target target) {
        if(!match.standing(player) || match.bags().busy(player.getUUID()) || match.trades().busy(player.getUUID()) || !match.phase().acceptsActions() || !player.getMainHandItem().isEmpty()) return false;
        EchoRoleData data=data(player);
        long now=match.matchTick();
        return switch(target.kind()) {
            case RESCUE -> match.combat().canRescue(player,UUID.fromString(target.id()));
            case FREE_EXIT -> EchoGateService.box(map.coreArea()).contains(player.position());
            case DROP -> drops.byId(UUID.fromString(target.id())).filter(d -> !drops.missing(d))
                    .map(d -> d.kind()==EchoDropService.Kind.CRYSTAL?carrier(player)&&data.freeCargoSlot()>=0:d.kind()==EchoDropService.Kind.BLACK_CRYSTAL?canPickupBlack(player,d.instanceId()):match.bags().canPickup(player,d.instanceId())).orElse(false);
            case BLACK_NODE -> match.phase()==EchoPhase.OPERATIONS && match.contracts().smuggler(player.getUUID()) && data.freeCargoSlot()>=0 && blackCount(player)<2 && black.available(target.id(),now);
            case BLACK_DROPOFF -> match.contracts().dropoff(player.getUUID(),target.id()) && blackCount(player)>0;
            case CRYSTAL -> match.phase()==EchoPhase.OPERATIONS && carrier(player) && data.freeCargoSlot()>=0 && now>=nodeReady.get(target.id());
            case BEACON -> match.phase()==EchoPhase.OPERATIONS && (carrier(player)?!data.cargo().isEmpty()&&beacons.get(target.id()).canSubmit()
                    : match.faction(player.getUUID())==EchoFaction.INTERCEPTOR&&beacons.get(target.id()).canStall(now));
            case ALTAR -> match.phase()==EchoPhase.OPERATIONS && keys!=null && keys.at(target.id()).filter(id -> match.bags().canPickup(player,id)).isPresent();
            case CORE -> match.phase()==EchoPhase.OPERATIONS && data.key()!=null && !data.inventory().locked(data.key()) && !gates.state().coreOpen(now);
            case CRATE -> match.phase()==EchoPhase.OPERATIONS && now>=crateReady.get(target.id());
        };
    }
    private double range(Target target) { return target.kind()==Kind.DROP?2:target.kind()==Kind.RESCUE?rules.combat().rescueRange():3; }
    public static boolean reachable(ServerPlayer player,Vec3 point,double range) {
        return EchoMachineInteractions.reachable(player.level(),player,point,range);
    }
    public void tickOperations() {
        long now=match.matchTick();
        tickRepairs(now);
        for(var action:actions.advance(now,action -> {
            ServerPlayer player=level.getServer().getPlayerList().getPlayer(action.player());
            Target target=resolve(action.target());
            return player!=null && target!=null && eligible(player,target)
                    && reachable(player,target.pos(),range(player,target))
                    && Math.pow(player.getX()-action.startX(),2)+Math.pow(player.getZ()-action.startZ(),2)<=0.75*0.75;
        })) {
            ServerPlayer player=level.getServer().getPlayerList().getPlayer(action.player());
            Target target=resolve(action.target());
            if(player!=null && target!=null && eligible(player,target)) complete(player,target);
        }
        if(match.phase()==EchoPhase.OPERATIONS) {
            keys.due(now).ifPresent(altar -> { if(spawnKey(altar)) announce("echo.goal.key_supply",altar); });
            for(var beacon:beacons.entrySet()) {
                Target target=handles.get("BEACON:"+beacon.getKey());
                // 护送人数只算真正站在信标范围内的站立托运者；reachable 是「目标点可达」，
                // 与「玩家站得近」无关，不能用来判断护送，否则隔着半张图也算护送。
                int escorts=(int)match.onlinePlayers().stream().filter(p -> match.standing(p)&&carrier(p)
                        && p.position().distanceToSqr(target.pos())<=rules.beacon().escortRange()*rules.beacon().escortRange()
                        && EchoGateService.box(map.coreArea()).contains(p.position())==EchoGateService.box(map.coreArea()).contains(target.pos())).count();
                if(beacon.getValue().tick(now,escorts)) {
                    announce("echo.goal.beacon_active",beacon.getKey()); burst(target.pos());
                }
                // 占位圈：每 20 tick 刷新一次，提示需要站立的范围；被迟滞时由客户端改色。
                if(now%20==0 && !beacon.getValue().active())
                    io.wifi.starrailexpress.util.ParticleFx.sendCustom(level,
                            org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFxIds.BEACON_RING,
                            target.pos(),20,(float)rules.beacon().escortRange(),
                            beacon.getValue().stalled(now)?1F:0F);
            }
        }
        for(var drop:drops.all()) if(now>=drop.expiresAt()) recycle(drop);
        if(now%20==0) drops.refresh(now);
        gates.tick(now);
        for(ServerPlayer player:match.onlinePlayers()) if(match.standing(player)) updateCarrier(player);
        if(now%5==0) feedback();
    }
    private void complete(ServerPlayer player,Target target) {
        long now=match.matchTick(); EchoRoleData data=data(player);
        switch(target.kind()) {
            case RESCUE -> match.combat().rescue(player,UUID.fromString(target.id()));
            case CRYSTAL, BLACK_NODE -> startRepair(player,target);
            case BLACK_DROPOFF -> {
                UUID id=data.cargo().stream().filter(black::black).findFirst().orElseThrow();
                removeCargo(player,id,Location.consumed()); black.consume(id); resources.remove(id); data.projectBlackCargo(blackCount(player));
                match.contracts().delivered(player.getUUID(),id); nearby(target.pos(),SoundEvents.RESPAWN_ANCHOR_CHARGE,48);
            }
            case BEACON -> {
                EchoBeaconState beacon=beacons.get(target.id());
                if(carrier(player)) {
                    // 已启动或已满料一律拒绝：不能因为投影慢一帧就把多余晶体塞进去。
                    if(beacon.active() || !beacon.canSubmit()) throw new IllegalStateException("BEACON_FULL");
                    UUID crystal=data.cargo().getFirst(); removeCargo(player,crystal,Location.consumed());
                    if(!beacon.submit()) throw new IllegalStateException("信标提交计划失效");
                    resources.remove(crystal); match.recordDelivery(player.getUUID());
                    reward(player,PackQuality.RARE,"submit:"+player.getUUID()+":"+target.id());
                    match.economy().award(player,org.agmas.noellesroles.game.modes.echo.economy.EchoEconomy.Source.SUBMIT,target.id());
                    announce("echo.goal.submitted",target.id());
                    if(!match.cards().consumeSilence(player.getUUID())) {
                        burst(target.pos());
                        submissionHighlights.put(target.id(),now+200);
                        EchoAudio.submission(match,target.pos());
                    }
                } else if(beacon.stall(now,rules.beacon().stallTicks())) {
                    reward(player,PackQuality.BLACK_MARKET,"stall:"+target.id());
                    match.economy().award(player,org.agmas.noellesroles.game.modes.echo.economy.EchoEconomy.Source.STALL,target.id()); announce("echo.goal.stalled",target.id()); burst(target.pos()); nearby(target.pos(),SoundEvents.RESPAWN_ANCHOR_CHARGE,24);
                }
            }
            case ALTAR -> keys.at(target.id()).flatMap(id -> drops.all().stream().filter(d -> d.instanceId().equals(id)).findFirst()).ifPresent(drop -> pickup(player,drop));
            case DROP -> drops.byId(UUID.fromString(target.id())).ifPresent(drop -> pickup(player,drop));
            case CORE -> {
                UUID key=data.key();
                var card=rules.card(data.inventory().find(key).orElseThrow().definitionId());
                if(cardEffect(player,card,target.pos())) {
                    data.inventory().remove(key,Location.consumed(),data.inventory().revision(),null);
                    match.contracts().keyUsed(player,key);
                    resources.remove(key); data.projectInventory();
                }
            }
            case FREE_EXIT -> EchoGateService.teleport(player,map.coreGate().freeExit().outsideSafe());
            case CRATE -> {
                var crate=crate(target.id());
                reward(player,crate.quality().equals("COMMON")?PackQuality.COMMON:PackQuality.BLACK_MARKET,"crate:"+target.id()+":"+now);
                crateReady.put(target.id(),crate.repeatable()?now+rules.logistics().crateRecoveryTicks():Long.MAX_VALUE); burst(target.pos());
            }
        }
    }
    private EchoMapConfig.Crate crate(String id) { return map.supplyCrates().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow(); }
    /**
     * 采集改为修机小游戏：先下发一轮圆环判定，命中才真正产出货物。
     *
     * <p>服务端不信任客户端上报的位置（见 {@link org.agmas.noellesroles.game.modes.echo.repair.EchoRepairCheck}）；
     * 取消、超时或失手都不消耗点位，也不进入节点恢复，玩家可以立刻重试。
     */
    private void startRepair(ServerPlayer player,Target target) {
        if(repairs.containsKey(player.getUUID())) return;
        var clazz=match.classState(player.getUUID());
        int zoneBonus=clazz==null?0:clazz.zoneBonus();
        double accidentScale=clazz==null?1D:clazz.accidentScale();
        long now=match.matchTick();
        var check=org.agmas.noellesroles.game.modes.echo.repair.EchoRepairCheck.begin(random,now,zoneBonus,accidentScale);
        String checkId=UUID.randomUUID().toString();
        repairs.put(player.getUUID(),new PendingRepair(check,checkId,target));
        var view=check.view();
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new org.agmas.noellesroles.game.modes.echo.repair.EchoRepairPayload(match.id(),
                        UUID.fromString(checkId),target.id(),view.speed(),view.zoneStart(),view.zoneWidth(),
                        view.accident(),view.startedAt(),view.timeoutTicks()));
        data(player).projectAction(null);
        actions.cancel(player.getUUID());
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.PLAYERS,0.5F,1.2F);
    }

    /**
     * 结算一次修机判定。
     *
     * @param position 客户端上报的指针位置；{@code -1} 表示主动放弃
     */
    public void submitRepair(ServerPlayer player,String checkId,int position) {
        var pending=repairs.get(player.getUUID());
        if(pending==null || !pending.checkId().equals(checkId)) return;
        repairs.remove(player.getUUID());
        long now=match.matchTick();
        if(position<0) { data(player).projectInteractionError("CANCELLED",now+40); return; }
        var result=pending.check().submit(now,position);
        if(result==org.agmas.noellesroles.game.modes.echo.repair.EchoRepairCheck.Result.HIT) {
            finishRepair(player,pending.target());
            return;
        }
        data(player).projectInteractionError(result.name(),now+40);
        player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(),SoundSource.PLAYERS,0.6F,0.7F);
        // 采集故障会泄露位置：附近追捕者收到一次红色圆圈声纹（只给方向，不给坐标）。
        faultPulse(player);
    }

    /**
     * 采集故障声纹：向 {@link #FAULT_RANGE} 内的站立追捕者各发一次方向信号。
     *
     * <p>只发方向扇区，不带坐标与 UUID；客户端渲染成红色圆圈。
     */
    private void faultPulse(ServerPlayer source) {
        for(ServerPlayer hunter:match.onlinePlayers()) {
            if(hunter==source || match.faction(hunter.getUUID())!=EchoFaction.INTERCEPTOR) continue;
            if(!match.standing(hunter)) continue;
            if(hunter.distanceToSqr(source)>FAULT_RANGE*FAULT_RANGE) continue;
            match.cards().signals().pulse(hunter,source,"fault_pulse");
        }
    }

    /** 判定命中后真正产出货物；与旧的采集完成逻辑一致。 */
    private void finishRepair(ServerPlayer player,Target target) {
        long now=match.matchTick();
        EchoRoleData data=data(player);
        if(target.kind()==Kind.BLACK_NODE) {
            UUID id=UUID.randomUUID(); ledger.register(match.id(),id,Location.cargo(player.getUUID(),data.cargo().size()));
            black.collect(player.getUUID(),target.id(),id,now);
            resources.put(id,new Resource(EchoDropService.Kind.BLACK_CRYSTAL,Long.MAX_VALUE));
            data.addCargo(id); data.projectBlackCargo(blackCount(player));
            nearby(target.pos(),SoundEvents.AMETHYST_BLOCK_CHIME,24);
            return;
        }
        UUID id=UUID.randomUUID(); ledger.register(match.id(),id,Location.cargo(player.getUUID(),data.cargo().size()));
        resources.put(id,new Resource(EchoDropService.Kind.CRYSTAL,now+rules.signals().cargoIntervalTicks()));
        data.addCargo(id);
        nodeReady.put(target.id(),now+rules.logistics().nodeRecoveryTicks());
        if(!match.cards().consumeSilence(player.getUUID())) {
            burst(target.pos()); nearby(target.pos(),SoundEvents.AMETHYST_BLOCK_BREAK,24);
        }
        if(random.nextDouble()<rules.logistics().bonusPackChance()) reward(player,PackQuality.COMMON,"collect:"+id);
        match.economy().award(player,org.agmas.noellesroles.game.modes.echo.economy.EchoEconomy.Source.REPAIR,null);
    }

    /** 断线、倒地、离开模式或换局时清掉未完成的判定。 */
    public void cancelRepair(UUID owner) { repairs.remove(owner); }

    /** 每 tick 清掉超时判定，避免玩家挂着界面。 */
    private void tickRepairs(long now) {
        if(repairs.isEmpty()) return;
        repairs.entrySet().removeIf(entry -> {
            if(!entry.getValue().check().expired(now)) return false;
            var player=level.getServer().getPlayerList().getPlayer(entry.getKey());
            if(player!=null) data(player).projectInteractionError("EXPIRED",now+40);
            return true;
        });
    }

    private void removeCargo(ServerPlayer player,UUID instance,Location destination) { removeCargo(player.getUUID(),instance,destination); }
    private void removeCargo(UUID owner,UUID instance,Location destination) {
        List<UUID> cargo=match.cargo(owner).items(); int index=cargo.indexOf(instance);
        if(index<0) throw new IllegalStateException("货物所有权不符");
        List<EchoResourceLedger.Move> moves=new ArrayList<>();
        moves.add(new EchoResourceLedger.Move(instance,Location.cargo(owner,index),destination));
        for(int n=index+1;n<cargo.size();n++) moves.add(new EchoResourceLedger.Move(cargo.get(n),Location.cargo(owner,n),Location.cargo(owner,n-1)));
        ledger.batchMove(match.id(),moves); match.cargo(owner).remove(instance); projectCargo(owner);
    }
    private void projectCargo(UUID owner) {
        var player=match.connectedPlayer(owner);
        if(player!=null) RoleData.getOptional(EchoRoleData.class,player).filter(d -> match.id().equals(d.matchId())).ifPresent(d -> {
            d.projectBlackCargo((int)match.cargo(owner).items().stream().filter(black::black).count()); d.projectInventory();
        });
    }
    private void pickup(ServerPlayer player,EchoDropService.Drop drop) {
        EchoRoleData data=data(player);
        if(drop.kind()!=EchoDropService.Kind.CRYSTAL && drop.kind()!=EchoDropService.Kind.BLACK_CRYSTAL) {
            if(!match.bags().canPickup(player,drop.instanceId())) return;
            match.bags().pickup(player,drop);
        } else {
            if(drop.kind()==EchoDropService.Kind.BLACK_CRYSTAL?!canPickupBlack(player,drop.instanceId()):!carrier(player)||data.freeCargoSlot()<0) return;
            ledger.move(match.id(),drop.instanceId(),Location.ground(drop.dropId()),Location.cargo(player.getUUID(),data.cargo().size()));
            drops.remove(drop.dropId()); data.addCargo(drop.instanceId());
        }
        data.projectBlackCargo(blackCount(player));
        if(keys!=null) keys.release(drop.altar(),drop.instanceId());
        player.displayClientMessage(Component.translatable("echo.goal.picked_up"),true);
    }
    public boolean dropCarried(ServerPlayer player,boolean key,Vec3 point) { return dropCarried(player.getUUID(),key,point); }
    public boolean dropCarried(UUID owner,boolean key,Vec3 point) {
        var inventory=match.bags().inventory(owner);
        UUID instance=key?inventory.items().stream().filter(org.agmas.noellesroles.game.modes.echo.inventory.EchoResourceInstance::key)
                .map(org.agmas.noellesroles.game.modes.echo.inventory.EchoResourceInstance::instanceId).findFirst().orElse(null):match.cargo(owner).items().stream().findFirst().orElse(null);
        if(instance==null || key && inventory.locked(instance)) return false;
        EchoDropService.Kind kind=key?EchoDropService.Kind.KEY:black.black(instance)?EchoDropService.Kind.BLACK_CRYSTAL:EchoDropService.Kind.CRYSTAL;
        var created=drops.spawn(instance,kind,point,match.matchTick()+(key?rules.logistics().keyGroundTicks():kind==EchoDropService.Kind.BLACK_CRYSTAL?rules.logistics().blackGroundTicks():rules.logistics().crystalGroundTicks()),null).orElseThrow();
        try {
            Location ground=Location.ground(created.dropId());
            if(key) inventory.remove(instance,ground,inventory.revision(),null);
            else removeCargo(owner,instance,ground);
        } catch(RuntimeException failure) { drops.remove(created.dropId()); throw failure; }
        if(key) { match.contracts().invalidate(instance); var player=match.connectedPlayer(owner); if(player!=null) match.bags().changed(player); }
        else projectCargo(owner);
        actions.cancel(owner); return true;
    }
    private boolean spawnKey(String altar) {
        if(match.bags().keyCount()>=match.playerCount()+3 || keys.at(altar).isPresent()) return false;
        Vec3 pos=handles.get("ALTAR:"+altar).pos().add(0,1,0); UUID instance=UUID.randomUUID();
        var drop=drops.spawn(instance,EchoDropService.Kind.KEY,pos,match.matchTick()+rules.logistics().keyGroundTicks(),altar);
        if(drop.isEmpty()) return false;
        ledger.register(match.id(),instance,Location.ground(drop.get().dropId()));
        match.bags().registerKey(instance);
        resources.put(instance,new Resource(EchoDropService.Kind.KEY,match.matchTick()+rules.signals().keyIntervalTicks()));
        if(!keys.occupy(altar,instance)) throw new IllegalStateException("祭坛占位冲突");
        return true;
    }
    private void recycle(EchoDropService.Drop drop) {
        ledger.move(match.id(),drop.instanceId(),Location.ground(drop.dropId()),Location.recycled());
        if(drop.kind()==EchoDropService.Kind.BLACK_CRYSTAL) black.recycle(drop.instanceId(),match.matchTick());
        resources.remove(drop.instanceId()); drops.remove(drop.dropId()); if(keys!=null) keys.release(drop.altar(),drop.instanceId());
        if(match.phase()!=EchoPhase.OPERATIONS) return;
        if(drop.kind()==EchoDropService.Kind.KEY) keys.emptyAltar().ifPresent(this::spawnKey);
        else if(drop.kind()==EchoDropService.Kind.CRYSTAL) nodeReady.keySet().stream().min(Comparator.comparingDouble(id -> handles.get("CRYSTAL:"+id).pos().distanceToSqr(drop.pos())))
                .ifPresent(id -> nodeReady.put(id,match.matchTick()));
    }
    private void reward(ServerPlayer player,PackQuality quality,String fact) { match.bags().grant(player,quality,fact); }
    private void updateCarrier(ServerPlayer player) {
        EchoRoleData data=data(player);
        data.projectBlackCargo(blackCount(player));
        match.cards().updateSpeed(player);
        long now=match.matchTick();
        long cargoDue=data.cargo().stream().filter(this::ordinaryCargo).map(resources::get).filter(Objects::nonNull).mapToLong(r -> r.nextPulse).min().orElse(Long.MAX_VALUE);
        if(now>=cargoDue) {
            directionSignal(player,"echo.signal.cargo");
            for(UUID id:data.cargo()) if(ordinaryCargo(id)) resources.get(id).nextPulse=now+rules.signals().cargoIntervalTicks();
        }
        Resource key=data.key()==null?null:resources.computeIfAbsent(data.key(),id -> new Resource(EchoDropService.Kind.KEY,now+rules.signals().keyIntervalTicks()));
        if(key!=null && now>=key.nextPulse) { if(!match.cards().suppressKeyPulse(player.getUUID())) directionSignal(player,"echo.signal.key"); key.nextPulse=now+rules.signals().keyIntervalTicks(); }
        data.projectKeyPulse(key==null?0:key.nextPulse);
        if(match.phase()==EchoPhase.OPERATIONS && EchoGateService.box(map.coreArea()).contains(player.position())) reward(player,PackQuality.RARE,"core:"+player.getUUID());
    }
    private void directionSignal(ServerPlayer source,String key) {
        for(ServerPlayer receiver:match.onlinePlayers()) if(match.standing(receiver) && !receiver.getUUID().equals(source.getUUID())
                && match.faction(receiver.getUUID())==EchoFaction.INTERCEPTOR && receiver.distanceToSqr(source)<=rules.signals().directionRange()*rules.signals().directionRange()) {
            double angle=Math.atan2(source.getZ()-receiver.getZ(),source.getX()-receiver.getX());
            int sector=Math.floorMod((int)Math.round(angle/(Math.PI/4)),8);
            receiver.sendSystemMessage(Component.translatable(key,Component.translatable("echo.direction."+sector)));
            match.cards().signals().pulse(receiver,source,key.equals("echo.signal.key")?"key_pulse":"cargo_pulse");
        }
    }
    public void beginEvacuation() { actions.cancelAll(); dwell.clear(); gates.beginEvacuation(match.matchTick()); }
    public void tickEvacuation() {
        if(!gates.state().evacuationOpen(match.matchTick())) { dwell.clear(); return; }
        for(ServerPlayer player:match.onlinePlayers()) {
            UUID uuid=player.getUUID();
            if(!match.standing(player) || !carrier(player) || !EchoGateService.box(map.evacuation().zone()).contains(player.position())
                    || actions.current(uuid).isPresent()) { dwell.remove(uuid); continue; }
            long entered=dwell.computeIfAbsent(uuid,k -> match.matchTick());
            if(match.matchTick()-entered>=rules.gate().evacuationDwellTicks()) {
                actions.cancel(uuid);
                while(!data(player).cargo().isEmpty()) if(!dropCarried(player,false,EchoGateService.vec(map.evacuation().exitDropAnchor()))) throw new IllegalStateException("撤离货物掉落失败");
                if(data(player).key()!=null && !dropCarried(player,true,EchoGateService.vec(map.evacuation().exitDropAnchor()))) throw new IllegalStateException("撤离钥匙掉落失败");
                match.markEscaped(uuid); dwell.remove(uuid);
                var speed=player.getAttribute(Attributes.MOVEMENT_SPEED); if(speed!=null) speed.removeModifier(CARGO_SPEED);
                player.setGameMode(GameType.SPECTATOR); GameUtilsBridge.spectator(player);
                EchoGateService.teleport(player,map.spectatorPos()); announce("echo.goal.escaped",player.getGameProfile().getName());
            }
        }
    }
    public boolean overload(ServerPlayer player) {
        if(!match.standing(player) || match.faction(player.getUUID())!=EchoFaction.INTERCEPTOR || match.phase()!=EchoPhase.EVACUATION
                || !reachable(player,EchoGateService.vec(map.evacuation().controlPos()),rules.gate().overloadRange())) return false;
        if(!gates.overload(match.matchTick())) return false;
        actions.cancel(player.getUUID()); dwell.clear(); announce("echo.goal.overload",""); return true;
    }
    private void feedback() {
        for(ServerPlayer player:match.onlinePlayers()) if(match.standing(player)) {
            var action=actions.current(player.getUUID());
            data(player).projectActionView(action.map(a -> {
                var target=resolve(a.target());
                return target==null?null:new org.agmas.noellesroles.game.modes.echo.action.EchoActionView(a.id(),target.kind().name(),a.startedAt(),a.duration());
            }).orElse(null));
        }
    }
    private record HintTarget(Target target, EchoMachineView.Type type, Vec3 pos, String id) { }

    /** 只在近距且无遮挡时投影一台机器；优先准星目标，不广播整张地图或私人状态。 */
    private EchoMachineView machineHint(ServerPlayer player) {
        if (!match.present(player) || player.isSpectator() || !data(player).life().potentiallyEscapable()) return null;
        var candidates = new ArrayList<HintTarget>();
        for (var target : handles.values()) {
            var type = switch (target.kind()) {
                case CRYSTAL -> EchoMachineView.Type.CRYSTAL;
                case BEACON -> map.beacons().stream().anyMatch(b -> b.id().equals(target.id()) && b.region().equals("core"))
                        ? EchoMachineView.Type.CORE_BEACON : EchoMachineView.Type.BEACON;
                case ALTAR -> EchoMachineView.Type.ALTAR;
                case CORE -> EchoMachineView.Type.CORE_CONSOLE;
                case FREE_EXIT -> EchoMachineView.Type.FREE_EXIT;
                case CRATE -> crate(target.id()).quality().equals("BLACK_MARKET") ? EchoMachineView.Type.BLACK_MARKET : EchoMachineView.Type.SUPPLY;
                case BLACK_NODE -> EchoMachineView.Type.BLACK_NODE;
                case BLACK_DROPOFF -> EchoMachineView.Type.BLACK_DROPOFF;
                default -> null;
            };
            if (type != null) candidates.add(new HintTarget(target, type, target.pos(), target.id()));
        }
        candidates.add(new HintTarget(null, EchoMachineView.Type.BROKER_EXIT, EchoGateService.vec(map.brokerExit().pos()), map.brokerExit().id()));
        candidates.add(new HintTarget(null, EchoMachineView.Type.EVACUATION_CONSOLE, EchoGateService.vec(map.evacuation().controlPos()), "evacuation"));
        Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
        var hit = level.clip(new ClipContext(eye, eye.add(look.scale(4.5)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        HintTarget selected = null;
        double best = Double.MAX_VALUE;
        for (var candidate : candidates) {
            BlockPos pos = BlockPos.containing(candidate.pos());
            if (!level.hasChunkAt(pos) || level.getBlockState(pos).isAir()) continue;
            Vec3 center = EchoMachineInteractions.aim(level,candidate.pos(),eye), delta = center.subtract(eye);
            if (delta.lengthSqr() > 4.5 * 4.5 || look.dot(delta.normalize()) < 0.25) continue;
            var visible = level.clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (visible.getType() != HitResult.Type.MISS && !EchoMachineInteractions.root(level,visible.getBlockPos()).equals(pos)) continue;
            double score = delta.lengthSqr() - (hit.getType() == HitResult.Type.BLOCK && EchoMachineInteractions.root(level,hit.getBlockPos()).equals(pos) ? 100 : 0);
            if (score < best) { best = score; selected = candidate; }
        }
        if (selected == null) return null;
        var status = machineStatus(player, selected);
        int amount = 0, required = 0, progress = 0, remaining = 0;
        var t = selected.target(); long now = match.matchTick();
        if (t != null) {
            if (t.kind() == Kind.BEACON) {
                var b = beacons.get(t.id()); amount = b.delivered(); required = b.requiredCrystals(); progress = b.progressPermille();
                long until = b.stalledUntil();
                remaining = hintRemaining(until, now);
            } else if (t.kind() == Kind.CRYSTAL) remaining = hintRemaining(nodeReady.get(t.id()), now);
            else if (t.kind() == Kind.CRATE && crateReady.get(t.id()) != Long.MAX_VALUE) remaining = hintRemaining(crateReady.get(t.id()), now);
            else if (t.kind() == Kind.CORE) remaining = hintRemaining(gates.state().coreOpenUntil(), now);
        } else if (selected.type() == EchoMachineView.Type.EVACUATION_CONSOLE)
            remaining = hintRemaining(gates.state().evacuationClosedUntil(), now);
        BlockPos pos = BlockPos.containing(selected.pos());
        return new EchoMachineView(pos.getX(), pos.getY(), pos.getZ(), selected.type(), selected.id(), status, amount, required, progress, remaining);
    }
    private static int hintRemaining(long deadline, long now) { return (int) Math.max(0, Math.min(720000, deadline - now)); }

    /** 只读当前事实；不调用 request/refuse，查看机器绝不会创建动作或改变错误状态。 */
    private EchoMachineView.Status machineStatus(ServerPlayer player, HintTarget hint) {
        var d = data(player); long now = match.matchTick();
        if (io.wifi.starrailexpress.cca.SREGameTimeComponent.KEY.get(level).isTimeFrozen()) return EchoMachineView.Status.PAUSED;
        if (!d.life().standing()) return EchoMachineView.Status.DOWNED;
        if (d.controlled()) return EchoMachineView.Status.CONTROLLED;
        if (match.bags().busy(player.getUUID()) || match.trades().busy(player.getUUID())) return EchoMachineView.Status.BUSY;
        if (!match.phase().acceptsActions()) return EchoMachineView.Status.WRONG_PHASE;
        if (hint.type() == EchoMachineView.Type.EVACUATION_CONSOLE) {
            if (match.phase() != EchoPhase.EVACUATION) return EchoMachineView.Status.EVAC_WAIT;
            if (match.faction(player.getUUID()) == EchoFaction.INTERCEPTOR)
                return gates.state().overloadUsed() ? EchoMachineView.Status.OVERLOAD_USED : EchoMachineView.Status.OVERLOAD_READY;
            if (!carrier(player)) return EchoMachineView.Status.WRONG_FACTION;
            return gates.state().evacuationOpen(now) ? EchoMachineView.Status.EVAC_OPEN : EchoMachineView.Status.STALLED;
        }
        if (hint.type() == EchoMachineView.Type.BROKER_EXIT) {
            var contract = match.contracts().state(player.getUUID());
            if (contract == null) return EchoMachineView.Status.WRONG_FACTION;
            if (!contract.mayRetire()) return EchoMachineView.Status.CONTRACT_REQUIRED;
            return match.contracts().retireReady(player.getUUID()) ? EchoMachineView.Status.RETIRE_READY : EchoMachineView.Status.RETIRE_WAIT;
        }
        var target = hint.target();
        if (!reachable(player, target.pos(), range(player, target))) return EchoMachineView.Status.OUT_OF_RANGE;
        if (!player.getMainHandItem().isEmpty()) return EchoMachineView.Status.EMPTY_HAND;
        if (target.kind() != Kind.FREE_EXIT && target.kind() != Kind.BLACK_DROPOFF && match.phase() != EchoPhase.OPERATIONS)
            return EchoMachineView.Status.WRONG_PHASE;
        return switch (target.kind()) {
            case CRYSTAL -> !carrier(player) ? EchoMachineView.Status.WRONG_FACTION : d.freeCargoSlot() < 0 ? EchoMachineView.Status.CARGO_FULL
                    : now < nodeReady.get(target.id()) ? EchoMachineView.Status.COOLDOWN : EchoMachineView.Status.READY;
            case BLACK_NODE -> !match.contracts().smuggler(player.getUUID()) ? EchoMachineView.Status.CONTRACT_REQUIRED
                    : d.freeCargoSlot() < 0 || blackCount(player) >= 2 ? EchoMachineView.Status.CARGO_FULL
                    : black.available(target.id(), now) ? EchoMachineView.Status.READY : EchoMachineView.Status.COOLDOWN;
            case BLACK_DROPOFF -> !match.contracts().smuggler(player.getUUID()) ? EchoMachineView.Status.CONTRACT_REQUIRED
                    : !match.contracts().dropoff(player.getUUID(), target.id()) ? EchoMachineView.Status.WRONG_DROPOFF
                    : blackCount(player) == 0 ? EchoMachineView.Status.NO_RESOURCE : EchoMachineView.Status.READY;
            case BEACON -> {
                var b = beacons.get(target.id());
                if (b.active()) yield EchoMachineView.Status.ACTIVE;
                if (carrier(player)) yield !b.canSubmit() ? now < b.stalledUntil() ? EchoMachineView.Status.STALLED : EchoMachineView.Status.CHARGING
                        : d.cargo().isEmpty() ? EchoMachineView.Status.NO_RESOURCE : EchoMachineView.Status.READY;
                if (match.faction(player.getUUID()) != EchoFaction.INTERCEPTOR) yield EchoMachineView.Status.WRONG_FACTION;
                yield b.canSubmit() ? EchoMachineView.Status.NO_RESOURCE : b.canStall(now) ? EchoMachineView.Status.READY : EchoMachineView.Status.COOLDOWN;
            }
            case ALTAR -> d.key() != null ? EchoMachineView.Status.KEY_LIMIT : d.inventory().emptySlot() < 0 ? EchoMachineView.Status.FULL
                    : keys != null && keys.at(target.id()).isPresent() ? EchoMachineView.Status.READY : EchoMachineView.Status.EMPTY;
            case CORE -> gates.state().coreOpen(now) ? EchoMachineView.Status.CORE_OPEN : d.key() == null ? EchoMachineView.Status.NO_RESOURCE
                    : d.inventory().locked(d.key()) ? EchoMachineView.Status.KEY_LOCKED : EchoMachineView.Status.READY;
            case CRATE -> crateReady.get(target.id()) == Long.MAX_VALUE ? EchoMachineView.Status.EMPTY
                    : now < crateReady.get(target.id()) ? EchoMachineView.Status.COOLDOWN : EchoMachineView.Status.READY;
            case FREE_EXIT -> EchoGateService.box(map.coreArea()).contains(player.position()) ? EchoMachineView.Status.READY : EchoMachineView.Status.WRONG_PHASE;
            default -> EchoMachineView.Status.WRONG_PHASE;
        };
    }
    public void publish(boolean finished) {
        var timer=io.wifi.starrailexpress.cca.SREGameTimeComponent.KEY.get(level);
        var state=gates.state();
        var entries=beacons.entrySet().stream().map(e -> {
            var b=e.getValue();
            var p=handles.get("BEACON:"+e.getKey()).pos();
            return new EchoPublicState.Beacon(e.getKey(),b.delivered(),b.requiredCrystals(),b.progressPermille(),b.active(),b.stalledUntil(),new EchoPublicState.Point(p.x,p.y,p.z),submissionHighlights.getOrDefault(e.getKey(),0L));
        }).toList();
        var view=new EchoPublicState(finished?EchoPhase.FINISHED:match.phase(),match.matchTick(),Math.max(0,timer.getTime()),timer.isTimeFrozen(),Math.toIntExact(match.escapedCount()),match.escapeQuota(),
                state.coreOpen(match.matchTick()),state.coreOpenUntil(),state.evacuationOpen(match.matchTick()),state.evacuationClosedUntil(),state.overloadUsed(),entries);
        var packet=new EchoPublicPayload(match.id(),level.dimension().location(),view);
        for(var player:level.players()) net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,packet);
        for (var player : match.onlinePlayers()) if (match.present(player))
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                    new EchoMachinePayload(match.id(), level.dimension().location(), finished ? null : machineHint(player)));
        // 首次/重连与低频刷新走已有任务点协议，绕过旧地图扫描缓存；结束时显式清空。
        for(var player:level.players()) if(finished || taskRecipients.put(player.getUUID(),player)!=player || level.getGameTime()%100==0)
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                    new org.agmas.noellesroles.packet.ScanAllTaskPointsPayload(finished?new HashMap<>():taskPoints()));
        if(finished) taskRecipients.clear();
    }
    private void announce(String key,String detail) {
        Component message=Component.translatable(key,detail);
        match.onlinePlayers().forEach(p -> p.sendSystemMessage(message)); SRE.REPLAY_MANAGER.recordCustomEvent(message);
    }
    private void burst(Vec3 pos) { ParticleFx.sphere(level,ParticleTypes.END_ROD,pos.add(0,1,0),16,0.5,0.02); }
    private void nearby(Vec3 pos,SoundEvent sound,int range) {
        EchoAudio.nearby(match,pos,sound,range,0.7F);
    }
    public void cleanup() {
        if(closed) return; closed=true; actions.cancelAll(); dwell.clear(); drops.cleanup();
        try { publish(true); } catch(RuntimeException failure) { org.agmas.noellesroles.Noellesroles.LOGGER.warn("回声公开HUD清理通知失败 {}",match.id(),failure); }
        for(ServerPlayer player:match.onlinePlayers()) {
            var speed=player.getAttribute(Attributes.MOVEMENT_SPEED); if(speed!=null) speed.removeModifier(CARGO_SPEED);
        }
        gates.cleanup();
    }
    /** 不经死亡流程的撤离也必须加入旁观语音隔离。 */
    private static final class GameUtilsBridge {
        static void spectator(ServerPlayer player) {
            io.wifi.starrailexpress.game.GameUtils.normalizeSpectatorFlightAbilities(player);
            if(!io.wifi.starrailexpress.compat.TrainVoicePlugin.isVoiceChatMissing()) io.wifi.starrailexpress.compat.TrainVoicePlugin.addPlayer(player.getUUID());
        }
    }
}
