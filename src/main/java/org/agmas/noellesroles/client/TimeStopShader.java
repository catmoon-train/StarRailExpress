package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.client.PostProcessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import org.agmas.noellesroles.init.ModEffects;

import java.util.function.BooleanSupplier;

public class TimeStopShader {
    public static TimeStopShader instance = new TimeStopShader();
    private PostProcessor m_post;

    // 时间停止状态
    private float timeStopProgress = 0.0f;      // 动画强度 (0~1)，仅前 1.5 秒有效
    private float stopAmount = 0.0f;            // 灰白程度 (0~1)
    private float totalTime = 0.0f;             // 累计时间，用于着色器脉动
    private float effectStartTime = 0;           // 本次效果开始时的 totalTime
    
    // 上一次的状态（用于检测效果开始或刷新）
    private boolean lastHasTimeStop = false;
    private int lastDuration = 0;

    // 常量
    private static final float ANIMATION_DURATION = 1.95f;  // 动画总时长（秒），延长 30%
    private static final float RECOVER_ADVANCE = 1.4f;      // 提前恢复时间（秒），加快 30%
    private static final float STOP_TRANSITION_SPEED = 0.4f; // stopAmount 过渡速度，加快 30%

    public void initPostProcessor() {
        if (m_post != null) return;
        m_post = new PostProcessor();
        initSanityPostProcess();
    }

    public void resize(int w, int h) {
        if (m_post == null) return;
        m_post.resize(w, h);
    }

    private boolean processPlayer(LocalPlayer player, BooleanSupplier action) {
        return player != null && action.getAsBoolean();
    }

    private void initSanityPostProcess() {
        Minecraft mc = Minecraft.getInstance();

        m_post.addSinglePassEntry("timestop", pass -> processPlayer(mc.player, () -> {
            if (!mc.player.hasEffect(ModEffects.TIME_STOP)) return false;
            var effect = pass.getEffect();
            if (effect == null) return false;
        
            // 更新时间（近似 60fps）
            totalTime += 0.016f;
        
            MobEffectInstance timeStopEffect = mc.player.getEffect(ModEffects.TIME_STOP);
            boolean hasTimeStop = timeStopEffect != null;
            int currentDuration = hasTimeStop ? timeStopEffect.getDuration() : 0;
        
            // 检测效果开始或刷新：从无到有，或持续时间增加（例如药水刷新）
            boolean effectStarted = false;
            if (hasTimeStop) {
                if (!lastHasTimeStop) {
                    effectStarted = true;
                } else if (currentDuration > lastDuration) {
                    effectStarted = true;
                }
            }
        
            if (effectStarted) {
                effectStartTime = totalTime;   // 重置动画开始时间
            }
        
            lastHasTimeStop = hasTimeStop;
            lastDuration = currentDuration;
        
            // 效果已持续时间（秒）
            float effectTime = hasTimeStop ? totalTime - effectStartTime : 0.0f;

            // ========== 1. 计算动画强度 timeStopProgress ==========
            if (hasTimeStop && effectTime < ANIMATION_DURATION) {
                // 分段：0-0.65s 上升，0.65-1.3s 保持，1.3-1.95s 下降（延长 30%）
                if (effectTime < 0.65f) {
                    timeStopProgress = (effectTime / 0.65f) * 0.7f;        // 0 → 0.7（幅度减少 30%）
                } else if (effectTime < 1.3f) {
                    timeStopProgress = 0.7f;                                // 保持 0.7
                } else {
                    timeStopProgress = 0.7f - (effectTime - 1.3f) / 0.65f * 0.7f; // 0.7 → 0
                }
                timeStopProgress = Mth.clamp(timeStopProgress, 0.0f, 1.0f);
            } else {
                timeStopProgress = 0.0f;
            }

            // ========== 2. 计算灰白目标值 stopAmount ==========
            float targetStop = 0.0f;
            if (hasTimeStop) {
                if (currentDuration == -1) { // 无限效果
                    targetStop = 1.0f;
                } else {
                    float remainingSec = currentDuration / 20.0f;
                    if (remainingSec <= RECOVER_ADVANCE) {
                        // 剩余 ≤ 2 秒，线性减小
                        targetStop = Math.max(0.0f, remainingSec / RECOVER_ADVANCE);
                    } else {
                        targetStop = 1.0f; // 保持灰白
                    }
                }
            }
            // 平滑过渡（加快速度）
            stopAmount += (targetStop - stopAmount) * STOP_TRANSITION_SPEED;
            stopAmount = Mth.clamp(stopAmount, 0.0f, 1.0f);

            // ========== 3. 设置 Uniform 参数 ==========
            var timeProgressUniform = effect.safeGetUniform("TimeProgress");
            if (timeProgressUniform != null) {
                timeProgressUniform.set(timeStopProgress);
            }

            var stopAmountUniform = effect.safeGetUniform("StopAmount");
            if (stopAmountUniform != null) {
                stopAmountUniform.set(stopAmount);
            }

            var timeTotalUniform = effect.safeGetUniform("TimeTotal");
            if (timeTotalUniform != null) {
                timeTotalUniform.set(totalTime);
            }

            var effectTimeUniform = effect.safeGetUniform("EffectTime");
            if (effectTimeUniform != null) {
                effectTimeUniform.set(effectTime);
            }

            // 只要动画或灰白还可见，就继续渲染
            return timeStopProgress > 0.01f || stopAmount > 0.01f;
        }));
    }

    public void renderPostProcess(float partialTicks) {
        if (m_post == null) return;
        m_post.render(partialTicks);
    }

    public void reset() {
        timeStopProgress = 0.0f;
        stopAmount = 0.0f;
        totalTime = 0.0f;
        effectStartTime = 0;
        lastHasTimeStop = false;
        lastDuration = 0;
    }

    public void forceStart() {
        // 仅用于测试
    }

    public void forceStop() {
        // 仅用于测试
    }
}