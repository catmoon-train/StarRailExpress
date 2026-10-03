package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 武士刀突刺的网络包：客户端左键（<b>不依赖准星目标，空挥也发</b>）时发出，
 * 服务端据此执行一次突刺。
 *
 * <p>存在的理由：原版左键只有命中实体才会调用 {@code Player#attack}，
 * 突刺的位移与伤害又完全不看准星目标，所以空挥时没有任何服务端入口。
 * 客户端拦截 {@code Minecraft#startAttack} 后主动发包（做法与下界合金矛的
 * {@link SpearStabC2SPacket} 一致）。
 *
 * <p>包内不带任何数据：连招进度、蓄力、职业、冷却、持刀状态全部由服务端自查，
 * 客户端无法伪造。
 */
public record KatanaThrustC2SPacket() implements CustomPacketPayload {

    public static final ResourceLocation KATANA_THRUST_PAYLOAD_ID = ResourceLocation
            .fromNamespaceAndPath(Noellesroles.MOD_ID, "katana_thrust");
    public static final CustomPacketPayload.Type<KatanaThrustC2SPacket> ID = new CustomPacketPayload.Type<>(
            KATANA_THRUST_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, KatanaThrustC2SPacket> CODEC = StreamCodec
            .ofMember(KatanaThrustC2SPacket::write, KatanaThrustC2SPacket::read);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    /** 空包：无需写出任何数据。 */
    public void write(FriendlyByteBuf buf) {
    }

    public static KatanaThrustC2SPacket read(FriendlyByteBuf buf) {
        return new KatanaThrustC2SPacket();
    }
}
