package org.agmas.noellesroles.game.modes.echo.trade;

import org.agmas.noellesroles.game.modes.echo.inventory.*;
import java.util.*;

/** 局内两阶段交易；不依赖世界，报价锁始终保留在原库存，终态不可再次成交。 */
public final class EchoTradeState {
    public enum Status { PROPOSED, EDITING, COMMITTED, CANCELLED }
    public record Committed(UUID tradeId,UUID a,UUID b,List<EchoResourceInstance> fromA,List<EchoResourceInstance> fromB) {
        public Committed { fromA=List.copyOf(fromA); fromB=List.copyOf(fromB); }
    }
    private final UUID id=UUID.randomUUID();
    private final EchoInventory a,b;
    private final long deadline;
    private final double range;
    private Status status=Status.PROPOSED;
    private List<UUID> offerA=List.of(),offerB=List.of();
    private long version=1,revisionA,revisionB;
    private boolean confirmedA,confirmedB;
    private Committed committed;
    public EchoTradeState(EchoInventory a,EchoInventory b,long deadline,double range) {
        if(a==null || b==null || a.owner().equals(b.owner()) || deadline<0 || !Double.isFinite(range) || range<=0 || range>16) throw new IllegalStateException("INVALID_OFFER");
        this.a=a; this.b=b; this.deadline=deadline; this.range=range; remember();
    }
    public UUID id() { return id; }
    public UUID a() { return a.owner(); }
    public UUID b() { return b.owner(); }
    public long deadline() { return deadline; }
    public double range() { return range; }
    public long version() { return version; }
    public Status status() { return status; }
    public boolean active() { return status==Status.PROPOSED || status==Status.EDITING; }
    public boolean contains(UUID owner) { return a().equals(owner) || b().equals(owner); }
    public UUID peer(UUID owner) { member(owner); return a().equals(owner)?b():a(); }
    public List<UUID> offer(UUID owner) { member(owner); return a().equals(owner)?offerA:offerB; }
    public boolean confirmed(UUID owner) { member(owner); return a().equals(owner)?confirmedA:confirmedB; }
    public Optional<Committed> committed() { return Optional.ofNullable(committed); }
    public List<EchoResourceInstance> resources(UUID owner) {
        var inv=inventory(owner); return offer(owner).stream().map(id -> inv.find(id).orElseThrow(() -> new IllegalStateException("NO_RESOURCE"))).toList();
    }
    public void accept(UUID owner,long expected,long now) {
        member(owner); valid(now);
        if(!b().equals(owner) || status!=Status.PROPOSED || expected!=version) reject("INVALID_OFFER");
        status=Status.EDITING; invalidate(); remember();
    }
    public void offer(UUID owner,List<UUID> ids,long expected,long ownRevision,long now) {
        editing(owner,expected,now); var inv=inventory(owner); inv.expect(ownRevision);
        var next=List.copyOf(ids);
        inv.quote(id,next,ownRevision);
        if(offer(owner).equals(next)) { refresh(); return; }
        if(a().equals(owner)) offerA=next; else offerB=next;
        invalidate(); remember();
    }
    /** 其它库存事务即使未碰报价，也必须撤销双方旧确认。 */
    public boolean refresh() {
        if(!active() || revisionA==a.revision() && revisionB==b.revision()) return false;
        invalidate(); remember(); return true;
    }
    public String layoutError() {
        if(offerA.isEmpty() && offerB.isEmpty()) return "INVALID_OFFER";
        try { EchoInventory.previewExchange(a,offerA,b,offerB,id); return ""; }
        catch(IllegalStateException rejected) { return rejected.getMessage(); }
    }
    public boolean confirm(UUID owner,long expected,long ownRevision,long now) {
        editing(owner,expected,now); inventory(owner).expect(ownRevision);
        String error=layoutError(); if(!error.isEmpty()) reject(error);
        if(a().equals(owner)) confirmedA=true; else confirmedB=true;
        if(!confirmedA || !confirmedB) return false;
        var event=new Committed(id,a(),b(),resources(a()),resources(b()));
        try { EchoInventory.exchange(a,offerA,revisionA,b,offerB,revisionB,id); }
        catch(IllegalStateException failure) { cancel(id); throw failure; }
        committed=event; status=Status.COMMITTED; version++; return true;
    }
    public boolean expire(long now) { if(active() && now>=deadline) return cancel(id); return false; }
    /** 精确令牌使旧回调不能释放新交易的锁。 */
    public boolean cancel(UUID expectedId) {
        if(!id.equals(expectedId) || !active()) return false;
        a.unlock(id); b.unlock(id); status=Status.CANCELLED; confirmedA=false; confirmedB=false; version++; return true;
    }
    private void editing(UUID owner,long expected,long now) {
        member(owner); valid(now); refresh();
        if(status!=Status.EDITING) reject("INVALID_OFFER");
        if(expected!=version) reject("REVISION_MISMATCH");
    }
    private void valid(long now) { if(expire(now) || !active()) reject("INVALID_OFFER"); }
    private EchoInventory inventory(UUID owner) { member(owner); return a().equals(owner)?a:b; }
    private void member(UUID owner) { if(!contains(owner)) reject("INVALID_SESSION"); }
    private void invalidate() { version++; confirmedA=false; confirmedB=false; }
    private void remember() { revisionA=a.revision(); revisionB=b.revision(); }
    private static void reject(String error) { throw new IllegalStateException(error); }
}
