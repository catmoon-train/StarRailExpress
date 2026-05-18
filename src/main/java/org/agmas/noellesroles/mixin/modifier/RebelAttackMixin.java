package org.agmas.noellesroles.mixin.modifier;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 起义军效果 - 当起义军玩家被平民攻击时，平民因误杀平民而死亡
 */
@Mixin(LivingEntity.class)
public abstract class RebelAttackMixin {

    @Inject(method = "attack(Lnet/minecraft/world/entity/Entity;)V", 
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", ordinal = 0),
            cancellable = true)
    private void onAttackTarget(Entity target, CallbackInfoReturnable<Boolean> cir) {
        // 只在服务端处理
        if (!((Object) this instanceof Player attacker) || attacker.level().isClientSide) return;
        if (!(target instanceof Player victim)) return;
        
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(attacker.level());
        if (gameWorld == null || !gameWorld.isRunning()) return;
        
        // 检查被攻击者是否是起义军玩家
        WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(attacker.level());
        if (modifiers != null && modifiers.isModifier(victim.getUUID(), TraitorAndModifiers.REBEL)) {
            // 检查攻击者是否是平民阵营
            if (gameWorld.isInnocent(attacker)) {
                // 取消攻击
                cir.setReturnValue(false);
                
                // 攻击者因误杀平民而死亡
                attacker.displayClientMessage(
                        Component.translatable("modifier.noellesroles.rebel.mistake_kill"), true);
                // 使用通用死亡原因
                GameUtils.killPlayer((ServerPlayer) attacker, true, (ServerPlayer) victim, 
                        ResourceLocation.fromNamespaceAndPath("starrail_express", "mistake_kill"));
            }
        }
    }
}
