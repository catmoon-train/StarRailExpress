package org.agmas.noellesroles.game.modes.echo.map;

import org.agmas.noellesroles.game.modes.echo.rules.EchoRosterPolicy;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.*;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** 有界、严格的地图侧文件读取；不接受绝对路径、缺失字段或符号链接越界。 */
public final class EchoMapRepository {
    private static final int MAX_BYTES = 1024 * 1024;
    private static final Gson GSON = new Gson();
    private EchoMapRepository() { }

    public static EchoMapConfig load(Path worldRoot, String mapId) throws IOException {
        if (mapId == null || mapId.length() > 128 || !mapId.matches("[a-z0-9_-]+(?:/[a-z0-9_-]+)*")) {
            throw new IOException("非法mapId");
        }
        Path world = worldRoot.toRealPath();
        Path root = world.resolve("echo_freight/maps").normalize();
        Path file = root.resolve(mapId + ".json").normalize();
        if (!file.startsWith(root) || !file.toRealPath().startsWith(root.toRealPath()) || !root.toRealPath().startsWith(world)) {
            throw new IOException("地图路径越界");
        }
        byte[] bytes;
        try (InputStream input = Files.newInputStream(file)) { bytes = input.readNBytes(MAX_BYTES + 1); }
        if (bytes.length > MAX_BYTES) throw new IOException("地图文件超过1MiB");
        EchoMapConfig config = parse(new String(bytes, StandardCharsets.UTF_8));
        if (!config.mapId().equals(mapId)) throw new IOException("侧文件mapId与路径不匹配");
        return config;
    }

