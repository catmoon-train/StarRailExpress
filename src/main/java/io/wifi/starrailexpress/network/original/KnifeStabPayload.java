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

package io.wifi.starrailexpress.network.original;

import io.wifi.starrailexpress.SRE;

import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.KillerKnifeDurability;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;
import org.jetbrains.annotations.NotNull;

public record KnifeStabPayload(int target) implements CustomPacketPayload {
    public static final Type<KnifeStabPayload> ID = new Type<>(SRE.id("knifestab"));
    public static final StreamCodec<FriendlyByteBuf, KnifeStabPayload> CODEC = StreamCodec.composite(ByteBufCodecs.INT,
            KnifeStabPayload::target, KnifeStabPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<KnifeStabPayload> {
        @Override
        public void receive(@NotNull KnifeStabPayload payload, ServerPlayNetworking.@NotNull Context context) {
            ServerPlayer player = context.player();
            Entity targetEntity = player.serverLevel().getEntity(payload.target());
            ServerPlayer target = null;
            if ((targetEntity instanceof ServerPlayer ts)) {
                target = ts;
            }

            if ((targetEntity instanceof PuppeteerBodyEntity bodyEntity)) {

                if (bodyEntity.distanceTo(player) > 4.0)
                    return;

                // 对傀儡本体造成致命伤害（20点以上确保击杀）
                bodyEntity.playerHurt(player, GameConstants.DeathReasons.PUPPETEER_KNIFE);

                bodyEntity.playSound(TMMSounds.ITEM_KNIFE_STAB, 1.0f, 1.0f);
                player.swing(InteractionHand.MAIN_HAND);

                if (!player.isCreative()
                    && !SREGameWorldComponent.KEY.get(player.level()).isRole(player, TMMRoles.LOOSE_END)
                    && !SREGameWorldComponent.KEY.get(player.level()).isRole(player,
                            SpecialGameModeRoles.SUPER_LOOSE_END)) {
                    player.getCooldowns().addCooldown(TMMItems.KNIFE,
                            GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.KNIFE, 600));
                }
                return;
            }
            if (target == null)
                return;
            if (target.distanceTo(player) > 3.0)
                return;
            // 杀手刀有限耐久：耗尽的刀不可用（但不会消失），需要重新购买替换。
            // Killer knife limited durability: a depleted knife cannot be used (but is not
            // removed);
            // the killer must re-buy to replace it. Only applies to stamped knives in
            // murder modes.
            ItemStack knife = player.getMainHandItem();
            boolean durabilityKnife = KillerKnifeDurability.isDurabilityModeEnabled(player.level())
                    && KillerKnifeDurability.isMarkedKnife(knife);
            if (durabilityKnife && KillerKnifeDurability.isDepleted(knife)) {
                player.displayClientMessage(
                        Component.translatable("message.sre.knife.depleted").withStyle(ChatFormatting.DARK_RED), true);
                return;
            }
            SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
            final var role = game.getRole(player);
            if (role != null) {
                if (!role.onUseKnifeHit(player, target)) {
                    return;
                }
            }
            GameUtils.killPlayer(target, true, player, GameConstants.DeathReasons.KNIFE);
            target.playSound(TMMSounds.ITEM_KNIFE_STAB, 1.0f, 1.0f);
            // 成功捅人后消耗 1 点耐久；耗尽时提示重新购买。
            // Consume one durability after a successful stab; warn when it becomes
            // depleted.
            if (durabilityKnife && KillerKnifeDurability.consumeOne(knife, player)) {
                player.displayClientMessage(
                        Component.translatable("message.sre.knife.broken").withStyle(ChatFormatting.DARK_RED), true);
            }
            player.swing(InteractionHand.MAIN_HAND);
            var cooldowns = player.getCooldowns();
            if (!player.isCreative()
                    && !SREGameWorldComponent.KEY.get(player.level()).isRole(player, TMMRoles.LOOSE_END)
                    && !SREGameWorldComponent.KEY.get(player.level()).isRole(player,
                            SpecialGameModeRoles.SUPER_LOOSE_END)) {
                cooldowns.addCooldown(TMMItems.KNIFE, GameConstants.ITEM_COOLDOWNS.get(TMMItems.KNIFE));

            }
        }
    }
}
