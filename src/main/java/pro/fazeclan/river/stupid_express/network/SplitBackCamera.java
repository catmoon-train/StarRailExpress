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
