package net.exmo.sre.sixtyseconds.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.wifi.starrailexpress.client.util.ClientSkinCache;
import net.exmo.sre.sixtyseconds.network.SixtySecondsEndGamePayload;
import net.exmo.sre.sixtyseconds.network.SixtySecondsEndGamePayload.PlayerResult;
import net.exmo.sre.sixtyseconds.network.SixtySecondsEndGamePayload.StatusCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * 60s 模式专属结算页面，替换原 {@code RoleAnnouncementTexts} / {@code RoundTextRenderer} 的 end-game HUD。
 * 展示：胜败标题、存活/死亡/变怪/撤离统计、按队伍分组的玩家名单。
 */
public class SixtySecondsEndScreen extends Screen {

    private static final int BG_COLOR = 0xC0101010;
    private static final int CARD_BG = 0x80182020;
    private static final int GOLD = 0xFFD700;
    private static final int RED = 0xE10000;
    private static final int GREEN = 0x55FF55;
    private static final int GRAY = 0x808080;

    private final SixtySecondsEndGamePayload data;
    private final List<TeamGroup> teams;
    private int scrollOffset = 0;
    private int maxScroll = 0;

    public SixtySecondsEndScreen(SixtySecondsEndGamePayload data) {
        super(Component.translatable("screen.noellesroles.sixty_seconds.end_title"));
        this.data = data;
        this.teams = buildTeamGroups(data.players());
    }

    // ── 队伍分组 ──

    private record TeamGroup(int teamId, List<PlayerResult> members) {
    }

    private static List<TeamGroup> buildTeamGroups(List<PlayerResult> players) {
        Map<Integer, List<PlayerResult>> map = new LinkedHashMap<>();
        for (PlayerResult p : players) {
            map.computeIfAbsent(p.teamId(), k -> new ArrayList<>()).add(p);
        }
        List<TeamGroup> groups = new ArrayList<>();
        for (var entry : map.entrySet()) {
            groups.add(new TeamGroup(entry.getKey(), entry.getValue()));
        }
        // 按队伍 ID 排序
        groups.sort(Comparator.comparingInt(TeamGroup::teamId));
        return groups;
    }

    // ── 统计 ──

    private int count(StatusCategory cat) {
        return (int) data.players().stream().filter(p -> p.category() == cat).count();
    }

