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

package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.role_data.neutral.MercenaryRoleData;
import org.agmas.noellesroles.role.ModRoles;

import java.util.UUID;

public class MercenaryContractItem extends Item {
    public static final String TAG_SIGNED = "signed";
    public static final String TAG_EMPLOYER_UUID = "employer_uuid";
    public static final String TAG_EMPLOYER_NAME = "employer_name";
    public static final String TAG_TARGET_UUID = "target_uuid";
    public static final String TAG_TARGET_NAME = "target_name";

    public static Runnable openGuiRunner = null;

    public MercenaryContractItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean signed = isSigned(stack);
        var gameWorld = SREGameWorldComponent.KEY.get(level);
        boolean isMercenary = gameWorld.isRole(player, ModRoles.MERCENARY);

        if (!signed) {
            if (isMercenary) {
                if (!level.isClientSide) {
                    player.displayClientMessage(
                            Component.translatable("message.noellesroles.mercenary.contract_mercenary_cannot_open")
                                    .withStyle(ChatFormatting.RED),
                            true);
                }
                return InteractionResultHolder.fail(stack);
            }

            if (level.isClientSide) {
                if (openGuiRunner != null)
                    openGuiRunner.run();
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        if (!isMercenary) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.mercenary.contract_only_mercenary")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
            return InteractionResultHolder.fail(stack);
        }

        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        UUID targetUuid = getUuid(stack, TAG_TARGET_UUID);
        String targetName = getString(stack, TAG_TARGET_NAME);
        UUID employerUuid = getUuid(stack, TAG_EMPLOYER_UUID);
        String employerName = getString(stack, TAG_EMPLOYER_NAME);

        if (targetUuid == null || targetName.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.mercenary.contract_invalid")
                            .withStyle(ChatFormatting.RED),
                    true);
            return InteractionResultHolder.fail(stack);
        }

        var comp = RoleData.getNullable(MercenaryRoleData.class, player);
        if (comp == null || comp.contractActive) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.mercenary.contract_already_active")
                            .withStyle(ChatFormatting.RED),
                    true);
            return InteractionResultHolder.fail(stack);
        }

        boolean ok = comp.startContract(employerUuid, employerName, targetUuid, targetName);
        if (!ok) {
            // 如果是冷却导致的失败，startContract 已经发送了冷却提示，此处不再发送通用失败提示
            if (comp.contractCooldown > 0) {
            return InteractionResultHolder.fail(stack);
            }
            player.displayClientMessage(
                Component.translatable("message.noellesroles.mercenary.contract_start_failed")
                    .withStyle(ChatFormatting.RED),
                true);
            return InteractionResultHolder.fail(stack);
        }

        // 雇佣任务开始，雇佣兵直接获得雇佣金
        io.wifi.starrailexpress.cca.SREPlayerShopComponent.KEY.get(player).addToBalance(175);

        Player target = level.getPlayerByUUID(targetUuid);
        if (target != null) {
            target.displayClientMessage(
                    Component.translatable("message.noellesroles.mercenary.you_are_wanted")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                    true);
        }

        stack.shrink(1);
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    public static boolean isSigned(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.getBoolean(TAG_SIGNED);
    }

    public static void applySignedData(ItemStack stack, UUID employerUuid, String employerName, UUID targetUuid,
            String targetName) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putBoolean(TAG_SIGNED, true);
        tag.putString(TAG_EMPLOYER_NAME, employerName == null ? "" : employerName);
        tag.putString(TAG_TARGET_NAME, targetName == null ? "" : targetName);
        if (employerUuid != null) {
            tag.putUUID(TAG_EMPLOYER_UUID, employerUuid);
        }
        if (targetUuid != null) {
            tag.putUUID(TAG_TARGET_UUID, targetUuid);
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.ITEM_NAME,
                Component.translatable("item.noellesroles.mercenary_contract.signed_name", targetName));
    }

    public static UUID getUuid(ItemStack stack, String key) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(key)) {
            return null;
        }
        try {
            return tag.getUUID(key);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String getString(ItemStack stack, String key) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains(key) ? tag.getString(key) : "";
    }
}
