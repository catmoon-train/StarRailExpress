package org.agmas.noellesroles.game.modes.echo.objective;

/** 核心门和撤离门分开计时；过载为整个截流阵营共享一次。 */
public final class EchoGateState {
    private final int overloadTicks;
    public EchoGateState() { this(600); }
    public EchoGateState(int overloadTicks) { if(overloadTicks<=0) throw new IllegalArgumentException("过载时长必须大于0"); this.overloadTicks=overloadTicks; }
    private long coreOpenUntil, evacuationClosedUntil;
    private boolean evacuating, overloadUsed;
    public boolean coreOpen(long now) { return evacuating || now<coreOpenUntil; }
    public boolean evacuationOpen(long now) { return evacuating && now>=evacuationClosedUntil; }
    public boolean overloadUsed() { return overloadUsed; }
    public long coreOpenUntil() { return coreOpenUntil; }
    public long evacuationClosedUntil() { return evacuationClosedUntil; }
    public boolean openCore(long now,int ticks) {
        if(ticks<=0 || coreOpen(now)) return false;
        coreOpenUntil=Math.addExact(now,ticks); return true;
    }
    public boolean extendCore(long now,int extra,int maximum) {
        if(evacuating || !coreOpen(now) || extra<=0 || maximum<=0) return false;
        long deadline=Math.min(coreOpenUntil+extra,now+maximum);
        if(deadline<=coreOpenUntil) return false;
        coreOpenUntil=deadline; return true;
    }
    public void beginEvacuation() { evacuating=true; }
    public boolean overload(long now) {
        if(!evacuationOpen(now) || overloadUsed) return false;
        overloadUsed=true; evacuationClosedUntil=now+overloadTicks; return true;
    }
}
