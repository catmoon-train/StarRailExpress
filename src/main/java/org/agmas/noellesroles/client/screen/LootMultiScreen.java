package org.agmas.noellesroles.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.client.widget.TextureWidget;
import org.agmas.noellesroles.utils.lottery.LotteryManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 五连抽结果展示界面
 * - 横向平铺五张卡片
 * - 带有神秘感的入场和揭示动画
 * - 右上角退出、右下角跳过文字按钮
 */
public class LootMultiScreen extends AbstractPixelScreen {
    private static final int[] QUALITY_GLOW_COLORS = {
            0x4099A3B0,
            0x5000C36A,
            0x603A74FF,
            0x70A344FF,
            0x80F3B34C,
            0x90FF5D5D,
    };

    private static final int[] QUALITY_GLOW_INNER_COLORS = {
            0x30C7D0DA,
            0x4000F59A,
            0x506AA0FF,
            0x60CA75FF,
            0x70FFD37A,
            0x80FF9A9A,
    };

    private static final int CARD_TEXTURE_SIZE = 28;
    private static final int BASE_CARD_SIZE = 28;
    private static final int MIN_CARD_SIZE = 42;
    private static final int MAX_CARD_SIZE = 116;
    private static final int ACTION_MARGIN = 16;
    private static final float INTRO_DELAY = 0.22f;
    private static final float INTRO_STAGGER = 0.12f;
    private static final float INTRO_DURATION = 0.5f;
    private static final float REVEAL_START_DELAY = 0.85f;
    private static final float REVEAL_INTERVAL = 0.5f;
    private static final float REVEAL_DURATION = 0.52f;
    private static final float FLASH_DURATION = 0.26f;
    private static final int BACKGROUND_COLOR = 0xE6080B14;
    private static final int BACKGROUND_BAND_COLOR = 0x181A2740;
    private static final int TITLE_COLOR = 0xFFE8D6A8;
    private static final int TITLE_SUB_COLOR = 0xAAE4EEF8;
    private static final int ACTION_IDLE_COLOR = 0xCFE3EBF5;
    private static final int ACTION_HOVER_COLOR = 0xFFFFE0A6;
    private static final int NAME_IDLE_COLOR = 0xFFDCE4EE;
    private static final int NAME_QUALITY_HIGHLIGHT = 0xFFFFE4AF;

    private static class ResultCard extends AbstractWidget {
        private final TextureWidget skinBG;
        private final TextureWidget skin;
        private final int quality;
        private final Component displayName;
        private final int pixelInset;
        private final int baseWidth;
        private final int baseHeight;
        private final int baseX;
        private final int baseY;
        private float ambientTime = 0f;
        private float introProgress = 0f;
        private float revealProgress = 0f;
        private float flashProgress = 0f;
        private boolean revealStarted = false;
        private boolean revealed = false;

