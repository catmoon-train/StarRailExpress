/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.index;

import dev.doctor4t.ratatouille.util.registrar.EntityTypeRegistrar;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.block.entity.SeatEntity;
import io.wifi.starrailexpress.customitem.CustomThrowableEntity;
import io.wifi.starrailexpress.content.entity.*;
import net.exmo.sre.planecrash.CrashPlaneEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public interface TMMEntities {
    EntityTypeRegistrar registrar = new EntityTypeRegistrar(SRE.TMM_MOD_ID);

    EntityType<SeatEntity> SEAT = registrar.create("seat", EntityType.Builder.of(SeatEntity::new, MobCategory.MISC)
            .sized(1f, 1f)
            .clientTrackingRange(128)
            .noSummon()
    );

    EntityType<PlayerBodyEntity> PLAYER_BODY = registrar.create("player_body", EntityType.Builder.of(PlayerBodyEntity::new, MobCategory.MISC)
            .sized(1f, 0.25f)
            .clientTrackingRange(128)
    );
    EntityType<FirecrackerEntity> FIRECRACKER = registrar.create("firecracker", EntityType.Builder.of(FirecrackerEntity::new, MobCategory.MISC)
            .sized(.2f, .2f)
            .clientTrackingRange(128)
    );
    EntityType<GrenadeEntity> GRENADE = registrar.create("grenade", EntityType.Builder.of(GrenadeEntity::new, MobCategory.MISC)
            .sized(.2f, .2f)
            .clientTrackingRange(128)
    );
    EntityType<NoteEntity> NOTE = registrar.create("note", EntityType.Builder.of(NoteEntity::new, MobCategory.MISC)
            .sized(.45f, .45f)
            .clientTrackingRange(128)
    );
    EntityType<StickyGrenadeEntity> STICKY_GRENADE = registrar.create("sticky_grenade",
            EntityType.Builder.of(StickyGrenadeEntity::new, MobCategory.MISC)
                    .sized(.2f, .2f)
                    .clientTrackingRange(128)
    );
    EntityType<TimedGrenadeEntity> TIMED_GRENADE = registrar.create("timed_grenade",
            EntityType.Builder.of(TimedGrenadeEntity::new, MobCategory.MISC)
                    .sized(.2f, .2f)
                    .clientTrackingRange(128)
    );

    /**
     * 自定义列车物品·投掷物的实体：拉栓 / 粘附 / 拆除 / 延迟 / 爆炸 / 区域等行为
     * 全部由物品配置（{@code CUSTOM_ITEM_ID} 组件）驱动，只有一个注册实体。
     *
     * <p>
     * 这里两处都写了<b>显式类型实参</b>并用 lambda 代替方法引用：否则
     * {@code create} 与 {@code Builder.of} 各自的类型变量会被推成不同的值
     * （target 推 {@code CustomThrowableEntity}、实参推上界 {@code Entity}）而报
     * 「推论变量 T 具有不兼容的等式约束条件」。
     */
    EntityType<CustomThrowableEntity> CUSTOM_THROWABLE = registrar.<CustomThrowableEntity>create(
            "custom_throwable",
            EntityType.Builder.<CustomThrowableEntity>of(
                    (type, level) -> new CustomThrowableEntity(type, level),
                    MobCategory.MISC)
                    .sized(.2f, .2f)
                    .clientTrackingRange(128)
    );

    EntityType<ZiplineRiderEntity> ZIPLINE_RIDER = registrar.create("zipline_rider",
            EntityType.Builder.of(ZiplineRiderEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .noSummon()
    );

    EntityType<CrashPlaneEntity> CRASH_PLANE = registrar.create("crash_plane",
            EntityType.Builder.of(CrashPlaneEntity::new, MobCategory.MISC)
                    .sized(12.0f, 4.0f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
    );

    EntityType<PurpleMonsterEntity> PURPLE_MONSTER = registrar.create("purple_monster",
            EntityType.Builder.of(PurpleMonsterEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .clientTrackingRange(64)
                    .updateInterval(2)
    );

    EntityType<PurpleMonsterSecondEntity> PURPLE_MONSTER_SECOND = registrar.create("purple_monster_second",
            EntityType.Builder.of(PurpleMonsterSecondEntity::new, MobCategory.MONSTER)
                    .sized(1.2f, 2.4f)
                    .eyeHeight(1.6f)
                    .clientTrackingRange(64)
                    .updateInterval(2)
    );

    static void initialize() {
        registrar.registerEntries();

        FabricDefaultAttributeRegistry.register(PLAYER_BODY, PlayerBodyEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(PURPLE_MONSTER, PurpleMonsterEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(PURPLE_MONSTER_SECOND, PurpleMonsterSecondEntity.createAttributes());
    }
}
