package org.agmas.noellesroles.mixin.modifier;

import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent.TrainTask;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.role.ModifierEffects;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

@Mixin(SREPlayerTaskComponent.class)
public class ManicTaskBlockMixin {

    /**
     * 狂躁症效果：
     * 1. 阻止任务完成（通过重置 fulfilled 标志）
     * 2. 附近有玩家完成任务时触发奖励
     */
    @Inject(method = "serverTick", at = @At("TAIL"))
    public void onServerTick(CallbackInfo ci) {
        SREPlayerTaskComponent self = (SREPlayerTaskComponent) (Object) this;
        if (!(self.getPlayer() instanceof ServerPlayer player)) return;
        
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.serverLevel());
        if (gameWorld == null || !gameWorld.isRunning()) return;
        
        WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(player.serverLevel());
        if (modifiers.isModifier(player.getUUID(), TraitorAndModifiers.MANIC)) {
            // 狂躁症玩家：阻止任务完成（通过将 fulfilled 标志重置）
            for (TrainTask task : new ArrayList<>(self.tasks.values())) {
                if (task instanceof SREPlayerTaskComponent.EatTask eatTask) {
                    eatTask.fulfilled = false;
                } else if (task instanceof SREPlayerTaskComponent.DrinkTask drinkTask) {
                    drinkTask.fulfilled = false;
                }
            }
        }
        
        // 检查附近5格范围内是否有狂躁症玩家，并触发奖励
        for (ServerPlayer nearby : player.serverLevel().getServer().getPlayerList().getPlayers()) {
            if (nearby == player) continue;
            if (!GameUtils.isPlayerAliveAndSurvival(nearby)) continue;
            
            double dist = player.distanceTo(nearby);
            if (dist <= 5.0) {
                WorldModifierComponent nearbyModifiers = WorldModifierComponent.KEY.get(nearby.serverLevel());
                if (nearbyModifiers.isModifier(nearby.getUUID(), TraitorAndModifiers.MANIC)) {
                    ModifierEffects.onNearbyTaskComplete(nearby);
                }
            }
        }
    }
}