        ResultCard(int x, int y, int size, int poolID, int quality, int ansID, int pixelInset) {
            super(x, y, size, size, Component.empty());
            this.quality = quality;
            this.pixelInset = pixelInset;
            this.baseWidth = size;
            this.baseHeight = size;
            this.baseX = x;
            this.baseY = y;

            ResourceLocation bgTexture = LotteryManager.getQualityBgResourceLocation(quality);
            String itemName = resolveItemName(poolID, quality, ansID);
            this.displayName = Component.literal(formatItemName(itemName));

            skinBG = new TextureWidget(x, y, size, size,
                    CARD_TEXTURE_SIZE, CARD_TEXTURE_SIZE,
                    bgTexture);
            skin = new TextureWidget(x + pixelInset, y + pixelInset,
                    size - 2 * pixelInset, size - 2 * pixelInset,
                    CARD_TEXTURE_SIZE, CARD_TEXTURE_SIZE,
                    LootScreenUtils.getItemResourceLocation(itemName));
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
            float intro = easeOutCubic(introProgress);
            if (intro <= 0f)
                return;

            int drawX = baseX;
            int drawY = baseY + Math.round((1.0f - intro) * 34.0f + (float) Math.sin(ambientTime * 2.3f + quality) * 2.5f);
            int drawW = baseWidth;
            int drawH = baseHeight;
            int baseAlpha = Math.max(0, Math.min(255, (int) (intro * 255.0f)));

            if (!revealStarted || revealProgress < 0.38f) {
                renderMysteryCard(guiGraphics, drawX, drawY, drawW, drawH, baseAlpha);
            }
            if (revealStarted) {
                renderRevealFlash(guiGraphics, drawX, drawY, drawW, drawH, baseAlpha);
            }
            if (revealStarted && revealProgress >= 0.24f) {
                float faceAlpha = Mth.clamp((revealProgress - 0.24f) / 0.32f, 0.0f, 1.0f);
                renderFace(guiGraphics, drawX, drawY, drawW, drawH, (int) (baseAlpha * faceAlpha), mouseX, mouseY, delta);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        }

        void tick(float deltaSeconds, float sceneTime, float introDelay) {
            ambientTime = sceneTime;
            introProgress = Mth.clamp((sceneTime - introDelay) / INTRO_DURATION, 0.0f, 1.0f);
            if (revealStarted) {
                revealProgress = Math.min(1.0f, revealProgress + deltaSeconds / REVEAL_DURATION);
                flashProgress = Math.min(1.0f, flashProgress + deltaSeconds / FLASH_DURATION);
                if (revealProgress >= 1.0f)
                    revealed = true;
            }
        }

        void startReveal() {
            if (revealStarted)
                return;
            revealStarted = true;
            revealProgress = 0.001f;
            flashProgress = 0f;
        }

        void forceReveal() {
            revealStarted = true;
            revealProgress = 1.0f;
            flashProgress = 1.0f;
            revealed = true;
        }

        boolean isRevealed() {
            return revealed;
        }

        Component getDisplayName() {
            return displayName;
        }

        int getBaseCenterX() {
            return baseX + baseWidth / 2;
        }

        int getNameY() {
            return baseY + baseHeight + 10;
        }

        float getLabelAlpha() {
            return revealed ? 1.0f : Mth.clamp((revealProgress - 0.56f) / 0.32f, 0.0f, 1.0f);
        }

        private void renderMysteryCard(GuiGraphics guiGraphics, int x, int y, int w, int h, int alpha) {
            float pulse = 0.55f + 0.45f * (float) Math.sin(ambientTime * 3.4f + quality * 1.37f);
            int outerColor = withAlpha(0xFF111827, alpha);
            int innerColor = withAlpha(0xFF0A1120, alpha);
            int borderColor = withAlpha(0xFF566074, Math.min(255, alpha + 16));
            int runeColor = withAlpha(0xFFB8C4D6, (int) (alpha * (0.2f + pulse * 0.22f)));
            int sweepColor = withAlpha(0xFFCEDAF2, (int) (alpha * 0.14f));
            int sweepY = y - 12 + (int) ((ambientTime * 34.0f + quality * 19.0f) % (h + 24));

            guiGraphics.fill(x, y, x + w, y + h, outerColor);
            guiGraphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, innerColor);
            guiGraphics.fill(x, y, x + w, y + 1, borderColor);
            guiGraphics.fill(x, y + h - 1, x + w, y + h, borderColor);
            guiGraphics.fill(x, y, x + 1, y + h, borderColor);
            guiGraphics.fill(x + w - 1, y, x + w, y + h, borderColor);
            guiGraphics.fill(x + 4, Math.max(y + 3, sweepY), x + w - 4, Math.min(y + h - 3, sweepY + 2), sweepColor);

            int runeSize = Math.max(8, (int) (w * 0.22f + pulse * 4.0f));
            int centerX = x + w / 2;
            int centerY = y + h / 2;
            guiGraphics.fill(centerX - 1, centerY - runeSize, centerX + 1, centerY + runeSize, runeColor);
            guiGraphics.fill(centerX - runeSize, centerY - 1, centerX + runeSize, centerY + 1, runeColor);
            guiGraphics.fill(centerX - runeSize / 2, centerY - runeSize / 2, centerX + runeSize / 2, centerY + runeSize / 2,
                    withAlpha(0xFFDEE8F5, (int) (alpha * (0.08f + pulse * 0.12f))));
        }

