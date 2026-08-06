/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.exmo.sre.dummy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 假人管理器：创建/移除/列举带皮肤的傀儡实体，并持久化到磁盘，
 * 服务器重启后自动重生。皮肤拉取等 IO 全部异步执行，不阻塞主线程。
 */
public final class DummyManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<Map<String, Object>>>() {
    }.getType();
    private static final Map<String, DummyEntity> DUMMIES = new ConcurrentHashMap<>();
    private static final List<DummyRecord> RECORDS = new ArrayList<>();
    private static MinecraftServer server;

    private DummyManager() {
    }

    public record DummyRecord(String name, String skin, boolean invincible,
                              String world, double x, double y, double z, float yaw, float pitch) {
    }

    public static void onServerStarted(MinecraftServer minecraftServer) {
        server = minecraftServer;
        DUMMIES.clear();
        RECORDS.clear();
        RECORDS.addAll(loadRecords());
        // 清理世界存档里残留的假人实体（假人由存档记录统一管理重生，避免重复）
        for (ServerLevel level : server.getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (entity instanceof DummyEntity) {
                    entity.discard();
                }
            }
        }
        // 逐个重生持久化的假人（皮肤异步拉取，逐个接入主线程）
        for (DummyRecord record : new ArrayList<>(RECORDS)) {
            ServerLevel level = findLevel(record.world());
            if (level != null) {
                spawn(level, new Vec3(record.x(), record.y(), record.z()), record.yaw(), record.pitch(),
                    record.skin(), record.name(), record.invincible(), false);
            }
        }
        SRE.LOGGER.info("已重生 {} 个假人", RECORDS.size());
    }

    /**
     * 创建假人（主线程调用；皮肤异步拉取后接入服务器）。
     *
     * @param skinOwner  皮肤来源玩家名（在线模式拉取皮肤，失败则用默认皮肤）
     * @param label      展示名（≤16 字符，头顶显示）
     * @param invincible 是否无敌
     * @param persist    是否写入持久化（重启后重生）
     */
    public static void spawn(ServerLevel level, Vec3 pos, float yaw, float pitch,
                             String skinOwner, String label, boolean invincible, boolean persist) {
        String name = label.length() > 16 ? label.substring(0, 16) : label;
        CompletableFuture.runAsync(() -> {
            // 异步拉取皮肤（网络 IO，不阻塞主线程）：
            // 先查档案缓存，再走 GameProfileRepository 解析名字，最后 fetchProfile 拿材质
            GameProfile skinProfile = null;
            try {
                var cached = server.getProfileCache() != null ? server.getProfileCache().get(skinOwner) : java.util.Optional.<GameProfile>empty();
                GameProfile partial = cached.orElse(null);
                if (partial == null) {
                    java.util.concurrent.atomic.AtomicReference<GameProfile> ref = new java.util.concurrent.atomic.AtomicReference<>();
                    java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                    server.getProfileRepository().findProfilesByNames(new String[]{skinOwner},
                        new com.mojang.authlib.ProfileLookupCallback() {
                            @Override
                            public void onProfileLookupSucceeded(GameProfile profile) {
                                ref.set(profile);
                                latch.countDown();
                            }

                            @Override
                            public void onProfileLookupFailed(String profileName, Exception exception) {
                                latch.countDown();
                            }
                        });
                    latch.await(10, java.util.concurrent.TimeUnit.SECONDS);
                    partial = ref.get();
                }
                if (partial != null && partial.getId() != null) {
                    var result = server.getSessionService().fetchProfile(partial.getId(), true);
                    skinProfile = result != null ? result.profile() : partial;
                }
            } catch (Exception e) {
                SRE.LOGGER.warn("假人皮肤拉取失败（{}），使用默认皮肤", skinOwner);
            }
            GameProfile display = new GameProfile(UUID.randomUUID(), name);
            if (skinProfile != null && skinProfile.getProperties().containsKey("textures")) {
                for (Property property : skinProfile.getProperties().get("textures")) {
                    display.getProperties().put("textures", property);
                }
            }
            final GameProfile finalProfile = display;
            final boolean finalInvincible = invincible;
            server.execute(() -> {
                DummyEntity old = DUMMIES.remove(name);
                if (old != null) {
                    old.discard();
                }
                DummyEntity dummy = new DummyEntity(level, finalProfile, skinOwner, name, finalInvincible);
                dummy.moveTo(pos.x, pos.y, pos.z, yaw, pitch);
                level.addFreshEntity(dummy);
                DUMMIES.put(name, dummy);
                if (persist) {
                    upsertRecord(new DummyRecord(name, skinOwner, finalInvincible,
                        level.dimension().location().toString(), pos.x, pos.y, pos.z, yaw, pitch));
                    saveRecordsAsync();
                }
            });
        });
    }

    /** 移除假人。 */
    public static boolean remove(String label) {
        DummyEntity dummy = DUMMIES.remove(label);
        RECORDS.removeIf(r -> r.name().equals(label));
        saveRecordsAsync();
        if (dummy != null) {
            dummy.discard();
            return true;
        }
        return false;
    }

    public static Collection<DummyEntity> all() {
        return DUMMIES.values();
    }

    public static DummyEntity byName(String label) {
        return DUMMIES.get(label);
    }

    /** 更新假人皮肤（如排行榜第一换人时调用），异步执行。 */
    public static void refreshSkin(String label, String newSkinOwner) {
        DummyEntity dummy = DUMMIES.get(label);
        if (dummy == null) {
            return;
        }
        ServerLevel level = (ServerLevel) dummy.level();
        Vec3 pos = dummy.position();
        float yaw = dummy.getYRot();
        boolean invincible = dummy.invincible();
        remove(label);
        spawn(level, pos, yaw, dummy.getXRot(), newSkinOwner, label, invincible, true);
    }

    private static void upsertRecord(DummyRecord record) {
        RECORDS.removeIf(r -> r.name().equals(record.name()));
        RECORDS.add(record);
    }

    private static Path storageFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("starrailexpress").resolve("dummies.json");
    }

    private static List<DummyRecord> loadRecords() {
        List<DummyRecord> out = new ArrayList<>();
        Path file = storageFile();
        if (!Files.exists(file)) {
            return out;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            List<Map<String, Object>> list = GSON.fromJson(json, LIST_TYPE);
            if (list == null) {
                return out;
            }
            for (Map<String, Object> map : list) {
                out.add(new DummyRecord(
                    str(map.get("name")), str(map.get("skin")), bool(map.get("invincible")),
                    str(map.get("world")), dbl(map.get("x")), dbl(map.get("y")), dbl(map.get("z")),
                    (float) dbl(map.get("yaw")), (float) dbl(map.get("pitch"))));
            }
        } catch (Exception e) {
            SRE.LOGGER.warn("读取假人存档失败", e);
        }
        return out;
    }

    private static void saveRecordsAsync() {
        List<Map<String, Object>> list = new ArrayList<>();
        synchronized (RECORDS) {
            for (DummyRecord r : RECORDS) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("name", r.name());
                map.put("skin", r.skin());
                map.put("invincible", r.invincible());
                map.put("world", r.world());
                map.put("x", r.x());
                map.put("y", r.y());
                map.put("z", r.z());
                map.put("yaw", (double) r.yaw());
                map.put("pitch", (double) r.pitch());
                list.add(map);
            }
        }
        String json = GSON.toJson(list);
        CompletableFuture.runAsync(() -> {
            try {
                Path file = storageFile();
                Files.createDirectories(file.getParent());
                Files.writeString(file, json, StandardCharsets.UTF_8);
            } catch (Exception e) {
                SRE.LOGGER.warn("保存假人存档失败", e);
            }
        });
    }

    private static ServerLevel findLevel(String dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(dimension)) {
                return level;
            }
        }
        return server.overworld();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static boolean bool(Object o) {
        return Boolean.TRUE.equals(o);
    }

    private static double dbl(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0.0;
    }
}
