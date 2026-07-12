package net.exmo.sre.sixtyseconds.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 《重生之我在哈比列车的60秒》——末日 60 秒生存模式开场演出（约 60s）。
 *
 * <p>纯客户端、自驱动（毫秒时间线，帧率无关，参照 {@code TrainLoadingScreen} /
 * {@code ChatDialogueScreen} 的写法）。一系列「黑底 + 淡入淡出图片卡 + 打字机字幕 +
 * 音效」的镜头，穿插大量黑屏独白，末段白闪冲击 → 死寂 → 标题落定。
 *
 * <p>字幕统一压在<b>下方黑幕带</b>内，放大字号；打字机敲字音在渲染中按「可见字数增长」
 * 触发，与文字同一时钟，保证同步。
 *
 * <p><b>图片素材</b>：镜头图从
 * {@code assets/noellesroles/textures/cinematic/sixty_seconds/s1.png … s7.png} 读取；
 * 素材缺失时该镜头自动降级为纯黑（不显示紫色缺失纹理），文字与音效照常。
 *
 * <p><b>音效</b>：全部复用原版 {@link SoundEvents}，不新增 ogg 资源（遵循 CLAUDE.md）。
 *
 * <p>触发：客户端命令 {@code /sre:client screen intro_sixty_seconds}。ESC 或点击可跳过。
 */
@Environment(EnvType.CLIENT)
public class SixtySecondsIntroScreen extends Screen {

    // ── 时间线常量（毫秒） ────────────────────────────────────
    private static final long TOTAL_MS = 60_000L;
    private static final long SHOT_FADE_MS = 600L;    // 每个镜头黑场淡入/淡出
    private static final long CHAR_INTERVAL_MS = 155L; // 打字机每字节奏（放慢）
    private static final long TYPE_DELAY_MS = 500L;    // 镜头淡入后再开始打字

    // ── 版式 ─────────────────────────────────────────────────
    private static final float SUBTITLE_SCALE = 1.55f; // 字幕放大
    private static final float TITLE_SCALE = 2.4f;      // 标题字号
    private static final float LETTERBOX_TOP = 0.10f;
    private static final float LETTERBOX_BOTTOM = 0.24f; // 下方黑幕带（放字幕）

    private static final String KEY = "cinematic.sre.sixty_seconds.";
    private static final int TEXT_RGB = 0xF3ECDC;   // 暖白
    private static final int TITLE_RGB = 0xF7EAD2;

    // ── 状态 ─────────────────────────────────────────────────
    private final long createdAt;
    private final List<Shot> shots = new ArrayList<>();
    private final List<Cue> cues = new ArrayList<>();
    private final Map<ResourceLocation, Boolean> texExists = new HashMap<>();

    private long elapsed;          // 当前经过（ms），tick 中推进（音效 cue 用）
    private int lastTypedChars = -1; // 上一帧可见字数（打字机敲字音用，渲染中推进）

    public SixtySecondsIntroScreen() {
        super(Component.translatable(KEY + "title"));
        this.createdAt = Util.getMillis();
        buildStoryboard();
        buildCues();
    }

    // ── 分镜脚本（60s，多黑屏独白） ───────────────────────────
    private void buildStoryboard() {
        // S0 黑场引子（时钟滴答）
        shots.add(Shot.black(0, 4_000));
        // 独白 1（黑屏）
        shots.add(Shot.black(4_000, 9_500, KEY + "m1"));
        // S1 平静黄昏
        shots.add(Shot.image(9_500, 15_000, "s1", KEY + "s1"));
        // 独白 2（黑屏）
        shots.add(Shot.black(15_000, 20_000, KEY + "m2a", KEY + "m2b"));
        // S2 陨石坠落
        shots.add(Shot.image(20_000, 25_500, "s2", KEY + "s2"));
        // S3 广播 / 最后通告
        shots.add(Shot.image(25_500, 30_500, "s3", KEY + "s3a", KEY + "s3b"));
        // S4 疯狂搜刮
        shots.add(Shot.image(30_500, 36_000, "s4", KEY + "s4a", KEY + "s4b"));
        // S5 冲向地下室
        shots.add(Shot.image(36_000, 40_000, "s5", KEY + "s5"));
        // S6 陨石撕裂天空（无字）
        shots.add(Shot.image(40_000, 43_500, "s6"));
        // S7 白闪 + 冲击
        shots.add(Shot.flash(43_500, 44_800));
        // 独白 3（死寂 · 黑屏）
        shots.add(Shot.black(44_800, 51_000, KEY + "m3a", KEY + "m3b", KEY + "m3c"));
        // 独白 4（黑屏）
        shots.add(Shot.black(51_000, 55_500, KEY + "m4"));
        // S9 地下室微光 + 标题
        Shot finale = Shot.image(55_500, 60_000, "s7", KEY + "s9");
        finale.title = true;
        shots.add(finale);
    }

