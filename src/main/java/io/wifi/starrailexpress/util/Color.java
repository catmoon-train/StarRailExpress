package io.wifi.starrailexpress.util;

import java.awt.color.ColorSpace;


public class Color extends java.awt.Color {

    // 对应构造方法 1
    public Color(int r, int g, int b) {
        super(r, g, b); // 调用 Color(int, int, int)
    }

    // 对应构造方法 2
    public Color(int r, int g, int b, int a) {
        super(r, g, b, a);
    }

    // 对应构造方法 3
    public Color(int rgb) {
        super(rgb);
    }

    // 对应构造方法 4
    public Color(int rgba, boolean hasalpha) {
        super(rgba, hasalpha);
    }

    // 对应构造方法 5
    public Color(float r, float g, float b) {
        super(r, g, b);
    }

    // 对应构造方法 6
    public Color(float r, float g, float b, float a) {
        super(r, g, b, a);
    }

    // 对应构造方法 7
    public Color(ColorSpace cspace, float[] components, float alpha) {
        super(cspace, components, alpha);
    }
}