package io.wifi.srepokemoncompat.sweep;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import java.lang.reflect.Method;

/** 竹叶清专用保护，不修改实体的自然消失、死亡或其他模组删除逻辑。 */
public final class SweepProtection {
    private static final Method VANILLA_OWNER_LOOKUP = vanillaOwnerLookup();
    private static final ClassValue<Boolean> PLAYER_OWNER_LOOKUP = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod(VANILLA_OWNER_LOOKUP.getName()).getDeclaringClass() == OwnableEntity.class;
            } catch (NoSuchMethodException error) {
                throw new IllegalStateException("OwnableEntity owner lookup missing", error);
            }
        }
    };

    public static boolean isProtected(Entity entity) {
        // 上游会将末影龙部件归一为本体，保护判断使用相同身份。
        if (entity instanceof EnderDragonPart part) entity = part.parentMob;
        if (entity instanceof Boat || entity instanceof AbstractMinecart) return true;
        if (!(entity instanceof LivingEntity living)) return false;
        if (living.hasCustomName()) return true;
        Boolean pokemon = PokemonSweepQuery.protectedState(entity);
        if (pokemon != null) return pokemon;
        if (entity instanceof OwnableEntity ownable) {
            LivingEntity owner = ownable.getOwner();
            if (owner != null) return owner instanceof Player;
            // 原版默认接口只按 UUID 查找玩家；即使玩家离线，也保留其驯服生物。
            // 自定义覆盖若不能解析主人类型，不把任意 NPC UUID 误认成玩家。
            if (ownable.getOwnerUUID() != null && PLAYER_OWNER_LOOKUP.get(entity.getClass())) return true;
        }
        if (entity instanceof TraceableEntity traceable && traceable.getOwner() instanceof Player) return true;
        return CustomPlayerOwnerQuery.hasPlayerOwner(entity);
    }

    private static Method vanillaOwnerLookup() {
        for (Method method : OwnableEntity.class.getMethods()) {
            // 通过类型获取运行时方法名，兼容开发 Mojmap 与生产 intermediary。
            if (method.isDefault() && method.getParameterCount() == 0 && method.getReturnType() == LivingEntity.class) return method;
        }
        throw new IllegalStateException("Vanilla player-owner lookup missing");
    }
    private SweepProtection() { }
}
