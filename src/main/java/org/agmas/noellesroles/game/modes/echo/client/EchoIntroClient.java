package org.agmas.noellesroles.game.modes.echo.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoIntroPayload;
import org.agmas.noellesroles.game.modes.echo.state.EchoSpecialization;

import java.util.Locale;

/**
 * 开局运镜的客户端表现：解说文字按服务器给的时间轴自己推进，运镜结束后在玩家身边显示所选专长的空间文字。
 *
 * <p>运镜本身由既有的 {@code AdvancedCameraDirector} 播放（黑边、交还视角都在那边），本类不接管相机，
 * 也不做任何权威判定；它只负责「这段时间屏幕/世界显示什么」，并对外提供
 * {@link #blocksUi()}，让专长选择界面推迟到运镜与专长展示之后再弹出。
 */
public final class EchoIntroClient {
    /** 运镜结束后先只展示专长文字，再允许专长界面打开（tick）。 */
    private static final int UI_BLOCK_TICKS = 30;
    /** 专长空间文字的显示时长（tick）。 */
    private static final int SPEC_TEXT_TICKS = 220;
    private static final MultiBufferSource.BufferSource BUFFER =
            MultiBufferSource.immediate(new ByteBufferBuilder(8192));

    private static EchoIntroPayload view;
    private static int age;
    private static int uiBlockTicks;
    private static int specTicks;

