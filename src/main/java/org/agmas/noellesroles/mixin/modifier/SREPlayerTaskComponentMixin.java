package org.agmas.noellesroles.mixin.modifier;

import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(SREPlayerTaskComponent.class)
public abstract class SREPlayerTaskComponentMixin {

    /**
     * 记录工作狂玩家开始计时时的游戏时间（用于延迟一秒后再应用加速）
     */
    private static final ConcurrentHashMap<UUID, Long> WORKAHOLIC_START_TIME = new ConcurrentHashMap<>();

    /**
     * 工作狂效果：任务刷新延后一秒，然后加速50%
     * 1. 首先设置正常的任务刷新时间
     * 2. 一秒后获取当前游戏时间，计算已过时间
     * 3. 减去已过时间后再应用50%加速
     */
    @Inject(method = "serverTick", at = @At(value = "FIELD", target = "Lio/wifi/starrailexpress/cca/SREPlayerTaskComponent;nextTaskTimer:I", ordinal = 0, shift = At.Shift.AFTER))
    public void onWorkaholicTaskRefresh(CallbackInfo ci) {
        SREPlayerTaskComponent self = (SREPlayerTaskComponent) (Object) this;
        if (self.getPlayer() instanceof ServerPlayer player) {
            if (self.nextTaskTimer > 2) {
                WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(player.level());
                if (modifiers.isModifier(player.getUUID(), TraitorAndModifiers.WORKAHOLIC)) {
                    // 获取当前游戏时间
                    long currentGameTime = player.serverLevel().getGameTime();
                    UUID uuid = player.getUUID();
                    
                    // 如果没有记录开始时间，说明是第一次进入这个刷新周期
                    if (!WORKAHOLIC_START_TIME.containsKey(uuid)) {
                        // 记录开始时间
                        WORKAHOLIC_START_TIME.put(uuid, currentGameTime);
                        // 先不应用加速，让任务刷新正常进行
                    } else {
                        // 获取开始时间
                        long startTime = WORKAHOLIC_START_TIME.get(uuid);
                        // 计算已过游戏时间
                        long elapsed = currentGameTime - startTime;
                        
                        // 如果已过1秒（20 ticks），则应用工作狂加速效果
                        if (elapsed >= 20) {
                            // 清除开始时间记录
                            WORKAHOLIC_START_TIME.remove(uuid);
                            
                            // 工作狂效果：任务刷新速度加快50%（缩短50%等待时间），最低为1tick
                            self.nextTaskTimer = (int) (self.nextTaskTimer * 0.5);
                            self.nextTaskTimer = Math.max(self.nextTaskTimer, 1);
                        }
                    }
                }
            }
        }
    }
}
