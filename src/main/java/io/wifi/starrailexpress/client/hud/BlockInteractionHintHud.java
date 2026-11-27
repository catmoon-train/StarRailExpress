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

import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.client.gui.OpeningPresentationCoordinator;
import io.wifi.starrailexpress.client.gui.ScopeOverlayRenderer;
import io.wifi.starrailexpress.client.ui.SRETheme;
import io.wifi.starrailexpress.client.ui.UiText;
import io.wifi.starrailexpress.content.block.DoorPartBlock;
import io.wifi.starrailexpress.content.block.EntityInteractionBlock;
import io.wifi.starrailexpress.content.block.EntityInteractionPanelBlock;
import io.wifi.starrailexpress.content.block.HornBlock;
import io.wifi.starrailexpress.content.block.MinigameQuestBlock;
import io.wifi.starrailexpress.content.block.MinigameQuestPanelBlock;
import io.wifi.starrailexpress.content.block.MountableBlock;
import io.wifi.starrailexpress.content.block.NeonPillarBlock;
import io.wifi.starrailexpress.content.block.NeonTubeBlock;
import io.wifi.starrailexpress.content.block.PlatterBlock;
import io.wifi.starrailexpress.content.block.PrivacyBlock;
import io.wifi.starrailexpress.content.block.SecurityMonitorBlock;
import io.wifi.starrailexpress.content.block.TicketGateBlock;
import io.wifi.starrailexpress.content.block.TicketOfficeBlock;
import io.wifi.starrailexpress.content.block.ToggleableFacingLightBlock;
import io.wifi.starrailexpress.content.block.TrimmedBedBlock;
import io.wifi.starrailexpress.content.block.VentHatchBlock;
import io.wifi.starrailexpress.content.block.ZiplineBlock;
import io.wifi.starrailexpress.customblock.CustomBlock;
import io.wifi.starrailexpress.customblock.CustomBlockData;
import io.wifi.starrailexpress.customblock.CustomBlockLoader;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.content.block.ChefTrayBlock;
import org.agmas.noellesroles.content.block.DevilRouletteTable;
import org.agmas.noellesroles.content.block.IdentityScannerBlock;
import org.agmas.noellesroles.content.block.LotteryMachineBlock;
import org.agmas.noellesroles.content.block.SREPlushBlock;
import org.agmas.noellesroles.content.block.SupplyCrateBlock;
import org.agmas.noellesroles.content.block.VendingMachinesBlock;

import java.util.Set;
import org.agmas.noellesroles.content.block.scene.CellarBlock;
import org.agmas.noellesroles.content.block.scene.StoveBlock;
import org.agmas.noellesroles.content.block.scene.TrashCanBlock;
import org.agmas.noellesroles.content.block.scene.WaterValveBlock;

/**
 * <b>准心右侧的方块交互提示</b>：鼠标指向本模组的可交互方块时，
 * 在准心右侧显示「按键 + 动作」（例如「右键 坐上」），下面一行是方块名。
 *
 * <h2>为什么要有它</h2>
 * 本模组有大量「看起来只是装饰、其实右键有功能」的方块（餐盘、马桶、沙发、检票门、
 * 售货机、私密玻璃……），光靠外观分辨不出能不能点。这里把交互意图直接贴在准心上，
 * 玩家不用逐个试错。
 *
 * <h2>画法（{@code docs/ui_style.md}）</h2>
 * <ul>
 * <li><b>背景透明</b>：不画卡片底，只有「按键胶囊的 1px 描边 + 黄铜字 + 硬阴影」，
 * 叠在场景上也不挡视线（同 {@code SkillCastAnnounceHud} 的克制做法）。</li>
 * <li><b>键位取真实按键</b>：胶囊里画的是「使用 / 放置方块」键的译文
 * （{@code options.keyUse}），玩家改键后提示跟着变，不写死「右键」。</li>
 * <li><b>只认本模组的方块</b>：先按命名空间过滤（见 {@link #OWN_NAMESPACES}），
 * 再做 {@code instanceof} 归类，所以原版箱子、原版按钮不会被误认。</li>
 * </ul>
 *
 * <h2>动画</h2>
 * 可见度用「指数趋近时间常数」而不是一次性插值：出现、消失、以及扫视时反复进出
 * 都是同一条曲线上的点，因此不会在边界上跳变或重播入场动画。入场额外有 6px 的滑入，
 * 换目标只让文字淡一档再回来；胶囊描边用 360ms 正弦呼吸（全屏唯一一处脉冲）。
 *
 * <p>
 * 必须走 {@link io.wifi.utils.client.betterrender.FakeHudRenderCallback}
 * （见 {@code SREClient} 的注册处），否则文字会掉出 {@code OptimizedTextRenderer}
 * 的帧生命周期外，表现为文字偶尔消失。
 */
