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
        float t = (System.currentTimeMillis() - startedAt) / 720.0F;
        if (t >= 1.0F) {
            startedAt = 0L;
            return;
        }
        float eased = VoteFlowFrame.ease(t);
        int veil = (int) ((1.0F - eased) * 205.0F);
        g.fill(0, 0, width, height, VoteFlowFrame.withAlpha(0xFF080604, veil));
        int lineX = Math.round(Mth.lerp(eased, -18.0F, width + 18.0F));
        g.fill(lineX - 1, 0, lineX + 2, height,
                VoteFlowFrame.withAlpha(VoteFlowFrame.GOLD, (int) (230 * (1.0F - t))));
        if (t < 0.58F) {
            Component next = Component.translatable("gui.sre.vote_flow.next_stop");
            g.drawCenteredString(Minecraft.getInstance().font, next, width / 2, height / 2 - 4,
                    VoteFlowFrame.withAlpha(VoteFlowFrame.TEXT, (int) (255 * (1.0F - t / 0.58F))));
        }
    }
}
