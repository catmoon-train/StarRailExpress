package org.agmas.noellesroles.game.modes.echo.inventory;

import org.agmas.noellesroles.game.modes.echo.cards.EchoCardRegistry;
import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;
import java.util.*;
import static org.agmas.noellesroles.game.modes.echo.inventory.EchoResourceLedger.Location;

/** 六槽唯一库存；先规划完整布局，再原子提交台账。快照只含不可变实例。 */
public final class EchoInventory {
    public record Slot(List<EchoResourceInstance> items,UUID reservation) {
        public Slot { items=List.copyOf(items); if(reservation!=null && !items.isEmpty()) throw new IllegalArgumentException("保留格有资源"); }
        public boolean empty() { return items.isEmpty() && reservation==null; }
    }
    public record Snapshot(long revision,List<Slot> slots,Map<UUID,UUID> locks,boolean keyReserved) {
        public Snapshot { slots=List.copyOf(slots); locks=Map.copyOf(locks); }
    }
    private final UUID owner;
    private final EchoResourceLedger ledger;
    private List<Slot> slots=new ArrayList<>(Collections.nCopies(6,new Slot(List.of(),null)));
    private final Map<UUID,UUID> locks=new HashMap<>();
    private boolean keyReserved;
    private long revision;
    public EchoInventory(UUID owner,EchoResourceLedger ledger) { this.owner=Objects.requireNonNull(owner); this.ledger=Objects.requireNonNull(ledger); }
    public UUID owner() { return owner; }
    public long revision() { return revision; }
    public Snapshot snapshot() { return new Snapshot(revision,slots,locks,keyReserved); }
    public List<EchoResourceInstance> items() { return slots.stream().flatMap(s -> s.items().stream()).toList(); }
    public Optional<EchoResourceInstance> find(UUID id) { return items().stream().filter(i -> i.instanceId().equals(id)).findFirst(); }
    public int slot(UUID id) { for(int n=0;n<6;n++) if(slots.get(n).items().stream().anyMatch(i -> i.instanceId().equals(id))) return n; return -1; }
    public Optional<EchoResourceInstance> key() { return items().stream().filter(EchoResourceInstance::key).findFirst(); }
    public boolean keyAvailable() { return key().isEmpty() && !keyReserved; }
    public boolean locked(UUID id) { return locks.containsKey(id); }
    public int emptySlot() { for(int n=0;n<6;n++) if(slots.get(n).empty()) return n; return -1; }
    public void expect(long expected) { if(expected!=revision) throw new IllegalStateException("REVISION_MISMATCH"); }
    public void reserveKey(boolean reserve) {
        if(keyReserved==reserve) return;
        if(reserve && key().isPresent()) throw new IllegalStateException("KEY_LIMIT");
        keyReserved=reserve; revision++;
    }
    public int reserve(UUID token,long expected) {
        expect(expected); Objects.requireNonNull(token);
        if(slots.stream().anyMatch(s -> token.equals(s.reservation()))) throw new IllegalStateException("LOCKED");
        int slot=emptySlot(); if(slot<0) throw new IllegalStateException("FULL");
        var plan=new ArrayList<>(slots); plan.set(slot,new Slot(List.of(),token)); slots=plan; revision++; return slot;
    }
    public void release(UUID token) {
        var plan=new ArrayList<>(slots); boolean changed=false;
        for(int n=0;n<6;n++) if(token.equals(plan.get(n).reservation())) { plan.set(n,new Slot(List.of(),null)); changed=true; }
        if(changed) { slots=plan; revision++; }
    }
    public void lock(UUID id,UUID token,long expected) {
        expect(expected); require(id); if(locks.containsKey(id)) throw new IllegalStateException("LOCKED");
        locks.put(id,token); revision++;
    }
    public void unlock(UUID token) { if(locks.values().removeIf(token::equals)) revision++; }
    /** 报价锁整批替换；无效新报价不得先释放旧锁。 */
    public void quote(UUID token,List<UUID> ids,long expected) {
        expect(expected); Objects.requireNonNull(token);
        if(ids.size()>2 || new HashSet<>(ids).size()!=ids.size()) throw new IllegalStateException("INVALID_OFFER");
        for(UUID id:ids) requireUnlocked(id,token);
        var old=locks.entrySet().stream().filter(e -> token.equals(e.getValue())).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
        if(old.equals(new HashSet<>(ids))) return;
        locks.values().removeIf(token::equals); ids.forEach(id -> locks.put(id,token)); revision++;
    }
    public boolean canReceive(EchoResourceInstance item) {
        try { var plan=new ArrayList<>(slots); place(plan,item,locks); validate(plan,keyReserved); return true; }
        catch(IllegalStateException failure) { return false; }
    }
    public void receive(EchoResourceInstance item,Location from,long expected) {
        expect(expected); requireMatch(item); var plan=new ArrayList<>(slots); int index=place(plan,item,locks); validate(plan,keyReserved);
        ledger.move(ledger.matchId(),item.instanceId(),from,Location.inventory(owner,index)); slots=plan; revision++;
    }
    public void remove(UUID id,Location destination,long expected,UUID token) {
        expect(expected); requireUnlocked(id,token); int index=slot(id); var plan=new ArrayList<>(slots); take(plan,id); validate(plan,keyReserved);
        ledger.move(ledger.matchId(),id,Location.inventory(owner,index),destination); slots=plan; locks.remove(id); revision++;
    }
    public void move(UUID id,int destination,long expected) {
        expect(expected); requireUnlocked(id,null); checkSlot(destination); int original=slot(id); if(original==destination) return;
        var item=require(id); var plan=new ArrayList<>(slots); take(plan,id); put(plan,destination,item,locks); validate(plan,keyReserved);
        ledger.move(ledger.matchId(),id,Location.inventory(owner,original),Location.inventory(owner,destination)); slots=plan; revision++;
    }
    public EchoResourceInstance craft(UUID first,UUID second,EchoFaction faction,long expected) {
        expect(expected); requireUnlocked(first,null); requireUnlocked(second,null);
        var a=require(first); var b=require(second);
        var output=EchoCardRegistry.craftOutput(first,a.definitionId(),second,b.definitionId(),faction).orElseThrow(() -> new IllegalStateException("INVALID_RECIPE"));
        int at=Math.min(slot(first),slot(second)); var plan=new ArrayList<>(slots); take(plan,first); take(plan,second);
        var created=EchoResourceInstance.card(ledger.matchId(),UUID.randomUUID(),output,UUID.randomUUID()); put(plan,at,created,locks); validate(plan,keyReserved);
        ledger.commit(ledger.matchId(),List.of(new EchoResourceLedger.Move(first,Location.inventory(owner,slot(first)),Location.consumed()),
                new EchoResourceLedger.Move(second,Location.inventory(owner,slot(second)),Location.consumed())),Map.of(created.instanceId(),Location.inventory(owner,at)));
        slots=plan; revision++; return created;
    }
    /** OFFERED输出入保留格；KEY配额在同一事务转换，不可先释放后捡另一把钥匙。 */
    public void resolve(UUID token,EchoResourceInstance output) {
        requireMatch(output); if(output.key() && !keyReserved) throw new IllegalStateException("KEY_LIMIT");
        int index=-1;
        for(int n=0;n<6;n++) if(token.equals(slots.get(n).reservation())) index=n;
        if(index<0) throw new IllegalStateException("INVALID_OFFER");
        var plan=new ArrayList<>(slots); plan.set(index,new Slot(List.of(output),null)); validate(plan,false);
        ledger.commit(ledger.matchId(),List.of(),Map.of(output.instanceId(),Location.inventory(owner,index)));
        slots=plan; keyReserved=false; revision++;
    }
    /** 双方最终布局先验；允许全满一换一，不先扣A再校验B。 */
    public static void exchange(EchoInventory a,List<UUID> offeredA,long revisionA,EchoInventory b,List<UUID> offeredB,long revisionB,UUID trade) {
        var plan=planExchange(a,offeredA,revisionA,b,offeredB,revisionB,trade);
        a.ledger.batchMove(a.ledger.matchId(),plan.moves());
        a.slots=plan.a(); b.slots=plan.b(); offeredA.forEach(a.locks::remove); offeredB.forEach(b.locks::remove); a.revision++; b.revision++;
    }
    public static void previewExchange(EchoInventory a,List<UUID> offeredA,EchoInventory b,List<UUID> offeredB,UUID trade) {
        planExchange(a,offeredA,a.revision(),b,offeredB,b.revision(),trade);
    }
    private record ExchangePlan(List<Slot> a,List<Slot> b,List<EchoResourceLedger.Move> moves) { }
    private static ExchangePlan planExchange(EchoInventory a,List<UUID> offeredA,long revisionA,EchoInventory b,List<UUID> offeredB,long revisionB,UUID trade) {
        if(a==b || a.owner.equals(b.owner) || a.ledger!=b.ledger) throw new IllegalStateException("INVALID_SESSION");
        a.expect(revisionA); b.expect(revisionB);
        if(offeredA.size()>2 || offeredB.size()>2 || new HashSet<>(offeredA).size()!=offeredA.size() || new HashSet<>(offeredB).size()!=offeredB.size()) throw new IllegalStateException("INVALID_OFFER");
        var planA=new ArrayList<>(a.slots); var planB=new ArrayList<>(b.slots);
        List<EchoResourceInstance> itemsA=new ArrayList<>(),itemsB=new ArrayList<>();
        for(UUID id:offeredA) { a.requireUnlocked(id,trade); itemsA.add(a.require(id)); take(planA,id); }
        for(UUID id:offeredB) { b.requireUnlocked(id,trade); itemsB.add(b.require(id)); take(planB,id); }
        List<EchoResourceLedger.Move> moves=new ArrayList<>();
        for(var item:itemsA) { int at=place(planB,item,b.locks); moves.add(new EchoResourceLedger.Move(item.instanceId(),Location.inventory(a.owner,a.slot(item.instanceId())),Location.inventory(b.owner,at))); }
        for(var item:itemsB) { int at=place(planA,item,a.locks); moves.add(new EchoResourceLedger.Move(item.instanceId(),Location.inventory(b.owner,b.slot(item.instanceId())),Location.inventory(a.owner,at))); }
        validate(planA,a.keyReserved); validate(planB,b.keyReserved);
        return new ExchangePlan(planA,planB,moves);
    }
    private EchoResourceInstance require(UUID id) { return find(id).orElseThrow(() -> new IllegalStateException("NO_RESOURCE")); }
    private void requireUnlocked(UUID id,UUID token) { require(id); if(locks.containsKey(id) && !Objects.equals(token,locks.get(id))) throw new IllegalStateException("LOCKED"); }
    private void requireMatch(EchoResourceInstance item) { if(!ledger.matchId().equals(item.matchId())) throw new IllegalStateException("INVALID_SESSION"); }
    private static void checkSlot(int n) { if(n<0 || n>=6) throw new IllegalArgumentException("INVALID_SLOT"); }
    private static void take(List<Slot> plan,UUID id) {
        for(int n=0;n<6;n++) { var old=plan.get(n); var remaining=old.items().stream().filter(i -> !i.instanceId().equals(id)).toList(); if(remaining.size()!=old.items().size()) { plan.set(n,new Slot(remaining,null)); return; } }
        throw new IllegalStateException("NO_RESOURCE");
    }
    private static int place(List<Slot> plan,EchoResourceInstance item,Map<UUID,UUID> locks) {
        for(int n=0;n<6;n++) { var slot=plan.get(n); if(slot.reservation()==null && slot.items().size()==1 && slot.items().getFirst().stacks(item) && !locks.containsKey(slot.items().getFirst().instanceId())) { put(plan,n,item,locks); return n; } }
        for(int n=0;n<6;n++) if(plan.get(n).empty()) { put(plan,n,item,locks); return n; }
        throw new IllegalStateException("FULL");
    }
    private static void put(List<Slot> plan,int at,EchoResourceInstance item,Map<UUID,UUID> locks) {
        var old=plan.get(at);
        if(old.reservation()!=null || old.items().size()>=2 || !old.items().isEmpty() && (!old.items().getFirst().stacks(item) || old.items().stream().anyMatch(i -> locks.containsKey(i.instanceId())))) throw new IllegalStateException("FULL");
        var items=new ArrayList<>(old.items()); items.add(item); plan.set(at,new Slot(items,null));
    }
    private static void validate(List<Slot> plan,boolean reservedKey) {
        Set<UUID> ids=new HashSet<>(); int keys=reservedKey?1:0;
        for(var slot:plan) for(var item:slot.items()) { if(!ids.add(item.instanceId())) throw new IllegalStateException("DUPLICATE"); if(item.key()) keys++; }
        if(keys>1) throw new IllegalStateException("KEY_LIMIT");
    }
}
