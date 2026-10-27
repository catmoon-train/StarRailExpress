package io.wifi.starrailexpress.settlement.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** GameRenderer 尾部唯一绘制点，覆盖 Screen/LoadingOverlay，不依赖可被取消的 HUD 回调。 */
public final class SettlementOverlay {
    private static final int GOLD = 0xFFD4AF37, GOLD_DIM = 0xFF8B6914, TEXT = 0xFFFFF4DC,
            MUTED = 0xFF9E8B6E, CREAM = 0xFFF5E8C8;
    private SettlementOverlay() { }

    public static void render(float partialTick) {
        if (!SettlementClient.isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        // 后处理及 HUD 共用状态保护，防止污染 Iris、原版后处理或下帧的 GUI。
        try (SettlementRenderState ignored = new SettlementRenderState()) {
            mc.getMainRenderTarget().bindWrite(true);
            RenderSystem.disableScissor(); RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(false);
            if (!SettlementClient.isBlack() && mc.screen == null && mc.getOverlay() == null)
                SettlementPostEffect.render(partialTick);
            mc.getMainRenderTarget().bindWrite(true);
            int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
            RenderSystem.backupProjectionMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, w, h, 0, 1000, 21000),
                    com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.getModelViewStack().pushMatrix();
            RenderSystem.getModelViewStack().identity().translate(0, 0, -11000);
            RenderSystem.applyModelViewMatrix();
            try {
                RenderSystem.disableDepthTest(); RenderSystem.depthMask(false);
                RenderSystem.disableScissor(); RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
                RenderSystem.setShaderColor(1, 1, 1, 1);
                GuiGraphics g = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
                if (SettlementClient.isBlack() || mc.screen != null || mc.getOverlay() != null) {
                    g.fill(0, 0, w, h, 0xFF000000);
                } else {
                    draw(g, w, h, partialTick);
                }
                g.flush();
            } finally {
                RenderSystem.getModelViewStack().popMatrix(); RenderSystem.applyModelViewMatrix();
                RenderSystem.restoreProjectionMatrix();
            }
        }
    }

    private static void draw(GuiGraphics g, int w, int h, float partial) {
        var snapshot = SettlementClient.snapshot();
        if (snapshot == null) { g.fill(0, 0, w, h, 0xFF000000); return; }
        double time = SettlementClient.tick() + partial;
        int letterbox = Math.max(6, h / 26);
        g.fill(0, 0, w, letterbox, 0xFF080603);
        g.fill(0, h - letterbox, w, h, 0xFF080603);

        // 片名：只在开场出现，两行居中，上下各一条极细金线，不使用实心面板。
        if (time >= 62 && time < 210) {
            float fade = (float) (SettlementClient.smooth((time - 62) / 26)
                    * (1 - SettlementClient.smooth((time - 180) / 30)));
            if (fade > .01f) {
                int alpha = (int) (fade * 255) << 24;
                int cx = w / 2, top = letterbox + Math.max(14, h / 12);
                Component subtitle = Component.translatable("settlement.starrailexpress.subtitle");
                int titleW = Math.min(w - 40, Minecraft.getInstance().font.width(SettlementClient.title()) + 28);
                ornament(g, cx, top - 9, titleW / 2, (alpha & 0xFF000000) | (GOLD & 0xFFFFFF));
                fit(g, SettlementClient.title(), cx - titleW / 2, top, titleW, (alpha & 0xFF000000) | (CREAM & 0xFFFFFF), true);
                fit(g, subtitle, cx - titleW / 2, top + 14, titleW, (alpha & 0xFF000000) | (MUTED & 0xFFFFFF), true);
                ornament(g, cx, top + 26, titleW / 2, (alpha & 0xFF000000) | (GOLD_DIM & 0xFFFFFF));
            }
        }

        // MVP 聚焦：大数字 + 小标签，浮在舞台下方，无面板遮挡。
        if (time >= 212 && time < 320) {
            var mvp = SettlementClient.byId.get(snapshot.mvp());
            float fade = (float) (SettlementClient.smooth((time - 212) / 22)
                    * (1 - SettlementClient.smooth((time - 300) / 20)));
            if (mvp != null && fade > .01f) {
                int alpha = (int) (fade * 255) << 24;
                double count = SettlementClient.smooth((time - 214) / 90);
                // MVP 数据放在画面上方的空区，绝不压住舞台人物。
                int baseY = letterbox + Math.max(34, h / 9);
                Component name = Component.translatable("settlement.starrailexpress.mvp_name", mvp.fact.name());
                fit(g, name, w / 2 - 120, baseY - 16, 240, (alpha & 0xFF000000) | (GOLD & 0xFFFFFF), true);
                stat(g, w / 2 - 74, baseY + 2, (int) (mvp.fact.kills() * count),
                        Component.translatable("settlement.starrailexpress.hud.kills"), alpha);
                stat(g, w / 2, baseY + 2, (int) (mvp.fact.tasks() * count),
                        Component.translatable("settlement.starrailexpress.hud.tasks"), alpha);
                stat(g, w / 2 + 74, baseY + 2, (int) (mvp.fact.score() * count),
                        Component.translatable("settlement.starrailexpress.hud.score"), alpha);
            }
        }

        // 事件字幕：底部两行居中，只留一条细金线和序号，不画面板。
        if (time >= 320 && SettlementClient.eventIndex() >= 0) {
            var event = snapshot.events().get(SettlementClient.eventIndex());
            long seconds = Math.max(0, event.time()) / 1000;
            // 事件字幕贴近画面下缘，但停在跳过提示之上，二者不重叠。
            // 字幕整体位于角色下方（角色脚尖之上仍留白），不与人物身体重叠。
            int baseY = h - letterbox - Math.max(38, h / 9);
            Component stamp = Component.translatable("settlement.starrailexpress.event",
                    SettlementClient.eventIndex() + 1, snapshot.events().size(),
                    String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60));
            fit(g, stamp, w / 2 - 120, baseY, 240, MUTED, true);
            ornament(g, w / 2, baseY + 11, Math.min(120, w / 3), GOLD_DIM);
            wrapped(g, SettlementClient.eventText(), w / 2 - Math.min(190, w / 2 - 18),
                    baseY + 16, Math.min(380, w - 36), Math.max(18, h / 12));
        }

