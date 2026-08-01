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

package io.wifi.starrailexpress.network.original;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.gui.HudMoodRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

public record TaskCompletePayload() implements CustomPacketPayload {
    public static final Type<TaskCompletePayload> ID = new Type<>(SRE.id("taskcomplete"));
    public static final StreamCodec<FriendlyByteBuf, TaskCompletePayload> CODEC = StreamCodec.unit(new TaskCompletePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    @Environment(EnvType.CLIENT)
    public static class Receiver implements ClientPlayNetworking.PlayPayloadHandler<TaskCompletePayload> {
        @Override
        public void receive(@NotNull TaskCompletePayload payload, ClientPlayNetworking.@NotNull Context context) {
            HudMoodRenderer.arrowProgress = 1f;
        }
    }
}