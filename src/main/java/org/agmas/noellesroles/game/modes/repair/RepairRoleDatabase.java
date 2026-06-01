package org.agmas.noellesroles.game.modes.repair;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.component.RepairRolePlayerComponent;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RepairRoleDatabase {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, Set<String>>>() {}.getType();
    private static final Map<UUID, Set<String>> CACHE = new HashMap<>();
    private static Path path;

    private RepairRoleDatabase() {
    }

    public static void init(MinecraftServer server) {
        path = server.getServerDirectory().resolve("config").resolve("noellesroles_repair_roles.json");
        if (!CACHE.isEmpty()) {
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) {
                save();
                return;
            }
            Map<String, Set<String>> loaded = GSON.fromJson(Files.readString(path), TYPE);
            if (loaded != null) {
                loaded.forEach((uuid, roles) -> CACHE.put(UUID.fromString(uuid), new LinkedHashSet<>(roles)));
            }
        } catch (Exception e) {
            Noellesroles.LOGGER.warn("Failed to load repair role database", e);
        }
    }

    public static void loadInto(ServerPlayer player) {
        init(player.server);
        RepairRolePlayerComponent component = ModComponents.REPAIR_ROLES.get(player);
        component.ownedRoles.clear();
        component.ownedRoles.addAll(CACHE.computeIfAbsent(player.getUUID(), ignored -> starterRoles()));
        component.ensureStarterRoles();
        saveFrom(player);
        component.sync();
    }

    public static void saveFrom(ServerPlayer player) {
        init(player.server);
        RepairRolePlayerComponent component = ModComponents.REPAIR_ROLES.get(player);
        CACHE.put(player.getUUID(), new LinkedHashSet<>(component.ownedRoles));
        save();
    }

    public static Set<String> starterRoles() {
        Set<String> roles = new LinkedHashSet<>();
        for (RepairRoleDefinition role : RepairRoleDefinition.values()) {
            if (role.starter) {
                roles.add(role.id);
            }
        }
        return roles;
    }

    private static void save() {
        if (path == null) {
            return;
        }
        Map<String, Set<String>> data = new HashMap<>();
        CACHE.forEach((uuid, roles) -> data.put(uuid.toString(), roles));
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(data));
        } catch (IOException e) {
            Noellesroles.LOGGER.warn("Failed to save repair role database", e);
        }
    }
}
