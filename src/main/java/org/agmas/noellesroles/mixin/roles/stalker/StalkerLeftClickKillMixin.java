package org.agmas.noellesroles.mixin.roles.stalker;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.component.MaChenXuPlayerComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.component.StalkerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin 注入到 ServerPlayerEntity.attack 方法
 * 让跟踪者二阶段持刀左键攻击也能直接杀死玩家
 */
@Mixin(ServerPlayer.class)
public abstract class StalkerLeftClickKillMixin {
    /**
     * 在玩家攻击实体时触发
     * 如果是跟踪者二阶段以上持刀攻击玩家，则直接击杀目标
     */
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void onStalkerKnifeAttack(Entity target, CallbackInfo ci) {
        ServerPlayer attacker = (ServerPlayer) (Object) this;

        // 检查目标是否是玩家
        if (!(target instanceof Player targetPlayer))
            return;
        // 是否为 stalker
        if (!SREGameWorldComponent.KEY.get(attacker.level()).isRole(attacker, ModRoles.STALKER))
            return;
        // 检查目标是否存活
        if (!GameUtils.isPlayerAliveAndSurvival(attacker))
            return;
        // 检查目标是否存活
        if (!GameUtils.isPlayerAliveAndSurvival(targetPlayer))
            return;

        // 获取跟踪者组件
        StalkerPlayerComponent stalkerComp = ModComponents.STALKER.get(attacker);

        // 检查是否是活跃的跟踪者且处于二阶段
        if (!stalkerComp.isActiveStalker())
            return;
        if (stalkerComp.phase < 2)
            return;

        // 检查手持物品是否是刀
        ItemStack mainHand = attacker.getItemInHand(InteractionHand.MAIN_HAND);
        if (!mainHand.is(TMMItems.KNIFE))
            return;

        // 三阶段时不能用左键击杀，只能用突进
        if (stalkerComp.phase == 3 && stalkerComp.dashModeActive) {
            ci.cancel();
            return;
        }

        // 检查攻击是否在冷却中
        if (stalkerComp.isAttackOnCooldown()) {
            ci.cancel();
            return;
        }

        // 二阶段：左键直接击杀
        GameUtils.killPlayer(targetPlayer, true, attacker, GameConstants.DeathReasons.KNIFE);

        // 触发攻击冷却
        stalkerComp.triggerAttackCooldown();

        // 击杀后定身0.5秒（10 tick）- 使用缓慢和失明来模拟
        attacker.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN, 10, 127, false, false, false));
        attacker.addEffect(new MobEffectInstance(
                MobEffects.DIG_SLOWDOWN, 10, 127, false, false, false));

        // 取消原始攻击逻辑
        ci.cancel();
    }

    /**
     * 在MaChenXu攻击实体时触发
     * 里世界中：左键标记玩家（无SAN要求），里世界结束后标记者死亡
     * 里世界外：对SAN≤10的玩家执行魂噬
     */
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void onMaChenXuKnifeAttack(Entity target, CallbackInfo ci) {
        ServerPlayer attacker = (ServerPlayer) (Object) this;

        // 检查目标是否是玩家
        if (!(target instanceof Player targetPlayer))
            return;
        // 是否为 machenxu
        if (!SREGameWorldComponent.KEY.get(attacker.level()).isRole(attacker, ModRoles.MA_CHEN_XU))
            return;
        // 检查攻击者是否存活
        if (!GameUtils.isPlayerAliveAndSurvival(attacker))
            return;
        // 检查目标是否存活
        if (!GameUtils.isPlayerAliveAndSurvival(targetPlayer))
            return;
        var mcxpc = MaChenXuPlayerComponent.KEY.get(attacker);

        if (mcxpc.otherworldActive) {
            // 里世界中：标记玩家
            if (mcxpc.markPlayer(targetPlayer)) {
                ci.cancel();
            }
        } else {
            // 里世界外：魂噬（SAN≤10）
            if (mcxpc.soulDevour(targetPlayer)) {
                ci.cancel();
            }
        }
    }
}