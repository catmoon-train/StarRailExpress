package io.wifi.starrailexpress.client;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Sodium 主类早于 Mixin 配置准备加载，使用固定生命周期事件约束，而不是对早载类注入。 */
public final class SodiumOptionsEnforcer {
    private SodiumOptionsEnforcer() { }

    public static void register() {
        SodiumOptionPolicy.enforce(SodiumClientMod.options());
        ClientTickEvents.START_CLIENT_TICK.register(client -> SodiumOptionPolicy.enforce(SodiumClientMod.options()));
    }
}
