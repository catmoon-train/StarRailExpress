/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package io.wifi.starrailexpress.client.ponder;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/**
 * 思索玩家模型的皮肤覆盖登记表。
 *
 * <p>必须用对象身份判等：不同段落里的玩家模型共用观看者的 GameProfile（UUID 相同），
 * 用普通 {@code WeakHashMap} 会让后一个模型覆盖前一个模型的皮肤，导致狂暴皮肤串成默认皮肤。
 * 这里用带引用队列的弱身份键，模型被回收后登记自动失效。
 */
public final class PonderSkinAccess {

    private static final ReferenceQueue<AbstractClientPlayer> QUEUE = new ReferenceQueue<>();
    private static final Map<IdentityKey, ResourceLocation> OVERRIDES = new HashMap<>();

    private PonderSkinAccess() {
    }

    private static final class IdentityKey extends WeakReference<AbstractClientPlayer> {
        private final int hash;

        IdentityKey(AbstractClientPlayer player) {
            super(player, QUEUE);
            this.hash = System.identityHashCode(player);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof IdentityKey key)) return false;
            AbstractClientPlayer self = get();
            return self != null && self == key.get();
        }
    }

    private static void drain() {
        Reference<? extends AbstractClientPlayer> cleared;
        while ((cleared = QUEUE.poll()) != null) {
            OVERRIDES.remove(cleared);
        }
    }

    /** 为指定玩家模型登记皮肤覆盖。 */
    public static void override(AbstractClientPlayer player, ResourceLocation texture) {
        if (player == null || texture == null) {
            return;
        }
        synchronized (OVERRIDES) {
            drain();
            OVERRIDES.put(new IdentityKey(player), texture);
        }
    }

    /** 查询皮肤覆盖；未登记时返回 null。 */
    @Nullable
    public static ResourceLocation textureOf(@Nullable AbstractClientPlayer player) {
        if (player == null) {
            return null;
        }
        synchronized (OVERRIDES) {
            drain();
            return OVERRIDES.get(new IdentityKey(player));
        }
    }

    /** 清空全部登记（资源重载或退出世界时调用）。 */
    public static void clear() {
        synchronized (OVERRIDES) {
            OVERRIDES.clear();
        }
    }
}
