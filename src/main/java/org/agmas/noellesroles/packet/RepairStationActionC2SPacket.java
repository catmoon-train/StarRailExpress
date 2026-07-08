package org.agmas.noellesroles.packet;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.content.block_entity.RepairStationBlockEntity;
import net.exmo.sre.repair.event.RepairEventSystem;
import net.exmo.sre.repair.util.RepairGameplayEffects;
import net.exmo.sre.repair.state.RepairModeState;

public record RepairStationActionC2SPacket(BlockPos blockPos, boolean greatHit, boolean accidentCheck,
        boolean accidentSuccess) implements CustomPacketPayload {
    public static final Type<RepairStationActionC2SPacket> ID = new Type<>(Noellesroles.id("repair_station_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RepairStationActionC2SPacket> CODEC = StreamCodec
            .ofMember(RepairStationActionC2SPacket::encode, RepairStationActionC2SPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(blockPos);
        buf.writeBoolean(greatHit);
        buf.writeBoolean(accidentCheck);
        buf.writeBoolean(accidentSuccess);
    }

    public static RepairStationActionC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new RepairStationActionC2SPacket(buf.readBlockPos(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(RepairStationActionC2SPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        if (!(player.level() instanceof ServerLevel level)
                || player.distanceToSqr(payload.blockPos().getCenter()) > 36.0D
                || !RepairModeState.canRepair(player)
                || !(level.getBlockEntity(payload.blockPos()) instanceof RepairStationBlockEntity station)) {
            return;
        }

        var component = ModComponents.REPAIR_ROLES.get(player);
        long now = level.getGameTime();
        if (now - component.lastRepairActionTick < RepairModeState.REPAIR_ACTION_COOLDOWN_TICKS) {
            return;
        }
        if (payload.accidentCheck() && !payload.accidentSuccess()) {
            station.triggerAccidentFailure(player);
            component.lastRepairActionTick = now;
            component.sync();
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.accident_failed")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (station.isRepairBlocked()) {
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.station_accident_locked",
                    Math.max(1, (station.getRepairBlockedTicks() + 19) / 20)).withStyle(ChatFormatting.RED), true);
            return;
        }

        String activeRole = component.activeRole;
        int amount = 3;
        if ("mechanic".equals(activeRole)) {
            amount += 2;
        }
        if (payload.greatHit()) {
            amount = Math.max(amount + 1, (int) Math.ceil(amount * 1.75D));
        }
        amount += RepairEventSystem.repairProgressBonus(level);

        boolean jammed = station.isJammed();
        if (!station.addProgress(amount)) {
            return;
        }
        level.playSound(null, payload.blockPos(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS,
                payload.greatHit() ? 0.8F : 0.55F, payload.greatHit() ? 1.25F : 0.9F);

        component.lastRepairActionTick = now;
        component.sync();

        int reward = (payload.greatHit() ? 7 : 3) + RepairEventSystem.repairCoinBonus(level);
        if (station.isCompleted()) {
            reward += 75;
        }
        RepairModeState.awardCoins(player, reward,
                payload.greatHit() ? "repair_coin_source.perfect" : "repair_coin_source.calibration");
        if (jammed) {
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.station_jammed_hint")
                    .withStyle(ChatFormatting.RED), true);
        }
        RepairGameplayEffects.burst(level, payload.blockPos().getX() + 0.5D,
                payload.blockPos().getY() + 0.8D, payload.blockPos().getZ() + 0.5D,
                payload.greatHit() ? 2 : 0);
        RepairModeState.addNeutralTaskProgress(player, "archivist", 1, RepairModeState.ARCHIVIST_TASK_NEEDED);
        RepairModeState.addNeutralTaskProgress(player, "saboteur", payload.greatHit() ? 2 : 1,
                RepairModeState.SABOTEUR_TASK_NEEDED);
        player.displayClientMessage(Component.translatable("message.noellesroles.repair.progress", station.getProgressPercent())
                .withStyle(payload.greatHit() ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
    }
}
