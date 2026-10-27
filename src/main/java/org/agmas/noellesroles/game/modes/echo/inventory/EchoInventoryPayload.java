package org.agmas.noellesroles.game.modes.echo.inventory;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** 有界意图信封；操作者取连接上下文，交易报价最多两实例，不接收资源定义。 */
public record EchoInventoryPayload(int protocol,UUID matchId,UUID epoch,long requestId,long revision,
                                   Operation operation,UUID first,UUID second,int option,Vec3 point,Vec3 secondPoint,
                                   UUID tradeId,long tradeVersion,List<UUID> offer) implements CustomPacketPayload {
    public enum Operation {
        SNAPSHOT, BEGIN_PACK, CANCEL_PACK, CHOOSE, MOVE, DROP, CRAFT, EQUIP_CARD, USE_CARD, DISMANTLE_SCENE,
        TRADE_PROPOSE, TRADE_ACCEPT, TRADE_OFFER, TRADE_CONFIRM, TRADE_CANCEL, RETIRE;
        public boolean trade() { return ordinal()>=TRADE_PROPOSE.ordinal() && ordinal()<=TRADE_CANCEL.ordinal(); }
    }
    public EchoInventoryPayload { offer=List.copyOf(offer); if(offer.size()>2) throw new IllegalArgumentException("INVALID_OFFER"); }
    public EchoInventoryPayload(int protocol,UUID matchId,UUID epoch,long requestId,long revision,Operation operation,UUID first,UUID second,int option) {
        this(protocol,matchId,epoch,requestId,revision,operation,first,second,option,Vec3.ZERO,Vec3.ZERO);
    }
    public EchoInventoryPayload(int protocol,UUID matchId,UUID epoch,long requestId,long revision,Operation operation,UUID first,UUID second,int option,Vec3 point,Vec3 secondPoint) {
        this(protocol,matchId,epoch,requestId,revision,operation,first,second,option,point,secondPoint,NONE,0,List.of());
    }
    public static final UUID NONE=new UUID(0,0);
    public static final Type<EchoInventoryPayload> ID=new Type<>(ResourceLocation.fromNamespaceAndPath("noellesroles","echo/inventory"));
    public static final StreamCodec<RegistryFriendlyByteBuf,EchoInventoryPayload> CODEC=StreamCodec.of((buf,p) -> {
        buf.writeVarInt(p.protocol); buf.writeUUID(p.matchId); buf.writeUUID(p.epoch); buf.writeVarLong(p.requestId); buf.writeVarLong(p.revision);
        buf.writeEnum(p.operation); buf.writeUUID(p.first); buf.writeUUID(p.second); buf.writeVarInt(p.option);
        buf.writeDouble(p.point.x); buf.writeDouble(p.point.y); buf.writeDouble(p.point.z);
        buf.writeDouble(p.secondPoint.x); buf.writeDouble(p.secondPoint.y); buf.writeDouble(p.secondPoint.z);
        buf.writeUUID(p.tradeId); buf.writeVarLong(p.tradeVersion); buf.writeVarInt(p.offer.size()); p.offer.forEach(buf::writeUUID);
    },buf -> {
        int protocol=buf.readVarInt(); UUID match=buf.readUUID(),epoch=buf.readUUID(); long request=buf.readVarLong(),revision=buf.readVarLong();
        var operation=buf.readEnum(Operation.class); UUID first=buf.readUUID(),second=buf.readUUID(); int option=buf.readVarInt();
        var point=new Vec3(buf.readDouble(),buf.readDouble(),buf.readDouble()); var other=new Vec3(buf.readDouble(),buf.readDouble(),buf.readDouble());
        UUID trade=buf.readUUID(); long version=buf.readVarLong(); int count=buf.readVarInt();
        if(count<0 || count>2) throw new IllegalArgumentException("INVALID_OFFER");
        var offer=new ArrayList<UUID>(count); for(int n=0;n<count;n++) offer.add(buf.readUUID());
        return new EchoInventoryPayload(protocol,match,epoch,request,revision,operation,first,second,option,point,other,trade,version,offer);
    });
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
