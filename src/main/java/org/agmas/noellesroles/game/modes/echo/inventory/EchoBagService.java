package org.agmas.noellesroles.game.modes.echo.inventory;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.modes.echo.cards.*;
import org.agmas.noellesroles.game.modes.echo.objective.EchoDropService;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoMatch;
import org.agmas.noellesroles.game.modes.echo.state.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.agmas.noellesroles.game.modes.echo.inventory.EchoResourceLedger.Location;

/** 库存、绑定包、配额与世界展示的唯一事务入口；不从ItemStack创建合法实例。 */
public final class EchoBagService {
    private static final ResourceLocation OPENING=ResourceLocation.fromNamespaceAndPath("noellesroles","echo_open_pack");
    private static final Set<String> ERRORS=Set.of("REVISION_MISMATCH","FULL","KEY_LIMIT","LOCKED","NO_RESOURCE","INVALID_RECIPE","INVALID_OFFER","BUSY","COOLDOWN","WRONG_PHASE","PAUSED","DOWNED","INVALID_SESSION","INVALID_SLOT","RATE_LIMIT","STALE_REQUEST","WRONG_FACTION","PASSIVE","NO_TARGET","OUT_OF_RANGE","NO_LOS","EFFECT_ACTIVE","EFFECT_UNAVAILABLE");
    private static final class Draft {
        final UUID pack,offer=UUID.randomUUID();
        final EchoPackDraft state;
        long channelEnd;
        boolean removed;
        Draft(EchoResourceInstance pack,org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig rules) { this.pack=pack.instanceId(); state=new EchoPackDraft(this.pack,pack.quality(),rules); }
    }
    private final EchoMatch match;
    private final ServerLevel level;
    private final EchoResourceLedger ledger;
    private final EchoDropService drops;
    private final Map<UUID,EchoInventory> inventories=new LinkedHashMap<>();
    private final Map<UUID,EchoRequestWindow> requests=new HashMap<>();
    private final Map<UUID,EchoResourceInstance> catalog=new HashMap<>();
    private final Map<UUID,UUID> pending=new LinkedHashMap<>();
    private final Map<String,UUID> rewards=new HashMap<>();
    private final Map<UUID,Draft> drafts=new HashMap<>();
    private final Map<UUID,Integer> resolvedPacks=new HashMap<>();
    private final Random random=new Random();
    public EchoBagService(EchoMatch match,ServerLevel level) {
        this.match=match; this.level=level; ledger=match.objectives().ledger(); drops=match.objectives().drops();
        for(ServerPlayer player:match.onlinePlayers()) {
            var inventory=new EchoInventory(player.getUUID(),ledger); var window=new EchoRequestWindow();
            inventories.put(player.getUUID(),inventory); requests.put(player.getUUID(),window);
            data(player).attachInventory(inventory,window.epoch(),match.faction(player.getUUID()));
        }
    }
    private EchoRoleData data(ServerPlayer player) { return Objects.requireNonNull(RoleData.getNullable(EchoRoleData.class,player)); }
    public EchoInventory inventory(UUID owner) { return inventories.get(owner); }
    private void project(UUID owner,java.util.function.Consumer<EchoRoleData> change) {
        var player=match.connectedPlayer(owner);
        if(player!=null) RoleData.getOptional(EchoRoleData.class,player).filter(d -> match.id().equals(d.matchId())).ifPresent(change);
    }
    public void attach(ServerPlayer player) {
        UUID owner=player.getUUID(); var window=new EchoRequestWindow(); requests.put(owner,window);
        data(player).attachInventory(inventory(owner),window.epoch(),match.faction(owner));
        data(player).attachCargo(match.cargo(owner));
        var draft=drafts.get(owner); if(draft!=null) project(owner,draft);
        changed(player);
    }
    /** 清理旧连接的写序列，引导取消但保留绑定包、候选与原有截止。 */
    public void disconnect(ServerPlayer player) {
        cancel(player.getUUID(),null); requests.remove(player.getUUID()); clearOpening(player);
        RoleData.getOptional(EchoRoleData.class,player).filter(d -> match.id().equals(d.matchId())).ifPresent(EchoRoleData::clear);
    }
    public Optional<EchoResourceInstance> resource(UUID id) { return Optional.ofNullable(catalog.get(id)); }
    public int resolvedPacks(UUID owner) { return resolvedPacks.getOrDefault(owner,0); }
    /** 先按接收方当前容量筛选，再随机抽一包；交换计划与台账同事务提交。 */
    public void stealPack(ServerPlayer actor,ServerPlayer victim) {
        var to=inventory(actor.getUUID()); var from=inventory(victim.getUUID());
        var available=from.items().stream().filter(r -> r.kind()==EchoResourceInstance.Kind.PACK && !from.locked(r.instanceId()) && to.canReceive(r)).toList();
        if(available.isEmpty()) throw new IllegalStateException(from.items().stream().anyMatch(r -> r.kind()==EchoResourceInstance.Kind.PACK && !from.locked(r.instanceId()))?"FULL":"NO_TARGET");
        var item=available.get(random.nextInt(available.size()));
        EchoInventory.exchange(from,List.of(item.instanceId()),from.revision(),to,List.of(),to.revision(),null);
        changed(victim); changed(actor);
    }
    public boolean busy(UUID owner) {
        Draft draft=drafts.get(owner); return draft!=null && (draft.state.state()==EchoPackDraft.State.CHANNELING || draft.state.state()==EchoPackDraft.State.OFFERED);
    }
    public boolean opening(UUID owner) { Draft draft=drafts.get(owner); return draft!=null && draft.state.state()==EchoPackDraft.State.CHANNELING; }
    public int keyCount() {
        return (int)catalog.values().stream().filter(EchoResourceInstance::key).filter(r -> ledger.find(r.instanceId()).filter(p -> !p.terminal()).isPresent()).count()
                +(int)drafts.values().stream().filter(d -> d.state.requiresKeyReservation()).count();
    }
    public void registerKey(UUID id) {
        catalog.putIfAbsent(id,EchoResourceInstance.card(match.id(),id,EchoCardRegistry.require("noellesroles:echo/sound_key"),id));
    }
    public boolean canPickup(ServerPlayer player,UUID instance) {
        var resource=catalog.get(instance); return resource!=null && !busy(player.getUUID()) && inventory(player.getUUID()).canReceive(resource);
    }
    public void pickup(ServerPlayer player,EchoDropService.Drop drop) {
        if(!canPickup(player,drop.instanceId())) return;
        var inventory=inventory(player.getUUID()); inventory.receive(catalog.get(drop.instanceId()),Location.ground(drop.dropId()),inventory.revision());
        drops.remove(drop.dropId()); changed(player);
    }
    public void grant(ServerPlayer player,EchoDraftService.PackQuality quality,String fact) {
        if(rewards.containsKey(fact)) return;
        UUID instance=UUID.randomUUID(); UUID event=UUID.nameUUIDFromBytes((match.id()+":"+fact).getBytes(StandardCharsets.UTF_8));
        var resource=EchoResourceInstance.pack(match.id(),instance,quality,event);
        ledger.register(match.id(),instance,new Location(EchoResourceLedger.Kind.PENDING_DELIVERY,null,-1,instance));
        catalog.put(instance,resource); pending.put(instance,player.getUUID()); rewards.put(fact,instance);
        deliver(instance,player);
    }
    private void deliver(UUID instance,ServerPlayer player) {
        var resource=catalog.get(instance); var inventory=inventory(player.getUUID());
        if(match.life(player.getUUID()).potentiallyEscapable() && !busy(player.getUUID()) && inventory.canReceive(resource)) {
            inventory.receive(resource,ledger.find(instance).orElseThrow(),inventory.revision()); pending.remove(instance); changed(player); return;
        }
        var kind=resource.key()?EchoDropService.Kind.KEY:resource.kind()==EchoResourceInstance.Kind.PACK?EchoDropService.Kind.PACK:EchoDropService.Kind.CARD;
        var display=drops.spawn(instance,kind,match.safeAnchor(player,false),match.matchTick()+(resource.key()?match.rules().logistics().keyGroundTicks():match.rules().logistics().resourceGroundTicks()),null);
        if(display.isPresent()) { ledger.move(match.id(),instance,ledger.find(instance).orElseThrow(),Location.ground(display.get().dropId())); pending.remove(instance); }
    }
    /** 接收器已排到会话主线程；旧epoch、乱序和重复请求均不重复业务。 */
    public void request(ServerPlayer player,EchoInventoryPayload payload) {
        if(!match.id().equals(payload.matchId()) || !requests.containsKey(player.getUUID())) return;
        var window=requests.get(player.getUUID());
        String error=window.accept(payload.protocol(),payload.epoch(),payload.requestId(),match.matchTick(),payload.operation()==EchoInventoryPayload.Operation.SNAPSHOT);
        if(error.equals("INVALID_SESSION")) return;
        if(error.equals("DUPLICATE")) { data(player).acknowledge(payload.requestId(),window.cached(payload.requestId()).orElse("STALE_REQUEST")); return; }
        if(error.isEmpty()) {
            try { execute(player,payload); }
            catch(IllegalArgumentException | IllegalStateException rejected) {
                if(!ERRORS.contains(rejected.getMessage())) throw rejected;
                error=rejected.getMessage();
            }
        }
        window.remember(payload.requestId(),error); data(player).acknowledge(payload.requestId(),error);
    }
    private void execute(ServerPlayer player,EchoInventoryPayload p) {
        var inventory=inventory(player.getUUID());
        if(p.operation()==EchoInventoryPayload.Operation.SNAPSHOT) { changed(player); return; }
        if(p.operation()==EchoInventoryPayload.Operation.CANCEL_PACK) { cancel(player,p.first()); return; }
        if(p.operation()==EchoInventoryPayload.Operation.TRADE_CANCEL) { match.trades().request(player,p); return; }
        if(SREGameTimeComponent.KEY.get(level).isTimeFrozen()) throw new IllegalStateException("PAUSED");
        if(!match.phase().acceptsActions()) throw new IllegalStateException("WRONG_PHASE");
        if(p.operation()==EchoInventoryPayload.Operation.CHOOSE) {
            if(!match.present(player) || !match.life(player.getUUID()).potentiallyEscapable()) throw new IllegalStateException("DOWNED");
            inventory.expect(p.revision()); choose(player,p.first(),p.option()); return;
        }
        if(!match.standing(player)) throw new IllegalStateException("DOWNED");
        inventory.expect(p.revision());
        if(busy(player.getUUID())) throw new IllegalStateException("BUSY");
        if(p.operation().trade()) { match.trades().request(player,p); return; }
        if(p.operation()==EchoInventoryPayload.Operation.BEGIN_PACK && match.trades().busy(player.getUUID())) throw new IllegalStateException("BUSY");
        switch(p.operation()) {
            case RETIRE -> match.contracts().retire(player,p.option()==1);
            case DISMANTLE_SCENE -> { if(!match.cards().scenes().dismantle(player)) throw new IllegalStateException("NO_TARGET"); }
            case EQUIP_CARD -> match.cards().equip(player,p.first());
            case USE_CARD -> match.cards().use(player,p);
            case BEGIN_PACK -> begin(player,p.first());
            case MOVE -> inventory.move(p.first(),p.option(),p.revision());
            case DROP -> {
                if(inventory.locked(p.first())) throw new IllegalStateException("LOCKED");
                if(!drop(player,p.first(),false)) throw new IllegalStateException(inventory.find(p.first()).isEmpty()?"NO_RESOURCE":"FULL");
            }
            case CRAFT -> {
                var a=inventory.find(p.first()).orElseThrow(() -> new IllegalStateException("NO_RESOURCE"));
                var b=inventory.find(p.second()).orElseThrow(() -> new IllegalStateException("NO_RESOURCE"));
                var output=EchoCardRegistry.craftOutput(p.first(),a.definitionId(),p.second(),b.definitionId(),match.faction(player.getUUID()));
                if(output.isPresent() && output.get().isKey() && !a.key() && !b.key() && keyCount()>=match.playerCount()+3) throw new IllegalStateException("KEY_LIMIT");
                var created=inventory.craft(p.first(),p.second(),match.faction(player.getUUID()),p.revision()); catalog.put(created.instanceId(),created);
                match.contracts().invalidate(p.first()); match.contracts().invalidate(p.second());
            }
            default -> throw new IllegalStateException("INVALID_OFFER");
        }
        changed(player);
    }
    private void begin(ServerPlayer player,UUID packId) {
        var inventory=inventory(player.getUUID());
        var pack=inventory.find(packId).filter(i -> i.kind()==EchoResourceInstance.Kind.PACK).orElseThrow(() -> new IllegalStateException("NO_RESOURCE"));
        Draft draft=drafts.get(player.getUUID());
        if(draft!=null && !draft.pack.equals(packId)) throw new IllegalStateException("BUSY");
        if(draft==null && inventory.locked(packId)) throw new IllegalStateException("LOCKED");
        if(inventory.emptySlot()<0) throw new IllegalStateException("FULL");
        boolean fresh=draft==null; if(fresh) draft=new Draft(pack,match.rules());
        if(!draft.state.begin(player.getUUID(),match.matchTick(),true,EchoDraftService.Context.all(match.faction(player.getUUID()),inventory.keyAvailable(),keyCount()<match.playerCount()+3),random))
            throw new IllegalStateException(draft.state.state()==EchoPackDraft.State.BOUND && match.matchTick()<draft.state.readyAt()?"COOLDOWN":"BUSY");
        if(fresh) { inventory.lock(packId,draft.offer,inventory.revision()); drafts.put(player.getUUID(),draft); }
        inventory.reserve(draft.offer,inventory.revision());
        if(draft.state.requiresKeyReservation()) inventory.reserveKey(true);
        draft.channelEnd=match.matchTick()+match.rules().draft().channelTicks();
        match.objectives().cancelActor(player); player.stopUsingItem();
        var speed=player.getAttribute(Attributes.MOVEMENT_SPEED);
        if(speed!=null) speed.removeModifier(OPENING);
        match.cards().updateSpeed(player);
        org.agmas.noellesroles.game.modes.echo.objective.EchoAudio.nearby(match,player.position(),SoundEvents.BUNDLE_INSERT,match.rules().signals().packSoundRange(),0.8F);
        project(player,draft);
    }
    public void cancel(ServerPlayer player,UUID expectedOffer) { cancel(player.getUUID(),expectedOffer); }
    private void cancel(UUID owner,UUID expectedOffer) {
        Draft draft=drafts.get(owner);
        if(draft==null || expectedOffer!=null && !expectedOffer.equals(draft.offer) && !expectedOffer.equals(draft.pack) || !draft.state.cancel(owner,match.matchTick())) return;
        inventory(owner).release(draft.offer);
        var player=match.connectedPlayer(owner); if(player!=null) clearOpening(player);
        project(owner,draft); if(player!=null) changed(player);
    }
    public void interrupt(ServerPlayer player) { cancel(player,null); }
    private void consumePack(UUID owner,Draft draft) {
        if(draft.removed || !draft.state.consumed()) return;
        var inventory=inventory(owner); inventory.remove(draft.pack,Location.consumed(),inventory.revision(),draft.offer); draft.removed=true;
        var player=match.connectedPlayer(owner); if(player!=null) clearOpening(player);
    }
    private void choose(ServerPlayer player,UUID offer,int index) {
        if(index<0 || index>2) throw new IllegalArgumentException("INVALID_OFFER");
        Draft draft=drafts.get(player.getUUID());
        if(draft==null || !draft.offer.equals(offer)) throw new IllegalStateException("INVALID_OFFER");
        var resolution=draft.state.choose(player.getUUID(),index,match.matchTick()).orElseThrow(() -> new IllegalStateException("INVALID_OFFER"));
        resolve(player.getUUID(),draft,resolution);
    }
    private void resolve(UUID owner,Draft draft,EchoPackDraft.Resolution resolution) {
        consumePack(owner,draft);
        var created=EchoResourceInstance.card(match.id(),UUID.randomUUID(),resolution.chosen(),catalog.get(draft.pack).originEventId());
        var inventory=inventory(owner); inventory.resolve(draft.offer,created); inventory.unlock(draft.offer);
        catalog.put(created.instanceId(),created); drafts.remove(owner); resolvedPacks.merge(owner,1,Integer::sum);
        match.contracts().resolved(owner,draft.pack);
        project(owner,d -> d.projectDraft(null));
        var player=match.connectedPlayer(owner); if(player!=null) changed(player);
    }
    /**
     * 直接发放一张指定卡牌（商店购买用），不经过抽包三选一。
     *
     * <p>仍然走同一套所有权台账：满包时按既有规则落地，不会凭空复制实例。
     *
     * @param definitionId 完整卡牌 ID（{@code noellesroles:echo/<id>}）
     * @return 是否已进入背包或落地
     */
    public boolean grantCard(ServerPlayer player,String definitionId,String fact) {
        var definition=EchoCardRegistry.find(definitionId).orElse(null);
        if(definition==null) return false;
        UUID instance=UUID.randomUUID();
        UUID event=UUID.nameUUIDFromBytes((match.id()+":"+fact).getBytes(StandardCharsets.UTF_8));
        var resource=EchoResourceInstance.card(match.id(),instance,definition,event);
        ledger.register(match.id(),instance,new Location(EchoResourceLedger.Kind.PENDING_DELIVERY,null,-1,instance));
        catalog.put(instance,resource); pending.put(instance,player.getUUID());
        deliver(instance,player);
        return catalog.containsKey(instance);
    }

