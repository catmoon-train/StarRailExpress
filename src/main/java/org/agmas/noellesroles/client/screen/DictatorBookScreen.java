package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.client.util.PinYinUtils;
import org.agmas.noellesroles.utils.RoleUtils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.client.widget.ConspiratorRoleWidget;
import org.agmas.noellesroles.client.widget.DictatorAvatarWidget;
import org.agmas.noellesroles.packet.DictatorGuessC2SPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 独裁之书选择界面（两阶段，参考阴谋书页）。
 *
 * <ol>
 * <li>选择目标玩家 —— <b>始终展示所有玩家</b>（与阴谋书页不同）；</li>
 * <li>选择猜测的职业 —— 只能选择「非好人方中立」与「杀手」职业。</li>
 * </ol>
 *
 * <p>猜中由服务端结算（闪电处决，死因「裁断」）；无论对错独裁之书都会消耗。
 */
public class DictatorBookScreen extends Screen {

    private static final int ROLES_PER_PAGE = 12;
    private static final int AVATAR_SIZE = 32;
    private static final int SPACING = 8;
    private static final int PER_ROW = 8;

    private int phase = 0; // 0 = 选择玩家, 1 = 选择职业
    private UUID selectedPlayer = null;
    private String selectedPlayerName = "";

    private final List<DictatorAvatarWidget> playerWidgets = new ArrayList<>();
    /** 职业框：直接复用阴谋书页的 {@link ConspiratorRoleWidget}（含职业颜色渲染）。 */
    private final List<ConspiratorRoleWidget> roleWidgets = new ArrayList<>();
    private Button prevPageButton;
    private Button nextPageButton;
    private EditBox searchWidget;
    private String search;
    private int page;
    private int filteredRoleCount; // 过滤后的职业总数（页码显示用）
    private int totalPages;

    public DictatorBookScreen() {
        super(Component.translatable("screen.noellesroles.dictator.book_title"));
    }

    @Override
    protected void init() {
        if (phase == 0) {
            rebuildPlayerSelection();
        } else {
            rebuildRoleSelection();
        }
    }

    // ---------- 阶段 1：选择玩家 ----------

    private void rebuildPlayerSelection() {
        for (DictatorAvatarWidget widget : playerWidgets) {
            removeWidget(widget);
        }
        playerWidgets.clear();
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        // 始终展示所有玩家（与阴谋书页不同，这里不做任何过滤）
        List<AbstractClientPlayer> players = new ArrayList<>(minecraft.level.players());
        if (search != null && !search.isBlank()) {
            String lower = search.toLowerCase();
            players.removeIf(p -> !p.getName().getString().toLowerCase().contains(lower));
        }

        int startY;
        if (players.isEmpty()) {
            // 搜索无匹配：保留搜索框（标红提示），否则无法继续输入
            startY = height / 2 - 20;
        } else {
            int columns = Math.min(players.size(), PER_ROW);
            int rows = (int) Math.ceil(players.size() / (double) PER_ROW);
            int totalWidth = columns * (AVATAR_SIZE + SPACING) - SPACING;
            int totalHeight = rows * (AVATAR_SIZE + SPACING) - SPACING;
            int startX = (width - totalWidth) / 2;
            startY = (height - totalHeight) / 2 + 14;

            for (int i = 0; i < players.size(); i++) {
                int col = i % PER_ROW;
                int row = i / PER_ROW;
                int x = startX + col * (AVATAR_SIZE + SPACING);
                int y = startY + row * (AVATAR_SIZE + SPACING);
                AbstractClientPlayer player = players.get(i);
                DictatorAvatarWidget widget = new DictatorAvatarWidget(x, y, AVATAR_SIZE, player, picked -> {
                    selectedPlayer = picked.getUUID();
                    selectedPlayerName = picked.getName().getString();
                    phase = 1;
                    page = 0;
                    search = null;
                    clearWidgets();
                    searchWidget = null;
                    init();
                });
                playerWidgets.add(widget);
                addRenderableWidget(widget);
            }
        }

        if (searchWidget == null) {
            searchWidget = new EditBox(font, (width - 220) / 2, startY - 32, 220, 20, Component.nullToEmpty(""));
            searchWidget.setHint(Component.translatable("screen.noellesroles.search.placeholder")
                    .withStyle(ChatFormatting.GRAY));
            searchWidget.setResponder(text -> {
                search = text == null || text.isBlank() ? null : text;
                rebuildPlayerSelection();
            });
        }
        searchWidget.setTextColor(players.isEmpty()
                ? ChatFormatting.RED.getColor() : ChatFormatting.WHITE.getColor());
        addRenderableWidget(searchWidget);
    }

