package io.wifi.starrailexpress.mixin.compat.cobblemon;

import io.wifi.starrailexpress.compat.cobblemon.CobblemonBattleControl;
import io.wifi.starrailexpress.compat.cobblemon.CobblemonMechanics;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 各入口只隔离玩法；生命周期、存储发射、任务队列和退出回调不整批取消。 */
public final class ServerMechanicsMixins {
    @Pseudo @Mixin(targets = "com.cobblemon.mod.common.api.reactive.ObservableSubscription", remap = false)
    public static class Events {
        @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
        private void sre$gameplay(Object event, CallbackInfo ci) {
            if (event != null && CobblemonMechanics.gameplayEvent(event.getClass().getName())
                    && !CobblemonMechanics.enabled()) ci.cancel();
        }
    }

    @Pseudo @Mixin(targets = "com.cobblemon.mod.fabric.net.FabricPacketInfo", remap = false)
    public static class Requests {
        @Inject(method = "registerServerHandler$lambda$0", at = @At("HEAD"), cancellable = true)
        private static void sre$requests(@Coerce Object info, @Coerce Object packet, ServerPlayNetworking.Context context, CallbackInfo ci) {
            if (!CobblemonMechanics.enabled() && !CobblemonMechanics.cleanupPacket(packet.getClass().getName())) ci.cancel();
        }
    }

    @Pseudo @Mixin(targets = "com.cobblemon.mod.common.CobblemonCallbacks", remap = false)
    public static class Scripts {
        @Inject(method = "run", at = @At("HEAD"), cancellable = true)
        private void sre$callbacks(ResourceLocation id, Map<?, ?> context, Map<?, ?> functions, @Coerce Object event, CallbackInfo ci) {
            if (!CobblemonMechanics.enabled() && !id.getPath().equals("server_stopping")
                    && !id.getPath().equals("player_logged_out")) ci.cancel();
        }
    }

    @Pseudo @Mixin(targets = "com.cobblemon.mod.common.api.storage.party.PlayerPartyStore", remap = false)
    public static class Party {
        @Inject(method = "onSecondPassed", at = @At("HEAD"), cancellable = true)
        private void sre$party(CallbackInfo ci) { if (!CobblemonMechanics.enabled()) ci.cancel(); }
    }

    @Pseudo @Mixin(targets = "com.cobblemon.mod.common.api.pokemon.effect.ShoulderEffectRegistry", remap = false)
    public static class Shoulder {
        @Inject(method = "refreshEffects", at = @At("HEAD"), cancellable = true)
        private void sre$effects(CallbackInfo ci) { if (!CobblemonMechanics.enabled()) ci.cancel(); }
    }

    @Pseudo @Mixin(targets = "com.cobblemon.mod.common.pokemon.effects.PotionBaseEffect$ShoulderStatusEffectInstance", remap = false)
    public static class ShoulderEffect {
        @Shadow private net.minecraft.world.effect.MobEffectInstance upgrade;
        // 固定 MC 1.21.1：这是 MobEffectInstance.tick 的生产命名；同时保留本项目开发命名。
        @Inject(method = {"tick", "method_5585"}, at = @At("HEAD"), cancellable = true)
        private void sre$expire(net.minecraft.world.entity.LivingEntity entity, Runnable callback, CallbackInfoReturnable<Boolean> cir) {
            if (CobblemonMechanics.enabled()) return;
            boolean restore = upgrade != null && (upgrade.isInfiniteDuration() || upgrade.getDuration() > 0);
            if (restore) entity.forceAddEffect(upgrade, null);
            cir.setReturnValue(restore);
        }
    }

    @Pseudo @Mixin(targets = "com.cobblemon.mod.common.util.PlayerExtensionsKt", remap = false)
    public static class Sleep {
        @Inject(method = "didSleep", at = @At("HEAD"), cancellable = true)
        private static void sre$sleep(CallbackInfoReturnable<Boolean> cir) {
            if (!CobblemonMechanics.enabled()) cir.setReturnValue(false);
        }
    }

    @Pseudo @Mixin(targets = "com.cobblemon.mod.common.starter.CobblemonStarterHandler", remap = false)
    public static class Starter {
        @Inject(method = {"handleJoin", "requestStarterChoice", "chooseStarter"}, at = @At("HEAD"), cancellable = true)
        private void sre$starter(CallbackInfo ci) { if (!CobblemonMechanics.enabled()) ci.cancel(); }
    }

    @Pseudo @Mixin(targets = "com.cobblemon.mod.common.battles.BattleRegistry", remap = false)
    public static class Battles {
        @Shadow @Final private static ConcurrentHashMap<UUID, Object> battleMap;

        @Inject(method = "startBattle", at = @At("HEAD"), cancellable = true)
        private static void sre$newBattle(CallbackInfoReturnable<Object> cir) {
            // 上游启动时的无玩家模拟战用于加载数据，必须保留。
            if (CobblemonMechanics.serverReady() && !CobblemonMechanics.enabled()) cir.setReturnValue(CobblemonBattleControl.disabledResult());
        }

        @Inject(method = "tick", at = @At("HEAD"))
        private void sre$endBattles(CallbackInfo ci) {
            if (!CobblemonMechanics.enabled()) {
                for (Object battle : new ArrayList<>(battleMap.values())) ((CobblemonBattleControl.BattleAccess) battle).sre$stopBattle();
            }
        }
    }

    @Pseudo @Mixin(targets = "com.cobblemon.mod.common.api.battles.model.PokemonBattle", remap = false)
    public abstract static class Battle implements CobblemonBattleControl.BattleAccess {
        @Shadow public abstract void stop();
        @Override public void sre$stopBattle() { stop(); }
    }
}
