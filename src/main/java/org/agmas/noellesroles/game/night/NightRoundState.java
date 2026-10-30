package org.agmas.noellesroles.game.night;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** 黑夜事件的纯状态：下一局请求与本局物品、死亡去重分别管理。 */
public final class NightRoundState {
    public static final float VIEW_DISTANCE = 16.0F;
    private boolean forcePending;
    private final Set<UUID> equipped = new HashSet<>();
    private final Set<UUID> dead = new HashSet<>();

    public boolean requestNext() {
        if (forcePending) return false;
        forcePending = true;
        return true;
    }

    public boolean isForcePending() { return forcePending; }

    /** 不支持的模式不消耗排队；0 概率不出现，10000 必出。 */
    public boolean prepare(boolean supported, int chance, int roll) {
        if (!supported) return false;
        boolean forced = forcePending;
        forcePending = false;
        return forced || roll < Math.clamp(chance, 0, 10000);
    }

    public boolean equip(UUID player) { return equipped.add(player); }
    public void retryEquip(UUID player) { equipped.remove(player); }
    public boolean markDead(UUID player) { return dead.add(player); }
    public void observeAlive(UUID player) { dead.remove(player); }

    /** 清本局不清下一局请求，支持在进行中的对局排队。 */
    public void clearRound() { equipped.clear(); dead.clear(); }

    /** 保留更强的失明/迷雾，任何无效或过远雾都不能绕过 16 格。 */
    public static float fogEnd(float current) {
        return Float.isFinite(current) && current > 0 ? Math.min(current, VIEW_DISTANCE) : VIEW_DISTANCE;
    }

    public static float fogStart(float current, float end) {
        return Float.isFinite(current) ? Math.clamp(current, 0, Math.max(0, end - 2)) : Math.max(0, end - 2);
    }
}
