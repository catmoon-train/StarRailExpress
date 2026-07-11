package io.wifi.starrailexpress.client.gui.screen.map_dev;

import net.minecraft.client.gui.screens.Screen;

public interface ModuleContext {
    double ax();

    double ay();

    double az();

    float playerYaw();

    float playerPitch();

    void sendOnly(String cmd);

    void sendAndClose(String cmd);

    double getOffsetX();

    double getOffsetY();

    double getOffsetZ();

    void setOffsetX(double v);

    void setOffsetY(double v);

    void setOffsetZ(double v);

    void resetOffsets();

    void refreshScreen();

    String quoteCommandArgument(String value);

    /**
     * 请求仅刷新当前激活的模块（不重建整个屏幕）。
     * 实现应重新初始化该模块并更新可滚动控件，同时保留固定控件和滚动偏移。
     */
    void requestModuleRefresh();

    Screen screen();
}