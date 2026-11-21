/* GNU General Public License v3 or later */
package io.wifi.starrailexpress.client.ponder;

import net.createmod.ponder.api.element.PonderOverlayElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 思索里的环境染色层：给整块画面叠一层颜色，用来近似表现「紫天 / 橙天 / 夜雾」
 * 这类整局世界状态。
 *
 * <p>Ponder 的离屏沙盒没有服务端的天空、雾与全局光照入口（见
 * {@code docs/PONDERER_NOTES.md}），所以这里用屏幕染色近似；
 * 颜色优先级按 {@code docs/halloween-ambience.md} 的约定：
 * 命令方块紫色 &gt; 万圣节橙色，液体 / 失明 / 黑暗的黑雾再压过橙色。
 */
public class PonderAmbienceOverlayElement implements PonderOverlayElement {

    /** 整体染色（雾、黑暗）。 */
    public static final String MODE_FOG = "fog";
    /** 顶部渐变染色（天空）。 */
    public static final String MODE_SKY = "sky";

    private final int color;
    private final float alpha;
    private final boolean sky;
    private boolean visible = true;

    public PonderAmbienceOverlayElement(int color, float alpha, boolean sky) {
        this.color = color & 0xFFFFFF;
        this.alpha = Math.max(0.0F, Math.min(1.0F, alpha));
        this.sky = sky;
    }

    public PonderAmbienceOverlayElement addTo(SceneBuilder scene) {
        scene.addInstruction(ps -> {
            ps.addElement(this);
            this.visible = true;
        });
        return this;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public void render(PonderScene scene, PonderUI screen, GuiGraphics graphics, float partialTicks) {
        if (!visible || alpha <= 0.0F) {
            return;
        }
        int top = argb(alpha);
        int bottom = argb(alpha * 0.25F);
        if (sky) {
            // 天顶着色：上半屏最浓，往下淡出。
            int half = Math.max(1, screen.height / 2);
            graphics.fillGradient(0, 0, screen.width, half, top, bottom);
        } else {
            graphics.fillGradient(0, 0, screen.width, screen.height, top, bottom);
        }
    }

    private int argb(float a) {
        int alpha8 = Math.round(Math.max(0.0F, Math.min(1.0F, a)) * 255.0F);
        return (alpha8 << 24) | color;
    }
}
