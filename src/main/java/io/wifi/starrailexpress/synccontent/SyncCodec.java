/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.synccontent;

import io.wifi.starrailexpress.scenery.SceneAssetCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * 同步内容的编解码工具：稳定哈希 + deflate 压缩 / 解压。
 *
 * <p>
 * JSON 配置文本压缩率通常有 5~10 倍，先压缩再发能显著减少发包数与带宽；哈希是
 * 「通道 + 协议版本 + 内容」的 SHA-256，客户端据此判断本地缓存是否命中。
 */
public final class SyncCodec {

    /** 单次解压允许的最大字节数（防解压炸弹）。 */
    public static final int MAX_INFLATED_BYTES = 16 * 1024 * 1024;

    private SyncCodec() {
    }

    /**
     * 同步内容哈希：{@code sha256("<通道>/<协议版本>\n<内容>")}。
     *
     * <p>
     * 协议版本参与哈希，因此升级协议后客户端旧缓存必然不命中。
     */
    public static String hash(ContentChannel channel, String json) {
        String content = json == null ? "" : json;
        byte[] raw = (channel.id() + "/" + channel.protocolVersion() + "\n" + content)
                .getBytes(StandardCharsets.UTF_8);
        return SceneAssetCodec.sha256(raw);
    }

    /** 是否为合法的哈希串（十六进制 SHA-256）。 */
    public static boolean isValidHash(String hash) {
        return SceneAssetCodec.isValidHash(hash);
    }

    /** UTF-8 文本 → deflate 压缩字节。 */
    public static byte[] deflate(String text) throws IOException {
        return deflate((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
    }

    /** deflate 压缩字节 → UTF-8 文本。 */
    public static String inflateToString(byte[] data) throws IOException {
        return new String(inflate(data), StandardCharsets.UTF_8);
    }

    /** deflate 压缩。 */
    public static byte[] deflate(byte[] input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.max(64, input.length / 4));
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, false);
        try (DeflaterOutputStream compressed = new DeflaterOutputStream(output, deflater)) {
            compressed.write(input);
        } finally {
            deflater.end();
        }
        return output.toByteArray();
    }

    /**
     * deflate 解压，总长度超过 {@link #MAX_INFLATED_BYTES} 直接抛错
     * （避免被恶意构造的压缩包撑爆内存）。
     */
    public static byte[] inflate(byte[] input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InflaterInputStream inflated = new InflaterInputStream(new ByteArrayInputStream(input))) {
            byte[] buffer = new byte[8192];
            int read;
            long total = 0L;
            while ((read = inflated.read(buffer)) > 0) {
                total += read;
                if (total > MAX_INFLATED_BYTES) {
                    throw new IOException("Sync payload too large after decompression: " + total);
                }
                output.write(buffer, 0, read);
            }
        }
        return output.toByteArray();
    }
}
