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
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.packet.PostmanDeliveryStateS2CPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AyayayaRoleData extends SimpleRoleData {

    public UUID deliveryTarget = null;
    public ItemStack putItem = ItemStack.EMPTY;
    public ItemStack targetItem = ItemStack.EMPTY;
    public boolean senderConfirmed = false;
    public boolean targetConfirmed = false;
    public String targetName = "";
    /** 发起者 UUID / 名字：通用会话在客户端没有 player 对象，必须单独携带。 */
    public UUID senderUuid = null;
    public String senderName = "";
    private final boolean genericSession;

    public AyayayaRoleData(RoleDataContext context) {
        this(context, false);
    }

    public AyayayaRoleData(RoleDataContext context, boolean genericSession) {
        super(context);
        this.genericSession = genericSession;
    }

    /**
     * 非射命丸文 / 姬海棠玩家临时发起的投递会话。
     *
     * <p>快递包裹已不限制职业，但会话状态原本只能挂在这两个职业的 RoleData 上；
     * 这里给其它职业准备一份「无同步」的临时会话，仅服务端可见（交换结算全在服务端完成）。</p>
     */
    private static final Map<UUID, AyayayaRoleData> GENERIC_SESSIONS = new HashMap<>();
    /** 服务端同步过来的通用会话镜像，只在客户端使用。 */
    private static final Map<UUID, AyayayaRoleData> CLIENT_SESSIONS = new HashMap<>();

    @Nullable
    private static AyayayaRoleData findSession(UUID viewerUuid, Map<UUID, AyayayaRoleData> sessions) {
        AyayayaRoleData own = sessions.get(viewerUuid);
        if (own != null && own.isDeliveryActive()) {
            return own;
        }
        for (AyayayaRoleData data : sessions.values()) {
            if (data.isDeliveryActive() && viewerUuid.equals(data.deliveryTarget)) {
                return data;
            }
        }
        return null;
    }

    /**
     * 传递会话只存在于射命丸文/姬海棠侧。接收方通过扫描持有本数据的玩家读取同一份状态。
     */
    @Nullable
    public static AyayayaRoleData resolve(Player viewer) {
        if (viewer == null || viewer.level() == null) {
            return null;
        }
        if (viewer.level().isClientSide) {
            // 非职业玩家在本地没有 RoleData，只能读取服务端同步过来的会话镜像。
            CLIENT_SESSIONS.values().removeIf(data -> !data.isDeliveryActive());
            AyayayaRoleData mirrored = findSession(viewer.getUUID(), CLIENT_SESSIONS);
            if (mirrored != null) {
                return mirrored;
            }
        }
        GENERIC_SESSIONS.values().removeIf(data -> !data.isDeliveryActive());
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
        return findSession(viewer.getUUID(), GENERIC_SESSIONS);
    }

    /**
     * 任意玩家发起投递时取会话：职业自带会话优先，其它职业用临时会话。
     */
    public static AyayayaRoleData resolveOrCreate(Player player) {
        AyayayaRoleData own = RoleData.getNullable(AyayayaRoleData.class, player);
        if (own != null) {
            return own;
        }
        GENERIC_SESSIONS.values().removeIf(data -> !data.isDeliveryActive());
        return GENERIC_SESSIONS.computeIfAbsent(player.getUUID(),
                id -> new AyayayaRoleData(new RoleDataContext(player, null, null, null), true));
    }

    @Override
    public void sync() {
        super.sync();
        if (genericSession && player != null && !player.level().isClientSide) {
            broadcastGenericState(this);
        }
    }

    @Override
    public void syncTo(ServerPlayer serverPlayer) {
        super.syncTo(serverPlayer);
        if (genericSession && serverPlayer != null) {
            sendGenericState(this, serverPlayer);
        }
    }

    private static void broadcastGenericState(AyayayaRoleData session) {
        if (session.player == null || session.player.level().isClientSide) {
            return;
        }
        if (session.player instanceof ServerPlayer sender) {
            sendGenericState(session, sender);
        }
        if (session.deliveryTarget != null) {
            Player target = session.player.level().getPlayerByUUID(session.deliveryTarget);
            if (target instanceof ServerPlayer targetPlayer) {
                sendGenericState(session, targetPlayer);
            }
        }
    }

    private static void sendGenericState(AyayayaRoleData session, ServerPlayer receiver) {
        UUID sender = session.senderUuid != null ? session.senderUuid
                : session.player != null ? session.player.getUUID() : null;
        ServerPlayNetworking.send(receiver, new PostmanDeliveryStateS2CPacket(
                sender, session.deliveryTarget, session.senderName, session.targetName,
                session.senderConfirmed, session.targetConfirmed, session.isDeliveryActive()));
    }

    /** 客户端接收通用会话状态；active 为 false 表示会话已结束。 */
    public static void applyClientState(PostmanDeliveryStateS2CPacket payload) {
        if (payload.senderUuid() == null) {
            return;
        }
        if (!payload.active()) {
            CLIENT_SESSIONS.remove(payload.senderUuid());
            return;
        }
        AyayayaRoleData data = CLIENT_SESSIONS.computeIfAbsent(payload.senderUuid(),
                id -> new AyayayaRoleData(new RoleDataContext(null, null, null, null), true));
        data.senderUuid = payload.senderUuid();
        data.deliveryTarget = payload.targetUuid();
        data.senderName = payload.senderName();
        data.targetName = payload.targetName();
        data.senderConfirmed = payload.senderConfirmed();
        data.targetConfirmed = payload.targetConfirmed();
    }

    public boolean isViewerReceiver(Player viewer) {
        return viewer != null && deliveryTarget != null && deliveryTarget.equals(viewer.getUUID());
    }

    public String displayNameFor(Player viewer) {
        if (isViewerReceiver(viewer)) {
            return senderName.isEmpty() && player != null ? player.getName().getString() : senderName;
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
        // 先广播结束状态，让双方客户端的镜像一起清掉，再丢弃发起者信息。
        this.sync();
        this.senderUuid = null;
        this.senderName = "";
    }

    @Override
    public void clear() {
        this.init();
    }

    public void startDelivery(UUID targetUuid, String targetPlayerName) {
        this.senderUuid = player != null ? player.getUUID() : null;
        this.senderName = player != null ? player.getName().getString() : "";
        this.deliveryTarget = targetUuid;
        this.targetName = targetPlayerName;
        this.putItem = ItemStack.EMPTY;
        this.targetItem = ItemStack.EMPTY;
        this.senderConfirmed = false;
        this.targetConfirmed = false;
        this.sync();
        // 通用会话的 sync() 已经同时发给双方，不再重复补发一份。
        if (!genericSession && player instanceof ServerPlayer && player.level() != null) {
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
