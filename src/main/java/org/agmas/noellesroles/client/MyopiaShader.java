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
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.world.effect.MobEffectInstance;
import org.agmas.noellesroles.content.effects.StatusAilmentPolicy;
import org.agmas.noellesroles.init.ModEffects;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * Depth-based myopia: distant geometry goes soft, nearby geometry stays readable.
 */
public final class MyopiaShader {
    public static final MyopiaShader INSTANCE = new MyopiaShader();

    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f projectionInverse = new Matrix4f();
    private PostProcessor post;

    private MyopiaShader() {
    }

    public void initPostProcessor() {
        if (post != null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        post = new PostProcessor();
        PostProcessor.PostPassEntry entry = post.addSinglePassEntry("myopia", this::preparePass);
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
        if (player == null) {
            return false;
        }
        MobEffectInstance effectInstance = player.getEffect(ModEffects.MYOPIA);
        if (effectInstance == null) {
            return false;
        }
        EffectInstance effect = pass.getEffect();
        if (effect == null) {
            return false;
        }
        projection.set(client.gameRenderer.getProjectionMatrix(client.options.fov().get()));
        projection.invert(projectionInverse);
        int amplifier = effectInstance.getAmplifier();
        set(effect, "Strength", StatusAilmentPolicy.myopiaStrength(amplifier));
        set(effect, "FocusDistance", StatusAilmentPolicy.myopiaFocusBlocks(amplifier));
        set(effect, "ProjectionInv", projectionInverse);
        return true;
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

    public void renderPostProcess(float partialTicks) {
        if (post == null) {
            return;
        }
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
