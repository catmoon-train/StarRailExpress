package org.agmas.noellesroles.game.modes.echo.contract;

import java.util.*;

/** 黑晶点各初始一枚，采后60秒恢复；元数据不复制台账容器，落地仍只许原主拾取。 */
public final class EchoBlackCrystalState {
    public record Crystal(UUID instance,UUID owner,String node) { }
    private final Map<String,Long> ready=new LinkedHashMap<>();
    private final Map<UUID,Crystal> crystals=new HashMap<>();
    private final int recoveryTicks;
    public EchoBlackCrystalState(Collection<String> nodes) { this(nodes,1200); }
    public EchoBlackCrystalState(Collection<String> nodes,int recoveryTicks) {
        if(recoveryTicks<=0) throw new IllegalArgumentException("黑晶恢复周期必须大于0");
        this.recoveryTicks=recoveryTicks; nodes.forEach(n -> ready.put(n,0L));
    }
    public boolean available(String node,long now) { return ready.containsKey(node) && now>=ready.get(node); }
    public void collect(UUID owner,String node,UUID instance,long now) {
        if(!available(node,now) || crystals.containsKey(instance)) throw new IllegalStateException("NO_TARGET");
        crystals.put(instance,new Crystal(instance,owner,node)); ready.put(node,now+recoveryTicks);
    }
    public boolean black(UUID id) { return crystals.containsKey(id); }
    public boolean owned(UUID id,UUID owner) { var c=crystals.get(id); return c!=null && c.owner().equals(owner); }
    public void consume(UUID id) { crystals.remove(id); }
    public void recycle(UUID id,long now) { var c=crystals.remove(id); if(c!=null) ready.put(c.node(),now); }
}
