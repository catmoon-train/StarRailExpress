package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.client.PostProcessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.agmas.noellesroles.component.MaChenXuPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import io.wifi.starrailexpress.client.SREClient;

import java.util.function.BooleanSupplier;

/**
 * 里世界 Shader 效果
 * 当任何玩家附近存在布袋鬼开启里世界时，对好人显示黑暗扭曲的后处理效果
 */
public class OtherworldShader {
    public static OtherworldShader instance = new OtherworldShader();
    private PostProcessor m_post;

    private float otherworldStrength = 0.0f;
    private float totalTime = 0.0f;
    /** 过渡动画：进入时先闪白再变暗 */
    private float transitionFlash = 0.0f;
    private boolean wasActive = false;

    private static final float FADE_IN_SPEED = 0.02f;
    private static final float FADE_OUT_SPEED = 0.04f;

    public void initPostProcessor() {
        if (m_post != null) return;
        m_post = new PostProcessor();
        initOtherworldPostProcess();
    }

    public void resize(int w, int h) {
        if (m_post == null) return;
        m_post.resize(w, h);
    }

    private boolean processPlayer(LocalPlayer player, BooleanSupplier action) {
        return player != null && action.getAsBoolean();
    }

    /**
     * 检查当前世界是否有任何布袋鬼正在使用里世界
     */
    public static boolean isAnyOtherworldActive() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        for (var player : mc.level.players()) {
            if (SREClient.gameComponent != null && SREClient.gameComponent.isRole(player, ModRoles.MA_CHEN_XU)) {
                MaChenXuPlayerComponent comp = MaChenXuPlayerComponent.KEY.get(player);
                if (comp.otherworldActive) return true;
            }
        }
        return false;
    }

    /**
     * 获取布袋鬼的MaChenXuPlayerComponent（本地玩家为布袋鬼时）
     */
    public static MaChenXuPlayerComponent getLocalMaChenXuComponent() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || SREClient.gameComponent == null) return null;
        if (SREClient.gameComponent.isRole(mc.player, ModRoles.MA_CHEN_XU)) {
            return MaChenXuPlayerComponent.KEY.get(mc.player);
        }
        return null;
    }

    private void initOtherworldPostProcess() {
        Minecraft mc = Minecraft.getInstance();

        m_post.addSinglePassEntry("otherworld", pass -> processPlayer(mc.player, () -> {
            boolean isActive = isAnyOtherworldActive();

            // 布袋鬼自己不受shader影响（他是施法者）
            MaChenXuPlayerComponent localComp = getLocalMaChenXuComponent();
            if (localComp != null && localComp.otherworldActive) {
                // 布袋鬼自己也显示轻微效果（更沉浸）
                // 但使用减弱版
            }

            totalTime += 0.016f;

            // 检测过渡：刚激活时触发闪白效果
            if (isActive && !wasActive) {
                transitionFlash = 1.5f; // 初始闪白强度
            }
            wasActive = isActive;

            // 闪白衰减
            if (transitionFlash > 0) {
                transitionFlash = Math.max(0, transitionFlash - 0.03f);
            }

            // 平滑渐入渐出（更慢的过渡，更有仪式感）
            if (isActive) {
                otherworldStrength = Math.min(1.0f, otherworldStrength + FADE_IN_SPEED);
            } else {
                otherworldStrength = Math.max(0.0f, otherworldStrength - FADE_OUT_SPEED);
            }

            if (otherworldStrength <= 0.01f) return false;

            var effect = pass.getEffect();
            if (effect == null) return false;

            // 布袋鬼自己效果减弱
            float finalStrength = otherworldStrength;
            if (localComp != null && localComp.otherworldActive) {
                finalStrength *= 0.3f;
            }
            // 过渡闪白叠加（进入时短暂增强到过度曝光再恢复）
            finalStrength = Math.min(2.0f, finalStrength + transitionFlash);

            var strengthUniform = effect.safeGetUniform("Strength");
            if (strengthUniform != null) {
                strengthUniform.set(finalStrength);
            }

            var timeUniform = effect.safeGetUniform("Time");
            if (timeUniform != null) {
                timeUniform.set(totalTime);
            }

            var pulseUniform = effect.safeGetUniform("PulsePhase");
            if (pulseUniform != null) {
                pulseUniform.set((totalTime % 30.0f) / 30.0f);
            }

            return true;
        }));
    }

    public void renderPostProcess(float partialTicks) {
        if (m_post == null) return;
        m_post.render(partialTicks);
    }

    public void reset() {
        otherworldStrength = 0.0f;
        totalTime = 0.0f;
        transitionFlash = 0.0f;
        wasActive = false;
    }
}
