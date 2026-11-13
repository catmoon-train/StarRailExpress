package org.agmas.noellesroles.client.map;

/** 地图使用 NativeImage 的 ABGR 格式；计算不依赖游戏或 OpenGL。 */
final class AreaMapColor {
    private AreaMapColor() { }

    static int tint(int abgr, int rgb) {
        if (rgb == -1) return abgr;
        return (abgr & 0xFF000000)
                | ((abgr & 255) * ((rgb >>> 16) & 255) / 255)
                | ((((abgr >>> 8) & 255) * ((rgb >>> 8) & 255) / 255) << 8)
                | ((((abgr >>> 16) & 255) * (rgb & 255) / 255) << 16);
    }

    /** 前景覆盖背景，保留透明度，避免玻璃和树叶变成灰色实心块。 */
    static int over(int front, int back) {
        int a = front >>> 24, b = back >>> 24;
        int alpha = a * 255 + b * (255 - a);
        if (alpha == 0) return 0;
        int result = ((alpha + 127) / 255) << 24;
        for (int shift = 0; shift <= 16; shift += 8) {
            int channel = (((front >>> shift) & 255) * a * 255
                    + ((back >>> shift) & 255) * b * (255 - a) + alpha / 2) / alpha;
            result |= channel << shift;
        }
        return result;
    }

    static int shade(int abgr, float brightness) {
        if (abgr == 0) return 0;
        int result = abgr & 0xFF000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            int channel = Math.max(0, Math.min(255, Math.round(((abgr >>> shift) & 255) * brightness)));
            result |= channel << shift;
        }
        return result;
    }

    /** Alpha 加权取平均；透明像素的隐藏 RGB 不参与取色，最多采样 16×16 个点。 */
    static final class Average {
        private long red, green, blue, alpha;
        private int count;

        void add(int abgr) {
            int a = abgr >>> 24;
            red += (abgr & 255) * a;
            green += ((abgr >>> 8) & 255) * a;
            blue += ((abgr >>> 16) & 255) * a;
            alpha += a;
            count++;
        }

        int color() {
            if (alpha == 0) return 0;
            return ((int) (alpha / count) << 24) | (int) (red / alpha)
                    | ((int) (green / alpha) << 8) | ((int) (blue / alpha) << 16);
        }
    }
}