    // ── 音效时间线（全原版音；已调柔音量） ────────────────────
    private void buildCues() {
        // S0 时钟滴答
        cue(600, SoundEvents.UI_BUTTON_CLICK, 0.7f, 0.28f);
        cue(2_200, SoundEvents.UI_BUTTON_CLICK, 0.7f, 0.28f);
        // 独白氛围：低沉洞穴回响
        cue(5_000, SoundEvents.AMBIENT_CAVE, 0.75f, 0.30f);
        // 天空异象：远方闷雷（两声渐近）
        cue(15_600, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5f, 0.28f);
        cue(20_600, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6f, 0.35f);
        // 广播室诡异氛围 + “60 秒”落字重音
        cue(25_800, SoundEvents.AMBIENT_CAVE, 0.8f, 0.35f);
        cue(28_600, SoundEvents.UI_BUTTON_CLICK, 1.3f, 0.32f);
        // 倒计时（S4→S5，渐急、渐高、柔和）
        cue(31_000, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.85f, 0.4f);
        cue(32_500, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.95f, 0.4f);
        cue(34_000, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.05f, 0.4f);
        cue(35_500, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.15f, 0.4f);
        cue(36_800, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.25f, 0.45f);
        cue(37_800, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.35f, 0.45f);
        cue(38_600, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.45f, 0.45f);
        cue(39_200, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.6f, 0.5f);
        cue(39_700, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.8f, 0.55f);
        // S7 陨石撞击（峰值）
        cue(43_550, SoundEvents.GENERIC_EXPLODE, 0.5f, 1.0f);
        cue(43_550, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6f, 0.9f);
        cue(43_950, SoundEvents.GENERIC_EXPLODE, 0.35f, 0.6f); // 碎屑余震
        // 死寂中的心跳（渐慢）
        cue(45_400, SoundEvents.WARDEN_HEARTBEAT, 0.9f, 0.5f);
        cue(47_000, SoundEvents.WARDEN_HEARTBEAT, 0.9f, 0.45f);
        cue(48_800, SoundEvents.WARDEN_HEARTBEAT, 0.85f, 0.4f);
        cue(50_600, SoundEvents.WARDEN_HEARTBEAT, 0.85f, 0.35f);
        cue(52_600, SoundEvents.WARDEN_HEARTBEAT, 0.8f, 0.3f);
        // S9 标题落定：低沉重锤
        cue(56_600, SoundEvents.ANVIL_LAND, 0.5f, 0.8f);
        cue(56_600, SoundEvents.GENERIC_EXPLODE, 0.3f, 0.4f);
    }

    // 原版 SoundEvents 字段类型不统一（部分 SoundEvent，部分 Holder<SoundEvent>），
    // 用重载让编译期各自匹配。
    private void cue(long at, SoundEvent sound, float pitch, float volume) {
        cues.add(new Cue(at, sound, pitch, volume));
    }

    private void cue(long at, Holder<SoundEvent> sound, float pitch, float volume) {
        cue(at, sound.value(), pitch, volume);
    }

