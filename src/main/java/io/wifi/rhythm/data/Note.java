package io.wifi.rhythm.data;

public class Note {
    public int startTime;      // 毫秒
    public int endTime;        // 0 = 非长按
    public String noteType;    // Single, Hold, HoldSingle
    public String positionType;// Left (上轨), Right (下轨)
}