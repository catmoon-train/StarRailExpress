package org.agmas.noellesroles.game.modes.echo.map;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** 仅发给获准管理员的只读预览；数量在分配前检查，客户端无写世界权限。 */
public record EchoMapPreviewPayload(ResourceLocation dimension,List<Entry> entries) implements CustomPacketPayload {
    public static final int MAX_ENTRIES=1536;
    public record Entry(String label,EchoMapConfig.Box box,int color) {
        public Entry { if(label==null || label.length()>96) throw new IllegalArgumentException("预览标签过长"); }
    }
    public EchoMapPreviewPayload { entries=List.copyOf(entries); if(entries.size()>MAX_ENTRIES) throw new IllegalArgumentException("预览点位过多"); }
    public static final Type<EchoMapPreviewPayload> ID=new Type<>(ResourceLocation.fromNamespaceAndPath("noellesroles","echo/map_preview"));
    public static final StreamCodec<RegistryFriendlyByteBuf,EchoMapPreviewPayload> CODEC=StreamCodec.of((buf,p) -> {
        buf.writeResourceLocation(p.dimension()); buf.writeVarInt(p.entries().size());
        for(var e:p.entries()) {
            buf.writeUtf(e.label(),96); var b=e.box();
            buf.writeDouble(b.minX()); buf.writeDouble(b.minY()); buf.writeDouble(b.minZ());
            buf.writeDouble(b.maxX()); buf.writeDouble(b.maxY()); buf.writeDouble(b.maxZ()); buf.writeInt(e.color());
        }
    },buf -> {
        var dimension=buf.readResourceLocation(); int size=buf.readVarInt();
        if(size<0 || size>MAX_ENTRIES) throw new IllegalArgumentException("预览点位过多");
        var entries=new ArrayList<Entry>(size);
        for(int n=0;n<size;n++) entries.add(new Entry(buf.readUtf(96),new EchoMapConfig.Box(buf.readDouble(),buf.readDouble(),buf.readDouble(),buf.readDouble(),buf.readDouble(),buf.readDouble()),buf.readInt()));
        return new EchoMapPreviewPayload(dimension,entries);
    });
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    public static EchoMapPreviewPayload from(ResourceLocation dimension,EchoMapConfig map) {
        if(!map.coordinateSpace().equals("world")) throw new IllegalArgumentException("预览需要已平移的实战坐标");
        var entries=new ArrayList<Entry>();
        spawns(entries,"carrier",map.carrierSpawns(),0x65D5D0); spawns(entries,"interceptor",map.interceptorSpawns(),0xE77B7B); spawns(entries,"broker",map.brokerSpawns(),0xD4AF37);
        point(entries,"spectator",map.spectatorPos().pos(),0xAAAAAA);
        map.beacons().forEach(p -> point(entries,"beacon/"+p.id()+" N"+p.minPlayers(),p.pos(),0x6FEBE8));
        map.crystalNodes().forEach(p -> point(entries,"crystal/"+p.id()+" N"+p.minPlayers(),p.pos(),0xC6A1FF));
        map.altars().forEach(p -> point(entries,"altar/"+p.id(),p.pos(),0xFFEB8A));
        map.supplyCrates().forEach(p -> point(entries,p.quality()+"/"+p.id()+" N"+p.minPlayers(),p.pos(),0xBCA473));
        map.blackCrystalNodes().forEach(p -> point(entries,"black_node/"+p.id(),p.pos(),0xDB75FF));
        map.blackDropoffs().forEach(p -> point(entries,"black_dropoff/"+p.id(),p.pos(),0xC57BAC));
        point(entries,"broker_exit/"+map.brokerExit().id(),map.brokerExit().pos(),0xD4AF37);
        for(var door:map.coreGate().doors()) { point(entries,"core_control/"+door.id(),door.controlPos(),0xFF9966); entries.add(new Entry("core_door/"+door.id(),door.volume(),0xFF6655)); }
        point(entries,"free_control",map.coreGate().freeExit().controlPos(),0x99CC66); point(entries,"free_safe",map.coreGate().freeExit().outsideSafe().pos(),0x99CC66);
        point(entries,"evacuation_control",map.evacuation().controlPos(),0x88CCFF); point(entries,"evacuation_safe",map.evacuation().outsideSafe().pos(),0x88CCFF); point(entries,"drop_anchor",map.evacuation().exitDropAnchor(),0xFFFFFF);
        entries.add(new Entry("core_area",map.coreArea(),0xD4AF37)); entries.add(new Entry("evacuation_door",map.evacuation().doorVolume(),0x66AADD)); entries.add(new Entry("evacuation_zone",map.evacuation().zone(),0x66DD88));
        return new EchoMapPreviewPayload(dimension,entries);
    }
    private static void point(List<Entry> entries,String label,EchoMapConfig.Vec p,int color) { entries.add(new Entry(label,new EchoMapConfig.Box(p.x()-0.18,p.y(),p.z()-0.18,p.x()+0.18,p.y()+0.7,p.z()+0.18),color)); }
    private static void spawns(List<Entry> entries,String kind,List<EchoMapConfig.Spawn> spawns,int color) {
        for(int n=0;n<spawns.size();n++) point(entries,kind+"/"+n+" yaw="+Math.round(spawns.get(n).yaw()),spawns.get(n).pos(),color);
    }
}
