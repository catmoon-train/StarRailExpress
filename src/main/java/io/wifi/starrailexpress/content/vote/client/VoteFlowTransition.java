package io.wifi.starrailexpress.content.vote.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Cross-screen rail sweep joining the mode and map stages. */
public final class VoteFlowTransition {
    private static boolean armed;
    private static long startedAt;

    private VoteFlowTransition() {}

    public static void armModeToMap() {
        armed = true;
    }

    public static void beginIfArmed() {
        if (armed) {
            armed = false;
            startedAt = System.currentTimeMillis();
        }
    }

    public static void render(GuiGraphics g, int width, int height) {
        if (startedAt == 0L) return;
        float t = (System.currentTimeMillis() - startedAt) / 920.0F;
        if (t >= 1.0F) {
            startedAt = 0L;
            return;
        }
        float eased = VoteFlowFrame.ease(t);
        int veil = (int) ((1.0F - eased) * 220.0F);
        g.fill(0, 0, width, height, VoteFlowFrame.withAlpha(0xFF080604, veil));
        int lineX = Math.round(Mth.lerp(eased, -32.0F, width + 32.0F));
        g.fill(lineX - 14, 0, lineX + 15, height,
                VoteFlowFrame.withAlpha(0xFF241A0A, (int) (82 * (1.0F - t))));
        g.fill(lineX - 2, 0, lineX + 3, height,
                VoteFlowFrame.withAlpha(VoteFlowFrame.GOLD, (int) (230 * (1.0F - t))));
        int echoX = lineX - 54;
        g.fill(echoX, 0, echoX + 1, height,
                VoteFlowFrame.withAlpha(VoteFlowFrame.GOLD_DIM, (int) (100 * (1.0F - t))));
        if (t < 0.58F) {
            Component next = Component.translatable("gui.sre.vote_flow.next_stop");
            int textY = height / 2 - 4 + Math.round((1.0F - VoteFlowFrame.ease(t / 0.58F)) * 10.0F);
            g.drawCenteredString(Minecraft.getInstance().font, next, width / 2, textY,
                    VoteFlowFrame.withAlpha(VoteFlowFrame.TEXT, (int) (255 * (1.0F - t / 0.58F))));
        }
    }
}
