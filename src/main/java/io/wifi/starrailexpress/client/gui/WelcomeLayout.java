package io.wifi.starrailexpress.client.gui;

/** Centers the complete announcement block, reserving space for all staged paragraphs. */
public record WelcomeLayout(float top, float scale, float titleY, float ruleY, float premiseY,
        float goalY, float contentHeight) {
    public static WelcomeLayout of(int screenHeight, float titleHeight, float premiseHeight, float goalHeight) {
        float titleY = 25;
        float ruleY = titleY + titleHeight + 12;
        float premiseY = ruleY + 17;
        float goalY = premiseY + premiseHeight + 14;
        float contentHeight = goalY + goalHeight;
        float scale = Math.min(1, Math.max(1, screenHeight - 72) / contentHeight);
        return new WelcomeLayout((screenHeight - contentHeight * scale) / 2,
                scale, titleY, ruleY, premiseY, goalY, contentHeight);
    }
}
