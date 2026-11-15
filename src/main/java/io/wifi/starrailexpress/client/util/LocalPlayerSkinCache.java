package io.wifi.starrailexpress.client.util;

import net.minecraft.client.resources.PlayerSkin;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;

/** 本人皮肤的会话级兜底：切服不清除成功结果，更换登录账号时隔离。仅在客户端线程使用。 */
public final class LocalPlayerSkinCache {
    private UUID account;
    private PlayerSkin lastGoodSkin;
    private CompletableFuture<PlayerSkin> accountLookup;

    /** 不替换有效的服务器皮肤；仅在原版返回自带默认皮肤时恢复本人皮肤。 */
    public PlayerSkin resolve(UUID accountId, PlayerSkin original,
                              Supplier<CompletableFuture<PlayerSkin>> loadAccountSkin) {
        if (!Objects.equals(account, accountId)) {
            account = accountId;
            lastGoodSkin = null;
            accountLookup = null;
        }
        if (accountId == null) {
            return original;
        }
        if (!isDefaultSkin(original)) {
            lastGoodSkin = original;
            return original;
        }
        if (lastGoodSkin != null) {
            return lastGoodSkin;
        }
        if (accountLookup == null) {
            // 登录档案尚未就绪时返回 null，下次查询再试，不在渲染线程 join/get。
            accountLookup = loadAccountSkin.get();
        }
        if (accountLookup != null) {
            try {
                PlayerSkin loaded = accountLookup.getNow(null);
                if (loaded != null && !isDefaultSkin(loaded)) {
                    lastGoodSkin = loaded;
                    return loaded;
                }
            } catch (CompletionException | CancellationException ignored) {
                // 无可用账号皮肤时继续使用原版结果，不让网络失败打断渲染。
            }
        }
        return original;
    }

    public static boolean isDefaultSkin(PlayerSkin skin) {
        return skin.texture().getNamespace().equals("minecraft")
                && skin.texture().getPath().startsWith("textures/entity/player/");
    }
}
