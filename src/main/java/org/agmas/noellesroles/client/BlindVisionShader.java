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

import io.wifi.starrailexpress.client.PostProcessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.PostPass;
import org.agmas.noellesroles.init.ModEffects;
import org.joml.Vector3f;

public class BlindVisionShader {
    public static final BlindVisionShader instance = new BlindVisionShader();
    private static final int SLOTS = BlindVisionClientHandle.MAX_SOURCES;
    private static final float POS_LERP = 0.10f;
    private static final float RADIUS_LERP = 0.08f;
    private static final float FADE_IN = 0.07f;
    private static final float FADE_OUT = 0.028f;

    private PostProcessor post;
    private float strength;
    private float totalTime;
    private boolean depthBound;
    private final Slot[] slots = new Slot[SLOTS];

    public BlindVisionShader() {
        for (int i = 0; i < SLOTS; i++) {
            slots[i] = new Slot();
        }
    }

    public void initPostProcessor() {
        if (post != null) {
            return;
        }
        post = new PostProcessor();
        Minecraft mc = Minecraft.getInstance();
        post.addSinglePassEntry("blind_vision", pass -> {
            LocalPlayer player = mc.player;
            if (player == null) {
                strength = 0.0f;
                return false;
            }
            boolean on = player.hasEffect(ModEffects.BLIND_VISION);
            if (on) {
                strength = Math.min(1.0f, strength + 0.05f);
            } else {
                strength = Math.max(0.0f, strength - 0.04f);
            }
            if (strength <= 0.01f) {
                return false;
            }
            totalTime += 0.016f;
            bindDepth(mc, pass);
            var effect = pass.getEffect();
            if (effect == null) {
                return false;
            }
            var strengthUniform = effect.safeGetUniform("Strength");
            if (strengthUniform != null) {
                strengthUniform.set(strength);
            }
            var timeUniform = effect.safeGetUniform("Time");
            if (timeUniform != null) {
                timeUniform.set(totalTime);
            }
            BlindVisionClientHandle.ensureProjection(mc);
            var nearUniform = effect.safeGetUniform("Near");
            if (nearUniform != null) {
                nearUniform.set(BlindVisionClientHandle.cameraNear());
            }
            var farUniform = effect.safeGetUniform("Far");
            if (farUniform != null) {
                farUniform.set(BlindVisionClientHandle.cameraFar());
            }
            var fovUniform = effect.safeGetUniform("TanHalfFov");
            if (fovUniform != null) {
                fovUniform.set(BlindVisionClientHandle.tanHalfFov());
            }
            var aspectUniform = effect.safeGetUniform("Aspect");
            if (aspectUniform != null) {
                aspectUniform.set(BlindVisionClientHandle.aspect());
            }
            Camera camera = mc.gameRenderer.getMainCamera();
            Vector3f rel = BlindVisionClientHandle.relScratch();

            BlindVisionClientHandle.Echo[] buffer = BlindVisionClientHandle.uploadBuffer();
            int count = BlindVisionClientHandle.copyEchoes(buffer);
            updateSlots(buffer, count);
            int live = 0;
            for (Slot slot : slots) {
                if (slot.fade > 0.01f) {
                    live++;
                }
            }
            var countUniform = effect.safeGetUniform("SoundCount");
            if (countUniform != null) {
                countUniform.set((float) live);
            }
            for (int i = 0; i < SLOTS; i++) {
                Slot slot = slots[i];
                var soundUniform = effect.safeGetUniform("Sound" + i);
                var paramUniform = effect.safeGetUniform("Param" + i);
                if (soundUniform != null) {
                    BlindVisionClientHandle.toViewSpace(slot.wx, slot.wy, slot.wz, camera, rel);
                    soundUniform.set(rel.x, rel.y, rel.z, slot.radius);
                }
                if (paramUniform != null) {
                    paramUniform.set(slot.fade, slot.maxRadius, slot.clarity, 0.0f);
                }
            }
            return true;
        });
    }

