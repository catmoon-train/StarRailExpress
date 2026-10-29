package org.agmas.noellesroles.game.modes.echo.state;

/** 生命事实与连接状态分离，倒地不是旁观或淘汰。 */
public enum EchoLifeState {
    HEALTHY, DOWNED, WOUNDED, ELIMINATED, ESCAPED, RETIRED;

    public boolean standing() {
        return this == HEALTHY || this == WOUNDED;
    }

    public boolean potentiallyEscapable() {
        return standing() || this == DOWNED;
    }
}
