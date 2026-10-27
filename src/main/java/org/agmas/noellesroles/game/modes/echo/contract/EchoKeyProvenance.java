package org.agmas.noellesroles.game.modes.echo.contract;

import java.util.*;

/** 每钥匙与每贩子只保留最后一条有效受让链；掉地、转手、合成、偷取即时失效。 */
public final class EchoKeyProvenance {
    public record Chain(UUID instance,UUID trade,UUID dealer,UUID recipient) { }
    private final Map<UUID,Chain> chains=new HashMap<>();
    public void invalidate(UUID instance) { chains.remove(instance); }
    public void traded(UUID instance,UUID trade,UUID dealer,UUID recipient) {
        chains.values().removeIf(c -> c.dealer().equals(dealer));
        chains.put(instance,new Chain(instance,trade,dealer,recipient));
    }
    public Optional<Chain> consume(UUID instance,UUID actor) {
        var chain=chains.remove(instance);
        return chain!=null && chain.recipient().equals(actor)?Optional.of(chain):Optional.empty();
    }
    public Collection<Chain> all() { return List.copyOf(chains.values()); }
    public void clear() { chains.clear(); }
}
