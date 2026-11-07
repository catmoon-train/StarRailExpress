package io.wifi.starrailexpress.settlement.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** GameRenderer 尾部唯一绘制点；漫画分镜不依赖可被取消的普通 HUD。 */
public final class SettlementOverlay {
    private static final int INK = 0xFF171719, PAPER = 0xFFF3EDDD, RED = 0xFFC34D43, MUTED = 0xFF827D73;
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
                if (SettlementClient.isBlack() || mc.screen != null || mc.getOverlay() != null)
                    g.fill(0, 0, w, h, 0xFF000000);
                else draw(g, w, h, partialTick);
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
        float ui = Mth.clamp(Math.min(w / 640f, h / 360f), .65f, 1.4f);
        int edge = Math.max(3, Math.round(5 * ui)), margin = Math.max(12, Math.round(w * .055f));
        boolean closing = time >= snapshot.durationTicks() - 80;

        // 纸白页边与墨线取代红帘；舞台中部保持干净，不给皮肤覆盖整屏网点。
        g.fill(0, 0, w, edge, PAPER); g.fill(0, h - edge, w, h, PAPER);
        g.fill(0, edge, edge, h - edge, PAPER); g.fill(w - edge, edge, w, h - edge, PAPER);
        g.renderOutline(edge, edge, w - edge * 2, h - edge * 2, INK);
        g.fillGradient(edge + 1, edge + 1, w - edge - 1, edge + Math.round(54 * ui), 0x66000000, 0);
        g.fillGradient(edge + 1, h - edge - Math.round(58 * ui), w - edge - 1, h - edge, 0, 0x88000000);
        float pageAlpha = (float) SettlementClient.smooth((time - 68) / 22);
        text(g, Component.translatable("settlement.starrailexpress.comic.running"), margin,
                edge + Math.round(8 * ui), Math.round(w * .65f), .72f * ui, tint(PAPER, pageAlpha), false);
        g.fill(w - margin - Math.round(23 * ui), edge + Math.round(9 * ui), w - margin,
                edge + Math.round(12 * ui), tint(RED, pageAlpha));

        double impact = pulse(time, 132, 15) * .65 + pulse(time, 218, 19);
        if (!closing && time >= 320 && SettlementClient.eventIndex() >= 0) {
            int ticks = snapshot.eventTicks();
            int index = SettlementClient.eventIndex();
            var event = snapshot.events().get(index);
            // 密集事件只在分镜组首帧、且是动作事件时强调，避免连续闪动。
            boolean action = event.source() != null || event.target() != null;
            int group = Math.max(1, (int) Math.ceil(24.0 / ticks));
            if (action && index % group == 0)
                impact = Math.max(impact, pulse(time, 320 + index * ticks, Math.min(12, ticks)) * .65);
        }
        if (!closing) speedLines(g, w, h, (float) Math.min(1, impact), ui);

        if (time >= 54 && time < 208) {
            float fade = envelope(time, 54, 17, 186, 22);
            int x = margin - Math.round((1 - fade) * 18 * ui);
            int y = Math.round(h * .13f), pw = Math.min(w - margin * 2, Math.round(265 * ui));
            paperPanel(g, x, y, pw, Math.round(74 * ui), fade, ui);
            int pad = Math.round(11 * ui);
            text(g, Component.translatable("settlement.starrailexpress.comic.chapter"), x + pad, y + Math.round(9 * ui),
                    pw - pad * 2, .78f * ui, tint(RED, fade), false);
            text(g, SettlementClient.title().copy().withStyle(style -> style.withColor(INK & 0xFFFFFF).withBold(true)),
                    x + pad, y + Math.round(26 * ui), pw - pad * 2, 1.8f * ui, tint(INK, fade), false);
            text(g, Component.translatable("settlement.starrailexpress.subtitle"), x + pad, y + Math.round(55 * ui),
                    pw - pad * 2, .8f * ui, tint(MUTED, fade), false);
        }

        if (time >= 212 && time < 320) {
            var mvp = SettlementClient.byId.get(snapshot.mvp());
            float fade = envelope(time, 212, 18, 298, 22);
            if (mvp != null) {
                // 数据卡居左，镜头将 MVP 留在画面偏右，不把人物压在面板下面。
                int pw = Math.min(Math.round(218 * ui), Math.round(w * .40f));
                int x = margin - Math.round((1 - fade) * 15 * ui), y = Math.round(h * .15f);
                int pad = Math.round(11 * ui);
                paperPanel(g, x, y, pw, Math.round(108 * ui), fade, ui);
                g.fill(x, y, x + Math.round(74 * ui), y + Math.round(18 * ui), tint(RED, fade));
                text(g, Component.translatable("settlement.starrailexpress.mvp"), x + Math.round(7 * ui),
                        y + Math.round(5 * ui), Math.round(62 * ui), .85f * ui, tint(PAPER, fade), false);
                text(g, Component.literal(mvp.fact.name()).withStyle(ChatFormatting.BOLD), x + pad,
                        y + Math.round(28 * ui), pw - pad * 2, 1.35f * ui, tint(INK, fade), false);
                g.fill(x + pad, y + Math.round(51 * ui), x + pw - pad, y + Math.round(52 * ui), tint(INK, fade * .22f));
                double count = SettlementClient.smooth((time - 218) / 62);
                String[] keys = {"kills", "tasks", "score"};
                int[] values = {mvp.fact.kills(), mvp.fact.tasks(), mvp.fact.score()};
                int column = (pw - pad * 2) / 3;
                for (int i = 0; i < 3; i++) {
                    int cx = x + pad + column * i + column / 2;
                    text(g, Component.literal(Long.toString(Math.round(values[i] * count))).withStyle(ChatFormatting.BOLD),
                            cx - column / 2, y + Math.round(61 * ui), column, 1.7f * ui, tint(INK, fade), true);
                    text(g, Component.translatable("settlement.starrailexpress.hud." + keys[i]), cx - column / 2,
                            y + Math.round(86 * ui), column, .78f * ui, tint(MUTED, fade), true);
                }
            }
        }

