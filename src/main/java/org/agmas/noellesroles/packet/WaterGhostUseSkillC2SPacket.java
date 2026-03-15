package org.agmas.noellesroles.packet;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.WaterGhostPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public record WaterGhostUseSkillC2SPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<WaterGhostUseSkillC2SPacket> TYPE = new CustomPacketPayload.Type<>(
            Noellesroles.id("water_ghost_use_skill"));

    public static final StreamCodec<FriendlyByteBuf, WaterGhostUseSkillC2SPacket> CODEC = StreamCodec.unit(new WaterGhostUseSkillC2SPacket());

    public WaterGhostUseSkillC2SPacket(FriendlyByteBuf buf) {
        this();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WaterGhostUseSkillC2SPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();

        context.server().execute(() -> {
            // 检查玩家是否存活
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                return;
            }

            // 检查是否是水鬼角色
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
            if (!gameWorldComponent.isRole(player, ModRoles.WATER_GHOST)) {
                return;
            }

            // 使用技能
            WaterGhostPlayerComponent component = WaterGhostPlayerComponent.KEY.get(player);
            component.useSkill();
        });
    }
}
