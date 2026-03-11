package org.agmas.noellesroles.mixin.roles.stalker;

import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameFunctions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.component.StalkerPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin 注入到 GameFunctions.killPlayer 方法
 * 用于记录跟踪者的击杀数（二阶段）
 */
@Mixin(GameFunctions.class)
public abstract class StalkerKillCountMixin {

    /**
     * 在玩家被杀死时触发
     * 检查是否是跟踪者用刀击杀，记录击杀数
     */
    @Inject(method = "killPlayer(Lnet/minecraft/world/entity/player/Player;ZLnet/minecraft/world/entity/player/Player;Lnet/minecraft/resources/ResourceLocation;)V", 
            at = @At("HEAD"))
    private static void onKillPlayerForStalker(Player victim, boolean spawnBody, Player killer, ResourceLocation deathReason, CallbackInfo ci) {
        if (killer == null) return;
        if (victim == null) return;
        
        // 检查是否是刀击杀
        if (!deathReason.equals(GameConstants.DeathReasons.KNIFE)) return;
        
        // 获取跟踪者组件
        StalkerPlayerComponent stalkerComp = ModComponents.STALKER.get(killer);
        
        // 检查是否是活跃的跟踪者且处于二阶段或以上
        if (stalkerComp.isActiveStalker() && stalkerComp.phase >= 2) {
            stalkerComp.addKill();
        }
    }
}