@Environment(EnvType.CLIENT)
public final class BlockInteractionHintHud {

    /** 只有这些命名空间下的方块算「本模组」，其中 {@code customblock} 是自定义方块。 */
    private static final Set<String> OWN_NAMESPACES = Set.of(
            "starrailexpress", "trainmurdermystery", "noellesroles", "customblock");

    /** 准心中心到提示左边缘的距离。 */
    private static final int CROSSHAIR_GAP = 14;
    /** 按键胶囊左右内边距。 */
    private static final int BADGE_PAD_X = 4;
    /** 按键胶囊最小高度（字体变大时按行高撑开）。 */
    private static final int BADGE_H = 13;
    /** 胶囊与动作文字之间的间距。 */
    private static final int KEY_GAP = 5;
    /** 第一行与第二行（方块名）之间的间距。 */
    private static final int LINE_GAP = 2;
    /** 出现时的水平位移量。 */
    private static final int SLIDE_PX = 6;
    /** 可见度趋近的时间常数（毫秒）：约 270ms 到达 95%，符合 ui_style §6 的 ≤0.4s。 */
    private static final float EASE_MS = 90.0F;
    /** 换目标时文字重新变亮的时长（毫秒）。 */
    private static final long SWAP_MS = 130L;
    /** 呼吸周期（毫秒，见 ui_style §6 的约 360ms）。 */
    private static final long BREATH_MS = 360L;

    // ── 动画状态 ───────────────────────────────────────────────────────────
    /** 当前指向的方块 id（null 表示还没有过目标）。 */
    private static ResourceLocation targetId;
    private static String targetAction = "";
    /** 是否有目标（决定可见度的趋近方向）。 */
    private static boolean targeted;
    /** 当前可见度 0~1。 */
    private static float visibility;
    /** 上一次推进可见度的时间，用于按真实毫秒算趋近比例。 */
    private static long lastAnimMs;
    /** 动作文字变化的时间（轻度重亮用）。 */
    private static long swapAt;

    // ── 归类缓存（instanceof 链没必要每帧走一遍）────────────────────────────
    private static Block cachedBlock;
    private static String cachedAction;
    // ── 文案缓存（按键名 / 方块名每帧重建会白白造对象）──────────────────────
    private static Object cachedKeyMapping;
    private static Component cachedKeyText;
    private static Block cachedNameBlock;
    private static Component cachedNameText;
    /** 自定义方块的名字按坐标取配置，和方块本身不是一对一，单独缓存。 */
    private static String cachedCustomName;
    private static Component cachedCustomNameText;

    private BlockInteractionHintHud() {
    }

    /** 是否开启（客户端设置，默认开）。 */
    private static boolean configEnabled() {
        SREClientConfig config = SREClientConfig.instance();
        return config == null || config.blockInteractionHint;
    }

