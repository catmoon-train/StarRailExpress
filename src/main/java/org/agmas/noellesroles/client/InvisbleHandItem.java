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

package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.cca.ExtraSlotComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.content.item.SkinableItem;
import io.wifi.starrailexpress.event.AllowItemShowInHand;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.item.HandCuffsItem;
import org.agmas.noellesroles.content.item.StalkerKnifeItem;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.touhou.THMiscRoles;
import org.agmas.noellesroles.utils.RoleUtils;

public class InvisbleHandItem {

    public static void register() {
        // 怀旧者里世界：隐藏手持物品（主手 + 副手）
        AllowItemShowInHand.EVENT.register((player, itemStack, mainHand) -> {
            if (SREClient.gameComponent != null && SREClient.gameComponent.getRole(player) != null) {
                if (SREClient.gameComponent.getRole(player).equals(ModRoles.SALTED_FISH)) {
                    if (player.isInvisible()) {
                        return ItemStack.EMPTY;
                    }
                }
            }
            if (player.hasEffect(ModEffects.NOSTALGIST_BACKWORLD)) {
                return ItemStack.EMPTY;
            }
            if (player.hasEffect(ModEffects.WRAITH_DIMENSION) && !player.hasEffect(ModEffects.WRAITH_MANIFEST)) {
                return ItemStack.EMPTY;
            }
            return null; // 不修改
        });
        // 显示手铐
        AllowItemShowInHand.EVENT.register((player, itemStack, mainHand) -> {
            if (mainHand)
                return null;
            var item = ExtraSlotComponent.getSlot(player, HandCuffsItem.SLOT_HANDCUFFS);
            if (item.is(ModItems.HANDCUFFS)) {
                return item;
            }
            return null; // 不修改
        });
        // 隐藏指定的物品
        AllowItemShowInHand.EVENT.register((player, itemStack, mainHand) -> {
            if (!mainHand)
                return null;
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
            if (gameWorld.isRole(player, ModRoles.VETERAN) && itemStack.is(TMMItems.KNIFE)) {
                return ModItems.SP_KNIFE.getDefaultInstance();
            }

            return null; // 不修改
        });

        // 妖梦

        AllowItemShowInHand.EVENT.register((player, itemStack, mainHand) -> {
            if (!GameUtils.isPlayerAliveAndSurvival(player))
                return null;
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
            if (gameWorld.isRole(player, THMiscRoles.KONPAKU_YOUMU)) {
                if (player.isInvisible()) {
                    return ItemStack.EMPTY;
                }
            }
            if (gameWorld.isRole(player, THMiscRoles.KONPAKU_YOUMU) && !mainHand) {
                if (player.getMainHandItem().is(ModItems.YOUMU_SWORD)) {
                    return ModItems.YOUMU_SWORD.getDefaultInstance();
                }
            }

            return null; // 不修改
        });
        // HoujuuNue
        AllowItemShowInHand.EVENT.register((player, itemStack, mainHand) -> {
            if (!mainHand)
                return null;
            if (!RoleUtils.isPlayerTheJob(player, THMiscRoles.HOUJUU_NUE)) {
                return null;
            }
            if (itemStack.is(TMMItems.KNIFE)) {
                if (TMMItems.REVOLVER instanceof SkinableItem ri) {
                    return ri.getDefaultInstanceForRenderer(player);
                } else {
                    return TMMItems.REVOLVER.getDefaultInstance();
                }
            } else if (itemStack.is(TMMItemTags.GUNS)) {
                if (TMMItems.KNIFE instanceof SkinableItem ri) {
                    return ri.getDefaultInstanceForRenderer(player);
                } else {
                    return TMMItems.KNIFE.getDefaultInstance();
                }
            } else if (itemStack.is(TMMItems.LOCKPICK)) {
                return ModItems.MASTER_KEY.getDefaultInstance();
            }
            return null; // 不修改
        });
        AllowItemShowInHand.EVENT.register((player, itemStack, mainHand) -> {
            if (!mainHand) {
                if (itemStack.getItem() instanceof StalkerKnifeItem) {
                    if (!(player.getMainHandItem().getItem() instanceof StalkerKnifeItem)) {
                        return ItemStack.EMPTY;
                    }
                }
            }
            if (SREClient.gameComponent != null && SREClient.gameComponent.getRole(player) != null
                    && SREClient.gameComponent.getRole(player).equals(ModRoles.STALKER)) {
                if (player.isCrouching()) {
                    return ItemStack.EMPTY;
                }
            } else if (SREClient.gameComponent != null && SREClient.gameComponent.getRole(player) != null
                    && SREClient.gameComponent.getRole(player).equals(ModRoles.EXECUTIONER)) {
                var ps = SREPlayerPsychoComponent.KEY.get(player);
                if (ps.psychoTicks > 0 && ps.type == 1) {
                    return TMMItems.REVOLVER.getDefaultInstance();
                }
            }
            return null;
        });

    }
}
