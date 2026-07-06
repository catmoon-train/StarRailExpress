package io.wifi.starrailexpress.client.gui.screen.map_dev;

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
}