    /** 每帧绘制（由 {@code SREClient} 的 FakeHudRenderCallback 调用）。 */
    public static void render(FakeGuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.options.hideGui) {
            reset();
            return;
        }
        Block block = null;
        String action = null;
        BlockPos pos = null;
        Level level = client.level;
        if (!(client.screen != null
                || !client.options.getCameraType().isFirstPerson()
                || client.player.isSpectator()
                || ScopeOverlayRenderer.shouldHideCrosshair()
                || OpeningPresentationCoordinator.shouldSuppressGameplayHud())
                && configEnabled()) {
            HitResult hit = client.hitResult;
            if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos hitPos = blockHit.getBlockPos();
                if (level.isLoaded(hitPos)) {
                    BlockState state = level.getBlockState(hitPos);
                    String resolved = actionKey(state.getBlock(), level, hitPos);
                    // 命中则有目标；归类为 null 时仍要把状态机推到「没有目标」
                    if (resolved != null) {
                        block = state.getBlock();
                        action = resolved;
                        pos = hitPos;
                    }
                }
            }
        }

        long now = Util.getMillis();
        applyTarget(block, action, now);
        // 无论有没有目标都要推进，否则隐藏期间的透明度会冻住、下次出现时跳变
        float vis = advanceVisibility(now);
        if (block == null || action == null || vis <= 0.01F) {
            return;
        }
        draw(graphics, client, block, action, pos, now, vis);
    }

    // ══════════════════════════════════════════════════════════════════
    // 1. 目标与动画状态
    // ══════════════════════════════════════════════════════════════════

    /**
     * 记录当前目标；换了方块 / 换了动作时只做轻度重亮，不重播入场。
     *
     * @param block  当前指向的方块（null = 没有可提示目标）
     * @param action 动作键（null = 没有可提示目标）
     */
    private static void applyTarget(Block block, String action, long now) {
        if (block == null || action == null) {
            targeted = false;
            return;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (targeted && (!id.equals(targetId) || !action.equals(targetAction))) {
            swapAt = now;
        }
        if (!targeted) {
            // 从隐藏重新出现：此时可见度按同一条曲线自己涨回去，无需重置时间戳
            swapAt = now;
        }
        targeted = true;
        targetId = id;
        targetAction = action;
    }

    private static void reset() {
        targeted = false;
        visibility = 0.0F;
        lastAnimMs = 0L;
        targetId = null;
        targetAction = "";
        swapAt = 0L;
    }

    /**
     * 按真实毫秒把可见度推向 0 / 1（指数趋近）。
     *
     * <p>
     * 用「按时间算比例」而不是「每次乘固定系数」：帧率变化时观感一致；
     * 反复进出同一个方块时也自然从当前透明度接回去，不会闪。
     */
    private static float advanceVisibility(long now) {
        if (lastAnimMs == 0L) {
            lastAnimMs = now;
        }
        long deltaMs = Math.max(0L, Math.min(now - lastAnimMs, 200L));
        lastAnimMs = now;
        float target = targeted ? 1.0F : 0.0F;
        if (deltaMs > 0L) {
            float factor = 1.0F - (float) Math.exp(-deltaMs / EASE_MS);
            visibility += (target - visibility) * factor;
            if (Math.abs(target - visibility) <= 0.002F) {
                visibility = target;
            }
        }
        return Mth.clamp(visibility, 0.0F, 1.0F);
    }

    /** 换目标时的短暂重亮：文字从 45% 亮度回到满亮，避免整块重播的闪烁。 */
    private static int textAlpha(long now, float vis) {
        float swap = 0.45F + 0.55F * easeOutCubic((now - swapAt) / (float) SWAP_MS);
        return Mth.clamp((int) (vis * swap * 255.0F), 0, 255);
    }

    // ══════════════════════════════════════════════════════════════════
    // 2. 绘制
    // ══════════════════════════════════════════════════════════════════

    private static void draw(FakeGuiGraphics graphics, Minecraft client, Block block, String action,
            BlockPos pos, long now, float vis) {
        int alpha = textAlpha(now, vis);
        if (alpha <= 0) {
            return;
        }
        Font font = client.font;
        int width = graphics.guiWidth();
        int centerX = width / 2;
        int centerY = graphics.guiHeight() / 2;

        Component keyText = keyText(client);
        Component actionText = UiText.of("hud.sre.interaction_hint." + action);
        Component nameText = nameText(client.level, block, pos);

        int badgeW = font.width(keyText) + BADGE_PAD_X * 2;
        int badgeH = Math.max(BADGE_H, font.lineHeight + 4);
        // 右边界：提示整体不许出屏（留 4px 呼吸）
        int rightLimit = width - 4;
        // 文字可用宽度：扣掉胶囊、间距与准心左侧的避让
        int textLimit = Math.max(24, rightLimit - (centerX + CROSSHAIR_GAP) - badgeW - KEY_GAP);

        Component shownName = nameText;
        if (font.width(nameText) > textLimit) {
            shownName = Component.literal(clip(font, nameText.getString(), textLimit - font.width("…")) + "…");
        }
        Component shownAction = actionText;
        if (font.width(actionText) > textLimit) {
            shownAction = Component.literal(clip(font, actionText.getString(), textLimit));
        }

        int textW = Math.max(font.width(shownAction), font.width(shownName));
        int totalW = badgeW + KEY_GAP + textW;

        // 入场：从准心侧滑出一点
        int slide = Math.round(-SLIDE_PX * (1.0F - vis));
        int x = centerX + CROSSHAIR_GAP + slide;
        int overflow = (x + totalW) - rightLimit;
        if (overflow > 0) {
            x -= overflow;
        }
        x = Math.max(x, centerX + 8);

        // 第一行（按键 + 动作）垂直居中压在准心线上，方块名挂在它下面一行
        int rowTop = centerY - badgeH / 2;
        int textX = x + badgeW + KEY_GAP;
        int actionY = rowTop + (badgeH - font.lineHeight) / 2;
        int nameY = rowTop + badgeH + LINE_GAP;

        // 准心 → 胶囊的细引线（半透明黄铜），把提示「挂」在准心上
        int lineAlpha = Mth.clamp((int) (alpha * 0.45F * breath(now)), 0, 255);
        if (lineAlpha > 0 && x - 4 > centerX + 6) {
            graphics.fill(centerX + 6, centerY, x - 4, centerY + 1,
                    SRETheme.withAlpha(SRETheme.BRASS, lineAlpha));
        }

        // 按键胶囊：1px 描边、内部透明
        int borderAlpha = Mth.clamp((int) (alpha * breath(now)), 0, 255);
        badgeOutline(graphics, x, rowTop, badgeW, badgeH,
                SRETheme.withAlpha(SRETheme.BRASS, borderAlpha));
        graphics.drawString(font, keyText, x + BADGE_PAD_X, actionY,
                SRETheme.withAlpha(SRETheme.BRASS_BRIGHT, alpha), true);

        // 动作（主视觉，最亮）与方块名（次要）
        graphics.drawString(font, shownAction, textX, actionY,
                SRETheme.withAlpha(SRETheme.TITLE, alpha), true);
        graphics.drawString(font, shownName, textX, nameY,
                SRETheme.withAlpha(SRETheme.MUTED, alpha), true);
    }

    /** 1px 描边（微圆角胶囊）：只描边、不填底，保证背景透明。 */
    private static void badgeOutline(FakeGuiGraphics graphics, int x, int y, int w, int h, int color) {
        if (w <= 2 || h <= 2) {
            return;
        }
        int x2 = x + w;
        int y2 = y + h;
        graphics.fill(x + 1, y, x2 - 1, y + 1, color);
        graphics.fill(x + 1, y2 - 1, x2 - 1, y2, color);
        graphics.fill(x, y + 1, x + 1, y2 - 1, color);
        graphics.fill(x2 - 1, y + 1, x2, y2 - 1, color);
    }

    /** 360ms 正弦呼吸（0.65~1.0）。 */
    private static float breath(long now) {
        double phase = (now % BREATH_MS) / (double) BREATH_MS * Math.PI * 2.0;
        return 0.65F + 0.35F * (float) ((Math.sin(phase) + 1.0) * 0.5);
    }

    /** 按宽度裁字（优先在空格处断开，中文逐字裁）。 */
    private static String clip(Font font, String text, int maxWidth) {
        if (maxWidth <= 0 || text.isEmpty()) {
            return "";
        }
        int width = 0;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int advance = font.width(String.valueOf(c));
            if (width + advance > maxWidth) {
                return out.toString().stripTrailing();
            }
            out.append(c);
            width += advance;
        }
        return out.toString();
    }

    private static float easeOutCubic(float t) {
        float f = 1.0F - Mth.clamp(t, 0.0F, 1.0F);
        return 1.0F - f * f * f;
    }

    // ══════════════════════════════════════════════════════════════════
    // 3. 文案缓存（避免每帧构造 Component）
    // ══════════════════════════════════════════════════════════════════

    /** 按键名：只在「使用键」真的改了之后重建。 */
    private static Component keyText(Minecraft client) {
        var mapping = client.options.keyUse;
        if (mapping != cachedKeyMapping || cachedKeyText == null) {
            cachedKeyMapping = mapping;
            cachedKeyText = mapping.getTranslatedKeyMessage();
        }
        return cachedKeyText;
    }

    /**
     * 方块名：同一个方块只取一次（{@code getName()} 每次都新建组件）。
     *
     * <p>
     * 自定义方块在世界里统一叫「自定义方块」（{@code block.getName()} 读的是通用翻译键），
     * 所以这里按坐标取配置里的显示名，玩家看到的才是自己起的名字。
     */
    private static Component nameText(Level level, Block block, BlockPos pos) {
        if (block instanceof CustomBlock) {
            CustomBlockData data = pos == null || level == null ? null : CustomBlockLoader.getDataAt(level, pos);
            if (data != null && data.displayName != null && !data.displayName.isBlank()) {
                if (!data.displayName.equals(cachedCustomName)) {
                    cachedCustomName = data.displayName;
                    cachedCustomNameText = Component.literal(data.displayName);
                }
                return cachedCustomNameText;
            }
        }
        if (block != cachedNameBlock || cachedNameText == null) {
            cachedNameBlock = block;
            cachedNameText = block.getName();
        }
        return cachedNameText;
    }

    /**
     * 归类方块并返回动作键（{@code hud.sre.interaction_hint.*} 的子键）。
     *
     * <p>
     * 自定义方块要看具体配置：没有右键事件时点它什么也不会发生，提示「交互」是假的。
     *
     * @return 动作键；{@code null} 表示这个方块不需要提示
     */
    private static String actionKey(Block block, Level level, BlockPos pos) {
        if (block instanceof CustomBlock) {
            CustomBlockData data = CustomBlockLoader.getDataAt(level, pos);
            if (data == null || data.eventsOf(CustomBlockData.BlockEventType.RIGHT_CLICK).isEmpty()) {
                return null;
            }
            return "interact";
        }
        if (block == cachedBlock) {
            return cachedAction;
        }
        String resolved = resolveAction(block);
        cachedBlock = block;
        cachedAction = resolved;
        return resolved;
    }

    private static String resolveAction(Block block) {
        // 只认本模组的方块：原版按钮 / 拉杆同样是 ButtonBlock / LeverBlock，必须先过命名空间
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null || !OWN_NAMESPACES.contains(id.getNamespace())) {
            return null;
        }
        // 门类（含检票门）：开关
        if (block instanceof DoorPartBlock || block instanceof TicketGateBlock) {
            return "open_door";
        }
        // 可坐 / 可躺
        if (block instanceof MountableBlock) {
            return "sit";
        }
        if (block instanceof TrimmedBedBlock) {
            return "lie";
        }
        // 餐盘 / 饮料盘 / 厨师托盘：拿取
        if (block instanceof PlatterBlock || block instanceof ChefTrayBlock) {
            return "take";
        }
        // 私密玻璃 / 通风口 / 开关式灯具 / 霓虹灯管与灯柱：切换（霓虹灯任何玩家都能开关）
        if (block instanceof PrivacyBlock || block instanceof VentHatchBlock
                || block instanceof ToggleableFacingLightBlock
                || block instanceof NeonTubeBlock || block instanceof NeonPillarBlock) {
            return "toggle";
        }
        // 按钮 / 拉杆
        if (block instanceof ButtonBlock) {
            return "press";
        }
        if (block instanceof LeverBlock) {
            return "pull";
        }
        if (block instanceof HornBlock) {
            return "horn";
        }
        if (block instanceof ZiplineBlock) {
            return "ride";
        }
        if (block instanceof SREPlushBlock) {
            return "squeeze";
        }
        // 场景方块：炉子点火、垃圾桶销毁物品、水阀点一下就用，都没有界面；地窖舱门是开合，按门算
        if (block instanceof CellarBlock) {
            return "open_door";
        }
        if (block instanceof StoveBlock || block instanceof TrashCanBlock || block instanceof WaterValveBlock) {
            return "interact";
        }
        // 实体交互面板、任务方块：点得到，但具体做什么取决于配置 / 身份，
        // 所以写「交互」而不写「打开」（写「打开」会让人以为一定有界面）。
        // 自定义方块前面已按配置单独判定，这里不再列。
        if (block instanceof EntityInteractionBlock || block instanceof EntityInteractionPanelBlock
                || block instanceof MinigameQuestBlock || block instanceof MinigameQuestPanelBlock) {
            return "interact";
        }
        // 打开界面的机器：普通玩家点得到、点开就是界面
        if (block instanceof VendingMachinesBlock || block instanceof LotteryMachineBlock
                || block instanceof SupplyCrateBlock || block instanceof IdentityScannerBlock
                || block instanceof TicketOfficeBlock || block instanceof SecurityMonitorBlock
                || block instanceof DevilRouletteTable) {
            return "open";
        }
        // 展示方块 / 特效生成器之类只对「创造 + 权限」开放编辑，给普通玩家提示「打开」是骗人的
        return null;
    }
}
