package org.agmas.noellesroles.game.halloween;

/** 与游戏注册表无关的链接计算，便于无客户端测试。 */
public final class HalloweenLinkRules {
    private HalloweenLinkRules() {}
    public static int sharedArmor(int own, int partner) {
        return (int) Math.min(Integer.MAX_VALUE, (long) Math.max(0, own) + Math.max(0, partner));
    }
    public static int consumeOwn(int own, int requested) {
        return Math.min(Math.max(0, own), Math.max(0, requested));
    }
    /** 镜像保存同一份物品引用，不创建可重复发奖或独立消耗的副本。 */
    public static <T> boolean mirrorSlots(java.util.List<T> source, java.util.List<T> target) {
        if (source.size() != target.size()) throw new IllegalArgumentException("slot size");
        boolean changed = false;
        for (int slot = 0; slot < source.size(); slot++) {
            if (source.get(slot) != target.get(slot)) {
                target.set(slot, source.get(slot));
                changed = true;
            }
        }
        return changed;
    }

    /** 仅解除槽位引用，不能修改原物品对象。 */
    public static <T> void detachSlots(java.util.List<T> target, T empty) {
        java.util.Collections.fill(target, empty);
    }
    public static boolean within(double distanceSquared, double limit) {
        return Double.isFinite(distanceSquared) && Double.isFinite(limit) && limit > 0
                && distanceSquared <= limit * limit;
    }
}
