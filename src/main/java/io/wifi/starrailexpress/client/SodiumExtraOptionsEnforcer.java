package io.wifi.starrailexpress.client;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Extra 主入口也在 Mixin 准备前加载，固定生命周期约束避免早载类注入崩溃。 */
public final class SodiumExtraOptionsEnforcer {
    private SodiumExtraOptionsEnforcer() { }

    public static void register() {
        SodiumExtraOptionPolicy.enforce(SodiumExtraClientMod.options());
        ClientTickEvents.START_CLIENT_TICK.register(client -> SodiumExtraOptionPolicy.enforce(SodiumExtraClientMod.options()));
    }
}
