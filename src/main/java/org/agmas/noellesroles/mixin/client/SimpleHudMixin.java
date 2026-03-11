package org.agmas.noellesroles.mixin.client;

import io.wifi.starrailexpress.api.Role;
import io.wifi.starrailexpress.cca.GameTimeComponent;
import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.game.GameFunctions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;

import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.roles.ghost.GhostPlayerComponent;
import org.agmas.noellesroles.roles.noise_maker.NoiseMakerPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class SimpleHudMixin {

    @Shadow
    public abstract Font getFont();

    @Inject(method = "render", at = @At("TAIL"))
    public void renderSimpleHud(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null)
            return;
        if(client.player.isSpectator()) return;

        GameWorldComponent gameWorld = GameWorldComponent.KEY.get(client.level);
        if (!GameFunctions.isPlayerAliveAndSurvival(client.player))
            return;
        Role role = gameWorld.getRole(client.player);
        if (role == null) {
            return;
        }
        Component text = null;
        int color = 0xffffff;
        if (gameWorld.isRole(client.player, ModRoles.GHOST)) {
            GhostPlayerComponent ghostComponent = GhostPlayerComponent.KEY.get(client.player);
            if (!ghostComponent.abilityUnlocked) {
                // 计算解锁剩余时间
                GameTimeComponent gameTime = GameTimeComponent.KEY.get(client.level);
                if (gameTime != null) {
                    long remainingTicks = gameTime.getTime();
                    if (remainingTicks > GhostPlayerComponent.UNLOCK_REMAINING_TICKS) {
                        // 剩余时间 > 3分钟，显示还需要多少时间到解锁
                        long ticksUntilUnlock = remainingTicks - GhostPlayerComponent.UNLOCK_REMAINING_TICKS;
                        int secondsUntilUnlock = ((int) ticksUntilUnlock + 19) / 20;
                        text = Component.translatable("gui.noellesroles.ghost.not_unlocked", secondsUntilUnlock);
                        color = 0xFFFF00; // 黄色
                    } else {
                        // 已到解锁时间但还没解锁，显示准备解锁
                        text = Component.translatable("gui.noellesroles.ghost.unlocking");
                        color = 0xFFAA00; // 橙色
                    }
                } else {
                    // 如果无法获取游戏时间，显示等待状态
                    text = Component.translatable("gui.noellesroles.ghost.waiting");
                    color = 0xFFAA00; // 橙色
                }
            } else if (ghostComponent.invisibilityTicks > 0) {
                int seconds = (ghostComponent.invisibilityTicks) / 20;
                text = Component.translatable("gui.noellesroles.ghost.during", seconds);
                color = 0x00fff7; // 青蓝色
            } else if (ghostComponent.cooldown > 0) {
                int seconds = (ghostComponent.cooldown) / 20;
                text = Component.translatable("gui.noellesroles.ghost.cooldown", seconds);
                color = 0xFF5555; // 红色
            } else {
                text = Component.translatable("gui.noellesroles.ghost.ready");
                color = 0x55FF55; // 绿色
            }
        } else if (gameWorld.isRole(client.player, ModRoles.NOISEMAKER)) {
            NoiseMakerPlayerComponent noisemakerComponent = NoiseMakerPlayerComponent.KEY.get(client.player);
            if (client.player.getActiveEffectsMap().containsKey(MobEffects.LUCK)) {
                MobEffectInstance eff = client.player.getActiveEffectsMap().getOrDefault(MobEffects.LUCK, null);
                int seconds = eff.getDuration() / 20;
                text = Component.translatable("gui.noellesroles.noisemaker.during", seconds);
                color = 0x00fff7; // 青蓝色
            } else if (noisemakerComponent.cooldown > 0) {
                int seconds = (noisemakerComponent.cooldown) / 20;
                text = Component.translatable("gui.noellesroles.noisemaker.cooldown", seconds);
                color = 0xFF5555; // 红色
            } else {
                text = Component.translatable("gui.noellesroles.noisemaker.ready");
                color = 0x55FF55; // 绿色
            }
        } else if (gameWorld.isRole(client.player, ModRoles.GLITCH_ROBOT)) {
            if (!client.player.getSlot(103).get().is(ModItems.NIGHT_VISION_GLASSES))
                return;
            text = Component.translatable("info.glitch_robot.take_off_glasses.tip",
                    Component.keybind("key.noellesroles.ability"));
        }

        if (text == null) {
            return;
            // text = Component.translatable("gui.noellesroles." +
            // role.getIdentifier().getPath() + ".below_tip");
        }
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int textWidth = getFont().width(text);

        // 右下角显示，留出一些边距
        int x = screenWidth - textWidth - 10;
        int y = screenHeight - 20;

        context.drawString(getFont(), text, x, y, color);

    }
}