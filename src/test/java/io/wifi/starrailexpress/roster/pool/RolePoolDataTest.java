package io.wifi.starrailexpress.roster.pool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * 内置职业池的数据自检：池子 id 唯一、职业 path 不重复、投票 resultId 能来回解析。
 *
 * <p>
 * 这些都是纯数据，不依赖服务端 / 客户端运行时，所以可以直接在单测里跑。
 */
class RolePoolDataTest {

    @Test
    void poolIdsAreUniqueAndNotBlank() {
        Set<String> ids = new HashSet<>();
        for (RolePool pool : BuiltinRolePools.ALL) {
            assertFalse(pool.id().isBlank(), "池子 id 不能为空");
            assertTrue(ids.add(pool.id()), "池子 id 重复：" + pool.id());
            assertFalse(pool.fallbackName().isBlank(), "池子展示名不能为空：" + pool.id());
        }
        assertEquals(ids.size(), BuiltinRolePools.ALL.size());
    }

    @Test
    void everyPoolKeepsItsRoleCount() {
        // 需求给的职业池表：常驻池 / 爆破狂欢 / 东方之夜 / 黑暗之时
        assertEquals(78, BuiltinRolePools.RESIDENT.rolePaths().size());
        assertEquals(42, BuiltinRolePools.BLAST_CARNIVAL.rolePaths().size());
        assertEquals(41, BuiltinRolePools.TOUHOU_NIGHT.rolePaths().size());
        assertEquals(40, BuiltinRolePools.DARK_HOURS.rolePaths().size());
        // 东方池 = 东方之夜 + 其它东方主题职业
        assertTrue(BuiltinRolePools.TOUHOU.rolePaths().size() > BuiltinRolePools.TOUHOU_NIGHT.rolePaths().size());
    }

    @Test
    void noDuplicateOrBlankRolePathsInsideAPool() {
        for (RolePool pool : BuiltinRolePools.ALL) {
            Set<String> seen = new HashSet<>();
            for (String path : pool.rolePaths()) {
                assertFalse(path.isBlank(), pool.id() + " 里有空职业 path");
                assertTrue(seen.add(path), pool.id() + " 里职业 path 重复：" + path);
            }
        }
    }

    @Test
    void onlyTheAllRolesPoolIsEmptyAndEveryPoolUsesAllModifiers() {
        for (RolePool pool : BuiltinRolePools.ALL) {
            assertEquals(pool.id().equals("all_roles"), pool.allRoles(),
                    pool.id() + " 的 allRoles 判定不符预期");
            assertTrue(pool.allModifiers(), pool.id() + " 目前应使用全部修饰符");
        }
    }

    @Test
    void touhouPoolContainsEverythingFromTouhouNight() {
        List<String> night = BuiltinRolePools.TOUHOU_NIGHT.rolePaths();
        List<String> touhou = new ArrayList<>(BuiltinRolePools.TOUHOU.rolePaths());
        assertTrue(touhou.containsAll(night), "东方池应包含东方之夜的全部职业");
    }

    @Test
    void voteResultIdRoundTripsAndIgnoresForeignIds() {
        assertEquals("blast_carnival",
                RolePool.idOfResult(BuiltinRolePools.BLAST_CARNIVAL.voteResultId()));
        assertEquals(BuiltinRolePools.RESIDENT.voteResultId(), "role_pool:resident");
        assertNull(RolePool.idOfResult("mode:murder"), "模式投票的 resultId 不应被当成池子");
        assertNull(RolePool.idOfResult("role_pool:"), "空 id 应被拒绝");
        assertNull(RolePool.idOfResult(null));
    }

    @Test
    void nameKeyFollowsTheTranslationConvention() {
        assertEquals("gui.sre.role_pool.name.dark_hours", BuiltinRolePools.DARK_HOURS.nameKey());
    }

    @Test
    void registryLooksUpEveryBuiltinPoolByIdAndResultId() {
        for (RolePool pool : BuiltinRolePools.ALL) {
            assertNotNull(RolePoolRegistry.byId(pool.id()), "注册表里找不到池子：" + pool.id());
            assertEquals(pool.id(), RolePoolRegistry.byResultId(pool.voteResultId()).id());
        }
        assertNull(RolePoolRegistry.byId("nope"));
        assertNull(RolePoolRegistry.byResultId("pool:resident"));
    }
}
