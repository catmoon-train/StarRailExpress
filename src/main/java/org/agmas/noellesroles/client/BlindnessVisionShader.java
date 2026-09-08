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

import com.mojang.blaze3d.platform.GlStateManager;
import io.wifi.starrailexpress.client.PostProcessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.EffectInstance;
import org.agmas.noellesroles.init.ModEffects;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * Full-screen Fabric equivalent of Blindness 1.5's composite1 shader.
 * The world is black unless a recent spatial sound reaches the reconstructed
 * surface point; the revealed area is grayscale and its edge is highlighted.
 */
public final class BlindnessVisionShader {
    public static final BlindnessVisionShader INSTANCE = new BlindnessVisionShader();

    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f projectionInverse = new Matrix4f();
    private final Matrix4f modelViewInverse = new Matrix4f();
    private PostProcessor post;
    /** 0 = world pass, 1 = hand-only pass after vanilla clears depth. */
    private float passMode;

    private BlindnessVisionShader() {
    }

    public void initPostProcessor() {
        if (post != null) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        post = new PostProcessor();
        PostProcessor.PostPassEntry entry = post.addSinglePassEntry("blindness_vision", this::preparePass);
        if (entry != null) {
            entry.getInPass().addAuxAsset("DepthSampler", () -> {
                int id = client.getMainRenderTarget().getDepthTextureId();
                GlStateManager._bindTexture(id);
                GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
                return id;
            }, client.getMainRenderTarget().width, client.getMainRenderTarget().height);
        }
    }

    private boolean preparePass(PostPass pass) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || !player.hasEffect(ModEffects.BLIND_VISION)) {
            return false;
        }

        updateProjection(client);
        Camera camera = client.gameRenderer.getMainCamera();
        BlindnessVisionClientHandle.prepareForRender(camera);
        projection.invert(projectionInverse);
        modelViewInverse.identity().rotate(camera.rotation());

        var effect = pass.getEffect();
        if (effect == null) {
            return false;
        }
        set(effect, "EffectStrength", 1.0f);
        set(effect, "PassMode", passMode);
        set(effect, "ProjectionInv", projectionInverse);
        set(effect, "ModelViewInv", modelViewInverse);
        set(effect, "CameraPosition", (float) camera.getPosition().x,
                (float) camera.getPosition().y, (float) camera.getPosition().z);
        // The Forge shader receives OptiFine's screenBrightness uniform. A
        // zero value makes low-volume sounds mathematically almost black, so
        // keep a visible baseline while retaining the grayscale falloff.
        set(effect, "ScreenBrightness", 0.75f);
        for (int i = 0; i < BlindnessVisionClientHandle.MAX_SOURCES; i++) {
            set(effect, "Sound" + i,
                    BlindnessVisionClientHandle.sourceValue(i, 0),
                    BlindnessVisionClientHandle.sourceValue(i, 1),
                    BlindnessVisionClientHandle.sourceValue(i, 2),
                    BlindnessVisionClientHandle.sourceValue(i, 3));
            set(effect, "Param" + i,
                    BlindnessVisionClientHandle.sourceValue(i, 4),
                    BlindnessVisionClientHandle.sourceValue(i, 5),
                    BlindnessVisionClientHandle.sourceValue(i, 6),
                    BlindnessVisionClientHandle.sourceValue(i, 7));
        }
        return true;
    }

    private void updateProjection(Minecraft client) {
        projection.set(client.gameRenderer.getProjectionMatrix(client.options.fov().get()));
    }

    private static void set(EffectInstance effect, String name, float value) {
        var uniform = effect.safeGetUniform(name);
        if (uniform != null) {
            uniform.set(value);
        }
    }

    private static void set(EffectInstance effect, String name, Matrix4f value) {
        var uniform = effect.safeGetUniform(name);
        if (uniform != null) {
            uniform.set(value);
        }
    }

    private static void set(EffectInstance effect, String name, float x, float y, float z) {
        var uniform = effect.safeGetUniform(name);
        if (uniform != null) {
            uniform.set(x, y, z);
        }
    }

    private static void set(EffectInstance effect, String name, float x, float y, float z, float w) {
        var uniform = effect.safeGetUniform(name);
        if (uniform != null) {
            uniform.set(x, y, z, w);
        }
    }

    public void renderPostProcess(float partialTicks) {
        renderPostProcess(partialTicks, false);
    }

    public void renderPostProcess(float partialTicks, boolean handOnly) {
        if (post == null) {
            return;
        }
        passMode = handOnly ? 1.0f : 0.0f;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return;
        }
        for (PostProcessor.PostPassEntry entry : post.passEntries) {
            if (entry.getInPass() == null || entry.getOutPass() == null
                    || entry.getInProcessor() != null && !entry.getInProcessor().apply(entry.getInPass())
                    || entry.getOutProcessor() != null && !entry.getOutProcessor().apply(entry.getOutPass())) {
                continue;
            }
            entry.getInPass().process(partialTicks);
            entry.getOutPass().process(partialTicks);
        }
    }

    public void resize(int width, int height) {
        if (post != null) {
            post.resize(width, height);
        }
    }
}
