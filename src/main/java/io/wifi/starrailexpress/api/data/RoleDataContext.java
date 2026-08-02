package io.wifi.starrailexpress.api.data;

import org.jetbrains.annotations.Nullable;

import io.wifi.starrailexpress.api.SRERole;
import net.minecraft.world.entity.player.Player;

public record RoleDataContext(Player player, SRERole role, @Nullable Runnable syncFunc) {
    public void sync() {
        if (syncFunc != null)
            syncFunc.run();
    }

    public Player getPlayer() {
        return player();
    }

    public boolean isClientSide() {
        return player.level().isClientSide;
    }
}
