package org.agmas.noellesroles.client.widget;

import java.util.Objects;
import java.util.OptionalInt;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.SingleKeyCache;

public class SelectedRoleIntroTextWidget extends net.minecraft.client.gui.components.AbstractStringWidget {
    private OptionalInt maxWidth;
    private OptionalInt maxRows;
    private final SingleKeyCache<CacheKey, MultiLineLabel> cache;
    private boolean centered;

    public SelectedRoleIntroTextWidget(Component component, Font font) {
        this(0, 0, component, font);
    }

    public SelectedRoleIntroTextWidget(int i, int j, Component component, Font font) {
        super(i, j, 0, 0, component, font);
        this.maxWidth = OptionalInt.empty();
        this.maxRows = OptionalInt.empty();
        this.centered = false;
        this.cache = net.minecraft.Util.singleKeyCache((cacheKey) -> {
            return cacheKey.maxRows.isPresent()
                    ? MultiLineLabel.create(font, cacheKey.maxWidth, cacheKey.maxRows.getAsInt(),
                            new Component[] { cacheKey.message })
                    : MultiLineLabel.create(font, cacheKey.message, cacheKey.maxWidth);
        });
        this.active = false;
    }

    public SelectedRoleIntroTextWidget setColor(int i) {
        super.setColor(i);
        return this;
    }

    public SelectedRoleIntroTextWidget setMaxWidth(int i) {
        this.maxWidth = OptionalInt.of(i);
        return this;
    }

    public SelectedRoleIntroTextWidget setMaxRows(int i) {
        this.maxRows = OptionalInt.of(i);
        return this;
    }

    public SelectedRoleIntroTextWidget setCentered(boolean bl) {
        this.centered = bl;
        return this;
    }

    public int getWidth() {
        // return ((MultiLineLabel) this.cache.getValue(this.getFreshCacheKey())).getWidth();
        return this.width;
    }

    public int getHeight() {
        int var10000 = ((MultiLineLabel) this.cache.getValue(this.getFreshCacheKey())).getLineCount();
        Objects.requireNonNull(this.getFont());
        return var10000 * 9;
    }

    public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
        var infoX = this.getX();
        var infoY = this.getY();
        var infoWidth = this.getWidth();
        var infoHeight = this.getHeight();
        guiGraphics.fill(infoX - 10, infoY - 10, infoX + infoWidth + 10, infoY + infoHeight + 10, 0x80000000);

        // 绘制边框
        guiGraphics.fill(infoX - 11, infoY - 11, infoX + infoWidth + 11, infoY - 10, 0xFF444444); // 上边框
        guiGraphics.fill(infoX - 11, infoY + infoHeight + 10, infoX + infoWidth + 11, infoY + infoHeight + 11,
                0xFF444444); // 下边框
        guiGraphics.fill(infoX - 11, infoY - 10, infoX - 10, infoY + infoHeight + 10, 0xFF444444); // 左边框
        guiGraphics.fill(infoX + infoWidth + 10, infoY - 10, infoX + infoWidth + 11, infoY + infoHeight + 10,
                0xFF444444); // 右边框
        MultiLineLabel multiLineLabel = (MultiLineLabel) this.cache.getValue(this.getFreshCacheKey());
        int k = this.getX();
        int l = this.getY();
        Objects.requireNonNull(this.getFont());
        int m = 9;
        int n = this.getColor();
        if (this.centered) {
            multiLineLabel.renderCentered(guiGraphics, k + this.getWidth() / 2, l, m, n);
        } else {
            multiLineLabel.renderLeftAligned(guiGraphics, k, l, m, n);
        }

    }

    private CacheKey getFreshCacheKey() {
        return new CacheKey(this.getMessage(), this.maxWidth.orElse(Integer.MAX_VALUE), this.maxRows);
    }

    @Environment(EnvType.CLIENT)
    static record CacheKey(Component message, int maxWidth, OptionalInt maxRows) {
        CacheKey(Component message, int maxWidth, OptionalInt maxRows) {
            this.message = message;
            this.maxWidth = maxWidth;
            this.maxRows = maxRows;
        }

        public Component message() {
            return this.message;
        }

        public int maxWidth() {
            return this.maxWidth;
        }

        public OptionalInt maxRows() {
            return this.maxRows;
        }
    }

}
