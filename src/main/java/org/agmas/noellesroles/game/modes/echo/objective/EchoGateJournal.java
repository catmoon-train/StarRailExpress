package org.agmas.noellesroles.game.modes.echo.objective;

import com.google.gson.Gson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** 方块写入前持久化允许恢复的前/后状态；与外部修改冲突时不强行覆盖。 */
public final class EchoGateJournal {
    public record Pos(int x,int y,int z) { }
    public record Entry(Pos pos,String original,List<String> expected) { }
    public record Document(UUID matchId,String dimension,String mapId,List<Entry> entries) { }
    public interface Access {
        String read(Pos pos);
        void write(Pos pos,String state);
    }
    private static final Gson GSON=new Gson();
    private final Path file;
    private final UUID matchId;
    private final String dimension,mapId;
    private final Map<Pos,String> original,expected;

    public EchoGateJournal(Path world,UUID matchId,String dimension,String mapId,Map<Pos,String> originals) {
        if(originals.isEmpty() || originals.size()>1024) throw new IllegalArgumentException("门体日志大小非法");
        this.matchId=Objects.requireNonNull(matchId); this.dimension=Objects.requireNonNull(dimension); this.mapId=Objects.requireNonNull(mapId);
        original=Map.copyOf(originals); expected=new LinkedHashMap<>(originals);
        file=world.resolve("echo_freight/recovery").resolve(matchId+".json");
    }

    public void apply(Map<Pos,String> changes,Access access) throws IOException {
        List<Entry> entries=new ArrayList<>();
        for(var change:changes.entrySet()) {
            if(!expected.containsKey(change.getKey()) || !access.read(change.getKey()).equals(expected.get(change.getKey()))) {
                throw new IOException("门体被外部修改: "+change.getKey());
            }
        }
        for(var p:original.keySet()) {
            String before=expected.get(p),after=changes.getOrDefault(p,before);
            entries.add(new Entry(p,original.get(p),before.equals(after)?List.of(after):List.of(before,after)));
        }
        save(file,new Document(matchId,dimension,mapId,entries));
        for(var change:changes.entrySet()) access.write(change.getKey(),change.getValue());
        expected.putAll(changes);
        entries.clear();
        for(var p:original.keySet()) entries.add(new Entry(p,original.get(p),List.of(expected.get(p))));
        save(file,new Document(matchId,dimension,mapId,entries));
    }

    public boolean restore(Access access) throws IOException {
        if(!Files.exists(file)) return true;
        return restoreFile(file,dimension,access);
    }

    public static List<String> recover(Path world,String dimension,Access access) {
        List<String> problems=new ArrayList<>();
        Path directory=world.resolve("echo_freight/recovery");
        if(!Files.isDirectory(directory)) return List.of();
        try(var paths=Files.list(directory)) {
            for(Path file:paths.filter(p -> p.getFileName().toString().endsWith(".json")).limit(257).toList()) {
                try { if(!restoreFile(file,dimension,access)) problems.add(file.getFileName().toString()); }
                catch(IOException|RuntimeException failure) { problems.add(file.getFileName()+": "+failure.getMessage()); }
            }
        } catch(IOException failure) { problems.add(failure.getMessage()); }
        return List.copyOf(problems);
    }

    public static boolean pending(Path world,String dimension,String mapId) {
        Path directory=world.resolve("echo_freight/recovery");
        if(!Files.isDirectory(directory)) return false;
        try(var paths=Files.list(directory)) {
            for(Path file:paths.filter(p -> p.getFileName().toString().endsWith(".json")).limit(257).toList()) {
                Document document=read(file);
                if(document.dimension().equals(dimension) && document.mapId().equals(mapId)) return true;
            }
            return false;
        } catch(IOException|RuntimeException failure) { return true; }
    }

    private static boolean restoreFile(Path file,String dimension,Access access) throws IOException {
        Document document=read(file);
        if(!dimension.equals(document.dimension())) return true;
        boolean clean=true;
        for(Entry entry:document.entries()) {
            String current=access.read(entry.pos());
            if(current.equals(entry.original())) continue;
            if(entry.expected().contains(current)) access.write(entry.pos(),entry.original());
            else clean=false;
        }
        if(clean) Files.delete(file);
        return clean;
    }

    private static Document read(Path file) throws IOException {
        if(Files.isSymbolicLink(file) || Files.size(file)>1024*1024) throw new IOException("非法恢复日志");
        Document document=GSON.fromJson(Files.readString(file,StandardCharsets.UTF_8),Document.class);
        if(document==null || document.matchId()==null || document.dimension()==null || document.mapId()==null
                || document.entries()==null || document.entries().size()>1024 || document.entries().isEmpty()
                || !file.getFileName().toString().equals(document.matchId()+".json")) throw new IOException("恢复日志结构无效");
        Set<Pos> seen=new HashSet<>();
        for(Entry entry:document.entries()) {
            if(entry==null || entry.pos()==null || !seen.add(entry.pos()) || Math.abs((long)entry.pos().x())>30_000_000
                    || Math.abs((long)entry.pos().z())>30_000_000 || Math.abs((long)entry.pos().y())>4096
                    || entry.original()==null || entry.original().length()>4096 || entry.expected()==null
                    || entry.expected().isEmpty() || entry.expected().size()>2
                    || entry.expected().stream().anyMatch(s -> s==null || s.length()>4096)) throw new IOException("恢复日志条目无效");
        }
        return document;
    }

    private static void save(Path file,Document document) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary=Files.createTempFile(file.getParent(),".gate-",".tmp");
        try {
            Files.writeString(temporary,GSON.toJson(document),StandardCharsets.UTF_8);
            try { Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException ignored) { Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
