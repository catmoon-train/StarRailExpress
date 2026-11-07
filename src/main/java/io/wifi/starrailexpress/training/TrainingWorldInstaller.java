package io.wifi.starrailexpress.training;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** 训练存档的独立 NIO 安装器；不依赖 Minecraft 类，不升级已有存档。 */
public final class TrainingWorldInstaller {
    public static final String MARKER_FILE = "sre-training.json";
    public static final String DEFAULT_DIRECTORY = "SRE_TrainingGround";
    private static final String TEMPLATE = "/data/starrailexpress/training/world-template.zip";
    private static final LinkOption[] NO_LINKS = {LinkOption.NOFOLLOW_LINKS};
    private static final int MAX_ZIP_BYTES = 16 * 1024 * 1024;
    private static final long MAX_FILE_BYTES = 16L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 64L * 1024 * 1024;
    private static final int MAX_ENTRIES = 1024;
    private static final Pattern MARKER = Pattern.compile(
            "\\{\\s*\"id\"\\s*:\\s*\"starrailexpress:training\"\\s*,\\s*\"version\"\\s*:\\s*1\\s*}"
            + "|\\{\\s*\"version\"\\s*:\\s*1\\s*,\\s*\"id\"\\s*:\\s*\"starrailexpress:training\"\\s*}");
    private static final Pattern LAYOUT_VERSION = Pattern.compile("\\A\\s*\\{\\s*\"version\"\\s*:\\s*1\\s*,");

    private TrainingWorldInstaller() {
    }

