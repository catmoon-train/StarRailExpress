package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.roles.fool.FoolPlayerComponent;

/**
 * 愚者 HUD 渲染器
 *
 * 显示信息：
 * - 处刑者手枪弹药数
 * - 塔罗会成员数
 * - 异端目标（如果有）
 * - 会议状态
 * - 冷却时间
 * - 庇护效果
 */
public abstract class FoolHud {

    public static void register() {
        CommonHudRenderCallback.EVENT.register((context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            LocalPlayer player = client.player;
            if (player == null || client.level == null) return;
            if (SREClient.isPlayerSpectator()) return;
            if (SREClient.gameComponent != null && SREClient.gameComponent.isRole(player, ModRoles.THE_FOOL)) return;

            FoolPlayerComponent comp = FoolPlayerComponent.KEY.get(player);
            if (!player.hasEffect(ModEffects.TAROT_ASSEMBLY) || !comp.voteInProgress || comp.voteEndTick <= 0) return;

            long remainingTicks = Math.max(0, comp.voteEndTick - client.level.getGameTime());
            Component meetingText = Component.translatable("hud.noellesroles.fool.member_meeting_active",
                    remainingTicks / 20);
            Component hintText = Component.translatable("hud.noellesroles.fool.member_vote_hint");

            Font renderer = client.font;
            int x = 10;
            int y = context.guiHeight() - 62;
            context.drawString(renderer, meetingText, x, y, 0xFFD700);
            context.drawString(renderer, hintText, x, y + 12, 0xFFF2A8);
        });

        RoleHudRenderCallback.EVENT.register(ModRoles.THE_FOOL_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            final Font renderer = client.font;
            final LocalPlayer player = client.player;
            if (player == null) return;

            FoolPlayerComponent comp = FoolPlayerComponent.KEY.get(player);

            context.pose().pushPose();

            int screenWidth = context.guiWidth();
            int screenHeight = context.guiHeight();
            int yOffset = screenHeight - 80;
            int xOffset = screenWidth - 200;
            int lineHeight = 12;

            // 弹药数
            Component bulletText = Component.translatable("hud.noellesroles.fool.bullets",
                    comp.executionerBullets);
            context.drawString(renderer, bulletText, xOffset, yOffset, comp.executionerBullets > 0 ? 0x55FF55 : 0xFF5555);
            yOffset += lineHeight;

            // 塔罗会成员数
            Component memberText = Component.translatable("hud.noellesroles.fool.tarot_members",
                    comp.tarotMembers.size());
            context.drawString(renderer, memberText, xOffset, yOffset, 0xFFAA00);
            yOffset += lineHeight;

            // 异端
            if (comp.hereticTarget != null) {
                var info = client.player.connection.getPlayerInfo(comp.hereticTarget);
                String name = info != null ? info.getProfile().getName() : "???";
                long remainingTicks = Math.max(0, comp.hereticEndTick - (client.level != null ? client.level.getGameTime() : 0));
                Component hereticText = Component.translatable("hud.noellesroles.fool.heretic",
                        name, remainingTicks / 20);
                context.drawString(renderer, hereticText, xOffset, yOffset, 0xFF0000);
                yOffset += lineHeight;
            }

            // 会议状态
            if (comp.inMeeting) {
                long gameTime = client.level != null ? client.level.getGameTime() : 0;
                long remainingTicks = Math.max(0, comp.meetingEndTick - gameTime);
                Component meetingText = Component.translatable("hud.noellesroles.fool.meeting_active",
                        remainingTicks / 20);
                context.drawString(renderer, meetingText, xOffset, yOffset, 0xFFD700);
                yOffset += lineHeight;

                if (comp.voteInProgress && comp.voteEndTick > 0 && comp.voteEndTick != comp.meetingEndTick) {
                    long voteRemainingTicks = Math.max(0, comp.voteEndTick - gameTime);
                    Component voteText = Component.translatable("hud.noellesroles.fool.vote_active",
                            voteRemainingTicks / 20);
                    context.drawString(renderer, voteText, xOffset, yOffset, 0xFFAA55);
                    yOffset += lineHeight;
                }
            }

            // 冷却
            if (comp.tarotCooldownEndTick > 0) {
                long gameTime = client.level != null ? client.level.getGameTime() : 0;
                long remaining = Math.max(0, (comp.tarotCooldownEndTick - gameTime) / 20);
                if (remaining > 0) {
                    Component cdText = Component.translatable("hud.noellesroles.fool.cooldown", remaining);
                    context.drawString(renderer, cdText, xOffset, yOffset, 0xAAAAAA);
                    yOffset += lineHeight;
                }
            }

            // 庇护
            if (comp.protectionSource != null) {
                var info = client.player.connection.getPlayerInfo(comp.protectionSource);
                String name = info != null ? info.getProfile().getName() : "???";
                Component protText = Component.translatable("hud.noellesroles.fool.protection", name);
                context.drawString(renderer, protText, xOffset, yOffset, 0x55FF55);
            }

            context.pose().popPose();
        });
    }
}
