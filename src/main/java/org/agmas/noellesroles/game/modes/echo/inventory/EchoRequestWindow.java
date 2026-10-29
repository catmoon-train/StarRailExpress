package org.agmas.noellesroles.game.modes.echo.inventory;

import java.util.*;

/** 每连接128份幂等回复；写请求每秒10次、短突发20，快照独立每秒2次。 */
public final class EchoRequestWindow {
    private final UUID epoch=UUID.randomUUID();
    private final LinkedHashMap<Long,String> results=new LinkedHashMap<>();
    private long highest,lastTick,snapshotStart=-20;
    private int tokens=40,snapshots;
    public UUID epoch() { return epoch; }
    /** 客户端恢复本地状态后也不得重用已确认的请求号，否则会收到旧操作的缓存回复。 */
    public static long nextRequestId(long localSequence,long acknowledged) {
        if(localSequence<0 || acknowledged<0) throw new IllegalArgumentException("请求序号不能为负");
        return Math.incrementExact(Math.max(localSequence,acknowledged));
    }
    public Optional<String> cached(long id) { return Optional.ofNullable(results.get(id)); }
    public String accept(int protocol,UUID epoch,long id,long now,boolean snapshot) {
        if(protocol!=1 || !this.epoch.equals(epoch) || id<=0) return "INVALID_SESSION";
        if(results.containsKey(id)) return "DUPLICATE";
        if(id<=highest) return "STALE_REQUEST";
        highest=id;
        if(snapshot) {
            if(now-snapshotStart>=20) { snapshotStart=now; snapshots=0; }
            return ++snapshots<=2?"":"RATE_LIMIT";
        }
        long elapsed=Math.max(0,now-lastTick); tokens=(int)Math.min(40,tokens+Math.min(40,elapsed)); lastTick=now;
        if(tokens<2) return "RATE_LIMIT"; tokens-=2; return "";
    }
    public void remember(long request,String error) {
        results.put(request,error); while(results.size()>128) results.remove(results.keySet().iterator().next());
    }
}
