package net.exmo.sre.sixtyseconds.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/** 服务端→客户端：打开保险库撬锁小游戏 */
public record OpenVaultLockpickS2CPacket(BlockPos vaultPos) implements CustomPacketPayload {
    public static final Type<OpenVaultLockpickS2CPacket> ID = new Type<>(Noellesroles.id("open_vault_lockpick"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenVaultLockpickS2CPacket> CODEC =
            StreamCodec.ofMember(OpenVaultLockpickS2CPacket::encode, OpenVaultLockpickS2CPacket::decode);

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(vaultPos);
    }

    private static OpenVaultLockpickS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new OpenVaultLockpickS2CPacket(buf.readBlockPos());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