        private void renderRevealFlash(GuiGraphics guiGraphics, int x, int y, int w, int h, int alpha) {
            float reveal = Mth.clamp(revealProgress, 0.0f, 1.0f);
            float flashPeak = 1.0f - Math.abs(reveal * 2.0f - 0.8f);
            flashPeak = Mth.clamp(flashPeak, 0.0f, 1.0f);
            int flashSize = 5 + (int) (flashPeak * 18.0f);
            int outerFlash = withAlpha(0xFFE5D0A0, (int) (alpha * flashPeak * 0.24f));
            int innerFlash = withAlpha(0xFFFFFFFF, (int) (alpha * flashPeak * 0.4f));
            guiGraphics.fill(x - flashSize, y - flashSize, x + w + flashSize, y + h + flashSize, outerFlash);
            guiGraphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, innerFlash);
        }

        private void renderFace(GuiGraphics guiGraphics, int x, int y, int w, int h, int alpha, int mouseX, int mouseY, float delta) {
            int glowColor = withAlpha(getGlowColor(quality), (int) (alpha * 0.75f));
            int innerGlowColor = withAlpha(getInnerGlowColor(quality), (int) (alpha * 0.85f));
            guiGraphics.fill(x - 4, y - 4, x + w + 4, y + h + 4, glowColor);
            guiGraphics.fill(x - 2, y - 2, x + w + 2, y + h + 2, innerGlowColor);

            skinBG.setPosition(x, y);
            skin.setPosition(x + pixelInset, y + pixelInset);
            skinBG.setAlpha(alpha / 255.0f);
            skin.setAlpha(alpha / 255.0f);
            RenderSystem.enableBlend();
            skinBG.render(guiGraphics, mouseX, mouseY, delta);
            skin.render(guiGraphics, mouseX, mouseY, delta);
        }

        private static String resolveItemName(int poolID, int quality, int ansID) {
            LotteryManager.LotteryPool pool = LotteryManager.getInstance().getLotteryPool(poolID);
            if (pool != null && quality >= 0 && quality < pool.getQualityListGroupConfigs().size()
                    && ansID >= 0 && ansID < pool.getQualityListGroupConfigs().get(quality).second.size()) {
                return pool.getQualityListGroupConfigs().get(quality).second.get(ansID);
            }
            return "unknown";
        }

        private static String formatItemName(String itemName) {
            if (itemName == null || itemName.isEmpty())
                return "unknown";
            int slashIndex = itemName.lastIndexOf('/');
            String result = slashIndex >= 0 ? itemName.substring(slashIndex + 1) : itemName;
            return result.replace('_', ' ');
        }

        private static int getGlowColor(int quality) {
            if (quality < 0)
                return QUALITY_GLOW_COLORS[0];
            if (quality >= QUALITY_GLOW_COLORS.length)
                return QUALITY_GLOW_COLORS[QUALITY_GLOW_COLORS.length - 1];
            return QUALITY_GLOW_COLORS[quality];
        }

