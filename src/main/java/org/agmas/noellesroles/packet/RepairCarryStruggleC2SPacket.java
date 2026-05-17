package org.agmas.noellesroles.packet;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.modes.repair.RepairModeState;

public record RepairCarryStruggleC2SPacket(String side) implements CustomPacketPayload {
    public static final Type<RepairCarryStruggleC2SPacket> ID = new Type<>(Noellesroles.id("repair_carry_struggle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RepairCarryStruggleC2SPacket> CODEC = StreamCodec
            .ofMember(RepairCarryStruggleC2SPacket::encode, RepairCarryStruggleC2SPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(side);
    }

    public static RepairCarryStruggleC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new RepairCarryStruggleC2SPacket(buf.readUtf());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(RepairCarryStruggleC2SPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        var component = ModComponents.REPAIR_ROLES.get(player);
        if (component.carriedBy == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(level.getPlayerByUUID(component.carriedBy) instanceof ServerPlayer hunter)) {
            component.carriedBy = null;
            component.sync();
            return;
        }
        String side = "right".equals(payload.side()) ? "right" : "left";
        long now = level.getGameTime();
        if (now - component.lastStruggleTick < 3) {
            return;
        }
        int gain = side.equals(component.lastStruggleSide) ? 2 : 11;
        if ("runner".equals(component.activeRole)) {
            gain += 2;
        }
        var hunterComponent = ModComponents.REPAIR_ROLES.get(hunter);
        if ("brute".equals(hunterComponent.activeRole)) {
            gain = Math.max(1, gain - 3);
        }
        component.lastStruggleSide = side;
        component.lastStruggleTick = now;
        component.carryStruggleProgress = Math.min(100, component.carryStruggleProgress + gain);
        component.sync();
        if (component.carryStruggleProgress >= 100) {
            RepairModeState.dropCarried(hunter, 20 * 6);
            component.downed = true;
            player.setPose(net.minecraft.world.entity.Pose.SWIMMING);
            component.sync();
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.struggle_free")
                    .withStyle(ChatFormatting.GREEN), true);
            hunter.displayClientMessage(Component.translatable("message.noellesroles.repair.struggle_escape")
                    .withStyle(ChatFormatting.RED), true);
        }
    }
}
