package io.wifi.starrailexpress.api;

import io.wifi.starrailexpress.cca.SRERoleWorldComponent;
import io.wifi.starrailexpress.event.OnRoleSkillUse;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.AbilityHandler;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.UUID;
import java.util.function.Consumer;

public class RoleSkill {
    public static record RoleSkillContext(ServerPlayer player, @Nullable UUID target) {
    }

    private static final HashMap<ResourceLocation, Consumer<RoleSkillContext>> REGISTERED_ROLE_SKILLS = new HashMap<>();

    static {
        registerDefaults();
    }

    public static boolean unregister(ResourceLocation role) {
        if (role == null) {
            return false;
        }
        if (REGISTERED_ROLE_SKILLS.containsKey(role)) {
            return false;
        }
        REGISTERED_ROLE_SKILLS.remove(role);
        return true;
    }

    public static boolean tryRegister(ResourceLocation role, Consumer<RoleSkillContext> handler) {
        if (role == null) {
            return false;
        }
        if (REGISTERED_ROLE_SKILLS.containsKey(role)) {
            return false;
        }
        REGISTERED_ROLE_SKILLS.put(role, handler);
        return true;
    }

    public static void register(ResourceLocation role, Consumer<RoleSkillContext> handler) {
        if (role == null) {
            return;
        }
        if (!tryRegister(role, handler)) {
            throw new RuntimeException("The handler of role '" + role.toString() + "' is already registered!");
        }
    }

    public static void register(SRERole role, Consumer<RoleSkillContext> handler) {
        if (role == null) {
            return;
        }
        register(role.identifier(), handler);
    }

    public static boolean isRegistered(ResourceLocation role) {
        return role != null && REGISTERED_ROLE_SKILLS.containsKey(role);
    }

    public static boolean isRegistered(SRERole role) {
        return role != null && REGISTERED_ROLE_SKILLS.containsKey(role.identifier());
    }

    public static boolean beforeUse(ServerPlayer player, SRERole role) {
        if (!isRegistered(role)) {
            return true;
        }
        return OnRoleSkillUse.BEFORE.invoker().onUse(player, role);
    }

    public static void afterUse(ServerPlayer player, SRERole role) {
        if (!isRegistered(role)) {
            return;
        }
        OnRoleSkillUse.AFTER.invoker().onUse(player, role);
    }

    public static boolean beginUse(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        SRERole role = SRERoleWorldComponent.KEY.get(player.level()).getRole(player);
        if (role == null) {
            return false;
        }
        beforeUse(player, role);
        if (REGISTERED_ROLE_SKILLS.containsKey(role.identifier())) {
            Consumer<RoleSkillContext> consumer = REGISTERED_ROLE_SKILLS.get(role.identifier());
            consumer.accept(new RoleSkillContext(player, null));
        } else {
            if (!RoleMethodDispatcher.callOnAbilityUse(player)) {
                AbilityHandler.handler(player);
            }
        }
        afterUse(player, role);
        return true;
    }

    public static boolean beginUseWithTarget(ServerPlayer player, UUID target) {
        if (player == null) {
            return false;
        }
        SRERole role = SRERoleWorldComponent.KEY.get(player.level()).getRole(player);
        if (role == null) {
            return false;
        }
        beforeUse(player, role);
        if (REGISTERED_ROLE_SKILLS.containsKey(role.identifier())) {
            Consumer<RoleSkillContext> consumer = REGISTERED_ROLE_SKILLS.get(role.identifier());
            consumer.accept(new RoleSkillContext(player, target));
        } else {
            AbilityHandler.handlerWithTarget(player, target);
        }
        afterUse(player, role);
        return true;
    }

    private static void registerDefaults() {
        // 默认不用注册
    }
}
