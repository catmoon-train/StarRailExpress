package io.wifi.starrailexpress.training;

import com.mojang.authlib.GameProfile;
import net.minecraft.Util;
import net.minecraft.world.level.block.entity.SkullBlockEntity;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/** 档案查询不接触世界/玩家列表；复用原版异步缓存，超时或离线时不阻塞训练。 */
final class TrainingBotProfiles {
    private TrainingBotProfiles() { }

    static CompletableFuture<GameProfile> resolve(String name) {
        return resolve(name, SkullBlockEntity::fetchGameProfile, Util.ioPool(), 3, TimeUnit.SECONDS);
    }

    /** 入口也在后台运行，避免缓存未命中或其他模组包装的查询在调用时阻塞服务器线程。 */
    static CompletableFuture<GameProfile> resolve(String name,
            Function<String, CompletableFuture<Optional<GameProfile>>> lookup, Executor executor,
            long timeout, TimeUnit unit) {
        GameProfile fallback = offline(name);
        return CompletableFuture.supplyAsync(() -> lookup.apply(name), executor)
                .thenCompose(Function.identity())
                .handle((profile, error) -> {
                    if (error != null || profile == null || profile.isEmpty()) return fallback;
                    GameProfile found = profile.get();
                    if (found.getId() == null || !name.equalsIgnoreCase(found.getName())) return fallback;
                    // 保持请求保留名不变，缓存只提供档案 UUID 和皮肤属性。
                    GameProfile result = new GameProfile(found.getId(), name);
                    result.getProperties().putAll(found.getProperties());
                    return result;
                })
                .completeOnTimeout(fallback, timeout, unit);
    }

    static GameProfile offline(String name) {
        return new GameProfile(UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)), name);
    }
}
