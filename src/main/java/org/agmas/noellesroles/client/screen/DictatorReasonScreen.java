package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.client.util.PinYinUtils;
import io.wifi.starrailexpress.game.GameConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.packet.DictatorJudgeC2SPacket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 独裁者裁决之剑 · 第一步：选择该尸体的死因（可搜索，参考推理师罗盘）。
 *
 * <p>选完死因后进入 {@link DictatorAvatarScreen} 选择凶手。
 */
public class DictatorReasonScreen extends Screen {

    private static final int PAGE_SIZE = 8;
    private static final int OPTION_WIDTH = 280;
    private static final int OPTION_HEIGHT = 20;
    private static final int OPTION_GAP = 4;

    private final List<String> allReasons = new ArrayList<>();
    private final List<Button> optionButtons = new ArrayList<>();
    private Button prevPageButton;
    private Button nextPageButton;
    private EditBox searchWidget;
    private String search;
    private int page;

    public DictatorReasonScreen() {
        super(Component.translatable("screen.noellesroles.dictator.reason_title"));
    }

    @Override
    protected void init() {
        if (allReasons.isEmpty()) {
            allReasons.addAll(GameConstants.DeathReasons.getAllDeathReasonIds());
            allReasons.sort(Comparator.naturalOrder());
        }
        rebuild();
    }

    private List<String> filtered() {
        List<String> result = new ArrayList<>();
        for (String id : allReasons) {
            if (matches(id)) {
                result.add(id);
            }
        }
        return result;
    }

    private boolean matches(String id) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String name = displayName(id).getString();
        String lower = search.toLowerCase();
        return id.toLowerCase().contains(lower) || name.toLowerCase().contains(lower)
                || PinYinUtils.contains(search, name);
    }

    private static Component displayName(String deathReasonId) {
        return Component.translatable("death_reason." + deathReasonId.replace(':', '.'));
    }

    private void rebuild() {
        for (Button button : optionButtons) {
            removeWidget(button);
        }
        if (prevPageButton != null) {
            removeWidget(prevPageButton);
        }
        if (nextPageButton != null) {
            removeWidget(nextPageButton);
        }
        optionButtons.clear();

        List<String> options = filtered();
        int maxPage = Math.max(0, (options.size() - 1) / PAGE_SIZE);
        if (page > maxPage) {
            page = maxPage;
        }
        if (page < 0) {
            page = 0;
        }
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, options.size());

        int listHeight = PAGE_SIZE * (OPTION_HEIGHT + OPTION_GAP) - OPTION_GAP;
        int x = (width - OPTION_WIDTH) / 2;
        int y = (height - listHeight) / 2 + 14;

        for (int i = start; i < end; i++) {
            String id = options.get(i);
            int index = i - start;
            Button button = Button.builder(displayName(id), b -> onReasonPicked(id))
                    .bounds(x, y + index * (OPTION_HEIGHT + OPTION_GAP), OPTION_WIDTH, OPTION_HEIGHT).build();
            optionButtons.add(button);
            addRenderableWidget(button);
        }

        if (searchWidget == null) {
            searchWidget = new EditBox(font, x, y - 30, OPTION_WIDTH, 20, Component.nullToEmpty(""));
            searchWidget.setHint(Component.translatable("screen.noellesroles.search.placeholder")
                    .withStyle(ChatFormatting.GRAY));
            searchWidget.setResponder(text -> {
                search = text == null || text.isBlank() ? null : text;
                page = 0;
                rebuild();
            });
        }
        addRenderableWidget(searchWidget);

        int pageY = y + listHeight + 8;
        prevPageButton = Button.builder(Component.literal("<"), button -> {
            if (page > 0) {
                page--;
                rebuild();
            }
        }).bounds(width / 2 - 70, pageY, 40, 20).build();
        prevPageButton.active = page > 0;
        addRenderableWidget(prevPageButton);
        nextPageButton = Button.builder(Component.literal(">"), button -> {
            if (page < maxPage) {
                page++;
                rebuild();
            }
        }).bounds(width / 2 + 30, pageY, 40, 20).build();
        nextPageButton.active = page < maxPage;
        addRenderableWidget(nextPageButton);
    }

    /** 选好死因：进入凶手选择界面。 */
    private void onReasonPicked(String reasonId) {
        if (minecraft == null) {
            return;
        }
        minecraft.setScreen(new DictatorAvatarScreen(
                Component.translatable("screen.noellesroles.dictator.killer_title"),
                player -> {
                    ClientPlayNetworking.send(new DictatorJudgeC2SPacket(reasonId, player.getUUID()));
                    minecraft.setScreen(null);
                }));
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredString(font,
                Component.translatable("screen.noellesroles.dictator.reason_title")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                width / 2, 30, 0xFFFFFF);
        context.drawCenteredString(font,
                Component.translatable("screen.noellesroles.dictator.hint").withStyle(ChatFormatting.GRAY),
                width / 2, height - 26, 0x888888);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && (searchWidget == null || !searchWidget.isFocused())) {
            int maxPage = Math.max(0, (filtered().size() - 1) / PAGE_SIZE);
            int next = page - (int) Math.signum(scrollY);
            if (next >= 0 && next <= maxPage) {
                page = next;
                rebuild();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
