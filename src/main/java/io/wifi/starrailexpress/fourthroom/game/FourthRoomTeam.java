package io.wifi.starrailexpress.fourthroom.game;

public enum FourthRoomTeam {
    RED,
    BLUE;

    public FourthRoomTeam opposite() {
        return this == RED ? BLUE : RED;
    }
}