    // ── 渲染 ──

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);

        int width = this.width;
        int centerX = width / 2;
        int y = 20;

        // ── 标题 ──
        Component title = data.winStatus() == io.wifi.starrailexpress.game.GameUtils.WinStatus.PASSENGERS
                ? Component.translatable("screen.noellesroles.sixty_seconds.end_win")
                : Component.translatable("screen.noellesroles.sixty_seconds.end_lose");
        int titleColor = data.winStatus() == io.wifi.starrailexpress.game.GameUtils.WinStatus.PASSENGERS ? GOLD : RED;
        graphics.pose().pushPose();
        graphics.pose().scale(2.0F, 2.0F, 1.0F);
        int titleW = font.width(title);
        graphics.drawString(font, title, centerX / 2 - titleW / 2, y / 2, titleColor, false);
        graphics.pose().popPose();
        y += 28;

        // ── 天数 ──
        Component dayText = Component.translatable("screen.noellesroles.sixty_seconds.end_day",
                data.dayNumber(), net.exmo.sre.sixtyseconds.logic.SixtySecondsManager.DEFAULT_TOTAL_DAYS);
        int dayW = font.width(dayText);
        graphics.drawString(font, dayText, centerX - dayW / 2, y, 0xCCCCCC, false);
        y += 16;

        // ── 统计行 ──
        int survived = count(StatusCategory.SURVIVED);
        int dead = count(StatusCategory.DEAD);
        int monsters = count(StatusCategory.MONSTER);
        int evac = count(StatusCategory.EVACUATED);

        int statY = y;
        int statGap = 60;
        int statStartX = centerX - (statGap * 3) / 2;

        drawStat(graphics, statStartX, statY, GREEN, "☗", survived,
                Component.translatable("screen.noellesroles.sixty_seconds.end_survived"));
        drawStat(graphics, statStartX + statGap, statY, GRAY, "☠", dead,
                Component.translatable("screen.noellesroles.sixty_seconds.end_dead"));
        drawStat(graphics, statStartX + statGap * 2, statY, RED, "☣", monsters,
                Component.translatable("screen.noellesroles.sixty_seconds.end_monster"));

        y = statY + 18;

        // ── 撤离统计 ──
        Component evacText = Component.translatable("screen.noellesroles.sixty_seconds.end_evacuated", evac, 8);
        int evacW = font.width(evacText);
        graphics.drawString(font, evacText, centerX - evacW / 2, y, GOLD, false);
        y += 18;

        // ── 玩家列表 ──
        int listY = y;
        int maxVisible = (this.height - listY - 30) / 16;
        int totalRows = teams.stream().mapToInt(t -> t.members().size() + 1).sum(); // +1 for team header
        maxScroll = Math.max(0, totalRows - maxVisible);

        int row = 0;
        int renderY = listY - scrollOffset * 16;

        for (TeamGroup team : teams) {
            if (renderY + 16 < listY || renderY > this.height) {
                row += team.members().size() + 1;
                renderY += (team.members().size() + 1) * 16;
                continue;
            }

            // 队伍头
            Component teamHeader = Component.translatable("screen.noellesroles.sixty_seconds.end_team",
                    team.teamId() + 1);
            int teamHW = font.width(teamHeader);
            graphics.drawString(font, teamHeader, centerX - teamHW / 2, renderY, 0xAAFFFF, false);
            renderY += 16;
            row++;

            for (PlayerResult player : team.members()) {
                if (renderY + 16 < listY || renderY > this.height) {
                    renderY += 16;
                    row++;
                    continue;
                }

                // 头像
                int headX = centerX - 80;
                drawPlayerHead(graphics, headX, renderY, player);

                // 名字
                Component name = Component.literal(player.name());
                int statusColor = switch (player.category()) {
                    case SURVIVED -> GREEN;
                    case EVACUATED -> GOLD;
                    case DEAD -> GRAY;
                    case MONSTER -> RED;
                };
                graphics.drawString(font, name, headX + 14, renderY + 1, statusColor, false);

                // 状态标记
                Component statusTag = switch (player.category()) {
                    case SURVIVED -> Component.translatable("screen.noellesroles.sixty_seconds.end_tag_survived");
                    case EVACUATED -> Component.translatable("screen.noellesroles.sixty_seconds.end_tag_evacuated");
                    case DEAD -> Component.translatable("screen.noellesroles.sixty_seconds.end_tag_dead");
                    case MONSTER -> Component.translatable("screen.noellesroles.sixty_seconds.end_tag_monster");
                };
                graphics.drawString(font, statusTag, centerX + 30, renderY + 1, statusColor, false);

                renderY += 16;
                row++;
            }
        }

        // ── 底部提示 ──
        Component tip = Component.translatable("screen.noellesroles.sixty_seconds.end_tip")
                .withStyle(ChatFormatting.GRAY);
        int tipW = font.width(tip);
        graphics.drawString(font, tip, centerX - tipW / 2, this.height - 20, 0x888888, false);

        super.render(graphics, mouseX, mouseY, delta);
    }

    private void drawStat(GuiGraphics graphics, int x, int y, int color, String icon, int count, Component label) {
        String text = icon + " " + count;
        graphics.drawString(font, text, x, y, color, false);
        graphics.drawString(font, label, x + font.width(text) + 4, y + 1, 0xAAAAAA, false);
    }

    @SuppressWarnings("deprecation")
    private void drawPlayerHead(GuiGraphics graphics, int x, int y, PlayerResult player) {
        Minecraft mc = Minecraft.getInstance();
        PlayerInfo info = mc.getConnection() != null
                ? mc.getConnection().getPlayerInfo(player.uuid())
                : null;

        if (info != null) {
            PlayerSkin skin = info.getSkin();
            if (skin.texture() != null) {
                ResourceLocation texture = skin.texture();
                // 8x8 head face + hat overlay
                graphics.pose().pushPose();
                graphics.pose().translate(x, y, 0);
                graphics.blit(texture, 0, 0, 8.0F, 8.0F, 8, 8, 64, 64);
                graphics.blit(texture, 0, 0, 40.0F, 8.0F, 8, 8, 64, 64);
                graphics.pose().popPose();
                return;
            }
        }

        // fallback: colored square
        int color = switch (player.category()) {
            case SURVIVED -> GREEN;
            case EVACUATED -> GOLD;
            case DEAD -> GRAY;
            case MONSTER -> RED;
        };
        graphics.fill(x, y, x + 8, y + 8, color | 0xFF000000);
    }

    // ── 滚动 ──

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY > 0) {
            scrollOffset = Math.max(0, scrollOffset - 1);
        } else if (scrollY < 0) {
            scrollOffset = Math.min(maxScroll, scrollOffset + 1);
        }
        return true;
    }

    // ── 关闭 ──

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected void init() {
        // 短暂延迟后才可关闭（防止玩家误触跳过结算）
        addRenderableWidget(Button.builder(Component.translatable("screen.noellesroles.sixty_seconds.end_continue"),
                btn -> this.onClose()).bounds(this.width / 2 - 50, this.height - 40, 100, 20).build());
    }

    @Override
    public void onClose() {
        if (this.minecraft != null && this.minecraft.player != null) {
            // 玩家按继续键后切旁观或重生
            this.minecraft.setScreen(null);
        }
    }
}
