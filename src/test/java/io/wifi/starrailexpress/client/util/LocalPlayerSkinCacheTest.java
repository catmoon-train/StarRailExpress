package io.wifi.starrailexpress.client.util;

import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LocalPlayerSkinCacheTest {
    private final LocalPlayerSkinCache cache = new LocalPlayerSkinCache();
    private final UUID account = UUID.randomUUID();
    private final PlayerSkin fallback = skin("minecraft:textures/entity/player/wide/steve.png", PlayerSkin.Model.WIDE);
    private final PlayerSkin own = skin("minecraft:skins/my-skin", PlayerSkin.Model.SLIM);

    private static PlayerSkin skin(String path, PlayerSkin.Model model) {
        return new PlayerSkin(ResourceLocation.parse(path), null, null, null, model, true);
    }

    private PlayerSkin resolve(PlayerSkin original) {
        return cache.resolve(account, original, () -> null);
    }

    @Test void keepsOwnSkinWhenNewServerReturnsDefault() {
        assertSame(own, resolve(own));
        assertSame(own, resolve(fallback));
        assertSame(own, resolve(fallback));
    }

    @Test void resourceReloadDoesNotOverwriteSuccessfulResultWithDefault() {
        resolve(own);
        assertSame(own, resolve(skin("minecraft:textures/entity/player/slim/alex.png", PlayerSkin.Model.SLIM)));
        assertEquals(PlayerSkin.Model.SLIM, resolve(fallback).model());
    }

    @Test void validNewServerSkinAlwaysWins() {
        resolve(own);
        PlayerSkin updated = skin("minecraft:skins/new-skin", PlayerSkin.Model.WIDE);
        assertSame(updated, resolve(updated));
        assertSame(updated, resolve(fallback));
    }

    @Test void pendingAccountSkinDoesNotBlockAndRecoversWhenComplete() {
        var request = new CompletableFuture<PlayerSkin>();
        assertSame(fallback, cache.resolve(account, fallback, () -> request));
        request.complete(own);
        assertSame(own, resolve(fallback));
    }

    @Test void accountLookupStartsOnlyOnceWhilePending() {
        var requests = new AtomicInteger();
        var request = new CompletableFuture<PlayerSkin>();
        for (int i = 0; i < 5; i++) {
            assertSame(fallback, cache.resolve(account, fallback, () -> {
                requests.incrementAndGet();
                return request;
            }));
        }
        assertEquals(1, requests.get());
    }

    @Test void profileNotReadyCanBeRetriedOnNextRead() {
        assertSame(fallback, resolve(fallback));
        assertSame(own, cache.resolve(account, fallback, () -> CompletableFuture.completedFuture(own)));
    }

    @Test void differentAccountCannotReusePreviousSkin() {
        resolve(own);
        assertSame(fallback, cache.resolve(UUID.randomUUID(), fallback, () -> null));
    }

    @Test void oldAccountFutureCannotOverwriteCurrentAccount() {
        var oldRequest = new CompletableFuture<PlayerSkin>();
        cache.resolve(account, fallback, () -> oldRequest);
        UUID nextAccount = UUID.randomUUID();
        var newRequest = new CompletableFuture<PlayerSkin>();
        cache.resolve(nextAccount, fallback, () -> newRequest);
        oldRequest.complete(own);
        assertSame(fallback, cache.resolve(nextAccount, fallback, () -> null));
        PlayerSkin nextSkin = skin("minecraft:skins/next-account", PlayerSkin.Model.WIDE);
        newRequest.complete(nextSkin);
        assertSame(nextSkin, cache.resolve(nextAccount, fallback, () -> null));
    }

    @Test void failedAndCancelledRequestsKeepVanillaFallback() {
        assertSame(fallback, cache.resolve(account, fallback,
                () -> CompletableFuture.failedFuture(new IllegalStateException("network"))));
        var cancelled = new CompletableFuture<PlayerSkin>();
        cancelled.cancel(false);
        assertSame(fallback, new LocalPlayerSkinCache().resolve(account, fallback, () -> cancelled));
    }

    @Test void defaultAccountSkinIsNotRememberedAsSuccess() {
        assertSame(fallback, cache.resolve(account, fallback, () -> CompletableFuture.completedFuture(fallback)));
        assertSame(own, resolve(own));
        assertSame(own, resolve(fallback));
    }

    @Test void existingSkinDoesNotTriggerAccountLookup() {
        assertSame(own, cache.resolve(account, own, () -> fail("有效皮肤不应额外加载账号皮肤")));
        assertSame(own, cache.resolve(account, fallback, () -> fail("已缓存本人皮肤不应重新加载")));
    }

    @Test void onlyVanillaPlayerTexturePathsAreDefaults() {
        assertTrue(LocalPlayerSkinCache.isDefaultSkin(fallback));
        assertFalse(LocalPlayerSkinCache.isDefaultSkin(own));
        assertFalse(LocalPlayerSkinCache.isDefaultSkin(skin("example:textures/entity/player/skin.png", PlayerSkin.Model.WIDE)));
    }
}
