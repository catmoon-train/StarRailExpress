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

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.leader.LeaderRoleData;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public abstract class LeaderHud {

    /** 准星指向显示职业名的最大距离（格） */
    private static final double ROLE_REVEAL_RANGE = 4.0D;

    /** 准星指向判定阈值（度）：视线与目标方向的夹角小于该值视为「指向」 */
    private static final double AIM_ANGLE_DEGREES = 12.0D;

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.LEADER_ID, (guiGraphics, deltaTracker) -> {
            try {
                render(guiGraphics, deltaTracker);
            } catch (Exception e) {
                SRE.LOGGER.error("[LEADER HUD] Error while rendering leader hud", e);
            }
        });
    }

    private static void render(io.wifi.utils.client.betterrender.FakeGuiGraphics guiGraphics,
                               net.minecraft.client.DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        if (player == null) {
            return;
        }

        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();
        Font font = client.font;

        LeaderRoleData data = getData(player);
        if (data == null) {
            return;
        }

        // ================= 右下角往上：技能状态 / 倒计时 / 追随者列表（三层分离，互不重叠） =================
        int xOffset = screenWidth - 10; // 右对齐
        int y = screenHeight - 10 - font.lineHeight; // 最底部：技能状态

        // 1) 技能状态（最底一层）
        if (data.skillUsed) {
            Component used = Component.translatable("hud.noellesroles.leader.skill_used")
                    .withStyle(ChatFormatting.GREEN);
            guiGraphics.drawString(font, used, xOffset - font.width(used), y, Color.WHITE.getRGB());
        } else {
            Component ready = Component.translatable("hud.noellesroles.leader.skill_ready")
                    .withStyle(ChatFormatting.GOLD);
            guiGraphics.drawString(font, ready, xOffset - font.width(ready), y, Color.WHITE.getRGB());
        }
        y -= font.lineHeight + 6;

        // 2) 倒计时（独立一层，固定位置，与技能状态/追随者列表均隔开）
        if (!data.skillUsed) {
            // 200 秒倒计时（客户端本地计算，零同步；安全时间内不下降）
            long remaining = remainingSeconds(player);
            Component countdown = Component.translatable("hud.noellesroles.leader.countdown", remaining)
                    .withStyle(remaining <= 10 ? ChatFormatting.RED : ChatFormatting.AQUA);
            guiGraphics.drawString(font, countdown, xOffset - font.width(countdown), y, Color.WHITE.getRGB());
        }
        y -= font.lineHeight + 6;

        // 3) 追随者列表（继续往上排）
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

        // ================= 屏幕中央偏下：准星指向的非杀手方中立职业名（不带玩家名） =================
        Component aimed = aimedNeutralRole(client);
        if (aimed != null) {
            int cx = screenWidth / 2 - font.width(aimed) / 2;
            int cy = (int) (screenHeight * 0.62);
            guiGraphics.drawString(font, aimed, cx, cy, Color.YELLOW.getRGB());
        }
    }

    /**
     * 找出准星指向（距离范围内 + 视线夹角小于阈值）的非杀手方中立职业，
     * 返回其职业名（支持自定义职业的自定义名称），不含玩家名。
     */
    @Nullable
    private static Component aimedNeutralRole(Minecraft client) {
        Player self = client.player;
        if (self == null || client.level == null) {
            return null;
        }
        // 拥有追随者后，靠近/指向非杀手方中立显示职业名的能力失效
        LeaderRoleData selfData = getData(self);
        if (selfData != null && !selfData.followers.isEmpty()) {
            return null;
        }
        var game = SREGameWorldComponent.KEY.get(self.level());
        if (game == null || !game.isRunning()) {
            return null;
        }
        Vec3 look = self.getLookAngle();
        Vec3 eye = self.getEyePosition(1.0F);
        Player best = null;
        double bestDot = Double.NEGATIVE_INFINITY;
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
            if (!(role.isNeutrals() && !role.isNeutralForKiller())) {
                continue;
            }
            Vec3 toTarget = p.getBoundingBox().getCenter().subtract(eye).normalize();
            double dot = look.dot(toTarget);
            if (dot > bestDot) {
                bestDot = dot;
                best = p;
            }
        }
        if (best == null || bestDot < Math.cos(Math.toRadians(AIM_ANGLE_DEGREES))) {
            return null;
        }
        SRERole role = game.getRole(best);
        if (role == null) {
            return null;
        }
        try {
            return role.getDisplayName();
        } catch (Exception e) {
            return Component.translatable("announcement.star.role." + role.identifier().getPath());
        }
    }

    @Nullable
    private static LeaderRoleData getData(Player player) {
        return RoleData.getNullable(LeaderRoleData.class, player);
    }

    /** 距「犹豫」死亡的剩余秒数（200 秒内未释放技能；安全时间不算作犹豫时间） */
    private static long remainingSeconds(Player player) {
        long start = SREGameTimeComponent.KEY.get(player.level()).startWorldTick;
        long elapsed = player.level().getGameTime() - start;
        LeaderRoleData data = getData(player);
        long safeTicks = data != null ? data.safeTimeTicks : 0;
        long effective = Math.max(0, elapsed - safeTicks);
        long remaining = 200 - effective / 20;
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
