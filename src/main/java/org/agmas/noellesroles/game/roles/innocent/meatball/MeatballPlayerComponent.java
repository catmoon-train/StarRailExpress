package org.agmas.noellesroles.game.roles.innocent.meatball;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class MeatballPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    private static final int BOUNTY_INCREASE_PER_TASK = 40;

    private final Player player;
    private int bounty = 0;

    public MeatballPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public void init() {
        this.bounty = 0;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    @Override
    public void serverTick() {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.MEATBALL)) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.bounty = tag.getInt("bounty");
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("bounty", this.bounty);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    public void sync() {
        ModComponents.MEATBALL.sync(this.player);
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void clientTick() {
        @Nullable var gameComp = SREGameWorldComponent.KEY.maybeGet(player.level()).orElse(null);
        if (gameComp == null) {
            return;
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    /**
     * 增加赏金
     */
    public void addBounty() {
        this.bounty += BOUNTY_INCREASE_PER_TASK;
        this.sync();
    }

    /**
     * 获取当前赏金
     */
    public int getBounty() {
        return this.bounty;
    }

    /**
     * 获取并清空赏金（杀手击杀时调用）
     * @return 获得的赏金
     */
    public int collectBounty() {
        int collected = this.bounty;
        this.bounty = 0;
        this.sync();
        return collected;
    }
}
