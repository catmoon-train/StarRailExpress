/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package io.wifi.starrailexpress.traindummy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import io.wifi.starrailexpress.SRE;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.StringUtil;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * 异步拉取正版皮肤，不阻塞主线程。失败时假人仍用默认皮肤。
 */
public final class TrainDummySkins {

    private TrainDummySkins() {
    }

    public static void fetch(MinecraftServer server, String name, BiConsumer<GameProfile, Boolean> callback) {
        if (server == null || name == null || name.isBlank() || !StringUtil.isValidPlayerName(name)) {
            return;
        }
        String skinOwner = name.length() > 16 ? name.substring(0, 16) : name;
        CompletableFuture.runAsync(() -> {
            GameProfile profile = null;
            try {
                var cached = server.getProfileCache() != null
                        ? server.getProfileCache().get(skinOwner)
                        : java.util.Optional.<GameProfile>empty();
                GameProfile partial = cached.orElse(null);
                if (partial == null && server.getProfileRepository() != null) {
                    AtomicReference<GameProfile> ref = new AtomicReference<>();
                    CountDownLatch latch = new CountDownLatch(1);
                    server.getProfileRepository().findProfilesByNames(new String[] { skinOwner },
                            new com.mojang.authlib.ProfileLookupCallback() {
                                @Override
                                public void onProfileLookupSucceeded(GameProfile found) {
                                    ref.set(found);
                                    latch.countDown();
                                }

                                @Override
                                public void onProfileLookupFailed(String profileName, Exception exception) {
                                    latch.countDown();
                                }
                            });
                    latch.await(8, TimeUnit.SECONDS);
                    partial = ref.get();
                }
                if (partial != null && partial.getId() != null && server.getSessionService() != null) {
                    var result = server.getSessionService().fetchProfile(partial.getId(), true);
                    profile = result != null ? result.profile() : partial;
                } else {
                    profile = partial;
                }
            } catch (Exception e) {
                SRE.LOGGER.warn("列车假人皮肤拉取失败（{}）", skinOwner);
            }
            GameProfile resolved = profile;
            boolean slim = isSlim(resolved);
            server.execute(() -> {
                if (resolved != null) {
                    callback.accept(resolved, slim);
                }
            });
        });
    }

    private static boolean isSlim(GameProfile profile) {
        if (profile == null || !profile.getProperties().containsKey("textures")) {
            return false;
        }
        for (Property property : profile.getProperties().get("textures")) {
            try {
                String json = new String(Base64.getDecoder().decode(property.value()), StandardCharsets.UTF_8);
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                if (root.has("textures")) {
                    JsonObject textures = root.getAsJsonObject("textures");
                    if (textures.has("SKIN")) {
                        JsonObject skin = textures.getAsJsonObject("SKIN");
                        if (skin.has("metadata") && skin.getAsJsonObject("metadata").has("model")) {
                            return "slim".equalsIgnoreCase(skin.getAsJsonObject("metadata").get("model").getAsString());
                        }
                    }
                }
            } catch (Exception ignored) {
                return false;
            }
        }
        return false;
    }

    public static UUID fallbackId(String name) {
        return UUID.nameUUIDFromBytes(("TrainDummy:" + name).getBytes(StandardCharsets.UTF_8));
    }
}
