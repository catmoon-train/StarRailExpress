package io.wifi.starrailexpress.client;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Sodium 主类早于 Mixin 配置准备加载，使用固定生命周期事件约束，而不是对早载类注入。
 * <p>
 * <b>不要在 {@code onInitializeClient} 阶段直接读 {@link SodiumClientMod#options()}：</b>
 * Sodium 的配置由它自己的 {@code ClientModInitializer} 加载，而 Fabric 的入口点调用顺序
 * 并不保证本模组晚于 Sodium。过早读取会抛
 * {@code IllegalStateException: Config not yet available}，直接导致客户端启动崩溃
 * <p>
 * 因此这里把强制应用整体延后到第一次 {@code START_CLIENT_TICK}（此时 Sodium 配置必然已就绪），
 * 并且每次读取都做一次「未就绪」兜底：Sodium 缺席或配置尚未可用时静默跳过，不影响启动。
 */
public final class SodiumOptionsEnforcer {
    private SodiumOptionsEnforcer() { }

    /** Sodium 配置是否已经可以安全读取；不可用时返回 false 而不抛异常。 */
    private static boolean enforceIfReady() {
        try {
            SodiumOptionPolicy.enforce(SodiumClientMod.options());
            return true;
        } catch (IllegalStateException | NoClassDefFoundError notReady) {
            // 配置尚未加载（或 Sodium 不在）：下一 tick 再试
            return false;
        }
    }

    public static void register() {
        // 不在初始化阶段读配置：只挂 tick 回调，第一次 tick 时 Sodium 一定已经初始化完毕。
        ClientTickEvents.START_CLIENT_TICK.register(client -> enforceIfReady());
    }
}
