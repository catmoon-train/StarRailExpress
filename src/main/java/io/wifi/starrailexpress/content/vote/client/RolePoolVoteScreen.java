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

package io.wifi.starrailexpress.content.vote.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics;
import io.wifi.starrailexpress.client.ui.SRETheme;
import io.wifi.starrailexpress.content.vote.VoteOption;
import io.wifi.starrailexpress.content.vote.network.VoteCastC2SPacket;
import io.wifi.starrailexpress.content.vote.network.VoteSyncS2CPacket;
import io.wifi.starrailexpress.roster.pool.RolePool;
import io.wifi.starrailexpress.roster.pool.RolePoolRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * 职业池投票界面（地图投票之前的一步）。
 *
 * <p>
 * 左侧是池子列表（编号 / 票数 / 已选标记），右侧是当前池子的职业一览：按
 * {@link io.wifi.starrailexpress.api.RoleTeam} 阵营分组、逐句换行、可滚动。
 *
 * <p>
 * 每人可选 {@code ClientVoteCache.getMaxSelectCount()} 个池子：点击行切换选中，
 * <b>Enter / 空格</b>提交。池子内容不额外走网络包——界面按选项的
 * {@code resultId}（{@code role_pool:<id>}）在客户端自己解析
 * （{@link RolePoolRegistry}），拿不到时回退到选项自带的说明文字。
 */
public final class RolePoolVoteScreen extends Screen {
    private static final int ROW_H = 31;
    private static final int HEADER_H = 28;
    private static final int DETAIL_LINE_H = 11;

    private int focusIndex;
    private int hoveredIndex = -1;
    private float scroll;
    private float scrollTarget;
    private int detailScroll;
    private long openedAt;
    private long focusChangedAt;
    /** 详情行缓存键（焦点 + 面板宽度），避免每帧重排长文本。 */
    private int detailCacheKey = -1;
    private List<FormattedCharSequence> detailCache = List.of();
    /** 本机当前勾选的池子（提交前的临时状态，顺序即显示的选择序号）。 */
    private final LinkedHashSet<Integer> selection = new LinkedHashSet<>();

    public RolePoolVoteScreen() {
        super(Component.translatable("gui.sre.role_pool_vote.title"));
    }

    public void updateData(VoteSyncS2CPacket packet) {
        // 增量同步包不带选项列表，拿它算边界会把焦点压回第一项
        int size = ClientVoteCache.getOptions().size();
        if (size > 0) {
            focusIndex = Mth.clamp(focusIndex, 0, size - 1);
        }
    }

    @Override
    protected void init() {
        openedAt = System.currentTimeMillis();
        focusChangedAt = openedAt;
        // 模式投票 → 职业池投票的过场（如果刚才是模式投票）
        VoteFlowTransition.beginIfArmed();
        selection.clear();
        selection.addAll(ClientVoteCache.getSelectedIndices());
        if (!selection.isEmpty()) {
            focusIndex = Mth.clamp(selection.iterator().next(), 0,
                    Math.max(0, ClientVoteCache.getOptions().size() - 1));
        }
        detailCacheKey = -1;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        VoteFlowFrame.renderBackground(g, width, height);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        VoteFlowFrame.Bounds b = VoteFlowFrame.layout(width, height);
        VoteFlowFrame.renderProgress(g, font, b, VoteFlowFrame.STEP_ROLE_POOL, null,
                ClientVoteCache.getRemainingSeconds());

        float intro = VoteFlowFrame.ease((System.currentTimeMillis() - openedAt) / 360.0F);
        int contentY = b.y() + 58;
        int contentH = b.h() - 72;
        int listW = Mth.clamp((int) (b.w() * 0.42F), 180, 320);
        int gap = 14;
        int detailX = b.x() + listW + gap;
        int detailW = b.w() - listW - gap;
        int slide = (int) ((1.0F - intro) * 22.0F);
        VoteFlowFrame.panel(g, b.x() - slide, contentY, listW, contentH, 238);
        VoteFlowFrame.panel(g, detailX + slide, contentY, detailW, contentH, 220);

        renderBoard(g, mouseX, mouseY, b.x() - slide, contentY, listW, contentH);
        renderDetail(g, detailX + slide, contentY, detailW, contentH);
        VoteFlowTransition.render(g, width, height);
    }

    // ── 左：池子列表 ────────────────────────────────────────────

