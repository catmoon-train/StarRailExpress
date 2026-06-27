package org.agmas.noellesroles.game.roles.innocence.photographer;

/**
 * 鸭子接口：由 mixin 注入到 exposure 的 {@code PhotographFrameEntity}，
 * 标记该照片框是否由"摄影师"放置（用于开局只清理摄影师放置的画框）。
 */
public interface SrePhotographerFrame {
    boolean sre$isPhotographerPlaced();

    void sre$setPhotographerPlaced(boolean placed);

    /** 该画框已传送玩家的次数。 */
    int sre$getTeleportCount();

    void sre$setTeleportCount(int count);
}
