package org.agmas.noellesroles.game.halloween.pumpkin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 不依赖游戏注册表的单局状态；资源账本不能被重连或时间回溯重置。 */
public final class PumpkinSpiritState {
    public final int target;
    private int collected;
    private boolean suppressed;
    private boolean waitingForDoor;
    private boolean pendingSuppressionRevive;
    private final Map<UUID, Long> attackReady = new HashMap<>();

    public PumpkinSpiritState(int startingPlayers) {
        target = Math.multiplyExact(Math.max(1, startingPlayers), 4);
    }

    /** 返回本次应发的糖果袋数，跨越多个十位也不漏发。 */
    public int credit(int count) {
        if (count <= 0) return 0;
        int before = collected / 10;
        collected = Math.addExact(collected, count);
        return collected / 10 - before;
    }

    public int collected() { return collected; }
    public boolean suppressed() { return suppressed; }
    public boolean won(boolean alive) { return alive && !suppressed && collected >= target; }
    public void died() { waitingForDoor = true; }
    public void revived() { waitingForDoor = false; pendingSuppressionRevive = false; }

    /** 存活和已死者都进入压制；解除后保留待复活请求，直到找到安全点。 */
    public boolean suppress(boolean desperadoPresent, boolean alive) {
        if (desperadoPresent) {
            suppressed = true;
            pendingSuppressionRevive = true;
            waitingForDoor = true;
            return alive;
        }
        suppressed = false;
        return false;
    }

    public boolean needsSuppressionRevive() { return !suppressed && pendingSuppressionRevive; }

    /** 每次有效敲门单独抛硬币；失败不消费等待资格。 */
    public boolean door(boolean spectator, boolean coin) {
        return spectator && waitingForDoor && !suppressed && !pendingSuppressionRevive && coin;
    }

    public boolean attack(UUID target, long now) {
        if (now < attackReady.getOrDefault(target, Long.MIN_VALUE)) return false;
        attackReady.put(target, now + 300);
        return true;
    }
}
