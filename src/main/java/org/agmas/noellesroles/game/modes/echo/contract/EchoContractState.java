package org.agmas.noellesroles.game.modes.echo.contract;

import org.agmas.noellesroles.game.modes.echo.state.*;
import java.util.*;

/** 个人契约只消费服务端已提交事实；成功不可逆，拾荒者在正常终局才决胜。 */
public final class EchoContractState {
    public enum Status { ACTIVE, ARMED, SUCCEEDED, FAILED, VOID }
    public record Snapshot(String status,int primary,int secondary,long succeededAt,String dropoff,boolean retireReady) { }
    private final EchoContract contract;
    private final Set<UUID> packs=new HashSet<>(),deliveries=new HashSet<>(),trades=new HashSet<>();
    private final EnumSet<EchoFaction> partners=EnumSet.noneOf(EchoFaction.class);
    private final Map<UUID,Set<UUID>> used=new HashMap<>();
    private Status status=Status.ACTIVE;
    private long succeededAt=-1;
    public EchoContractState(EchoContract contract) { this.contract=Objects.requireNonNull(contract); }
    public EchoContract contract() { return contract; }
    public Status status() { return status; }
    public boolean succeeded() { return status==Status.SUCCEEDED; }
    public boolean armed() { return status==Status.ARMED; }
    public boolean active() { return status==Status.ACTIVE || status==Status.ARMED; }
    public boolean mayRetire() { return succeeded() && !contract.requiresFinalSurvival(); }
    public void resolved(UUID pack) {
        if(contract!=EchoContract.SCAVENGER || !active() || packs.size()>=3) return;
        packs.add(pack); if(packs.size()==3) status=Status.ARMED;
    }
    public void delivered(UUID instance,long now) {
        if(contract!=EchoContract.SMUGGLER || !active()) return;
        deliveries.add(instance); if(deliveries.size()>=2) succeed(now);
    }
    public void traded(UUID event,UUID peer,EchoFaction faction,List<UUID> outgoing,List<UUID> incoming,long now) {
        if(contract!=EchoContract.INFORMATION_DEALER || !active() || trades.contains(event) || outgoing.isEmpty() || incoming.isEmpty()
                || faction==EchoFaction.BROKER || faction==null) return;
        Set<UUID> resources=new HashSet<>(outgoing); resources.addAll(incoming);
        if(resources.stream().anyMatch(used.getOrDefault(peer,Set.of())::contains)) return;
        trades.add(event); used.computeIfAbsent(peer,id -> new HashSet<>()).addAll(resources); partners.add(faction);
        if(partners.contains(EchoFaction.CARRIER) && partners.contains(EchoFaction.INTERCEPTOR)) succeed(now);
    }
    public void keyConsumed(long now) { if(contract==EchoContract.KEY_DEALER && active()) succeed(now); }
    private void succeed(long now) { status=Status.SUCCEEDED; succeededAt=now; }
    public void depart() { if(active()) status=Status.FAILED; }
    public void finish(boolean normal,boolean online,EchoLifeState life,long now) {
        if(!normal) { status=Status.VOID; return; }
        if(contract==EchoContract.SCAVENGER && armed() && online && life.standing()) succeed(now);
        else if(active()) status=Status.FAILED;
    }
    public Snapshot snapshot(String dropoff,boolean retireReady) {
        int primary=switch(contract) {
            case SMUGGLER -> deliveries.size(); case SCAVENGER -> packs.size();
            case INFORMATION_DEALER -> partners.contains(EchoFaction.CARRIER)?1:0; case KEY_DEALER -> succeeded()?1:0;
        };
        return new Snapshot(status.name(),primary,contract==EchoContract.INFORMATION_DEALER && partners.contains(EchoFaction.INTERCEPTOR)?1:0,succeededAt,dropoff,retireReady);
    }
}
