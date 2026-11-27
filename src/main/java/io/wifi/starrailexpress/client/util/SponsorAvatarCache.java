/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.client.util;

import com.mojang.authlib.GameProfile;
import io.wifi.starrailexpress.SRE;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 界面用「玩家头像」解析器：把一个玩家名解析成可绘制的皮肤纹理。
 * <p>
 * 解析顺序（全部异步，界面渲染永远不等待）：
 * <ol>
 *     <li>当前服务器在线玩家列表（{@link ClientPacketListener#getPlayerInfo(String)}），最准且零成本；</li>
 *     <li>离线时向 Mojang 档案服务按名字查一次（{@link SkullBlockEntity#fetchGameProfile(String)}，
 *     原版自带缓存），拿到档案后交给 {@link net.minecraft.client.resources.SkinManager#getOrLoad} 加载皮肤；</li>
 *     <li>都失败时用原版离线默认皮肤（按名字哈希挑史蒂夫 / 爱丽克丝之一）。</li>
 * </ol>
 * 每帧只读缓存，未命中时返回 {@code null}，由调用方画占位方块；
 * 只有换了新名单或重进服务器才需要重新解析。
 */
public final class SponsorAvatarCache {

    /** 解析状态：加载中 / 已加载（success 可能为 {@code null}，表示连默认皮肤都不用画）。 */
    private static final Map<String, Entry> ENTRIES = new HashMap<>();

    private SponsorAvatarCache() {
    }

    private static final class Entry {
        ResourceLocation texture;
        boolean done;
        long order;
    }

    private static long requestCounter = 0L;

    /**
     * 取该玩家名对应的头像纹理；未就绪时返回 {@code null} 并异步开始加载。
     *
     * @param name 玩家名（赞助者名单里的原始名字）
     */
    @Nullable
    public static ResourceLocation get(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String key = name.toLowerCase(Locale.ROOT);
        synchronized (ENTRIES) {
            Entry entry = ENTRIES.get(key);
            if (entry != null) {
                return entry.done ? entry.texture : null;
            }
            entry = new Entry();
            entry.order = requestCounter++;
            ENTRIES.put(key, entry);
        }
        requestAsync(name, key);
        return null;
    }

    /** 名字对应玩家是否在线（在线时列表已经带皮肤，不必等档案查询）。 */
    @Nullable
    private static PlayerInfo online(String name) {
        Minecraft client = Minecraft.getInstance();
        ClientPacketListener listener = client.getConnection();
        if (listener == null) {
            return null;
        }
        PlayerInfo info = listener.getPlayerInfo(name);
        if (info != null) {
            return info;
        }
        // 服务端名可能带后缀图标，逐个比对忽略大小写
        for (PlayerInfo candidate : listener.getOnlinePlayers()) {
            GameProfile profile = candidate.getProfile();
            if (profile != null && profile.getName() != null
                    && profile.getName().equalsIgnoreCase(name)) {
                return candidate;
            }
        }
        return null;
    }

    private static void requestAsync(String name, String key) {
        PlayerInfo info = online(name);
        if (info != null) {
            PlayerSkin skin = info.getSkin();
            if (skin != null && skin.texture() != null) {
                complete(key, skin.texture());
                return;
            }
            // 在线但皮肤还空着：稍后由下一帧重试
            remove(key);
            return;
        }

        Minecraft client = Minecraft.getInstance();
        CompletableFuture<Optional<GameProfile>> lookup;
        try {
            lookup = SkullBlockEntity.fetchGameProfile(name);
        } catch (Exception e) {
            SRE.LOGGER.debug("[Sponsor] 头像档案查询失败：{}", name, e);
            complete(key, offlineTexture(name));
            return;
        }
        lookup.thenCompose(optional -> {
            if (optional.isEmpty()) {
                return CompletableFuture.<PlayerSkin>completedFuture(null);
            }
            try {
                return client.getSkinManager().getOrLoad(optional.get());
            } catch (Exception e) {
                return CompletableFuture.<PlayerSkin>completedFuture(null);
            }
        }).whenComplete((skin, error) -> client.execute(() -> {
            if (skin != null && skin.texture() != null) {
                complete(key, skin.texture());
            } else {
                complete(key, offlineTexture(name));
            }
        }));
    }

    /** 原版离线默认皮肤（按名字哈希挑一个）。 */
    public static ResourceLocation offlineTexture(String name) {
        try {
            PlayerSkin skin = DefaultPlayerSkin.get(new GameProfile(offlineUuid(name), name));
            if (skin != null && skin.texture() != null) {
                return skin.texture();
            }
        } catch (Exception ignored) {
        }
        return DefaultPlayerSkin.getDefaultTexture();
    }

    private static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void complete(String key, @Nullable ResourceLocation texture) {
        synchronized (ENTRIES) {
            Entry entry = ENTRIES.get(key);
            if (entry == null) {
                return;
            }
            entry.texture = texture;
            entry.done = true;
        }
    }

    private static void remove(String key) {
        synchronized (ENTRIES) {
            ENTRIES.remove(key);
        }
    }

    /** 清空全部缓存（换服 / 名单刷新时调用）。 */
    public static void clear() {
        synchronized (ENTRIES) {
            ENTRIES.clear();
        }
    }

    public static int size() {
        synchronized (ENTRIES) {
            return ENTRIES.size();
        }
    }
}