    private EchoIntroClient() { }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(EchoIntroPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    view = payload;
                    age = 0;
                    uiBlockTicks = 0;
                    specTicks = 0;
                }));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (view != null) {
                if (client.player == null || client.level == null) {
                    view = null;
                } else if (++age > view.totalTicks()) {
                    finishIntro();
                }
            }
            if (uiBlockTicks > 0) uiBlockTicks--;
            if (specTicks > 0) specTicks--;
        });
        // 开局运镜期间 CommonClientHudRenderer 会被 OpeningPresentationCoordinator 抑制（那是给常规对局
        // HUD 用的），所以解说文字直接挂到未被抑制的 FakeHudRenderCallback 上，
        // 与地图规则卡 / welcome 走同一条渲染链。
        io.wifi.utils.client.betterrender.FakeHudRenderCallback.EVENT.register(
                (graphics, delta) -> renderCue(graphics));
        WorldRenderEvents.AFTER_TRANSLUCENT.register(EchoIntroClient::renderSpecText);
    }

    /** 运镜（含解说）是否仍在播放。 */
    public static boolean isActive() {
        return view != null && age <= view.totalTicks();
    }

    /** 运镜或专长展示期间为 true：专长选择界面此时不该抢屏。 */
    public static boolean blocksUi() {
        return isActive() || uiBlockTicks > 0;
    }

    /** 让专长空间文字重新显示一段时间（选择变更时调用）；运镜期间不抢镜。 */
    public static void showcaseSpecialization() {
        if (!isActive()) specTicks = SPEC_TEXT_TICKS;
    }

    public static boolean showingSpecialization() {
        return specTicks > 0;
    }

    /** 调试命令用：立刻结束运镜文字。 */
    public static void clear() {
        view = null;
        age = 0;
        uiBlockTicks = 0;
        specTicks = 0;
    }

    private static void finishIntro() {
        view = null;
        age = 0;
        uiBlockTicks = UI_BLOCK_TICKS;
        specTicks = SPEC_TEXT_TICKS;
    }

    // ==================== 运镜解说文字 ====================

    private static void renderCue(FakeGuiGraphics g) {
        if (!isActive()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui || client.screen != null) return;
        var cue = view.cueAt(age);
        if (cue == null) return;
        float fade = cueFade(cue);
        if (fade <= 0.02F) return;
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        int bar = Math.max(18, Math.round(height * 0.11F));
        int contentWidth = Mth.clamp(Math.round(width * 0.34F), 200, 380);
        int left = 30;
        int baseY = height - bar - 76;
        // 左侧金色引导线 + 标题 + 正文，与地图规则卡（右侧）分区，不互相遮挡。
        g.fill(left - 8, baseY, left - 5, baseY + 44, EchoAnim.fade(0xFFD4AF37, fade));
        drawScaled(g, Component.translatable(cue.titleKey()), left, baseY, 1.55F, EchoAnim.fade(0xFFFFF4DC, fade));
        int y = baseY + 24;
        for (FormattedCharSequence line : client.font.split(Component.translatable(cue.bodyKey()),
                Math.max(60, Math.round(contentWidth / 1.05F)))) {
            if (y > baseY + 58) break;
            drawScaled(g, line, left, y, 1.05F, EchoAnim.fade(0xFFC8B898, fade));
            y += 12;
        }
        int span = Math.max(1, cue.endTick() - cue.startTick());
        int done = Mth.clamp(age - cue.startTick(), 0, span);
        g.fill(left, baseY + 62, left + contentWidth, baseY + 64, EchoAnim.fade(0x66382A1B, fade));
        g.fill(left, baseY + 62, left + contentWidth * done / span, baseY + 64, EchoAnim.fade(0xFFD4AF37, fade));
    }

    /** 单条解说自己的淡入/淡出，避免与相邻解说之间出现硬切。 */
    private static float cueFade(EchoIntroPayload.Cue cue) {
        float in = Mth.clamp((age - cue.startTick()) / 8.0F, 0.0F, 1.0F);
        float out = Mth.clamp((cue.endTick() - age) / 10.0F, 0.0F, 1.0F);
        return Math.min(in, out);
    }

    private static void drawScaled(FakeGuiGraphics g, Component text, int x, int y, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(Minecraft.getInstance().font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private static void drawScaled(FakeGuiGraphics g, FormattedCharSequence text, int x, int y, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(Minecraft.getInstance().font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    // ==================== 专长空间文字 ====================

    private static void renderSpecText(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
        if (specTicks <= 0) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.options.hideGui || context.matrixStack() == null) return;
        var view = EchoSpecializationClient.view();
        if (view == null) return;
        var spec = EchoSpecialization.byId(view.selected()).orElse(null);
        if (spec == null) return;
        float alpha = Math.min(1.0F, Math.min(specTicks / 20.0F, (SPEC_TEXT_TICKS - specTicks) / 8.0F));
        if (alpha <= 0.02F) return;
        int color = EchoAnim.fade(0xFFFFF4DC, alpha);
        Component text = Component.translatable("echo.intro.spec_tag", Component.translatable(spec.nameKey()));
        // 站在玩家「相机右侧」一点点，读起来像贴在人物旁边而不是压在头顶。
        Vec3 camera = context.camera().getPosition();
        var left = context.camera().getLeftVector();
        Vec3 right = new Vec3(-left.x(), 0.0D, -left.z());
        right = right.lengthSqr() < 1.0E-4D ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 pos = client.player.getPosition(context.tickCounter().getGameTimeDeltaPartialTick(false))
                .add(right.x * 1.05D, client.player.getBbHeight() * 0.85D, right.z * 1.05D);
        float scale = 0.026F;
        var pose = context.matrixStack();
        pose.pushPose();
        pose.translate(pos.x - camera.x, pos.y - camera.y, pos.z - camera.z);
        pose.mulPose(client.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-scale, -scale, scale);
        client.font.drawInBatch(text, -client.font.width(text) / 2F, 0, color, false,
                pose.last().pose(), BUFFER, Font.DisplayMode.SEE_THROUGH, 0x40000000, 15728880);
        pose.popPose();
        BUFFER.endBatch();
    }

    /** 供 HUD 之外的地方（调试命令）复用的一句摘要。 */
    public static String describe() {
        if (view == null) return "idle";
        return String.format(Locale.ROOT, "intro %d/%d cues=%d specText=%d", age, view.totalTicks(),
                view.cues().size(), specTicks);
    }
}
