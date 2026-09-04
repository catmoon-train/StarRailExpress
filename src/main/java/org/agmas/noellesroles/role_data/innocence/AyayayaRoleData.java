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

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class AyayayaRoleData extends SimpleRoleData {

    public UUID deliveryTarget = null;
    public ItemStack putItem = ItemStack.EMPTY;
    public ItemStack targetItem = ItemStack.EMPTY;
    public boolean senderConfirmed = false;
    public boolean targetConfirmed = false;
    public String targetName = "";

    public AyayayaRoleData(RoleDataContext context) {
        super(context);
    }

    /**
     * 传递会话只存在于射命丸文/姬海棠侧。接收方通过扫描持有本数据的玩家读取同一份状态。
     */
    @Nullable
    public static AyayayaRoleData resolve(Player viewer) {
        if (viewer == null || viewer.level() == null) {
            return null;
        }
        AyayayaRoleData own = RoleData.getNullable(AyayayaRoleData.class, viewer);
        if (own != null && own.isDeliveryActive()) {
            return own;
        }
        for (Player other : viewer.level().players()) {
            if (other == viewer) {
                continue;
            }
            AyayayaRoleData data = RoleData.getNullable(AyayayaRoleData.class, other);
            if (data != null && viewer.getUUID().equals(data.deliveryTarget)) {
                return data;
            }
        }
        return null;
    }

    public boolean isViewerReceiver(Player viewer) {
        return viewer != null && deliveryTarget != null && deliveryTarget.equals(viewer.getUUID());
    }

    public String displayNameFor(Player viewer) {
        if (isViewerReceiver(viewer)) {
            return player.getName().getString();
        }
        return targetName;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) {
        if (p == player) {
            return true;
        }
        return deliveryTarget != null && deliveryTarget.equals(p.getUUID());
    }

    @Override
    public void init() {
        this.deliveryTarget = null;
        this.putItem = ItemStack.EMPTY;
        this.targetItem = ItemStack.EMPTY;
        this.senderConfirmed = false;
        this.targetConfirmed = false;
        this.targetName = "";
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    public void startDelivery(UUID targetUuid, String targetPlayerName) {
        this.deliveryTarget = targetUuid;
        this.targetName = targetPlayerName;
        this.putItem = ItemStack.EMPTY;
        this.targetItem = ItemStack.EMPTY;
        this.senderConfirmed = false;
        this.targetConfirmed = false;
        this.sync();
        if (player instanceof ServerPlayer && player.level() != null) {
            Player target = player.level().getPlayerByUUID(targetUuid);
            if (target instanceof ServerPlayer serverTarget) {
                syncTo(serverTarget);
            }
        }
    }

    public void setItem(ItemStack item, boolean issender) {
        if (issender) {
            this.putItem = item.copy();
            this.senderConfirmed = false;
        } else {
            this.targetItem = item.copy();
            this.targetConfirmed = false;
        }
        this.sync();
    }

    public void confirm(boolean issender) {
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        ConfigWorldComponent.onPlayerUsedSkill((ServerPlayer) player);
        if (issender) {
            this.senderConfirmed = true;
        } else {
            this.targetConfirmed = true;
        }
        this.sync();
    }

    public void unconfirm(boolean issender) {
        if (issender) {
            this.senderConfirmed = false;
        } else {
            this.targetConfirmed = false;
        }
        this.sync();
    }

    public boolean isBothConfirmed() {
        return senderConfirmed && targetConfirmed;
    }

    public boolean isDeliveryActive() {
        return deliveryTarget != null;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (deliveryTarget != null) {
            tag.putUUID("deliveryTarget", deliveryTarget);
        }

        if (!putItem.isEmpty()) {
            CompoundTag senderItemTag = new CompoundTag();
            putItem.save(registryLookup, senderItemTag);
            tag.put("senderItem", senderItemTag);
        }

        if (!targetItem.isEmpty()) {
            CompoundTag targetItemTag = new CompoundTag();
            targetItem.save(registryLookup, targetItemTag);
            tag.put("targetItem", targetItemTag);
        }

        tag.putBoolean("senderConfirmed", senderConfirmed);
        tag.putBoolean("targetConfirmed", targetConfirmed);
        tag.putString("targetName", targetName);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.deliveryTarget = tag.contains("deliveryTarget") ? tag.getUUID("deliveryTarget") : null;

        if (tag.contains("senderItem")) {
            this.putItem = ItemStack.parseOptional(registryLookup, tag.getCompound("senderItem"));
        } else {
            this.putItem = ItemStack.EMPTY;
        }

        if (tag.contains("targetItem")) {
            this.targetItem = ItemStack.parseOptional(registryLookup, tag.getCompound("targetItem"));
        } else {
            this.targetItem = ItemStack.EMPTY;
        }

        this.senderConfirmed = tag.getBoolean("senderConfirmed");
        this.targetConfirmed = tag.getBoolean("targetConfirmed");
        this.targetName = tag.getString("targetName");
    }
}