    // ---------- 阶段 2：选择职业 ----------

    /** 可被猜测的职业：非好人方中立 + 杀手职业（好人方中立不可被猜测）。 */
    private List<SRERole> guessableRoles() {
        List<SRERole> result = new ArrayList<>();
        for (SRERole role : Noellesroles.getAllRolesSorted(false)) {
            if (role == null || role instanceof net.exmo.sre.repair.role.RepairRole) {
                continue;
            }
            // 好人方中立（随好人胜利的中立，如回声聆听者）不属于「可猜身份」
            if (role.isNeutralForInnocent()) {
                continue;
            }
            boolean nonInnocentNeutral = role.isNeutrals() && !role.isInnocent();
            boolean killer = role.canUseKiller() && !role.isInnocent();
            if (nonInnocentNeutral || killer) {
                result.add(role);
            }
        }
        return result;
    }

    private void rebuildRoleSelection() {
        for (ConspiratorRoleWidget widget : roleWidgets) {
            removeWidget(widget);
        }
        if (prevPageButton != null) {
            removeWidget(prevPageButton);
        }
        if (nextPageButton != null) {
            removeWidget(nextPageButton);
        }
        roleWidgets.clear();

        // 与阴谋书页一致：先按搜索内容过滤，再对过滤结果分页
        List<SRERole> filtered = new ArrayList<>();
        String query = search == null ? null : search.trim().toLowerCase();
        for (SRERole role : guessableRoles()) {
            if (matchesRoleSearch(role, query)) {
                filtered.add(role);
            }
        }
        filteredRoleCount = filtered.size();
        totalPages = (int) Math.ceil(filteredRoleCount / (double) ROLES_PER_PAGE);
        if (page >= totalPages) {
            page = totalPages - 1;
        }
        if (page < 0) {
            page = 0;
        }
        int start = page * ROLES_PER_PAGE;
        int end = Math.min(start + ROLES_PER_PAGE, filteredRoleCount);
        int rolesOnThisPage = end - start;

        // ===== 排版完全照搬阴谋书页（ConspiratorScreen.initRoleSelection）=====
        int columns = Math.max(1, Math.min(rolesOnThisPage, 4));
        int rows = Math.max(1, (int) Math.ceil(rolesOnThisPage / 4.0));
        int widgetWidth = 90;
        int widgetHeight = 24;
        int spacingX = 10;
        int spacingY = 6;
        int totalWidth = columns * (widgetWidth + spacingX) - spacingX;
        int totalHeight = rows * (widgetHeight + spacingY) - spacingY;
        int startX = (width - totalWidth) / 2;
        int startY = (height - totalHeight) / 2 + 10;

        for (int i = start; i < end; i++) {
            SRERole role = filtered.get(i);
            int indexOnPage = i - start;
            int col = indexOnPage % 4;
            int row = indexOnPage / 4;
            int x = startX + col * (widgetWidth + spacingX);
            int y = startY + row * (widgetHeight + spacingY);
            ConspiratorRoleWidget widget = new ConspiratorRoleWidget(
                    x, y, widgetWidth, widgetHeight, role, this::onRolePicked);
            roleWidgets.add(widget);
            addRenderableWidget(widget);
        }

        if (searchWidget == null) {
            searchWidget = new EditBox(font, startX, startY - 40, totalWidth, 20, Component.nullToEmpty(""));
            searchWidget.setHint(Component.translatable("screen.noellesroles.search.placeholder")
                    .withStyle(ChatFormatting.GRAY));
            searchWidget.setResponder(text -> {
                search = text == null || text.isBlank() ? null : text;
                page = 0;
                rebuildRoleSelection();
            });
        }
        // 搜索框位置跟随阴谋书页布局（setX/setY/setWidth 重建位置但保留输入焦点）
        searchWidget.setX(startX);
        searchWidget.setY(startY - 40);
        searchWidget.setWidth(totalWidth);
        searchWidget.setTextColor(filteredRoleCount == 0
                ? ChatFormatting.RED.getColor() : ChatFormatting.WHITE.getColor());
        addRenderableWidget(searchWidget);

        // ===== 翻页按钮：同阴谋书页（「上一页/下一页」文案 + 位置）=====
        int buttonWidth = 60;
        int buttonHeight = 20;
        int buttonY = startY + totalHeight + 20;
        prevPageButton = Button.builder(
                Component.translatable("screen.noellesroles.conspirator.prev_page"),
                button -> {
                    if (page > 0) {
                        page--;
                        rebuildRoleSelection();
                    }
                }).bounds(width / 2 - buttonWidth - 30, buttonY, buttonWidth, buttonHeight).build();
        prevPageButton.active = page > 0;
        addRenderableWidget(prevPageButton);
        nextPageButton = Button.builder(
                Component.translatable("screen.noellesroles.conspirator.next_page"),
                button -> {
                    if (page < totalPages - 1) {
                        page++;
                        rebuildRoleSelection();
                    }
                }).bounds(width / 2 + 30, buttonY, buttonWidth, buttonHeight).build();
        nextPageButton.active = page < totalPages - 1;
        addRenderableWidget(nextPageButton);
    }

