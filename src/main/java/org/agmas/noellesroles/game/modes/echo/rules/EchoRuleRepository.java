package org.agmas.noellesroles.game.modes.echo.rules;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** 有界、拒绝重复/未知字段的规则覆盖；无文件才使用默认，坏文件绝不静默回退。 */
public final class EchoRuleRepository {
    public record Snapshot(EchoRuleConfig values,String hash,String sourceDigest) { }
    private static final int MAX_BYTES=65536;
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private EchoRuleRepository() { }
    private static byte[] bytes(Path worldRoot) throws IOException {
        Path root=worldRoot.toRealPath(),dir=root.resolve("echo_freight"),path=dir.resolve("rules.json");
        if(Files.isSymbolicLink(dir) || Files.isSymbolicLink(path)) throw new IOException("规则路径不得包含符号链接");
        if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS)) return null;
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)) throw new IOException("规则目标不是普通文件");
        try(var input=Files.newInputStream(path)) {
            byte[] bytes=input.readNBytes(MAX_BYTES+1);
            if(bytes.length>MAX_BYTES) throw new IOException("规则文件超过64KiB");
            return bytes;
        }
    }
    public static String digest(Path worldRoot) throws IOException { var bytes=bytes(worldRoot); return bytes==null?"absent":hash(bytes); }
    public static Snapshot load(Path worldRoot) throws IOException {
        byte[] bytes=bytes(worldRoot);
        if(bytes==null) return snapshot(EchoRuleConfig.DEFAULT,"absent");
        String json=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        return snapshot(parse(json),hash(bytes));
    }
    private static Snapshot snapshot(EchoRuleConfig config,String source) { return new Snapshot(config,hash(GSON.toJson(effective(config)).getBytes(StandardCharsets.UTF_8)),source); }
    public static String hash(EchoRuleConfig config) { return snapshot(config,"").hash(); }
    private static JsonObject effective(EchoRuleConfig config) {
        var root=external(config); var cards=new JsonObject();
        org.agmas.noellesroles.game.modes.echo.cards.EchoCardRegistry.all().stream().sorted(Comparator.comparing(org.agmas.noellesroles.game.modes.echo.cards.EchoCardDefinition::id)).forEach(base -> {
            var card=config.card(base.id()); var fields=new JsonObject(); fields.addProperty("weight",card.weight());
            new TreeMap<>(card.params()).forEach((name,value) -> fields.addProperty(name,BigDecimal.valueOf(value).stripTrailingZeros())); cards.add(card.shortId(),fields);
        }); root.add("cards",cards); return root;
    }
    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public static EchoRuleConfig parse(String json) throws IOException {
        if(json==null || json.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES || json.startsWith("\uFEFF")) throw new IOException("规则JSON为空、含BOM或超过64KiB");
        try(var reader=new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            JsonObject object=read(reader,0);
            if(reader.peek()!=JsonToken.END_DOCUMENT) throw new IOException("规则JSON含尾随内容");
            if(object.has("schemaVersion")) {
                var schema=object.remove("schemaVersion");
                if(!schema.isJsonPrimitive() || !schema.getAsJsonPrimitive().isNumber() || schema.getAsBigDecimal().compareTo(BigDecimal.ONE)!=0) throw new IOException("规则schemaVersion必须为1");
            }
            return (EchoRuleConfig)merge(EchoRuleConfig.DEFAULT,object,"rules");
        } catch(ReflectiveOperationException | RuntimeException invalid) { throw new IOException("规则JSON无效: "+invalid.getMessage(),invalid); }
    }
    private static JsonObject read(JsonReader reader,int depth) throws IOException {
        if(depth>6 || reader.peek()!=JsonToken.BEGIN_OBJECT) throw new IOException("规则仅接受有界对象和数值");
        reader.beginObject(); var object=new JsonObject();
        while(reader.hasNext()) {
            String key=reader.nextName();
            if(key.length()>64 || object.has(key)) throw new IOException("重复/过长规则字段: "+key);
            if(reader.peek()==JsonToken.BEGIN_OBJECT) object.add(key,read(reader,depth+1));
            else if(reader.peek()==JsonToken.NUMBER) {
                String number=reader.nextString(); if(number.length()>64) throw new IOException("规则数字过长");
                object.add(key,new JsonPrimitive(new BigDecimal(number)));
            } else throw new IOException("规则字段只能是数值或对象: "+key);
        }
        reader.endObject(); return object;
    }
    private static String key(String internal) { return internal.endsWith("Ticks")?internal.substring(0,internal.length()-5)+"Seconds":internal; }
    private static Object merge(Record defaults,JsonObject overrides,String path) throws ReflectiveOperationException,IOException {
        var components=defaults.getClass().getRecordComponents(); var names=new HashSet<String>();
        for(var c:components) names.add(key(c.getName()));
        for(String supplied:overrides.keySet()) if(!names.contains(supplied)) throw new IOException("未知规则字段: "+path+"."+supplied);
        Object[] values=new Object[components.length]; Class<?>[] types=new Class<?>[components.length];
        for(int n=0;n<components.length;n++) {
            var c=components[n]; types[n]=c.getType(); Object old=c.getAccessor().invoke(defaults); var supplied=overrides.get(key(c.getName()));
            if(supplied==null) { values[n]=old; continue; }
            String field=path+"."+key(c.getName());
            if(old instanceof EchoCardTuning) { values[n]=cardTuning(supplied); continue; }
            if(old instanceof Record record) {
                if(!supplied.isJsonObject()) throw new IOException(field+"必须为对象");
                values[n]=merge(record,supplied.getAsJsonObject(),field); continue;
            }
            if(!supplied.isJsonPrimitive() || !supplied.getAsJsonPrimitive().isNumber()) throw new IOException(field+"必须为数值");
            BigDecimal number=supplied.getAsBigDecimal();
            if(c.getName().endsWith("Ticks")) number=number.multiply(BigDecimal.valueOf(20));
            try {
                if(c.getType()==int.class) values[n]=number.intValueExact();
                else { double value=number.doubleValue(); if(!Double.isFinite(value)) throw new ArithmeticException(); values[n]=value; }
            } catch(ArithmeticException invalid) { throw new IOException(field+"越界或不能转换为整数tick",invalid); }
        }
        try { return defaults.getClass().getDeclaredConstructor(types).newInstance(values); }
        catch(java.lang.reflect.InvocationTargetException invalid) { throw new IOException(path+": "+invalid.getCause().getMessage(),invalid.getCause()); }
    }
    private static EchoCardTuning cardTuning(JsonElement supplied) throws IOException {
        if(!supplied.isJsonObject()) throw new IOException("cards必须为对象");
        var result=new TreeMap<String,Map<String,Double>>();
        for(var entry:supplied.getAsJsonObject().entrySet()) {
            var card=org.agmas.noellesroles.game.modes.echo.cards.EchoCardRegistry.require("noellesroles:echo/"+entry.getKey());
            if(!entry.getValue().isJsonObject()) throw new IOException("卡牌覆盖必须为对象");
            var values=new TreeMap<String,Double>();
            for(var field:entry.getValue().getAsJsonObject().entrySet()) {
                String internal=field.getKey().endsWith("Seconds")?field.getKey().substring(0,field.getKey().length()-7)+"Ticks":field.getKey();
                if(field.getKey().endsWith("Ticks") || !EchoCardTuning.allowed(card,internal)) throw new IOException("不支持的卡牌字段: "+entry.getKey()+"."+field.getKey());
                if(!field.getValue().isJsonPrimitive() || !field.getValue().getAsJsonPrimitive().isNumber()) throw new IOException("卡牌参数必须是数值");
                BigDecimal number=field.getValue().getAsBigDecimal();
                if(internal.endsWith("Ticks")) { number=number.multiply(BigDecimal.valueOf(20)); number.intValueExact(); }
                values.put(internal,number.doubleValue());
            }
            result.put(entry.getKey(),values);
        }
        return new EchoCardTuning(result);
    }
    public static String json(EchoRuleConfig config) { return GSON.toJson(external(config)); }
    /** 生成受支持字段的完整示例（外部时间全部是秒）；不写世界文件。 */
    public static String example() { var root=external(EchoRuleConfig.DEFAULT); root.addProperty("schemaVersion",1); return GSON.toJson(root)+"\n"; }
    private static JsonObject external(Record record) {
        var result=new JsonObject();
        try {
            for(var c:record.getClass().getRecordComponents()) {
                Object value=c.getAccessor().invoke(record);
                if(value instanceof EchoCardTuning tuning) {
                    var cards=new JsonObject();
                    tuning.overrides().forEach((id,fields) -> {
                        var card=new JsonObject(); fields.forEach((name,amount) -> {
                            BigDecimal number=BigDecimal.valueOf(amount);
                            if(name.endsWith("Ticks")) number=number.divide(BigDecimal.valueOf(20));
                            card.addProperty(key(name),number.stripTrailingZeros());
                        }); cards.add(id,card);
                    }); result.add(c.getName(),cards);
                }
                else if(value instanceof Record nested) result.add(key(c.getName()),external(nested));
                else {
                    BigDecimal number=new BigDecimal(value.toString());
                    if(c.getName().endsWith("Ticks")) number=number.divide(BigDecimal.valueOf(20));
                    result.addProperty(key(c.getName()),number.stripTrailingZeros());
                }
            }
        } catch(ReflectiveOperationException impossible) { throw new IllegalStateException(impossible); }
        return result;
    }
}
