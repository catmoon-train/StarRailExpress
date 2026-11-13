package org.agmas.noellesroles.game.minigame.roulette;

import java.util.UUID;

/** 只验证选择请求的授权外壳；具体背包快照和道具条件仍由服务端逐项重验。 */
public final class RouletteSelectionPolicy {
    private RouletteSelectionPolicy() {}
    public static boolean authorized(UUID session, UUID requestSession, UUID token, UUID requestToken,
            UUID owner, UUID requester, boolean ready, boolean operator, boolean seated, boolean inRange, boolean present) {
        return session != null && session.equals(requestSession) && token != null && token.equals(requestToken)
                && owner != null && owner.equals(requester) && ready && operator && seated && inRange && present;
    }
    public static boolean stealable(RouletteItem item, boolean sourceMatches, boolean syringeMatches, boolean effectAllowed) {
        return item != null && item != RouletteItem.ADRENALINE && sourceMatches && syringeMatches && effectAllowed;
    }
}
