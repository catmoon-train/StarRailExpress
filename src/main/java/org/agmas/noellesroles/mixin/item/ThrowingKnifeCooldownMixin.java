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


package org.agmas.noellesroles.mixin.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.network.original.KnifeStabPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.agmas.noellesroles.game.roles.killer.stalker.StalkerPlayerComponent;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.agmas.noellesroles.content.item.StalkerKnifeItem.performDashOnHit;

@Mixin(KnifeStabPayload.Receiver.class)
public class ThrowingKnifeCooldownMixin {
    @Inject(method = "receive", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemCooldowns;addCooldown(Lnet/minecraft/world/item/Item;I)V"))
    private void receive(KnifeStabPayload payload, ServerPlayNetworking.Context context, CallbackInfo ci) {
        ServerPlayer player = context.player();
        player.getCooldowns().addCooldown(ModItems.THROWING_KNIFE, (Integer) GameConstants.ITEM_COOLDOWNS.get(TMMItems.KNIFE));
        if (player.getMainHandItem().getItem() == ModItems.STALKER_KNIFE_OFFHAND) {
            boolean isThirdPhase = SREGameWorldComponent.KEY.get(player.serverLevel()).isRole(player, ModRoles.STALKER) && StalkerPlayerComponent.KEY.get(player).phase == 3;
            player.getCooldowns().addCooldown(ModItems.STALKER_KNIFE_OFFHAND, isThirdPhase ? GameConstants.ITEM_COOLDOWNS.get(TMMItems.KNIFE): GameConstants.ITEM_COOLDOWNS.get(TMMItems.KNIFE)-200 );

            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,15,0));
            // ── 击中后向前突进 ────────────────────────────────────────────
            ServerLevel serverLevel = context.player().serverLevel();
            performDashOnHit(serverLevel, player, serverLevel.getEntity(payload.target()));
        }else if (player.getMainHandItem().getItem() == ModItems.STALKER_KNIFE) {
            boolean isThirdPhase = SREGameWorldComponent.KEY.get(player.serverLevel()).isRole(player, ModRoles.STALKER) && StalkerPlayerComponent.KEY.get(player).phase == 3;
            player.getCooldowns().addCooldown(ModItems.STALKER_KNIFE, isThirdPhase ? GameConstants.ITEM_COOLDOWNS.get(TMMItems.KNIFE): GameConstants.ITEM_COOLDOWNS.get(TMMItems.KNIFE)-200 );

            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,15,0));
            // ── 击中后向前突进 ────────────────────────────────────────────
            ServerLevel serverLevel = context.player().serverLevel();
            performDashOnHit(serverLevel, player, serverLevel.getEntity(payload.target()));
        }
    }
}
