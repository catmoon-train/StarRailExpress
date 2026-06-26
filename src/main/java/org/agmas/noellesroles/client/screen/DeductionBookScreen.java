package org.agmas.noellesroles.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;
import org.agmas.noellesroles.game.roles.innocent.great_detective.DetectiveClue;
import org.agmas.noellesroles.game.roles.innocent.great_detective.GreatDetectivePlayerComponent;
import org.agmas.noellesroles.packet.GreatDetectiveRevealC2SPacket;

import java.util.List;
import java.util.UUID;

/**
 * 推理之书界面。
 *
 * <p>每页对应一名凶手，列出已掌握的线索。线索 &gt;= 3 条时显示可点击的"目标情况"，
 * 点击后向服务端请求记录该凶手与自己的距离快照，并在书上展示（只显示触发时的距离）。
 * 左右方向键或翻页按钮切换凶手页。
 */
public class DeductionBookScreen extends Screen {

    private int page = 0;

    // 当前帧的"目标情况"可点击区域（仅在可点击时有效）
    private boolean targetBtnVisible = false;
    private int targetBtnX;
    private int targetBtnY;
    private int targetBtnW;
    private int targetBtnH;
    private UUID currentKiller = null;

    public DeductionBookScreen() {
        super(Component.translatable("screen.noellesroles.great_detective.title"));
    }

    private GreatDetectivePlayerComponent component() {
        if (minecraft == null || minecraft.player == null) {
            return null;
        }
        return GreatDetectivePlayerComponent.KEY.get(minecraft.player);
    }

    @Override
    protected void init() {
        super.init();
        int cx = width / 2;
        int by = height / 2;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> page = Math.max(0, page - 1))
                .bounds(cx - 130, by, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> page = page + 1)
                .bounds(cx + 110, by, 20, 20).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g, mouseX, mouseY, delta);
        super.render(g, mouseX, mouseY, delta);

        targetBtnVisible = false;
        currentKiller = null;

        int cx = width / 2;
        int top = height / 4;

        g.drawCenteredString(font, title.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                cx, top - 34, 0xFFFFFF);

        GreatDetectivePlayerComponent comp = component();
        List<UUID> killers = comp == null ? List.of() : comp.getKillerOrder();
        if (killers.isEmpty()) {
            g.drawCenteredString(font,
                    Component.translatable("screen.noellesroles.great_detective.empty")
                            .withStyle(ChatFormatting.GRAY),
                    cx, top + 20, 0xAAAAAA);
            return;
        }

        if (page < 0) {
            page = 0;
        }
        if (page >= killers.size()) {
            page = killers.size() - 1;
        }
        UUID killer = killers.get(page);
        currentKiller = killer;

        g.drawCenteredString(font,
                Component.translatable("screen.noellesroles.great_detective.suspect", page + 1, killers.size())
                        .withStyle(ChatFormatting.YELLOW),
                cx, top - 14, 0xFFFFFF);

        List<DetectiveClue> clues = comp.getClues(killer);
        int y = top + 8;
        int idx = 1;
        for (DetectiveClue clue : clues) {
            Component line = Component.literal(idx + ". ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(DeductionClueText.render(clue));
            g.drawCenteredString(font, line, cx, y, 0xFFFFFF);
            y += 14;
            idx++;
        }

        // 目标情况：线索 >= 3 条解锁
        if (clues.size() >= 3) {
            y += 10;
            if (comp.hasRevealedDistance(killer)) {
                int dist = comp.getRevealedDistance(killer);
                Component distText = dist < 0
                        ? Component.translatable("screen.noellesroles.great_detective.distance_unknown")
                                .withStyle(ChatFormatting.RED)
                        : Component.translatable("screen.noellesroles.great_detective.distance", dist)
                                .withStyle(ChatFormatting.AQUA);
                g.drawCenteredString(font, distText, cx, y, 0xFFFFFF);
            } else {
                Component btn = Component.translatable("screen.noellesroles.great_detective.target_situation")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.UNDERLINE);
                int w = font.width(btn);
                targetBtnX = cx - w / 2;
                targetBtnY = y;
                targetBtnW = w;
                targetBtnH = font.lineHeight;
                targetBtnVisible = true;
                boolean hover = mouseX >= targetBtnX && mouseX <= targetBtnX + w
                        && mouseY >= y && mouseY <= y + font.lineHeight;
                g.drawCenteredString(font, btn, cx, y, hover ? 0x55FF55 : 0xFFFFFF);
            }
        } else {
            y += 10;
            g.drawCenteredString(font,
                    Component.translatable("screen.noellesroles.great_detective.need_more", 3 - clues.size())
                            .withStyle(ChatFormatting.DARK_GRAY),
                    cx, y, 0x888888);
        }

        if (killers.size() > 1) {
            g.drawCenteredString(font,
                    Component.translatable("screen.noellesroles.great_detective.page_hint")
                            .withStyle(ChatFormatting.GRAY),
                    cx, height - 40, 0x888888);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && targetBtnVisible && currentKiller != null
                && mouseX >= targetBtnX && mouseX <= targetBtnX + targetBtnW
                && mouseY >= targetBtnY && mouseY <= targetBtnY + targetBtnH) {
            ClientPlayNetworking.send(new GreatDetectiveRevealC2SPacket(currentKiller));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 263) { // 左方向键
            page = Math.max(0, page - 1);
            return true;
        }
        if (keyCode == 262) { // 右方向键
            page = page + 1;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
