/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.leader.LeaderRoleData;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public abstract class LeaderHud {

    /** 本能透视：靠近非杀手方中立时显示其职业名的最大距离（格） */
    private static final double ROLE_REVEAL_RANGE = 6.0D;

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.LEADER_ID, (guiGraphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            Player player = client.player;
            if (player == null) {
                return;
            }

            int screenWidth = guiGraphics.guiWidth();
            int screenHeight = guiGraphics.guiHeight();
            Font font = client.font;
            int yOffset = screenHeight - 10 - font.lineHeight;
            int xOffset = screenWidth - 10;

            LeaderRoleData data = getData(player);
            if (data == null) {
                return;
            }

            // 技能状态
            if (data.skillUsed) {
                Component used = Component.translatable("hud.noellesroles.leader.skill_used")
                        .withStyle(ChatFormatting.GREEN);
                guiGraphics.drawString(font, used, xOffset - font.width(used), yOffset, Color.WHITE.getRGB());
            } else {
                Component ready = Component.translatable("hud.noellesroles.leader.skill_ready")
                        .withStyle(ChatFormatting.GOLD);
                guiGraphics.drawString(font, ready, xOffset - font.width(ready), yOffset, Color.WHITE.getRGB());

                // 200 秒倒计时（客户端本地计算，零同步）
                long remaining = remainingSeconds(player);
                Component countdown = Component.translatable("hud.noellesroles.leader.countdown", remaining)
                        .withStyle(remaining <= 10 ? ChatFormatting.RED : ChatFormatting.AQUA);
                guiGraphics.drawString(font, countdown, xOffset - font.width(countdown),
                        yOffset - font.lineHeight - 4, Color.WHITE.getRGB());
            }

            // 追随者列表
            int y = yOffset - font.lineHeight * 2 - 8;
            if (!data.followers.isEmpty()) {
                for (int i = 0; i < data.followers.size(); i++) {
                    String rolePath = i < data.followerRoleIds.size() ? data.followerRoleIds.get(i) : "";
                    String name = i < data.followerNames.size() ? data.followerNames.get(i) : "";
                    Component followerText = Component.translatable("hud.noellesroles.leader.follower",
                            displayRoleName(rolePath), name).withStyle(ChatFormatting.LIGHT_PURPLE);
                    guiGraphics.drawString(font, followerText, xOffset - font.width(followerText), y,
                            Color.WHITE.getRGB());
                    y -= font.lineHeight + 2;
                }
            }

            // 本能透视：靠近非杀手方中立的中立职业时，显示其职业名（类似杀手本能看杀手）
            Component nearby = nearbyNeutralRoles(client);
            if (nearby != null) {
                guiGraphics.drawString(font, nearby, xOffset - font.width(nearby), y, Color.YELLOW.getRGB());
            }
        });
    }

    /** 找出靠近的非杀手方中立职业（含玩家名 + 职业名） */
    @Nullable
    private static Component nearbyNeutralRoles(Minecraft client) {
        Player self = client.player;
        if (self == null || !(client.level != null)) {
            return null;
        }
        var game = SREGameWorldComponent.KEY.get(self.level());
        if (game == null || !game.isRunning()) {
            return null;
        }
        for (Player p : client.level.players()) {
            if (p == self || p.isSpectator() || p.isInvisible()) {
                continue;
            }
            if (self.distanceTo(p) > ROLE_REVEAL_RANGE) {
                continue;
            }
            SRERole role = game.getRole(p);
            if (role == null) {
                continue;
            }
            // 非杀手方中立：中立但非杀手方
            if (role.isNeutrals() && !role.isNeutralForKiller()) {
                return Component.translatable("hud.noellesroles.leader.nearby_role",
                        p.getDisplayName(),
                        Component.translatable("announcement.star.role." + role.identifier().getPath()))
                        .withStyle(ChatFormatting.YELLOW);
            }
        }
        return null;
    }

    @Nullable
    private static LeaderRoleData getData(Player player) {
        return RoleData.getNullable(LeaderRoleData.class, player);
    }

    /** 距「犹豫」死亡的剩余秒数（200 秒内未释放技能） */
    private static long remainingSeconds(Player player) {
        long start = SREGameTimeComponent.KEY.get(player.level()).startWorldTick;
        long elapsed = player.level().getGameTime() - start;
        long remaining = 200 - elapsed / 20;
        return Math.max(0, remaining);
    }

    /** 职业名：优先翻译键，支持自定义职业 */
    private static String displayRoleName(String path) {
        if (path.isEmpty()) {
            return "?";
        }
        String key = "announcement.star.role." + path;
        String translated = Component.translatable(key).getString();
        if (!translated.equals(key)) {
            return translated;
        }
        return path;
    }
}
