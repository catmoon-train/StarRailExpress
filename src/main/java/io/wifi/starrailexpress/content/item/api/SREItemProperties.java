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
     * 像弩和球棒一样举起来
     */
    public interface HeldLikeBat {
    }

    /**
     * 使用此物品将独立处理开门逻辑，不传递到门上
     */
    public interface DoorCustomOpenItem {
    }

    /**
     * 死亡时掉落物品
     */
    public interface DropWhenDead {
        /**
         * 当死亡时调用
         */
        public default ItemStack onDrop(ServerPlayer player, ItemStack stack) {
            return stack;
        }
    }

    /**
     * 死亡时物品掉落左轮手枪
     */
    public interface DropRevolverWhenDead {
    }

    /**
     * 像左轮手枪一样举起来
     */
    public interface HeldLikeRevolver {
    }

    /**
     * 是武器
     */
    
    public interface TrainWeapon {
    }
    /**
     * 可以左键攻击玩家，但不会死
     */
    public interface LeftClickHurtable {
        /**
         * 客户端/服务端尝试攻击玩家时触发。返回CONSUME/FAIL则取消原有攻击逻辑传递链。
         * 
         * @param attacker
         * @param target
         * @param mainhandItem
         */
        public default InteractionResult onTryHurt(Player attacker, Entity target, ItemStack mainhandItem) {
            return InteractionResult.PASS;
        }

        /**
         * 服务端玩家攻击玩家时触发。
         * 攻击力度可通过 self.getAttackStrengthScale(0.5F) >= 1f 判断是否为满攻击。在onTryHurt前触发。
         * 
         * @param attacker
         * @param target
         * @param mainhandItem
         * @return boolean 是否启用原版逻辑
         */
        public default boolean onServerAttack(ServerPlayer attacker, ServerPlayer target, ItemStack mainhandItem) {
            return true;
        }
    }

    /**
     * 可以左键攻击玩家，且会死
     */
    public interface LeftClickKillable extends LeftClickHurtable {
        @Override
        public default boolean onServerAttack(ServerPlayer attacker, ServerPlayer target, ItemStack mainhandItem) {
            if (attacker.getAttackStrengthScale(0.75F) < 1f) {
                return false;
            }
            if (GameUtils.isPlayerAliveAndSurvival(attacker) && GameUtils.isPlayerAliveAndSurvival(target)) {
                GameUtils.killPlayer(target, true, attacker, SkinUtils.getItemTypeResourceLocation(mainhandItem));
            }
            return false;
        }
    }
}
