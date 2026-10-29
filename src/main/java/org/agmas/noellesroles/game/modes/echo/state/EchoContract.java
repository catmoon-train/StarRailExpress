package org.agmas.noellesroles.game.modes.echo.state;

/** 中立个人契约；拾荒者需要终局存活，其余可提前锁定成功。 */
public enum EchoContract {
    SMUGGLER, INFORMATION_DEALER, SCAVENGER, KEY_DEALER;

    public boolean requiresFinalSurvival() {
        return this == SCAVENGER;
    }
}
