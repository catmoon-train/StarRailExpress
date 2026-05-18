package org.agmas.noellesroles.mixin.modifier;

import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent.TrainTask;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.role.ModifierEffects;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

/**
 * 狂躁症效果：
 * 1. 任务名字乱码（通过客户端渲染时处理）
 * 2. 阻止任务完成（狂躁症玩家即使满足条件也不视为完成）
 * 3. 完成任务时不给金币和理智
 * 4. 当附近玩家完成任务时触发奖励（每5秒最多一次）
 */
@Mixin(SREPlayerTaskComponent.class)
public class ManicTaskBlockMixin {

    /**
     * 阻止狂躁症玩家的任务完成
     * 通过将 isFulfilled 标记为 false 来阻止任务完成
     */
    @Inject(method = "serverTick", at = @At(value = "INVOKE", target = "Lio/wifi/starrailexpress/cca/SREPlayerTaskComponent$TrainTask;tick(Lnet/minecraft/world/entity/player/Player;)V", shift = At.Shift.AFTER))
    private void onTaskTick(CallbackInfo ci) {
        SREPlayerTaskComponent self = (SREPlayerTaskComponent) (Object) this;
        if (!(self.getPlayer() instanceof ServerPlayer player)) return;
        
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.serverLevel());
        if (gameWorld == null || !gameWorld.isRunning()) return;
        
        WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(player.serverLevel());
        
        // 狂躁症玩家：阻止任务完成（通过将 fulfilled 标志重置）
        if (modifiers.isModifier(player.getUUID(), TraitorAndModifiers.MANIC)) {
            for (TrainTask task : new ArrayList<>(self.tasks.values())) {
                if (task instanceof SREPlayerTaskComponent.EatTask eatTask) {
                    eatTask.fulfilled = false;
                } else if (task instanceof SREPlayerTaskComponent.DrinkTask drinkTask) {
                    drinkTask.fulfilled = false;
                }
            }
        }
    }

    /**
     * 当有任务完成时，检查附近是否有狂躁症玩家
     */
    @Inject(method = "serverTick", at = @At(value = "FIELD", target = "Lio/wifi/starrailexpress/cca/SREPlayerTaskComponent;parallelTaskGenerated:Z", ordinal = 0))
    private void onTaskComplete(CallbackInfo ci) {
        SREPlayerTaskComponent self = (SREPlayerTaskComponent) (Object) this;
        if (!(self.getPlayer() instanceof ServerPlayer player)) return;
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return;
        
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
    
    /**
     * 狂躁症玩家完成任务时不获得奖励
     * 通过在addMood调用前检查并跳过
     */
    @Inject(method = "serverTick", at = @At(value = "INVOKE", target = "Lio/wifi/starrailexpress/cca/SREPlayerMoodComponent;addMood(F)V", ordinal = 0, shift = At.Shift.BEFORE))
    private void onMoodGain(CallbackInfo ci) {
        SREPlayerTaskComponent self = (SREPlayerTaskComponent) (Object) this;
        if (!(self.getPlayer() instanceof ServerPlayer player)) return;
        
        WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(player.serverLevel());
        
        // 狂躁症玩家不获得理智奖励
        if (modifiers.isModifier(player.getUUID(), TraitorAndModifiers.MANIC)) {
            ci.cancel();
        }
    }

    /**
     * 阻止狂躁症玩家在任务完成时获得金币
     */
    @Inject(method = "serverTick", at = @At(value = "INVOKE", target = "Lio/wifi/starrailexpress/api/RoleMethodDispatcher;callOnFinishQuest(Lnet/minecraft/world/entity/player/Player;Ljava/lang/String;IZ)V", ordinal = 0, shift = At.Shift.BEFORE))
    private void onQuestFinish(CallbackInfo ci) {
        SREPlayerTaskComponent self = (SREPlayerTaskComponent) (Object) this;
        if (!(self.getPlayer() instanceof ServerPlayer player)) return;
        
        WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(player.serverLevel());
        
        // 狂躁症玩家完成任务时，不调用onFinishQuest（阻止金币奖励）
        if (modifiers.isModifier(player.getUUID(), TraitorAndModifiers.MANIC)) {
            ci.cancel();
        }
    }
}
