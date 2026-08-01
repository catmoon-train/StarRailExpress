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

package io.wifi.starrailexpress.content.vote;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public record ClientPlayerOption(Component display, UUID uuid, Component description) implements VoteOption {
    @Override
    public Component display() {
        return display;
    }

    @Override
    public ResourceLocation typeId() {
        return ResourceLocation.withDefaultNamespace("player");
    }

    @Override
    public boolean isPlayer() {
        return true;
    }

    @Override
    public boolean isItem() {
        return false;
    }

    /**
     * Not used for client and server
     */
    @Override
    public String resultId() {
        return "";
    }
}