    public static void requireMapId(String mapId) throws IOException {
        if(mapId==null || mapId.length()>128 || !mapId.matches("[a-z0-9_-]+(?:/[a-z0-9_-]+)*")) throw new IOException("非法mapId");
    }
    private static Path destination(Path worldRoot,String mapId,boolean create) throws IOException {
        requireMapId(mapId); Path world=worldRoot.toRealPath(); Path file=world.resolve("echo_freight/maps").resolve(mapId+".json").normalize();
        if(!file.startsWith(world.resolve("echo_freight/maps"))) throw new IOException("地图路径越界");
        Path part=world;
        for(Path segment:world.relativize(file)) {
            part=part.resolve(segment);
            if(Files.isSymbolicLink(part) || Files.exists(part) && !part.toRealPath().startsWith(world)) throw new IOException("地图路径包含链接或越界");
        }
        if(create) Files.createDirectories(file.getParent());
        return file;
    }
    public static String digest(Path worldRoot,String mapId) throws IOException {
        Path file=destination(worldRoot,mapId,false);
        if(!Files.exists(file)) return "";
        if(!Files.isRegularFile(file)) throw new IOException("地图目标不是文件");
        byte[] bytes;
        try(InputStream input=Files.newInputStream(file)) { bytes=input.readNBytes(MAX_BYTES+1); }
        if(bytes.length>MAX_BYTES) throw new IOException("地图文件超过1MiB");
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    /** expectedDigest为空只允许新建；覆盖必须持有管理员确认时的原文件摘要。 */
    public static Path save(Path worldRoot,EchoMapConfig map,String expectedDigest) throws IOException {
        requireMapId(map.mapId());
        if(!map.coordinateSpace().equals("template")) throw new IOException("禁止导出实战坐标");
        for(int count=EchoRosterPolicy.MIN_PLAYERS;count<=EchoMapValidator.validationPlayerLimit(map);count++) {
            var errors=EchoMapValidator.validate(map,count);
            if(!errors.isEmpty()) throw new IOException("N="+count+": "+String.join("; ",errors.stream().limit(8).toList()));
        }
        String json=new GsonBuilder().setPrettyPrinting().create().toJson(map)+"\n";
        byte[] bytes=json.getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_BYTES) throw new IOException("地图文件超过1MiB");
        parse(json);
        if(expectedDigest==null || !expectedDigest.equals(digest(worldRoot,map.mapId()))) throw new IOException("目标文件已变化，需要重新确认覆盖");
        Path file=destination(worldRoot,map.mapId(),true),temporary=Files.createTempFile(file.getParent(),".echo-map-",".tmp");
        try {
            Files.write(temporary,bytes);
            if(!expectedDigest.equals(digest(worldRoot,map.mapId()))) throw new IOException("目标文件已变化，需要重新确认覆盖");
            if(expectedDigest.isEmpty()) Files.move(temporary,file,java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            else Files.move(temporary,file,java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
        return file;
    }

    public static EchoMapConfig parse(String json) throws IOException {
        if (json == null || json.length() > MAX_BYTES) throw new IOException("地图JSON为空或超过大小限制");
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            JsonElement tree = read(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("JSON含尾随内容");
            // 旧独立中立出生点可省略；存在时仍严格解析并原样保存，运行时不使用。
            if(tree.isJsonObject() && !tree.getAsJsonObject().has("brokerSpawns"))
                tree.getAsJsonObject().add("brokerSpawns",new JsonArray());
            requireFields(tree, EchoMapConfig.class, "map");
            EchoMapConfig map = GSON.fromJson(tree, EchoMapConfig.class);
            if (!map.coordinateSpace().equals("template")) throw new IOException("侧文件必须使用template坐标");
            return map;
        } catch (RuntimeException exception) {
            throw new IOException("地图JSON无效: " + exception.getMessage(), exception);
        }
    }

    private static JsonElement read(JsonReader reader, int depth) throws IOException {
        if (depth > 16) throw new IOException("JSON嵌套过深");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject object = new JsonObject();
                Set<String> keys = new HashSet<>();
                reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (key.length() > 128 || !keys.add(key)) throw new IOException("重复或超长字段: " + key);
                    object.add(key, read(reader, depth + 1));
                    if (keys.size() > 128) throw new IOException("字段过多");
                }
                reader.endObject();
                yield object;
            }
            case BEGIN_ARRAY -> {
                JsonArray array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) {
                    if (array.size() >= 128) throw new IOException("点位数组超过128项");
                    array.add(read(reader, depth + 1));
                }
                reader.endArray();
                yield array;
            }
            case STRING -> {
                String value = reader.nextString();
                if (value.length() > 256) throw new IOException("字符串过长");
                yield new JsonPrimitive(value);
            }
            case NUMBER -> {
                String value = reader.nextString();
                double number = Double.parseDouble(value);
                if (!Double.isFinite(number)) throw new IOException("非有限数值");
                yield new JsonPrimitive(new java.math.BigDecimal(value));
            }
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            default -> throw new IOException("不允许空值或不完整JSON");
        };
    }

    private static void requireFields(JsonElement element, Type type, String path) throws IOException {
        if (type instanceof ParameterizedType parameterized) {
            if (!element.isJsonArray()) throw new IOException(path + "必须为数组");
            for (JsonElement child : element.getAsJsonArray()) requireFields(child, parameterized.getActualTypeArguments()[0], path + "[]");
            return;
        }
        Class<?> clazz = (Class<?>) type;
        if (clazz.isRecord()) {
            if (!element.isJsonObject()) throw new IOException(path + "必须为对象");
            JsonObject object = element.getAsJsonObject();
            Set<String> expected = new HashSet<>();
            for (var component : clazz.getRecordComponents()) {
                expected.add(component.getName());
                if (!object.has(component.getName()) || object.get(component.getName()).isJsonNull()) {
                    throw new IOException(path + "." + component.getName() + "缺失");
                }
                requireFields(object.get(component.getName()), component.getGenericType(), path + "." + component.getName());
            }
            if (!expected.containsAll(object.keySet())) throw new IOException(path + "包含未知字段");
        } else {
            if (!element.isJsonPrimitive()) throw new IOException(path + "类型无效");
            JsonPrimitive value = element.getAsJsonPrimitive();
            if (clazz == String.class && !value.isString() || clazz == boolean.class && !value.isBoolean()
                    || clazz != String.class && clazz != boolean.class && !value.isNumber()) throw new IOException(path + "类型不匹配");
            if (clazz == int.class) {
                double number = value.getAsDouble();
                if (number != Math.rint(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
                    throw new IOException(path + "必须为32位整数");
                }
            }
        }
    }
}
