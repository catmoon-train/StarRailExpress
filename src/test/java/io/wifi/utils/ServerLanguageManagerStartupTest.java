package io.wifi.utils;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class ServerLanguageManagerStartupTest {
    private static final String MANAGER = "io.wifi.utils.ServerLanguageManager";
    private static final String HOLDER = MANAGER + "$HttpClientHolder";

    @Test
    void managerInitializationDoesNotNeedDownloadClient() throws Exception {
        ClassLoader loader = isolatedLoader(true);
        Class<?> manager = Class.forName(MANAGER, true, loader);
        assertSame(loader, manager.getClassLoader());
        assertThrows(ClassNotFoundException.class, () -> Class.forName(HOLDER, true, loader));
    }

    @Test
    void concurrentFirstDownloadUsesOneClientWithOriginalPolicy() throws Exception {
        ClassLoader loader = isolatedLoader(false);
        List<Callable<HttpClient>> calls = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            calls.add(() -> {
                Class<?> holder = Class.forName(HOLDER, true, loader);
                Field field = holder.getDeclaredField("INSTANCE");
                field.setAccessible(true);
                return (HttpClient) field.get(null);
            });
        }
        HttpClient client = null;
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (var result : executor.invokeAll(calls)) {
                HttpClient actual = result.get();
                if (client == null) client = actual;
                assertSame(client, actual);
            }
        }
        assertNotNull(client);
        try (HttpClient downloadClient = client) {
            assertEquals(Duration.ofSeconds(10), downloadClient.connectTimeout().orElseThrow());
            assertEquals(HttpClient.Redirect.NORMAL, downloadClient.followRedirects());
        }
    }

    /** 隔离目标类的初始化状态；其余游戏与日志依赖沿用测试类路径。 */
    private static ClassLoader isolatedLoader(boolean rejectHolder) {
        return new ClassLoader(ServerLanguageManagerStartupTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(MANAGER) && !name.startsWith(MANAGER + "$")) {
                    return super.loadClass(name, resolve);
                }
                if (rejectHolder && name.equals(HOLDER)) {
                    throw new ClassNotFoundException("启动路径不应加载下载客户端");
                }
                synchronized (getClassLoadingLock(name)) {
                    Class<?> type = findLoadedClass(name);
                    if (type == null) {
                        String resource = name.replace('.', '/') + ".class";
                        try (InputStream input = getParent().getResourceAsStream(resource)) {
                            if (input == null) throw new ClassNotFoundException(name);
                            byte[] bytes = input.readAllBytes();
                            type = defineClass(name, bytes, 0, bytes.length);
                        } catch (IOException e) {
                            throw new ClassNotFoundException(name, e);
                        }
                    }
                    if (resolve) resolveClass(type);
                    return type;
                }
            }
        };
    }
}
