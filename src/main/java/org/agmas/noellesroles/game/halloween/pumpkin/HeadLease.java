package org.agmas.noellesroles.game.halloween.pumpkin;

/** 头槽租约的纯状态机：重复施加仅延期，原物品只取回一次。 */
public final class HeadLease<T> {
    private T original;
    private long expires;
    private boolean active = true;

    public HeadLease(T original, long expires) {
        this.original = original;
        this.expires = expires;
    }

    public void extend(long until) { if (active) expires = Math.max(expires, until); }
    public boolean expired(long now) { return active && now >= expires; }
    public boolean active() { return active; }
    public T release() {
        if (!active) return null;
        active = false;
        T result = original;
        original = null;
        return result;
    }
}
