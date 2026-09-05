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

package io.wifi.starrailexpress.client.hud;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.network.SkillCastAnnouncePayload;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 屏幕左侧技能释放通告：无背景，仅文字投影；滑入叠放后淡出。
 */
public final class SkillCastAnnounceHud {
    private static final int MAX_TOASTS = 8;
    private static final int LIFE_MS = 3400;
    private static final int IN_MS = 280;
    private static final int OUT_MS = 420;
    private static final int SLIDE_PX = 22;
    private static final int ROW_H = 14;
    private static final int GAP = 3;
    private static final int MARGIN_X = 8;
    private static final int TEXT = 0xFFFFF4DC;

    private static final List<Toast> TOASTS = new ArrayList<>();

    private SkillCastAnnounceHud() {
    }

    public static void register() {
        CommonHudRenderCallback.EVENT.register((graphics, deltaTracker) -> render(graphics));
    }

    public static void push(SkillCastAnnouncePayload payload) {
        if (payload == null || !SREConfig.instance().skillCastAnnounceHud) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        ResourceLocation roleId = payload.roleId();
        Component roleName = RoleUtils.getRoleNameWithColor(roleId);
        if (roleName == null) {
            roleName = Component.literal(roleId == null ? "?" : roleId.getPath());
        }
        MutableComponent line = Component.translatable("hud.sre.skill_cast",
                Component.literal(payload.playerName() == null ? "" : payload.playerName()),
                roleName);
        long now = Util.getMillis();
        TOASTS.add(new Toast(line, now));
        while (TOASTS.size() > MAX_TOASTS) {
            TOASTS.remove(0);
        }
    }

    private static void render(FakeGuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui || TOASTS.isEmpty()) {
            return;
        }
        if (!SREConfig.instance().skillCastAnnounceHud) {
            TOASTS.clear();
            return;
        }
        long now = Util.getMillis();
        Iterator<Toast> iterator = TOASTS.iterator();
        while (iterator.hasNext()) {
            Toast toast = iterator.next();
            if (now - toast.createdMs >= LIFE_MS) {
                iterator.remove();
            }
        }
        if (TOASTS.isEmpty()) {
            return;
        }

        int baseY = Mth.clamp((int) (graphics.guiHeight() * 0.26F), 52, 96);
        int index = 0;
        for (Toast toast : TOASTS) {
            long age = now - toast.createdMs;
            float alpha = alphaForAge(age);
            if (alpha <= 0.02F) {
                index++;
                continue;
            }
            float slide = slideForAge(age);
            int targetY = baseY + index * (ROW_H + GAP);
            if (!toast.yInit) {
                toast.animY = targetY + 8;
                toast.yInit = true;
            } else {
                toast.animY += (targetY - toast.animY) * 0.22F;
            }
            int y = Math.round(toast.animY);
            int x = MARGIN_X + Math.round(-SLIDE_PX * slide);
            int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
            graphics.drawString(client.font, toast.line, x, y, withAlpha(TEXT, a), true);
            index++;
        }
    }

    private static float alphaForAge(long age) {
        if (age < IN_MS) {
            return easeOutCubic(age / (float) IN_MS);
        }
        long fadeStart = LIFE_MS - OUT_MS;
        if (age > fadeStart) {
            return 1.0F - easeOutCubic((age - fadeStart) / (float) OUT_MS);
        }
        return 1.0F;
    }

    private static float slideForAge(long age) {
        if (age < IN_MS) {
            return 1.0F - easeOutCubic(age / (float) IN_MS);
        }
        long fadeStart = LIFE_MS - OUT_MS;
        if (age > fadeStart) {
            return easeOutCubic((age - fadeStart) / (float) OUT_MS);
        }
        return 0.0F;
    }

    private static float easeOutCubic(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        float f = 1.0F - t;
        return 1.0F - f * f * f;
    }

    private static int withAlpha(int color, int a) {
        return (Mth.clamp(a, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    private static final class Toast {
        final Component line;
        final long createdMs;
        float animY;
        boolean yInit;

        Toast(Component line, long createdMs) {
            this.line = line;
            this.createdMs = createdMs;
        }
    }
}
