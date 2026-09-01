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

package io.wifi.mixins.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.utils.client.betterrender.TextBatchingBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SignRenderer.class)
public class SignRendererMixin {
    @Unique
    private static final int SRE$MAX_BLOCK_DISTANCE = 32 * 32;
    @Unique
    private static final int SRE$MAX_TEXT_DISTANCE = 16 * 16;
    @Unique
    private static final int SRE$ULTRA_MAX_TEXT_DISTANCE = 8 * 8;

    @Inject(method = "renderSignText", at = @At("HEAD"), cancellable = true)
    private void sre$blockRenderSignText(BlockPos blockPos, SignText signText, PoseStack poseStack,
            MultiBufferSource multiBufferSource, int i, int j, int k, boolean bl, CallbackInfo ci) {
        final var client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        if (SREConfig.isUltraPerfMode()) {
            if (blockPos.distToCenterSqr(client.player.position()) >= SRE$ULTRA_MAX_TEXT_DISTANCE) {
                ci.cancel();
                return;
            }
        } else {
            if (blockPos.distToCenterSqr(client.player.position()) >= SRE$MAX_TEXT_DISTANCE) {
                ci.cancel();
                return;
            }
        }
    }

    /**
     * The sign text is routed into the batching buffer by the {@code @Redirect}s
     * below; flushing right at the end of this method draws it while the world's
     * camera model-view matrix is still active, so the glyphs land on the sign.
     */
    @Inject(method = "renderSignText", at = @At("RETURN"))
    private void sre$flushSignText(CallbackInfo ci) {
        TextBatchingBuffer.SIGN_TEXT.flush();
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void sre$blockRenderSign(SignBlockEntity signBlockEntity, float f, PoseStack poseStack,
            MultiBufferSource multiBufferSource, int i, int j, CallbackInfo ci) {
        final var client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        if (signBlockEntity.getBlockPos().distToCenterSqr(client.player.position()) >= SRE$MAX_BLOCK_DISTANCE) {
            ci.cancel();
            return;
        }
    }

    /**
     * Routes sign text glyphs into the frame-scoped {@link TextBatchingBuffer}
     * instead of the per-glyph world BufferSource. The buffer is flushed at the
     * end of this same call, so the glyphs draw while the world's camera
     * model-view matrix is still active and depth-test against the rendered
     * terrain.
     */
    @Redirect(method = "renderSignText",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I"))
    private int sre$batchSignText(Font font, FormattedCharSequence seq, float x, float y, int color, boolean shadow,
            Matrix4f matrix, MultiBufferSource buffers, Font.DisplayMode mode, int light, int overlay) {
        return font.drawInBatch(seq, x, y, color, shadow, matrix, TextBatchingBuffer.SIGN_TEXT, mode, light, overlay);
    }

    @Redirect(method = "renderSignText",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;drawInBatch8xOutline(Lnet/minecraft/util/FormattedCharSequence;FFIILorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void sre$batchSignTextOutline(Font font, FormattedCharSequence seq, float x, float y, int color,
            int outlineColor, Matrix4f matrix, MultiBufferSource buffers, int light) {
        font.drawInBatch8xOutline(seq, x, y, color, outlineColor, matrix, TextBatchingBuffer.SIGN_TEXT, light);
    }
}
