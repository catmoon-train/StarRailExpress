package io.wifi.srepackcompat.voice;

import de.maxhenkel.voicechat.VoicechatClient;
import de.maxhenkel.voicechat.voice.client.ClientManager;
import de.maxhenkel.voicechat.voice.client.GroupChatManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;

/** 死亡语音组只显示发言者；按住 Alt 显示头像右侧的名字。 */
public final class DeadVoiceGroupHud {
    private DeadVoiceGroupHud() { }

    public static boolean render(GuiGraphics graphics) {
        var group = ClientManager.getPlayerStateManager().getGroup();
        // 群组 ID 由服务端随机生成，使用服务端同步的名称和隐藏属性识别。
        if (group == null || !group.isHidden() || !"Train Spectators".equals(group.getName())) {
            return false;
        }
        var client = ClientManager.getClient();
        Minecraft minecraft = Minecraft.getInstance();
        if (client == null || minecraft.getConnection() == null) return true;

        var talkCache = client.getTalkCache();
        var speakers = GroupChatManager.getGroupMembers(VoicechatClient.CLIENT_CONFIG.showOwnGroupIcon.get())
                .stream()
                .filter(member -> !member.isDisabled() && !member.isDisconnected()
                        && talkCache.isTalking(member.getUuid()))
                .toList();
        if (speakers.isEmpty()) return true;

        // 人数较多时整体缩放，保证所有发言者仍在屏幕内。
        float scale = Math.min(VoicechatClient.CLIENT_CONFIG.groupHudIconScale.get().floatValue() * 0.6F,
                (graphics.guiHeight() - 16F) / (speakers.size() * 20F));
        if (scale <= 0F) return true;
        boolean showNames = Screen.hasAltDown();
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(8F, (graphics.guiHeight() - speakers.size() * 20F * scale) / 2F, 0F);
            graphics.pose().scale(scale, scale, 1F);
            int y = 0;
            for (var member : speakers) {
                var info = minecraft.getConnection().getPlayerInfo(member.getUuid());
                var skin = info == null ? DefaultPlayerSkin.get(member.getUuid()) : info.getSkin();
                graphics.fill(-1, y - 1, 17, y + 17, 0xFF72C17B);
                PlayerFaceRenderer.draw(graphics, skin.texture(), 0, y, 16);
                if (showNames) {
                    // 玩家名是服务端同步的数据，不新增硬编码界面文案。
                    String name = member.getName();
                    int maxWidth = Math.max(0, (int) ((graphics.guiWidth() - 8F) / scale) - 24);
                    name = minecraft.font.plainSubstrByWidth(name, maxWidth);
                    int width = minecraft.font.width(name);
                    graphics.fill(20, y + 2, 24 + width, y + 14, 0xD81A1008);
                    graphics.drawString(minecraft.font, name, 22, y + 4, 0xFFFFF4DC, true);
                }
                y += 20;
            }
        } finally {
            graphics.pose().popPose();
        }
        return true;
    }
}
