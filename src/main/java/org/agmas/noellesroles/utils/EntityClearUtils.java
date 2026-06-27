package org.agmas.noellesroles.utils;

import io.wifi.starrailexpress.content.entity.GrenadeEntity;
import io.wifi.starrailexpress.content.entity.NoteEntity;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.OnGameEnd;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ThrownTrident;
import org.agmas.harpymodloader.events.GameInitializeEvent;
import org.agmas.noellesroles.content.block_entity.DevilRouletteTableEntity;
import org.agmas.noellesroles.content.entity.CalamityMarkEntity;
import org.agmas.noellesroles.content.entity.ChlorineBombEntity;
import org.agmas.noellesroles.content.entity.DecoyGrenadeEntity;
import org.agmas.noellesroles.content.entity.FlareEntity;
import org.agmas.noellesroles.content.entity.FlashGrenadeEntity;
import org.agmas.noellesroles.content.entity.KuiXiPuppetEntity;
import org.agmas.noellesroles.content.entity.LockEntity;
import org.agmas.noellesroles.content.entity.LockEntityManager;
import org.agmas.noellesroles.content.entity.MummyEntity;
import org.agmas.noellesroles.content.entity.PoisonGasCloudEntity;
import org.agmas.noellesroles.content.entity.PoisonGasTankEntity;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;
import org.agmas.noellesroles.content.entity.PurifyBombEntity;
import org.agmas.noellesroles.content.entity.SilenceTotemEntity;
import org.agmas.noellesroles.content.entity.SmokeGrenadeEntity;
import org.agmas.noellesroles.content.entity.ThrowingKnifeEntity;
import org.agmas.noellesroles.content.entity.TripwireTrapEntity;
import org.agmas.noellesroles.content.entity.WheelchairEntity;
import org.agmas.noellesroles.game.roles.neutral.cuckoo.CuckooEggData;
import pro.fazeclan.river.stupid_express.role.necromancer.cca.NecromancerComponent;

public class EntityClearUtils {
    public static void registerResetEvent() {
        GameInitializeEvent.EVENT.register((serverLevel, gameWorldComponent, players) -> {
            LockEntityManager.getInstance().resetLockEntities();
            clearCuckooEggs(serverLevel);
            CuckooEggData.reset();
        });
        OnGameEnd.EVENT.register((world, gameWorldComponent) -> {
            var component = NecromancerComponent.KEY.get(world);
            component.reset();
            LockEntityManager.getInstance().resetLockEntities();
            // 先清除蛋实体，再重置蛋数据（顺序很重要！）
            clearCuckooEggs(world);
            CuckooEggData.reset();
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
                        entity instanceof Pig ||
                        entity instanceof MummyEntity ||
                        entity instanceof GrenadeEntity ||
                        entity instanceof SmokeGrenadeEntity ||
                        entity instanceof ThrowingKnifeEntity ||
                        entity instanceof ChlorineBombEntity ||
                        entity instanceof PurifyBombEntity ||
                        entity instanceof PoisonGasTankEntity ||
                        entity instanceof PoisonGasCloudEntity ||
                        entity instanceof CalamityMarkEntity ||
                        entity instanceof TripwireTrapEntity ||
                        entity instanceof PuppeteerBodyEntity ||
                        entity instanceof FlashGrenadeEntity ||
                        entity instanceof FlareEntity ||
                        entity instanceof DecoyGrenadeEntity ||
                        entity instanceof SilenceTotemEntity ||
                        entity instanceof ThrownTrident ||
                        entity instanceof AreaEffectCloud ||
                        entity instanceof ItemEntity ||
                        entity instanceof PlayerBodyEntity ||
                        entity instanceof WheelchairEntity ||
                        entity instanceof KuiXiPuppetEntity ||
                        entity instanceof NoteEntity ||
                        entity instanceof DevilRouletteTableEntity.TableTextDisplay ||
                        entity instanceof DevilRouletteTableEntity.TableItemDisplay) {
                    entitiesToRemove.add(entity);
                } else if (entity instanceof io.github.mortuusars.exposure.world.entity.PhotographFrameEntity
                        && entity instanceof org.agmas.noellesroles.game.roles.innocence.photographer.SrePhotographerFrame frame
                        && frame.sre$isPhotographerPlaced()) {
                    // 仅清理摄影师放置的照片框
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

    /** 在reset之前清除所有布谷鸟蛋实体 */
    private static void clearCuckooEggs(ServerLevel world) {
        try {
            java.util.List<net.minecraft.world.entity.Entity> eggsToRemove = new java.util.ArrayList<>();
            world.getAllEntities().forEach((entity) -> {
                if (entity instanceof net.minecraft.world.entity.Display.BlockDisplay
                        && CuckooEggData.isCuckooEgg(entity)) {
                    eggsToRemove.add(entity);
                }
            });
            for (net.minecraft.world.entity.Entity egg : eggsToRemove) {
                if (!egg.isRemoved()) {
                    egg.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                }
            }
        } catch (Exception ignored) {
        }
    }
}
