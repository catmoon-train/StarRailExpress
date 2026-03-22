package io.wifi.starrailexpress.client.gui.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.MapVotingComponent;
import io.wifi.starrailexpress.client.InputHandler;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.data.MapConfig;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapSelectorScreen extends Screen {
    private static final int CARD_WIDTH = 164;
    private static final int CARD_HEIGHT = 224;
    private static final int CARD_SPACING = 18;
    private static final int SIDE_PADDING = 56;
    private static final int BOTTOM_PANEL_HEIGHT = 86;
    private static final int PARTICLE_COUNT = 68;

    private static final int COLOR_BG_TOP = 0xFF060B18;
    private static final int COLOR_BG_BOTTOM = 0xFF111A32;
    private static final int COLOR_CARD_TOP = 0xFF16233F;
    private static final int COLOR_CARD_BOTTOM = 0xFF101A30;
    private static final int COLOR_PANEL = 0xC0131F37;
    private static final int COLOR_PANEL_DARK = 0xE00A1122;
    private static final int COLOR_TEXT = 0xFFEAF1FF;
    private static final int COLOR_TEXT_DIM = 0xFF97A6CC;
    private static final int COLOR_ACCENT = 0xFF51D2FF;
    private static final int COLOR_WARNING = 0xFFFF6D6D;

    private final List<MapOption> mapOptions = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final Map<String, Integer> voteCounts = new HashMap<>();

    private MapOption hoveredMap;
    private MapOption selectedMap;

    private float introProgress;
    private float backgroundTick;
    private float scrollTarget;
    private float scrollPosition;
    private long openedAt;

    public MapSelectorScreen() {
        super(Component.translatable("gui.sre.map_selector.title"));
        initMapOptions();
    }

    private void initMapOptions() {
        mapOptions.clear();

        List<MapConfig.MapEntry> configMaps = MapConfig.getInstance().getMaps();
        if (configMaps != null && !configMaps.isEmpty()) {
            for (int i = 0; i < configMaps.size(); i++) {
                MapConfig.MapEntry entry = configMaps.get(i);
                mapOptions.add(new MapOption(
                        entry.getId(),
                        Component.translatable(entry.getDisplayName()).getString(),
                        Component.translatable(entry.getDescription()).getString(),
                        entry.getColor(),
                        i * 0.07f));
            }
            return;
        }

        mapOptions.add(new MapOption(
                "random",
                Component.translatable("gui.sre.map_selector.random").getString(),
                Component.translatable("gui.sre.map_selector.random.desc").getString(),
                0xFF4CC9F0,
                0.00f));
        mapOptions.add(new MapOption(
                "areas1",
                Component.translatable("gui.sre.map_selector.zeppelin").getString(),
                Component.translatable("gui.sre.map_selector.zeppelin.desc").getString(),
                0xFF9D0208,
                0.07f));
    }

    private void initParticles() {
        particles.clear();
        int count = Math.max(PARTICLE_COUNT, width / 16);
        for (int i = 0; i < count; i++) {
            particles.add(new Particle(
                    (float) (Math.random() * width),
                    (float) (Math.random() * height),
                    (float) ((Math.random() - 0.5f) * 0.28f),
                    (float) (0.18f + Math.random() * 0.55f),
                    (float) (1.0f + Math.random() * 1.8f),
                    (float) (Math.random() * 0.6f + 0.2f),
                    (float) (Math.random() * Math.PI * 2.0f)));
        }
    }

    @Override
    protected void init() {
        super.init();

        openedAt = System.currentTimeMillis();
        introProgress = 0.0f;
        backgroundTick = 0.0f;
        scrollTarget = Mth.clamp(scrollTarget, 0.0f, getMaxScroll());
        scrollPosition = scrollTarget;

        if (mapOptions.size() == 1 && selectedMap == null) {
            selectedMap = mapOptions.getFirst();
        }

        initParticles();
        ensureMapVisible(selectedMap);
    }

    @Override
    public void tick() {
        super.tick();

        introProgress = Mth.lerp(0.09f, introProgress, 1.0f);
        scrollPosition = Mth.lerp(0.24f, scrollPosition, scrollTarget);
        backgroundTick += 0.015f;

        for (MapOption option : mapOptions) {
            option.hoverTime = Mth.lerp(0.2f, option.hoverTime, option == hoveredMap ? 1.0f : 0.0f);
            option.selectionTime = Mth.lerp(0.18f, option.selectionTime, option == selectedMap ? 1.0f : 0.0f);
        }

        float elapsedSeconds = (System.currentTimeMillis() - openedAt) / 1000.0f;
        for (Particle particle : particles) {
            particle.update(width, height, elapsedSeconds);
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        // Fully custom background.
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        renderModernBackground(guiGraphics);
        renderHeader(guiGraphics);
        renderMapOptions(guiGraphics, mouseX, mouseY);
        renderVotingTimer(guiGraphics);
        drawSelectionInfo(guiGraphics);

        super.render(guiGraphics, mouseX, mouseY, partialTicks);
    }

    private void renderModernBackground(GuiGraphics guiGraphics) {
        guiGraphics.fillGradient(0, 0, width, height, COLOR_BG_TOP, COLOR_BG_BOTTOM);

        drawAmbientGlows(guiGraphics);
        drawDynamicGrid(guiGraphics);
        drawParticles(guiGraphics);

        guiGraphics.fillGradient(0, 0, width, 95,
                withAlpha(0x000000, 160),
                withAlpha(0x000000, 0));
        guiGraphics.fillGradient(0, height - 125, width, height,
                withAlpha(0x000000, 0),
                withAlpha(0x000000, 180));
    }

    private void drawAmbientGlows(GuiGraphics guiGraphics) {
        float t = (System.currentTimeMillis() - openedAt) / 1000.0f;

        drawSoftGlow(guiGraphics,
                (int) (width * 0.22f + Math.sin(t * 0.7f) * 70.0f),
                (int) (height * 0.20f + Math.cos(t * 0.45f) * 36.0f),
                210,
                0x2A74FF,
                0.12f);

        drawSoftGlow(guiGraphics,
                (int) (width * 0.82f + Math.cos(t * 0.55f) * 65.0f),
                (int) (height * 0.33f + Math.sin(t * 0.65f) * 44.0f),
                180,
                0x26CFFF,
                0.09f);

        drawSoftGlow(guiGraphics,
                (int) (width * 0.52f + Math.sin(t * 0.33f) * 90.0f),
                (int) (height * 0.86f + Math.cos(t * 0.29f) * 24.0f),
                220,
                0x1EAAE0,
                0.08f);
    }

    private void drawSoftGlow(GuiGraphics guiGraphics, int centerX, int centerY, int radius, int rgbColor,
            float intensity) {
        int layers = 8;
        for (int layer = layers; layer >= 1; layer--) {
            float ratio = layer / (float) layers;
            int alpha = (int) (255.0f * intensity * ratio * ratio);
            int currentRadius = (int) (radius * ratio);
            guiGraphics.fill(
                    centerX - currentRadius,
                    centerY - currentRadius,
                    centerX + currentRadius,
                    centerY + currentRadius,
                    withAlpha(rgbColor, alpha));
        }
    }

    private void drawDynamicGrid(GuiGraphics guiGraphics) {
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();

        Tesselator tesselator = Tesselator.getInstance();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Matrix4f matrix = poseStack.last().pose();
        var buffer = tesselator.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        int majorStep = 48;
        int minorStep = majorStep / 2;
        float drift = (backgroundTick * 18.0f) % majorStep;
        float skew = 0.22f;

        int majorColor = withAlpha(0x6CA7FF, 30);
        int minorColor = withAlpha(0x6CA7FF, 16);

        for (int x = -majorStep * 3; x <= width + majorStep * 3; x += majorStep) {
            float startX = x + drift;
            float endX = startX + height * skew;
            buffer.addVertex(matrix, startX, -majorStep, 0).setColor(majorColor);
            buffer.addVertex(matrix, endX, height + majorStep, 0).setColor(majorColor);
        }

        for (int x = -majorStep * 3 + minorStep; x <= width + majorStep * 3; x += majorStep) {
            float startX = x + drift;
            float endX = startX + height * skew;
            buffer.addVertex(matrix, startX, -majorStep, 0).setColor(minorColor);
            buffer.addVertex(matrix, endX, height + majorStep, 0).setColor(minorColor);
        }

        float verticalDrift = (backgroundTick * 12.0f) % majorStep;
        for (int y = -majorStep * 2; y <= height + majorStep * 2; y += majorStep) {
            float startY = y + verticalDrift;
            buffer.addVertex(matrix, -majorStep, startY, 0).setColor(majorColor);
            buffer.addVertex(matrix, width + majorStep, startY, 0).setColor(majorColor);
        }

        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private void drawParticles(GuiGraphics guiGraphics) {
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();

        Tesselator tesselator = Tesselator.getInstance();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Matrix4f matrix = poseStack.last().pose();
        var buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        float time = (System.currentTimeMillis() - openedAt) / 1000.0f;
        for (Particle particle : particles) {
            float size = particle.size * (0.8f + 0.2f * (float) Math.sin(time * particle.twinkleSpeed + particle.phase));
            int alpha = (int) (255.0f * particle.getAlpha(time));
            int color = withAlpha(0xC7E4FF, alpha);

            buffer.addVertex(matrix, particle.x - size, particle.y - size, 0).setColor(color);
            buffer.addVertex(matrix, particle.x + size, particle.y - size, 0).setColor(color);
            buffer.addVertex(matrix, particle.x + size, particle.y + size, 0).setColor(color);
            buffer.addVertex(matrix, particle.x - size, particle.y + size, 0).setColor(color);
        }

        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private void renderHeader(GuiGraphics guiGraphics) {
        float show = easeOutCubic(Mth.clamp((introProgress - 0.04f) * 1.3f, 0.0f, 1.0f));
        int alpha = (int) (show * 255.0f);
        int yOffset = (int) ((1.0f - show) * 12.0f);

        int titleY = 28 - yOffset;
        guiGraphics.drawCenteredString(
                font,
                Component.translatable("gui.sre.map_selector.title").withStyle(ChatFormatting.BOLD),
                width / 2,
                titleY,
                withAlpha(COLOR_TEXT, alpha));

        Component keyHint = InputHandler.getOpenVotingScreenKeybind() != null
                ? InputHandler.getOpenVotingScreenKeybind().getTranslatedKeyMessage()
                : Component.literal("M");
        Component subtitle = Component.translatable("gui.sre.map_selector.subtitle", keyHint);
        guiGraphics.drawCenteredString(
                font,
                subtitle,
                width / 2,
                titleY + 16,
                withAlpha(COLOR_TEXT_DIM, (int) (alpha * 0.92f)));
    }

    private void renderMapOptions(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (mapOptions.isEmpty()) {
            return;
        }

        hoveredMap = null;

        int cardCount = mapOptions.size();
        int totalWidth = cardCount * CARD_WIDTH + Math.max(0, cardCount - 1) * CARD_SPACING;
        int availableWidth = width - SIDE_PADDING * 2;

        int startX = totalWidth > availableWidth ? SIDE_PADDING : (width - totalWidth) / 2;
        int startY = Mth.clamp((height - CARD_HEIGHT) / 2, 84, Math.max(84, height - CARD_HEIGHT - 12));

        if (totalWidth > availableWidth) {
            guiGraphics.fill(0, startY - 10, SIDE_PADDING, startY + CARD_HEIGHT + 10, withAlpha(0x02050E, 115));
            guiGraphics.fill(width - SIDE_PADDING, startY - 10, width, startY + CARD_HEIGHT + 10, withAlpha(0x02050E, 115));

            int pulseAlpha = (int) (140.0f + Math.sin(backgroundTick * 4.0f) * 55.0f);
            if (scrollTarget > 1.0f) {
                guiGraphics.drawString(font, "<", 16, startY + CARD_HEIGHT / 2 - 4, withAlpha(COLOR_TEXT_DIM, pulseAlpha), false);
            }
            if (scrollTarget < getMaxScroll() - 1) {
                guiGraphics.drawString(font, ">", width - 22, startY + CARD_HEIGHT / 2 - 4, withAlpha(COLOR_TEXT_DIM, pulseAlpha), false);
            }
        }

        for (int i = 0; i < cardCount; i++) {
            MapOption map = mapOptions.get(i);

            float entrance = easeOutCubic(Mth.clamp((introProgress - map.introDelay) * 1.8f, 0.0f, 1.0f));
            float cardX = startX + i * (CARD_WIDTH + CARD_SPACING) - scrollPosition + (1.0f - entrance) * 10.0f;
            float cardY = startY + (1.0f - entrance) * (22.0f + i * 1.2f) - map.hoverTime * 8.0f - map.selectionTime * 4.0f;

            if (cardX + CARD_WIDTH < -24 || cardX > width + 24) {
                continue;
            }

            boolean isHovered = mouseX >= cardX && mouseX <= cardX + CARD_WIDTH
                    && mouseY >= cardY && mouseY <= cardY + CARD_HEIGHT;
            if (isHovered) {
                hoveredMap = map;
            }

            drawMapCard(guiGraphics, map, i, cardX, cardY, entrance, isHovered);
        }
    }

    private void drawMapCard(GuiGraphics guiGraphics, MapOption map, int index, float x, float y,
            float entrance, boolean isHovered) {
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();

        float scale = 0.92f + entrance * 0.08f + map.hoverTime * 0.025f + map.selectionTime * 0.02f;
        float centerX = x + CARD_WIDTH / 2.0f;
        float centerY = y + CARD_HEIGHT / 2.0f;

        poseStack.translate(centerX, centerY, 0.0f);
        poseStack.scale(scale, scale, 1.0f);
        poseStack.translate(-centerX, -centerY, 0.0f);

        int alpha = (int) (255.0f * entrance);
        int cardX = Mth.floor(x);
        int cardY = Mth.floor(y);

        int mapAccent = normalizeOpaqueColor(map.color);
        float accentMix = 0.14f + map.hoverTime * 0.14f + map.selectionTime * 0.38f;

        int topColor = mixColor(COLOR_CARD_TOP, mapAccent, accentMix);
        int bottomColor = mixColor(COLOR_CARD_BOTTOM, mapAccent, accentMix * 0.62f);

        int shadowAlpha = (int) (alpha * (0.30f + map.hoverTime * 0.25f + map.selectionTime * 0.18f));
        guiGraphics.fill(cardX - 7, cardY + 5, cardX + CARD_WIDTH + 7, cardY + CARD_HEIGHT + 10, withAlpha(0x000000, shadowAlpha));

        guiGraphics.fillGradient(
                cardX,
                cardY,
                cardX + CARD_WIDTH,
                cardY + CARD_HEIGHT,
                withAlpha(topColor, alpha),
                withAlpha(bottomColor, alpha));

        int borderColor = mixColor(0x3D5488, mapAccent, 0.58f);
        float pulse = 0.62f + 0.38f * (float) Math.sin(backgroundTick * 4.8f + index * 0.8f);
        int borderAlpha = (int) (alpha * (0.34f + map.hoverTime * 0.26f + map.selectionTime * (0.30f + 0.20f * pulse)));
        int borderWidth = map == selectedMap ? 2 : 1;
        drawRectBorder(guiGraphics, cardX, cardY, CARD_WIDTH, CARD_HEIGHT, borderWidth, withAlpha(borderColor, borderAlpha));

        guiGraphics.fillGradient(
                cardX + 1,
                cardY + 1,
                cardX + CARD_WIDTH - 1,
                cardY + 8,
                withAlpha(mapAccent, alpha),
                withAlpha(mixColor(mapAccent, 0x0E172F, 0.42f), alpha));

        int nameColor = withAlpha(COLOR_TEXT, (int) (alpha * (0.90f + map.hoverTime * 0.10f)));
        guiGraphics.drawCenteredString(
                font,
                clipText(map.displayName, CARD_WIDTH - 18),
                cardX + CARD_WIDTH / 2,
                cardY + 16,
                nameColor);

        if (SREClient.isPlayerSpectatingOrCreative()) {
            guiGraphics.drawCenteredString(
                    font,
                    clipText(map.id, CARD_WIDTH - 18),
                    cardX + CARD_WIDTH / 2,
                    cardY + 30,
                    withAlpha(COLOR_TEXT_DIM, (int) (alpha * 0.86f)));
        }

        int previewX = cardX + 12;
        int previewY = cardY + 50;
        int previewWidth = CARD_WIDTH - 24;
        int previewHeight = 110;

        guiGraphics.fillGradient(
                previewX,
                previewY,
                previewX + previewWidth,
                previewY + previewHeight,
                withAlpha(0x1A2647, (int) (alpha * 0.90f)),
                withAlpha(0x0B1226, (int) (alpha * 0.90f)));
        drawRectBorder(guiGraphics, previewX, previewY, previewWidth, previewHeight, 1, withAlpha(mapAccent, (int) (alpha * 0.62f)));

        drawMapPreviewImage(guiGraphics, map.id, previewX + 1, previewY + 1, previewWidth - 2, previewHeight - 2);

        float scanY = (backgroundTick * 68.0f + index * 17.0f) % (previewHeight + 18.0f) - 8.0f;
        int scanAlpha = (int) (alpha * (0.08f + map.hoverTime * 0.10f + map.selectionTime * 0.12f));
        guiGraphics.fill(
                previewX + 2,
                (int) (previewY + scanY),
                previewX + previewWidth - 2,
                (int) (previewY + scanY + 5),
                withAlpha(0xFFFFFF, scanAlpha));

        int descriptionY = cardY + CARD_HEIGHT - 46;
        guiGraphics.drawCenteredString(
                font,
                clipText(map.description, CARD_WIDTH - 18),
                cardX + CARD_WIDTH / 2,
                descriptionY,
                withAlpha(COLOR_TEXT_DIM, (int) (alpha * 0.90f)));

        int voteCount = getVoteCount(map.id);
        if (voteCount > 0) {
            String voteText = Component.translatable("gui.sre.map_selector.vote_count", voteCount).getString();
            int totalVotes = getTotalVoteCount();
            if (totalVotes > 0) {
                int percent = Mth.floor(voteCount * 100.0f / totalVotes);
                voteText = voteText + "  " + percent + "%";
            }

            int badgeWidth = Math.min(CARD_WIDTH - 18, font.width(voteText) + 12);
            int badgeX = cardX + (CARD_WIDTH - badgeWidth) / 2;
            int badgeY = cardY + CARD_HEIGHT - 28;
            int badgeColor = mixColor(mapAccent, COLOR_ACCENT, 0.45f);

            guiGraphics.fill(
                    badgeX,
                    badgeY,
                    badgeX + badgeWidth,
                    badgeY + 13,
                    withAlpha(badgeColor, (int) (alpha * 0.30f)));
            drawRectBorder(guiGraphics, badgeX, badgeY, badgeWidth, 13, 1, withAlpha(badgeColor, (int) (alpha * 0.75f)));
            guiGraphics.drawCenteredString(
                    font,
                    voteText,
                    cardX + CARD_WIDTH / 2,
                    badgeY + 2,
                    withAlpha(COLOR_TEXT, (int) (alpha * 0.98f)));
        }

        if (map == selectedMap) {
            drawSelectionIndicator(guiGraphics, cardX, cardY, index, mapAccent, map.selectionTime, alpha);
        } else if (isHovered) {
            drawRectBorder(
                    guiGraphics,
                    cardX - 1,
                    cardY - 1,
                    CARD_WIDTH + 2,
                    CARD_HEIGHT + 2,
                    1,
                    withAlpha(COLOR_ACCENT, (int) (alpha * 0.40f)));
        }

        poseStack.popPose();
    }

    private void drawMapPreviewImage(GuiGraphics guiGraphics, String id, int x, int y, int width, int height) {
        try {
            ResourceLocation textureLocation = ResourceLocation.tryBuild(SRE.MOD_ID, "textures/gui/maps/" + id + ".png");
            if (textureLocation != null && textureExists(textureLocation)) {
                guiGraphics.blit(textureLocation, x, y, 0, 0, width, height, width, height);
                return;
            }
        } catch (Exception ignored) {
            // Fallback placeholder below.
        }

        drawPlaceholderImage(guiGraphics, id, x, y, width, height);
    }

    private boolean textureExists(ResourceLocation resourceLocation) {
        try {
            return Minecraft.getInstance().getResourceManager().getResource(resourceLocation).isPresent();
        } catch (Exception ignored) {
            return false;
        }
    }

    private void drawPlaceholderImage(GuiGraphics guiGraphics, String mapId, int x, int y, int width, int height) {
        guiGraphics.fillGradient(
                x,
                y,
                x + width,
                y + height,
                withAlpha(0x1B2A4F, 190),
                withAlpha(0x0C1326, 210));

        String initial = mapId.isEmpty() ? "?" : String.valueOf(Character.toUpperCase(mapId.charAt(0)));
        int textColor = withAlpha(0xC9DCFF, 175);
        int textX = x + (width - font.width(initial)) / 2;
        int textY = y + (height - font.lineHeight) / 2;
        guiGraphics.drawString(font, initial, textX, textY, textColor, false);
    }

    private void drawSelectionIndicator(GuiGraphics guiGraphics, int x, int y, int index, int mapAccent,
            float selectionAmount, int alpha) {
        int glowColor = mixColor(mapAccent, COLOR_ACCENT, 0.40f);

        for (int i = 4; i >= 1; i--) {
            float ratio = i / 4.0f;
            int inset = i * 2;
            int borderAlpha = (int) (alpha * selectionAmount * 0.12f * ratio * ratio);
            drawRectBorder(
                    guiGraphics,
                    x - inset,
                    y - inset,
                    CARD_WIDTH + inset * 2,
                    CARD_HEIGHT + inset * 2,
                    1,
                    withAlpha(glowColor, borderAlpha));
        }

        drawRectBorder(guiGraphics, x - 2, y - 2, CARD_WIDTH + 4, CARD_HEIGHT + 4, 2,
                withAlpha(glowColor, (int) (alpha * selectionAmount * 0.70f)));

        float perimeter = CARD_WIDTH * 2.0f + CARD_HEIGHT * 2.0f;
        float markerOffset = (backgroundTick * 96.0f + index * 20.0f) % perimeter;
        int markerColor = withAlpha(0xFFFFFF, (int) (230.0f * selectionAmount));
        int markerSize = 8;

        if (markerOffset < CARD_WIDTH) {
            int markerX = x + (int) markerOffset;
            guiGraphics.fill(markerX, y - 2, markerX + markerSize, y, markerColor);
        } else if (markerOffset < CARD_WIDTH + CARD_HEIGHT) {
            int markerY = y + (int) (markerOffset - CARD_WIDTH);
            guiGraphics.fill(x + CARD_WIDTH + 2, markerY, x + CARD_WIDTH + 4, markerY + markerSize, markerColor);
        } else if (markerOffset < CARD_WIDTH * 2.0f + CARD_HEIGHT) {
            int markerX = x + CARD_WIDTH - (int) (markerOffset - CARD_WIDTH - CARD_HEIGHT);
            guiGraphics.fill(markerX, y + CARD_HEIGHT + 2, markerX + markerSize, y + CARD_HEIGHT + 4, markerColor);
        } else {
            int markerY = y + CARD_HEIGHT - (int) (markerOffset - CARD_WIDTH * 2.0f - CARD_HEIGHT);
            guiGraphics.fill(x - 4, markerY, x - 2, markerY + markerSize, markerColor);
        }

        String tagText = "PICK";
        int tagWidth = font.width(tagText) + 10;
        int tagX = x + CARD_WIDTH - tagWidth - 6;
        int tagY = y + 10;
        int tagTextColor = withAlpha(0x081126, (int) (255.0f * selectionAmount));

        guiGraphics.fill(tagX, tagY, tagX + tagWidth, tagY + 12, withAlpha(glowColor, (int) (205.0f * selectionAmount)));
        guiGraphics.drawString(font, tagText, tagX + 5, tagY + 2, tagTextColor, false);
    }

    private void drawSelectionInfo(GuiGraphics guiGraphics) {
        if (selectedMap == null) {
            return;
        }

        float reveal = easeOutCubic(selectedMap.selectionTime);
        if (reveal <= 0.01f) {
            return;
        }

        int panelTop = height - BOTTOM_PANEL_HEIGHT + (int) ((1.0f - reveal) * 18.0f);
        int alpha = (int) (220.0f * reveal);

        guiGraphics.fillGradient(
                0,
                panelTop,
                width,
                height,
                withAlpha(COLOR_PANEL, alpha),
                withAlpha(COLOR_PANEL_DARK, Math.min(255, alpha + 18)));
        guiGraphics.fill(0, panelTop, width, panelTop + 1, withAlpha(COLOR_ACCENT, (int) (alpha * 0.85f)));

        guiGraphics.drawCenteredString(
                font,
                Component.translatable("gui.sre.map_selector.selected", selectedMap.displayName).withStyle(ChatFormatting.BOLD),
                width / 2,
                panelTop + 14,
                withAlpha(COLOR_TEXT, alpha));

        guiGraphics.drawCenteredString(
                font,
                Component.translatable("gui.sre.map_selector.map_id", selectedMap.id),
                width / 2,
                panelTop + 32,
                withAlpha(COLOR_TEXT_DIM, (int) (alpha * 0.95f)));

        guiGraphics.drawCenteredString(
                font,
                Component.translatable("gui.sre.map_selector.confirm_prompt"),
                width / 2,
                panelTop + 51,
                withAlpha(COLOR_TEXT_DIM, (int) (alpha * 0.82f)));
    }

    private void renderVotingTimer(GuiGraphics guiGraphics) {
        MapVotingComponent votingComponent = getVotingComponent();
        if (votingComponent == null || !votingComponent.isVotingActive()) {
            return;
        }

        int timeLeft = Math.max(0, votingComponent.getVotingTimeLeft() / 20);
        int totalTime = Math.max(1, votingComponent.getTotalVotingTime() / 20);
        float progress = Mth.clamp(timeLeft / (float) totalTime, 0.0f, 1.0f);

        String timerText = Component.translatable("gui.sre.map_selector.voting_timer", timeLeft).getString();
        int panelWidth = font.width(timerText) + 28;
        int panelX = (width - panelWidth) / 2;
        int panelY = 56;

        float urgencyPulse = timeLeft <= 10 ? (0.65f + 0.35f * (float) Math.sin(backgroundTick * 8.0f)) : 1.0f;
        int frameColor = timeLeft <= 10 ? COLOR_WARNING : COLOR_ACCENT;

        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + 18, withAlpha(COLOR_PANEL_DARK, (int) (190.0f * urgencyPulse)));
        drawRectBorder(guiGraphics, panelX, panelY, panelWidth, 18, 1, withAlpha(frameColor, (int) (220.0f * urgencyPulse)));
        guiGraphics.drawString(font, timerText, panelX + 12, panelY + 5, withAlpha(COLOR_TEXT, 245), false);

        int progressY = panelY + 18;
        guiGraphics.fill(panelX, progressY, panelX + panelWidth, progressY + 3, withAlpha(0x23304F, 230));
        guiGraphics.fill(panelX, progressY, panelX + Math.max(1, (int) (panelWidth * progress)), progressY + 3,
                withAlpha(frameColor, 245));

        guiGraphics.drawCenteredString(
                font,
                Component.translatable("gui.sre.map_selector.voting_active"),
                width / 2,
                panelY + 27,
                withAlpha(COLOR_TEXT_DIM, 220));
    }

    private int getVoteCount(String mapId) {
        MapVotingComponent votingComponent = getVotingComponent();
        if (votingComponent != null && votingComponent.isVotingActive()) {
            return votingComponent.getVoteCount(mapId);
        }
        return voteCounts.getOrDefault(mapId, 0);
    }

    private int getTotalVoteCount() {
        MapVotingComponent votingComponent = getVotingComponent();
        if (votingComponent != null && votingComponent.isVotingActive()) {
            int sum = 0;
            for (int value : votingComponent.getAllVotes().values()) {
                sum += value;
            }
            return sum;
        }

        int sum = 0;
        for (int value : voteCounts.values()) {
            sum += value;
        }
        return sum;
    }

    public void setVoteCount(String mapId, int count) {
        voteCounts.put(mapId, count);
    }

    public void addVote(String mapId) {
        voteCounts.put(mapId, getVoteCount(mapId) + 1);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredMap != null) {
            if (selectedMap == hoveredMap) {
                selectedMap = null;
            } else {
                selectedMap = hoveredMap;
                ensureMapVisible(selectedMap);
            }

            submitVote(hoveredMap);
            playClickSound();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void submitVote(MapOption map) {
        MapVotingComponent votingComponent = getVotingComponent();
        if (votingComponent == null || !votingComponent.isVotingActive() || minecraft == null || minecraft.player == null) {
            return;
        }

        ClientPlayNetworking.send(new io.wifi.starrailexpress.network.VoteForMapPayload(map.id));
        minecraft.player.displayClientMessage(
                Component.translatable("gui.sre.map_selector.selected", map.displayName).withStyle(ChatFormatting.GREEN),
                false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) {
            return false;
        }

        scrollTarget = Mth.clamp(scrollTarget - (float) deltaY * 42.0f, 0.0f, maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257) { // Enter
            confirmSelection();
            return true;
        }
        if (keyCode == 256) { // ESC
            onClose();
            return true;
        }
        if (keyCode == 263) { // Left
            moveSelection(-1);
            return true;
        }
        if (keyCode == 262) { // Right
            moveSelection(1);
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void moveSelection(int direction) {
        if (mapOptions.isEmpty()) {
            return;
        }

        int currentIndex = selectedMap == null
                ? (direction > 0 ? -1 : mapOptions.size())
                : mapOptions.indexOf(selectedMap);
        int targetIndex = Mth.clamp(currentIndex + direction, 0, mapOptions.size() - 1);

        if (targetIndex != currentIndex && targetIndex >= 0 && targetIndex < mapOptions.size()) {
            selectedMap = mapOptions.get(targetIndex);
            ensureMapVisible(selectedMap);
            playClickSound();
        }
    }

    private void ensureMapVisible(MapOption map) {
        if (map == null) {
            return;
        }

        int index = mapOptions.indexOf(map);
        if (index < 0) {
            return;
        }

        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) {
            scrollTarget = 0.0f;
            return;
        }

        float cardLeft = SIDE_PADDING + index * (CARD_WIDTH + CARD_SPACING) - scrollTarget;
        float cardRight = cardLeft + CARD_WIDTH;
        float visibleLeft = SIDE_PADDING + 8.0f;
        float visibleRight = width - SIDE_PADDING - 8.0f;

        if (cardLeft < visibleLeft) {
            scrollTarget -= (visibleLeft - cardLeft);
        } else if (cardRight > visibleRight) {
            scrollTarget += (cardRight - visibleRight);
        }

        scrollTarget = Mth.clamp(scrollTarget, 0.0f, maxScroll);
    }

    private int getMaxScroll() {
        int totalWidth = mapOptions.size() * CARD_WIDTH + Math.max(0, mapOptions.size() - 1) * CARD_SPACING;
        int visibleWidth = width - SIDE_PADDING * 2;
        return Math.max(0, totalWidth - visibleWidth);
    }

    private void confirmSelection() {
        if (selectedMap == null) {
            return;
        }

        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
        }

        MapVotingComponent votingComponent = getVotingComponent();
        if (minecraft != null && minecraft.player != null && votingComponent != null && votingComponent.isVotingActive()) {
            ClientPlayNetworking.send(new io.wifi.starrailexpress.network.VoteForMapPayload(selectedMap.id));
            minecraft.player.displayClientMessage(
                    Component.translatable("gui.sre.map_selector.voted_for", selectedMap.displayName)
                            .withStyle(ChatFormatting.GREEN),
                    false);
        } else if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.translatable("gui.sre.map_selector.selected", selectedMap.displayName)
                            .withStyle(ChatFormatting.GREEN),
                    false);
        }

        onClose();
    }

    private void playClickSound() {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.3f, 1.0f + (float) Math.random() * 0.15f);
        }
    }

    private MapVotingComponent getVotingComponent() {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        return MapVotingComponent.KEY.get(minecraft.level);
    }

    @Override
    public void onClose() {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.2f, 0.8f);
        }
        super.onClose();
    }

    private void drawRectBorder(GuiGraphics guiGraphics, int x, int y, int width, int height, int thickness, int color) {
        for (int i = 0; i < thickness; i++) {
            guiGraphics.fill(x + i, y + i, x + width - i, y + i + 1, color);
            guiGraphics.fill(x + i, y + height - i - 1, x + width - i, y + height - i, color);
            guiGraphics.fill(x + i, y + i, x + i + 1, y + height - i, color);
            guiGraphics.fill(x + width - i - 1, y + i, x + width - i, y + height - i, color);
        }
    }

    private String clipText(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        String clipped = font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(ellipsis)));
        return clipped + ellipsis;
    }

    private static float easeOutCubic(float value) {
        float clamped = Mth.clamp(value, 0.0f, 1.0f);
        float inverse = 1.0f - clamped;
        return 1.0f - inverse * inverse * inverse;
    }

    private static int withAlpha(int color, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    private static int normalizeOpaqueColor(int color) {
        return 0xFF000000 | (color & 0x00FFFFFF);
    }

    private static int mixColor(int from, int to, float factor) {
        float clamped = Mth.clamp(factor, 0.0f, 1.0f);

        int fromR = (from >> 16) & 0xFF;
        int fromG = (from >> 8) & 0xFF;
        int fromB = from & 0xFF;

        int toR = (to >> 16) & 0xFF;
        int toG = (to >> 8) & 0xFF;
        int toB = to & 0xFF;

        int r = Mth.floor(Mth.lerp(clamped, fromR, toR));
        int g = Mth.floor(Mth.lerp(clamped, fromG, toG));
        int b = Mth.floor(Mth.lerp(clamped, fromB, toB));

        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static final class MapOption {
        private final String id;
        private final String displayName;
        private final String description;
        private final int color;
        private final float introDelay;

        private float hoverTime;
        private float selectionTime;

        private MapOption(String id, String displayName, String description, int color, float introDelay) {
            this.id = id;
            this.displayName = displayName;
            this.description = description;
            this.color = color;
            this.introDelay = introDelay;
        }
    }

    private static final class Particle {
        private float x;
        private float y;
        private final float velocityX;
        private final float velocityY;
        private final float size;
        private final float baseAlpha;
        private final float twinkleSpeed;
        private final float phase;

        private Particle(float x, float y, float velocityX, float velocityY, float size,
                float baseAlpha, float phase) {
            this.x = x;
            this.y = y;
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            this.size = size;
            this.baseAlpha = baseAlpha;
            this.twinkleSpeed = 0.8f + (float) Math.random() * 1.2f;
            this.phase = phase;
        }

        private void update(int screenWidth, int screenHeight, float time) {
            x += velocityX + (float) Math.sin(time * 0.6f + phase) * 0.05f;
            y += velocityY;

            if (x < -16) {
                x = screenWidth + 16;
            } else if (x > screenWidth + 16) {
                x = -16;
            }

            if (y > screenHeight + 16) {
                y = -16;
                x = (float) (Math.random() * screenWidth);
            }
        }

        private float getAlpha(float time) {
            float twinkle = 0.6f + 0.4f * (float) Math.sin(time * twinkleSpeed + phase);
            return Mth.clamp(baseAlpha * twinkle, 0.0f, 1.0f);
        }
    }
}