    public void tick() {
        restrictOpening();
        for(var entry:List.copyOf(drafts.entrySet())) {
            UUID owner=entry.getKey(); Draft draft=entry.getValue(); var player=match.connectedPlayer(owner);
            if(opening(owner) && (player==null || !match.standing(player))) cancel(owner,draft.offer);
            var result=draft.state.advance(match.matchTick()); consumePack(owner,draft);
            if(result.isPresent()) resolve(owner,draft,result.get()); else project(owner,draft);
        }
        if(match.matchTick()%20==0) for(var entry:List.copyOf(pending.entrySet())) {
            var player=match.connectedPlayer(entry.getValue()); if(player!=null) deliver(entry.getKey(),player);
        }
    }
    private void project(ServerPlayer player,Draft draft) { project(player.getUUID(),draft); }
    private void project(UUID owner,Draft draft) {
        project(owner,d -> d.projectDraft(new EchoDraftView(draft.offer,draft.pack,draft.state.state().name(),draft.state.visibleCandidates(owner).stream().map(EchoCardDefinition::id).toList(),
                draft.state.state()==EchoPackDraft.State.CHANNELING?draft.channelEnd:draft.state.choiceEnd(),draft.state.readyAt())));
    }
    /** 限制本身不推进时间；跳跃取消引导，正常水平移动与台阶仍由原版处理。 */
    public void restrictOpening() {
        for(ServerPlayer player:match.onlinePlayers()) if(opening(player.getUUID()) && !player.onGround() && player.getDeltaMovement().y>0.18 && !player.horizontalCollision) cancel(player,null);
    }
    private void clearOpening(ServerPlayer player) {
        var speed=player.getAttribute(Attributes.MOVEMENT_SPEED); if(speed!=null) speed.removeModifier(OPENING);
        if(match.result()==null && match.present(player)) match.cards().updateSpeed(player);
    }
    public void changed(ServerPlayer player) {
        var data=RoleData.getNullable(EchoRoleData.class,player);
        if(data==null || !match.id().equals(data.matchId()) || data.inventory()!=inventory(player.getUUID())) return;
        while(match.cargo(player.getUUID()).items().size()>data.capacity()) if(!match.objectives().dropCarried(player,false,match.safeAnchor(player,false))) throw new IllegalStateException("物流超额掉货失败");
        if(match.trades()!=null) match.trades().inventoryChanged(player.getUUID());
        data.projectInventory();
        org.agmas.noellesroles.game.modes.echo.content.EchoResourceItem.sync(player);
    }
    public boolean drop(ServerPlayer player,UUID instance,boolean terminal) { return drop(player.getUUID(),instance,terminal,match.safeAnchor(player,false)); }
    private boolean drop(UUID owner,UUID instance,boolean terminal,Vec3 anchor) {
        var inventory=inventory(owner); var resource=inventory.find(instance).orElse(null);
        if(resource==null || inventory.locked(instance) || !terminal && busy(owner)) return false;
        var display=drops.spawn(instance,resource.key()?EchoDropService.Kind.KEY:resource.kind()==EchoResourceInstance.Kind.PACK?EchoDropService.Kind.PACK:EchoDropService.Kind.CARD,
                anchor,match.matchTick()+(resource.key()?match.rules().logistics().keyGroundTicks():match.rules().logistics().resourceGroundTicks()),null).orElseThrow();
        try { inventory.remove(instance,Location.ground(display.dropId()),inventory.revision(),null); }
        catch(RuntimeException failure) { drops.remove(display.dropId()); throw failure; }
        match.contracts().invalidate(instance);
        var player=match.connectedPlayer(owner); if(player!=null) changed(player); return true;
    }
    public void depart(ServerPlayer player) { depart(player.getUUID(),match.safeAnchor(player,false)); }
    public void depart(UUID owner,Vec3 anchor) {
        match.trades().interrupt(owner);
        Draft draft=drafts.get(owner); var inventory=inventory(owner);
        if(draft!=null) {
            if(draft.state.state()==EchoPackDraft.State.OFFERED) {
                var resolution=draft.state.choose(owner,0,match.matchTick()).orElseThrow(); resolve(owner,draft,resolution);
            } else {
                inventory.remove(draft.pack,Location.recycled(),inventory.revision(),draft.offer); draft.state.discard();
                inventory.release(draft.offer); inventory.unlock(draft.offer); inventory.reserveKey(false); drafts.remove(owner); project(owner,d -> d.projectDraft(null));
            }
        }
        var player=match.connectedPlayer(owner); if(player!=null) clearOpening(player);
        for(var item:List.copyOf(inventory.items())) if(!drop(owner,item.instanceId(),true,anchor)) throw new IllegalStateException("退出资源仍被锁定");
        for(var entry:List.copyOf(pending.entrySet())) if(entry.getValue().equals(owner)) {
            var resource=catalog.get(entry.getKey());
            var display=drops.spawn(entry.getKey(),resource.key()?EchoDropService.Kind.KEY:resource.kind()==EchoResourceInstance.Kind.PACK?EchoDropService.Kind.PACK:EchoDropService.Kind.CARD,
                    anchor,match.matchTick()+(resource.key()?match.rules().logistics().keyGroundTicks():match.rules().logistics().resourceGroundTicks()),null).orElseThrow();
            ledger.move(match.id(),entry.getKey(),ledger.find(entry.getKey()).orElseThrow(),Location.ground(display.dropId())); pending.remove(entry.getKey());
        }
    }
    public void cleanup() {
        for(ServerPlayer player:match.onlinePlayers()) { clearOpening(player); project(player.getUUID(),d -> d.projectDraft(null)); }
        for(var entry:drafts.entrySet()) {
            entry.getValue().state.discard(); var inventory=inventory(entry.getKey());
            inventory.release(entry.getValue().offer); inventory.unlock(entry.getValue().offer); inventory.reserveKey(false);
        }
        drafts.clear(); requests.clear(); pending.clear();
    }
}
