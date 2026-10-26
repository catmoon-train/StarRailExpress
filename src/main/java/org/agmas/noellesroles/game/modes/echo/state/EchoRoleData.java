package org.agmas.noellesroles.game.modes.echo.state;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.game.modes.echo.cards.EchoCardDefinition;
import org.agmas.noellesroles.game.modes.echo.cards.EchoCardRegistry;
import org.agmas.noellesroles.game.modes.echo.combat.EchoCombatState;
import org.agmas.noellesroles.game.modes.echo.inventory.*;
import org.agmas.noellesroles.game.modes.echo.classes.EchoClass;
import java.util.*;

/** 在线时挂载会话库存/货槽；clear只撤销投影引用，不删除会话资源或隐藏候选。 */
public final class EchoRoleData extends SimpleRoleData {
    private UUID matchId,actionId,epoch;
    private UUID selectedCard;
    private List<org.agmas.noellesroles.game.modes.echo.cards.EchoSignalView> signals=List.of();
    private org.agmas.noellesroles.game.modes.echo.action.EchoActionView actionView;
    private long keyPulseAt,interactionUntil;
    private String interactionError="";
    public org.agmas.noellesroles.game.modes.echo.action.EchoActionView actionView() { return actionView; }
    public UUID actionId() { return actionId; }
    public long keyPulseAt() { return keyPulseAt; }
    public String interactionError() { return projectedTick<interactionUntil?interactionError:""; }
    public void projectKeyPulse(long next) { if(keyPulseAt!=next) { keyPulseAt=next; sync(); } }
    public void projectInteractionError(String error,long until) {
        if(error.equals(interactionError) && until<interactionUntil+20) return;
        interactionError=error; interactionUntil=until; sync();
    }
    public void projectActionView(org.agmas.noellesroles.game.modes.echo.action.EchoActionView next) {
        if(!Objects.equals(actionView,next)) { actionView=next; actionId=next==null?null:next.id(); sync(); }
    }
    public UUID selectedCard() { return selectedCard; }
    public List<org.agmas.noellesroles.game.modes.echo.cards.EchoSignalView> signals() { return signals; }
    public void projectSelected(UUID id) { if(!Objects.equals(selectedCard,id)) { selectedCard=id; sync(); } }
    public void projectSignals(List<org.agmas.noellesroles.game.modes.echo.cards.EchoSignalView> next) { if(!signals.equals(next)) { signals=List.copyOf(next); sync(); } }
    private List<org.agmas.noellesroles.game.modes.echo.cards.EchoSceneState.Scene> scenes=List.of();
    public List<org.agmas.noellesroles.game.modes.echo.cards.EchoSceneState.Scene> scenes() { return scenes; }
    public void projectScenes(List<org.agmas.noellesroles.game.modes.echo.cards.EchoSceneState.Scene> next) { if(!scenes.equals(next)) { scenes=List.copyOf(next); sync(); } }
    private org.agmas.noellesroles.game.modes.echo.trade.EchoTradeView trade;
    public org.agmas.noellesroles.game.modes.echo.trade.EchoTradeView trade() { return trade; }
    public void projectTrade(org.agmas.noellesroles.game.modes.echo.trade.EchoTradeView next) { if(!Objects.equals(trade,next)) { trade=next; sync(); } }
    private org.agmas.noellesroles.game.modes.echo.contract.EchoContractState.Snapshot contractProgress;
    private int blackCargoCount;
    public org.agmas.noellesroles.game.modes.echo.contract.EchoContractState.Snapshot contractProgress() { return contractProgress; }
    public int blackCargoCount() { return blackCargoCount; }
    public void projectContract(org.agmas.noellesroles.game.modes.echo.contract.EchoContractState.Snapshot next) { if(!Objects.equals(next,contractProgress)) { contractProgress=next; sync(); } }
    public void projectBlackCargo(int next) { if(next!=blackCargoCount) { blackCargoCount=next; sync(); } }
    private EchoSpecialization specialization;
    private long specializationDeadline;
    private boolean specializationLocked;
    private EchoClass playerClass;
    /** 未选择时回退本阵营默认项；faction 为空时给托运者默认，避免空指针。 */
    public EchoSpecialization specialization() { return specialization!=null?specialization:EchoSpecialization.starter(faction); }
    /**
     * 追猎者职业：只在专长接受职业分支（HUNTER）时生效，其余情况返回 {@code null}，
     * 让战斗服务走原版割刀分支，SABOTEUR 与其它阵营的手感完全不变。
     */
    /** 本人本局职业；未投影时按阵营给一个默认值，避免客户端空指针。 */
    public EchoClass playerClass() {
        return playerClass!=null?playerClass:EchoClass.defaultFor(faction,0);
    }
    private long skillReadyAt, skillBuffUntil, skillPendingUntil;
    public EchoSkill skill() { return EchoSkill.of(specialization()); }
    public long skillReadyAt() { return skillReadyAt; }
    public long skillBuffUntil() { return skillBuffUntil; }
    public long skillPendingUntil() { return skillPendingUntil; }
    public boolean skillBuffActive() { return skillBuffUntil>projectedTick; }
    public boolean skillPendingActive() { return skillPendingUntil>projectedTick; }
    public long skillRemaining() { return Math.max(0,skillReadyAt-projectedTick); }
    public void projectSkill(long readyAt,long buffUntil,long pendingUntil) {
        if(skillReadyAt==readyAt && skillBuffUntil==buffUntil && skillPendingUntil==pendingUntil) return;
        skillReadyAt=readyAt; skillBuffUntil=buffUntil; skillPendingUntil=pendingUntil; sync();
    }
    private long rollReadyAt, rollUntil;
    public long rollRemaining() { return Math.max(0,rollReadyAt-projectedTick); }
    public boolean rolling() { return projectedTick<rollUntil; }
    public void projectRoll(long readyAt,long until) {
        if(rollReadyAt==readyAt && rollUntil==until) return;
        rollReadyAt=readyAt; rollUntil=until; sync();
    }
    public long specializationDeadline() { return specializationDeadline; }
    public boolean specializationLocked() { return specializationLocked; }
    public void projectSpecialization(EchoSpecialization next) {
        if(next==null || next.faction()!=faction) return;
        if(!Objects.equals(specialization,next)) { specialization=next; sync(); }
    }
    /** 切换职业只改打法，不改专长数值；由会话在 SAFE 内调用，锁定后不再接受。 */
    /** 职业是本人私有信息，只发给本人；换局或换职业时覆盖。 */
    public void projectClass(EchoClass next) {
        if(next==null || next.faction()!=faction) return;
        if(!Objects.equals(playerClass,next)) { playerClass=next; sync(); }
    }
    public void projectSpecializationWindow(long deadline,boolean locked) {
        if(specializationDeadline==deadline && specializationLocked==locked) return;
        specializationDeadline=deadline; specializationLocked=locked; sync();
    }
    private EchoContract contract;
    private EchoFaction faction;
    private EchoPhase phase=EchoPhase.SAFE;
    private final List<UUID> cargo=new ArrayList<>();
    private EchoCargoState attachedCargo;
    private EchoInventory inventory;
    private EchoInventory.Snapshot bagSnapshot;
    private EchoDraftView draft;
    private long acknowledged,projectedTick;
    private String requestError="";
    /** 追猎刀固定蓄力 0.8 秒，不受专长或狩猎本能缩短。 */
    public int cutterChargeTicks() {
        return org.agmas.noellesroles.game.modes.echo.combat.EchoCutterStrike.CHARGE_TICKS;
    }
    /** 一次性效果按当前专长判定，避免切换专长后旧窗口误用。 */
    public boolean skillPending(EchoSkill skill) { return skillPendingActive() && skill()==skill; }
    public boolean skillBuff(EchoSkill skill) { return skillBuffActive() && skill()==skill; }
    private int baseCapacity=2;
    private double baseCargoPenalty=0.10;
    private int cutterChargeTicks=12;
    private org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig rules=org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig.DEFAULT;
    private boolean rulesReady;
    public org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig rules() { return rules; }
    public boolean rulesReady() { return rulesReady; }
    public void projectRules(org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig rules) { this.rules=Objects.requireNonNull(rules); rulesReady=true; }
    private double tradeRange=3;
    public double tradeRange() { return tradeRange; }
    public void projectTradeRange(double next) { if(Double.compare(next,tradeRange)!=0) { tradeRange=next; sync(); } }
    public void configureRules(org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig rules) {
        projectRules(rules);
        baseCapacity=rules.logistics().capacity(); baseCargoPenalty=rules.logistics().cargoPenalty(); cutterChargeTicks=rules.combat().cutterChargeTicks(); sync();
        if(player instanceof ServerPlayer server && matchId!=null) net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(server,
                new org.agmas.noellesroles.game.modes.echo.rules.EchoRulesPayload(matchId,server.level().dimension().location(),rules));
    }
    public EchoRoleData(RoleDataContext context) { super(context); }
    public UUID matchId() { return matchId; }
    public UUID epoch() { return epoch; }
    public EchoContract contract() { return contract; }
    public EchoFaction faction() { return faction; }
    public EchoPhase phase() { return phase; }
    public EchoLifeState life() { return combat.life(); }
    public float health() { return combat.health(); }
    public boolean controlled() { return projectedTick<combat.controlledUntil(); }
    public long bleedRemaining() { return Math.max(0,combat.bleedUntil()-projectedTick); }
    public long projectedTick() { return projectedTick; }
    public long shoveRemaining() { return Math.max(0,combat.shoveReadyAt()-projectedTick); }
    public List<UUID> cargo() { return attachedCargo!=null?attachedCargo.items():List.copyOf(cargo); }
    public EchoInventory inventory() { return inventory; }
    public EchoInventory.Snapshot bag() { return inventory!=null?inventory.snapshot():bagSnapshot; }
    public EchoDraftView draft() { return draft; }
    public long acknowledged() { return acknowledged; }
    public String requestError() { return requestError; }
    private EchoCombatState.Snapshot combat=new EchoCombatState.Snapshot(EchoLifeState.HEALTHY,0,0,0,0,0,0);
    public UUID key() { return items().stream().filter(EchoResourceInstance::key).map(EchoResourceInstance::instanceId).findFirst().orElse(null); }
    public List<EchoResourceInstance> items() { var bag=bag(); return bag==null?List.of():bag.slots().stream().flatMap(s -> s.items().stream()).toList(); }
    private Optional<EchoCardDefinition> logistics() {
        return items().stream().filter(i -> i.kind()!=EchoResourceInstance.Kind.PACK).map(i -> rules.card(i.definitionId()))
                .filter(c -> c.usableBy(faction) && c.series()==EchoCardDefinition.Series.LOGISTICS)
                .max(Comparator.comparingDouble(c -> c.params().getOrDefault("cargoCapacity",2D)));
    }
    public int capacity() {
        int base=Math.max(baseCapacity,logistics().map(c -> c.params().get("cargoCapacity").intValue()).orElse(baseCapacity));
        int bonus=specialization().cargoCapacityBonus(rules.specialization())+(skillBuff(EchoSkill.STEADY_HAUL)?1:0);
        return Math.max(1,Math.min(4,base+bonus));
    }
    public double cargoPenalty() {
        if(skillBuff(EchoSkill.STEADY_HAUL) || skillBuff(EchoSkill.DARK_CACHE)) return 0;
        double base=Math.min(baseCargoPenalty,logistics().map(c -> c.params().get("cargoPenalty")).orElse(baseCargoPenalty));
        return Math.max(0,Math.min(1,base+specialization().cargoPenaltyDelta(rules.specialization())));
    }
    public int collectTicks(int base) { return EchoSpecialization.scaled(base,specialization().collectSpeed(rules.specialization())); }
    public int blackCollectTicks(int base) { return EchoSpecialization.scaled(base,specialization().blackCollectSpeed(rules.specialization())); }
    public int stallTicks(int base) { return EchoSpecialization.scaled(base,specialization().stallSpeed(rules.specialization())); }
    public int tradeRangeBonus() { return specialization().tradeRangeBonus(rules.specialization()); }
    public double proposalCooldownSpeed() { return specialization().proposalCooldownSpeed(rules.specialization()); }
    public int freeCargoSlot() { return cargo().size()<capacity()?cargo().size():-1; }
    public void attachCargo(EchoCargoState cargo) { attachedCargo=Objects.requireNonNull(cargo); sync(); }
    public void addCargo(UUID instance) {
        if(attachedCargo==null) throw new IllegalStateException("货槽未挂载");
        attachedCargo.add(instance,capacity()); sync();
    }
    /** 台账货槽使用紧凑下标；移走后外层同事务重排后续实例的位置。 */
    public void removeCargo(UUID instance) {
        if(attachedCargo==null) throw new IllegalStateException("货槽未挂载");
        attachedCargo.remove(instance); sync();
    }
    public void bind(UUID id,EchoContract contract,EchoPhase phase) { matchId=id; this.contract=contract; this.phase=phase; sync(); }
    public void attachInventory(EchoInventory inventory,UUID epoch,EchoFaction faction) {
        // 回复序号和错误属于连接 epoch，不能让上一连接的 COOLDOWN 被新拆包请求误认。
        if(!Objects.equals(this.epoch,epoch)) { acknowledged=0; requestError=""; }
        this.inventory=inventory; this.epoch=epoch; this.faction=faction; sync();
    }
    public void projectInventory() { sync(); }
    public void acknowledge(long request,String error) { acknowledged=request; requestError=error; sync(); }
    public void projectDraft(EchoDraftView view) { if(!Objects.equals(draft,view)) { draft=view; sync(); } }
    public void projectAction(UUID id) { if(!Objects.equals(actionId,id) || id==null && actionView!=null) { actionId=id; actionView=null; sync(); } }
    public void projectPhase(EchoPhase phase) { if(this.phase!=phase) { this.phase=phase; sync(); } }
    public void projectCombat(EchoCombatState.Snapshot snapshot,long now,boolean force) {
        if(!force && combat.equals(snapshot) && now%100!=0) return;
        combat=snapshot; projectedTick=now; sync();
    }
    @Override public void clientTick() {
        if(matchId!=null && !io.wifi.starrailexpress.cca.SREGameTimeComponent.KEY.get(player.level()).isTimeFrozen()) projectedTick++;
    }
    @Override public boolean shouldSyncWith(ServerPlayer recipient) { return recipient==player; }
    @Override public void writeToSyncNbt(CompoundTag tag,HolderLookup.Provider provider) {
        if(player instanceof ServerPlayer server) {
            var match=org.agmas.noellesroles.game.modes.echo.runtime.EchoSessions.get(server.serverLevel());
            if(match!=null && match.id().equals(matchId)) projectedTick=match.matchTick();
        }
        if(matchId!=null) tag.putUUID("echoMatch",matchId);
        if(epoch!=null) tag.putUUID("echoEpoch",epoch);
        tag.putString("echoPhase",phase.name());
        tag.putDouble("echoTradeRange",tradeRange);
        tag.putInt("echoBaseCapacity",baseCapacity); tag.putDouble("echoBaseCargoPenalty",baseCargoPenalty);
        tag.putInt("echoCutterChargeTicks",cutterChargeTicks);
        if(specialization!=null) tag.putString("echoSpecialization",specialization.name());
        if(playerClass!=null) tag.putString("echoClass",playerClass.name());
        tag.putLong("echoSpecDeadline",specializationDeadline); tag.putBoolean("echoSpecLocked",specializationLocked);
        if(contract!=null) tag.putString("echoContract",contract.name());
        tag.putLong("echoSkillReady",skillReadyAt); tag.putLong("echoSkillBuff",skillBuffUntil); tag.putLong("echoSkillPending",skillPendingUntil);
        tag.putLong("echoRollReady",rollReadyAt); tag.putLong("echoRollUntil",rollUntil);
        tag.putLong("echoKeyPulse",keyPulseAt); tag.putString("echoInteractionError",interactionError); tag.putLong("echoInteractionUntil",interactionUntil);
        if(actionView!=null) { var a=new CompoundTag(); a.putUUID("id",actionView.id()); a.putString("kind",actionView.kind()); a.putLong("start",actionView.startedAt()); a.putInt("duration",actionView.duration()); tag.put("echoActionView",a); }
        tag.putInt("echoBlackCargo",blackCargoCount);
        if(contractProgress!=null) {
            var c=new CompoundTag(); c.putString("status",contractProgress.status()); c.putInt("primary",contractProgress.primary()); c.putInt("secondary",contractProgress.secondary());
            c.putLong("succeededAt",contractProgress.succeededAt()); c.putString("dropoff",contractProgress.dropoff()); c.putBoolean("retire",contractProgress.retireReady()); tag.put("echoContractProgress",c);
        }
        if(faction!=null) tag.putString("echoFaction",faction.name());
        if(actionId!=null) tag.putUUID("echoAction",actionId);
        if(selectedCard!=null) tag.putUUID("echoSelected",selectedCard);
        ListTag signalTags=new ListTag();
        for(var signal:signals) {
            CompoundTag s=new CompoundTag(); s.putString("kind",signal.kind()); s.putInt("sector",signal.sector()); s.putLong("until",signal.until());
            if(signal.target()!=null) s.putUUID("target",signal.target()); signalTags.add(s);
        }
        tag.put("echoSignals",signalTags);
        tag.put("echoScenes",org.agmas.noellesroles.game.modes.echo.cards.EchoSceneNbt.write(scenes));
        if(trade!=null) tag.put("echoTrade",trade.write());
        ListTag goods=new ListTag(); cargo().forEach(id -> goods.add(NbtUtils.createUUID(id))); tag.put("echoCargo",goods);
        if(bag()!=null) tag.put("echoBag",EchoInventoryNbt.write(bag()));
        tag.putLong("echoAck",acknowledged); tag.putString("echoError",requestError);
        if(draft!=null) {
            CompoundTag d=new CompoundTag(); d.putUUID("offer",draft.offerId()); d.putUUID("pack",draft.packId()); d.putString("state",draft.state());
            d.putLong("deadline",draft.deadline()); d.putLong("ready",draft.readyAt()); ListTag choices=new ListTag();
            draft.candidates().forEach(c -> choices.add(StringTag.valueOf(c))); d.put("choices",choices); tag.put("echoDraft",d);
        }
        CompoundTag c=new CompoundTag(); c.putString("life",combat.life().name()); c.putLong("tick",projectedTick);
        c.putLong("bleed",combat.bleedUntil()); c.putLong("protection",combat.protectedUntil()); c.putLong("control",combat.controlledUntil());
        c.putFloat("health",combat.health());
        c.putLong("immune",combat.controlImmuneUntil()); c.putLong("cutter",combat.cutterReadyAt()); c.putLong("shove",combat.shoveReadyAt()); tag.put("echoCombat",c);
    }
    @Override public void readFromSyncNbt(CompoundTag tag,HolderLookup.Provider provider) {
        var previousMatch=matchId;
        matchId=tag.hasUUID("echoMatch")?tag.getUUID("echoMatch"):null; epoch=tag.hasUUID("echoEpoch")?tag.getUUID("echoEpoch"):null;
        if(!Objects.equals(previousMatch,matchId)) { rulesReady=false; rules=org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig.DEFAULT; }
        tradeRange=tag.contains("echoTradeRange")?tag.getDouble("echoTradeRange"):3;
        if(!Double.isFinite(tradeRange) || tradeRange<0.5 || tradeRange>128) tradeRange=3;
        baseCapacity=tag.contains("echoBaseCapacity")?Math.max(1,Math.min(4,tag.getInt("echoBaseCapacity"))):2;
        double penalty=tag.contains("echoBaseCargoPenalty")?tag.getDouble("echoBaseCargoPenalty"):0.10;
        baseCargoPenalty=Double.isFinite(penalty)?Math.max(0,Math.min(1,penalty)):0.10;
        cutterChargeTicks=tag.contains("echoCutterChargeTicks")?Math.max(1,Math.min(72000,tag.getInt("echoCutterChargeTicks"))):12;
        specialization=tag.contains("echoSpecialization")?EchoSpecialization.byId(tag.getString("echoSpecialization").toLowerCase(java.util.Locale.ROOT)).filter(s -> s.faction()==faction).orElse(null):null;
        playerClass=tag.contains("echoClass")?EchoClass.byId(tag.getString("echoClass")).orElse(null):null;
        specializationDeadline=Math.max(0,tag.getLong("echoSpecDeadline")); specializationLocked=tag.getBoolean("echoSpecLocked");
        phase=parse(EchoPhase.class,tag.getString("echoPhase"),EchoPhase.SAFE); contract=parse(EchoContract.class,tag.getString("echoContract"),null);
        skillReadyAt=Math.max(0,tag.getLong("echoSkillReady")); skillBuffUntil=Math.max(0,tag.getLong("echoSkillBuff")); skillPendingUntil=Math.max(0,tag.getLong("echoSkillPending"));
        rollReadyAt=Math.max(0,tag.getLong("echoRollReady")); rollUntil=Math.max(0,tag.getLong("echoRollUntil"));
        keyPulseAt=Math.max(0,tag.getLong("echoKeyPulse")); interactionError=tag.getString("echoInteractionError"); interactionUntil=Math.max(0,tag.getLong("echoInteractionUntil"));
        actionView=null;
        if(tag.contains("echoActionView")) {
            var a=tag.getCompound("echoActionView");
            try { actionView=new org.agmas.noellesroles.game.modes.echo.action.EchoActionView(a.getUUID("id"),a.getString("kind"),a.getLong("start"),a.getInt("duration")); }
            catch(IllegalArgumentException invalid) { actionView=null; }
        }
        faction=parse(EchoFaction.class,tag.getString("echoFaction"),null); actionId=tag.hasUUID("echoAction")?tag.getUUID("echoAction"):null;
        blackCargoCount=Math.max(0,Math.min(2,tag.getInt("echoBlackCargo"))); contractProgress=null;
        if(tag.contains("echoContractProgress")) {
            var c=tag.getCompound("echoContractProgress");
            contractProgress=new org.agmas.noellesroles.game.modes.echo.contract.EchoContractState.Snapshot(c.getString("status"),Math.max(0,Math.min(3,c.getInt("primary"))),Math.max(0,Math.min(1,c.getInt("secondary"))),c.getLong("succeededAt"),c.getString("dropoff"),c.getBoolean("retire"));
        }
        selectedCard=tag.hasUUID("echoSelected")?tag.getUUID("echoSelected"):null;
        var projected=new ArrayList<org.agmas.noellesroles.game.modes.echo.cards.EchoSignalView>();
        var signalTags=tag.getList("echoSignals",Tag.TAG_COMPOUND);
        for(int n=0;n<Math.min(32,signalTags.size());n++) {
            var s=signalTags.getCompound(n); projected.add(new org.agmas.noellesroles.game.modes.echo.cards.EchoSignalView(s.getString("kind"),s.hasUUID("target")?s.getUUID("target"):null,Math.floorMod(s.getInt("sector"),8),Math.max(0,s.getLong("until"))));
        }
        signals=List.copyOf(projected);
        scenes=org.agmas.noellesroles.game.modes.echo.cards.EchoSceneNbt.read(tag.getList("echoScenes",Tag.TAG_COMPOUND));
        trade=tag.contains("echoTrade")?org.agmas.noellesroles.game.modes.echo.trade.EchoTradeView.read(tag.getCompound("echoTrade")):null;
        cargo.clear(); ListTag goods=tag.getList("echoCargo",Tag.TAG_INT_ARRAY); for(int n=0;n<Math.min(4,goods.size());n++) cargo.add(NbtUtils.loadUUID(goods.get(n)));
        bagSnapshot=tag.contains("echoBag")?EchoInventoryNbt.read(tag.getCompound("echoBag")):null;
        acknowledged=tag.getLong("echoAck"); requestError=tag.getString("echoError"); draft=null;
        if(tag.contains("echoDraft")) {
            CompoundTag d=tag.getCompound("echoDraft"); List<String> choices=new ArrayList<>(); ListTag entries=d.getList("choices",Tag.TAG_STRING);
            for(int n=0;n<Math.min(3,entries.size());n++) choices.add(entries.getString(n));
            draft=new EchoDraftView(d.getUUID("offer"),d.getUUID("pack"),d.getString("state"),choices,d.getLong("deadline"),d.getLong("ready"));
        }
        CompoundTag c=tag.getCompound("echoCombat"); projectedTick=c.getLong("tick");
        var life=parse(EchoLifeState.class,c.getString("life"),EchoLifeState.HEALTHY);
        float health=c.contains("health")?c.getFloat("health"):life==EchoLifeState.HEALTHY?EchoCombatState.MAX_HEALTH:life==EchoLifeState.WOUNDED?EchoCombatState.CUTTER_DAMAGE:0F;
        combat=new EchoCombatState.Snapshot(life,c.getLong("bleed"),c.getLong("protection"),c.getLong("control"),c.getLong("immune"),c.getLong("cutter"),c.getLong("shove"),Float.isFinite(health)?Math.max(0,Math.min(EchoCombatState.MAX_HEALTH,health)):0F);
    }
    private static <E extends Enum<E>> E parse(Class<E> type,String value,E fallback) { try { return Enum.valueOf(type,value); } catch(IllegalArgumentException ignored) { return fallback; } }
    @Override public void clear() {
        attachedCargo=null;
        baseCapacity=2; baseCargoPenalty=0.10;
        actionView=null; keyPulseAt=0; interactionUntil=0; interactionError="";
        cutterChargeTicks=12;
        tradeRange=3;
        specialization=null; specializationDeadline=0; specializationLocked=false; playerClass=null;
        rules=org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig.DEFAULT; rulesReady=false;
        matchId=null; epoch=null; contract=null; faction=null; phase=EchoPhase.FINISHED; cargo.clear(); actionId=null;
        skillReadyAt=0; skillBuffUntil=0; skillPendingUntil=0;
        rollReadyAt=0; rollUntil=0;
        trade=null; contractProgress=null; blackCargoCount=0;
        selectedCard=null; signals=List.of(); scenes=List.of();
        inventory=null; bagSnapshot=null; draft=null; acknowledged=0; requestError=""; projectedTick=0;
        combat=new EchoCombatState.Snapshot(EchoLifeState.HEALTHY,0,0,0,0,0,0);
    }
}