        // 跳过提示：只在可跳过后出现，短线 + 小字，不做整条填充。
        if (time >= 100) {
            int y = h - letterbox - 9;
            Component skip = Component.translatable("settlement.starrailexpress.skip");
            int textW = Minecraft.getInstance().font.width(skip);
            fit(g, skip, w / 2 - textW / 2, y, textW, MUTED, true);
            int barW = Math.min(70, w / 5);
            float held = Math.min(1, SettlementClient.holdTicks() / 30f);
            int bx = w / 2 - barW / 2;
            g.fill(bx, y + 11, bx + barW, y + 12, 0x40FFE8C0);
            if (held > 0) g.fill(bx, y + 11, bx + (int) (barW * held), y + 12, GOLD);
        }

        curtains(g, w, h, time);
        float black = (float) (1 - SettlementClient.smooth(time / 40));
        black = Math.max(black, (float) SettlementClient.smooth((time - snapshot.durationTicks() + 22) / 22));
        if (black > 0) g.fill(0, 0, w, h, ((int) (255 * black) << 24));
    }

    /** 一条两端渐隐的金线，中心一颗菱形，替代厚重边框。 */
    private static void ornament(GuiGraphics g, int cx, int y, int halfWidth, int color) {
        int alpha = color >>> 24;
        for (int i = 0; i < halfWidth; i++) {
            float t = i / (float) Math.max(1, halfWidth);
            int a = (int) (alpha * (1 - t) * .85f) << 24;
            g.fill(cx - i - 1, y, cx - i, y + 1, a | (color & 0xFFFFFF));
            g.fill(cx + i, y, cx + i + 1, y + 1, a | (color & 0xFFFFFF));
        }
        g.fill(cx - 2, y - 2, cx + 2, y + 3, color);
        g.fill(cx - 1, y - 1, cx + 1, y + 2, 0xFFFFE6A1);
    }

    private static void stat(GuiGraphics g, int cx, int y, int value, Component label, int alpha) {
        var font = Minecraft.getInstance().font;
        String number = Integer.toString(value);
        float scale = 1.7f;
        g.pose().pushPose();
        g.pose().translate(cx, y, 0); g.pose().scale(scale, scale, 1);
        g.drawString(font, number, -font.width(number) / 2, 0, (alpha & 0xFF000000) | (GOLD & 0xFFFFFF), false);
        g.pose().popPose();
        fit(g, label, cx - 34, y + 19, 68, (alpha & 0xFF000000) | (MUTED & 0xFFFFFF), true);
    }

    private static void curtains(GuiGraphics g, int w, int h, double time) {
        double opened = SettlementClient.smooth((time - 20) / 70);
        int side = (int) ((w / 2.0) * (1 - opened));
        // 帷幕移出后仍保留侧帷；褶皱与金色流苏全部本地绘制。
        side = Math.max(Math.max(4, w / 60), side);
        for (int x = 0; x < side; x += 3) {
            double fold = .5 + .5 * Math.cos(x * .23);
            int red = 65 + (int) (fold * 55), green = 8 + (int) (fold * 9), blue = 18 + (int) (fold * 8);
            int color = 0xFF000000 | red << 16 | green << 8 | blue;
            g.fill(x, 0, Math.min(side, x + 3), h, color);
            g.fill(w - Math.min(side, x + 3), 0, w - x, h, color);
        }
        g.fill(side - 2, 0, side, h, 0xFFC9A84C);
        g.fill(w - side, 0, w - side + 2, h, 0xFFC9A84C);
        int valance = Math.max(8, h / 26);
        for (int x = 0; x < w; x += 4) {
            int drop = valance + (int) ((.5 + .5 * Math.cos(x * Math.PI * 8 / Math.max(1, w))) * 7);
            g.fill(x, 0, x + 4, drop, 0xFF570B18);
            g.fill(x, drop - 1, x + 4, drop + 1, 0xFF8B6914);
        }
    }

    private static void fit(GuiGraphics g, Component text, int x, int y, int width, int color, boolean center) {
        var font = Minecraft.getInstance().font;
        float scale = Math.min(1, width / (float) Math.max(1, font.width(text)));
        g.pose().pushPose();
        g.pose().translate(center ? x + width / 2f : x, y, 0); g.pose().scale(scale, scale, 1);
        g.drawString(font, text, center ? -font.width(text) / 2 : 0, 0, color, false);
        g.pose().popPose();
    }

    private static void wrapped(GuiGraphics g, Component text, int x, int y, int width, int height) {
        var font = Minecraft.getInstance().font;
        float scale = 1;
        var lines = font.split(text, Math.max(1, width));
        if (lines.size() * 10 > height) {
            scale = Mth.clamp((float) Math.sqrt(height / (double) (lines.size() * 10)), .15f, 1);
            lines = font.split(text, Math.max(1, (int) (width / scale)));
            scale = Math.min(scale, height / (float) Math.max(1, lines.size() * 10));
        }
        g.enableScissor(x, y, x + width, y + height);
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(scale, scale, 1);
        for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), 0, i * 10, TEXT, false);
        g.pose().popPose(); g.disableScissor();
    }
}
