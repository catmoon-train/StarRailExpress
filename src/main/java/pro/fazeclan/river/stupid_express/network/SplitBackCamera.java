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

package pro.fazeclan.river.stupid_express.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public class SplitBackCamera implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.tryBuild("stupid_express", "split_back_camera");
    public static final Type<SplitBackCamera> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, SplitBackCamera> CODEC =
            StreamCodec.ofMember(
                    SplitBackCamera::write,
                    SplitBackCamera::read
            );
    public void write(FriendlyByteBuf buf) {
    }

    public static SplitBackCamera read(FriendlyByteBuf buf) {
        return new  SplitBackCamera();
    }


    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


}
