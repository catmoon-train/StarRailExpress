package org.agmas.noellesroles.game.modes.echo.inventory;

import java.util.List;
import java.util.UUID;

/** 本人可见的开包投影；未到OFFERED时candidates始终为空。 */
public record EchoDraftView(UUID offerId,UUID packId,String state,List<String> candidates,long deadline,long readyAt) {
    public EchoDraftView { candidates=List.copyOf(candidates); }
}
