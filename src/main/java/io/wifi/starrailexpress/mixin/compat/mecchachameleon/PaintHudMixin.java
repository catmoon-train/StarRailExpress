package io.wifi.starrailexpress.mixin.compat.mecchachameleon;

import com.mecchachameleon.client.*;
import com.mecchachameleon.paint.PaintAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(PaintHud.class)
public class PaintHudMixin {
    @Inject(method = "renderHud", at = @At("HEAD"), cancellable = true)
    private static void renderHud(GuiGraphics graphics, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.options.hideGui) {
            boolean locked = (Boolean)mc.player.getAttachedOrCreate(PaintAttachments.LOCKED);
            int cx = mc.getWindow().getGuiScaledWidth() / 2;
            if (locked && FreeCam.active() && !WorldBrush.paintMode()) {
                String mode = "● 自由视角";
                graphics.drawString(mc.font, mode, cx - mc.font.width(mode) / 2, 4, -11141291);
                ControlHints.rowCentered(graphics, mc.font, cx, 13, List.of(new String[]{LockControls.paintKey.getTranslatedKeyMessage().getString(), "绘画"}, new String[]{"Esc", "离开"}));
            } else if (locked && !FreeCam.active()) {
                String mode = "● 隐藏中";
                graphics.drawString(mc.font, mode, cx - mc.font.width(mode) / 2, 4, -11141291);
                ControlHints.rowCentered(graphics, mc.font, cx, 13, List.of(new String[]{"Space", "移动"}, new String[]{LockControls.paintKey.getTranslatedKeyMessage().getString(), "绘画"}, new String[]{"R", "姿势"}, new String[]{"Esc", "菜单"}));
            }

        }
        ci.cancel();
    }
}
