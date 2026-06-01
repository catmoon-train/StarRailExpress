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
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!RepairModeState.isRepairGameRunning(level)) {
            return;
        }
        if ("downed".equals(payload.side())) {
            handleDownedStruggle(player, level, component);
            return;
        }
        if (component.carriedBy == null) {
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
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    org.agmas.noellesroles.init.ModEffects.NO_COLLIDE, 20 * 60, 0, false, false, true));
            component.sync();
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.struggle_free")
                    .withStyle(ChatFormatting.GREEN), true);
            hunter.displayClientMessage(Component.translatable("message.noellesroles.repair.struggle_escape")
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    private static void handleDownedStruggle(ServerPlayer player, ServerLevel level,
            org.agmas.noellesroles.component.RepairRolePlayerComponent component) {
        if (!component.downed || component.carriedBy != null || component.trialStand.present()) {
            return;
        }
        long now = level.getGameTime();
        if (now - component.downedLastStruggleTick < 6) {
            return;
        }
        component.downedLastStruggleTick = now;
        component.downedStruggleProgress = Math.min(100, component.downedStruggleProgress +
                ("runner".equals(component.activeRole) ? 9 : 7));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                org.agmas.noellesroles.init.ModEffects.NO_COLLIDE, 40, 0, false, false, true));
        if (component.downedStruggleProgress >= 100) {
            component.downedStruggleProgress = 0;
            component.carryBlockedTicks = Math.max(component.carryBlockedTicks, 20 * 4);
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.downed_struggle_relief")
                    .withStyle(ChatFormatting.GREEN), true);
        }
        component.sync();
    }
}
