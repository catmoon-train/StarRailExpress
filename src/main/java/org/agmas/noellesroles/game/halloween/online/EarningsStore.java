package org.agmas.noellesroles.game.halloween.online;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 带校验的只追加账本。新增/确认先 force 再改变内存；所有文件操作使用同一互斥锁。
 * 保留已确认事件用于凭据去重，不执行有丢失窗口的重写/压缩。 */
public final class EarningsStore implements AutoCloseable {
    @FunctionalInterface
    interface BeforeForce { void run(String payload) throws IOException; }
    private final FileChannel channel;
    private final FileLock lock;
    private final BeforeForce beforeForce;
    private final Map<UUID, Earning> seen = new LinkedHashMap<>();
    private final Map<UUID, Earning> pending = new LinkedHashMap<>();
    private UUID serverId;
    private boolean broken;

    public EarningsStore(Path directory) throws IOException { this(directory, payload -> {}); }

    EarningsStore(Path directory, BeforeForce beforeForce) throws IOException {
        Files.createDirectories(directory);
        this.beforeForce = beforeForce;
        channel = FileChannel.open(directory.resolve("earnings.wal"), StandardOpenOption.CREATE,
                StandardOpenOption.READ, StandardOpenOption.WRITE);
        FileLock acquired = null;
        try {
            acquired = channel.tryLock();
            if (acquired == null) throw new IOException("Halloween outbox is already locked");
            lock = acquired;
            recover(directory);
            if (serverId == null) {
                serverId = UUID.randomUUID();
                append("S " + serverId);
            }
        } catch (IOException | RuntimeException ex) {
            if (acquired != null) acquired.release();
            channel.close();
            throw new IOException("Cannot open halloween outbox; preserve earnings.wal for recovery", ex);
        }
    }

    public synchronized UUID serverId() { return serverId; }
    public synchronized int pendingCount() { return pending.size(); }
    public synchronized List<Earning> pending(int limit) {
        List<Earning> result = new ArrayList<>();
        for (Earning entry : pending.values()) {
            if (result.size() >= limit) break;
            result.add(entry);
        }
        return List.copyOf(result);
    }
    public synchronized boolean isPending(UUID id) { return pending.containsKey(id); }

    public synchronized void record(Earning entry) throws IOException {
        ensureOpen();
        Earning previous = seen.get(entry.id());
        if (previous != null) {
            if (!previous.equals(entry)) throw new IOException("Conflicting earning id; outbox preserved");
            return;
        }
        append("E " + entry.id() + " " + entry.minecraftUuid() + " " + entry.amount() + " " + entry.earnedAt());
        seen.put(entry.id(), entry);
        pending.put(entry.id(), entry);
    }

    public synchronized void acknowledge(Collection<UUID> ids) throws IOException {
        ensureOpen();
        List<UUID> accepted = ids.stream().distinct().filter(pending::containsKey).toList();
        if (accepted.isEmpty()) return;
        append("A " + String.join(",", accepted.stream().map(UUID::toString).toList()));
        for (UUID id : accepted) pending.remove(id);
    }

    private void ensureOpen() throws IOException {
        if (broken || !channel.isOpen()) throw new IOException("Halloween outbox failed closed; repair/restart required");
    }

    private void append(String payload) throws IOException {
        ensureOpen();
        long start = channel.size();
        try {
            channel.position(start);
            ByteBuffer buffer = ByteBuffer.wrap((payload + "\t" + checksum(payload) + "\n").getBytes(StandardCharsets.UTF_8));
            while (buffer.hasRemaining()) channel.write(buffer);
            beforeForce.run(payload);
            channel.force(true);
        } catch (IOException ex) {
            try {
                channel.truncate(start);
                channel.force(true);
            } catch (IOException rollback) {
                broken = true;
                ex.addSuppressed(rollback);
            }
            throw ex;
        }
    }

    private void recover(Path directory) throws IOException {
        channel.position(0);
        ByteBuffer buffer = ByteBuffer.allocate(8192);
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        long completeEnd = 0;
        long position = 0;
        while (channel.read(buffer) > 0) {
            buffer.flip();
            while (buffer.hasRemaining()) {
                byte value = buffer.get();
                position++;
                if (value == '\n') {
                    try {
                        restoreLine(line.toString(StandardCharsets.UTF_8));
                    } catch (RuntimeException ex) {
                        throw new IOException("Corrupt halloween WAL at byte " + completeEnd + "; failed closed, file preserved");
                    }
                    line.reset();
                    completeEnd = position;
                } else {
                    line.write(value);
                    if (line.size() > 65536) throw new IOException("Oversized/corrupt halloween WAL; file preserved");
                }
            }
            buffer.clear();
        }
        if (line.size() > 0) {
            // 先保存半截尾行，再截到最后一个完整且校验正确的记录。
            Path tail = directory.resolve("incomplete-tail-" + UUID.randomUUID() + ".bin");
            try (FileChannel backup = FileChannel.open(tail, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                ByteBuffer bytes = ByteBuffer.wrap(line.toByteArray());
                while (bytes.hasRemaining()) backup.write(bytes);
                backup.force(true);
            }
            channel.truncate(completeEnd);
            channel.force(true);
        }
    }

    private void restoreLine(String line) {
        int split = line.lastIndexOf('\t');
        if (split < 0) throw new IllegalArgumentException();
        String payload = line.substring(0, split);
        if (!checksum(payload).equals(line.substring(split + 1))) throw new IllegalArgumentException();
        String[] parts = payload.split(" ", -1);
        if (parts[0].equals("S") && parts.length == 2 && serverId == null && seen.isEmpty()) {
            serverId = Earning.canonicalUuid(parts[1]);
        } else if (parts[0].equals("E") && parts.length == 5 && serverId != null) {
            Earning entry = new Earning(Earning.canonicalUuid(parts[1]), Earning.canonicalUuid(parts[2]),
                    Integer.parseInt(parts[3]), Long.parseLong(parts[4]));
            if (seen.putIfAbsent(entry.id(), entry) != null) throw new IllegalArgumentException();
            pending.put(entry.id(), entry);
        } else if (parts[0].equals("A") && parts.length == 2 && serverId != null) {
            for (String value : parts[1].split(",", -1)) {
                UUID id = Earning.canonicalUuid(value);
                if (!seen.containsKey(id)) throw new IllegalArgumentException();
                pending.remove(id);
            }
        } else throw new IllegalArgumentException();
    }

    private static String checksum(String payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        try { if (lock.isValid()) lock.release(); }
        finally { channel.close(); }
    }
}