    private void renderBoard(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
        List<VoteOption> options = ClientVoteCache.getOptions();
        g.drawString(font,
                Component.translatable("gui.sre.role_pool_vote.list_header").withStyle(ChatFormatting.BOLD),
                x + 11, y + 10, VoteFlowFrame.TEXT, false);
        g.drawString(font, Component.translatable("gui.sre.vote_flow.votes"), x + w - 52, y + 10,
                VoteFlowFrame.MUTED, false);
        g.fill(x + 9, y + HEADER_H - 1, x + w - 9, y + HEADER_H,
                VoteFlowFrame.withAlpha(SRETheme.BORDER, 0x33));

        int viewportY = y + HEADER_H;
        int viewportH = h - HEADER_H - 8;
        int contentH = options.size() * ROW_H;
        int maxScroll = Math.max(0, contentH - viewportH);
        scrollTarget = Mth.clamp(scrollTarget, 0, maxScroll);
        scroll = Mth.lerp(0.22F, scroll, scrollTarget);
        hoveredIndex = -1;
        g.enableScissor(x + 1, viewportY, x + w - 1, viewportY + viewportH);
        for (int i = 0; i < options.size(); i++) {
            int rowY = viewportY + i * ROW_H - Math.round(scroll);
            float rowIn = VoteFlowFrame.ease((System.currentTimeMillis() - openedAt - i * 34L) / 280.0F);
            int rowShift = Math.round((1.0F - rowIn) * 18.0F);
            boolean hover = mouseX >= x + 5 && mouseX < x + w - 5 && mouseY >= rowY && mouseY < rowY + ROW_H
                    && mouseY >= viewportY && mouseY < viewportY + viewportH;
            boolean selected = selection.contains(i);
            boolean focused = i == focusIndex;
            if (hover) {
                hoveredIndex = i;
            }
            int rowAlpha = Math.round(255.0F * rowIn);
            if (selected) {
                int pulse = 70 + (int) (Math.sin(System.currentTimeMillis() / 220.0 + i) * 18.0);
                g.fillGradient(x + 5 + rowShift, rowY + 2, x + w - 5, rowY + ROW_H - 2,
                        VoteFlowFrame.withAlpha(SRETheme.SELECTED_TOP, Math.min(rowAlpha, pulse)),
                        VoteFlowFrame.withAlpha(SRETheme.SELECTED_BOTTOM, Math.min(rowAlpha, 42)));
            } else if (hover || focused) {
                g.fill(x + 5 + rowShift, rowY + 2, x + w - 5, rowY + ROW_H - 2,
                        VoteFlowFrame.withAlpha(0xFFFFFFFF, Math.min(rowAlpha, 34)));
            }
            int nodeColor = selected ? VoteFlowFrame.GOLD : focused ? VoteFlowFrame.GOLD_DIM : SRETheme.CARD_BORDER;
            g.fill(x + 15 + rowShift, rowY, x + 16 + rowShift, rowY + ROW_H,
                    VoteFlowFrame.withAlpha(selected ? SRETheme.GOLD : SRETheme.CARD_BORDER,
                            Math.min(rowAlpha, selected ? 153 : 51)));
            g.fill(x + 12 + rowShift, rowY + 11, x + 19 + rowShift, rowY + 18,
                    VoteFlowFrame.withAlpha(nodeColor, rowAlpha));
            g.drawString(font, String.format("%02d", i + 1), x + 26 + rowShift, rowY + 11,
                    VoteFlowFrame.withAlpha(VoteFlowFrame.MUTED, rowAlpha), false);

            int reserved = ClientVoteCache.isShowResults() ? 78 : 52;
            Component name = rowName(options.get(i));
            String clipped = MapUiGraphics.clip(font, name.getString(), Math.max(36, w - reserved));
            g.drawString(font, clipped, x + 50 + rowShift, rowY + 11,
                    VoteFlowFrame.withAlpha(selected ? VoteFlowFrame.TEXT : SRETheme.BODY, rowAlpha), false);

            if (ClientVoteCache.isShowResults()) {
                String votes = String.valueOf(ClientVoteCache.getResults().getOrDefault(i, 0));
                g.drawString(font, votes, x + w - 20 - font.width(votes), rowY + 11,
                        selected ? VoteFlowFrame.GOLD : VoteFlowFrame.MUTED, false);
            }
            int pick = selectionOrder(i);
            if (pick > 0) {
                String badge = String.valueOf(pick);
                g.drawString(font, badge, x + w - (ClientVoteCache.isShowResults() ? 40 : 24) - font.width(badge),
                        rowY + 11, VoteFlowFrame.GOLD, false);
            }
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int thumbH = Math.max(18, viewportH * viewportH / contentH);
            int thumbY = viewportY + Math.round((viewportH - thumbH) * scroll / maxScroll);
            g.fill(x + w - 4, viewportY, x + w - 2, viewportY + viewportH, 0x33000000);
            g.fill(x + w - 4, thumbY, x + w - 2, thumbY + thumbH, VoteFlowFrame.GOLD_DIM);
        }
    }

