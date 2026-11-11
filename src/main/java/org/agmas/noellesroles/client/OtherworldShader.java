/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostPass;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.MaChenXuRoleData;
import org.joml.Matrix4f;

/**
 * 里世界的独立后处理；不替换 GameRenderer 的后处理链，也不触碰世界方块。
 * 强度由客户端 tick 推进，不随帧率改变。加载失败时保留独立的 HUD 警告。
 */
public class OtherworldShader {
    public static OtherworldShader instance = new OtherworldShader();

    private final Matrix4f projection = new Matrix4f();
    private RenderTarget swapBuffer;
    private PostPass realmPass;
    private PostPass copyPass;
    private boolean failed;

    /** 按需创建；保留旧 GameRenderer mixin 的调用接口。 */
    public void initPostProcessor() {
        if (realmPass != null || failed || !SackGhostRealmFx.hasVisuals()) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            RenderTarget main = mc.getMainRenderTarget();
            swapBuffer = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            swapBuffer.setClearColor(0.0F, 0.0F, 0.0F, 1.0F);
            realmPass = new PostPass(mc.getResourceManager(), "otherworld", main, swapBuffer, true);
            copyPass = new PostPass(mc.getResourceManager(), "blit", swapBuffer, main, true);
            updateProjection(main.width, main.height);
        } catch (Exception error) {
            disable(error);
        } finally {
            mc.getMainRenderTarget().bindWrite(false);
        }
    }

    public void resize(int w, int h) {
        if (swapBuffer == null || w <= 0 || h <= 0) return;
        if (swapBuffer.width == w && swapBuffer.height == h) return;
        try {
            swapBuffer.resize(w, h, Minecraft.ON_OSX);
            updateProjection(w, h);
        } catch (Exception error) {
            disable(error);
        }
    }

    private void updateProjection(int w, int h) {
        projection.setOrtho(0.0F, w, 0.0F, h, 0.1F, 1000.0F);
        realmPass.setOrthoMatrix(projection);
        copyPass.setOrthoMatrix(projection);
    }

    /** 全图影响只读取本人的光环；施法者同步数据作为效果尚未抵达时的后备。 */
    public static boolean isAnyOtherworldActive() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.player.isAlive() || mc.player.isSpectator()
                || SREClient.gameComponent == null || !SREClient.gameComponent.isRunning()) return false;
        if (SackGhostClient.hasActiveRealm()) return true;
        if (mc.player.hasEffect(ModEffects.OTHERWORLD_AURA)) return true;
        MaChenXuRoleData data = getLocalMaChenXuComponent();
        return data != null && data.otherworldActive;
    }

    public static MaChenXuRoleData getLocalMaChenXuComponent() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || SREClient.gameComponent == null) return null;
        if (SREClient.gameComponent.isRole(mc.player, ModRoles.MA_CHEN_XU)) {
            return RoleData.getNullable(MaChenXuRoleData.class, mc.player);
        }
        return null;
    }

    public void renderPostProcess(float partialTicks) {
        if (!SackGhostRealmFx.hasVisuals()) return;
        initPostProcessor();
        if (realmPass == null || copyPass == null || failed) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            RenderTarget main = mc.getMainRenderTarget();
            resize(main.width, main.height);
            if (realmPass == null || copyPass == null) return;
            var effect = realmPass.getEffect();
            effect.safeGetUniform("Strength").set(SackGhostRealmFx.strength(partialTicks));
            effect.safeGetUniform("Time").set(SackGhostRealmFx.time(partialTicks));
            effect.safeGetUniform("Pressure").set(SackGhostRealmFx.pressure(partialTicks));
            effect.safeGetUniform("Condemned").set(SackGhostRealmFx.markStrength(partialTicks));
            effect.safeGetUniform("Entry").set(SackGhostRealmFx.entry(partialTicks));
            realmPass.process(partialTicks);
            copyPass.process(partialTicks);
        } catch (Exception error) {
            disable(error);
        } finally {
            mc.getMainRenderTarget().bindWrite(false);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.defaultBlendFunc();
        }
    }

    /** 退出、断线与换世界共用清理，既清视觉状态也释放显存。 */
    public void reset() {
        SackGhostRealmFx.reset();
    }

    /** 资源重载后才允许重试失败的着色器，避免每帧重复编译与刷日志。 */
    void reload() {
        clearPostProcessor();
        failed = false;
    }

    void clearPostProcessor() {
        if (realmPass != null) {
            realmPass.close();
            realmPass = null;
        }
        if (copyPass != null) {
            copyPass.close();
            copyPass = null;
        }
        if (swapBuffer != null) {
            swapBuffer.destroyBuffers();
            swapBuffer = null;
        }
    }

    private void disable(Exception error) {
        clearPostProcessor();
        failed = true;
        SRE.LOGGER.warn("Otherworld post effect disabled until resource reload; HUD remains available", error);
    }
}
