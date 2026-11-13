package org.agmas.noellesroles.game.roles.innocence.decree;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.*;

/** 只托管领域自己添加的短租效果，离场恢复进入前尚未过期的状态。 */
final class DecreeEffects {
    private record Lease(MobEffectInstance previous, long since, int amplifier) {}
    private static final Map<UUID, Map<Holder<MobEffect>, Lease>> LEASES = new HashMap<>();
    private DecreeEffects() {}

    static void update(ServerPlayer player, Map<Holder<MobEffect>, Integer> wanted) {
        Map<Holder<MobEffect>, Lease> leases = LEASES.get(player.getUUID());
        if (leases == null && wanted.isEmpty()) return;
        if (leases == null) {
            leases = new HashMap<>();
            LEASES.put(player.getUUID(), leases);
        }
        long now = player.level().getGameTime();
        Iterator<Map.Entry<Holder<MobEffect>, Lease>> iterator = leases.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (wanted.containsKey(entry.getKey())) continue;
            Lease lease = entry.getValue();
            MobEffectInstance current = player.getEffect(entry.getKey());
            if (current != null && current.getAmplifier() == lease.amplifier && current.getDuration() <= 24) {
                player.removeEffect(entry.getKey());
                var previous = lease.previous;
                if (previous != null) {
                    int remaining = previous.isInfiniteDuration() ? -1 : previous.getDuration() - (int) (now - lease.since);
                    if (previous.isInfiniteDuration() || remaining > 0) player.addEffect(new MobEffectInstance(entry.getKey(), remaining,
                            previous.getAmplifier(), previous.isAmbient(), previous.isVisible(), previous.showIcon()));
                }
            }
            iterator.remove();
        }
        for (var entry : wanted.entrySet()) {
            var effect = entry.getKey();
            int amplifier = entry.getValue();
            boolean entered = !leases.containsKey(effect);
            if (entered) {
                var previous = player.getEffect(effect);
                leases.put(effect, new Lease(previous == null ? null : new MobEffectInstance(previous), now, amplifier));
            }
            var current = player.getEffect(effect);
            if (entered || current == null || current.getDuration() <= 4) {
                player.addEffect(new MobEffectInstance(effect, 24, amplifier, false, false, true));
            }
        }
        if (leases.isEmpty()) LEASES.remove(player.getUUID());
    }
}
