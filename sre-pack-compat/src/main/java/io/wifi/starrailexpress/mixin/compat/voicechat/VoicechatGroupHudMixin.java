package io.wifi.starrailexpress.mixin.compat.voicechat;

import io.wifi.srepackcompat.voice.DeadVoiceGroupHud;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 只替换死亡语音组的头像 HUD，其他群组仍由上游绘制。 */
@Pseudo
@Mixin(targets = "de.maxhenkel.voicechat.voice.client.GroupChatManager", remap = false)
public abstract class VoicechatGroupHudMixin {
    @Inject(method = "renderIcons", at = @At("HEAD"), cancellable = true)
    private static void sreCompat$renderSpeakers(GuiGraphics graphics, CallbackInfo ci) {
        if (DeadVoiceGroupHud.render(graphics)) ci.cancel();
    }
}
