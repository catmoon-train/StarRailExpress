package io.wifi.starrailexpress.client;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 检查实际编译产物的短路路径，不在单元测试 JVM 中初始化 Minecraft 注册表。 */
class PlayerRenderHotPathTest {
    private static final String MORPH = "io/wifi/starrailexpress/morph/MorphApiClient";
    private static final String MANAGER = "io/wifi/starrailexpress/plush/PlushEquipmentManager";
    private static final String SKIN = "org/agmas/noellesroles/mixin/client/AbstractClientPlayerSkinMixin";

    @Test
    void ordinaryHeldItemsReturnBeforeAnyDisplayedPlushLookup() throws IOException {
        MethodNode method = method(MORPH, "remapHeldPlush");
        TypeInsnNode plushCheck = null;
        for (var instruction : method.instructions) {
            if (instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.INSTANCEOF
                    && type.desc.equals("org/agmas/noellesroles/content/block/SREPlushItem")) plushCheck = type;
        }
        assertNotNull(plushCheck);
        var branch = assertInstanceOf(JumpInsnNode.class, next(plushCheck));
        assertTrue(branch.getOpcode() == Opcodes.IFNE || branch.getOpcode() == Opcodes.IFEQ);
        AbstractInsnNode falsePath = branch.getOpcode() == Opcodes.IFEQ ? next(branch.label) : next(branch);
        assertEquals(Opcodes.ACONST_NULL, falsePath.getOpcode(), "非玩偶分支必须直接不覆盖，而不是解析拥有者");
        assertEquals(Opcodes.ARETURN, next(falsePath).getOpcode());
        MethodInsnNode lookup = calls(method, MORPH, "getDisplayedPlushStack").getFirst();
        assertTrue(method.instructions.indexOf(lookup) > method.instructions.indexOf(plushCheck));
        assertEquals(1, calls(method, MORPH, "getDisplayedPlushStack").size());
    }

    @Test
    void plushRemappingStillChecksEmptyAndSameComponents() throws IOException {
        MethodNode method = method(MORPH, "remapHeldPlush");
        assertEquals(1, calls(method, "net/minecraft/world/item/ItemStack", "isEmpty").size());
        assertEquals(1, calls(method, "net/minecraft/world/item/ItemStack", "isSameItemSameComponents").size());
        assertEquals(1, calls(method, MORPH, "getDisplayedPlushStack").size());
    }

