package org.agmas.noellesroles.client.screen;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 抽象像素屏幕类
 * <p>
 *     - 用于实现需要像素级放大的屏幕
 * </p>
 */
public class AbstractPixelScreen extends Screen {
    protected AbstractPixelScreen(Component component) {
        super(component);
    }
    @Override
    protected void init()
    {
        super.init();
        centerX = width / 2;
        centerY = height / 2;
    }
    protected int centerX = 0;
    protected int centerY = 0;
    protected int pixelSize = 1;// 最大（默认）像素缩放的大小
}
