package net.exmo.sre.sixtyseconds.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端→客户端：打开<b>统一避难所门菜单</b>（{@code ShelterDoorScreen}）。
 * 服务端按玩家上下文（相位 / 是否在搜索区 / 是否本队门）算好可用操作列表，客户端只负责展示：
 * 每个 {@link Option} 带动作 id（{@code SixtySecondsDoorMenu.ACTION_*}）、可用状态与一个数字参数
 * （存物资=携带上限、返回=剩余冷却秒数，其余为 0）。
 */
public record OpenShelterDoorS2CPacket(BlockPos pos, boolean ownDoor, int doorHp, int doorMaxHp,
        int doorLevel, boolean doorBroken, int storedSupplies, List<Option> options, int rvEntityId)
        implements CustomPacketPayload {

    public record Option(int action, boolean enabled, int param) {
    }

    /** 门（非房车）用：rvEntityId = -1，动作回传走门坐标路径。 */
    public OpenShelterDoorS2CPacket(BlockPos pos, boolean ownDoor, int doorHp, int doorMaxHp,
            int doorLevel, boolean doorBroken, int storedSupplies, List<Option> options) {
        this(pos, ownDoor, doorHp, doorMaxHp, doorLevel, doorBroken, storedSupplies, options, -1);
    }

    public static final Type<OpenShelterDoorS2CPacket> ID = new Type<>(Noellesroles.id("open_shelter_door"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenShelterDoorS2CPacket> CODEC =
            StreamCodec.ofMember(OpenShelterDoorS2CPacket::encode, OpenShelterDoorS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeBoolean(ownDoor);
        buf.writeVarInt(doorHp);
        buf.writeVarInt(doorMaxHp);
        buf.writeVarInt(doorLevel);
        buf.writeBoolean(doorBroken);
        buf.writeVarInt(storedSupplies);
        buf.writeVarInt(options.size());
        for (Option option : options) {
            buf.writeVarInt(option.action);
            buf.writeBoolean(option.enabled);
            buf.writeVarInt(option.param);
        }
        buf.writeInt(rvEntityId);
    }

    public static OpenShelterDoorS2CPacket decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        boolean ownDoor = buf.readBoolean();
        int doorHp = buf.readVarInt();
        int doorMaxHp = buf.readVarInt();
        int doorLevel = buf.readVarInt();
        boolean doorBroken = buf.readBoolean();
        int storedSupplies = buf.readVarInt();
        int count = buf.readVarInt();
        List<Option> options = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            options.add(new Option(buf.readVarInt(), buf.readBoolean(), buf.readVarInt()));
        }
        int rvEntityId = buf.readInt();
        return new OpenShelterDoorS2CPacket(pos, ownDoor, doorHp, doorMaxHp, doorLevel, doorBroken,
                storedSupplies, options, rvEntityId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
