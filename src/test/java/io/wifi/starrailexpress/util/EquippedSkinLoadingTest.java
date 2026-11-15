package io.wifi.starrailexpress.util;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 无需启动 Minecraft 的装备加载链路回归检查。 */
class EquippedSkinLoadingTest {
    private static final String ROOT = "io/wifi/starrailexpress/";
    private static final String MANAGER = ROOT + "util/ItemSkinManager";
    private static final String COMPONENT = ROOT + "cca/SREPlayerSkinsComponent";
    private static final String ECONOMY = ROOT + "data/PlayerSkinEconomyManager";

    @Test
    void serverEquipmentReadsSameComponentAsManagementScreen() throws IOException {
        List<MethodInsnNode> calls = calls(methods(ECONOMY), "getEquippedSkinForItemType");
        assertTrue(calls.stream().anyMatch(call -> call.owner.equals(COMPONENT)
                && call.name.equals("getEquippedSkin")));
        assertFalse(calls.stream().anyMatch(call -> call.name.equals("getOrDefault")),
                "不能继续从独立的 economy 装备映射读取服务端皮肤");
    }

    @Test
    void joiningAndManualEquipmentBothRefreshInventory() throws IOException {
        List<MethodNode> methods = methods(ECONOMY);
        assertRefresh(calls(methods, "onJoin"));
        assertRefresh(calls(methods, "setEquippedSkinForItemType"));
    }

    @Test
    void asyncSkinLoadRefreshesInventoryBeforeSyncingUi() throws IOException {
        boolean found = false;
        for (MethodNode method : methods(COMPONENT)) {
            List<MethodInsnNode> calls = calls(List.of(method), method.name);
            int refresh = -1;
            for (int i = 0; i < calls.size(); i++) {
                MethodInsnNode call = calls.get(i);
                if (call.owner.equals(MANAGER) && call.name.equals("refreshEquippedSkins")) {
                    refresh = i;
                }
                if (refresh >= 0 && call.owner.equals(COMPONENT) && call.name.equals("sync")) {
                    found = true;
                }
            }
        }
        assertTrue(found, "异步拉取后必须先刷新物品，再同步界面的装备状态");
    }

    @Test
    void refreshBypassesOldStackSnapshotAndIncludesOffhand() throws IOException {
        List<MethodInsnNode> calls = calls(methods(MANAGER), "refreshEquippedSkins");
        assertTrue(calls.stream().anyMatch(call -> call.name.equals("getContainerSize")),
                "遍历完整背包以覆盖副手，不能只刷新主手");
        assertTrue(calls.stream().anyMatch(call -> call.owner.equals(COMPONENT)
                && call.name.equals("getEquippedSkin")));
        assertTrue(calls.stream().anyMatch(call -> call.name.equals("resolveRandomSkin")));
        assertTrue(calls.stream().anyMatch(call -> call.name.equals("set")));
        assertFalse(calls.stream().anyMatch(call -> call.owner.equals(MANAGER)
                && call.name.equals("getEquippedSkin")), "刷新时不能重新读回物品的旧皮肤");
    }

    @Test
    void normalInventoryTicksDoNotForceRefreshTransferredItems() throws IOException {
        assertFalse(calls(methods(MANAGER), "applyEquippedSkin").stream()
                .anyMatch(call -> call.name.equals("refreshEquippedSkins")));
    }

    private static void assertRefresh(List<MethodInsnNode> calls) {
        assertTrue(calls.stream().anyMatch(call -> call.owner.equals(MANAGER)
                && call.name.equals("refreshEquippedSkins")));
    }

    private static List<MethodNode> methods(String className) throws IOException {
        try (var input = EquippedSkinLoadingTest.class.getClassLoader().getResourceAsStream(className + ".class")) {
            assertNotNull(input, className);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node.methods;
        }
    }

    private static List<MethodInsnNode> calls(List<MethodNode> methods, String name) {
        List<MethodInsnNode> result = new ArrayList<>();
        for (MethodNode method : methods) {
            if (!method.name.equals(name)) continue;
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call) result.add(call);
            }
        }
        return result;
    }
}
