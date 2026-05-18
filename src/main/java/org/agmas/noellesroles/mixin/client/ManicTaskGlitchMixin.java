package org.agmas.noellesroles.mixin.client;

import io.wifi.starrailexpress.client.gui.MoodRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Random;

@Mixin(MoodRenderer.TaskRenderer.class)
public class ManicTaskGlitchMixin {

    private static final Random RANDOM = new Random();

    @Inject(method = "tick", at = @At("RETURN"))
    public void onTick(CallbackInfoReturnable<Boolean> cir) {
        // 在客户端检查是否有狂躁症修饰符
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        try {
            var self = (MoodRenderer.TaskRenderer) (Object) this;
            WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(mc.player.level());
            if (modifiers != null && modifiers.isModifier(mc.player.getUUID(), TraitorAndModifiers.MANIC)) {
                // 将任务名称转换为乱码
                self.text = glitchText(self.text);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private Component glitchText(Component original) {
        // 生成随机乱码字符
        String chars = "!@#$%^&*()_+-=[]{}|;':\",./<>?`~ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder glitched = new StringBuilder();
        String text = original.getString();
        
        for (int i = 0; i < text.length(); i++) {
            // 50% 概率将字符替换为乱码
            if (RANDOM.nextFloat() < 0.5f) {
                glitched.append(chars.charAt(RANDOM.nextInt(chars.length())));
            } else {
                glitched.append(text.charAt(i));
            }
        }
        
        return Component.literal(glitched.toString()).withStyle(style -> 
            style.withColor(0xFF4444)); // 红色表示狂躁
    }
}
