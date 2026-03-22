package pro.fazeclan.river.stupid_express.mixin.client.modifier.refugee;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent;

@Mixin(Gui.class)
public abstract class RefugeeHudMixin {

    @Shadow
    public abstract Font getFont();

    @Inject(method = "render", at = @At("TAIL"))
    public void renderRefugeeHud(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null)
            return;

        // 检查玩家是否有难民modifier
        WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(client.level);
        if (!worldModifierComponent.isModifier(client.player.getUUID(), SEModifiers.REFUGEE)) {
            return;
        }

        // 检查玩家是否为旁观者模式
        if (!client.player.isSpectator()) {
            return;
        }

        // 获取难民组件
        RefugeeComponent refugeeComponent = RefugeeComponent.KEY.get(client.level);
        if (refugeeComponent == null) {
            return;
        }

        // 计算剩余时间
        long currentTime = client.level.getGameTime();
        long revivalTime = refugeeComponent.getRevivalTime(client.player.getUUID());

        if (revivalTime == -1) {
            return;
        }

        long ticksRemaining = revivalTime - currentTime;
        int secondsRemaining = (int) ((ticksRemaining + 19) / 20);

        Component text = Component.translatable("gui.stupid_express.refugee.revival", secondsRemaining);
        int color = 0x55ff55; // 绿色

        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int textWidth = getFont().width(text);

        // 右下角显示
        int x = screenWidth - textWidth - 10;
        int y = screenHeight - 20;

        context.drawString(getFont(), text, x, y, color);
    }
}
