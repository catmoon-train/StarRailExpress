package org.agmas.noellesroles.mixin.halloween.pumpkin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREWeakArmorPlayerComponent;
import io.wifi.starrailexpress.event.EarlyKillPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.halloween.pumpkin.PumpkinSpiritHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 事件的否决不能挡 forceDeath，因此在真实凶手解析后、任何盾与奖励前阻断。 */
@Mixin(GameMode.class)
public abstract class PumpkinDeathMixin {
    @Inject(method = "killPlayer", at = @At(value = "FIELD",
            target = "Lio/wifi/starrailexpress/event/OnKillPlayerTriggered;EVENT:Lnet/fabricmc/fabric/api/event/Event;"), cancellable = true)
    private void pumpkin$noKilling(Player victim, boolean body, Player providedKiller,
            ResourceLocation reason, boolean force, CallbackInfo ci, @Local(name = "killer") Player killer) {
        if (PumpkinSpiritHandler.preventsKill(victim, killer)) {
            if (killer instanceof net.minecraft.server.level.ServerPlayer actor
                    && victim instanceof net.minecraft.server.level.ServerPlayer target)
                PumpkinSpiritHandler.tryPumpkinAttack(actor, target);
            ci.cancel();
        }
    }

    @WrapOperation(method = "killPlayer", at = @At(value = "INVOKE",
            target = "Lio/wifi/starrailexpress/event/EarlyKillPlayer;findTrueKiller(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/resources/ResourceLocation;Z)Lnet/minecraft/world/entity/player/Player;"))
    private Player pumpkin$noSuppressionAttribution(EarlyKillPlayer event, Player victim, Player killer,
            ResourceLocation reason, boolean force, Operation<Player> original) {
        return PumpkinSpiritHandler.isSuppressionDeath(victim) ? null : original.call(event, victim, killer, reason, force);
    }

    @WrapOperation(method = "killPlayer", at = @At(value = "INVOKE",
            target = "Lio/wifi/starrailexpress/cca/SREWeakArmorPlayerComponent;getWeakArmor()I"))
    private int pumpkin$skipWeakShield(SREWeakArmorPlayerComponent shield, Operation<Integer> original,
            @Local(argsOnly = true, ordinal = 0) Player victim) {
        return PumpkinSpiritHandler.isSuppressionDeath(victim) ? 0 : original.call(shield);
    }

    @WrapOperation(method = "killPlayer", at = @At(value = "INVOKE",
            target = "Lio/wifi/starrailexpress/api/GameMode;shouldGiveKillerBalance(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/resources/ResourceLocation;)Z"))
    private boolean pumpkin$noBaseCoins(GameMode mode, Player victim, Player killer, ResourceLocation reason,
            Operation<Boolean> original) {
        return !PumpkinSpiritHandler.deniesKillerCoins(victim, killer) && original.call(mode, victim, killer, reason);
    }

    @WrapOperation(method = "killPlayer", at = @At(value = "INVOKE",
            target = "Lio/wifi/starrailexpress/api/SRERole;grantKillCoin(Lnet/minecraft/world/entity/player/Player;)V"))
    private void pumpkin$noExtraKillCoins(SRERole role, Player killer, Operation<Void> original,
            @Local(argsOnly = true, ordinal = 0) Player victim) {
        if (!PumpkinSpiritHandler.deniesKillerCoins(victim, killer)) original.call(role, killer);
    }
}
