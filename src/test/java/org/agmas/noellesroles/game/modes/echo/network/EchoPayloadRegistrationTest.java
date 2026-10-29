package org.agmas.noellesroles.game.modes.echo.network;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回声模式的网络自检：每个 {@code CustomPacketPayload} 都必须在 {@code ModPackets} 里
 * 用 {@code PayloadTypeRegistry} 注册过。
 *
 * <p>这条约束此前只靠人眼维护。漏注册时服务端会在启动阶段直接崩：
 * <pre>
 *   Cannot register handler as no payload type has been registered with name "noellesroles:echo/shop"
 * </pre>
 * 崩溃点离真正的疏漏（少一行 register）很远，因此用离线静态扫描把问题提前到测试阶段。
 *
 * <p>只做源码级检查，不启动 Fabric 网络栈，可在普通单元测试里跑。
 */
class EchoPayloadRegistrationTest {
    private static final Path ECHO_SOURCES =
            Path.of("src/main/java/org/agmas/noellesroles/game/modes/echo");
    private static final Path MOD_PACKETS =
            Path.of("src/main/java/org/agmas/noellesroles/init/ModPackets.java");

    /** 声明侧：源码里出现 {@code implements CustomPacketPayload}。 */
    private static final Pattern DECLARES_PAYLOAD = Pattern.compile("implements\\s+CustomPacketPayload");

    /**
     * 注册侧：{@code ...EchoShopPayload.CODEC}、{@code ...EchoRollRequest.ID} 这类写法。
     * 类名不要求 Payload 后缀，因为 {@code EchoRollRequest} 也是 payload。
     */
    private static final Pattern REGISTERED =
            Pattern.compile("\\b([A-Z][A-Za-z0-9]*)\\s*\\.\\s*(?:ID|CODEC)\\b");

    @Test
    void everyEchoPayloadIsRegistered() throws IOException {
        Set<String> declared = declaredPayloads();
        Set<String> registered = registeredPayloads();

        assertTrue(declared.size() >= 20, "未扫描到回声 payload，检查路径是否变化：" + declared);

        List<String> missing = new ArrayList<>(declared);
        missing.removeAll(registered);
        assertTrue(missing.isEmpty(),
                "以下 payload 已声明但未在 ModPackets 注册，服务端启动会崩：" + missing);
    }

    /** 扫描 echo 包下所有声明了 CustomPacketPayload 的类名。 */
    private static Set<String> declaredPayloads() throws IOException {
        Set<String> names = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(ECHO_SOURCES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                if (!DECLARES_PAYLOAD.matcher(source).find()) continue;
                String name = file.getFileName().toString();
                names.add(name.substring(0, name.length() - ".java".length()));
            }
        }
        return names;
    }

    /** 扫描 ModPackets 中出现的 payload 类名（ID 或 CODEC 任一即可）。 */
    private static Set<String> registeredPayloads() throws IOException {
        String source = Files.readString(MOD_PACKETS, StandardCharsets.UTF_8);
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = REGISTERED.matcher(source);
        while (matcher.find()) names.add(matcher.group(1));
        return names;
    }
}
