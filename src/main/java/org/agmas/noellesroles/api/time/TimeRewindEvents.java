package org.agmas.noellesroles.api.time;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;

/** 回溯原版状态的生命周期扩展；用于排除不应复制的局内物品与临时增益。 */
public final class TimeRewindEvents {
    private TimeRewindEvents() {}

    @FunctionalInterface
    public interface PlayerState {
        /** 在服务端主线程处理本次快照的可修改副本，不修改已经保存的节点。 */
        void apply(ServerPlayer player, CompoundTag state);
    }

    @FunctionalInterface
    public interface AreaItem {
        /** 返回 false 时，该掉落物不参与区域回溯的保存、删除与生成。 */
        boolean allow(ItemEntity item);
    }

    /** 原版 NBT 捕获后、构造不可变节点前。 */
    public static final Event<PlayerState> CAPTURE = stateEvent();
    /** 载入原版 NBT 前，可保全当前实物；身份检查通过后才触发。 */
    public static final Event<PlayerState> BEFORE_RESTORE = stateEvent();
    /** 所有组件恢复后触发；原版恢复失败也触发，用于归还保全的实物。 */
    public static final Event<PlayerState> AFTER_RESTORE = stateEvent();
    public static final Event<AreaItem> ALLOW_AREA_ITEM = EventFactory.createArrayBacked(AreaItem.class,
            listeners -> item -> {
                for (AreaItem listener : listeners) if (!listener.allow(item)) return false;
                return true;
            });

    private static Event<PlayerState> stateEvent() {
        return EventFactory.createArrayBacked(PlayerState.class, listeners -> (player, state) -> {
            for (PlayerState listener : listeners) listener.apply(player, state);
        });
    }
}
