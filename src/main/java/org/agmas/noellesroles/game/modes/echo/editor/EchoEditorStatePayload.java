package org.agmas.noellesroles.game.modes.echo.editor;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 编辑工具物品的状态快照（只发回操作者本人）。
 *
 * <p>只含地图 id / 草稿 revision / 点位摘要 / 角点坐标 / 一条本地化消息，不含文件路径或其它玩家数据；
 * 所有列表都有硬上限，避免把超大地图整份推给客户端。角点坐标同时供「取自准星」回显与输入框手动编辑使用；
 * {@link #detail} 是当前选中点位类型的逐条列表（id / 人数档 / 模板坐标），供界面直接改和删。
 */
public record EchoEditorStatePayload(boolean session, String mapId, String mapName, long revision,
                                     boolean declared, boolean fileExists, boolean bound,
                                     boolean cornerA, boolean cornerB,
                                     double cornerAX, double cornerAY, double cornerAZ,
                                     double cornerBX, double cornerBY, double cornerBZ,
                                     List<Section> sections, Section detail, List<String> issues,
                                     String messageKey, List<String> messageArgs) implements CustomPacketPayload {
    public static final int MAX_SECTIONS = 12, MAX_IDS = 8, MAX_ISSUES = 8, MAX_ARGS = 3;
    public static final int MAX_TEXT = 96, MAX_KEY = 64;
    /** 逐条列表上限（界面里一页放不下可以滚，这里只兜底防超大包）。 */
    public static final int MAX_ENTRIES = 24;

    /** 一个点位的逐条信息：id / 人数档（0 = 该类型没有人数档）/ 模板坐标。 */
    public record Entry(String id, int players, double x, double y, double z) {
        public Entry {
            Objects.requireNonNull(id);
            if (id.length() > MAX_TEXT) throw new IllegalArgumentException("点位id过长");
        }
    }

    /** 一个字段的点位摘要：字段名 + 总数 + 前若干个 id（+ 逐条列表，仅 detail 填充）。 */
    public record Section(String field, int count, List<String> ids, List<Entry> entries) {
        public Section {
            Objects.requireNonNull(field); ids = List.copyOf(ids); entries = List.copyOf(entries);
            if (field.length() > MAX_KEY || ids.size() > MAX_IDS || entries.size() > MAX_ENTRIES)
                throw new IllegalArgumentException("编辑摘要超限");
        }
    }

    public static final Type<EchoEditorStatePayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath("noellesroles", "echo/editor_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EchoEditorStatePayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeBoolean(p.session()); buf.writeUtf(p.mapId(), 128); buf.writeUtf(p.mapName(), 128);
        buf.writeVarLong(p.revision()); buf.writeBoolean(p.declared());
        buf.writeBoolean(p.fileExists()); buf.writeBoolean(p.bound());
        buf.writeBoolean(p.cornerA()); buf.writeBoolean(p.cornerB());
        buf.writeDouble(p.cornerAX()); buf.writeDouble(p.cornerAY()); buf.writeDouble(p.cornerAZ());
        buf.writeDouble(p.cornerBX()); buf.writeDouble(p.cornerBY()); buf.writeDouble(p.cornerBZ());
        writeSections(buf, p.sections());
        buf.writeBoolean(p.detail() != null);
        if (p.detail() != null) writeSection(buf, p.detail());
        buf.writeVarInt(p.issues().size());
        for (var issue : p.issues()) buf.writeUtf(issue, MAX_TEXT);
        buf.writeUtf(p.messageKey(), MAX_KEY); buf.writeVarInt(p.messageArgs().size());
        for (var arg : p.messageArgs()) buf.writeUtf(arg, MAX_TEXT);
    }, buf -> {
        boolean session = buf.readBoolean(); String mapId = buf.readUtf(128), mapName = buf.readUtf(128);
        long revision = buf.readVarLong(); boolean declared = buf.readBoolean();
        boolean fileExists = buf.readBoolean(), bound = buf.readBoolean();
        boolean cornerA = buf.readBoolean(), cornerB = buf.readBoolean();
        double cornerAX = buf.readDouble(), cornerAY = buf.readDouble(), cornerAZ = buf.readDouble();
        double cornerBX = buf.readDouble(), cornerBY = buf.readDouble(), cornerBZ = buf.readDouble();
        var sections = readSections(buf);
        Section detail = buf.readBoolean() ? readSection(buf) : null;
        int issueCount = buf.readVarInt();
        if (issueCount < 0 || issueCount > MAX_ISSUES) throw new IllegalArgumentException("校验问题过多");
        var issues = new ArrayList<String>(issueCount);
        for (int n = 0; n < issueCount; n++) issues.add(buf.readUtf(MAX_TEXT));
        String messageKey = buf.readUtf(MAX_KEY); int argCount = buf.readVarInt();
        if (argCount < 0 || argCount > MAX_ARGS) throw new IllegalArgumentException("消息参数过多");
        var args = new ArrayList<String>(argCount);
        for (int n = 0; n < argCount; n++) args.add(buf.readUtf(MAX_TEXT));
        return new EchoEditorStatePayload(session, mapId, mapName, revision, declared, fileExists, bound,
                cornerA, cornerB, cornerAX, cornerAY, cornerAZ, cornerBX, cornerBY, cornerBZ,
                sections, detail, issues, messageKey, args);
    });

    private static void writeSections(RegistryFriendlyByteBuf buf, List<Section> sections) {
        buf.writeVarInt(sections.size());
        for (var section : sections) writeSection(buf, section);
    }

    private static void writeSection(RegistryFriendlyByteBuf buf, Section section) {
        buf.writeUtf(section.field(), MAX_KEY); buf.writeVarInt(section.count());
        buf.writeVarInt(section.ids().size());
        for (var id : section.ids()) buf.writeUtf(id, MAX_TEXT);
        buf.writeVarInt(section.entries().size());
        for (var entry : section.entries()) {
            buf.writeUtf(entry.id(), MAX_TEXT); buf.writeVarInt(entry.players());
            buf.writeDouble(entry.x()); buf.writeDouble(entry.y()); buf.writeDouble(entry.z());
        }
    }

    private static List<Section> readSections(RegistryFriendlyByteBuf buf) {
        int sectionCount = buf.readVarInt();
        if (sectionCount < 0 || sectionCount > MAX_SECTIONS) throw new IllegalArgumentException("编辑摘要字段过多");
        var sections = new ArrayList<Section>(sectionCount);
        for (int n = 0; n < sectionCount; n++) sections.add(readSection(buf));
        return sections;
    }

    private static Section readSection(RegistryFriendlyByteBuf buf) {
        String field = buf.readUtf(MAX_KEY); int count = buf.readVarInt(); int size = buf.readVarInt();
        if (size < 0 || size > MAX_IDS) throw new IllegalArgumentException("编辑摘要 id 过多");
        var ids = new ArrayList<String>(size);
        for (int i = 0; i < size; i++) ids.add(buf.readUtf(MAX_TEXT));
        int entryCount = buf.readVarInt();
        if (entryCount < 0 || entryCount > MAX_ENTRIES) throw new IllegalArgumentException("编辑逐条列表过长");
        var entries = new ArrayList<Entry>(entryCount);
        for (int i = 0; i < entryCount; i++)
            entries.add(new Entry(buf.readUtf(MAX_TEXT), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble()));
        return new Section(field, count, ids, entries);
    }

    public EchoEditorStatePayload {
        mapId = mapId == null ? "" : mapId; mapName = mapName == null ? "" : mapName;
        messageKey = messageKey == null ? "" : messageKey;
        sections = List.copyOf(sections); issues = List.copyOf(issues); messageArgs = List.copyOf(messageArgs);
        if (sections.size() > MAX_SECTIONS || issues.size() > MAX_ISSUES || messageArgs.size() > MAX_ARGS)
            throw new IllegalArgumentException("编辑快照超限");
    }

    /** 空状态（未开启会话 / 无权限时回给客户端）。 */
    public static EchoEditorStatePayload empty(String messageKey, String... args) {
        return new EchoEditorStatePayload(false, "", "", 0L, false, false, false, false, false,
                0, 0, 0, 0, 0, 0, List.of(), null, List.of(), messageKey, List.of(args));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
