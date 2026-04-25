package io.wifi.starrailexpress.content.vote;

import io.wifi.starrailexpress.network.NetworkHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 客户端投票界面。
 * <p>
 * 由服务端发送 {@link OpenVoteScreenS2CPayload} 触发打开；
 * 服务端发送 {@link CloseVoteScreenS2CPayload} 触发关闭。
 */
@Environment(EnvType.CLIENT)
public class VoteScreen extends Screen {

    // ── 布局常量 ──────────────────────────────────────────────────────────────
    private static final int PANEL_W      = 380;
    private static final int HDR_H        = 44;
    private static final int OPTION_H     = 44;
    private static final int OPTION_GAP   = 4;
    private static final int FOOTER_H     = 0;
    private static final int PADDING      = 10;

    // ── 颜色 ──────────────────────────────────────────────────────────────────
    private static final int BG           = 0xCC080E1C;
    private static final int HDR_BG       = 0xEE060B16;
    private static final int DIVIDER      = 0x55FFFFFF;
    private static final int OPT_NORMAL   = 0x991A2038;
    private static final int OPT_HOVER    = 0xBB1D3060;
    private static final int OPT_VOTED    = 0xCC163468;
    private static final int OPT_DISABLED = 0x881A2038;
    private static final int ACCENT_GOLD  = 0xFFFFAA00;
    private static final int ACCENT_BLUE  = 0xFF5599EE;
    private static final int ACCENT_GREEN = 0xFF00DD88;
    private static final int BAR_BG       = 0x44FFFFFF;
    private static final int BAR_FG       = 0x99FFAA00;
    private static final int TEXT_WHITE   = 0xFFFFFFFF;
    private static final int TEXT_GRAY    = 0xFFAAAAAA;
    private static final int TEXT_DIM     = 0xFF556688;

    // ── 数据 ──────────────────────────────────────────────────────────────────
    private VoteClientState state;
    private int panelX, panelY, panelW, panelH;

    // ── 动画 ──────────────────────────────────────────────────────────────────
    private int openTick = 0;
    private static final int OPEN_TICKS = 12;
    private final float[] optHoverAnims;
    /** 投票反馈提示计时器 */
    private int feedbackTimer = 0;
    private String feedbackText = "";
    private int feedbackColor = ACCENT_GREEN;

    public VoteScreen(VoteClientState state) {
        super(Component.translatable("gui.sre.vote.title"));
        this.state = state;
        this.optHoverAnims = new float[state.options.size()];
    }

    // ── 布局 ──────────────────────────────────────────────────────────────────

    private void computeLayout() {
        int optionCount = state.options.size();
        int contentH = HDR_H + optionCount * (OPTION_H + OPTION_GAP) + PADDING;
        panelW = Math.min(PANEL_W, this.width - 20);
        panelH = Math.min(contentH, this.height - 20);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
    }