    @Test
    void identityScanDoesNotAllocateAnInventorySnapshot() throws IOException {
        ClassNode clazz = read(MANAGER);
        MethodNode method = method(clazz, "findIdentityPlush");
        assertFalse(clazz.methods.stream().anyMatch(m -> m.name.equals("collectInventories")));
        for (var instruction : method.instructions) {
            assertFalse(instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW,
                    "查询路径只解码实际候选，不应创建列表或扫描器对象");
        }
        assertFalse(calls(method, MANAGER, "collectInventories").size() > 0);
        assertTrue(calls(method, "net/minecraft/world/entity/player/Inventory", "getItem").size() > 0);
        String slotMapOwner = null;
        for (var instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field
                    && field.owner.equals("io/wifi/starrailexpress/cca/ExtraSlotComponent") && field.name.equals("SLOTS")) {
                slotMapOwner = Type.getType(field.desc).getInternalName();
            }
        }
        assertNotNull(slotMapOwner);
        assertFalse(calls(method, slotMapOwner, "values").isEmpty(), "额外槽位仍必须参与选择");
    }

    @Test
    void preferredIdentityShortCircuitsBothInventoryAndExtraSlots() throws IOException {
        MethodNode method = method(MANAGER, "findIdentityPlush");
        var preferredChecks = calls(method, MANAGER, "isSponsorIdentity");
        assertEquals(2, preferredChecks.size(), "背包与额外槽位均须保留赞助者优先规则");
        for (var check : preferredChecks) {
            var branch = assertInstanceOf(JumpInsnNode.class, next(check));
            AbstractInsnNode preferredPath = branch.getOpcode() == Opcodes.IFNE ? next(branch.label) : next(branch);
            assertEquals(Opcodes.ALOAD, preferredPath.getOpcode());
            assertEquals(Opcodes.ARETURN, next(preferredPath).getOpcode(), "首个优先候选已决定结果，应立即返回");
        }
        int extraIndex = -1;
        for (var instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field
                    && field.owner.equals("io/wifi/starrailexpress/cca/ExtraSlotComponent") && field.name.equals("KEY")) {
                extraIndex = method.instructions.indexOf(instruction);
            }
        }
        assertTrue(extraIndex > method.instructions.indexOf(preferredChecks.getFirst()));
        assertTrue(extraIndex < method.instructions.indexOf(preferredChecks.getLast()));
        assertEquals(2, calls(method, "io/wifi/starrailexpress/plush/PlushEquipmentIdentity", "fromStack").size());
    }

    @Test
    void nullPlayerStillReturnsWithoutInventoryAccess() throws IOException {
        MethodNode method = method(MANAGER, "findIdentityPlush");
        var first = first(method);
        assertEquals(Opcodes.ALOAD, first.getOpcode());
        var branch = assertInstanceOf(JumpInsnNode.class, next(first));
        var nullPath = branch.getOpcode() == Opcodes.IFNULL ? next(branch.label) : next(branch);
        assertEquals(Opcodes.ACONST_NULL, nullPath.getOpcode());
        assertEquals(Opcodes.ARETURN, next(nullPath).getOpcode());
    }

    @Test
    void sameTickSkinCacheReturnsBeforeConfigurationLookup() throws IOException {
        MethodNode method = method(SKIN, "applySkinSwap");
        MethodInsnNode config = calls(method, "io/wifi/starrailexpress/SREClientConfig", "instance").getFirst();
        int configIndex = method.instructions.indexOf(config);
        int fastReturn = -1;
        boolean sawZeroTickBranch = false;
        for (var instruction : method.instructions) {
            if (method.instructions.indexOf(instruction) >= configIndex) break;
            if (instruction instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IFNE) sawZeroTickBranch = true;
            if (instruction.getOpcode() == Opcodes.ARETURN) {
                AbstractInsnNode previous = previous(instruction);
                if (previous instanceof FieldInsnNode field && field.name.equals("cacheResult")) fastReturn = method.instructions.indexOf(instruction);
            }
        }
        assertTrue(sawZeroTickBranch, "delta==0 才能命中同 Tick 快速返回");
        assertTrue(fastReturn > 0 && fastReturn < configIndex);
        assertEquals(1, calls(method, "io/wifi/starrailexpress/SREClientConfig", "instance").size());
        assertEquals(1, calls(method, SKIN, "getResult").size(), "未命中仍走原皮肤事件解析");
        ClassNode clazz = read(SKIN);
        assertEquals(1, clazz.fields.stream().filter(f -> f.name.equals("CACHE_TICK_GAP")).findFirst().orElseThrow().value);
        assertEquals(2, clazz.fields.stream().filter(f -> f.name.equals("CACHE_TICK_GAP_ULTRA")).findFirst().orElseThrow().value);
    }

    @Test
    void lobbyAndWorldGuardsStillPrecedeSkinCache() throws IOException {
        MethodNode method = method(SKIN, "applySkinSwap");
        int firstCache = Integer.MAX_VALUE;
        var lobbyChecks = new ArrayList<Integer>();
        int worldCheck = -1;
        for (var instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field) {
                int i = method.instructions.indexOf(instruction);
                if (field.name.equals("cacheResult")) firstCache = Math.min(firstCache, i);
                if (field.name.equals("isLobby") || field.name.equals("isInLobby")) lobbyChecks.add(i);
                if (field.owner.equals("net/minecraft/client/Minecraft") && field.name.equals("level")) worldCheck = i;
            }
        }
        assertEquals(2, lobbyChecks.size());
        for (int i : lobbyChecks) assertTrue(i < firstCache);
        assertTrue(worldCheck > 0 && worldCheck < firstCache);
    }

    private static ClassNode read(String name) throws IOException {
        try (InputStream input = PlayerRenderHotPathTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(input, name);
            var node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static MethodNode method(String owner, String name) throws IOException { return method(read(owner), name); }
    private static MethodNode method(ClassNode owner, String name) {
        return owner.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }
    private static List<MethodInsnNode> calls(MethodNode method, String owner, String name) {
        var result = new ArrayList<MethodInsnNode>();
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) result.add(call);
        return result;
    }
    private static AbstractInsnNode first(MethodNode method) {
        var current = method.instructions.getFirst();
        while (current != null && current.getOpcode() < 0) current = current.getNext();
        return current;
    }
    private static AbstractInsnNode next(AbstractInsnNode current) {
        do { current = current.getNext(); } while (current != null && current.getOpcode() < 0);
        assertNotNull(current);
        return current;
    }
    private static AbstractInsnNode previous(AbstractInsnNode current) {
        do { current = current.getPrevious(); } while (current != null && current.getOpcode() < 0);
        assertNotNull(current);
        return current;
    }
}
