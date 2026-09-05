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

package org.agmas.noellesroles.game.roles.innocence.great_detective;

import io.wifi.starrailexpress.game.forensic.ForensicCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.Locale;
import java.util.UUID;

/**
 * 大侦探勘察结果的聊天文案。按线索类型与内容散列选句，避免每次都是同一句「记入推理之书」。
 */
public final class DetectiveFlavor {

    public static final int VARIANTS = 3;

    private DetectiveFlavor() {
    }

    public static MutableComponent deathTime(UUID salt, Component deadName, Component formattedTime) {
        return Component.translatable(key("time", variant(salt, "time")), deadName, formattedTime);
    }

    public static MutableComponent clueLine(DetectiveClue clue) {
        int i = variant(clue.type().name() + "\0" + clue.value());
        return Component.translatable(key(clue.type().name().toLowerCase(Locale.ROOT), i), clueValue(clue));
    }

    public static MutableComponent noKiller(UUID salt, Component deadName, Component formattedTime) {
        return Component.translatable(key("no_killer", variant(salt, "nokiller")), deadName, formattedTime);
    }

    public static MutableComponent noNewClue(UUID salt, Component deadName, Component formattedTime) {
        return Component.translatable(key("no_new", variant(salt, "nonew")), deadName, formattedTime);
    }

    public static MutableComponent enoughClues(UUID salt) {
        return Component.translatable(key("enough", variant(salt, "enough")));
    }

    public static Component formatTime(int totalSeconds) {
        int mins = Math.max(0, totalSeconds) / 60;
        int secs = Math.max(0, totalSeconds) % 60;
        if (mins > 0) {
            return Component.translatable("message.noellesroles.great_detective.time.min_sec", mins, secs);
        }
        return Component.translatable("message.noellesroles.great_detective.time.sec", secs);
    }

    public static Component clueValue(DetectiveClue clue) {
        return switch (clue.type()) {
            case WEAPON -> Component.translatable(parseCategory(clue.value()).langKey);
            case ROLE -> {
                ResourceLocation id = ResourceLocation.tryParse(clue.value());
                Component name = id == null ? null : RoleUtils.getRoleName(id);
                yield name != null ? name : Component.literal(clue.value());
            }
            case MODIFIER -> {
                ResourceLocation id = ResourceLocation.tryParse(clue.value());
                Component name = id == null ? null : RoleUtils.getModifierName(id);
                yield name != null ? name : Component.literal(clue.value());
            }
            case NAME, ROOM, KILLS, NEARBY -> Component.literal(clue.value());
            case FACTION, HELD, GOLD, FLOOR, GUN, RANGE -> Component.translatable(
                    "screen.noellesroles.great_detective.value."
                            + clue.type().name().toLowerCase(Locale.ROOT) + "." + clue.value());
        };
    }

    private static ForensicCategory parseCategory(String value) {
        try {
            return ForensicCategory.valueOf(value);
        } catch (IllegalArgumentException e) {
            return ForensicCategory.UNKNOWN;
        }
    }

    private static String key(String kind, int variant) {
        return "message.noellesroles.great_detective.flavor." + kind + "." + variant;
    }

    private static int variant(UUID salt, String kind) {
        int h = salt == null ? 0 : salt.hashCode();
        h = 31 * h + kind.hashCode();
        return Math.floorMod(h, VARIANTS);
    }

    private static int variant(String salt) {
        return Math.floorMod(salt.hashCode(), VARIANTS);
    }
}
