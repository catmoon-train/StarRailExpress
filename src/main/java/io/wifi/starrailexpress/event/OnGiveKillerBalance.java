package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 事件接口：计算击杀者击杀玩家后应获得的金币奖励总量。
 * 所有监听器的返回值将被累加作为最终奖励金额。
 *
 * <p>Event interface that computes the total balance (currency) to award to the killer
 * when a player is killed. All listener return values are summed to produce the final amount.
 */
public interface OnGiveKillerBalance {

    /**
     * 计算击杀者金币奖励的事件。
     * 游戏当前定义的死亡类型名称有：
     * 'fell_out_of_train'、'poison'、'grenade'、'bat_hit'、'gun_shot'、'knife_stab'。
     * 其他未显式定义的死亡类型默认为 'generic'。
     *
     * <p>Event callback to calculate the balance reward for the killer.
     * The game currently has the following death type names defined:
     * 'fell_out_of_train', 'poison', 'grenade', 'bat_hit', 'gun_shot',
     * 'knife_stab'.
     * Any other death type not explicitly defined will default to 'generic'.
     *
     * @see io.wifi.starrailexpress.game.GameConstants.DeathReasons
     */
    Event<OnGiveKillerBalance> EVENT = createArrayBacked(OnGiveKillerBalance.class,
            listeners -> (victim, killer, deathReason) -> {
                int balance = 0;
                for (OnGiveKillerBalance listener : listeners) {
                    balance += listener.onGiveKillerBalance(victim, killer, deathReason);
                }
                return balance;
            });

    /**
     * 计算击杀者应获得的金币奖励数量。
     *
     * <p>Calculates the balance amount to award to the killer.
     *
     * @param victim     被击杀的玩家 / the player who was killed
     * @param killer     击杀者 / the player who performed the kill
     * @param deathReason 死亡原因的资源定位符 / resource location identifying the death reason
     * @return 应给予击杀者的金币数量（将与其他监听器结果累加）/
     *         the balance amount to award (summed with other listeners' results)
     */
    int onGiveKillerBalance(Player victim, Player killer, ResourceLocation deathReason);
}