        if (!closing && time >= 320 && SettlementClient.eventIndex() >= 0) {
            var event = snapshot.events().get(SettlementClient.eventIndex());
            long seconds = Math.max(0, event.time()) / 1000;
            int pw = Math.min(w - margin * 2, Math.round(428 * ui));
            int ph = Math.round(66 * ui), x = (w - pw) / 2, y = h - edge - Math.round(31 * ui) - ph;
            // 回放原文含职业颜色，放在墨色字幕格内保持原有颜色的可读性。
            g.fill(x + 3, y + 3, x + pw + 3, y + ph + 3, 0x88000000);
            g.fill(x, y, x + pw, y + ph, 0xED171719);
            g.renderOutline(x, y, pw, ph, 0xB8F3EDDD);
            g.fill(x, y, x + Math.round(3 * ui), y + ph, RED);
            Component stamp = Component.translatable("settlement.starrailexpress.event", SettlementClient.eventIndex() + 1,
                    snapshot.events().size(), String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60));
            text(g, stamp, x + 12, y + Math.round(8 * ui), pw - 24, .75f * ui, 0xFFB9B2A4, true);
            wrapped(g, SettlementClient.eventText(), x + 12, y + Math.round(25 * ui), pw - 24,
                    ph - Math.round(31 * ui), ui);
            float progress = (float) (time - 320) / Math.max(1, snapshot.events().size() * snapshot.eventTicks());
            g.fill(x, y + ph - 2, x + Math.round(pw * Mth.clamp(progress, 0, 1)), y + ph, RED);
        }

        if (time >= 100 && !closing) {
            float fade = (float) SettlementClient.smooth((time - 100) / 15);
            int pw = Math.min(w - margin * 2, Math.round(180 * ui));
            int y = h - edge - Math.round(17 * ui), x = w - margin - pw;
            text(g, Component.translatable("settlement.starrailexpress.hud.skip"), x, y, pw, .76f * ui,
                    tint(PAPER, fade * .78f), false);
            float held = Math.min(1, SettlementClient.holdTicks() / 30f);
            if (held > 0) g.fill(x, y + Math.round(11 * ui), x + Math.round(pw * held), y + Math.round(13 * ui), RED);
        }

        shutters(g, w, h, time, snapshot.durationTicks());
        if (closing) {
            float fade = envelope(time, snapshot.durationTicks() - 58, 18, snapshot.durationTicks() - 22, 22);
            int pw = Math.min(w - margin * 2, Math.round(286 * ui)), ph = Math.round(73 * ui);
            int x = (w - pw) / 2, y = (h - ph) / 2;
            paperPanel(g, x, y, pw, ph, fade, ui);
            text(g, Component.translatable("settlement.starrailexpress.hud.curtain_call").withStyle(ChatFormatting.BOLD),
                    x + 10, y + Math.round(17 * ui), pw - 20, 1.55f * ui, tint(INK, fade), true);
            text(g, Component.translatable("settlement.starrailexpress.closing"), x + 10, y + Math.round(46 * ui),
                    pw - 20, .8f * ui, tint(MUTED, fade), true);
        }
        float black = (float) (1 - SettlementClient.smooth(time / 28));
        black = Math.max(black, (float) SettlementClient.smooth((time - snapshot.durationTicks() + 20) / 20));
        if (black > 0) g.fill(0, 0, w, h, tint(0xFF000000, black));
    }

    /** 纸片、错位墨影与局部网点构成分镜标题块；每个元素共享淡入透明度。 */
    private static void paperPanel(GuiGraphics g, int x, int y, int w, int h, float alpha, float ui) {
        if (alpha <= .01f) return;
        int shift = Math.max(2, Math.round(4 * ui));
        g.fill(x + shift, y + shift, x + w + shift, y + h + shift, tint(INK, alpha * .8f));
        g.fill(x, y, x + w, y + h, tint(PAPER, alpha * .97f));
        g.renderOutline(x, y, w, h, tint(INK, alpha));
        int step = Math.max(4, Math.round(6 * ui)), dot = Math.max(1, Math.round(ui));
        // 网点只放在卡片右侧的小三角内，固定采样，不随帧随机闪烁。
        for (int row = 0; row < 6; row++) for (int col = 0; col < 6 - row; col++) {
            int px = x + w - 5 - col * step, py = y + 5 + row * step;
            if (px > x && py + dot < y + h)
                g.fill(px, py, px + dot, py + dot, tint(INK, alpha * .12f));
        }
    }

    /** 斜切黑页从两侧揭开/合上，不再保留与场景割裂的剧场侧帘。 */
    private static void shutters(GuiGraphics g, int w, int h, double time, int duration) {
        double opened = SettlementClient.smooth((time - 12) / 65)
                * (1 - SettlementClient.smooth((time - duration + 66) / 46));
        float side = (float) (w * .65 * (1 - opened));
        if (side < .5f) return;
        float skew = (float) (w * .045 * (1 - opened));
        g.flush();
        RenderSystem.disableDepthTest(); RenderSystem.depthMask(false);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f m = g.pose().last().pose();
        quad(b, m, 0, 0, 0, h, side - skew, h, side + skew, 0, INK);
        quad(b, m, w - side - skew, 0, w - side + skew, h, w, h, w, 0, INK);
        quad(b, m, side + skew - 3, 0, side - skew - 3, h, side - skew, h, side + skew, 0, PAPER);
        quad(b, m, w - side - skew, 0, w - side + skew, h, w - side + skew + 3, h, w - side - skew + 3, 0, PAPER);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    /** 仅边缘出现短促放射线；合批绘制，不覆盖人物中央，不做整屏爆闪。 */
    private static void speedLines(GuiGraphics g, int w, int h, float strength, float ui) {
        if (strength <= .02f) return;
        g.flush();
        RenderSystem.disableDepthTest(); RenderSystem.depthMask(false);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f m = g.pose().last().pose();
        float cx = w * .56f, cy = h * .50f, radius = (float) Math.hypot(w, h);
        for (int i = 0; i < 28; i++) {
            double angle = i * Math.PI * 2 / 28 + .035 * Math.sin(i * 7.3);
            float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
            float innerX = cx + dx * w * (.32f + .04f * (i % 3));
            float innerY = cy + dy * h * (.34f + .03f * (i % 4));
            float outerX = cx + dx * radius, outerY = cy + dy * radius;
            float thick = (2 + i % 3) * ui * strength;
            quad(b, m, innerX - dy * .3f, innerY + dx * .3f, outerX - dy * thick, outerY + dx * thick,
                    outerX + dy * thick, outerY - dx * thick, innerX + dy * .3f, innerY - dx * .3f,
                    tint(PAPER, strength * .55f));
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    private static void quad(BufferBuilder b, Matrix4f m, float x0, float y0, float x1, float y1,
                             float x2, float y2, float x3, float y3, int color) {
        b.addVertex(m, x0, y0, 0).setColor(color); b.addVertex(m, x1, y1, 0).setColor(color);
        b.addVertex(m, x2, y2, 0).setColor(color); b.addVertex(m, x3, y3, 0).setColor(color);
    }

    private static float envelope(double time, double start, double in, double end, double out) {
        return (float) (SettlementClient.smooth((time - start) / in) * (1 - SettlementClient.smooth((time - end) / out)));
    }

    private static double pulse(double time, double start, double duration) {
        if (time < start || time > start + duration) return 0;
        return (1 - SettlementClient.smooth((time - start) / duration));
    }

    private static int tint(int color, float alpha) {
        return Math.round(Mth.clamp(alpha, 0, 1) * 255) << 24 | color & 0xFFFFFF;
    }

    private static void text(GuiGraphics g, Component text, int x, int y, int width, float preferredScale,
                             int color, boolean center) {
        if ((color >>> 24) < 4 || width <= 0) return;
        var font = Minecraft.getInstance().font;
        float scale = Math.min(preferredScale, width / (float) Math.max(1, font.width(text)));
        g.pose().pushPose();
        g.pose().translate(center ? x + width / 2f : x, y, 0); g.pose().scale(scale, scale, 1);
        g.drawString(font, text, center ? -font.width(text) / 2 : 0, 0, color, false);
        g.pose().popPose();
    }

    private static void wrapped(GuiGraphics g, Component text, int x, int y, int width, int height, float ui) {
        var font = Minecraft.getInstance().font;
        float scale = ui;
        var lines = font.split(text, Math.max(1, (int) (width / scale)));
        if (lines.size() * 11 * scale > height) {
            scale *= (float) Math.sqrt(height / (double) (lines.size() * 11 * scale));
            lines = font.split(text, Math.max(1, (int) (width / scale)));
            scale = Math.min(scale, height / (float) Math.max(1, lines.size() * 11));
        }
        g.enableScissor(x, y, x + width, y + height);
        g.pose().pushPose(); g.pose().translate(x + width / 2f, y, 0); g.pose().scale(scale, scale, 1);
        for (int i = 0; i < lines.size(); i++)
            g.drawString(font, lines.get(i), -font.width(lines.get(i)) / 2, i * 11, PAPER, false);
        g.pose().popPose(); g.disableScissor();
    }
}
