package org.agmas.noellesroles.game.roles.killer.delayer;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeathWithKiller;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 滞时鬼（Delayer）玩家组件
 * - 击杀玩家时为游戏增加20秒
 * - 当存在滞时鬼且游戏时间为1分25秒（85秒）时，增加30秒（仅触发一次）
 */
public class DelayerPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final org.ladysnake.cca.api.v3.component.ComponentKey<DelayerPlayerComponent> KEY = ModComponents.DELAYER;

    private final Player player;

    // world-level 一次性触发标志（每轮仅触发一次）
    public static volatile boolean timeBoostTriggered = false;

    public DelayerPlayerComponent(Player player) {
        this.player = player;
    }

    public static void registerEvents() {
        // 击杀触发：当被击杀的玩家死亡且有击杀者时，为游戏增加20秒
        AfterShieldAllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (victim == null || killer == null) return true;
            var world = victim.level();
            if (world == null || world.isClientSide()) return true;
            var gameWorld = SREGameWorldComponent.KEY.get(world);
            if (gameWorld.isRole(killer, ModRoles.DELAYER)) {
                SREGameTimeComponent.KEY.get(world).addTime(20 * 20);
            }
            return true;
        });
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        // nothing
    }

    @Override
    public void clear() {
        // nothing
    }

    @Override
    public void clientTick() {
        // client-side nothing
    }

    @Override
    public void serverTick() {
        var world = player.level();
        if (world.isClientSide()) return;
        var gameWorld = SREGameWorldComponent.KEY.get(world);
        if (!gameWorld.isRunning()) return;
        if (!gameWorld.isRole(player, ModRoles.DELAYER)) return;

        // 仅在角色存在且尚未触发时检查全局计时器
        if (!timeBoostTriggered) {
            var timeComp = SREGameTimeComponent.KEY.get(world);
            int timeLeft = timeComp.getTime();
            // 85 秒 = 85 * 20 ticks
            if (timeLeft == 85 * 20) {
                timeComp.addTime(30 * 20);
                timeBoostTriggered = true;
            }
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
