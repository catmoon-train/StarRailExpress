package io.wifi.starrailexpress.event;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** 阵营能力区分及旧导航适配器的源码契约，完整编译负责核对调用签名。 */
class AllegianceNavigationCompatibilityTest {
    @Test
    void bothAllegianceOverloadsKeepNullGuardAndLazyAbilityFallback() throws Exception {
        String game = read("io/wifi/starrailexpress/cca/SREGameWorldComponent.java");
        String byPlayer = method(game, "public @Nullable SRERole getAllegianceRole(@Nullable Player player)");
        String byId = method(game, "public @Nullable SRERole getAllegianceRole(@Nullable UUID uuid)");
        assertTrue(byPlayer.indexOf("if (player == null) return null;") < byPlayer.indexOf("OVERRIDE.invoker()"));
        assertTrue(byId.indexOf("if (uuid == null) return null;") < byId.indexOf("OVERRIDE.invoker()"));
        assertTrue(byPlayer.contains("resolve(player.level(), player.getUUID())"));
        assertTrue(byId.contains("resolve(world, uuid)"));
        assertTrue(byPlayer.contains("override != null ? override : getRole(player)"));
        assertTrue(byId.contains("override != null ? override : getRole(uuid)"));
        assertFalse(byPlayer.contains("CloneDoctor"));
        assertFalse(byId.contains("CloneDoctor"));
    }

    @Test
    void cloneIdentityIsAnInitializedBusinessListenerRatherThanACoreCondition() throws Exception {
        String role = read("org/agmas/noellesroles/game/roles/killer/clone_doctor/CloneDoctorRole.java");
        String initialization = method(role, "public static void registerEvents()");
        assertTrue(initialization.contains("RoleAllegianceEvents.OVERRIDE.register"));
        assertTrue(initialization.contains("isClone(level, playerId) ? ModRoles.CLONE_DOCTOR : null"));
        String register = read("org/agmas/noellesroles/register/NREventRegister.java");
        assertTrue(register.contains("CloneDoctorRole.registerEvents()"));
        String event = read("io/wifi/starrailexpress/event/RoleAllegianceEvents.java");
        assertTrue(event.contains("if (role != null) return role;"));
        assertFalse(event.contains("org.agmas.noellesroles"));
        assertFalse(event.contains("net.minecraft.client"));
    }

    @Test
    void legacyGridAdapterRetainsItsSurfaceHeightAndDirectionContract() throws Exception {
        String navigator = read("org/agmas/noellesroles/game/fake_steve/FakeSteveNavigator.java");
        assertTrue(navigator.contains("static final Direction[] HORIZONTAL"));
        assertTrue(navigator.contains("Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST"));
        String surface = method(navigator, "static double standingSurfaceY(ServerLevel level, BlockPos feet)");
        assertTrue(surface.contains("FakeStevePathPolicy.feetCanOccupy"));
        assertTrue(surface.contains("feet.getY() + height"));
        assertTrue(surface.contains("below.getY() + belowShape.max(Direction.Axis.Y)"));
        assertTrue(navigator.contains("static double standingY(ServerLevel level, BlockPos feet)"));
        String adapter = read("org/agmas/noellesroles/game/fake_steve/FakeSteveLevelNavWorld.java");
        assertTrue(adapter.contains("FakeSteveNavigator.standingSurfaceY(level, feet)"));
        assertTrue(adapter.contains("FakeSteveNavigator.HORIZONTAL"));
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, signature);
        int end = source.indexOf("\n    }", start);
        assertTrue(end > start, signature);
        return source.substring(start, end);
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of("src/main/java", path));
    }
}
