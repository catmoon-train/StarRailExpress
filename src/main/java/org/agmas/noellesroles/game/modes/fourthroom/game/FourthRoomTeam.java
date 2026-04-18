package org.agmas.noellesroles.game.modes.fourthroom.game;

public enum FourthRoomTeam {
    RED,
    BLUE;

    public FourthRoomTeam opposite() {
        return this == RED ? BLUE : RED;
    }
}