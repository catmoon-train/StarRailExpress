package org.agmas.noellesroles.api;

import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * 攀爬状态持有者。由 {@code PlayerClimbStateMixin} 挂到 {@code Player} 上，
 * 和主模组的 {@code PlayerStaminaGetter}（体力条）是同一套路子：
 * 字段挂在 Player 自身、两端各持一份，不额外发包。
 *
 * <p>用 {@link #of(Player)} 取状态，取不到（不是 Player）时返回 null。
 */
public interface PlayerClimbState {

    ClimbState noellesroles$climbState();

    @Nullable
    static ClimbState of(Player player) {
        if (player instanceof PlayerClimbState holder) {
            return holder.noellesroles$climbState();
        }
        return null;
    }
}
