package io.wifi.starrailexpress.event;

import io.wifi.starrailexpress.api.SRERole;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** 实际阵营身份覆盖；能力职业不变，具体身份规则由业务模块一次注册。 */
public final class RoleAllegianceEvents {
    private RoleAllegianceEvents() {
    }

    /** 首个非 null 身份生效；全部返回 null 时，由调用方继续查询当前能力职业。 */
    public static final Event<RoleOverride> OVERRIDE = EventFactory.createArrayBacked(RoleOverride.class,
            listeners -> (level, playerId) -> {
                for (RoleOverride listener : listeners) {
                    SRERole role = listener.resolve(level, playerId);
                    if (role != null) return role;
                }
                return null;
            });

    @FunctionalInterface
    public interface RoleOverride {
        @Nullable SRERole resolve(Level level, UUID playerId);
    }
}
