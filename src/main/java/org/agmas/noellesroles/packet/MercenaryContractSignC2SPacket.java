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

package org.agmas.noellesroles.packet;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.item.MercenaryContractItem;
import org.agmas.noellesroles.role.ModRoles;

import java.util.UUID;

public record MercenaryContractSignC2SPacket(UUID targetUuid) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MercenaryContractSignC2SPacket> TYPE =
            new CustomPacketPayload.Type<>(Noellesroles.id("mercenary_sign_contract"));

    public static final StreamCodec<FriendlyByteBuf, MercenaryContractSignC2SPacket> CODEC =
            CustomPacketPayload.codec(MercenaryContractSignC2SPacket::write, MercenaryContractSignC2SPacket::new);

    public MercenaryContractSignC2SPacket(FriendlyByteBuf buf) {
        this(buf.readUUID());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(targetUuid);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MercenaryContractSignC2SPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer signer = context.player();
        context.server().execute(() -> {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(signer.level());
            if (!gameWorld.isRunning() || !io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(signer)) {
                return;
            }
            if (gameWorld.isRole(signer, ModRoles.MERCENARY)) {
                signer.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.noellesroles.mercenary.contract_mercenary_cannot_open")
                                .withStyle(ChatFormatting.RED),
                        true);
                return;
            }

            Player target = signer.level().getPlayerByUUID(payload.targetUuid());
            if (target == null || target == signer || !io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(target)) {
                signer.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.noellesroles.mercenary.contract_target_invalid")
                                .withStyle(ChatFormatting.RED),
                        true);
                return;
            }
            if (gameWorld.isRole(target, ModRoles.MERCENARY)) {
                signer.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.noellesroles.mercenary.contract_target_invalid")
                                .withStyle(ChatFormatting.RED),
                        true);
                return;
            }

            ItemStack contractStack = ItemStack.EMPTY;
            InteractionHand handWithContract = null;
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack handItem = signer.getItemInHand(hand);
                if (handItem.is(org.agmas.noellesroles.init.ModItems.MERCENARY_CONTRACT)
                        && !MercenaryContractItem.isSigned(handItem)) {
                    contractStack = handItem;
                    handWithContract = hand;
                    break;
                }
            }

            if (contractStack.isEmpty() || handWithContract == null) {
                return;
            }

            SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(signer);
            if (shop.balance < 175) {
                signer.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.noellesroles.mercenary.contract_not_enough_gold")
                                .withStyle(ChatFormatting.RED),
                        true);
                return;
            }

            shop.addToBalance(-175);
            MercenaryContractItem.applySignedData(
                    contractStack,
                    signer.getUUID(),
                    signer.getScoreboardName(),
                    target.getUUID(),
                    target.getScoreboardName());
            // 回放记录：玩家使用未签订契约花费金币指定通缉目标
            SRE.REPLAY_MANAGER.recordCustomEvent(
                net.minecraft.network.chat.Component.translatable("replay.event.contract.wanted",
                    GameReplayUtils.getReplayPlayerDisplayText(signer, true),
                    GameReplayUtils.getReplayPlayerDisplayText(target, true)));

            signer.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.noellesroles.mercenary.contract_signed_success", target.getDisplayName())
                            .withStyle(ChatFormatting.GOLD),
                    true);
        });
    }
}
