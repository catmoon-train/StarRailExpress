package org.agmas.noellesroles.game.modes.echo.map;

import java.util.Objects;
import java.util.UUID;

/** 一次开局的一次性路线许可；未知不是通过，已知失败永不产生确认令牌。 */
public final class EchoRoutePermit {
    public enum Status { SCANNING, MANUAL, AUTHORIZED, FAILED, CONSUMED }
    public enum Result { PASSED, MANUAL, FAILED }
    public record Stamp(String configuration,long terrainRevision) {
        public Stamp { Objects.requireNonNull(configuration); }
    }
    public static final long SCAN_TTL=1200, CONFIRM_TTL=600, USE_TTL=100;
    private final Stamp stamp;
    private final long started;
    private long deadline;
    private Status status=Status.SCANNING;
    private UUID nonce;
    public EchoRoutePermit(Stamp stamp,long now) { this.stamp=Objects.requireNonNull(stamp); started=now; deadline=Math.addExact(now,SCAN_TTL); }
    public Status status() { return status; }
    public UUID nonce() { return nonce; }
    public boolean valid(Stamp current,long now) {
        if(status==Status.FAILED || status==Status.CONSUMED) return false;
        if(!stamp.equals(current) || now<started || now>=deadline) { fail(); return false; }
        return true;
    }
    public void complete(Result result,Stamp current,long now) {
        if(status!=Status.SCANNING || !valid(current,now)) return;
        switch(result) {
            case FAILED -> fail();
            case MANUAL -> { status=Status.MANUAL; nonce=UUID.randomUUID(); deadline=Math.addExact(now,CONFIRM_TTL); }
            case PASSED -> { status=Status.AUTHORIZED; deadline=Math.addExact(now,USE_TTL); }
        }
    }
    public boolean confirm(UUID supplied,Stamp current,long now) {
        UUID expected=nonce; nonce=null;
        if(status!=Status.MANUAL || !valid(current,now) || expected==null || !expected.equals(supplied)) { fail(); return false; }
        status=Status.AUTHORIZED; deadline=Math.addExact(now,USE_TTL); return true;
    }
    public boolean consume(Stamp current,long now) {
        if(status!=Status.AUTHORIZED || !valid(current,now)) { fail(); return false; }
        status=Status.CONSUMED; nonce=null; return true;
    }
    public void fail() { status=Status.FAILED; nonce=null; }
}
