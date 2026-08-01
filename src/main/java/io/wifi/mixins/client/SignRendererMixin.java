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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SignRenderer.class)
public class SignRendererMixin {
    @Unique
    private static final int SRE$MAX_BLOCK_DISTANCE = 32 * 32;
    private static final int SRE$MAX_TEXT_DISTANCE = 16 * 16;
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
}
