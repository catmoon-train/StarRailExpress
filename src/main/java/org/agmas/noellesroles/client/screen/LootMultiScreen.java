package org.agmas.noellesroles.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;
import org.agmas.noellesroles.client.animation.AbstractAnimation;
import org.agmas.noellesroles.client.animation.BezierAnimation;
import org.agmas.noellesroles.client.widget.TextureWidget;
import org.agmas.noellesroles.client.widget.TimerWidget;
import org.agmas.noellesroles.utils.Pair;
import org.agmas.noellesroles.utils.lottery.LotteryManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 五连抽结果展示界面
 * - 竖向排列五张卡片
 * - 根据品质添加发光效果
 * - 依次翻牌展示
 */
public class LootMultiScreen extends AbstractPixelScreen {
    /** 品质对应的发光颜色 (ARGB) */
    private static final int[] QUALITY_GLOW_COLORS = {
            0x40AAAAAA, // 0: common - 灰色微光
            0x5000CC00, // 1: uncommon - 绿色光
            0x600066FF, // 2: rare - 蓝色光
            0x70AA00FF, // 3: epic - 紫色光
            0x80FFAA00, // 4: legendary - 金色光
            0x90FF3333, // 5: unbelievable - 红色光
    };

    /** 品质对应的发光颜色 (更亮的内层) */
    private static final int[] QUALITY_GLOW_INNER_COLORS = {
            0x30CCCCCC, // 0: common
            0x4000FF00, // 1: uncommon
            0x500088FF, // 2: rare
            0x60CC44FF, // 3: epic
            0x70FFCC00, // 4: legendary
            0x80FF6666, // 5: unbelievable
    };

    private static class ResultCard extends AbstractWidget {
        private final TextureWidget skinBG;
        private final TextureWidget skin;
        private final int quality;
        private float revealProgress = 0f;
        private boolean revealed = false;
        private final int cardWidth;
        private final int cardHeight;

        public ResultCard(int x, int y, int w, int h, int poolID, int quality, int ansID, int pixelSize) {
            super(x, y, w, h, Component.empty());
            this.quality = quality;
            this.cardWidth = w;
            this.cardHeight = h;
            skinBG = new TextureWidget(x, y, w, h, w, h,
                    LotteryManager.getQualityBgResourceLocation(quality));
            String itemName = LotteryManager.getInstance().getLotteryPool(poolID)
                    .getQualityListGroupConfigs().get(quality).second.get(ansID);
            skin = new TextureWidget(x + pixelSize, y + pixelSize,
                    w - 2 * pixelSize, h - 2 * pixelSize, w - 2 * pixelSize, h - 2 * pixelSize,
                    LootScreenUtils.getItemResourceLocation(itemName));
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
            if (!revealed) {
                // 未翻开时显示神秘背面（深色矩形 + 问号）
                guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF1A1A2E);
                guiGraphics.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, 0xFF16213E);
                // 绘制问号
                Minecraft mc = Minecraft.getInstance();
                Component questionMark = Component.literal("?");
                int textX = getX() + (getWidth() - mc.font.width(questionMark)) / 2;
                int textY = getY() + (getHeight() - mc.font.lineHeight) / 2;
                guiGraphics.drawString(mc.font, questionMark, textX, textY, 0x44FFFFFF, false);
                return;
            }
            // 绘制外层发光
            int glowSize = 3;
            int glowColor = getGlowColor(quality);
            guiGraphics.fill(getX() - glowSize, getY() - glowSize,
                    getX() + getWidth() + glowSize, getY() + getHeight() + glowSize, glowColor);
            // 绘制内层发光
            int innerGlowSize = 1;
            int innerGlowColor = getInnerGlowColor(quality);
            guiGraphics.fill(getX() - innerGlowSize, getY() - innerGlowSize,
                    getX() + getWidth() + innerGlowSize, getY() + getHeight() + innerGlowSize, innerGlowColor);

