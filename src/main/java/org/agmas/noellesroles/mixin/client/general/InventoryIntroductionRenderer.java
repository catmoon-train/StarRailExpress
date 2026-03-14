// Source code is decompiled from a .class file using FernFlower decompiler (from Intellij IDEA).
package org.agmas.noellesroles.mixin.client.general;

import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.utils.RoleUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ LimitedInventoryScreen.class })
public class InventoryIntroductionRenderer {
   public InventoryIntroductionRenderer() {
   }

   private float getScare(int height) {
      if (height <= 300) {
         return 0.4f;
      }
      if (height <= 400) {
         return 0.6f;
      }
      if (height >= 600) {
         return 1f;
      }
      return 0.8f;
   }

   @Inject(method = { "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V" }, at = { @At("TAIL") })
   public void render(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         SREGameWorldComponent gameWorldComponent = SREClient.gameComponent;
         if (gameWorldComponent != null) {
            // Role
            float scale = getScare(context.guiHeight());
            SRERole role = gameWorldComponent.getRole(player);
            Font font = Minecraft.getInstance().font;
            final int MAX_WIDTH = (int)(context.guiWidth() / scale / 4);

            if (role != null) {
               String roleName = role.getIdentifier().getPath();
               if (roleName != null) {
                  int x = 10;
                  int y = 10;
                  Component roleNameComponent = Component.translatable("announcement.star.role." + roleName)
                        .withStyle(ChatFormatting.BOLD);
                  Component roleInfoComponent = Component.translatable("info.screen.roleid." + roleName);
                  PoseStack poseStack = context.pose();
                  poseStack.pushPose();
                  poseStack.scale(scale, scale, 1.0F);
                  float scaledX = (float) x / scale;
                  float scaledY = (float) y / scale;
                  this.renderScaledTextWithShadow(context, font, roleNameComponent, scaledX, scaledY, scale, 16777215,
                        4210752);
                  Objects.requireNonNull(font);
                  int roleNameHeight = (int) (9.0F * scale);
                  int currentY = y + roleNameHeight + 2;
                  List<FormattedCharSequence> infoLines = font.split(roleInfoComponent, MAX_WIDTH);
                  int i = 0;
                  for (FormattedCharSequence line : infoLines) {
                     i++;
                     if (currentY >= (float) context.guiHeight() / 3) {
                        float lineY = (float) currentY / scale;
                        var moreInfo = Component.translatable("info.screen.role.see_more")
                              .withStyle(ChatFormatting.GRAY);
                        context.drawString(font, moreInfo, (int) scaledX, (int) lineY, 11184810);
                        break;
                     }
                     float lineY = (float) currentY / scale;
                     context.drawString(font, line, (int) scaledX, (int) lineY, 11184810);
                     currentY += (int) (9.0F * scale) + 2;
                  }

                  poseStack.popPose();
                  int infoLineCount = i;
                  Objects.requireNonNull(font);
                  int var10000 = (int) (9.0F * scale);
                  int totalHeight = var10000 + infoLineCount * (int) (9.0F * scale) + infoLineCount * 2 + 2;
                  int scaledNameWidth = (int) ((float) font.width(roleNameComponent) * scale);
                  int maxInfoWidth = infoLines.stream().mapToInt((component) -> {
                     return (int) ((float) font.width(component) * scale);
                  }).max().orElse(0);
                  int maxWidth = Math.max(scaledNameWidth, maxInfoWidth);
                  this.drawScaledBackground(context, x, y, maxWidth, totalHeight);
               }
            }
            // Modifier
            WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(player.level());
            if (worldModifierComponent != null) {
               var modifiers = worldModifierComponent.getDisplayableModifiers(player);
               if (modifiers != null && modifiers.size() > 0) {
                  int x = 10;
                  int y = (int) ((float) context.guiHeight()) - 10;
                  for (var modifier : modifiers) {
                     Component modifierNameComponent = modifier.getName()
                           .withStyle(ChatFormatting.BOLD);
                     Component modifierInfoComponent = RoleUtils.getModifierDescription(modifier);
                     PoseStack poseStack = context.pose();
                     poseStack.pushPose();
                     poseStack.scale(scale, scale, 1.0F);
                     float scaledX = (float) x / scale;
                     float scaledY = (float) y / scale;

                     Objects.requireNonNull(font);
                     int modifierNameHeight = (int) (9.0F * scale);
                     int currentY = y + modifierNameHeight + 2;
                     List<FormattedCharSequence> infoLines = font.split(modifierInfoComponent, MAX_WIDTH);

                     int infoLineCount = infoLines.size();
                     int var10000 = (int) (9.0F * scale);
                     int totalHeight = var10000 + infoLineCount * (int) (9.0F * scale) + infoLineCount * 2 + 2;
                     int scaledNameWidth = (int) ((float) font.width(modifierNameComponent) * scale);
                     int maxInfoWidth = infoLines.stream().mapToInt((component) -> {
                        return (int) ((float) font.width(component) * scale);
                     }).max().orElse(0);
                     int maxWidth = Math.max(scaledNameWidth, maxInfoWidth);
                     this.renderScaledTextWithShadow(context, font, modifierNameComponent, scaledX,
                           scaledY - totalHeight / scale, scale,
                           16777215,
                           4210752);
                     for (Iterator<FormattedCharSequence> var22 = infoLines.iterator(); var22
                           .hasNext(); currentY += (int) (9.0F * scale) + 2) {
                        FormattedCharSequence line = var22.next();
                        float lineY = (float) currentY / scale;
                        context.drawString(font, line, (int) (scaledX), (int) (lineY - totalHeight / scale), 11184810);
                     }
                     poseStack.popPose();

                     this.drawScaledBackground(context, x, y - totalHeight, maxWidth, totalHeight);
                     y -= (totalHeight + 10);
                  }
               }
            }
         }
      }

   }

   private void renderScaledTextWithShadow(GuiGraphics context, Font font, Component text, float x, float y,
         float scale, int textColor, int shadowColor) {
      PoseStack poseStack = context.pose();
      poseStack.pushPose();
      context.drawString(font, text, (int) (x + 1.0F / scale), (int) (y + 1.0F / scale), shadowColor);
      context.drawString(font, text, (int) x, (int) y, textColor);
      poseStack.popPose();
   }

   private void drawScaledBackground(GuiGraphics context, int x, int y, int width, int height) {
      int padding = 3;
      int borderThickness = 1;
      int bgX = x - padding;
      int bgY = y - padding;
      int bgWidth = width + padding * 2;
      int bgHeight = height + padding * 2;
      int backgroundColor = Integer.MIN_VALUE;
      context.fill(bgX, bgY, bgX + bgWidth, bgY + bgHeight, backgroundColor);
      int borderColor = -10066330;
      context.fill(bgX, bgY, bgX + bgWidth, bgY + borderThickness, borderColor);
      context.fill(bgX, bgY + bgHeight - borderThickness, bgX + bgWidth, bgY + bgHeight, borderColor);
      context.fill(bgX, bgY, bgX + borderThickness, bgY + bgHeight, borderColor);
      context.fill(bgX + bgWidth - borderThickness, bgY, bgX + bgWidth, bgY + bgHeight, borderColor);
   }
}
