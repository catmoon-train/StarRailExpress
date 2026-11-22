package org.agmas.noellesroles.game.roles.special.variant_loose_end;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * 变种亡命徒的公共基类。
 * <p>
 * 统一识别变种亡命徒并配置公共击杀资金，不承载邪恶战争的物品、分配、死亡或生存规则。
 * 新亡命徒继承本类后，公共物品与外围系统即可通过 {@link #isVariant(SRERole)} 自动识别。
 */
public abstract class VariantLooseEndRole extends NormalRole {
    protected VariantLooseEndRole(ResourceLocation identifier, int color, boolean isInnocent,
            boolean canUseKiller, MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
        // 走通用击杀奖励入口，不受子类增益回调提前返回的影响。
        setKillExtraCoinAwards(50);
    }

    /** 判断职业是否为任意变种亡命徒。 */
    public static boolean isVariant(@Nullable SRERole role) {
        return role instanceof VariantLooseEndRole;
    }

    /** 判断玩家当前是否为任意变种亡命徒。 */
    public static boolean isVariant(@Nullable Player player) {
        if (player == null || player.level() == null) {
            return false;
        }
        return isVariant(SREGameWorldComponent.KEY.get(player.level()).getRole(player));
    }
}
