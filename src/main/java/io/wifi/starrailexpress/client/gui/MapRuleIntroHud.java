package io.wifi.starrailexpress.client.gui;

import io.wifi.starrailexpress.client.gui.screen.mapui.MapCapabilitySummary;
import io.wifi.starrailexpress.client.gui.screen.mapui.MapIntroClientCache;
import io.wifi.starrailexpress.content.vote.client.VoteFlowFrame;
import io.wifi.starrailexpress.game.data.MapConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;

/** Pure renderer/timer for the background-free map rules typography. */
public final class MapRuleIntroHud {
    private static final long DURATION_MS = 5_000L;
    private static final float FADE_IN_MS = 400.0F;
    private static final float FADE_OUT_MS = 700.0F;
    private static final ResourceLocation TMM_LETTER_ID = ResourceLocation.fromNamespaceAndPath("trainmurdermystery",
            "letter");
    private static String mapId;
    private static long shownAt;
    private static float anchorX;
    private static List<Component> rules = List.of();
    private static final List<List<FormattedCharSequence>> wrappedRules = new ArrayList<>();
    private static int wrappedForWidth = -1;

    private MapRuleIntroHud() {}

    public static void start(String id) {
        if (id == null || id.isBlank()) return;
        if (id.equals(mapId) && shownAt > 0L) return;
        mapId = id;
        shownAt = System.currentTimeMillis();
        anchorX = Float.NaN;
        rules = MapCapabilitySummary.forMap(id).ruleLines(5);
        wrappedRules.clear();
        wrappedForWidth = -1;
    }

    public static void tick(Minecraft client) {
        if (!isVisible() || client.player == null) return;
        long elapsed = System.currentTimeMillis() - shownAt;
        if (elapsed >= DURATION_MS) {
            clear();
            return;
        }
        boolean fadingOut = elapsed >= DURATION_MS - (long) FADE_OUT_MS;
        boolean holdingLetter = isLetter(client.player.getMainHandItem()) || isLetter(client.player.getOffhandItem());
        int width = client.getWindow().getGuiScaledWidth();
        float contentWidth = Mth.clamp(width * 0.30F, 230.0F, 330.0F);
        float target = holdingLetter ? width / 2.0F : width - 32.0F - contentWidth / 2.0F;
        if (Float.isNaN(anchorX)) anchorX = target;
        if (!fadingOut) anchorX = Mth.lerp(0.18F, anchorX, target);
    }

    public static void render(GuiGraphics g, float partialTick) {
        if (!isVisible()) return;
        Minecraft client = Minecraft.getInstance();
        long elapsed = System.currentTimeMillis() - shownAt;
        if (elapsed >= DURATION_MS) return;
        float fade = fadeAmount(elapsed);
        int alpha = Math.round(fade * 255.0F);
        if (alpha <= 3) return;
        float contentWidth = Mth.clamp(g.guiWidth() * 0.30F, 230.0F, 330.0F);
        if (Float.isNaN(anchorX)) anchorX = g.guiWidth() - 32.0F - contentWidth / 2.0F;
        float enter = VoteFlowFrame.ease(elapsed / 430.0F);
        int left = Math.round(anchorX - contentWidth / 2.0F + (1.0F - enter) * 24.0F);
        int y = Math.max(48, (g.guiHeight() - 178) / 2);
        int wrapWidth = Math.max(1, Math.round(contentWidth / 1.18F));
        if (wrappedForWidth != wrapWidth) {
            rebuildWrappedRules(client, wrapWidth);
        }

        Component masthead = Component.translatable("gui.sre.map_briefing.masthead")
                .withStyle(ChatFormatting.BOLD);
        drawScaled(g, masthead, left, y, 1.10F, VoteFlowFrame.withAlpha(VoteFlowFrame.GOLD_DIM, alpha));
        Component mapName = Component.literal(mapName(mapId)).withStyle(ChatFormatting.BOLD);
        drawScaled(g, mapName, left, y + 24, 1.65F, VoteFlowFrame.withAlpha(VoteFlowFrame.TEXT, alpha));
        int ruleY = y + 55;
        g.fill(left, ruleY - 8, left + Math.round(contentWidth * enter), ruleY - 6,
                VoteFlowFrame.withAlpha(VoteFlowFrame.GOLD, alpha));
        int ruleIndex = 0;
        for (List<FormattedCharSequence> lines : wrappedRules) {
            float lineIn = VoteFlowFrame.ease((elapsed - 260.0F - ruleIndex * 90.0F) / 260.0F);
            int lineAlpha = Math.round(alpha * lineIn);
            if (lineAlpha <= 3) {
                ruleY += lines.size() * 17 + 2;
                ruleIndex++;
                continue;
            }
            int lineShift = Math.round((1.0F - lineIn) * 9.0F);
            for (FormattedCharSequence line : lines) {
                drawScaled(g, line, left + lineShift, ruleY, 1.18F,
                        VoteFlowFrame.withAlpha(VoteFlowFrame.TEXT, lineAlpha));
                ruleY += 17;
            }
            ruleY += 2;
            ruleIndex++;
        }
        Component hint = Component.translatable("gui.sre.map_briefing.skip");
        drawScaled(g, hint, left, ruleY + 7, 0.95F, VoteFlowFrame.withAlpha(VoteFlowFrame.MUTED, alpha));
    }

    public static boolean isVisible() {
        return mapId != null && shownAt > 0L;
    }

    public static void clear() {
        mapId = null;
        shownAt = 0L;
        anchorX = Float.NaN;
        rules = List.of();
        wrappedRules.clear();
        wrappedForWidth = -1;
    }

    private static float fadeAmount(long elapsed) {
        float fadeIn = Mth.clamp(elapsed / FADE_IN_MS, 0.0F, 1.0F);
        float fadeOut = Mth.clamp((DURATION_MS - elapsed) / FADE_OUT_MS, 0.0F, 1.0F);
        return Math.min(fadeIn, fadeOut);
    }

    private static void rebuildWrappedRules(Minecraft client, int wrapWidth) {
        wrappedRules.clear();
        for (Component rule : rules) {
            wrappedRules.add(List.copyOf(client.font.split(rule, wrapWidth)));
        }
        wrappedForWidth = wrapWidth;
    }

    private static void drawScaled(GuiGraphics g, FormattedCharSequence text, int x, int y,
            float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(Minecraft.getInstance().font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private static void drawScaled(GuiGraphics g, Component text, int x, int y, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(Minecraft.getInstance().font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private static String mapName(String id) {
        MapConfig.MapEntry entry = MapConfig.getInstance().getMapById(id);
        String displayName = entry == null ? null : entry.getDisplayName();
        if (displayName == null || displayName.isBlank()) {
            var synced = MapIntroClientCache.getVoteMap(id);
            displayName = synced == null ? null : synced.displayName();
        }
        if (displayName == null || displayName.isBlank()) return id;
        return Component.translatableWithFallback(displayName, displayName).getString();
    }

    /** Accept the shared item singleton and the runtime registry id used by legacy TMM stacks. */
    private static boolean isLetter(ItemStack stack) {
        return stack.is(ModItems.LETTER_ITEM)
                || TMM_LETTER_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }
}
