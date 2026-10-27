package org.agmas.noellesroles.game.modes.echo.runtime;

import java.util.Objects;
import java.util.UUID;

/** 单局单次宽限；只使用模式时钟，连接恢复不修改任何玩法截止时间。 */
public final class EchoConnectionState {
    public static final int GRACE_TICKS=600;
    public enum Status { CONNECTED, GRACE, EXPIRED }
    private final UUID matchId;
    private Status status=Status.CONNECTED;
    private boolean used;
    private long deadline;
    public EchoConnectionState(UUID matchId) { this.matchId=Objects.requireNonNull(matchId); }
    public Status status() { return status; }
    public long deadline() { return deadline; }
    public boolean connected() { return status==Status.CONNECTED; }
    public boolean inGrace(long now) { return status==Status.GRACE && now<deadline; }
    public boolean disconnect(long now) {
        if(status!=Status.CONNECTED) return false;
        if(used) { status=Status.EXPIRED; return false; }
        used=true; deadline=Math.addExact(now,GRACE_TICKS); status=Status.GRACE; return true;
    }
    public boolean reconnect(UUID match,long now) {
        if(!matchId.equals(match) || !inGrace(now)) return false;
        status=Status.CONNECTED; return true;
    }
    public boolean expire(long now) {
        if(status!=Status.GRACE || now<deadline) return false;
        status=Status.EXPIRED; return true;
    }
    public void leave() { status=Status.EXPIRED; }
}
