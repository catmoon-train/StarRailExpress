package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * @deprecated 已迁移到 org.agmas.noellesroles.game.roles.neutral.warden.WardenPlayerComponent
 * 此类仅保留以避免类加载错误，不再注册组件KEY。
 */
@Deprecated
public class WardenPlayerComponent implements RoleComponent, ServerTickingComponent {
    // KEY已迁移到noellesroles包下，不再在此注册以避免冲突

    private final Player player;

    public WardenPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {}

    @Override
    public void clear() {}

    @Override
    public void serverTick() {}

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {}

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {}

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {}

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {}
}