    // ── 初始化 ────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        clearWidgets();
        computeLayout();
        initOptionButtons();
    }

    private void initOptionButtons() {
        int optionCount = state.options.size();
        for (int i = 0; i < optionCount; i++) {
            final int idx = i;
            VoteOption opt = state.options.get(i);
            boolean alreadyVoted = (state.myVote == i);
            boolean canVote = state.allowRevote || state.myVote < 0;

            int bx = panelX + PADDING;
            int by = panelY + HDR_H + i * (OPTION_H + OPTION_GAP);
            int bw = panelW - PADDING * 2;
            int bh = OPTION_H;

            if (canVote || alreadyVoted) {
                // 左侧图标区 (20px) + 文字其余部分
                addRenderableWidget(
                    ModernButton.builder(Component.empty(), btn -> {
                        if (canVote) submitVote(idx);
                    })
                    .bounds(bx, by, bw, bh)
                    .accentBar(isMyVote ? ModernButton.AccentSide.LEFT : new ModernButton.AccentSide[0])
                    .accentColor(ACCENT_GOLD)
                    .build()
                );
            }
        }
    }

    private void submitVote(int optionIndex) {
        VoteClientState s = state;
        if (s == null) return;
        boolean canVote = s.allowRevote || s.myVote < 0;
        if (!canVote) return;
        NetworkHandler.sendToServer(new VoteSubmitC2SPayload(s.sessionId, optionIndex));
        s.myVote = optionIndex;
        // 重建按钮（更新高亮状态）
        clearWidgets();
        initOptionButtons();
        feedbackTimer = 60;
        feedbackText = Component.translatable("gui.sre.vote.voted",
                s.options.get(optionIndex).getLabel()).getString();
        feedbackColor = ACCENT_GREEN;
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (openTick < OPEN_TICKS) openTick++;
        if (feedbackTimer > 0) feedbackTimer--;

        // 同步最新状态（可能被网络包异步更新了）
        VoteClientState latest = VoteClientState.current;
        if (latest == null) {
            // 投票已结束，关闭界面
            onClose();
            return;
        }
        if (latest != state) {
            state = latest;
            clearWidgets();
            computeLayout();
            initOptionButtons();
        }
    }

    // ── 渲染 ──────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        float alpha = openTick < OPEN_TICKS ? (float) openTick / OPEN_TICKS : 1f;
        if (alpha < 0.02f) { super.render(g, mouseX, mouseY, delta); return; }

        // 背景面板
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, BG);
        // 顶部标题栏
        g.fill(panelX, panelY, panelX + panelW, panelY + HDR_H, HDR_BG);
        // 分隔线
        g.fill(panelX, panelY + HDR_H - 1, panelX + panelW, panelY + HDR_H, DIVIDER);

        renderHeader(g, delta);
        renderOptions(g, mouseX, mouseY, delta);
        renderFeedback(g);

        super.render(g, mouseX, mouseY, delta);
    }

    private void renderHeader(GuiGraphics g, float delta) {
        // 标题
        String titleText = state.title;
        int tx = panelX + PADDING;
        int ty = panelY + (HDR_H - font.lineHeight) / 2;
        g.drawString(font, titleText, tx, ty, ACCENT_GOLD, false);

        // 倒计时 / 剩余时间
        if (state.endTimeMillis > 0) {
            long remaining = state.endTimeMillis - System.currentTimeMillis();
            if (remaining < 0) remaining = 0;
            int secs = (int) (remaining / 1000);
            String timerStr = secs + "s";
            int timerColor = secs <= 10 ? 0xFFFF4444 : TEXT_GRAY;
            int tw = font.width(timerStr);
            g.drawString(font, timerStr, panelX + panelW - tw - PADDING, ty, timerColor, false);
        }

        // 总票数
        if (state.showResults) {
            int total = state.voteCounts.values().stream().mapToInt(Integer::intValue).sum();
            String countStr = Component.translatable("gui.sre.vote.total_votes", total).getString();
            int cw = font.width(countStr);
            int cx = panelX + panelW - cw - PADDING;
            int cy = panelY + HDR_H - font.lineHeight - 4;
            if (state.endTimeMillis <= 0) {
                g.drawString(font, countStr, cx, cy, TEXT_DIM, false);
            }
        }
    }

    private void renderOptions(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int totalVotes = state.showResults
                ? state.voteCounts.values().stream().mapToInt(Integer::intValue).sum()
                : 0;

        for (int i = 0; i < state.options.size(); i++) {
            VoteOption opt = state.options.get(i);
            boolean isMyVote = (state.myVote == i);
            int bx = panelX + PADDING;
            int by = panelY + HDR_H + i * (OPTION_H + OPTION_GAP);
            int bw = panelW - PADDING * 2;

            // 悬浮动画
            boolean hovered = mouseX >= bx && mouseX < bx + bw
                    && mouseY >= by && mouseY < by + OPTION_H;
            float targetHover = hovered ? 1f : 0f;
            optHoverAnims[i] = Mth.lerp(0.25f, optHoverAnims[i], targetHover);

            // 背景色（按钮组件自己绘制背景，这里不再绘制）

            int textX = bx + PADDING + (opt.getType() == VoteOption.Type.ITEM ? 20 : 0);
            int textY = by + (OPTION_H - font.lineHeight) / 2;

            // ITEM 类型：渲染物品图标
            if (opt.getType() == VoteOption.Type.ITEM && !opt.getItem().isEmpty()) {
                g.renderFakeItem(opt.getItem(), bx + PADDING - 2, by + (OPTION_H - 16) / 2);
            }

            // PLAYER 类型：渲染玩家头颅图标
            if (opt.getType() == VoteOption.Type.PLAYER && opt.getPlayerUuid() != null) {
                ItemStack skull = buildSkullItem(opt);
                g.renderFakeItem(skull, bx + PADDING - 2, by + (OPTION_H - 16) / 2);
                textX = bx + PADDING + 20;
            }

            // 选项标签
            String label = opt.getLabel();
            int labelColor = isMyVote ? ACCENT_GOLD : TEXT_WHITE;
            if (isMyVote) {
                g.drawString(font, "✔ " + label, textX, textY, labelColor, false);
            } else {
                g.drawString(font, label, textX, textY, labelColor, false);
            }

            // 票数进度条（仅在 showResults=true 时显示）
            if (state.showResults) {
                int votes = state.voteCounts.getOrDefault(i, 0);
                float ratio = totalVotes > 0 ? (float) votes / totalVotes : 0f;
                int barX = textX + font.width(label) + 8;
                if (isMyVote) barX += font.width("✔ ") - font.width(label) + font.width("✔ " + label) + 8 - font.width(label) - 8;
                // 简化：使用右侧固定区域
                int barRight = bx + bw - PADDING;
                int barY = by + OPTION_H - 9;
                int barH = 5;
                int barW = barRight - (bx + PADDING);
                if (barW > 0) {
                    g.fill(bx + PADDING, barY, bx + PADDING + barW, barY + barH, BAR_BG);
                    int fill = (int) (barW * ratio);
                    if (fill > 0) {
                        g.fill(bx + PADDING, barY, bx + PADDING + fill, barY + barH,
                                isMyVote ? ACCENT_GOLD : BAR_FG);
                    }
                    // 票数文字
                    String voteStr = String.valueOf(votes);
                    g.drawString(font, voteStr, barRight - font.width(voteStr), barY - font.lineHeight - 1,
                            TEXT_DIM, false);
                }
            }
        }
    }

    private void renderFeedback(GuiGraphics g) {
        if (feedbackTimer <= 0 || feedbackText.isEmpty()) return;
        float alpha2 = Math.min(1f, feedbackTimer / 20f);
        int color = (feedbackColor & 0x00FFFFFF) | ((int) (0xFF * alpha2) << 24);
        int tw = font.width(feedbackText);
        g.drawString(font, feedbackText, (this.width - tw) / 2, panelY + panelH + 6, color, false);
    }

    private ItemStack buildSkullItem(VoteOption opt) {
        ItemStack skull = new ItemStack(Items.PLAYER_HEAD);
        // UUID 可以在客户端通过皮肤系统获取头颅纹理，这里仅作简单展示
        return skull;
    }

    // ── 关闭 ──────────────────────────────────────────────────────────────────

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        super.onClose();
    }

    /** 外部（网络包）调用此方法刷新显示状态 */
    public void refreshState(VoteClientState newState) {
        this.state = newState;
        clearWidgets();
        computeLayout();
        initOptionButtons();
    }

    // ── 辅助 ──────────────────────────────────────────────────────────────────

    /** 将 VoteClientState 刷新后若界面已打开则同步更新 */
    public static void tryRefreshOpenScreen(VoteClientState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof VoteScreen vs) {
            vs.refreshState(state);
        }
    }

    /** 若界面打开则关闭 */
    public static void tryClose(String sessionId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof VoteScreen) {
            mc.execute(() -> mc.setScreen(null));
        }
    }

    /**
     * 用于显示最终结果的简单覆盖文本列表（在界面关闭前短暂显示），
     * 目前实现为日志输出，可扩展为 HUD 通知。
     */
    public static void showFinalResults(List<String> lines) {
        // 可以在此添加一个短暂的 HUD 通知
    }
}
