package org.agmas.noellesroles.game.modes.echo.map;

import com.google.gson.*;
import org.agmas.noellesroles.game.modes.echo.rules.EchoRosterPolicy;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/** 仅存模板坐标的内存草稿；先在副本解析成功再替换，失败不会破坏管理员已有编辑。 */
public final class EchoMapDraft {
    private static final Gson GSON=new Gson();
    public static final List<String> KINDS=List.of("carrier_spawn","interceptor_spawn","broker_spawn","spectator","beacon_open","beacon_core","crystal","altar","supply","black_market","black_node","black_dropoff","broker_exit","core_control","free_control","free_safe","evacuation_control","evacuation_safe","drop_anchor");
    public static final List<String> VOLUMES=List.of("core_area","core_door","evacuation_door","evacuation_zone");
    private JsonObject root;
    private long revision;
    public EchoMapDraft(EchoMapConfig source) throws IOException {
        if(!source.coordinateSpace().equals("template")) throw new IOException("编辑草稿必须使用模板坐标");
        EchoMapRepository.requireMapId(source.mapId()); root=GSON.toJsonTree(source).getAsJsonObject();
    }
    public static EchoMapDraft empty(String mapId) throws IOException {
        var p=new EchoMapConfig.Vec(0,64,0); var s=new EchoMapConfig.Spawn(0.5,64,0.5,0,0);
        return new EchoMapDraft(new EchoMapConfig(1,mapId,"template",new EchoMapConfig.Box(-4,64,-4,5,68,5),
                List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),
                new EchoMapConfig.CoreGate(List.of(),new EchoMapConfig.FreeExit("service_exit",p,s)),
                new EchoMapConfig.Evacuation(p,new EchoMapConfig.Box(8,64,8,9,67,9),new EchoMapConfig.Box(10,64,10,11,68,11),s,p),
                List.of(),List.of(),new EchoMapConfig.Point("broker_exit",p),s));
    }
    public long revision() { return revision; }
    public EchoMapConfig snapshot() throws IOException { return EchoMapRepository.parse(root.toString()); }
    public EchoMapConfig exported(String mapId) throws IOException {
        EchoMapRepository.requireMapId(mapId); var copy=root.deepCopy(); copy.addProperty("mapId",mapId); return EchoMapRepository.parse(copy.toString());
    }
    private void update(Consumer<JsonObject> change) throws IOException {
        var copy=root.deepCopy(); change.accept(copy); EchoMapRepository.parse(copy.toString()); root=copy; revision++;
    }
    private static JsonObject vec(EchoMapConfig.Vec p) { return GSON.toJsonTree(p).getAsJsonObject(); }
    private static JsonObject spawn(EchoMapConfig.Vec p,float yaw,float pitch) { return GSON.toJsonTree(new EchoMapConfig.Spawn(p.x(),p.y(),p.z(),yaw,pitch)).getAsJsonObject(); }
    private static void id(String id) { if(id==null || !id.matches("[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("点位id必须是1–64位小写字母、数字、下划线或连字符"); }
    private static JsonObject entry(JsonArray array,String id) {
        for(var element:array) if(element.getAsJsonObject().get("id").getAsString().equals(id)) return element.getAsJsonObject();
        if(array.size()>=128) throw new IllegalArgumentException("点位数组超过128项");
        var next=new JsonObject(); next.addProperty("id",id); array.add(next); return next;
    }
    private static void putSpawn(JsonObject tree,String field,String id,JsonObject value) {
        var array=tree.getAsJsonArray(field);
        int index;
        if(id==null || id.isBlank()) {
            // 留空 = 追加到队尾（界面提示「下一个下标」就是当前长度）
            index=array.size();
        } else {
            try { index=Integer.parseInt(id.trim()); } catch(NumberFormatException invalid) { throw new IllegalArgumentException("出生点id必须是0起的连续下标"); }
        }
        if(index<0 || index>array.size() || index>=128)
            throw new IllegalArgumentException("出生点下标越界：当前已有"+array.size()+"个，新点请填"+array.size()+"或留空追加");
        if(index==array.size()) array.add(value); else array.set(index,value);
    }
    public void mark(String kind,String id,EchoMapConfig.Vec point,float yaw,float pitch,int minPlayers) throws IOException {
        if(!KINDS.contains(kind)) throw new IllegalArgumentException("未知点位kind: "+kind);
        if(minPlayers<EchoRosterPolicy.MIN_PLAYERS || minPlayers>EchoRosterPolicy.MAX_PLAYERS) throw new IllegalArgumentException("minPlayers必须为6–32");
        // 出生点允许留空（自动追加）；其余类型必须给 id
        if(!kind.endsWith("_spawn")) id(id);
        update(tree -> {
            JsonObject gate=tree.getAsJsonObject("coreGate"),evacuation=tree.getAsJsonObject("evacuation");
            switch(kind) {
                case "carrier_spawn" -> putSpawn(tree,"carrierSpawns",id,spawn(point,yaw,pitch));
                case "interceptor_spawn" -> putSpawn(tree,"interceptorSpawns",id,spawn(point,yaw,pitch));
                case "broker_spawn" -> putSpawn(tree,"brokerSpawns",id,spawn(point,yaw,pitch));
                case "spectator" -> tree.add("spectatorPos",spawn(point,yaw,pitch));
                case "free_control" -> { gate.getAsJsonObject("freeExit").addProperty("id",id); gate.getAsJsonObject("freeExit").add("controlPos",vec(point)); }
                case "free_safe" -> gate.getAsJsonObject("freeExit").add("outsideSafe",spawn(point,yaw,pitch));
                case "evacuation_control" -> evacuation.add("controlPos",vec(point));
                case "evacuation_safe" -> evacuation.add("outsideSafe",spawn(point,yaw,pitch));
                case "drop_anchor" -> evacuation.add("exitDropAnchor",vec(point));
                case "broker_exit" -> { var e=new JsonObject(); e.addProperty("id",id); e.add("pos",vec(point)); tree.add("brokerExit",e); }
                case "core_control" -> {
                    var e=entry(gate.getAsJsonArray("doors"),id); e.add("controlPos",vec(point));
                    if(!e.has("volume")) e.add("volume",GSON.toJsonTree(new EchoMapConfig.Box(Math.floor(point.x()),Math.floor(point.y()),Math.floor(point.z()),Math.floor(point.x())+1,Math.floor(point.y())+1,Math.floor(point.z())+1)));
                }
                default -> {
                    String field=switch(kind) {
                        case "beacon_open","beacon_core" -> "beacons"; case "crystal" -> "crystalNodes"; case "altar" -> "altars";
                        case "supply","black_market" -> "supplyCrates"; case "black_node" -> "blackCrystalNodes"; case "black_dropoff" -> "blackDropoffs";
                        default -> throw new IllegalArgumentException("未知点位kind");
                    };
                    var e=entry(tree.getAsJsonArray(field),id); e.add("pos",vec(point));
                    if(kind.startsWith("beacon_")) { e.addProperty("region",kind.equals("beacon_core")?"core":"open"); e.addProperty("minPlayers",minPlayers); }
                    if(kind.equals("crystal")) e.addProperty("minPlayers",minPlayers);
                    if(kind.equals("supply") || kind.equals("black_market")) {
                        e.addProperty("minPlayers",minPlayers); e.addProperty("quality",kind.equals("supply")?"COMMON":"BLACK_MARKET"); e.addProperty("repeatable",kind.equals("supply"));
                    }
                }
            }
        });
    }
    public void volume(String kind,String id,EchoMapConfig.Box volume) throws IOException {
        if(!VOLUMES.contains(kind)) throw new IllegalArgumentException("未知区域kind: "+kind);
        if(!volume.integral() || volume.volume()>1_000_000) throw new IllegalArgumentException("区域必须为整数半开框，且体积不超过100万");
        if((kind.equals("core_door") || kind.equals("evacuation_door")) && volume.volume()>1024) throw new IllegalArgumentException("门体体积超过1024");
        update(tree -> {
            var value=GSON.toJsonTree(volume);
            switch(kind) {
                case "core_area" -> tree.add("coreArea",value);
                case "evacuation_door" -> tree.getAsJsonObject("evacuation").add("doorVolume",value);
                case "evacuation_zone" -> tree.getAsJsonObject("evacuation").add("zone",value);
                case "core_door" -> {
                    JsonObject door=null;
                    for(var e:tree.getAsJsonObject("coreGate").getAsJsonArray("doors")) if(e.getAsJsonObject().get("id").getAsString().equals(id)) door=e.getAsJsonObject();
                    if(door==null) throw new IllegalArgumentException("请先mark core_control，设置同id控制点");
                    door.add("volume",value);
                }
                default -> throw new IllegalArgumentException("未知区域kind");
            }
        });
    }
    public void remove(String field,String id) throws IOException {
        if(!List.of("carrierSpawns","interceptorSpawns","brokerSpawns","beacons","crystalNodes","altars","supplyCrates","blackCrystalNodes","blackDropoffs","doors").contains(field)) throw new IllegalArgumentException("只能删除列表内点位");
        update(tree -> {
            var array=field.equals("doors")?tree.getAsJsonObject("coreGate").getAsJsonArray(field):tree.getAsJsonArray(field);
            boolean found=false;
            for(int i=array.size()-1;i>=0;i--) if(field.endsWith("Spawns")?Integer.toString(i).equals(id):array.get(i).getAsJsonObject().get("id").getAsString().equals(id)) { array.remove(i); found=true; }
            if(!found) throw new IllegalArgumentException("不存在该点位");
        });
    }

    // ==================== 点位查询与就地修改（编辑器列表用） ====================

    /** 一个点位的摘要（模板坐标）：id / 人数档（0 = 该类型没有人数档）/ 坐标。 */
    public record PointInfo(String id,int players,double x,double y,double z) { }

    private static String fieldOf(String kind) {
        return switch(kind) {
            case "beacon_open","beacon_core" -> "beacons"; case "crystal" -> "crystalNodes"; case "altar" -> "altars";
            case "supply","black_market" -> "supplyCrates"; case "black_node" -> "blackCrystalNodes"; case "black_dropoff" -> "blackDropoffs";
            case "core_control" -> "doors";
            case "carrier_spawn" -> "carrierSpawns"; case "interceptor_spawn" -> "interceptorSpawns"; case "broker_spawn" -> "brokerSpawns";
            default -> throw new IllegalArgumentException("未知点位kind");
        };
    }

    /** 该 kind 是否带人数档（minPlayers）：信标 / 晶簇 / 物资箱（含黑市箱）。 */
    private static boolean hasPlayers(String kind) {
        return kind.startsWith("beacon_") || kind.equals("crystal") || kind.equals("supply") || kind.equals("black_market");
    }

    /** 某 kind 的全部点位（模板坐标，文件顺序）；单例类型最多一条。 */
    public List<PointInfo> points(String kind) {
        if(!KINDS.contains(kind)) throw new IllegalArgumentException("未知点位kind: "+kind);
        var out=new ArrayList<PointInfo>();
        var gate=root.getAsJsonObject("coreGate"); var evacuation=root.getAsJsonObject("evacuation");
        switch(kind) {
            case "carrier_spawn" -> spawns(out,root.getAsJsonArray("carrierSpawns"));
            case "interceptor_spawn" -> spawns(out,root.getAsJsonArray("interceptorSpawns"));
            case "broker_spawn" -> spawns(out,root.getAsJsonArray("brokerSpawns"));
            case "spectator" -> { if(root.has("spectatorPos")) single(out,"",root.getAsJsonObject("spectatorPos")); }
            case "free_control" -> {
                if(gate.has("freeExit")) { var e=gate.getAsJsonObject("freeExit"); if(e.has("controlPos")) single(out,e.get("id").getAsString(),e.getAsJsonObject("controlPos")); }
            }
            case "free_safe" -> {
                if(gate.has("freeExit") && gate.getAsJsonObject("freeExit").has("outsideSafe")) single(out,"",gate.getAsJsonObject("freeExit").getAsJsonObject("outsideSafe"));
            }
            case "evacuation_control" -> { if(evacuation.has("controlPos")) single(out,"",evacuation.getAsJsonObject("controlPos")); }
            case "evacuation_safe" -> { if(evacuation.has("outsideSafe")) single(out,"",evacuation.getAsJsonObject("outsideSafe")); }
            case "drop_anchor" -> { if(evacuation.has("exitDropAnchor")) single(out,"",evacuation.getAsJsonObject("exitDropAnchor")); }
            case "broker_exit" -> { if(root.has("brokerExit")) { var e=root.getAsJsonObject("brokerExit"); single(out,e.get("id").getAsString(),e.getAsJsonObject("pos")); } }
            case "core_control" -> entries(out,gate.getAsJsonArray("doors"),true);
            default -> entries(out,root.getAsJsonArray(fieldOf(kind)),false);
        }
        return List.copyOf(out);
    }

    private static void single(List<PointInfo> out,String id,JsonObject pos) {
        out.add(new PointInfo(id,0,pos.get("x").getAsDouble(),pos.get("y").getAsDouble(),pos.get("z").getAsDouble()));
    }
    private static void spawns(List<PointInfo> out,JsonArray array) {
        for(int i=0;i<array.size();i++) single(out,Integer.toString(i),array.get(i).getAsJsonObject());
    }
    private static void entries(List<PointInfo> out,JsonArray array,boolean controlPos) {
        for(var element:array) {
            var e=element.getAsJsonObject();
            var pos=controlPos?e.getAsJsonObject("controlPos"):e.getAsJsonObject("pos");
            out.add(new PointInfo(e.get("id").getAsString(),e.has("minPlayers")?e.get("minPlayers").getAsInt():0,
                    pos.get("x").getAsDouble(),pos.get("y").getAsDouble(),pos.get("z").getAsDouble()));
        }
    }

    /**
     * 就地修改点位：id / 人数档 / 坐标都可以改（{@code move} 为模板坐标，null 表示不改）。
     * 出生点不能改名（id 是下标）；人数档只对信标 / 晶簇 / 物资箱有效。
     */
    public void meta(String kind,String oldId,String newId,Integer minPlayers,EchoMapConfig.Vec move) throws IOException {
        if(!KINDS.contains(kind)) throw new IllegalArgumentException("未知点位kind: "+kind);
        id(oldId);
        String target=newId==null||newId.isBlank()?oldId:newId.trim();
        if(!target.equals(oldId)) id(target);
        boolean spawn=kind.endsWith("_spawn");
        if(spawn) {
            if(!target.equals(oldId)) throw new IllegalArgumentException("出生点id是下标，不能改名；删除后重新标记即可");
            if(minPlayers!=null) throw new IllegalArgumentException("出生点没有人数档");
        }
        update(tree -> {
            JsonObject gate=tree.getAsJsonObject("coreGate"),evacuation=tree.getAsJsonObject("evacuation");
            switch(kind) {
                case "free_control" -> {
                    var free=gate.getAsJsonObject("freeExit");
                    free.addProperty("id",target);
                    if(move!=null) setPos(free.getAsJsonObject("controlPos"),move);
                }
                case "broker_exit" -> {
                    var e=tree.getAsJsonObject("brokerExit");
                    e.addProperty("id",target);
                    if(move!=null) setPos(e.getAsJsonObject("pos"),move);
                }
                case "spectator" -> { if(move!=null) setPos(tree.getAsJsonObject("spectatorPos"),move); }
                case "free_safe" -> { if(move!=null) setPos(gate.getAsJsonObject("freeExit").getAsJsonObject("outsideSafe"),move); }
                case "evacuation_control" -> { if(move!=null) setPos(evacuation.getAsJsonObject("controlPos"),move); }
                case "evacuation_safe" -> { if(move!=null) setPos(evacuation.getAsJsonObject("outsideSafe"),move); }
                case "drop_anchor" -> { if(move!=null) setPos(evacuation.getAsJsonObject("exitDropAnchor"),move); }
                case "carrier_spawn","interceptor_spawn","broker_spawn" -> {
                    if(move==null) throw new IllegalArgumentException("出生点只能改坐标：请填写X/Y/Z");
                    var array=tree.getAsJsonArray(fieldOf(kind));
                    int index=Integer.parseInt(oldId);
                    if(index<0 || index>=array.size()) throw new IllegalArgumentException("不存在该出生点");
                    var element=array.get(index).getAsJsonObject();
                    array.set(index,spawn(move,element.get("yaw").getAsFloat(),element.get("pitch").getAsFloat()));
                }
                default -> {
                    String field=fieldOf(kind);
                    var array=field.equals("doors")?gate.getAsJsonArray(field):tree.getAsJsonArray(field);
                    JsonObject found=null;
                    for(var element:array) { var e=element.getAsJsonObject(); if(e.get("id").getAsString().equals(oldId)) found=e; }
                    if(found==null) throw new IllegalArgumentException("不存在该点位");
                    if(!target.equals(oldId)) {
                        for(var element:array) if(element.getAsJsonObject().get("id").getAsString().equals(target))
                            throw new IllegalArgumentException("目标id已存在");
                        found.addProperty("id",target);
                    }
                    if(minPlayers!=null) {
                        if(!hasPlayers(kind)) throw new IllegalArgumentException("该类型没有人数档");
                        if(minPlayers<EchoRosterPolicy.MIN_PLAYERS || minPlayers>EchoRosterPolicy.MAX_PLAYERS) throw new IllegalArgumentException("minPlayers必须为6–32");
                        found.addProperty("minPlayers",minPlayers);
                    }
                    if(move!=null) setPos(found.has("controlPos")?found.getAsJsonObject("controlPos"):found.getAsJsonObject("pos"),move);
                }
            }
        });
    }

    /** 只改坐标三兄弟，保留同一对象里的 yaw/pitch 等字段。 */
    private static void setPos(JsonObject target,EchoMapConfig.Vec p) {
        target.addProperty("x",p.x()); target.addProperty("y",p.y()); target.addProperty("z",p.z());
    }
}
