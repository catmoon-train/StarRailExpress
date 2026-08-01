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

package net.exmo.sre.mod_whitelist.server.network;

import net.exmo.sre.mod_whitelist.ModWhitelist;
import net.exmo.sre.mod_whitelist.common.auth.JarAuthCore;
import net.exmo.sre.mod_whitelist.common.network.JarAuthChallengePayload;
import net.exmo.sre.mod_whitelist.common.network.JarAuthResponsePayload;
import net.exmo.sre.mod_whitelist.common.utils.MWLogger;
import net.exmo.sre.mod_whitelist.server.config.MWServerConfig;
import net.exmo.sre.mod_whitelist.server.config.MismatchType;
import net.exmo.sre.mod_whitelist.server.storage.ViolationRecordStorage;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.apache.commons.lang3.tuple.Pair;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JAR 密钥认证（服务端）。默认关闭，由 {@code ENABLE_JAR_KEY_AUTH} 打开。
 *
 * 玩家加入时下发一次性 nonce 挑战；客户端须在 {@value #RESPONSE_TIMEOUT_MS} 毫秒内
 * 用「嵌入密钥 + 自身 jar 摘要 + 模组版本」计算出与服务端一致的 HMAC 应答，
 * 否则视为运行了被修改 / 未签名 / 版本不符的客户端 jar，记录违规并断开连接。
 * 服务端自身 jar 未签名（缺少密钥文件）时打印告警并放行 —— 无法用不存在的密钥验证他人。
 */
public final class JarAuthServerHandler {

    private static final long RESPONSE_TIMEOUT_MS = 10_000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private record Pending(String nonce, long deadlineMillis) {
    }

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static boolean warnedUnsignedServer;

    private JarAuthServerHandler() {
    }

    public static void initialize() {
        ServerPlayNetworking.registerGlobalReceiver(JarAuthResponsePayload.ID,
                JarAuthServerHandler::handleResponse);
        if (MWServerConfig.ENABLE_JAR_KEY_AUTH.value()) {
            // 提前在后台预热服务端自身摘要，避免首位玩家进服时卡主线程
            new Thread(JarAuthCore::warmUp, "SRE-JarAuth-WarmUp").start();
        }
    }

    /** 玩家加入：如启用则下发挑战。由 {@link ModWhitelistServerNetworkHandler} 的 JOIN 事件调用。 */
    public static void onPlayerJoin(ServerPlayer player) {
        if (!MWServerConfig.ENABLE_JAR_KEY_AUTH.value()) {
            return;
        }
        if (MWServerConfig.SKIP_VERIFICATION_FOR_OPS.value() && player.hasPermissions(2)) {
            return;
        }
        if (!JarAuthCore.hasKey()) {
            if (!warnedUnsignedServer) {
                warnedUnsignedServer = true;
                MWLogger.LOGGER.warn("[JarAuth] ENABLE_JAR_KEY_AUTH is on but the SERVER jar is unsigned "
                        + "(missing {}). Sign the jar with tools/sign_sre_jar.py — skipping enforcement.",
                        JarAuthCore.KEY_FILE_NAME);
            }
            return;
        }
        byte[] nonceBytes = new byte[16];
        RANDOM.nextBytes(nonceBytes);
        StringBuilder nonce = new StringBuilder(32);
        for (byte b : nonceBytes) {
            nonce.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        PENDING.put(player.getUUID(), new Pending(nonce.toString(),
                System.currentTimeMillis() + RESPONSE_TIMEOUT_MS));
        ServerPlayNetworking.send(player, new JarAuthChallengePayload(nonce.toString()));
        MWLogger.LOGGER.debug("[JarAuth] Sent challenge to {}", player.getName().getString());
    }

    public static void onPlayerDisconnect(UUID uuid) {
        PENDING.remove(uuid);
    }

    /** 每 tick 检查应答超时。由 {@link ModWhitelistServerNetworkHandler} 的 tick 事件调用。 */
    public static void checkTimeouts(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Pending> entry : PENDING.entrySet()) {
            if (now < entry.getValue().deadlineMillis()) {
                continue;
            }
            PENDING.remove(entry.getKey());
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                MWLogger.LOGGER.warn("[JarAuth] Player {} timed out without a jar auth response",
                        player.getName().getString());
                disconnectWithViolation(player, "JarAuth: response timeout");
            }
        }
    }

    private static void handleResponse(JarAuthResponsePayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        Pending pending = PENDING.remove(player.getUUID());
        if (!MWServerConfig.ENABLE_JAR_KEY_AUTH.value() || pending == null) {
            return;
        }

        if (!payload.hasKey()) {
            MWLogger.LOGGER.warn("[JarAuth] Player {} runs an UNSIGNED jar", player.getName().getString());
            disconnectWithViolation(player, "JarAuth: unsigned client jar");
            return;
        }
        if (!ModWhitelist.MOD_VERSION.equals(payload.version())) {
            MWLogger.LOGGER.warn("[JarAuth] Player {} version mismatch: client={}, server={}",
                    player.getName().getString(), payload.version(), ModWhitelist.MOD_VERSION);
            disconnectWithViolation(player, "JarAuth: version mismatch " + payload.version());
            return;
        }
        String expected = JarAuthCore.computeResponse(pending.nonce(), ModWhitelist.MOD_VERSION);
        if (expected == null || !JarAuthCore.responsesEqual(expected, payload.response())) {
            MWLogger.LOGGER.warn("[JarAuth] Player {} failed jar auth (modified client jar?)",
                    player.getName().getString());
            disconnectWithViolation(player, "JarAuth: HMAC mismatch");
            return;
        }
        MWLogger.LOGGER.info("[JarAuth] Player {} passed jar key authentication", player.getName().getString());
    }

    private static void disconnectWithViolation(ServerPlayer player, String detail) {
        List<Pair<String, MismatchType>> mismatches = List.of(
                Pair.of(detail, MismatchType.INSTALLED_BUT_SHOULD_NOT_INSTALL));
        ViolationRecordStorage.recordViolation(
                player.getName().getString(),
                player.getUUID(),
                PlayerNetworkInfoUtil.getPlayerIP(player),
                PlayerNetworkInfoUtil.getPlayerMACAddress(player),
                mismatches);
        player.connection.disconnect(Component.literal(
                "客户端认证失败：请使用本服务器发布的官方签名客户端（版本需一致）\n"
                        + "Client authentication failed: please use the official signed client jar for this server."));
    }
}
