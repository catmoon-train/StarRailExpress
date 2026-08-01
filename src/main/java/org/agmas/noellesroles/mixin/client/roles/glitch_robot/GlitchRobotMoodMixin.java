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

package org.agmas.noellesroles.mixin.client.roles.glitch_robot;

import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.gui.HudMoodRenderer;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

@Mixin(HudMoodRenderer.class)
public class GlitchRobotMoodMixin {

    @Shadow
    public static float moodOffset;

    @Shadow
    public static float moodTextWidth;

    @Shadow
    public static float moodRender;

    @Shadow
    public static float moodAlpha;
    @Shadow
    public static Random random;
    // 使用平民的心情纹理（直接引用 HudMoodRenderer 的常量）
    @Unique
    private static final ResourceLocation CIVILIAN_MOOD = HudMoodRenderer.MOOD_HAPPY;

    @Inject(method = "renderKiller", at = @At("HEAD"), cancellable = true)
    private static void glitchRobotMood(Font textRenderer, FakeGuiGraphics context, int color, SRERole role, CallbackInfo ci) {
        SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
                .get(Minecraft.getInstance().player.level());
        if (gameWorldComponent.isRole(Minecraft.getInstance().player, ModRoles.GLITCH_ROBOT)) {
            context.pose().pushPose();
            context.pose().translate(0.0F, 3.0F * moodOffset, 0.0F);
            context.blitSprite(CIVILIAN_MOOD, 5, 6, 14, 17);
            context.pose().popPose();
            context.pose().pushPose();
            context.pose().translate(0.0F, 10.0F * moodOffset, 0.0F);
            PoseStack var10000 = context.pose();
            var10000.translate(26.0F, (float) (8 + 9), 0.0F);
            context.pose().scale((moodTextWidth - 8.0F) * moodRender, 1.0F, 1.0F);
            context.fill(0, 0, 1, 1, ModRoles.GLITCH_ROBOT.color() | (int) (moodAlpha * 255.0F) << 24);
            context.pose().popPose();
            ci.cancel();
        }
    }
}