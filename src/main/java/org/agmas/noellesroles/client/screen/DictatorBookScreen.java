package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.client.util.PinYinUtils;
import io.wifi.starrailexpress.util.RoleUtils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.Noellesroles;
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
    private final List<Button> roleButtons = new ArrayList<>();
    private Button prevPageButton;
    private Button nextPageButton;
    private EditBox searchWidget;
    private String search;
    private int page;

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
        if (players.isEmpty()) {
            return;
        }

        int columns = Math.min(players.size(), PER_ROW);
        int rows = (int) Math.ceil(players.size() / (double) PER_ROW);
        int totalWidth = columns * (AVATAR_SIZE + SPACING) - SPACING;
        int totalHeight = rows * (AVATAR_SIZE + SPACING) - SPACING;
        int startX = (width - totalWidth) / 2;
        int startY = (height - totalHeight) / 2 + 14;

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

        if (searchWidget == null) {
            searchWidget = new EditBox(font, (width - 220) / 2, startY - 32, 220, 20, Component.nullToEmpty(""));
            searchWidget.setHint(Component.translatable("screen.noellesroles.search.placeholder")
                    .withStyle(ChatFormatting.GRAY));
            searchWidget.setResponder(text -> {
                search = text == null || text.isBlank() ? null : text;
                rebuildPlayerSelection();
            });
        }
        addRenderableWidget(searchWidget);
    }

    // ---------- 阶段 2：选择职业 ----------

    /** 可被猜测的职业：非好人方中立 + 杀手职业。 */
    private List<SRERole> guessableRoles() {
        List<SRERole> result = new ArrayList<>();
        for (SRERole role : Noellesroles.getAllRolesSorted(false)) {
            if (role == null || role instanceof net.exmo.sre.repair.role.RepairRole) {
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
        for (Button button : roleButtons) {
            removeWidget(button);
        }
        if (prevPageButton != null) {
            removeWidget(prevPageButton);
        }
        if (nextPageButton != null) {
            removeWidget(nextPageButton);
        }
        roleButtons.clear();

        List<SRERole> roles = guessableRoles();
        int maxPage = Math.max(0, (roles.size() - 1) / ROLES_PER_PAGE);
        if (page > maxPage) {
            page = maxPage;
        }
        if (page < 0) {
            page = 0;
        }
        int start = page * ROLES_PER_PAGE;
        int end = Math.min(start + ROLES_PER_PAGE, roles.size());

        int columns = 4;
        int rows = (int) Math.ceil(ROLES_PER_PAGE / (double) columns);
        int widgetWidth = 96;
        int widgetHeight = 24;
        int spacingX = 10;
        int spacingY = 6;
        int totalWidth = columns * (widgetWidth + spacingX) - spacingX;
        int totalHeight = rows * (widgetHeight + spacingY) - spacingY;
        int startX = (width - totalWidth) / 2;
        int startY = (height - totalHeight) / 2 + 14;

        for (int i = start; i < end; i++) {
            SRERole role = roles.get(i);
            int indexOnPage = i - start;
            int col = indexOnPage % columns;
            int row = indexOnPage / columns;
            int x = startX + col * (widgetWidth + spacingX);
            int y = startY + row * (widgetHeight + spacingY);
            Button button = Button.builder(RoleUtils.getRoleName(role), b -> onRolePicked(role))
                    .bounds(x, y, widgetWidth, widgetHeight).build();
            roleButtons.add(button);
            addRenderableWidget(button);
        }

        if (searchWidget == null) {
            searchWidget = new EditBox(font, (width - totalWidth) / 2, startY - 32, totalWidth, 20,
                    Component.nullToEmpty(""));
            searchWidget.setHint(Component.translatable("screen.noellesroles.search.placeholder")
                    .withStyle(ChatFormatting.GRAY));
            searchWidget.setResponder(text -> {
                search = text == null || text.isBlank() ? null : text;
                page = 0;
                rebuildRoleSelection();
            });
        }
        addRenderableWidget(searchWidget);
        applyRoleSearchFilter(roles);

        int pageY = startY + totalHeight + 12;
        prevPageButton = Button.builder(Component.literal("<"), button -> {
            if (page > 0) {
                page--;
                rebuildRoleSelection();
            }
        }).bounds(width / 2 - 70, pageY, 40, 20).build();
        prevPageButton.active = page > 0;
        addRenderableWidget(prevPageButton);
        nextPageButton = Button.builder(Component.literal(">"), button -> {
            if (page < maxPage) {
                page++;
                rebuildRoleSelection();
            }
        }).bounds(width / 2 + 30, pageY, 40, 20).build();
        nextPageButton.active = page < maxPage;
        addRenderableWidget(nextPageButton);
    }

    /** 按搜索内容隐藏不匹配的职业按钮（与阴谋家一致的「先过滤再分页」思路）。 */
    private void applyRoleSearchFilter(List<SRERole> roles) {
        if (search == null || search.isBlank()) {
            return;
        }
        String lower = search.toLowerCase();
        int start = page * ROLES_PER_PAGE;
        int end = Math.min(start + ROLES_PER_PAGE, roles.size());
        int visible = 0;
        for (int i = start; i < end; i++) {
            SRERole role = roles.get(i);
            String name = RoleUtils.getRoleName(role).getString();
            boolean match = name.toLowerCase().contains(lower)
                    || role.identifier().toString().toLowerCase().contains(lower)
                    || PinYinUtils.contains(search, name);
            Button button = roleButtons.get(visible);
            button.visible = match;
            visible++;
        }
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
