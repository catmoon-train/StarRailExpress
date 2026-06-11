package io.wifi.starrailexpress.content.item.api;

import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.SkinUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class SREItemProperties {
    /**
     * 像左轮手枪一样会举起来
     */
    public interface HeldLikeRevolver {
    }

    /**
     * 可以左键攻击玩家，但不会死
     */
    public interface LeftClickHurtable {
        /**
         * 客户端/服务端尝试攻击玩家时触发。返回CONSUME则取消原有攻击逻辑传递链。
         * 
         * @param attacker
         * @param target
         * @param mainhandItem
         */
        public default InteractionResult onTryHurt(Player attacker, Entity target, ItemStack mainhandItem) {
            return InteractionResult.PASS;
        }

        /**
         * 服务端玩家攻击玩家时触发
         * 
         * @param attacker
         * @param target
         * @param mainhandItem
         */
        public default void onAttack(ServerPlayer attacker, ServerPlayer target, ItemStack mainhandItem) {
        }
    }

    /**
     * 可以左键攻击玩家，且会死
     */
    public interface LeftClickKillable extends LeftClickHurtable {
        @Override
        public default void onAttack(ServerPlayer attacker, ServerPlayer target, ItemStack mainhandItem) {
            GameUtils.killPlayer(target, true, attacker, SkinUtils.getItemTypeResourceLocation(mainhandItem));
        }
    }
}
