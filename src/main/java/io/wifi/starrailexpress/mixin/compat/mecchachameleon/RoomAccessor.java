package io.wifi.starrailexpress.mixin.compat.mecchachameleon;

import com.mecchachameleon.game.Room;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;
import java.util.UUID;

/**
 * 变色龙的 {@code Room} 只能通过 {@code /meccha} 命令驱动，内部状态全是私有字段。
 * SRE 需要直接把一局游戏写进房间，因此暴露这几个字段。
 */
@Mixin(value = Room.class, remap = false)
public interface RoomAccessor {
    @Accessor("phase")
    void setPhase(Room.Phase phase);

    @Accessor("roster")
    Set<UUID> getRoster();

    @Accessor("seekers")
    Set<UUID> getSeekers();
}
