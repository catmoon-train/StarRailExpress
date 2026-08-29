package org.agmas.noellesroles.mixin.client.general;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.agmas.noellesroles.init.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(MouseHandler.class)
public class UpsideDownEffectMouseMixin {

    @ModifyVariable(method = "onPress", // 目标方法名
            at = @At("HEAD"), // 在方法开头执行
            argsOnly = true, // 只修改传入的参数
            ordinal = 0 // 第一个 int 参数（即 button）
    )
    private int swapMouseButtons(int button) {
        Minecraft client = Minecraft.getInstance();

        // 仅在“游戏内（无UI界面）且玩家拥有自定义药水效果”时交换
        if (client.screen == null
                && client.getOverlay() == null
                && client.player != null
                && client.player.hasEffect(ModEffects.MOUSE_UPSIDE_DOWN)) {

            if (button == 0)
                return 1; // 左键 → 右键
            if (button == 1)
                return 0; // 右键 → 左键
        }
        return button; // 其他情况保持不变（如中键、UI内）
    }
}