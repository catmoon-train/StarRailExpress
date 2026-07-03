package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 事件接口：当触发GameUtils.killPlayer触发。
 * 所有监听器均会被调用（非拦截型事件）。
 */
public interface OnKillPlayerTriggered {

    /**
     * 当触发GameUtils.killPlayer触发。
     * 游戏当前定义的死亡类型名称有：
     * 'fell_out_of_train'、'poison'、'grenade'、'bat_hit'、'gun_shot'、'knife_stab'。
     * 其他未显式定义的死亡类型默认为 'generic'。
     *
     * <p>Event callback invoked when a player dies (no-killer variant).
     * The game currently has the following death type names defined:
     * 'fell_out_of_train', 'poison', 'grenade', 'bat_hit', 'gun_shot',
     * 'knife_stab'.
     * Any other death type not explicitly defined will default to 'generic'.
     *
     * @see io.wifi.starrailexpress.game.GameConstants.DeathReasons
     */
    Event<OnKillPlayerTriggered> EVENT = createArrayBacked(OnKillPlayerTriggered.class, listeners -> (victim, spawnBody, _killer, deathReasosn, forceKill) -> {
        for (OnKillPlayerTriggered listener : listeners) {
            listener.onKillPlayerTriggered(victim, spawnBody, _killer, deathReasosn, forceKill);
        }
        return;
    });

    /**
     * 玩家死亡时的回调方法（无击杀者）。
     *
     * <p>Callback invoked when a player dies (no-killer variant).
     *
     * @param player     死亡的玩家 / the player who died
     * @param deathReason 死亡原因的资源定位符 / resource location identifying the death reason
     */
    void onKillPlayerTriggered(Player victim, boolean spawnBody, @Nullable Player _killer,
            ResourceLocation deathReason, boolean forceDeath);
}