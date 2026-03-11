package org.agmas.noellesroles.mixin.client.roles.thief;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Gui.class)
public abstract class ThiefHudMixin {
    @Shadow public abstract Font getFont();

//    @Inject(method = "render", at = @At("TAIL"))
//    public void thiefHud(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
//        if (Minecraft.getInstance().player == null) return;
//
//        GameWorldComponent gameWorldComponent = GameWorldComponent.KEY.get(Minecraft.getInstance().player.level());
//        AbilityPlayerComponent abilityPlayerComponent = AbilityPlayerComponent.KEY.get(Minecraft.getInstance().player);
//        ThiefPlayerComponent thiefComponent = ThiefPlayerComponent.KEY.get(Minecraft.getInstance().player);
//
//        if (gameWorldComponent.isRole(Minecraft.getInstance().player, ModRoles.THIEF)) {
//            int drawY = context.guiHeight();
//
//            Component hint = Component.translatable("tip.thief.steal");
//            drawY -= getFont().wordWrapHeight(hint, 999999);
//            context.drawString(getFont(), hint, context.guiWidth() - getFont().width(hint), drawY, 0xFFFFFF);
//
//            if (thiefComponent.hasBlackoutEffect) {
//                Component blackout = Component.translatable("tip.thief.blackout_active");
//                drawY -= getFont().wordWrapHeight(blackout, 999999);
//                context.drawString(getFont(), blackout, context.guiWidth() - getFont().width(blackout), drawY, 0x00FF00);
//            }
//
//            if (abilityPlayerComponent.cooldown > 0) {
//                Component line = Component.translatable("tip.noellesroles.cooldown", abilityPlayerComponent.cooldown / 20);
//                drawY -= getFont().wordWrapHeight(line, 999999);
//                context.drawString(getFont(), line, context.guiWidth() - getFont().width(line), drawY, ModRoles.THIEF.color());
//            }
//        }
//    }
}