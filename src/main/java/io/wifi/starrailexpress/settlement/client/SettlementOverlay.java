package io.wifi.starrailexpress.settlement.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** GameRenderer 尾部唯一绘制点，覆盖 Screen/LoadingOverlay，不依赖可被取消的 HUD 回调。 */
public final class SettlementOverlay {
    private static final int GOLD = 0xFFD4AF37, TEXT = 0xFFFFF4DC, MUTED = 0xFF9E8B6E;
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
        int letterbox = Math.max(8, h / 22);
        g.fill(0, 0, w, letterbox, 0xFF080603);
        g.fill(0, h - letterbox, w, h, 0xFF080603);
        int ticketW = Math.max(30, Math.min(560, w - 28)), ticketX = (w - ticketW) / 2;
        if (time >= 65) {
            panel(g, ticketX, letterbox + 6, ticketW, 38);
            fit(g, SettlementClient.title(), ticketX + 9, letterbox + 12, ticketW - 18, GOLD, true);
            fit(g, Component.translatable("settlement.starrailexpress.subtitle"), ticketX + 9,
                    letterbox + 27, ticketW - 18, MUTED, true);
        }
        int footerH = Math.min(Math.max(38, h / 3), Math.min(112, Math.max(58, h / 4)));
        int footerY = h - letterbox - footerH - 13;
        if (time < 320) {
            panel(g, ticketX, footerY, ticketW, footerH);
            var mvp = SettlementClient.byId.get(snapshot.mvp());
            fit(g, mvp == null ? Component.translatable("settlement.starrailexpress.cast")
                            : Component.translatable("settlement.starrailexpress.mvp_name", mvp.fact.name()),
                    ticketX + 9, footerY + 8, ticketW - 18, GOLD, true);
            int totalKills = mvp == null ? 0 : mvp.fact.kills();
            int totalTasks = mvp == null ? 0 : mvp.fact.tasks();
            int totalScore = mvp == null ? 0 : mvp.fact.score();
            double count = SettlementClient.smooth((time - 145) / 120);
            int col = (ticketW - 16) / 3;
            fit(g, Component.translatable("settlement.starrailexpress.kills", (int) (totalKills * count)),
                    ticketX + 8, footerY + 27, col - 4, TEXT, true);
            fit(g, Component.translatable("settlement.starrailexpress.tasks", (int) (totalTasks * count)),
                    ticketX + 8 + col, footerY + 27, col - 4, TEXT, true);
            fit(g, Component.translatable("settlement.starrailexpress.score", (int) (totalScore * count)),
                    ticketX + 8 + col * 2, footerY + 27, col - 4, TEXT, true);
            if (mvp != null && footerH >= 75) {
                fit(g, Component.translatable("settlement.starrailexpress.personal", mvp.fact.kills(),
                                mvp.fact.tasks(), mvp.fact.score()), ticketX + 8, footerY + 46, ticketW - 16, MUTED, true);
            }
        } else if (SettlementClient.eventIndex() >= 0) {
            var event = snapshot.events().get(SettlementClient.eventIndex());
            panel(g, ticketX, footerY, ticketW, footerH);
            long seconds = Math.max(0, event.time()) / 1000;
            Component stamp = Component.translatable("settlement.starrailexpress.event",
                    SettlementClient.eventIndex() + 1, snapshot.events().size(),
                    String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60));
            fit(g, stamp, ticketX + 9, footerY + 7, ticketW - 18, GOLD, false);
            // 全文保持 Component/FormattedCharSequence 的翻译和样式；超长时缩小而非截断事件。
            wrapped(g, SettlementClient.eventText(), ticketX + 9, footerY + 23, ticketW - 18, footerH - 38);
        }
        if (time >= 100) {
            Component skip = Component.translatable("settlement.starrailexpress.skip");
            fit(g, skip, ticketX + 6, h - letterbox - 10, ticketW - 12, MUTED, true);
            int bar = (int) ((ticketW - 20) * Math.min(1, SettlementClient.holdTicks() / 30f));
            g.fill(ticketX + 10, h - letterbox - 2, ticketX + ticketW - 10, h - letterbox, 0xFF4A3621);
            g.fill(ticketX + 10, h - letterbox - 2, ticketX + 10 + bar, h - letterbox, GOLD);
        }
        curtains(g, w, h, time);
        float black = (float) (1 - SettlementClient.smooth(time / 40));
        black = Math.max(black, (float) SettlementClient.smooth((time - snapshot.durationTicks() + 22) / 22));
        if (black > 0) g.fill(0, 0, w, h, ((int) (255 * black) << 24));
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

    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, 0xD81A1008, 0xE820140A);
        g.renderOutline(x, y, w, h, 0xFF8B6914);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x33FFE8C0);
        // 车票两侧打孔。
        for (int i = y + 7; i < y + h - 4; i += 7) {
            g.fill(x + 3, i, x + 4, i + 2, 0x667D653A);
            g.fill(x + w - 4, i, x + w - 3, i + 2, 0x667D653A);
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