    /** 职业是否匹配搜索内容（名称 / id / 拼音，同阴谋书页）。 */
    private boolean matchesRoleSearch(SRERole role, String lower) {
        if (lower == null || lower.isBlank()) {
            return true;
        }
        String name = RoleUtils.getRoleName(role).getString();
        return name.toLowerCase().contains(lower)
                || role.identifier().toString().toLowerCase().contains(lower)
                || PinYinUtils.contains(search, name);
    }

    private void onRolePicked(SRERole role) {
        if (selectedPlayer == null || minecraft == null) {
            return;
        }
        ClientPlayNetworking.send(new DictatorGuessC2SPacket(selectedPlayer, role.identifier().toString()));
        minecraft.setScreen(null);
    }

    // ---------- 渲染 / 交互 ----------

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        Component title = phase == 0
                ? Component.translatable("screen.noellesroles.dictator.book_select_player")
                : Component.translatable("screen.noellesroles.dictator.book_select_role", selectedPlayerName);
        context.drawCenteredString(font, title.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                width / 2, 30, 0xFFFFFF);
        // 页码信息：同阴谋书页
        if (phase == 1 && filteredRoleCount > ROLES_PER_PAGE) {
            context.drawCenteredString(font,
                    Component.translatable("screen.noellesroles.conspirator.page_info", page + 1, totalPages)
                            .withStyle(ChatFormatting.YELLOW),
                    width / 2, 45, 0xFFFFFF);
        }
        context.drawCenteredString(font,
                Component.translatable("screen.noellesroles.dictator.hint").withStyle(ChatFormatting.GRAY),
                width / 2, height - 26, 0x888888);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && phase == 1) {
            // ESC 返回玩家选择
            phase = 0;
            selectedPlayer = null;
            selectedPlayerName = "";
            search = null;
            clearWidgets();
            searchWidget = null;
            init();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
