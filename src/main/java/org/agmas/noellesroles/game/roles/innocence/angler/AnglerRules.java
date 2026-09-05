/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.game.roles.innocence.angler;

import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 垂钓者数值常量。不进配置，避免动版本号与大段 Config。
 */
public final class AnglerRules {
    public static final ResourceLocation DEATH_EXHAUSTED = Noellesroles.id("angler_exhausted");
    public static final ResourceLocation DEATH_CATCH = Noellesroles.id("angler_catch");

    public static final float ROD_DURABILITY_PER_PLAYER = 1.5f;
    public static final int GRENADE_ODDS = 1000;

    public static final int CLAIM_RADIUS = 3;
    public static final int CLAIM_TICKS = 5 * 20;
    public static final int PATROL_MIN_TICKS = 20 * 20;
    public static final int PATROL_MAX_TICKS = 30 * 20;
    public static final int RIDE_COOLDOWN_TICKS = 15 * 20;
    public static final int ROD_RIDE_COOLDOWN_TICKS = 30 * 20;
    public static final int ROD_RIDE_DISMOUNT_GRACE_TICKS = 15;
    public static final float ROD_RIDE_SPEED = 0.32f;

    public static final int CARP_LIVE_TICKS = 3 * 20;
    public static final int CARP_RADIUS = 2;
    public static final int CARP_COINS = 30;
    public static final int CARP_ERROR_COINS = 15;

    public static final int COIN_STEAL_MAX = 30;
    public static final int HISTORY_CAP = 64;
    public static final double CATCH_SPOT_DEDUP = 2.0;

    public static final int TICKET_RETURN_TICKS = 8 * 20;
    public static final int HEART_TICKS = 10 * 20;
    public static final int GLOVE_TICKS = 10 * 20;
    public static final int HAIR_STUN_TICKS = 2 * 20;
    public static final int HAIR_SPEED_TICKS = 8 * 20;
    public static final int INK_TICKS = 4 * 20;
    public static final int WATCH_TICKS = 20 * 20;
    public static final int TOOTH_TICKS = 8 * 20;
    public static final int FLOUNDER_TICKS = 30 * 20;
    public static final int UPSIDE_DOWN_TICKS = 15 * 20;
    public static final int KELP_GLOW_TICKS = 3 * 20;
    public static final int KELP_SLOW_TICKS = 5 * 20;
    public static final double KELP_RADIUS = 4.0;
    public static final int COFFIN_DARK_TICKS = 5 * 20;
    public static final int TASK_GAIN = 15;
    public static final int TASK_LOSS = 10;
    public static final int DUPLICATE_DELETE_CHANCE = 30;
    public static final int ERROR_SELF_GRENADE_TICKS = 40;

    private AnglerRules() {
    }

    public static int rodDurability(int playerCount) {
        return Math.max(1, Math.round(playerCount * ROD_DURABILITY_PER_PLAYER));
    }

    public static int randomPatrolInterval(net.minecraft.util.RandomSource random) {
        return PATROL_MIN_TICKS + random.nextInt(PATROL_MAX_TICKS - PATROL_MIN_TICKS + 1);
    }
}
