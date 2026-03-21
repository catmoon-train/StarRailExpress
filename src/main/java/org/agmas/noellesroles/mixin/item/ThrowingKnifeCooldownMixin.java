
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
import org.agmas.noellesroles.component.StalkerPlayerComponent;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.agmas.noellesroles.item.StalkerKnifeItem.performDashOnHit;

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
