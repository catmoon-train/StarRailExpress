package io.wifi.starrailexpress.client.gui;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Animated HUD toast for newly unlocked roles.
 */
public class RoleUnlockHudRenderer {

    private static final long DURATION_MS = 6800L;
    private static final Deque<UnlockToast> QUEUE = new ArrayDeque<>();

    private static UnlockToast current;
    private static long startMs;

    private RoleUnlockHudRenderer() {
    }

    public static void enqueue(int globalGamesPlayed, List<String> unlockedRoleIds) {
        if (unlockedRoleIds == null || unlockedRoleIds.isEmpty()) {
            return;
        }
        QUEUE.add(new UnlockToast(globalGamesPlayed, new ArrayList<>(unlockedRoleIds)));
    }

    public static void render(GuiGraphics g) {
        if (current == null) {
            current = QUEUE.poll();
            if (current == null) {
                return;
            }
            startMs = System.currentTimeMillis();
        }

        long elapsed = System.currentTimeMillis() - startMs;
        if (elapsed >= DURATION_MS) {
            current = null;
            return;
        }

        float t = Mth.clamp((float) elapsed / DURATION_MS, 0.0f, 1.0f);
        float alpha = computeAlpha(t);
        float inOut = easeOutCubic(Math.min(1.0f, t / 0.25f));
        float slideY = (1.0f - inOut) * 22.0f;

        int screenW = g.guiWidth();
        int centerX = screenW / 2;

        int w = 240;
        int h = 58;
        int x = centerX - w / 2;
        int y = (int) (24 - slideY);

        int mainColor = getMainColor(current.roleIds);
        int panel = withAlpha(0xFF101927, alpha * 0.94f);
        int border = withAlpha(blend(mainColor, 0xFF091018, 0.30f), alpha);
        int accent = withAlpha(mainColor, alpha);

        // Glow/backplate
        g.fill(x - 4, y - 4, x + w + 4, y + h + 4, withAlpha(blend(mainColor, 0xFF000000, 0.75f), alpha * 0.25f));
        g.fill(x, y, x + w, y + h, panel);
        drawBorder(g, x, y, w, h, border);

        // Accent line
        g.fill(x, y, x + w, y + 3, accent);

        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) {
            return;
        }

        String title = "角色解锁";
        String sub = Language.getInstance().getOrDefault("hud.role_unlock.global_matches", "全局对局数：") + current.globalGamesPlayed;
        int textX = x + 10;

        g.drawString(mc.font, title, textX, y + 10, withAlpha(0xFFE9F2FF, alpha), false);
        g.drawString(mc.font, sub, textX, y + 22, withAlpha(0xFF9EB1C7, alpha), false);

        int chipY = y + 36;
        int chipX = textX;
        int maxShow = Math.min(2, current.roleIds.size());
        for (int i = 0; i < maxShow; i++) {
            ResourceLocation id = ResourceLocation.tryParse(current.roleIds.get(i));
            String name = roleName(id);
            int cw = Math.min(110, mc.font.width(name) + 14);
            int cc = withAlpha(getColor(id), alpha);

            g.fill(chipX, chipY, chipX + cw, chipY + 14, withAlpha(blend(cc, 0xFF0B111A, 0.72f), alpha));
            drawBorder(g, chipX, chipY, cw, 14, withAlpha(blend(cc, 0xFF000000, 0.35f), alpha));
            g.drawString(mc.font, trimToWidth(mc, name, cw - 8), chipX + 4, chipY + 3,
                    withAlpha(0xFFEAF5FF, alpha), false);
            chipX += cw + 6;
        }

        if (current.roleIds.size() > maxShow) {
            String more = "+" + (current.roleIds.size() - maxShow) + " 更多";
            g.drawString(mc.font, more, chipX + 2, chipY + 3, withAlpha(0xFF8CA0B8, alpha), false);
        }
    }

    private static String roleName(ResourceLocation id) {
        if (id == null) {
            return "unknown";
        }

        return  I18n.get("announcement.star.role."+id.getPath());
    }

    private static int getMainColor(List<String> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return 0xFF46A4FF;
        }
        ResourceLocation id = ResourceLocation.tryParse(roleIds.get(0));
        return getColor(id);
    }

    private static int getColor(ResourceLocation id) {
        if (id == null) {
            return 0xFF46A4FF;
        }
        SRERole role = TMMRoles.ROLES.get(id);
        return role != null ? (0xFF000000 | role.getColor()) : 0xFF46A4FF;
    }

    private static int withAlpha(int argb, float alpha) {
        int a = Mth.clamp((int) (alpha * 255.0f), 0, 255);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static int blend(int c1, int c2, float t) {
        int r1 = (c1 >> 16) & 0xFF;
        int g1 = (c1 >> 8) & 0xFF;
        int b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF;
        int g2 = (c2 >> 8) & 0xFF;
        int b2 = c2 & 0xFF;
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static float computeAlpha(float t) {
        if (t < 0.12f) {
            return easeOutCubic(t / 0.12f);
        }
        if (t > 0.82f) {
            return 1.0f - easeInCubic((t - 0.82f) / 0.18f);
        }
        return 1.0f;
    }

    private static float easeOutCubic(float t) {
        float v = 1.0f - t;
        return 1.0f - v * v * v;
    }

    private static float easeInCubic(float t) {
        return t * t * t;
    }

    private static String trimToWidth(Minecraft mc, String text, int maxWidth) {
        if (mc.font.width(text) <= maxWidth) {
            return text;
        }
        String dots = "...";
        int dotsW = mc.font.width(dots);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (mc.font.width(sb.toString() + ch) + dotsW > maxWidth) {
                break;
            }
            sb.append(ch);
        }
        return sb + dots;
    }

    private record UnlockToast(int globalGamesPlayed, List<String> roleIds) {
    }
}
