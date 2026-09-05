package io.wifi.starrailexpress.client.gui;

import io.wifi.starrailexpress.client.gui.screen.mapui.MapCapabilitySummary;
import io.wifi.starrailexpress.content.vote.client.VoteFlowFrame;
import io.wifi.starrailexpress.game.data.MapConfig;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;

/** Pure renderer/timer for the background-free map rules typography. */
public final class MapRuleIntroHud {
    private static final long DURATION_MS = 5_000L;
    private static final ResourceLocation TMM_LETTER_ID = ResourceLocation.fromNamespaceAndPath("trainmurdermystery",
            "letter");
    private static String mapId;
    private static long shownAt;
    private static float anchorX;
    private static List<Component> rules = List.of();

    private MapRuleIntroHud() {}

    public static void start(String id) {
        mapId = id;
        shownAt = System.currentTimeMillis();
        anchorX = Float.NaN;
        rules = MapCapabilitySummary.forMap(id).ruleLines(5);
    }

    public static void tick(Minecraft client) {
        if (!isVisible() || client.player == null) return;
        boolean holdingLetter = isLetter(client.player.getMainHandItem()) || isLetter(client.player.getOffhandItem());
        int width = client.getWindow().getGuiScaledWidth();
        float contentWidth = Mth.clamp(width * 0.30F, 230.0F, 330.0F);
        float target = holdingLetter ? width / 2.0F : width - 32.0F - contentWidth / 2.0F;
        if (Float.isNaN(anchorX)) anchorX = target;
        anchorX = Mth.lerp(0.18F, anchorX, target);
        if (System.currentTimeMillis() - shownAt >= DURATION_MS) clear();
    }

    public static void render(GuiGraphics g, float partialTick) {
        if (!isVisible()) return;
        Minecraft client = Minecraft.getInstance();
        long elapsed = System.currentTimeMillis() - shownAt;
        float progress = elapsed / (float) DURATION_MS;
        float fade = Math.min(1.0F, Math.min(progress / 0.10F, (1.0F - progress) / 0.16F));
        int alpha = Math.round(Mth.clamp(fade, 0.0F, 1.0F) * 255.0F);
        float contentWidth = Mth.clamp(g.guiWidth() * 0.30F, 230.0F, 330.0F);
        int left = Math.round(anchorX - contentWidth / 2.0F);
        int y = Math.max(48, (g.guiHeight() - 178) / 2);

        Component masthead = Component.translatable("gui.sre.map_briefing.masthead")
                .withStyle(ChatFormatting.BOLD);
        drawScaled(g, masthead, left, y, 1.10F, VoteFlowFrame.withAlpha(VoteFlowFrame.GOLD_DIM, alpha));
        Component mapName = Component.literal(mapName(mapId)).withStyle(ChatFormatting.BOLD);
        drawScaled(g, mapName, left, y + 24, 1.65F, VoteFlowFrame.withAlpha(VoteFlowFrame.TEXT, alpha));
        int ruleY = y + 55;
        g.fill(left, ruleY - 8, left + Math.round(contentWidth), ruleY - 6,
                VoteFlowFrame.withAlpha(VoteFlowFrame.GOLD, alpha));
        for (Component rule : rules) {
            for (var line : client.font.split(rule, Math.round(contentWidth / 1.18F))) {
                drawScaled(g, line, left, ruleY, 1.18F, VoteFlowFrame.withAlpha(VoteFlowFrame.TEXT, alpha));
                ruleY += 17;
            }
            ruleY += 2;
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
    }

    private static void drawScaled(GuiGraphics g, net.minecraft.util.FormattedCharSequence text, int x, int y,
            float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(Minecraft.getInstance().font, text, 0, 0, color, true);
        g.pose().popPose();
    }

    private static void drawScaled(GuiGraphics g, Component text, int x, int y, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(Minecraft.getInstance().font, text, 0, 0, color, true);
        g.pose().popPose();
    }

    private static String mapName(String id) {
        MapConfig.MapEntry entry = MapConfig.getInstance().getMapById(id);
        if (entry == null || entry.getDisplayName() == null) return id;
        return Component.translatableWithFallback(entry.getDisplayName(), entry.getDisplayName()).getString();
    }

    /** Accept the shared item singleton and the runtime registry id used by legacy TMM stacks. */
    private static boolean isLetter(ItemStack stack) {
        return stack.is(ModItems.LETTER_ITEM)
                || TMM_LETTER_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }
}
