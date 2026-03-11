package org.agmas.noellesroles.packet;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.component.RecorderPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import io.wifi.starrailexpress.cca.GameWorldComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

public record RecorderC2SPacket(UUID targetUuid, String roleId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RecorderC2SPacket> TYPE = new CustomPacketPayload.Type<>(
            Noellesroles.id("recorder_guess"));

    public static final StreamCodec<FriendlyByteBuf, RecorderC2SPacket> CODEC = CustomPacketPayload.codec(
            RecorderC2SPacket::write, RecorderC2SPacket::new);

    public RecorderC2SPacket(FriendlyByteBuf buf) {
        this(buf.readUUID(), buf.readUtf());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(targetUuid);
        buf.writeUtf(roleId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RecorderC2SPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();

        context.server().execute(() -> {
            GameWorldComponent gameWorld = GameWorldComponent.KEY.get(player.level());

            if (!gameWorld.isRole(player, ModRoles.RECORDER))
                return;

            RecorderPlayerComponent recorder = ModComponents.RECORDER.get(player);
            ResourceLocation roleId = ResourceLocation.tryParse(payload.roleId());

            if (roleId != null) {
                recorder.addGuess(payload.targetUuid(), roleId);
            }
        });
    }
}