    public void resize(int w, int h) {
        if (post != null) {
            post.resize(w, h);
        }
    }

    public void renderPostProcess(float partialTicks) {
        if (post == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
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

    private void bindDepth(Minecraft mc, PostPass pass) {
        if (depthBound) {
            return;
        }
        var target = mc.getMainRenderTarget();
        pass.addAuxAsset("DepthSampler", () -> {
            int id = mc.getMainRenderTarget().getDepthTextureId();
            com.mojang.blaze3d.platform.GlStateManager._bindTexture(id);
            com.mojang.blaze3d.platform.GlStateManager._texParameter(
                    org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                    org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER,
                    org.lwjgl.opengl.GL11.GL_NEAREST);
            com.mojang.blaze3d.platform.GlStateManager._texParameter(
                    org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                    org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER,
                    org.lwjgl.opengl.GL11.GL_NEAREST);
            com.mojang.blaze3d.platform.GlStateManager._texParameter(
                    org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                    org.lwjgl.opengl.GL14.GL_TEXTURE_COMPARE_MODE,
                    org.lwjgl.opengl.GL11.GL_NONE);
            return id;
        }, target.width, target.height);
        depthBound = true;
    }

    private void updateSlots(BlindVisionClientHandle.Echo[] echoes, int count) {
        boolean[] taken = new boolean[count];
        for (Slot slot : slots) {
            int best = -1;
            double bestDist = 12.0 * 12.0;
            for (int i = 0; i < count; i++) {
                if (taken[i]) {
                    continue;
                }
                BlindVisionClientHandle.Echo echo = echoes[i];
                double dx = echo.x - slot.wx;
                double dy = echo.y - slot.wy;
                double dz = echo.z - slot.wz;
                double dist = dx * dx + dy * dy + dz * dz;
                if (slot.fade < 0.02f) {
                    dist = Double.MAX_VALUE;
                }
                if (dist < bestDist) {
                    bestDist = dist;
                    best = i;
                }
            }
            if (best >= 0 && (slot.fade < 0.02f || bestDist < 12.0 * 12.0)) {
                taken[best] = true;
                BlindVisionClientHandle.Echo echo = echoes[best];
                float follow = slot.fade < 0.02f ? 1.0f : POS_LERP;
                slot.wx += (echo.x - slot.wx) * follow;
                slot.wy += (echo.y - slot.wy) * follow;
                slot.wz += (echo.z - slot.wz) * follow;
                slot.radius += (echo.currentRadius() - slot.radius) * RADIUS_LERP;
                slot.maxRadius += (echo.maxRadius - slot.maxRadius) * RADIUS_LERP;
                slot.clarity += (echo.clarity() - slot.clarity) * POS_LERP;
                slot.fade += (echo.fade() - slot.fade) * FADE_IN;
            } else {
                slot.fade += (0.0f - slot.fade) * FADE_OUT;
                slot.radius += (0.0f - slot.radius) * RADIUS_LERP;
                if (slot.fade < 0.01f) {
                    slot.fade = 0.0f;
                    slot.radius = 0.0f;
                }
            }
        }
        for (int i = 0; i < count; i++) {
            if (taken[i]) {
                continue;
            }
            Slot empty = weakestSlot();
            BlindVisionClientHandle.Echo echo = echoes[i];
            empty.wx = echo.x;
            empty.wy = echo.y;
            empty.wz = echo.z;
            empty.radius = 0.0f;
            empty.maxRadius = echo.maxRadius;
            empty.clarity = echo.clarity();
            empty.fade = 0.02f;
        }
    }

    private Slot weakestSlot() {
        Slot worst = slots[0];
        for (Slot slot : slots) {
            if (slot.fade < worst.fade) {
                worst = slot;
            }
        }
        return worst;
    }

    private static final class Slot {
        double wx;
        double wy;
        double wz;
        float radius;
        float maxRadius;
        float fade;
        float clarity;
    }
}