        private static int getInnerGlowColor(int quality) {
            if (quality < 0)
                return QUALITY_GLOW_INNER_COLORS[0];
            if (quality >= QUALITY_GLOW_INNER_COLORS.length)
                return QUALITY_GLOW_INNER_COLORS[QUALITY_GLOW_INNER_COLORS.length - 1];
            return QUALITY_GLOW_INNER_COLORS[quality];
        }
    }

    private final int poolId;
    private final List<int[]> results;
    private final List<ResultCard> resultCards = new ArrayList<>();
    private int revealedCount = 0;
    private boolean allRevealed = false;
    private float sceneTime = 0f;
    private int nextRevealIndex = 0;
    private int cardSize = 0;
    private int cardGap = 0;
    private int cardsStartX = 0;
    private int cardsY = 0;
    private Screen parent;

    public LootMultiScreen(int poolId, List<int[]> results, Screen parent) {
        super(Component.empty());
        this.poolId = poolId;
        this.results = results;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        resultCards.clear();
        revealedCount = 0;
        allRevealed = false;
        sceneTime = 0f;
        nextRevealIndex = 0;
        clearWidgets();

        int cardCount = Math.max(1, results.size());
        int horizontalPadding = Math.max(18, width / 24);
        int usableWidth = Math.max(220, width - horizontalPadding * 2);
        int usableHeight = Math.max(160, height - 130);
        cardGap = Mth.clamp(width / 70, 8, 18);
        cardSize = (usableWidth - cardGap * (cardCount - 1)) / cardCount;
        cardSize = Math.min(cardSize, usableHeight);
        cardSize = Mth.clamp(cardSize, MIN_CARD_SIZE, MAX_CARD_SIZE);
        int totalWidth = cardSize * cardCount + cardGap * (cardCount - 1);
        cardsStartX = centerX - totalWidth / 2;
        cardsY = centerY - cardSize / 2 - 18;
        int pixelInset = Math.max(2, cardSize / 16);

        for (int i = 0; i < results.size(); ++i) {
            int[] result = results.get(i);
            int x = cardsStartX + i * (cardSize + cardGap);
            ResultCard card = new ResultCard(x, cardsY, cardSize, poolId, result[0], result[1], pixelInset);
            resultCards.add(card);
            addRenderableWidget(card);
        }
    }

    private void revealCard(int index) {
        if (index < 0 || index >= resultCards.size())
            return;
        ResultCard card = resultCards.get(index);
        if (card.isRevealed())
            return;

        card.startReveal();
        revealedCount++;
        if (revealedCount >= resultCards.size())
            allRevealed = true;

        int quality = results.get(index)[0];
        if (quality >= 3) {
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0F, 1.0F));
        } else {
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 0.9F, 1.15F));
        }
    }

    private void revealAllImmediately() {
        if (allRevealed)
            return;
        nextRevealIndex = resultCards.size();
        revealedCount = resultCards.size();
        allRevealed = true;
        for (ResultCard card : resultCards)
            card.forceReveal();
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.05F));
    }

    private void updateScene(float delta) {
        float deltaSeconds = delta / 10.0f;
        sceneTime += deltaSeconds;

        while (nextRevealIndex < resultCards.size()
                && sceneTime >= REVEAL_START_DELAY + nextRevealIndex * REVEAL_INTERVAL) {
            revealCard(nextRevealIndex);
            nextRevealIndex++;
        }

        for (int i = 0; i < resultCards.size(); ++i) {
            resultCards.get(i).tick(deltaSeconds, sceneTime, INTRO_DELAY + i * INTRO_STAGGER);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        updateScene(delta);
        renderBackdrop(guiGraphics);
        renderTitle(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, delta);
        renderCardNames(guiGraphics);
        renderActionTexts(guiGraphics, mouseX, mouseY);
    }

    private void renderBackdrop(GuiGraphics guiGraphics) {
        guiGraphics.fill(0, 0, width, height, BACKGROUND_COLOR);

        int centerGlowWidth = Math.max(240, cardSize * resultCards.size() + cardGap * Math.max(0, resultCards.size() - 1) + 140);
        int centerGlowHeight = Math.max(120, cardSize + 90);
        for (int i = 0; i < 5; ++i) {
            int expand = i * 18;
            int alpha = Math.max(8, 30 - i * 5);
            guiGraphics.fill(centerX - centerGlowWidth / 2 - expand,
                    cardsY - 50 - expand / 2,
                    centerX + centerGlowWidth / 2 + expand,
                    cardsY + centerGlowHeight + expand,
                    withAlpha(0xFF182131, alpha));
        }

        for (int i = 0; i < 8; ++i) {
            int beamX = (int) ((sceneTime * 42.0f + i * 47.0f) % (width + 120)) - 60;
            int beamAlpha = 9 + (i % 3) * 5;
            guiGraphics.fill(beamX, 0, beamX + 2, height, withAlpha(BACKGROUND_BAND_COLOR, beamAlpha));
        }

        int shimmerY = cardsY - 18 + (int) ((sceneTime * 22.0f) % Math.max(44, cardSize + 36));
        guiGraphics.fill(Math.max(0, cardsStartX - 34), shimmerY,
                Math.min(width, cardsStartX + resultCards.size() * cardSize + Math.max(0, resultCards.size() - 1) * cardGap + 34),
                shimmerY + 2, withAlpha(0xFFD3C29B, 22));
    }

    private void renderTitle(GuiGraphics guiGraphics) {
        Component title = Component.literal("五连揭示");
        Component subtitle = Component.literal("静候命运落定");
        int titleX = centerX - font.width(title) / 2;
        int subtitleX = centerX - font.width(subtitle) / 2;
        guiGraphics.drawString(font, title, titleX, Math.max(20, cardsY - 54), TITLE_COLOR, false);
        guiGraphics.drawString(font, subtitle, subtitleX, Math.max(34, cardsY - 38), TITLE_SUB_COLOR, false);
    }

    private void renderCardNames(GuiGraphics guiGraphics) {
        for (ResultCard card : resultCards) {
            float labelAlpha = card.getLabelAlpha();
            if (labelAlpha <= 0f)
                continue;
            String rawText = card.getDisplayName().getString();
            String clipped = font.plainSubstrByWidth(rawText, Math.max(20, cardSize + 12));
            int textWidth = font.width(clipped);
            int textX = card.getBaseCenterX() - textWidth / 2;
            int textY = card.getNameY();
            int qualityColor = card.quality >= 3 ? NAME_QUALITY_HIGHLIGHT : NAME_IDLE_COLOR;
            guiGraphics.drawString(font, clipped, textX, textY, withAlpha(qualityColor, (int) (labelAlpha * 255.0f)), false);
        }
    }

    private void renderActionTexts(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Component closeLabel = Component.literal("退出");
        Component skipLabel = Component.literal(allRevealed ? "继续" : "跳过");

        int closeX = width - ACTION_MARGIN - font.width(closeLabel);
        int closeY = ACTION_MARGIN;
        int skipX = width - ACTION_MARGIN - font.width(skipLabel);
        int skipY = height - ACTION_MARGIN - font.lineHeight;

        boolean closeHovered = isInTextRect(mouseX, mouseY, closeX, closeY, font.width(closeLabel), font.lineHeight);
        boolean skipHovered = isInTextRect(mouseX, mouseY, skipX, skipY, font.width(skipLabel), font.lineHeight);

        renderActionText(guiGraphics, closeLabel, closeX, closeY, closeHovered);
        renderActionText(guiGraphics, skipLabel, skipX, skipY, skipHovered);
    }

    private void renderActionText(GuiGraphics guiGraphics, Component text, int x, int y, boolean hovered) {
        int color = hovered ? ACTION_HOVER_COLOR : ACTION_IDLE_COLOR;
        guiGraphics.drawString(font, text, x, y, color, false);
        if (hovered) {
            guiGraphics.fill(x, y + font.lineHeight + 1, x + font.width(text), y + font.lineHeight + 2, ACTION_HOVER_COLOR);
        }
    }

    private boolean isInTextRect(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0)
            return super.mouseClicked(mouseX, mouseY, button);

        Component closeLabel = Component.literal("退出");
        Component skipLabel = Component.literal(allRevealed ? "继续" : "跳过");
        int closeX = width - ACTION_MARGIN - font.width(closeLabel);
        int closeY = ACTION_MARGIN;
        int skipX = width - ACTION_MARGIN - font.width(skipLabel);
        int skipY = height - ACTION_MARGIN - font.lineHeight;

        if (isInTextRect(mouseX, mouseY, closeX, closeY, font.width(closeLabel), font.lineHeight)) {
            this.onClose();
            Minecraft.getInstance().execute(()->Minecraft.getInstance().setScreen( parent));
            return true;
        }
        if (isInTextRect(mouseX, mouseY, skipX, skipY, font.width(skipLabel), font.lineHeight)) {
            if (allRevealed) {
                onClose();
                Minecraft.getInstance().execute(()->Minecraft.getInstance().setScreen( parent));
            } else {
                revealAllImmediately();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static float easeOutCubic(float progress) {
        float inverse = 1.0f - Mth.clamp(progress, 0.0f, 1.0f);
        return 1.0f - inverse * inverse * inverse;
    }

    private static int withAlpha(int color, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }
}
