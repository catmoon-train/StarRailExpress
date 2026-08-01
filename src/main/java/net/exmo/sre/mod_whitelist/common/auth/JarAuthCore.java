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

package net.exmo.sre.mod_whitelist.common.auth;

import net.exmo.sre.mod_whitelist.common.utils.MWLogger;
import net.exmo.sre.mod_whitelist.common.utils.SHA256Utils;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.jetbrains.annotations.Nullable;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Optional;

import static io.wifi.starrailexpress.SRE.MOD_ID;

/**
 * 客户端 JAR 密钥认证的共享核心（服务端与客户端使用完全相同的逻辑）。
 *
 * 工作方式：发布者用外部工具 {@code tools/sign_sre_jar.py} 把一份随机密钥
 * （{@value #KEY_FILE_NAME}，不进版本库）写入最终发布的 jar；服务端与所有
 * 客户端运行<b>同一份签名后的 jar</b>。认证时服务端下发随机 nonce，双方各自以
 * 「嵌入密钥」为 HMAC 密钥，对「自身 jar 的确定性摘要 | nonce | 模组版本」做
 * HMAC-SHA256 —— 只要客户端 jar 的任何字节被改动（包括被替换的 class、资源或
 * 缺失的密钥文件），摘要就会不同，应答即无法匹配。
 *
 * 摘要复用 {@link SHA256Utils#hashPaths}，与既有 mod_whitelist 哈希工具链保持一致。
 * 注意：这是针对"改客户端模组"的准入门槛，不是完备的反作弊 —— 可与
 * {@code VERIFY_STARRAILEXPRESS_HASHES} 白名单叠加使用。
 */
public final class JarAuthCore {

    /** 外部签名工具写入 jar 根部的密钥文件名。 */
    public static final String KEY_FILE_NAME = "sre_auth_key.txt";

    private static volatile String cachedDigest;
    private static volatile String cachedKey;
    private static volatile boolean resolved;

    private JarAuthCore() {
    }

    /** 本端 jar 是否带有嵌入密钥（未签名的开发环境返回 false）。 */
    public static boolean hasKey() {
        resolve();
        return cachedKey != null;
    }

    /**
     * 计算对给定 nonce 的认证应答；密钥或摘要不可用时返回 null。
     * 计算结果为 hex 编码的 HMAC-SHA256。
     */
    @Nullable
    public static String computeResponse(String nonceHex, String modVersion) {
        resolve();
        if (cachedKey == null || cachedDigest == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(cachedKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(
                    (cachedDigest + "|" + nonceHex + "|" + modVersion).getBytes(StandardCharsets.UTF_8));
            return bytesToHex(out);
        } catch (Exception e) {
            MWLogger.LOGGER.error("[JarAuth] Failed to compute HMAC response", e);
            return null;
        }
    }

    /** 常量时间比较两个应答，避免时序侧信道。 */
    public static boolean responsesEqual(@Nullable String a, @Nullable String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /** 预热摘要缓存（jar 较大时首次计算有 IO 开销，建议在后台线程调用）。 */
    public static void warmUp() {
        resolve();
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(MOD_ID);
        if (container.isEmpty() || container.get().getRootPaths().isEmpty()) {
            MWLogger.LOGGER.warn("[JarAuth] Cannot locate own mod container; jar auth unavailable");
            return;
        }
        try {
            for (Path root : container.get().getRootPaths()) {
                Path keyPath = root.resolve(KEY_FILE_NAME);
                if (Files.isRegularFile(keyPath)) {
                    cachedKey = Files.readString(keyPath, StandardCharsets.UTF_8).trim();
                    break;
                }
            }
            if (cachedKey == null || cachedKey.isEmpty()) {
                cachedKey = null;
                MWLogger.LOGGER.info("[JarAuth] No embedded auth key ({}) — jar is unsigned", KEY_FILE_NAME);
            }
            cachedDigest = SHA256Utils.hashPaths(container.get().getRootPaths());
            MWLogger.LOGGER.info("[JarAuth] Self digest computed: {}", cachedDigest);
        } catch (Exception e) {
            cachedKey = null;
            cachedDigest = null;
            MWLogger.LOGGER.error("[JarAuth] Failed to resolve jar auth material", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
