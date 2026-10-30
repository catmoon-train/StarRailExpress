package org.agmas.noellesroles.game.halloween.online;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 仅确认已经可靠提交的收益；抛错或未确认的条目仍留在本地 WAL。 */
@FunctionalInterface
public interface EarningsSink {
    Set<UUID> persist(UUID serverId, List<Earning> entries) throws IOException, InterruptedException;
}
