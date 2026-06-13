package io.wifi.starrailexpress.scenery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;

public final class SceneRegistryFingerprint {
    private static final String VERSION_PREFIX = "scene-registry-v2:";

    private SceneRegistryFingerprint() {
    }

    public static String compute(RegistryAccess registryAccess) {
        MessageDigest digest = sha256();
        updateRegistry(digest, registryAccess, Registries.BLOCK);
        updateRegistry(digest, registryAccess, Registries.BIOME);
        return VERSION_PREFIX + HexFormat.of().formatHex(digest.digest());
    }

    public static boolean isCompatible(String fingerprint, RegistryAccess registryAccess) {
        if (fingerprint == null || fingerprint.isBlank() || registryAccess == null) {
            return false;
        }
        if (fingerprint.startsWith(VERSION_PREFIX)) {
            return fingerprint.equals(compute(registryAccess));
        }
        // Older assets hashed every dynamic registry. Those registries legitimately
        // differ between dedicated servers and clients, so the legacy value is advisory.
        return fingerprint.matches("[0-9a-f]{64}");
    }

    public static boolean isLegacy(String fingerprint) {
        return fingerprint != null && fingerprint.matches("[0-9a-f]{64}");
    }

    private static <T> void updateRegistry(MessageDigest digest, RegistryAccess registryAccess,
            ResourceKey<? extends Registry<T>> registryKey) {
        Registry<T> registry = registryAccess.registryOrThrow(registryKey);
        update(digest, registryKey.location().toString());
        registry.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        (left, right) -> left.location().toString().compareTo(right.location().toString())))
                .forEach(entry -> {
                    update(digest, entry.getKey().location().toString());
                    update(digest, Integer.toString(registry.getId(entry.getValue())));
                });
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
