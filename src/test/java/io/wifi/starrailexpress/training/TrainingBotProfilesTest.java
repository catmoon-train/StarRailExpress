package io.wifi.starrailexpress.training;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TrainingBotProfilesTest {
    private static final String NAME = "_srtabcdef000001";

    @Test void lookupStartsOnlyOnTheExecutorAndPreservesResolvedTextures() {
        var tasks = new ArrayDeque<Runnable>();
        var remote = new CompletableFuture<Optional<GameProfile>>();
        var called = new AtomicReference<String>();
        var result = TrainingBotProfiles.resolve(NAME, name -> {
            called.set(name);
            return remote;
        }, tasks::add, 30, TimeUnit.SECONDS);
        assertNull(called.get());
        assertFalse(result.isDone());
        tasks.remove().run();
        assertEquals(NAME, called.get());
        assertFalse(result.isDone());
        var found = new GameProfile(UUID.randomUUID(), NAME.toUpperCase(java.util.Locale.ROOT));
        found.getProperties().put("textures", new Property("textures", "cached-skin", "signature"));
        remote.complete(Optional.of(found));
        var profile = result.getNow(null);
        assertNotNull(profile);
        assertEquals(NAME, profile.getName());
        assertEquals(found.getId(), profile.getId());
        assertEquals(found.getProperties().get("textures"), profile.getProperties().get("textures"));
        assertNotSame(found, profile);
    }

    @Test void aBlockingLookupDoesNotRunOnOrBlockTheCaller() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var queryThread = new AtomicReference<Thread>();
        Thread caller = Thread.currentThread();
        try {
            var result = TrainingBotProfiles.resolve(NAME, name -> {
                queryThread.set(Thread.currentThread());
                started.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test release timed out");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return CompletableFuture.completedFuture(Optional.empty());
            }, executor, 30, TimeUnit.SECONDS);
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertNotSame(caller, queryThread.get());
            assertFalse(result.isDone());
            release.countDown();
            assertEquals(TrainingBotProfiles.offline(NAME), result.get(2, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test void missingProfilesUseStableOfflineIdentity() {
        var result = TrainingBotProfiles.resolve(NAME, name -> CompletableFuture.completedFuture(Optional.empty()),
                Runnable::run, 30, TimeUnit.SECONDS);
        assertEquals(TrainingBotProfiles.offline(NAME), result.getNow(null));
        assertEquals(NAME, result.getNow(null).getName());
        assertEquals(TrainingBotProfiles.offline(NAME).getId(), result.getNow(null).getId());
        assertNotEquals(TrainingBotProfiles.offline(NAME + "x").getId(), result.getNow(null).getId());
    }

    @Test void lookupThrowAndFailedFutureBothFallBackWithoutBreakingSpawning() {
        var thrown = TrainingBotProfiles.resolve(NAME, name -> { throw new IllegalStateException("offline"); },
                Runnable::run, 30, TimeUnit.SECONDS);
        var failed = TrainingBotProfiles.resolve(NAME, name -> CompletableFuture.failedFuture(new IllegalStateException("offline")),
                Runnable::run, 30, TimeUnit.SECONDS);
        assertEquals(TrainingBotProfiles.offline(NAME), thrown.getNow(null));
        assertEquals(TrainingBotProfiles.offline(NAME), failed.getNow(null));
    }

    @Test void timeoutReturnsOfflineAndLateNetworkCompletionCannotChangeIdentity() throws Exception {
        var remote = new CompletableFuture<Optional<GameProfile>>();
        var result = TrainingBotProfiles.resolve(NAME, name -> remote, Runnable::run, 10, TimeUnit.MILLISECONDS);
        GameProfile fallback = result.get(2, TimeUnit.SECONDS);
        assertEquals(TrainingBotProfiles.offline(NAME), fallback);
        assertFalse(remote.isDone()); // 不取消或修改原版共享缓存的请求。
        remote.complete(Optional.of(new GameProfile(UUID.randomUUID(), NAME)));
        assertSame(fallback, result.getNow(null));
    }

    @Test void cancellingOneConsumerDoesNotCancelTheSharedNetworkFuture() {
        var remote = new CompletableFuture<Optional<GameProfile>>();
        var first = TrainingBotProfiles.resolve(NAME, name -> remote, Runnable::run, 30, TimeUnit.SECONDS);
        var second = TrainingBotProfiles.resolve(NAME, name -> remote, Runnable::run, 30, TimeUnit.SECONDS);
        assertTrue(first.cancel(false));
        assertFalse(remote.isCancelled());
        remote.complete(Optional.empty());
        assertTrue(first.isCancelled());
        assertEquals(TrainingBotProfiles.offline(NAME), second.getNow(null));
    }

    @Test void wrongNameProfilesCannotReplaceReservedIdentity() {
        var result = TrainingBotProfiles.resolve(NAME,
                name -> CompletableFuture.completedFuture(Optional.of(new GameProfile(UUID.randomUUID(), "unrelatedPlayer"))),
                Runnable::run, 30, TimeUnit.SECONDS);
        assertEquals(TrainingBotProfiles.offline(NAME), result.getNow(null));
    }
}