            RenderSystem.enableBlend();
            skinBG.render(guiGraphics, mouseX, mouseY, delta);
            skin.render(guiGraphics, mouseX, mouseY, delta);
        }

        @Override
        public void setPosition(int x, int y) {
            int deltaX = skinBG.getWidth() - skin.getWidth();
            int deltaY = skinBG.getHeight() - skin.getHeight();
            skinBG.setPosition(x, y);
            skin.setPosition(x + deltaX / 2, y + deltaY / 2);
            super.setPosition(x, y);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        }

        public void reveal() {
            revealed = true;
        }

        public boolean isRevealed() {
            return revealed;
        }

        private static int getGlowColor(int quality) {
            if (quality < 0) return QUALITY_GLOW_COLORS[0];
            if (quality >= QUALITY_GLOW_COLORS.length) return QUALITY_GLOW_COLORS[QUALITY_GLOW_COLORS.length - 1];
            return QUALITY_GLOW_COLORS[quality];
        }

        private static int getInnerGlowColor(int quality) {
            if (quality < 0) return QUALITY_GLOW_INNER_COLORS[0];
            if (quality >= QUALITY_GLOW_INNER_COLORS.length) return QUALITY_GLOW_INNER_COLORS[QUALITY_GLOW_INNER_COLORS.length - 1];
            return QUALITY_GLOW_INNER_COLORS[quality];
        }
    }

    private final int poolId;
    private final List<int[]> results;
    private final List<ResultCard> resultCards = new ArrayList<>();
    private final List<AbstractAnimation> animations = new ArrayList<>();
    private final List<TimerWidget> timerWidgets = new ArrayList<>();
    private int revealedCount = 0;
    private boolean allRevealed = false;
    private static final int CARD_SIZE = 28;
    private static final int CARD_INTERVAL = 6;

    public LootMultiScreen(int poolId, List<int[]> results) {
        super(Component.empty());
        this.poolId = poolId;
        this.results = results;
    }

    @Override
    protected void init() {
        super.init();
        resultCards.clear();
        animations.clear();
        timerWidgets.clear();
        revealedCount = 0;
        allRevealed = false;

        while (width > CARD_SIZE * 5 * pixelSize)
            ++pixelSize;

        int cardW = CARD_SIZE * pixelSize;
        int cardH = CARD_SIZE * pixelSize;
        int totalH = results.size() * cardH + (results.size() - 1) * CARD_INTERVAL * pixelSize;
        int startY = centerY - totalH / 2;
        int cardX = centerX - cardW / 2;

        // 创建卡片 - 竖向排列
        for (int i = 0; i < results.size(); ++i) {
            int[] result = results.get(i);
            int y = startY + i * (cardH + CARD_INTERVAL * pixelSize);
            ResultCard card = new ResultCard(cardX, y, cardW, cardH, poolId, result[0], result[1], pixelSize);
            resultCards.add(card);
            addRenderableWidget(card);
        }

        // 依次翻牌定时器
        for (int i = 0; i < results.size(); ++i) {
            final int idx = i;
            timerWidgets.add(new TimerWidget(0.6f + i * 0.5f, true, timer -> {
                revealCard(idx);
            }));
        }

        // 全部翻完后定时跳转
        timerWidgets.add(new TimerWidget(0.6f + results.size() * 0.5f + 2.0f, true, timer -> {
            onClose();
        }));
    }

    private void revealCard(int idx) {
        if (idx < 0 || idx >= resultCards.size())
            return;
        ResultCard card = resultCards.get(idx);
        if (card.isRevealed())
            return;

        card.reveal();
        revealedCount++;
        if (revealedCount >= results.size())
            allRevealed = true;

        // 播放音效
        int quality = results.get(idx)[0];
        if (quality >= 3) {
            // 高品质播放升级音效
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0F, 1.0F));
        } else {
            // 普通品质播放按钮音效
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }

        // 缩放弹跳动画
        BezierAnimation scaleAnim = new BezierAnimation(
                card,
                new Vec2(card.getWidth() * 0.15f, -card.getHeight() * 0.15f),
                new Vec2(card.getWidth() * 0.2f, card.getHeight() * 0.2f),
                new Vec2(card.getWidth() * 0.1f, card.getHeight() * 0.1f),
                10,
                (Vec2 pos) -> {
                    card.setSize((int) pos.x + card.getWidth(), (int) pos.y + card.getHeight());
                }
        );
        animations.add(scaleAnim);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        // 绘制暗色背景增加神秘感
        guiGraphics.fill(0, 0, width, height, 0xCC0A0A1A);

        // 更新动画
        animations.forEach(anim -> anim.renderUpdate(delta));
        animations.removeIf(AbstractAnimation::isFinished);
        timerWidgets.forEach(timer -> timer.onRenderUpdate(delta));

        // 渲染卡片
        for (ResultCard card : resultCards) {
            // 居中修正
            card.setPosition(centerX - card.getWidth() / 2, card.getY());
            card.render(guiGraphics, mouseX, mouseY, delta);
        }

        // 绘制物品名称
        for (int i = 0; i < resultCards.size(); ++i) {
            ResultCard card = resultCards.get(i);
            if (card.isRevealed()) {
                int[] result = results.get(i);
                String itemName = LotteryManager.getInstance().getLotteryPool(poolId)
                        .getQualityListGroupConfigs().get(result[0]).second.get(result[1]);
                Component nameText = Component.literal(itemName);
                int textX = centerX + card.getWidth() / 2 + 6;
                int textY = card.getY() + (card.getHeight() - font.lineHeight) / 2;
                guiGraphics.drawString(font, nameText, textX, textY, 0xFFFFFFFF);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 点击可以跳过等待，直接翻开所有卡片
        if (!allRevealed) {
            for (int i = 0; i < resultCards.size(); ++i) {
                revealCard(i);
            }
            timerWidgets.clear();
            // 添加延迟关闭定时器
            timerWidgets.add(new TimerWidget(3.0f, true, timer -> onClose()));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
