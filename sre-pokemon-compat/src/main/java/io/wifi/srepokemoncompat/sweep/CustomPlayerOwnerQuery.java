package io.wifi.srepokemoncompat.sweep;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/** 兼容本体召唤生物的公开主人接口，按类型缓存，不读取或猜测任意实体 NBT。 */
public final class CustomPlayerOwnerQuery {
    private static final Access NONE = new Access(null, null, false);
    private static final ClassValue<Access> ACCESS = new ClassValue<>() {
        @Override protected Access computeValue(Class<?> type) {
            try {
                Method owner = find(type, new String[]{"getOwner", "getOwnerPlayer", "getOwnerEntity"}, true);
                if (owner == null) return NONE;
                boolean playerTyped = Player.class.isAssignableFrom(owner.getReturnType());
                Method uuid = playerTyped ? find(type, new String[]{"getOwnerUuid", "getOwnerUUID"}, false) : null;
                var lookup = MethodHandles.publicLookup();
                return new Access(lookup.unreflect(owner).asType(MethodType.methodType(Object.class, Object.class)),
                        uuid == null ? null : lookup.unreflect(uuid).asType(MethodType.methodType(Object.class, Object.class)), playerTyped);
            } catch (IllegalAccessException error) {
                throw new IllegalStateException("Cannot inspect public player-owner API", error);
            }
        }
    };

    public static boolean hasPlayerOwner(Object entity) { return ACCESS.get(entity.getClass()).test(entity); }

    private static Method find(Class<?> type, String[] names, boolean owner) {
        for (String name : names) {
            try {
                Method method = type.getMethod(name);
                if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) continue;
                Class<?> result = method.getReturnType();
                if (owner ? Entity.class.isAssignableFrom(result) : result == UUID.class || result == Optional.class) return method;
            } catch (NoSuchMethodException ignored) { }
        }
        return null;
    }

    private record Access(MethodHandle owner, MethodHandle uuid, boolean playerTyped) {
        boolean test(Object entity) {
            if (owner == null) return false;
            try {
                Object value = (Object) owner.invokeExact(entity);
                if (value != null) return value instanceof Player;
                if (!playerTyped || uuid == null) return false;
                Object id = (Object) uuid.invokeExact(entity);
                return id instanceof UUID || id instanceof Optional<?> optional && optional.filter(UUID.class::isInstance).isPresent();
            } catch (Throwable error) {
                throw new IllegalStateException("Cannot read public player-owner API", error);
            }
        }
    }
    private CustomPlayerOwnerQuery() { }
}
