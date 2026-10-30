package org.agmas.noellesroles.game.halloween.online;

import java.util.Objects;
import java.util.UUID;

/** 一次收益的稳定凭据，重试时不得重新生成 id 或获得时间。 */
public record Earning(UUID id, UUID minecraftUuid, int amount, long earnedAt) {
    public Earning {
        Objects.requireNonNull(id);
        Objects.requireNonNull(minecraftUuid);
        if (amount <= 0 || earnedAt < 0) throw new IllegalArgumentException("Invalid earning");
    }
}
