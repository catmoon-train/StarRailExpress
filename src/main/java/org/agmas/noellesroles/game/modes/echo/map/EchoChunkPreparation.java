package org.agmas.noellesroles.game.modes.echo.map;

import java.util.ArrayList;
import java.util.List;

/** 有界区块计划与租约；请求不等待生成，完整加载后才允许真实安全检查。 */
public final class EchoChunkPreparation implements AutoCloseable {
    public static final int MAX_CHUNKS=1024, REQUESTS_PER_TICK=4, MAX_PENDING=16;
    public static final long LOAD_TTL=2400;
    public record Chunk(int x,int z) { }
    public interface Access {
        void request(Chunk chunk);
        boolean loaded(Chunk chunk);
        void release(Chunk chunk);
    }
    private final List<Chunk> plan;
    private final List<Chunk> held=new ArrayList<>();
    private final Access access;
    private final long started;
    private boolean closed,ready;
    private int loaded;

    /** 包含碰撞/射线读边缘的两格余量；先算总量再分配，超大地图不发任何票据。 */
    public static List<Chunk> plan(EchoMapConfig.Box area) {
        int minX=(int)Math.floor((area.minX()-2)/16), maxX=(int)Math.floor((Math.ceil(area.maxX())+1)/16);
        int minZ=(int)Math.floor((area.minZ()-2)/16), maxZ=(int)Math.floor((Math.ceil(area.maxZ())+1)/16);
        long size=(long)(maxX-minX+1)*(maxZ-minZ+1);
        if(size<=0 || size>MAX_CHUNKS) throw new IllegalArgumentException("playArea区块计划超过"+MAX_CHUNKS+"，请缩小实战范围");
        List<Chunk> result=new ArrayList<>((int)size);
        for(int x=minX;x<=maxX;x++) for(int z=minZ;z<=maxZ;z++) result.add(new Chunk(x,z));
        return List.copyOf(result);
    }
    public EchoChunkPreparation(EchoMapConfig.Box area,Access access,long now) {
        this.plan=plan(area); this.access=access; started=now;
    }
    public boolean advance(long now) {
        if(closed) throw new IllegalStateException("区块租约已关闭");
        if(ready) return true;
        if(now<started || now-started>=LOAD_TTL) { close(); throw new IllegalStateException("区块准备超时"); }
        try {
            loaded=0;
            for(var chunk:held) if(access.loaded(chunk)) loaded++;
            int budget=Math.min(REQUESTS_PER_TICK,MAX_PENDING-(held.size()-loaded));
            for(int i=0;i<budget && held.size()<plan.size();i++) {
                var chunk=plan.get(held.size());
                held.add(chunk); // 即使后端请求抛错，也尝试撤销本租约的票据。
                access.request(chunk);
            }
            ready=held.size()==plan.size() && loaded==plan.size();
            return ready;
        } catch(RuntimeException failure) { close(); throw failure; }
    }
    public int loaded() { return loaded; }
    public int total() { return plan.size(); }
    @Override public void close() {
        if(closed) return;
        closed=true;
        RuntimeException failure=null;
        for(var chunk:held) try { access.release(chunk); }
        catch(RuntimeException exception) { if(failure==null) failure=exception; else failure.addSuppressed(exception); }
        held.clear();
        if(failure!=null) throw failure;
    }
}