    // ── 生命周期 ──────────────────────────────────────────────
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        LocalPlayer player = null;
        if (minecraft != null) {
            player = minecraft.player;
        }
        if (player!= null && !player.hasPermissions(1)){
            return false;
        }
        return true;
    }

    @Override
    public void tick() {
        this.elapsed = Util.getMillis() - createdAt;
        if (elapsed >= TOTAL_MS) {
            onClose();
            return;
        }
        for (Cue c : cues) {
            if (!c.fired && elapsed >= c.at) {
                c.fired = true;
                play(c.sound, c.pitch, c.volume);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        onClose();
        return true;
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    // ── 渲染 ──────────────────────────────────────────────────
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xFF000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int w = this.width, h = this.height;
        long now = Util.getMillis() - createdAt; // 渲染用实时 ms（更平滑，也用于打字机同步）

        g.fill(0, 0, w, h, 0xFF000000);

        Shot shot = shotAt(now);
        if (shot != null) {
            float a = shot.alpha(now);
            if (shot.type == Type.IMAGE) {
                renderImage(g, shot, now, a, w, h);
                renderLetterbox(g, w, h, a);
                renderVignette(g, w, h, a);
            } else if (shot.type == Type.FLASH) {
                renderFlash(g, shot, now, w, h);
            }
            if (shot.title) {
                renderTitle(g, shot, now, w, h);
            }
            renderSubtitle(g, shot, now, a, w, h);
        }

        if (minecraft != null && minecraft.player != null && minecraft.player.hasPermissions(1)) {
            renderSkipHint(g, w, h);
        }
    }

    private void renderImage(GuiGraphics g, Shot shot, long now, float alpha, int w, int h) {
        ResourceLocation rl = shot.texture;
        if (rl == null || !textureAvailable(rl) || alpha <= 0.01f) return;

        float p = shot.progress(now);
        float zoom = 1.0f + 0.06f * p; // 缓慢推近（Ken Burns）
        int dw = (int) (w * zoom), dh = (int) (h * zoom);
        int x = (w - dw) / 2, y = (h - dh) / 2;

        RenderSystem.enableBlend();
        g.setColor(1f, 1f, 1f, alpha);
        g.enableScissor(0, 0, w, h);
        g.blit(rl, x, y, 0, 0, dw, dh, dw, dh);
        g.disableScissor();
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private void renderLetterbox(GuiGraphics g, int w, int h, float alpha) {
        int top = (int) (h * LETTERBOX_TOP);
        int bot = (int) (h * LETTERBOX_BOTTOM);
        int c = withAlpha(0x000000, alpha);
        g.fill(0, 0, w, top, c);
        g.fill(0, h - bot, w, h, c);
    }

    private void renderVignette(GuiGraphics g, int w, int h, float alpha) {
        int topH = (int) (h * 0.22f);
        int botH = (int) (h * 0.40f);
        g.fillGradient(0, 0, w, topH, withAlpha(0x05070E, 0.55f * alpha), withAlpha(0x05070E, 0f));
        g.fillGradient(0, h - botH, w, h, withAlpha(0x05070E, 0f), withAlpha(0x05070E, 0.85f * alpha));
    }

    private void renderFlash(GuiGraphics g, Shot shot, long now, int w, int h) {
        long e = now - shot.start;
        long dur = shot.end - shot.start;
        long rise = dur / 3;
        float f = e < rise ? (float) e / rise : 1f - (float) (e - rise) / (dur - rise);
        f = Mth.clamp(f, 0f, 1f);
        g.fill(0, 0, w, h, withAlpha(0xFFFFFF, f));
    }

    /** 字幕：统一压在下方黑幕带内，放大字号，打字机逐字浮现（含敲字音同步）。 */
    private void renderSubtitle(GuiGraphics g, Shot shot, long now, float alpha, int w, int h) {
        if (shot.lines.isEmpty() || alpha <= 0.02f) {
            if (shot.lines.isEmpty()) lastTypedChars = 0;
            return;
        }

        int shown = typedCharsFor(shot, now);
        // 打字机敲字音：可见字数增长且新增不多（排除切镜跳变）→ 敲一下，与文字完全同步
        if (lastTypedChars >= 0 && shown > lastTypedChars && shown - lastTypedChars <= 3
                && !isBlank(shot.charAtGlobal(shown - 1))) {
            play(SoundEvents.UI_BUTTON_CLICK, 1.15f, 0.16f);
        }
        lastTypedChars = shown;

        int lineH = (int) (font.lineHeight * SUBTITLE_SCALE) + 8;
        int n = shot.lines.size();
        int bottomY = h - (int) (h * 0.07f);
        int baseY = bottomY - n * lineH;

        int consumed = 0;
        for (int i = 0; i < n; i++) {
            String full = shot.lineText(i);
            int visible = shown >= consumed + full.length()
                    ? full.length()
                    : Math.max(0, shown - consumed);
            consumed += full.length();
            if (visible <= 0) continue;

            boolean typing = visible < full.length();
            String draw = typing ? full.substring(0, visible) + "_" : full;
            int y = baseY + i * lineH;
            drawCenteredScaled(g, draw, w / 2, y, SUBTITLE_SCALE, withAlpha(TEXT_RGB, alpha));
        }
    }

    private void renderTitle(GuiGraphics g, Shot shot, long now, int w, int h) {
        long e = now - shot.start;
        float in = smoothstep((e - 1000L) / 1000f); // 标题稍晚浮现
        if (in <= 0.01f) return;
        String title = Component.translatable(KEY + "title").getString();
        float scale = TITLE_SCALE + 0.3f * (1f - in); // 轻微回落
        drawCenteredScaled(g, title, w / 2, (int) (h * 0.40f), scale, withAlpha(TITLE_RGB, in));
    }

    private void renderSkipHint(GuiGraphics g, int w, int h) {
        String hint = Component.translatable(KEY + "hint").getString();
        g.drawString(font, hint, w - font.width(hint) - 8, h - font.lineHeight - 6,
                withAlpha(0x8899AA, 0.55f), false);
    }

    // ── 打字机进度 ────────────────────────────────────────────
    private int typedCharsFor(Shot shot, long now) {
        long reveal = now - shot.start - SHOT_FADE_MS - TYPE_DELAY_MS;
        if (reveal <= 0) return 0;
        return (int) (reveal / CHAR_INTERVAL_MS);
    }

    private Shot shotAt(long now) {
        for (Shot s : shots) {
            if (now >= s.start && now < s.end) return s;
        }
        return shots.isEmpty() ? null : shots.get(shots.size() - 1);
    }

    private static boolean isBlank(char c) {
        return c == '\0' || Character.isWhitespace(c);
    }

    // ── 纹理存在性（缺失则降级黑场） ──────────────────────────
    private boolean textureAvailable(ResourceLocation rl) {
        Boolean cached = texExists.get(rl);
        if (cached != null) return cached;
        boolean present = minecraft != null
                && minecraft.getResourceManager().getResource(rl).isPresent();
        texExists.put(rl, present);
        return present;
    }

    // ── 声音 ──────────────────────────────────────────────────
    private void play(SoundEvent sound, float pitch, float volume) {
        if (minecraft == null || minecraft.getSoundManager() == null) return;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private void play(Holder<SoundEvent> sound, float pitch, float volume) {
        play(sound.value(), pitch, volume);
    }

    // ── 小工具（内联自 LoadingFx，避免跨包耦合） ──────────────
    private static float clamp01(float t) {
        return Mth.clamp(t, 0f, 1f);
    }

    private static float smoothstep(float t) {
        t = clamp01(t);
        return t * t * (3f - 2f * t);
    }

    private static int withAlpha(int rgb, float alpha) {
        int a = Mth.clamp((int) (alpha * 255f), 0, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private void drawCenteredScaled(GuiGraphics g, String text, int cx, int y, float scale, int argb) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0f);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, -font.width(text) / 2, 0, argb, true);
        g.pose().popPose();
    }

    // ── 类型 ─────────────────────────────────────────────────
    private enum Type { BLACK, IMAGE, FLASH }

    private static final class Shot {
        final Type type;
        final long start;
        final long end;
        final ResourceLocation texture;   // 可空
        final List<String> lines;         // 翻译键
        boolean title;

        private Shot(Type type, long start, long end, ResourceLocation texture, List<String> lines) {
            this.type = type;
            this.start = start;
            this.end = end;
            this.texture = texture;
            this.lines = lines;
        }

        static Shot black(long s, long e, String... lineKeys) {
            return new Shot(Type.BLACK, s, e, null, List.of(lineKeys));
        }

        static Shot flash(long s, long e) {
            return new Shot(Type.FLASH, s, e, null, List.of());
        }

        static Shot image(long s, long e, String img, String... lineKeys) {
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(
                    "noellesroles", "textures/cinematic/sixty_seconds/" + img + ".png");
            return new Shot(Type.IMAGE, s, e, rl, List.of(lineKeys));
        }

        float alpha(long now) {
            float in = smoothstep((now - start) / (float) SHOT_FADE_MS);
            float out = smoothstep((end - now) / (float) SHOT_FADE_MS);
            return Math.min(in, out);
        }

        float progress(long now) {
            return clamp01((now - start) / (float) (end - start));
        }

        String lineText(int i) {
            return Component.translatable(lines.get(i)).getString();
        }

        /** 跨所有行拼接后的第 idx 个字符（用于打字机敲字音判断空白）。 */
        char charAtGlobal(int idx) {
            int consumed = 0;
            for (String key : lines) {
                String s = Component.translatable(key).getString();
                if (idx < consumed + s.length()) return s.charAt(idx - consumed);
                consumed += s.length();
            }
            return '\0';
        }
    }

    private static final class Cue {
        final long at;
        final SoundEvent sound;
        final float pitch;
        final float volume;
        boolean fired;

        Cue(long at, SoundEvent sound, float pitch, float volume) {
            this.at = at;
            this.sound = sound;
            this.pitch = pitch;
            this.volume = volume;
        }
    }
}
