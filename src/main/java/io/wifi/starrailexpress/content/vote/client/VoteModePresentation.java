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

package io.wifi.starrailexpress.content.vote.client;

import io.wifi.starrailexpress.content.vote.VoteOption;
import net.minecraft.network.chat.Component;

/** Client-side names and descriptions for known game-mode vote result ids. */
public final class VoteModePresentation {
    private VoteModePresentation() {}

    public static Component name(VoteOption option) {
        return name(option.resultId(), option.display());
    }

    public static Component name(String modeId, Component fallback) {
        String path = path(modeId);
        if (path.isBlank()) return fallback;
        return Component.translatableWithFallback("gui.sre.vote_flow.mode_name." + path, fallback.getString());
    }

    public static Component description(VoteOption option) {
        Component fallback = option.description() == null
                ? Component.translatable("gui.sre.vote_flow.mode_fallback")
                : option.description();
        String path = path(option.resultId());
        if (path.isBlank()) return fallback;
        return Component.translatableWithFallback("gui.sre.vote_flow.mode_description." + path,
                fallback.getString());
    }

    public static String path(String modeId) {
        if (modeId == null || modeId.isBlank()) return "";
        String value = modeId.startsWith("mode:") ? modeId.substring("mode:".length()) : modeId;
        int namespace = value.indexOf(':');
        return namespace >= 0 ? value.substring(namespace + 1) : value;
    }
}