    /** 只认专用标记和受支持的版本，不依据目录名或世界显示名。 */
    public static boolean isTrainingWorld(Path worldDir) {
        try {
            Path world = worldDir.toAbsolutePath().normalize();
            requireSafeAncestors(world);
            Path marker = world.resolve(MARKER_FILE);
            return Files.isDirectory(world, NO_LINKS) && Files.isRegularFile(marker, NO_LINKS)
                    && Files.size(marker) <= 256
                    && MARKER.matcher(Files.readString(marker, StandardCharsets.UTF_8).trim()).matches();
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    /** 复用已有训练存档；同名普通世界只会导致分配新名称，绝不覆盖。 */
    public static synchronized Path installOrFind(Path savesDir) throws IOException {
        return installOrFind(savesDir, TrainingWorldInstaller::templateStream);
    }

    // 包内重载供纯 Java 测试注入损坏/恶意模板，不暴露给运行时调用方。
    static Path installOrFind(Path savesDir, TemplateSource source) throws IOException {
        Path saves = savesDir.toAbsolutePath().normalize();
        requireSafeAncestors(saves);
        Files.createDirectories(saves);
        try (var children = Files.list(saves)) {
            for (Path candidate : children.filter(path -> path.getFileName().toString()
                    .matches(DEFAULT_DIRECTORY + "(?:_[0-9]+)?")).sorted().toList()) {
                if (isTrainingWorld(candidate)) {
                    return candidate;
                }
            }
        }
        Path target = null;
        for (int suffix = 0; suffix < 10000; suffix++) {
            Path candidate = saves.resolve(DEFAULT_DIRECTORY + (suffix == 0 ? "" : "_" + suffix));
            if (!Files.exists(candidate, NO_LINKS)) {
                target = candidate;
                break;
            }
        }
        if (target == null) {
            throw new IOException("No free training world directory (10000 names are occupied)");
        }
        Path stage = Files.createTempDirectory(saves, ".sre-training-stage-");
        try {
            extract(source, stage);
            // 不使用 REPLACE_EXISTING；原子重命名失败就中止，不降级为逐文件覆盖。
            if (Files.exists(target, NO_LINKS)) {
                throw new IOException("Training destination appeared during installation: " + target);
            }
            Files.move(stage, target, StandardCopyOption.ATOMIC_MOVE);
            return target;
        } finally {
            deleteOwnedTree(stage);
        }
    }

    /**
     * 仅在调用方正常关闭世界并成功调用原版 makeWorldBackup 后调用。
     * 暂存完整模板后原子换位；保留训练设置，失败则恢复旧目录，拒绝普通世界及链接。
     */
    public static synchronized void rebuild(Path worldDir) throws IOException {
        Path world = worldDir.toAbsolutePath().normalize();
        if (!isTrainingWorld(world)) {
            throw new IOException("Refusing to rebuild an unrecognized training world: " + world);
        }
        requireSafeTree(world);
        checkUnlocked(world);
        Path stage = Files.createTempDirectory(world.getParent(), ".sre-training-stage-");
        Path previous = world.resolveSibling(".sre-training-previous-" + UUID.randomUUID());
        boolean movedOld = false;
        boolean installed = false;
        try {
            extract(TrainingWorldInstaller::templateStream, stage);
            Path settings = world.resolve("training-settings.json");
            if (Files.exists(settings, NO_LINKS)) {
                if (!Files.isRegularFile(settings, NO_LINKS) || Files.size(settings) > 1024 * 1024) {
                    throw new IOException("Invalid or oversized training-settings.json");
                }
                Files.copy(settings, stage.resolve("training-settings.json"));
            }
            if (!isTrainingWorld(world)) {
                throw new IOException("Training marker changed during rebuild");
            }
            checkUnlocked(world);
            Files.move(world, previous, StandardCopyOption.ATOMIC_MOVE);
            movedOld = true;
            try {
                Files.move(stage, world, StandardCopyOption.ATOMIC_MOVE);
                installed = true;
            } catch (IOException failure) {
                try {
                    Files.move(previous, world, StandardCopyOption.ATOMIC_MOVE);
                    movedOld = false;
                } catch (IOException rollback) {
                    failure.addSuppressed(rollback);
                }
                throw failure;
            }
        } finally {
            deleteOwnedTree(stage);
            if (movedOld && installed && isTrainingWorld(previous)) {
                deleteOwnedTree(previous);
            }
            // 回滚失败时保留 previous，不删除唯一的旧存档；异常包含恢复所需的路径。
        }
    }

    private static InputStream templateStream() throws IOException {
        InputStream stream = TrainingWorldInstaller.class.getResourceAsStream(TEMPLATE);
        if (stream == null) {
            throw new IOException("Missing bundled training template: " + TEMPLATE);
        }
        return stream;
    }

    @FunctionalInterface
    interface TemplateSource {
        InputStream open() throws IOException;
    }

    private record EntryInfo(long size, long compressedSize) {
    }

    private static void extract(TemplateSource source, Path stage) throws IOException {
        byte[] archive;
        try (InputStream input = source.open()) {
            archive = input.readNBytes(MAX_ZIP_BYTES + 1);
        }
        if (archive.length > MAX_ZIP_BYTES) {
            throw new IOException("Training ZIP exceeds compressed size limit");
        }
        Map<String, EntryInfo> expected = inspectDirectory(archive);
        Set<String> seen = new HashSet<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                EntryInfo info = expected.get(name);
                if (info == null || !seen.add(name)) {
                    throw new IOException("Duplicate or unlisted ZIP entry: " + name);
                }
                Path destination = safeDestination(stage, name);
                if (entry.isDirectory()) {
                    // closeEntry 会自动排空数据，目录也必须显式限制，不能漏过 ZIP 炸弹。
                    if (info.size() != 0 || zip.read() != -1) {
                        throw new IOException("ZIP directory contains file data: " + name);
                    }
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    long size = 0;
                    try (var output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW)) {
                        int read;
                        while ((read = zip.read(buffer)) != -1) {
                            size += read;
                            total += read;
                            if (size > MAX_FILE_BYTES || size > info.size() || total > MAX_TOTAL_BYTES) {
                                throw new IOException("Training ZIP exceeds expanded size limit: " + name);
                            }
                            output.write(buffer, 0, read);
                        }
                    }
                    if (size != info.size()) {
                        throw new IOException("ZIP size mismatch: " + name);
                    }
                }
                zip.closeEntry();
            }
        }
        if (!seen.equals(expected.keySet())) {
            throw new IOException("ZIP local/central directory mismatch");
        }
        validateTemplate(stage);
    }

    /** JDK ZIP 流不提供 Unix mode，必须额外检查中央目录，才能拒绝伪装成文件的软链接。 */
    private static Map<String, EntryInfo> inspectDirectory(byte[] archive) throws IOException {
        ByteBuffer zip = ByteBuffer.wrap(archive).order(ByteOrder.LITTLE_ENDIAN);
        int end = -1;
        for (int i = archive.length - 22; i >= Math.max(0, archive.length - 65557); i--) {
            if (zip.getInt(i) == 0x06054b50 && i + 22 + u16(zip, i + 20) == archive.length) {
                end = i;
                break;
            }
        }
        if (end < 0 || u16(zip, end + 4) != 0 || u16(zip, end + 6) != 0) {
            throw new IOException("Invalid, split or ZIP64 training archive");
        }
        int count = u16(zip, end + 10);
        long offset = u32(zip, end + 16);
        long length = u32(zip, end + 12);
        if (count == 0 || count > MAX_ENTRIES || u16(zip, end + 8) != count || offset + length != end) {
            throw new IOException("Invalid training ZIP directory limits");
        }
        Map<String, EntryInfo> entries = new HashMap<>();
        Set<String> portableNames = new HashSet<>();
        long expanded = 0;
        int cursor = (int) offset;
        for (int i = 0; i < count; i++) {
            if (cursor < 0 || cursor + 46 > end || zip.getInt(cursor) != 0x02014b50) {
                throw new IOException("Malformed training ZIP directory");
            }
            int nameLength = u16(zip, cursor + 28);
            int next = cursor + 46 + nameLength + u16(zip, cursor + 30) + u16(zip, cursor + 32);
            int mode = (int) (u32(zip, cursor + 38) >>> 16) & 0170000;
            long size = u32(zip, cursor + 24);
            long compressed = u32(zip, cursor + 20);
            if (next > end || (mode != 0 && mode != 0100000 && mode != 0040000)
                    || (u16(zip, cursor + 8) & 1) != 0 || u16(zip, cursor + 34) != 0
                    || size > MAX_FILE_BYTES || (expanded += size) > MAX_TOTAL_BYTES
                    || size > Math.max(1, compressed) * 1000L) {
                throw new IOException("Unsafe entry type, encryption or ZIP expansion limit");
            }
            String name = new String(archive, cursor + 46, nameLength, StandardCharsets.UTF_8);
            safeDestination(Path.of("training-stage"), name);
            String portable = name.toLowerCase(Locale.ROOT).replaceAll("/+$", "");
            if (!portableNames.add(portable) || entries.put(name, new EntryInfo(size, compressed)) != null) {
                throw new IOException("Duplicate training ZIP path: " + name);
            }
            cursor = next;
        }
        if (cursor != end) {
            throw new IOException("Trailing ZIP directory data");
        }
        return entries;
    }

    private static int u16(ByteBuffer bytes, int offset) {
        return Short.toUnsignedInt(bytes.getShort(offset));
    }

    private static long u32(ByteBuffer bytes, int offset) {
        return Integer.toUnsignedLong(bytes.getInt(offset));
    }

    private static Path safeDestination(Path stage, String name) throws IOException {
        if (name.isEmpty() || name.length() > 240 || name.startsWith("/") || name.contains("\\")
                || name.indexOf(':') >= 0 || name.indexOf('\0') >= 0) {
            throw new IOException("Invalid training ZIP path: " + name);
        }
        String trimmed = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        for (String part : trimmed.split("/", -1)) {
            if (part.isBlank() || part.equals(".") || part.equals("..") || part.endsWith(".") || part.endsWith(" ")
                    || !part.matches("[A-Za-z0-9_.-]+")
                    || part.toUpperCase(Locale.ROOT).matches("(?:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?")) {
                throw new IOException("Nonportable or traversing ZIP path: " + name);
            }
        }
        Path target = stage.resolve(trimmed).normalize();
        if (!target.startsWith(stage) || target.equals(stage)) {
            throw new IOException("ZIP path escaped staging directory: " + name);
        }
        return target;
    }

    private static void validateTemplate(Path stage) throws IOException {
        if (!isTrainingWorld(stage)) {
            throw new IOException("Missing or unsupported training marker/version");
        }
        for (String name : new String[]{"level.dat", "region/r.0.0.mca", "layout.json",
                "train_maps/training.json", "readyArea.json"}) {
            if (!Files.isRegularFile(stage.resolve(name), NO_LINKS) || Files.size(stage.resolve(name)) == 0) {
                throw new IOException("Incomplete training template: " + name);
            }
        }
        try (InputStream level = Files.newInputStream(stage.resolve("level.dat"))) {
            if (level.read() != 0x1f || level.read() != 0x8b) {
                throw new IOException("Training level.dat is not gzip NBT");
            }
        }
        if (Files.size(stage.resolve("region/r.0.0.mca")) < 12288
                || Files.size(stage.resolve("layout.json")) > 1024 * 1024
                || !LAYOUT_VERSION.matcher(Files.readString(stage.resolve("layout.json"))).find()) {
            throw new IOException("Invalid training region or layout version");
        }
        for (String forbidden : new String[]{"session.lock", "playerdata", "stats", "advancements"}) {
            if (Files.exists(stage.resolve(forbidden), NO_LINKS)) {
                throw new IOException("Template contains private/session data: " + forbidden);
            }
        }
    }

    private static void checkUnlocked(Path world) throws IOException {
        Path lock = world.resolve("session.lock");
        if (!Files.exists(lock, NO_LINKS)) {
            return;
        }
        try (FileChannel channel = FileChannel.open(lock, StandardOpenOption.WRITE);
                var acquired = channel.tryLock()) {
            if (acquired == null) {
                throw new IOException("Training world is still locked: " + world);
            }
        } catch (OverlappingFileLockException busy) {
            throw new IOException("Training world is still open: " + world, busy);
        }
    }

    private static void requireSafeAncestors(Path path) throws IOException {
        for (Path part = path; part != null; part = part.getParent()) {
            if (Files.exists(part, NO_LINKS)
                    && (Files.isSymbolicLink(part) || Files.readAttributes(part, BasicFileAttributes.class,
                            LinkOption.NOFOLLOW_LINKS).isOther())) {
                throw new IOException("Refusing symbolic link/reparse path: " + part);
            }
        }
    }

    private static void requireSafeTree(Path root) throws IOException {
        requireSafeAncestors(root);
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                if (attributes.isOther() || attributes.isSymbolicLink() || Files.isSymbolicLink(directory)) {
                    throw new IOException("Refusing linked or special world directory: " + directory);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (!attributes.isRegularFile()) {
                    throw new IOException("Refusing linked or special world file: " + file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // 只用于本次创建的暂存目录，或成功换位后、标记仍然有效的旧训练目录。
    private static void deleteOwnedTree(Path root) throws IOException {
        if (!Files.exists(root, NO_LINKS)) {
            return;
        }
        requireSafeTree(root);
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
