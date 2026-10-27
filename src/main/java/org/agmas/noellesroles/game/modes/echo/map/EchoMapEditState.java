package org.agmas.noellesroles.game.modes.echo.map;

import java.io.IOException;
import java.util.UUID;

/** 编辑坐标与一次性覆盖确认；不持有世界，不接触文件或建筑。 */
public final class EchoMapEditState {
    public static final long TTL=36_000, CONFIRM_TTL=600;
    public record Context(String dimension,String mapId,EchoMapConfig.Vec offset) { }
    public record Confirmation(UUID nonce,String mapId,long revision,String digest,long expiresAt) { }
    private final Context context;
    private final EchoMapDraft draft;
    private long touched;
    private EchoMapConfig.Vec cornerA,cornerB;
    private Confirmation confirmation;
    public EchoMapEditState(Context context,EchoMapDraft draft,long now) throws IOException {
        EchoMapRepository.requireMapId(context.mapId());
        var o=context.offset();
        if(o.x()!=Math.rint(o.x()) || o.y()!=Math.rint(o.y()) || o.z()!=Math.rint(o.z())) throw new IOException("模板平移必须为整数方块");
        this.context=context; this.draft=draft; this.touched=now;
    }
    public Context context() { return context; }
    public EchoMapDraft draft() { return draft; }
    public boolean valid(Context current,long now) { return context.equals(current) && now>=touched && now-touched<TTL; }
    public void touch(long now) { touched=now; }
    public EchoMapConfig.Vec template(EchoMapConfig.Vec world) {
        var o=context.offset(); return new EchoMapConfig.Vec(world.x()-o.x(),world.y()-o.y(),world.z()-o.z());
    }
    public void corner(boolean first,EchoMapConfig.Vec worldBlock) {
        if(worldBlock.x()!=Math.rint(worldBlock.x()) || worldBlock.y()!=Math.rint(worldBlock.y()) || worldBlock.z()!=Math.rint(worldBlock.z())) throw new IllegalArgumentException("角点必须是整数方块");
        if(first) cornerA=template(worldBlock); else cornerB=template(worldBlock);
    }
    public EchoMapConfig.Box selection() {
        if(cornerA==null || cornerB==null) throw new IllegalArgumentException("请先选择 corner a 和 corner b");
        return new EchoMapConfig.Box(Math.min(cornerA.x(),cornerB.x()),Math.min(cornerA.y(),cornerB.y()),Math.min(cornerA.z(),cornerB.z()),
                Math.max(cornerA.x(),cornerB.x())+1,Math.max(cornerA.y(),cornerB.y())+1,Math.max(cornerA.z(),cornerB.z())+1);
    }
    public boolean hasCornerA() { return cornerA != null; }
    public boolean hasCornerB() { return cornerB != null; }
    /** 角点坐标（模板坐标系）；没设过返回 null。 */
    public EchoMapConfig.Vec cornerA() { return cornerA; }
    public EchoMapConfig.Vec cornerB() { return cornerB; }
    public Confirmation confirm(String mapId,String digest,long now) throws IOException {
        EchoMapRepository.requireMapId(mapId);
        if(digest==null || !digest.matches("[0-9a-f]{64}")) throw new IOException("覆盖摘要无效");
        confirmation=new Confirmation(UUID.randomUUID(),mapId,draft.revision(),digest,now+CONFIRM_TTL); return confirmation;
    }
    /** 先撤销令牌，再验证；即使失败也不能重放旧确认。 */
    public String consume(UUID nonce,String mapId,String currentDigest,long now) throws IOException {
        var token=confirmation; confirmation=null;
        if(token==null || !token.nonce().equals(nonce) || !token.mapId().equals(mapId) || token.revision()!=draft.revision()
                || now>=token.expiresAt() || !token.digest().equals(currentDigest)) throw new IOException("覆盖确认失效：草稿、文件已变化或令牌已过期，请重新 save");
        return token.digest();
    }
    public void saved() { confirmation=null; }
}
