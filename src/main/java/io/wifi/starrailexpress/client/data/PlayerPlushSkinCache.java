package io.wifi.starrailexpress.client.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import io.wifi.starrailexpress.SRE;
import io.wifi.syncrequests.SyncRequests;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端按<b>用户名</b>解析玩家皮肤（用于自定义玩家 plush 的渲染）。
 * <p>
 * 流程：用户名 → UUID（Mojang {@code api.mojang.com}）→ 带材质的 {@link GameProfile}
 * （{@code MinecraftSessionService.fetchProfile}）→ 皮肤贴图 {@link ResourceLocation}
 * （{@code SkinManager.getOrLoad}）。结果异步缓存；渲染时若尚未就绪返回 {@code null}，由渲染器回退默认皮肤。
 * <p>
 * 支持热加载：{@link #invalidate} 清除某个名字的缓存即可在下次渲染时重新拉取最新皮肤。
 */
public final class PlayerPlushSkinCache {

    /** 解析结果：皮肤贴图 + 是否纤细（Alex）模型。 */
    public record Resolved(ResourceLocation texture, boolean slim) {
    }

    private static final long RETRY_COOLDOWN_MS = 30_000L;

    private static final Map<String, Resolved> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> PENDING = new ConcurrentHashMap<>();
    private static final Map<String, Long> NEXT_RETRY = new ConcurrentHashMap<>();

    private PlayerPlushSkinCache() {
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 取该用户名的皮肤解析结果。未缓存时异步触发拉取并返回 {@code null}（本帧用默认皮肤）。
     */
    public static Resolved get(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String key = key(name);
        Resolved cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        startLoad(key, name.trim());
        return null;
    }

    /** 清除某个名字的缓存，使其下次重新拉取（热加载）。 */
    public static void invalidate(String name) {
        if (name == null) {
            return;
        }
        String key = key(name);
        CACHE.remove(key);
        NEXT_RETRY.remove(key);
    }

    private static void startLoad(String key, String name) {
        if (PENDING.putIfAbsent(key, Boolean.TRUE) != null) {
            return; // 已在拉取
        }
        Long retryAt = NEXT_RETRY.get(key);
        if (retryAt != null && System.currentTimeMillis() < retryAt) {
            PENDING.remove(key);
            return; // 处于失败冷却期
        }

        CompletableFuture
                .supplyAsync(() -> resolveProfile(name), Util.backgroundExecutor())
                .thenCompose(profile -> {
                    if (profile == null) {
                        return CompletableFuture.completedFuture((PlayerSkin) null);
                    }
                    return Minecraft.getInstance().getSkinManager().getOrLoad(profile);
                })
                .whenComplete((skin, throwable) -> {
                    PENDING.remove(key);
                    if (throwable != null || skin == null || skin.texture() == null) {
                        NEXT_RETRY.put(key, System.currentTimeMillis() + RETRY_COOLDOWN_MS);
                        if (throwable != null) {
                            SRE.LOGGER.debug("[CustomPlush] 解析玩家 {} 皮肤失败", name, throwable);
                        }
                        return;
                    }
                    CACHE.put(key, new Resolved(skin.texture(), skin.model() == PlayerSkin.Model.SLIM));
                    NEXT_RETRY.remove(key);
                });
    }

    /** 用户名 → 带材质的 GameProfile（阻塞，跑在后台线程）。失败返回 null。 */
    private static GameProfile resolveProfile(String name) {
        try {
            UUID uuid = resolveUuid(name);
            if (uuid == null) {
                return null;
            }
            var result = Minecraft.getInstance().getMinecraftSessionService().fetchProfile(uuid, false);
            return result == null ? null : result.profile();
        } catch (Exception e) {
            SRE.LOGGER.debug("[CustomPlush] 解析玩家 {} 资料失败", name, e);
            return null;
        }
    }

    /** 用户名 → UUID（Mojang API）。失败返回 null。 */
    private static UUID resolveUuid(String name) throws Exception {
        String body = SyncRequests.sendGet("https://api.mojang.com/users/profiles/minecraft/" + name);
        if (body == null || body.isBlank()) {
            return null;
        }
        JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
        if (!obj.has("id")) {
            return null;
        }
        return fromUndashed(obj.get("id").getAsString());
    }

    /** 把 32 位无连字符的十六进制 id 转成 {@link UUID}。 */
    private static UUID fromUndashed(String id) {
        if (id == null || id.length() != 32) {
            return null;
        }
        String dashed = id.substring(0, 8) + "-" + id.substring(8, 12) + "-" + id.substring(12, 16)
                + "-" + id.substring(16, 20) + "-" + id.substring(20);
        return UUID.fromString(dashed);
    }
}
