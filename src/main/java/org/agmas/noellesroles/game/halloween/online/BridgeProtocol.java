package org.agmas.noellesroles.game.halloween.online;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 官网桥接协议；签名基于最终发送的原始 UTF-8 字节，而非重序列化 JSON。 */
public final class BridgeProtocol {
    public static final String PATH = "/api/halloween/bridge/earn";
    private BridgeProtocol() {}

    public static byte[] body(UUID serverId, UUID batchId, List<Earning> entries) {
        if (entries.isEmpty() || entries.size() > 128) throw new IllegalArgumentException("Invalid batch size");
        JsonObject root = new JsonObject();
        root.addProperty("serverId", serverId.toString());
        root.addProperty("batchId", batchId.toString());
        JsonArray array = new JsonArray();
        for (Earning entry : entries) {
            JsonObject row = new JsonObject();
            row.addProperty("id", entry.id().toString());
            row.addProperty("minecraftUuid", entry.minecraftUuid().toString());
            row.addProperty("amount", entry.amount());
            row.addProperty("earnedAt", entry.earnedAt());
            array.add(row);
        }
        root.add("entries", array);
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static String signature(String secret, long timestamp, byte[] body) {
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
            String message = timestamp + "\nPOST\n" + PATH + "\n" + hash;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 unavailable", ex);
        }
    }

    public static UUID canonicalUuid(String value) {
        UUID id = UUID.fromString(value);
        if (!id.toString().equals(value)) throw new IllegalArgumentException("Noncanonical UUID");
        return id;
    }

    public static Set<UUID> acknowledgements(int status, String response) throws IOException {
        if (status != 200) throw new IOException("Bridge HTTP " + status);
        try (JsonReader reader = new JsonReader(new StringReader(response))) {
            // JsonParser/Gson.fromJson 会隐式启用宽松 JSON；ACK 必须严格解析，坏响应不能删队列。
            reader.setLenient(false);
            reader.beginObject();
            boolean ok = false;
            boolean gotIds = false;
            Set<UUID> result = new LinkedHashSet<>();
            while (reader.hasNext()) {
                switch (reader.nextName()) {
                    case "ok" -> {
                        if (ok || reader.peek() != JsonToken.BOOLEAN || !reader.nextBoolean()) throw new IllegalArgumentException();
                        ok = true;
                    }
                    case "acknowledgedIds" -> {
                        if (gotIds) throw new IllegalArgumentException();
                        gotIds = true;
                        reader.beginArray();
                        while (reader.hasNext()) {
                            if (reader.peek() != JsonToken.STRING) throw new IllegalArgumentException();
                            result.add(canonicalUuid(reader.nextString()));
                        }
                        reader.endArray();
                    }
                    default -> reader.skipValue();
                }
            }
            reader.endObject();
            if (!ok || !gotIds || reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException();
            return result;
        } catch (IOException | RuntimeException ex) {
            throw new IOException("Malformed bridge acknowledgement");
        }
    }
}
