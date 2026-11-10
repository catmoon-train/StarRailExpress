package pro.fazeclan.river.stupid_express.role.arsonist;

import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnPlayerDeathWithBody;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.util.ParticleFx;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.constants.SERoles;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 借鉴鸟兽兽的燃烧尸体流程，但纵火犯固定为3秒，且不依赖凶手继续存活。 */
public final class ArsonistBurnedBodies {
    private record BurningBody(ServerLevel level, long removeAt) {}
    private static final Map<UUID, BurningBody> BODIES = new HashMap<>();
    private ArsonistBurnedBodies() {}

    public static void register() {
        OnPlayerDeathWithBody.EVENT.register((victim, killer, reason, body) -> {
            if (!(victim instanceof ServerPlayer target) || body == null
                    || !reason.equals(GameConstants.DeathReasons.IGNITED) || !(killer instanceof ServerPlayer owner)
                    || !SREGameWorldComponent.KEY.get(owner.level()).isRole(owner, SERoles.ARSONIST)) return;
            body.igniteForSeconds(3);
            body.setNonInteractable(true);
            BODIES.put(body.getUUID(), new BurningBody(target.serverLevel(),
                    target.level().getGameTime() + ArsonistRules.BODY_BURN_TICKS));
            ParticleFx.sendCustom(target.serverLevel(), StupidExpress.id("arsonist_kill"), target.position().add(0, 0.8, 0),
                    ArsonistRules.BODY_BURN_TICKS);
            ServerPlayNetworking.send(target, new ArsonistPackets.DeathFxS2C());
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var iterator = BODIES.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                var burning = entry.getValue();
                var body = burning.level().getEntity(entry.getKey());
                if (!(body instanceof PlayerBodyEntity) || body.isRemoved()) { iterator.remove(); continue; }
                if (burning.level().getGameTime() >= burning.removeAt()) { body.discard(); iterator.remove(); }
            }
        });
        OnGameEnd.EVENT.register((level, game) -> BODIES.entrySet().removeIf(entry -> {
            if (entry.getValue().level() != level) return false;
            var body = level.getEntity(entry.getKey());
            if (body != null) body.discard();
            return true;
        }));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> BODIES.clear());
    }
}