    // ── 右：池子内容 ────────────────────────────────────────────

    private void renderDetail(GuiGraphics g, int x, int y, int w, int h) {
        List<VoteOption> options = ClientVoteCache.getOptions();
        if (options.isEmpty()) {
            return;
        }
        focusIndex = Mth.clamp(focusIndex, 0, options.size() - 1);
        float detailIn = VoteFlowFrame.ease((System.currentTimeMillis() - focusChangedAt) / 220.0F);
        int detailAlpha = Math.round(255.0F * detailIn);
        int detailShift = Math.round((1.0F - detailIn) * 12.0F);

        g.drawString(font, Component.translatable("gui.sre.role_pool_vote.detail_header"), x + 16, y + 13,
                VoteFlowFrame.MUTED, false);
        Component title = rowName(options.get(focusIndex)).copy().withStyle(ChatFormatting.BOLD);
        VoteFlowFrame.scaledCentered(g, font, title, x + w / 2.0F, y + 35 + detailShift,
                Math.min(1.35F, (w - 32.0F) / Math.max(1, font.width(title))),
                VoteFlowFrame.withAlpha(VoteFlowFrame.TEXT, detailAlpha));
        g.fill(x + 14, y + 58, x + w - 14, y + 59,
                VoteFlowFrame.withAlpha(SRETheme.BORDER, detailAlpha));

        int viewportY = y + 66;
        int viewportH = Math.max(10, h - 66 - 30);
        List<FormattedCharSequence> lines = detailLines(options.get(focusIndex), w - 32);
        int contentH = lines.size() * DETAIL_LINE_H;
        int maxScroll = Math.max(0, contentH - viewportH);
        detailScroll = Mth.clamp(detailScroll, 0, maxScroll);
        g.enableScissor(x + 8, viewportY, x + w - 8, viewportY + viewportH);
        for (int i = 0; i < lines.size(); i++) {
            int lineY = viewportY + i * DETAIL_LINE_H - detailScroll;
            if (lineY + DETAIL_LINE_H < viewportY || lineY > viewportY + viewportH) {
                continue;
            }
            g.drawString(font, lines.get(i), x + 16, lineY,
                    VoteFlowFrame.withAlpha(SRETheme.BODY, detailAlpha), false);
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int thumbH = Math.max(16, viewportH * viewportH / contentH);
            int thumbY = viewportY + Math.round((viewportH - thumbH) * detailScroll / (float) maxScroll);
            g.fill(x + w - 4, viewportY, x + w - 2, viewportY + viewportH, 0x33000000);
            g.fill(x + w - 4, thumbY, x + w - 2, thumbY + thumbH, VoteFlowFrame.GOLD_DIM);
            g.drawString(font, Component.translatable("gui.sre.role_pool_vote.scroll_hint"), x + 16,
                    y + h - 34, VoteFlowFrame.MUTED, false);
        }

        Component state;
        int stateColor;
        if (ClientVoteCache.hasVoted()) {
            state = Component.translatable("gui.sre.vote_flow.voted");
            stateColor = VoteFlowFrame.GOLD;
        } else if (selection.isEmpty()) {
            state = Component.translatable("gui.sre.role_pool_vote.hint_select", maxSelect());
            stateColor = VoteFlowFrame.MUTED;
        } else {
            state = Component.translatable("gui.sre.role_pool_vote.hint_confirm", selection.size(), maxSelect());
            stateColor = VoteFlowFrame.GOLD_DIM;
        }
        g.drawCenteredString(font, MapUiGraphics.clip(font, state.getString(), w - 24), x + w / 2, y + h - 18,
                stateColor);
    }

    /** 列表行 / 详情标题的池子名：能解析出池子就用它的名字，否则用选项自带的显示名。 */
    private Component rowName(VoteOption option) {
        RolePool pool = RolePoolRegistry.byResultId(option.resultId());
        if (pool != null) {
            return pool.displayName();
        }
        return option.display() == null ? Component.empty() : option.display();
    }

    /**
     * 详情行：池子内容按阵营分组，组标题 + 以「、」分隔的职业名（职业名沿用阵营色）。
     * 解析不到池子时退化成选项自带的说明文字。
     */
    private List<FormattedCharSequence> detailLines(VoteOption option, int maxWidth) {
        RolePool pool = RolePoolRegistry.byResultId(option.resultId());
        int key = pool == null ? -1 : focusIndex * 8191 + Math.max(1, maxWidth);
        if (key >= 0 && key == detailCacheKey) {
            return detailCache;
        }
        List<FormattedCharSequence> lines = new ArrayList<>();
        if (pool == null) {
            Component description = option.description();
            if (description != null) {
                lines.addAll(font.split(description, maxWidth));
            }
        } else {
            for (RolePoolRegistry.FactionGroup group : RolePoolRegistry.groupsOf(pool)) {
                if (!lines.isEmpty()) {
                    lines.add(FormattedCharSequence.EMPTY);
                }
                lines.addAll(font.split(groupHeader(group), maxWidth));
                lines.addAll(font.split(groupBody(group), maxWidth));
            }
            if (lines.isEmpty()) {
                lines.addAll(font.split(Component.translatable("gui.sre.role_pool_vote.empty"), maxWidth));
            }
        }
        detailCacheKey = key;
        detailCache = lines;
        return lines;
    }

