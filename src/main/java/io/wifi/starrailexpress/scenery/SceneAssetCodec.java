package io.wifi.starrailexpress.scenery;

import net.minecraft.world.phys.AABB;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public final class SceneAssetCodec {
    private static final int MAGIC = 0x53524553;
    private static final int LEGACY_SCHEMA = 1;
    private static final int WHOLE_ASSET_SCHEMA = 2;
    private static final int COMPRESSION_DEFLATE = 1;
    private static final int MAX_SECTIONS = 1_000_000;
    private static final int MAX_SECTION_PAYLOAD = 8 * 1024 * 1024;
    private static final int MAX_ASSET_PAYLOAD = 1024 * 1024 * 1024;

    private SceneAssetCodec() {
    }

    public static byte[] encode(SceneAsset asset) throws IOException {
        if (asset.schema() == LEGACY_SCHEMA) {
            return encodeLegacy(asset);
        }
        if (asset.schema() != WHOLE_ASSET_SCHEMA) {
            throw new IOException("Unsupported scene asset schema: " + asset.schema());
        }

        byte[] body = encodeBody(asset);
        if (body.length > MAX_ASSET_PAYLOAD) {
            throw new IOException("Scene asset payload is too large: " + body.length);
        }
        byte[] compressed = compress(body);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(output)) {
            data.writeInt(MAGIC);
            data.writeInt(asset.schema());
            data.writeByte(COMPRESSION_DEFLATE);
            data.writeInt(body.length);
            writeBytes(data, compressed);
        }
        return output.toByteArray();
    }

    private static byte[] encodeLegacy(SceneAsset asset) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(output)) {
            data.writeInt(MAGIC);
            data.writeInt(LEGACY_SCHEMA);
            data.writeUTF(asset.minecraftVersion());
            data.writeUTF(asset.registryFingerprint());
            writeBox(data, asset.sourceArea());

            List<SceneAsset.SectionData> sections = new ArrayList<>(asset.sections());
            sections.sort(Comparator.comparingInt(SceneAsset.SectionData::sectionX)
                    .thenComparingInt(SceneAsset.SectionData::sectionZ)
                    .thenComparingInt(SceneAsset.SectionData::sectionY));
            data.writeInt(sections.size());
            for (SceneAsset.SectionData section : sections) {
                data.writeInt(section.sectionX());
                data.writeInt(section.sectionY());
                data.writeInt(section.sectionZ());
                byte[] compressed = compress(section.sectionPayload());
                data.writeInt(section.sectionPayload().length);
                writeBytes(data, compressed);
                writeBytes(data, section.skyLight());
                writeBytes(data, section.blockLight());
            }
        }
        return output.toByteArray();
    }

    public static SceneAsset decode(byte[] encoded) throws IOException {
        try (DataInputStream data = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (data.readInt() != MAGIC) {
                throw new IOException("Not an SRE scene asset");
            }
            int schema = data.readInt();
            if (schema == LEGACY_SCHEMA) {
                return decodeLegacy(data, schema);
            }
            if (schema != WHOLE_ASSET_SCHEMA) {
                throw new IOException("Unsupported scene asset schema: " + schema);
            }
            int compression = data.readUnsignedByte();
            if (compression != COMPRESSION_DEFLATE) {
                throw new IOException("Unsupported scene asset compression: " + compression);
            }
            int rawLength = data.readInt();
            if (rawLength < 0 || rawLength > MAX_ASSET_PAYLOAD) {
                throw new IOException("Invalid scene asset payload size: " + rawLength);
            }
            byte[] compressed = readBytes(data, MAX_ASSET_PAYLOAD);
            if (data.available() != 0) {
                throw new IOException("Trailing data in scene asset");
            }
            return decodeBody(decompress(compressed, rawLength, "Scene asset payload"), schema);
        }
    }

    private static byte[] encodeBody(SceneAsset asset) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(output)) {
            data.writeUTF(asset.minecraftVersion());
            data.writeUTF(asset.registryFingerprint());
            writeBox(data, asset.sourceArea());

            List<SceneAsset.SectionData> sections = sortedSections(asset);
            data.writeInt(sections.size());
            for (SceneAsset.SectionData section : sections) {
                data.writeInt(section.sectionX());
                data.writeInt(section.sectionY());
                data.writeInt(section.sectionZ());
                writeBytes(data, section.sectionPayload());
                writeBytes(data, section.skyLight());
                writeBytes(data, section.blockLight());
            }
        }
        return output.toByteArray();
    }

    private static SceneAsset decodeBody(byte[] body, int schema) throws IOException {
        try (DataInputStream data = new DataInputStream(new ByteArrayInputStream(body))) {
            String minecraftVersion = data.readUTF();
            String registryFingerprint = data.readUTF();
            AABB sourceArea = readBox(data);
            int sectionCount = data.readInt();
            if (sectionCount < 0 || sectionCount > MAX_SECTIONS) {
                throw new IOException("Invalid scene section count: " + sectionCount);
            }

            List<SceneAsset.SectionData> sections = new ArrayList<>(sectionCount);
            for (int i = 0; i < sectionCount; i++) {
                int x = data.readInt();
                int y = data.readInt();
                int z = data.readInt();
                byte[] sectionPayload = readBytes(data, MAX_SECTION_PAYLOAD);
                byte[] skyLight = readBytes(data, 4096);
                byte[] blockLight = readBytes(data, 4096);
                sections.add(new SceneAsset.SectionData(x, y, z, sectionPayload, skyLight, blockLight));
            }
            if (data.available() != 0) {
                throw new IOException("Trailing data in scene asset");
            }
            return new SceneAsset(schema, minecraftVersion, registryFingerprint, sourceArea, sections);
        }
    }

    private static SceneAsset decodeLegacy(DataInputStream data, int schema) throws IOException {
        String minecraftVersion = data.readUTF();
        String registryFingerprint = data.readUTF();
        AABB sourceArea = readBox(data);
        int sectionCount = readSectionCount(data);
        List<SceneAsset.SectionData> sections = new ArrayList<>(sectionCount);
        for (int i = 0; i < sectionCount; i++) {
            int x = data.readInt();
            int y = data.readInt();
            int z = data.readInt();
            int rawLength = data.readInt();
            if (rawLength < 0 || rawLength > MAX_SECTION_PAYLOAD) {
                throw new IOException("Invalid scene section payload size: " + rawLength);
            }
            byte[] sectionPayload = decompress(
                    readBytes(data, MAX_SECTION_PAYLOAD), rawLength, "Scene section payload");
            byte[] skyLight = readBytes(data, 4096);
            byte[] blockLight = readBytes(data, 4096);
            sections.add(new SceneAsset.SectionData(x, y, z, sectionPayload, skyLight, blockLight));
        }
        if (data.available() != 0) {
            throw new IOException("Trailing data in scene asset");
        }
        return new SceneAsset(schema, minecraftVersion, registryFingerprint, sourceArea, sections);
    }

    private static int readSectionCount(DataInputStream data) throws IOException {
        int sectionCount = data.readInt();
        if (sectionCount < 0 || sectionCount > MAX_SECTIONS) {
            throw new IOException("Invalid scene section count: " + sectionCount);
        }
        return sectionCount;
    }

    private static List<SceneAsset.SectionData> sortedSections(SceneAsset asset) {
        List<SceneAsset.SectionData> sections = new ArrayList<>(asset.sections());
        sections.sort(Comparator.comparingInt(SceneAsset.SectionData::sectionX)
                .thenComparingInt(SceneAsset.SectionData::sectionZ)
                .thenComparingInt(SceneAsset.SectionData::sectionY));
        return sections;
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public static boolean isValidHash(String hash) {
        return hash != null && hash.matches("[0-9a-f]{64}");
    }

    private static byte[] compress(byte[] input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, false);
        try (DeflaterOutputStream compressed = new DeflaterOutputStream(output, deflater)) {
            compressed.write(input);
        } finally {
            deflater.end();
        }
        return output.toByteArray();
    }

    private static byte[] decompress(byte[] input, int expectedSize, String description) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(expectedSize, 64 * 1024));
        try (InflaterInputStream inflated = new InflaterInputStream(new ByteArrayInputStream(input))) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = inflated.read(buffer)) >= 0) {
                total += read;
                if (total > expectedSize) {
                    throw new IOException(description + " exceeds declared length");
                }
                output.write(buffer, 0, read);
            }
        }
        byte[] result = output.toByteArray();
        if (result.length != expectedSize) {
            throw new IOException(description + " length mismatch");
        }
        return result;
    }

    private static void writeBytes(DataOutputStream data, byte[] bytes) throws IOException {
        data.writeInt(bytes.length);
        data.write(bytes);
    }

    private static byte[] readBytes(DataInputStream data, int maximum) throws IOException {
        int length = data.readInt();
        if (length < 0 || length > maximum) {
            throw new IOException("Invalid byte array length: " + length);
        }
        byte[] bytes = data.readNBytes(length);
        if (bytes.length != length) {
            throw new IOException("Unexpected end of scene asset");
        }
        return bytes;
    }

    private static void writeBox(DataOutputStream data, AABB box) throws IOException {
        data.writeDouble(box.minX);
        data.writeDouble(box.minY);
        data.writeDouble(box.minZ);
        data.writeDouble(box.maxX);
        data.writeDouble(box.maxY);
        data.writeDouble(box.maxZ);
    }

    private static AABB readBox(DataInputStream data) throws IOException {
        return new AABB(
                data.readDouble(), data.readDouble(), data.readDouble(),
                data.readDouble(), data.readDouble(), data.readDouble());
    }
}
