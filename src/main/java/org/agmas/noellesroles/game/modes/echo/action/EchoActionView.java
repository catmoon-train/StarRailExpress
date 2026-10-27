package org.agmas.noellesroles.game.modes.echo.action;

import java.util.*;

/** 本人引导的只读投影；到达末端不意味着客户端获准宣布完成。 */
public record EchoActionView(UUID id,String kind,long startedAt,int duration) {
    private static final Set<String> KINDS=Set.of("CRYSTAL","BLACK_NODE","BLACK_DROPOFF","BEACON","ALTAR","RESCUE","CRATE","DROP","CORE","FREE_EXIT");
    public EchoActionView {
        Objects.requireNonNull(id);
        if(!KINDS.contains(kind) || startedAt<0 || duration<1 || duration>72000 || startedAt>Long.MAX_VALUE-duration) throw new IllegalArgumentException("无效引导投影");
    }
    public int progress(long now) { return (int)Math.min(999,(now<=startedAt?0:Math.min(duration,now-startedAt))*1000/duration); }
    public long remaining(long now) { return Math.max(0,startedAt+duration-now); }
}
