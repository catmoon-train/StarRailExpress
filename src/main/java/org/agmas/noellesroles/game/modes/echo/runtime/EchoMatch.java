package org.agmas.noellesroles.game.modes.echo.runtime;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleMethodDispatcher;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnGameTrueStarted;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.modes.echo.EchoRoles;
import org.agmas.noellesroles.game.modes.echo.map.EchoMapConfig;
import org.agmas.noellesroles.game.modes.echo.objective.EchoObjectiveService;
import org.agmas.noellesroles.game.modes.echo.result.*;
import org.agmas.noellesroles.game.modes.echo.rules.EchoRosterPolicy;
import org.agmas.noellesroles.game.modes.echo.rules.EchoRules;
import org.agmas.noellesroles.game.modes.echo.state.*;
import org.agmas.noellesroles.game.modes.echo.combat.EchoCombatState;
import org.agmas.noellesroles.game.modes.echo.combat.EchoCombatService;
import org.agmas.noellesroles.game.modes.echo.rules.EchoWinPolicy;

import java.io.IOException;
import java.util.*;

/** 目标、战斗和终局在统一tick边界串行提交；成员状态独立于原版生命值。 */
public final class EchoMatch {
    private static final class Member {
        final UUID uuid;
        final String name;
        final EchoFaction faction;
        final EchoContract contract;
        EchoCombatState state;
        org.agmas.noellesroles.game.modes.echo.state.EchoSpecialization specialization;
        /** 追猎者职业；SABOTEUR 与其它阵营为 null，战斗服务据此走原版分支。 */
        org.agmas.noellesroles.game.modes.echo.classes.EchoClassState clazz;
        final EchoCargoState cargo=new EchoCargoState();
        final EchoConnectionState connection;
        ServerPlayer live;
        final Vec3 spawn;
        final org.agmas.noellesroles.game.modes.echo.state.EchoSkillState skills=new org.agmas.noellesroles.game.modes.echo.state.EchoSkillState();
        /** 职业运行时：连段、在飞蛛丝弹、自己布下的黏网。 */
        Vec3 lastSafe;
        int delivered;
        Member(ServerPlayer player,EchoFaction faction,EchoContract contract,Vec3 spawn,UUID matchId,org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig rules,org.agmas.noellesroles.game.modes.echo.state.EchoSpecialization specialization,org.agmas.noellesroles.game.modes.echo.classes.EchoClass clazz) {
            uuid=player.getUUID(); name=player.getGameProfile().getName(); this.faction=faction; this.contract=contract; lastSafe=spawn; this.spawn=spawn;
            live=player; connection=new EchoConnectionState(matchId); this.specialization=specialization;
            this.clazz=new org.agmas.noellesroles.game.modes.echo.classes.EchoClassState(clazz);
            state=new EchoCombatState(specialization.combat(rules));
        }
    }
    private record Intent(UUID player,Runnable command,boolean inventory) { }
    private final ServerLevel level;
    private final UUID id=UUID.randomUUID();
    private final EchoPreflight.Prepared prepared;
    private final EchoRoundClock clock=new EchoRoundClock();
    private final LinkedHashMap<UUID,Member> members=new LinkedHashMap<>();
    private final ArrayDeque<Intent> intents=new ArrayDeque<>();
    private final Map<UUID,Integer> inputCounts=new HashMap<>();
    private final EchoObjectiveService objectives;
    private final EchoCombatService combat;
    private final org.agmas.noellesroles.game.modes.echo.action.EchoRollService rolls;
    private final org.agmas.noellesroles.game.modes.echo.inventory.EchoBagService bags;
    private final org.agmas.noellesroles.game.modes.echo.cards.EchoCardEffects cards;
    private final org.agmas.noellesroles.game.modes.echo.trade.EchoTradeService trades;
    private org.agmas.noellesroles.game.modes.echo.economy.EchoEconomy economy;
    private org.agmas.noellesroles.game.modes.echo.economy.EchoShopService shop;
    private final org.agmas.noellesroles.game.modes.echo.contract.EchoContractService contracts;
    public org.agmas.noellesroles.game.modes.echo.contract.EchoContractService contracts() { return contracts; }
    public Set<UUID> participants() { return Collections.unmodifiableSet(members.keySet()); }
    public EchoContract contract(UUID owner) { var member=members.get(owner); return member==null?null:member.contract; }
    private EchoResultSnapshot result;
    private boolean activated,reportSaved,requestEvacuation;