    private Component groupHeader(RolePoolRegistry.FactionGroup group) {
        Component faction = group.team() == null
                ? Component.translatable("gui.sre.role_pool_vote.faction_other")
                : group.team().displayName();
        return Component.translatable("gui.sre.role_pool_vote.faction_count", faction, group.roles().size())
                .withStyle(ChatFormatting.BOLD);
    }

    private Component groupBody(RolePoolRegistry.FactionGroup group) {
        int color = (group.team() == null ? SRETheme.BODY : group.team().color()) & SRETheme.RGB;
        MutableComponent body = Component.empty();
        for (int i = 0; i < group.roles().size(); i++) {
            if (i > 0) {
                body.append(Component.literal("、").withColor(SRETheme.MUTED));
            }
            body.append(group.roles().get(i).getName().copy().withColor(color));
        }
        return body;
    }

    // ── 交互 ────────────────────────────────────────────────────

    private int maxSelect() {
        return Math.max(1, ClientVoteCache.getMaxSelectCount());
    }

    private int selectionOrder(int index) {
        int order = 0;
        for (int picked : selection) {
            order++;
            if (picked == index) {
                return order;
            }
        }
        return 0;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredIndex >= 0) {
            changeFocus(hoveredIndex);
            toggleSelection(hoveredIndex);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void toggleSelection(int index) {
        if (ClientVoteCache.hasVoted()) {
            playClick(0.8F);
            return;
        }
        if (selection.contains(index)) {
            selection.remove(index);
        } else if (selection.size() < maxSelect()) {
            selection.add(index);
        } else {
            // 已选满：保持原选择，仅提示（再点一次同一个池子可取消）
            playClick(0.8F);
            return;
        }
        playClick(1.15F);
    }

    private void submit() {
        if (ClientVoteCache.hasVoted() || selection.isEmpty() || ClientVoteCache.getOptions().isEmpty()) {
            return;
        }
        List<Integer> indices = List.copyOf(selection);
        ClientPlayNetworking.send(new VoteCastC2SPacket(indices));
        ClientVoteCache.onVoteSubmitted(indices);
        playClick(1.0F);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        VoteFlowFrame.Bounds b = VoteFlowFrame.layout(width, height);
        int listW = Mth.clamp((int) (b.w() * 0.42F), 180, 320);
        if (mouseX < b.x() + listW) {
            scrollTarget -= (float) verticalAmount * ROW_H * 1.5F;
        } else {
            detailScroll -= (int) Math.round(verticalAmount * DETAIL_LINE_H * 3);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case 264, 265 -> { // 上 / 下
                moveFocus(keyCode == 264 ? 1 : -1);
                return true;
            }
            case 257, 335, 32 -> { // Enter / 小键盘 Enter / 空格
                submit();
                return true;
            }
            case 266 -> { // PageUp
                detailScroll -= DETAIL_LINE_H * 8;
                return true;
            }
            case 267 -> { // PageDown
                detailScroll += DETAIL_LINE_H * 8;
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    private void moveFocus(int direction) {
        int size = ClientVoteCache.getOptions().size();
        if (size == 0) {
            return;
        }
        changeFocus(Mth.clamp(focusIndex + direction, 0, size - 1));
        float rowTop = focusIndex * ROW_H;
        VoteFlowFrame.Bounds b = VoteFlowFrame.layout(width, height);
        int viewportH = Math.max(10, b.h() - 72 - HEADER_H - 8);
        if (rowTop < scrollTarget) {
            scrollTarget = rowTop;
        }
        if (rowTop + ROW_H > scrollTarget + viewportH) {
            scrollTarget = rowTop + ROW_H - viewportH;
        }
        playClick(1.15F);
    }

    private void changeFocus(int index) {
        int next = Mth.clamp(index, 0, Math.max(0, ClientVoteCache.getOptions().size() - 1));
        if (next != focusIndex) {
            focusChangedAt = System.currentTimeMillis();
            detailScroll = 0;
            detailCacheKey = -1;
        }
        focusIndex = next;
    }

    private void playClick(float pitch) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
        }
    }
}
