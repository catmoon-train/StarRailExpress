package io.wifi.srepackcompat.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** WaterConfig 内部使用 Path.toFile()；将内置资源转为短期普通文件，兼容 JAR/ZIP 资源路径。 */
public final class NativeConfigFile implements AutoCloseable {
    private final Path path;

    private NativeConfigFile(Path path) { this.path = path; }

    public static NativeConfigFile copyOf(Path resource) throws IOException {
        Path temp = Files.createTempFile("sre-pack-compat-chloride-", ".toml");
        try {
            Files.copy(resource, temp, StandardCopyOption.REPLACE_EXISTING);
            return new NativeConfigFile(temp);
        } catch (IOException | RuntimeException | Error failure) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    public Path path() { return path; }

    @Override
    public void close() throws IOException { Files.deleteIfExists(path); }
}
