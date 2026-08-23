/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.role_data.innocence;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

public class PilotRoleData extends SimpleRoleData {

    public PilotRoleData(RoleDataContext context) {
        super(context);
    }

    /**
     * 重置组件状态
     */
    @Override
    public void init() {
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    /**
     * 脱下喷气背包和鞘翅
     * 
     * @return 是否成功脱下
     */
    public boolean removeJetpack() {
        // 验证是飞行员
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.PILOT)) {
            return false;
        }

        // 检查玩家是否存活
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }

        boolean removedSomething = false;
        StringBuilder messageBuilder = new StringBuilder();

        // 检查身上是否有喷气背包
        ItemStack chestplate = player.getInventory().getArmor(2); // 胸甲槽位
        if (chestplate.is(ModItems.JETPACK)) {
            // 尝试将喷气背包放入背包
            ItemStack jetpack = chestplate.copy();
            player.getInventory().armor.set(2, ItemStack.EMPTY);

            if (!player.getInventory().add(jetpack)) {
                // 背包满了，恢复装备并提示
                player.getInventory().armor.set(2, jetpack);
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.displayClientMessage(
                            Component.translatable("message.noellesroles.pilot.inventory_full"),
                            true);
                }
                return false;
            }

            removedSomething = true;
            messageBuilder.append(Component.translatable("message.noellesroles.pilot.jetpack_removed").getString());
        }

        // 检查身上是否有鞘翅（胸甲位置）
        ItemStack currentChest = player.getInventory().getArmor(2);
        if (currentChest.is(net.minecraft.world.item.Items.ELYTRA)) {
            // 尝试将鞘翅放入背包
            ItemStack elytra = currentChest.copy();
            player.getInventory().armor.set(2, ItemStack.EMPTY);

            if (!player.getInventory().add(elytra)) {
                // 背包满了，恢复装备并提示
                player.getInventory().armor.set(2, elytra);
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.displayClientMessage(
                            Component.translatable("message.noellesroles.pilot.inventory_full"),
                            true);
                }
                return false;
            }

            if (removedSomething) {
                messageBuilder.append(", ");
            }
            messageBuilder.append(Component.translatable("message.noellesroles.pilot.elytra_removed").getString());
            removedSomething = true;
        }

        if (!removedSomething) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.pilot.no_jetpack"),
                        true);
            }
            return false;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.literal(messageBuilder.toString()),
                    true);
        }

        this.sync();
        return true;
    }

    // ==================== Tick 处理 ====================

    @Override
    public void serverTick() {
        // 飞行员不需要每tick处理，主要逻辑在喷气背包的tick处理中
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 飞行员不需要同步特殊状态
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 飞行员不需要读取特殊状态
    }
}
