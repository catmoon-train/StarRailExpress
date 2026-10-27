package org.agmas.noellesroles.game.modes.echo.state;

/** 对局阶段；安全期不接受资源写入。 */
public enum EchoPhase {
    SAFE, OPERATIONS, EVACUATION, SETTLING, FINISHED;

    public boolean acceptsActions() {
        return this == OPERATIONS || this == EVACUATION;
    }
}
