package net.exmo.sre.sixtyseconds.network;

import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.content.block.SixtySecondsVaultBlock;
import net.exmo.sre.sixtyseconds.content.block_entity.SixtySecondsVaultBlockEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.agmas.noellesroles.Noellesroles;

/** 客户端→服务端：保险库撬锁成功通知 */
public record VaultLockpickCompleteC2SPacket(BlockPos vaultPos) implements CustomPacketPayload {
    public static final Type<VaultLockpickCompleteC2SPacket> ID = new Type<>(Noellesroles.id("vault_lockpick_complete"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VaultLockpickCompleteC2SPacket> CODEC =
            StreamCodec.ofMember(VaultLockpickCompleteC2SPacket::encode, VaultLockpickCompleteC2SPacket::decode);

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(vaultPos);
    }

    private static VaultLockpickCompleteC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new VaultLockpickCompleteC2SPacket(buf.readBlockPos());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }

    public static void handle(VaultLockpickCompleteC2SPacket packet, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            if (!SixtySecondsMod.isActive(player.level())) return;
            BlockPos pos = packet.vaultPos();
            BlockEntity be = player.level().getBlockEntity(pos);
            if (!(be instanceof SixtySecondsVaultBlockEntity vault)) return;

            BlockState state = player.level().getBlockState(pos);
            int rows = state.getBlock() instanceof SixtySecondsVaultBlock vb ? vb.rows() : 3;
            MenuType<?> menuType = switch (rows) {
                case 2 -> MenuType.GENERIC_9x2;
                case 6 -> MenuType.GENERIC_9x6;
                default -> MenuType.GENERIC_9x3;
            };
            player.openMenu(new SimpleMenuProvider(
                    (syncId, inv, p) -> new ChestMenu(menuType, syncId, inv, vault, rows),
                    Component.translatable(state.getBlock().getDescriptionId())));
        });
    }
}
