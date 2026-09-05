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

package org.agmas.noellesroles.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.game.roles.innocence.great_detective.DetectiveClue;
import org.agmas.noellesroles.game.roles.innocence.great_detective.DetectiveFlavor;

import java.util.Locale;

/**
 * 客户端：把一条 {@link DetectiveClue} 渲染成本地化的可读文本。
 */
public final class DeductionClueText {

    private DeductionClueText() {
    }

    public static Component render(DetectiveClue clue) {
        String typeKey = clue.type().name().toLowerCase(Locale.ROOT);
        return Component.translatable("screen.noellesroles.great_detective.clue." + typeKey,
                DetectiveFlavor.clueValue(clue).copy().withStyle(ChatFormatting.WHITE));
    }
}
