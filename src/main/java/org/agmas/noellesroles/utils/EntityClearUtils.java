package org.agmas.noellesroles.utils;

import io.wifi.starrailexpress.entity.NoteEntity;
import io.wifi.starrailexpress.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.OnGameEnd;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ThrownTrident;

import org.agmas.harpymodloader.events.GameInitializeEvent;
import org.agmas.noellesroles.entity.KuiXiPuppetEntity;
import org.agmas.noellesroles.entity.LockEntity;
import org.agmas.noellesroles.entity.LockEntityManager;
import org.agmas.noellesroles.entity.WheelchairEntity;
import pro.fazeclan.river.stupid_express.role.necromancer.cca.NecromancerComponent;

public class EntityClearUtils {
    public static void registerResetEvent() {
        GameInitializeEvent.EVENT.register((serverLevel, gameWorldComponent, players) -> {
            LockEntityManager.getInstance().resetLockEntities();
        });
        OnGameEnd.EVENT.register((world, gameWorldComponent) -> {
            var component = NecromancerComponent.KEY.get(world);
            component.reset();
            LockEntityManager.getInstance().resetLockEntities();
        });
    }

    public static void clearAllEntities(ServerLevel world) {
        // 先清除所有锁实体及其映射
        try {

            // // 清除玩家属性
            // for (var pl : world.players()) {
            // RoleUtils.RemoveAllPlayerAttributes(pl);
            // }

            // 收集需要删除的实体列表，避免在遍历过程中修改集合
            java.util.List<net.minecraft.world.entity.Entity> entitiesToRemove = new java.util.ArrayList<>();

            world.getAllEntities().forEach((entity) -> {
                if (entity instanceof LockEntity ||
                        entity instanceof ThrownTrident ||
                        entity instanceof AreaEffectCloud ||
                        entity instanceof ItemEntity ||
                        entity instanceof PlayerBodyEntity ||
                        entity instanceof WheelchairEntity ||
                        entity instanceof KuiXiPuppetEntity ||
                        entity instanceof NoteEntity) {
                    entitiesToRemove.add(entity);
                }
            });
            // 安全地删除收集到的实体
            for (net.minecraft.world.entity.Entity entity : entitiesToRemove) {
                if (!entity.isRemoved()) { // 双重检查确保实体未被其他地方删除
                    entity.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                }
            }
        } catch (Exception ignored) {
        }
    }
}