    public EchoMatch(ServerLevel level,EchoPreflight.Prepared prepared,List<ServerPlayer> players) {
        this.level=level; this.prepared=prepared;
        Random random=new Random();
        var assignment=EchoRosterPolicy.assign(players.stream().map(ServerPlayer::getUUID).toList(),random);
        var contracts=EchoRosterPolicy.assignContracts(assignment,random);
        var game=SREGameWorldComponent.KEY.get(level);
        for(ServerPlayer player:players) game.addRole(player,EchoRoles.get(assignment.get(player.getUUID())),false);
        game.syncRolesNow();
        EnumMap<EchoFaction,Integer> indices=new EnumMap<>(EchoFaction.class);
        int civilianSpawnIndex=0;
        for(ServerPlayer player:players) {
            EchoFaction faction=assignment.get(player.getUUID()); EchoContract contract=contracts.get(player.getUUID());
            var role=EchoRoles.get(faction); RoleMethodDispatcher.onInit(role,level.getServer(),player);
            var data=RoleData.getNullable(EchoRoleData.class,player);
            if(data==null) throw new IllegalStateException("回声职业数据未初始化");
            data.bind(id,contract,EchoPhase.SAFE);
            data.configureRules(rules());
            List<EchoMapConfig.Spawn> spawns=faction==EchoFaction.INTERCEPTOR
                    ?prepared.map().interceptorSpawns():prepared.map().carrierSpawns();
            int index=indices.getOrDefault(faction,0); indices.put(faction,index+1);
            var spawn=spawns.get(faction==EchoFaction.INTERCEPTOR?index:civilianSpawnIndex++%spawns.size());
            var spec=org.agmas.noellesroles.game.modes.echo.state.EchoSpecialization.starter(faction);
            // 每阵营按出场序号轮流拿职业，保证同阵营的人打法不同。
            var clazz=org.agmas.noellesroles.game.modes.echo.classes.EchoClass.defaultFor(faction,index);
            members.put(player.getUUID(),new Member(player,faction,contract,new Vec3(spawn.x(),spawn.y(),spawn.z()),id,rules(),spec,clazz));
            data.projectSpecialization(spec);
            data.projectClass(clazz);
            data.attachCargo(cargo(player.getUUID()));
            player.teleportTo(level,spawn.x(),spawn.y(),spawn.z(),spawn.yaw(),spawn.pitch());
            player.sendSystemMessage(Component.translatable("echo.runtime.started"));
        }
        objectives=new EchoObjectiveService(this,level,prepared.map(),players.size());
        combat=new EchoCombatService(this,level);
        rolls=new org.agmas.noellesroles.game.modes.echo.action.EchoRollService(this,level);
        bags=new org.agmas.noellesroles.game.modes.echo.inventory.EchoBagService(this,level);
        cards=new org.agmas.noellesroles.game.modes.echo.cards.EchoCardEffects(this,level);
        trades=new org.agmas.noellesroles.game.modes.echo.trade.EchoTradeService(this,level);
        this.contracts=new org.agmas.noellesroles.game.modes.echo.contract.EchoContractService(this,level,prepared.map());
        economy=new org.agmas.noellesroles.game.modes.echo.economy.EchoEconomy();
        shop=new org.agmas.noellesroles.game.modes.echo.economy.EchoShopService(this,level,economy);
        for(ServerPlayer player:players) bags.grant(player,org.agmas.noellesroles.game.modes.echo.cards.EchoDraftService.PackQuality.COMMON,"starter:"+player.getUUID());
        for(ServerPlayer player:players) publishSpecialization(player);
        game.isSkillAvailable=false;
    }
    public UUID id() { return id; }
    /** 已平移到实战坐标的地图配置；开局运镜与预览共用同一份快照。 */
    public EchoMapConfig map() { return prepared.map(); }
    public org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig rules() { return prepared.rules().values(); }
    public String rulesHash() { return prepared.rules().hash(); }
    private final long specializationDeadline=org.agmas.noellesroles.game.modes.echo.rules.EchoRules.SAFE_TICKS-40;
    public long specializationDeadline() { return specializationDeadline; }
    public org.agmas.noellesroles.game.modes.echo.state.EchoSpecialization specialization(UUID id) {
        var member=members.get(id); return member==null?null:member.specialization;
    }
    /** SAFE 内且未到截止才可改；进入作业后一律锁定。 */
    public boolean specializationLocked() { return phase()!=EchoPhase.SAFE || matchTick()>=specializationDeadline; }
    public boolean selectSpecialization(ServerPlayer player,int option) {
        var member=members.get(player.getUUID());
        if(member==null || member.live!=player || specializationLocked()) return false;
        var options=org.agmas.noellesroles.game.modes.echo.state.EchoSpecialization.of(member.faction);
        if(option<0 || option>=options.size()) return false;
        var next=options.get(option);
        if(member.specialization==next) return true;
        member.specialization=next;
        member.state=new EchoCombatState(next.combat(rules()));
        var data=RoleData.getNullable(EchoRoleData.class,player);
        if(data!=null) data.projectSpecialization(next);
        bags.changed(player);
        return true;
    }
    /** 职业只在 SAFE 内可改，且只能换成同阵营的另外两个；进入作业后一律锁定。 */
    public boolean selectClass(ServerPlayer player,int option) {
        var member=members.get(player.getUUID());
        if(member==null || member.live!=player || specializationLocked()) return false;
        var pool=org.agmas.noellesroles.game.modes.echo.classes.EchoClass.of(member.faction);
        if(option<0 || option>=pool.size()) return false;
        var next=pool.get(option);
        if(member.clazz.clazz()==next) return true;
        // 换职业时清空冷却与连段，避免上一种打法的状态泄漏到新打法。
        member.clazz.setClass(next);
        RoleData.getOptional(EchoRoleData.class,player).ifPresent(data -> data.projectClass(next));
        return true;
    }
    /** 首次绑定与重连各发一次；只发给本人，不广播。 */
    public void publishSpecialization(ServerPlayer player) {
        var member=members.get(player.getUUID());
        if(member==null || player!=member.live || player.level()!=level) return;
        var data=RoleData.getNullable(EchoRoleData.class,player);
        if(data==null || !id.equals(data.matchId())) return;
        data.projectSpecialization(member.specialization);
        data.projectSpecializationWindow(specializationDeadline,specializationLocked());
        data.projectClass(member.clazz.clazz());
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                org.agmas.noellesroles.game.modes.echo.specialization.EchoSpecializationPayload.of(id,member.faction,
                        member.contract==null?"":member.contract.name(),specializationDeadline,member.specialization.id(),specializationLocked()));
    }
    private void lockSpecializations() {
        for(var member:members.values()) {
            var player=level.getServer().getPlayerList().getPlayer(member.uuid);
            if(player==null) continue;
            RoleData.getOptional(EchoRoleData.class,player).ifPresent(d -> d.projectSpecializationWindow(specializationDeadline,true));
        }
    }
    public EchoPhase phase() { return clock.phase(); }
    public long matchTick() { return clock.matchTick(); }
    public int playerCount() { return members.size(); }
    /** 保留协议字段，0 表示不限撤离人数；不再使用历史人数档配额。 */
    public int escapeQuota() { return 0; }
    public long escapedCount() { return members.values().stream().filter(m -> m.state.life()==EchoLifeState.ESCAPED).count(); }
    public EchoResultSnapshot result() { return result; }
    public EchoObjectiveService objectives() { return objectives; }
    public EchoFaction faction(UUID uuid) { Member member=members.get(uuid); return member==null?null:member.faction; }
    public EchoCombatService combat() { return combat; }
    public org.agmas.noellesroles.game.modes.echo.inventory.EchoBagService bags() { return bags; }
    public org.agmas.noellesroles.game.modes.echo.cards.EchoCardEffects cards() { return cards; }
    public org.agmas.noellesroles.game.modes.echo.trade.EchoTradeService trades() { return trades; }
    public EchoCombatState state(UUID id) { Member member=members.get(id); return member==null?null:member.state; }
    public EchoLifeState life(UUID id) { EchoCombatState state=state(id); return state==null?EchoLifeState.ELIMINATED:state.life(); }
    public org.agmas.noellesroles.game.modes.echo.state.EchoSkillState skills(UUID id) {
        var member=members.get(id); return member==null?null:member.skills;
    }
    /** 追猎者职业运行时；非成员返回 null。 */
    /** 该成员的追猎者职业；SABOTEUR 与其它阵营返回 null。 */
    /** 玩家本局职业运行时；非成员返回 null。 */
    public org.agmas.noellesroles.game.modes.echo.classes.EchoClassState classState(UUID id) {
        var member=members.get(id); return member==null?null:member.clazz;
    }
    /** 玩家本局职业；非成员返回 null。 */
    public org.agmas.noellesroles.game.modes.echo.classes.EchoClass playerClass(UUID id) {
        var member=members.get(id); return member==null?null:member.clazz.clazz();
    }
    /** 该成员当前专长的一次性效果是否在窗口内。 */
    public boolean skillPendingActive(UUID id,org.agmas.noellesroles.game.modes.echo.state.EchoSkill skill) {
        var s=skills(id); return s!=null && s.pendingActive(skill,matchTick());
    }
    public boolean skillBuffActive(UUID id,org.agmas.noellesroles.game.modes.echo.state.EchoSkill skill) {
        var s=skills(id); return s!=null && s.buffActive(skill,matchTick());
    }
    public boolean consumeSkillPending(UUID id,org.agmas.noellesroles.game.modes.echo.state.EchoSkill skill) {
        var s=skills(id); return s!=null && s.consumePending(skill,matchTick());
    }
    /** 右键专长信物：失败不进入冷却，也不消耗任何资源。 */
    public boolean useSkill(ServerPlayer player) {
        var member=members.get(player.getUUID());
        if(member==null || member.live!=player || !present(player) || !standing(player)) return false;
        if(!phase().acceptsActions()) return false;
        if(!state(player.getUUID()).canAct(matchTick())) return false;
        var skill=org.agmas.noellesroles.game.modes.echo.state.EchoSkill.of(member.specialization);
        long now=matchTick();
        // 职业技能与专长技能共用第 3 格信物：任一在冷却中都不能释放。
        if(!member.skills.ready(now) || !member.clazz.skillReady(now)) return false;
        if(!member.clazz.activate(now)) return false;
        if(skill==org.agmas.noellesroles.game.modes.echo.state.EchoSkill.BROKER_DEAL && !trades.skillDeal(player)) return false;
        if(!member.skills.activate(skill,now)) return false;
        // 职业技能：裂爪是突进＋终结技就绪，织网是就地布阵；两者都不改冷却以外的状态。
        var clazz=member.clazz.clazz();
        if(clazz==org.agmas.noellesroles.game.modes.echo.classes.EchoClass.RAVAGER) combat.rendDash(player);
        if(clazz==org.agmas.noellesroles.game.modes.echo.classes.EchoClass.WARDEN) combat.placePin(player);
        if(clazz==org.agmas.noellesroles.game.modes.echo.classes.EchoClass.TRAPPER) combat.placeJam(player);
        org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFx.cast(this,player,skill);
        publishSkill(player);
        return true;
    }
    private void publishSkill(ServerPlayer player) {
        var member=members.get(player.getUUID()); if(member==null) return;
        RoleData.getOptional(EchoRoleData.class,player).filter(d -> id.equals(d.matchId()))
                .ifPresent(d -> d.projectSkill(Math.max(member.skills.readyAt(),member.clazz.skillReadyAt()),
                        member.skills.buffUntil(),member.skills.pendingUntil()));
    }
    /** 对局经济：发币与查询余额。 */
    public org.agmas.noellesroles.game.modes.echo.economy.EchoEconomy economy() { return economy; }
    /** 对局商店：卡包与售货机。 */
    public org.agmas.noellesroles.game.modes.echo.economy.EchoShopService shop() { return shop; }
    public Vec3 spawn(UUID id) { return members.get(id).spawn; }
    public EchoCargoState cargo(UUID owner) { return members.get(owner).cargo; }
    public Vec3 lastSafe(UUID owner) { return members.get(owner).lastSafe; }
    public ServerPlayer connectedPlayer(UUID owner) {
        var member=members.get(owner);
        return member!=null && member.connection.connected() && member.live!=null && member.live.level()==level
                && level.getServer().getPlayerList().getPlayer(owner)==member.live?member.live:null;
    }
    public boolean inGrace(UUID owner) { var m=members.get(owner); return m!=null && m.connection.inGrace(matchTick()); }
    public void activeAction(UUID id) { if(state(id)!=null) state(id).activeAction(); }
    private boolean safeSpot(ServerPlayer player) {
        Vec3 pos=player.position();
        return !player.isInWater() && !player.isInLava() && level.noCollision(player) && level.getBlockState(player.blockPosition().below()).isCollisionShapeFullBlock(level,player.blockPosition().below())
                && io.wifi.starrailexpress.cca.AreasWorldComponent.KEY.get(level).getPlayArea().contains(pos)
                && !org.agmas.noellesroles.game.modes.echo.objective.EchoGateService.box(prepared.map().evacuation().zone()).contains(pos)
                && !org.agmas.noellesroles.game.modes.echo.objective.EchoGateService.box(prepared.map().evacuation().doorVolume()).contains(pos)
                && prepared.map().coreGate().doors().stream().noneMatch(d -> org.agmas.noellesroles.game.modes.echo.objective.EchoGateService.box(d.volume()).contains(pos));
    }
    public Vec3 safeAnchor(ServerPlayer player,boolean environmental) {
        return !environmental && safeSpot(player)?player.position():members.get(player.getUUID()).lastSafe;
    }
    private boolean settlePolicy(int remaining) {
        var facts=members.values().stream().map(m -> new EchoWinPolicy.PlayerFact(m.faction,m.state.life(),
                connectedPlayer(m.uuid)!=null,m.connection.inGrace(matchTick()))).toList();
        var decision=EchoWinPolicy.evaluate(new EchoWinPolicy.Facts(phase(),Math.max(0,remaining),escapeQuota(),false,facts));
        if(decision.outcome()==EchoWinPolicy.Outcome.NONE) return false;
        settle(EchoResultSnapshot.Outcome.valueOf(decision.outcome().name()),decision.reason().name(),true);
        return true;
    }
    public List<ServerPlayer> onlinePlayers() {
        return members.keySet().stream().map(this::connectedPlayer).filter(Objects::nonNull).toList();
    }
    public boolean present(ServerPlayer player) {
        Member member=members.get(player.getUUID());
        return result==null && member!=null && connectedPlayer(player.getUUID())==player && !player.isSpectator() && !player.isCreative() && player.isAlive()
                && member.state.life().potentiallyEscapable() && SREGameWorldComponent.KEY.get(level).getRole(player)==EchoRoles.get(member.faction)
                && RoleData.getOptional(EchoRoleData.class,player).map(d -> id.equals(d.matchId()) && d.inventory()==bags.inventory(player.getUUID())).orElse(false);
    }
    public boolean standing(ServerPlayer player) {
        return present(player) && state(player.getUUID()).canAct(matchTick());
    }
    /** 释放旧连接的全部输入；资源和倒地截止仍由会话持有。 */
    public void disconnect(ServerPlayer player) {
        var member=members.get(player.getUUID());
        if(result!=null || member==null || member.live!=player || !member.connection.connected()) return;
        try {
            boolean eligible=player.level()==level && !player.isSpectator() && !player.isCreative() && player.isAlive()
                    && SREGameWorldComponent.KEY.get(level).getRole(player)==EchoRoles.get(member.faction)
                    && RoleData.getOptional(EchoRoleData.class,player).map(d -> id.equals(d.matchId()) && d.inventory()==bags.inventory(member.uuid)).orElse(false)
                    && io.wifi.starrailexpress.cca.AreasWorldComponent.KEY.get(level).getPlayArea().contains(player.position());
            if(eligible && safeSpot(player)) member.lastSafe=player.position();
            trades.interrupt(member.uuid); objectives.cancelActor(member.uuid); member.state.cancelCharge();
            rolls.cancel(player);
            bags.interrupt(player); contracts.suspend(member.uuid);
            while(!member.cargo.items().isEmpty()) if(!objectives.dropCarried(member.uuid,false,member.lastSafe)) throw new IllegalStateException("断线掉货失败");
            if(bags.inventory(member.uuid).items().stream().anyMatch(org.agmas.noellesroles.game.modes.echo.inventory.EchoResourceInstance::key)
                    && !objectives.dropCarried(member.uuid,true,member.lastSafe)) throw new IllegalStateException("断线掉钥匙失败");
            bags.disconnect(player); cards.disconnect(player); combat.detach(player); member.live=null; intents.removeIf(i -> i.player().equals(member.uuid));
            if(!member.state.life().potentiallyEscapable()) member.connection.leave();
            else if(!eligible || !member.connection.disconnect(matchTick())) exit(member.uuid);
        } catch(RuntimeException failure) {
            Noellesroles.LOGGER.error("回声断线托管失败 {}",member.uuid,failure); settle(EchoResultSnapshot.Outcome.VOID,"ESCROW_FAILURE",true);
        }
    }
    /** 永久退出可在玩家/职业数据已经消失后执行，重复调用不重复掉包。 */
    public void exit(UUID owner) {
        var member=members.get(owner); if(member==null) return;
        member.connection.leave(); intents.removeIf(i -> i.player().equals(owner));
        if(!member.state.life().potentiallyEscapable()) return;
        member.state.depart(EchoLifeState.ELIMINATED); trades.interrupt(owner); objectives.cancelActor(owner);
        member.state.depart(EchoLifeState.ELIMINATED); trades.interrupt(owner); objectives.cancelActor(owner); member.clazz.clear();
        bags.depart(owner,member.lastSafe); contracts.depart(owner); cards.depart(owner);
        if(member.live!=null) rolls.cancel(member.live);
        if(member.live!=null) { combat.detach(member.live); RoleData.getOptional(EchoRoleData.class,member.live).filter(d -> id.equals(d.matchId())).ifPresent(EchoRoleData::clear); }
        member.live=null;
    }
    private void spectator(ServerPlayer player) {
        RoleData.getOptional(EchoRoleData.class,player).ifPresent(d -> { d.clear(); d.projectInventory(); });
        combat.detach(player); player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR); GameUtils.normalizeSpectatorFlightAbilities(player);
        if(!io.wifi.starrailexpress.compat.TrainVoicePlugin.isVoiceChatMissing()) io.wifi.starrailexpress.compat.TrainVoicePlugin.addPlayer(player.getUUID());
    }
    public void join(ServerPlayer player) {
        if(result!=null) return;
        var member=members.get(player.getUUID());
        if(member==null) { if(player.level()==level) spectator(player); return; }
        if(member.live==player && member.connection.connected()) return;
        if(!member.state.life().potentiallyEscapable() || member.state.life()==EchoLifeState.DOWNED && matchTick()>=member.state.bleedUntil()
                || player.level()!=level || !member.connection.reconnect(id,matchTick())) {
            exit(member.uuid); spectator(player); return;
        }
        member.live=player;
        var game=SREGameWorldComponent.KEY.get(level); game.addRole(player,EchoRoles.get(member.faction),false); game.syncRolesNow();
        RoleMethodDispatcher.onInit(EchoRoles.get(member.faction),level.getServer(),player);
        var data=Objects.requireNonNull(RoleData.getNullable(EchoRoleData.class,player)); data.bind(id,member.contract,phase());
        data.configureRules(rules());
        player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE); player.setHealth(player.getMaxHealth());
        Vec3 point=member.lastSafe;
        player.teleportTo(level,point.x,point.y,point.z,player.getYRot(),player.getXRot());
        if(!safeSpot(player)) { point=member.spawn; player.teleportTo(level,point.x,point.y,point.z,player.getYRot(),player.getXRot()); }
        io.wifi.starrailexpress.compat.TrainVoicePlugin.resetPlayer(member.uuid);
        bags.attach(player); combat.restore(player); cards.restore(player); contracts.restore(member.uuid);
        data.projectCombat(member.state.snapshot(),matchTick(),true);
        data.projectSpecialization(member.specialization);
        data.projectSpecializationWindow(specializationDeadline,specializationLocked());
        data.projectClass(member.clazz.clazz());
        player.sendSystemMessage(Component.translatable("echo.connection.restored"));
        publishSpecialization(player);
        publishSkill(player);
        rolls.publish(player);
    }
    private void connections() {
        for(var member:members.values()) {
            if(!member.state.life().potentiallyEscapable()) continue;
            if(member.connection.expire(matchTick())) { exit(member.uuid); continue; }
            if(!member.connection.connected()) continue;
            var player=level.getServer().getPlayerList().getPlayer(member.uuid);
            if(player==null || player!=member.live) { if(member.live!=null) disconnect(member.live); continue; }
            if(player.level()!=level || player.isSpectator() || player.isCreative() || !player.isAlive()
                    || SREGameWorldComponent.KEY.get(level).getRole(player)!=EchoRoles.get(member.faction)
                    || RoleData.getOptional(EchoRoleData.class,player).map(d -> !id.equals(d.matchId()) || d.inventory()!=bags.inventory(member.uuid)).orElse(true)) {
                exit(member.uuid); spectator(player); continue;
            }
            if(!io.wifi.starrailexpress.cca.AreasWorldComponent.KEY.get(level).getPlayArea().contains(player.position())) {
                // 地图边缘/坠落不是主动离场：回安全点并按模式血量结算，避免直接触发人数不足。
                var point=member.lastSafe;
                player.teleportTo(level,point.x,point.y,point.z,player.getYRot(),player.getXRot());
                combat.lethal(player,null,io.wifi.starrailexpress.game.GameConstants.DeathReasons.FELL_OUT_OF_TRAIN,false,true);
                continue;
            }
            if(member.state.life().standing() && matchTick()%20==0 && safeSpot(player)) member.lastSafe=player.position();
        }
    }
    public void recordDelivery(UUID uuid) { members.get(uuid).delivered++; }
    public void retire(ServerPlayer player) {
        UUID owner=player.getUUID();
        if(!standing(player) || !contracts.retireReady(owner)) throw new IllegalStateException("NO_TARGET");
        trades.interrupt(owner); objectives.cancelActor(player);
        while(!RoleData.getNullable(EchoRoleData.class,player).cargo().isEmpty())
            if(!objectives.dropCarried(player,false,safeAnchor(player,false))) throw new IllegalStateException("退场货物掉落失败");
        bags.depart(player); cards.depart(player); state(owner).depart(EchoLifeState.RETIRED);
        RoleData.getOptional(EchoRoleData.class,player).ifPresent(d -> d.projectCombat(state(owner).snapshot(),matchTick(),true));
        player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
        GameUtils.normalizeSpectatorFlightAbilities(player);
        if(!io.wifi.starrailexpress.compat.TrainVoicePlugin.isVoiceChatMissing()) io.wifi.starrailexpress.compat.TrainVoicePlugin.addPlayer(owner);
        org.agmas.noellesroles.game.modes.echo.objective.EchoGateService.teleport(player,prepared.map().spectatorPos());
    }
    public void markEscaped(UUID uuid) {
        var escapedPlayer=connectedPlayer(uuid);
        if(escapedPlayer!=null) economy.award(escapedPlayer,
                org.agmas.noellesroles.game.modes.echo.economy.EchoEconomy.Source.ESCAPE,null);
        Member member=members.get(uuid);
        if(member==null || member.faction!=EchoFaction.CARRIER || !member.state.life().standing()) throw new IllegalStateException("非法撤离");
        ServerPlayer player=level.getServer().getPlayerList().getPlayer(uuid);
        trades.interrupt(uuid);
        if(player!=null) bags.depart(player);
        if(player!=null) cards.depart(player);
        member.state.depart(EchoLifeState.ESCAPED);
    }
    private boolean enqueue(ServerPlayer player,Runnable command) {
        if(!standing(player) || rolls.active(player.getUUID()) || bags.busy(player.getUUID()) || !phase().acceptsActions() || SREGameTimeComponent.KEY.get(level).isTimeFrozen()
                || intents.size()>=128 || inputCounts.getOrDefault(player.getUUID(),0)>=12) return false;
        inputCounts.merge(player.getUUID(),1,Integer::sum); intents.addLast(new Intent(player.getUUID(),command,false)); return true;
    }
    public void inventoryRequest(ServerPlayer player,org.agmas.noellesroles.game.modes.echo.inventory.EchoInventoryPayload payload) {
        if(result!=null || !present(player) || rolls.active(player.getUUID()) || !id.equals(payload.matchId()) || intents.size()>=128) return;
        if(SREGameTimeComponent.KEY.get(level).isTimeFrozen()) { bags.request(player,payload); return; }
        intents.addLast(new Intent(player.getUUID(),() -> bags.request(player,payload),true));
    }
    public boolean quickTrade(ServerPlayer player) {
        return faction(player.getUUID())==EchoFaction.BROKER && enqueue(player,() -> trades.quick(player));
    }
    public boolean useBlock(ServerPlayer player,BlockPos pos) { return enqueue(player,() -> { if(!cards.scenes().dismantle(player)) objectives.useBlock(player,pos); }); }
    public boolean useEntity(ServerPlayer player,UUID entity) { return enqueue(player,() -> { if(!cards.scenes().dismantle(player)) objectives.useEntity(player,entity); }); }
    public boolean overload(ServerPlayer player) { return !trades.busy(player.getUUID()) && enqueue(player,() -> { if(!trades.busy(player.getUUID())) objectives.overload(player); }); }
    public boolean releaseCutter(ServerPlayer player) {
        long releasedAt=matchTick();
        boolean queued=enqueue(player,() -> combat.releaseCutter(player,releasedAt));
        if(queued) combat.awaitCutterRelease(player);
        return queued;
    }
    public boolean shove(ServerPlayer player) { return enqueue(player,() -> combat.shove(player)); }
    public boolean requestRoll(ServerPlayer player,org.agmas.noellesroles.game.modes.echo.action.EchoRollRequest request) {
        return id.equals(request.match()) && faction(player.getUUID())==EchoFaction.CARRIER
                && EchoRollState.validDirection(request.x(),request.z()) && enqueue(player,() -> rolls.start(player,request.x(),request.z()));
    }
    public void cancel(ServerPlayer player,UUID actionId) {
        if(result!=null || !members.containsKey(player.getUUID())) return;
        if(SREGameTimeComponent.KEY.get(level).isTimeFrozen()) objectives.cancel(player,actionId);
        else enqueue(player,() -> objectives.cancel(player,actionId));
    }
    public boolean drop(ServerPlayer player,boolean key) {
        return enqueue(player,() -> {
            BlockPos ground=player.blockPosition().below();
            boolean safe=level.noCollision(player) && level.getBlockState(ground).isCollisionShapeFullBlock(level,ground)
                    && io.wifi.starrailexpress.cca.AreasWorldComponent.KEY.get(level).getPlayArea().contains(player.position());
            objectives.dropCarried(player,key,safe?player.position():members.get(player.getUUID()).lastSafe);
        });
    }

    public void startTick(long sequence) {
        inputCounts.clear();
        if(result!=null) return;
        var timer=SREGameTimeComponent.KEY.get(level);
        if(!activated) { timer.setResetTime(EchoRules.SAFE_TICKS); timer.reset(); activated=true; }
        clock.capture(sequence,timer.getTime(),timer.isTimeFrozen());
    }
    public void endTick() {
        if(result!=null || !activated) return;
        var timer=SREGameTimeComponent.KEY.get(level);
        try {
            connections();
            if(result!=null) return;
            if(clock.canProcess(timer.isTimeFrozen())) {
                combat.tick();
                while(!intents.isEmpty()) {
                    Intent intent=intents.removeFirst(); ServerPlayer player=level.getServer().getPlayerList().getPlayer(intent.player());
                    if(player!=null && (intent.inventory()?present(player) && !rolls.active(player.getUUID()):standing(player) && !rolls.active(player.getUUID()) && !bags.busy(player.getUUID()))) intent.command().run();
                }
                rolls.tick();
                bags.tick();
                trades.tick();
                contracts.tick();
                cards.tick();
                boolean switched=false;
                if(phase().acceptsActions()) {
                    objectives.tickOperations();
                for(var member:members.values()) member.skills.expire(matchTick());
                    if(phase()==EchoPhase.OPERATIONS && (requestEvacuation || objectives.allActive()) && clock.openEvacuation(false)) {
                        requestEvacuation=false; timer.setResetTime(prepared.profile().evacuationTicks()); timer.reset();
                        objectives.beginEvacuation(); projectPhase(); switched=true;
                    }
                    if(phase()==EchoPhase.EVACUATION && !switched) objectives.tickEvacuation();
                    if(settlePolicy(timer.getTime())) return;
                }
            } else { intents.clear(); rolls.freeze(); combat.restrict(); }
            var boundary=clock.finish(timer.getTime(),timer.isTimeFrozen());
            if(boundary==EchoRoundClock.Boundary.OPERATIONS_STARTED) {
                timer.setResetTime(prepared.profile().operationTicks()); timer.reset();
                SREGameWorldComponent.KEY.get(level).isSkillAvailable=true;
                objectives.startOperations(); projectPhase(); lockSpecializations();
                OnGameTrueStarted.EVENT.invoker().onGameTrueStarted(level);
            } else if(boundary==EchoRoundClock.Boundary.TIME_EXPIRED) {
                boolean evacuationSucceeded=escapedCount()>0;
                settle(evacuationSucceeded?EchoResultSnapshot.Outcome.CARRIERS:EchoResultSnapshot.Outcome.INTERCEPTORS,
                        evacuationSucceeded?"ESCAPE_COMPLETED":"TIME_EXPIRED",true);
            }
            if(result==null && level.getGameTime()%10==0) objectives.publish(false);
        } catch(RuntimeException failure) {
            Noellesroles.LOGGER.error("回声目标事务失败，安全中止 {}",id,failure);
            settle(EchoResultSnapshot.Outcome.VOID,"OBJECTIVE_FAILURE",true);
        }
    }
    public boolean requestDevelopmentEvacuation() {
        if(result!=null || phase()!=EchoPhase.OPERATIONS) return false;
        requestEvacuation=true; return true;
    }
    public void settle(EchoResultSnapshot.Outcome outcome,String reason,boolean stop) {
        if(result!=null) return;
        Noellesroles.LOGGER.info("回声结算 match={} reason={} phase={} tick={} remaining={} members={}",id,reason,phase(),matchTick(),
                SREGameTimeComponent.KEY.get(level).getTime(),members.values().stream().map(m -> m.name+":"+m.faction+":"+m.state.life()+":"+m.connection.connected()).toList());
        contracts.finish(outcome!=EchoResultSnapshot.Outcome.VOID);
        List<EchoResultSnapshot.PlayerResult> rows=new ArrayList<>();
        for(Member member:members.values()) {
            boolean wins=outcome!=EchoResultSnapshot.Outcome.VOID && (outcome==EchoResultSnapshot.Outcome.CARRIERS&&member.faction==EchoFaction.CARRIER
                    || outcome==EchoResultSnapshot.Outcome.INTERCEPTORS&&member.faction==EchoFaction.INTERCEPTOR || member.faction==EchoFaction.BROKER&&contracts.succeeded(member.uuid));
            ServerPlayer online=level.getServer().getPlayerList().getPlayer(member.uuid);
            rows.add(new EchoResultSnapshot.PlayerResult(member.uuid,member.name,member.faction,member.state.life(),
                    member.contract,contracts.succeeded(member.uuid),online!=null&&online.level()==level,wins,member.specialization));
        }
        result=new EchoResultSnapshot(id,prepared.map().mapId(),outcome,reason,clock.matchTick(),EchoSessions.debug(level),rows,rulesHash());
        clock.settle(); intents.clear();
        SREGameWorldComponent.KEY.get(level).isSkillAvailable=false;
        EchoResultAdapter.apply(level,result);
        SRE.REPLAY_MANAGER.recordCustomEvent(Component.translatable(result.debug()?"echo.runtime.result_debug":"echo.runtime.result",
                Component.translatable(EchoResultReason.translationKey(reason))));
        rolls.cleanup();
        contracts.cleanup();
        try { try { trades.cleanup(); } finally { try { cards.cleanup(); } finally { try { bags.cleanup(); } finally { try { objectives.cleanup(); } finally { combat.cleanup(); } } } } }
        catch(RuntimeException failure) { Noellesroles.LOGGER.error("回声目标清理失败 {}",id,failure); }
        if(stop) GameUtils.stopGame(level);
    }
    public void finalizeMatch() {
        if(result==null) settle(EchoResultSnapshot.Outcome.VOID,"EXTERNAL_STOP",false);
        clock.finishMatch(); EchoResultAdapter.apply(level,result);
        if(!reportSaved) {
            reportSaved=true;
            try { EchoMatchReportStore.save(level.getServer().getWorldPath(LevelResource.ROOT),result); }
            catch(IOException failure) { Noellesroles.LOGGER.error("回声对局报告写入失败 {}",id,failure); }
        }
    }
    private void projectPhase() {
        for(ServerPlayer player:onlinePlayers()) {
            RoleData.getOptional(EchoRoleData.class,player).ifPresent(data -> data.projectPhase(clock.phase()));
            player.sendSystemMessage(Component.translatable("echo.phase.changed",Component.translatable("echo.phase."+clock.phase().name().toLowerCase(Locale.ROOT))));
        }
    }
}
