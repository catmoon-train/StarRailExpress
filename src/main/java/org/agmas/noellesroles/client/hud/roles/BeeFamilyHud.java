package org.agmas.noellesroles.client.hud.roles;

import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.bouns.BounsRoles;
import org.agmas.noellesroles.role_data.neutral.BeeFamilyRoleData;
import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

public class BeeFamilyHud {
    public static void register() {

        RoleHudRenderCallback.EVENT.register(BounsRoles.BEE_WASP.identifier(), (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player.isSpectator())
                return;
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = 10; // 距离左边缘
            int y = screenHeight - 20; // 距离底部

            Font font = client.font;
            {
                BeeFamilyRoleData roleData = RoleData.getNullable(BeeFamilyRoleData.class, client.player);
                if (roleData == null) {
                    return;
                }

                Component cdText = Component
                        .translatable("hud.noellesroles.bee_family.channel",
                                roleData.beeChannel
                                        ? Component.translatable("hud.noellesroles.bee_family.channel.bee")
                                                .withStyle(ChatFormatting.YELLOW)
                                        : Component.translatable("hud.noellesroles.bee_family.channel.normal")
                                                .withStyle(ChatFormatting.AQUA))
                        .withStyle(ChatFormatting.GOLD);
                context.drawString(font, cdText, x, y - 10, 0xffffffff);
            }
        });
        RoleHudRenderCallback.EVENT.register(BounsRoles.BEE_WORKER.identifier(), (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player.isSpectator())
                return;
            // 获取探员组件
            var cca = SREAbilityPlayerComponent.KEY.get(client.player);

            // 渲染位置 - 右下角
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = 10; // 距离左边缘
            int y = screenHeight - 20; // 距离底部

            Font textRenderer = client.font;

            if (cca.duration > 0) {
                String cdSeconds = String.format("%.1fs", cca.duration / 20f);
                Component cdText = Component
                        .translatable("hud.noellesroles.bee_worker.tip",
                                Component.literal(cdSeconds).withStyle(ChatFormatting.RED))
                        .withStyle(ChatFormatting.YELLOW);
                context.drawString(textRenderer, cdText, x, y, 0xffffffff);
            }
            {
                BeeFamilyRoleData roleData = RoleData.getNullable(BeeFamilyRoleData.class, client.player);
                if (roleData == null) {
                    return;
                }

                Component cdText = Component
                        .translatable("hud.noellesroles.bee_family.channel",
                                roleData.beeChannel
                                        ? Component.translatable("hud.noellesroles.bee_family.channel.bee")
                                                .withStyle(ChatFormatting.YELLOW)
                                        : Component.translatable("hud.noellesroles.bee_family.channel.normal")
                                                .withStyle(ChatFormatting.AQUA))
                        .withStyle(ChatFormatting.GOLD);
                context.drawString(textRenderer, cdText, x, y - 10, 0xffffffff);
            }
        });

        RoleHudRenderCallback.EVENT.register(BounsRoles.BEE_QUEEN.identifier(), (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();

            // 获取探员组件
            var cca = SREAbilityPlayerComponent.KEY.get(client.player);

            // 渲染位置 - 右下角
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = 10; // 距离左边缘
            int y = screenHeight - 20; // 距离底部

            Font textRenderer = client.font;

            if (cca.hasCooldown()) {
                String cdSeconds = cca.getCooldownStr();
                Component cdText = Component.translatable("hud.noellesroles.bee_queen.spawn.cooldown", cdSeconds)
                        .withStyle(ChatFormatting.RED);
                context.drawString(textRenderer, cdText, x, y, 0xffffffff);

            } else {
                Component cdText = Component.translatable("hud.noellesroles.bee_queen.spawn.ready")
                        .withStyle(ChatFormatting.GREEN);
                context.drawString(textRenderer, cdText, x, y, 0xffffffff);
            }
            {
                SRERole reviveRole = BounsRoles.BEE_WORKER;
                if (cca.status > 0) {
                    reviveRole = BounsRoles.BEE_WASP;
                }
                Component cdText = Component
                        .translatable("hud.noellesroles.bee_queen.tip", RoleUtils.getRoleNameWithColor(reviveRole))
                        .withStyle(ChatFormatting.GOLD);
                context.drawString(textRenderer, cdText, x, y - 10, 0xffffffff);
            }

            {
                BeeFamilyRoleData roleData = RoleData.getNullable(BeeFamilyRoleData.class, client.player);
                if (roleData == null) {
                    return;
                }

                Component cdText = Component
                        .translatable("hud.noellesroles.bee_family.channel",
                                roleData.beeChannel
                                        ? Component.translatable("hud.noellesroles.bee_family.channel.bee")
                                                .withStyle(ChatFormatting.YELLOW)
                                        : Component.translatable("hud.noellesroles.bee_family.channel.normal")
                                                .withStyle(ChatFormatting.AQUA))
                        .withStyle(ChatFormatting.GOLD);
                context.drawString(textRenderer, cdText, x, y - 20, 0xffffffff);
            }
        });
    }
}
