package io.wifi.starrailexpress.settlement.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 一个镜头段只分配一次名签槽位；动作、事件优先级和像素级锚点抖动不能反复触发布局。 */
final class SettlementNameplateLayout {
    private final Map<UUID, SettlementPresentation.Rect> slots = new HashMap<>();
    private final java.util.Set<UUID> hidden=new java.util.HashSet<>();
    private Object shot;
    private int width, height;

    void begin(Object shot, int width, int height) {
        if (!java.util.Objects.equals(this.shot, shot) || this.width != width || this.height != height) {
            clear(); this.shot=shot; this.width=width; this.height=height;
        }
    }

    SettlementPresentation.Rect place(UUID id, double x, double y, double w, double h,
                                      SettlementPresentation.Rect screen, List<SettlementPresentation.Rect> faces,
                                      double gap, boolean side) {
        if (hidden.contains(id)) return null;
        var saved=slots.get(id);
        if (saved != null) return saved;
        var obstacles=new java.util.ArrayList<>(faces);
        for (var slot:slots.values()) obstacles.add(slot.grow(gap*.4));
        var result=SettlementPresentation.placeLabel(x,y,w,h,screen,obstacles,gap,side);
        if (result == null) hidden.add(id);
        if (result != null) slots.put(id,result);
        return result;
    }

    void clear() { slots.clear(); hidden.clear(); shot=null; }
}
