package org.agmas.noellesroles.blood;

import java.util.LinkedHashSet;
import java.util.SequencedSet;

import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.blood.particle.BloodParticle;
import org.agmas.noellesroles.repack.HSRItems;

import io.wifi.starrailexpress.entity.GrenadeEntity;
import io.wifi.starrailexpress.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.index.TMMItems;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

/**
 * 血液效果主控制器
 * 负责血液粒子的初始化、检测和生成逻辑
 * 根据玩家死亡原因（武器类型）生成不同类型的血液效果
 */
public class BloodMain {
    /** 血液粒子类型定义 */
    public static final SimpleParticleType BLOOD_PARTICLE = FabricParticleTypes.simple();

    /** 标记当前tick是否有手榴弹爆炸 */
    private boolean grenadeThisTick = false;

    /** 是否启用粒子效果 */
    public boolean enabled = false;

    /** 记录最近几个tick内是否有手榴弹爆炸（用于检测连续爆炸） */
    private SequencedSet<Boolean> hasGrenade = new LinkedHashSet<>();

    /**
     * 初始化血液系统
     * 注册粒子工厂、配置武器血液效果、设置客户端tick事件监听
     */
    public void init() {
        ParticleFactoryRegistry.getInstance().register(BloodMain.BLOOD_PARTICLE, BloodParticle.Factory::new);
        // 配置不同武器的血液效果参数
        // 参数说明：武器物品，强度，方向性，最小血量，最大血量，扩散区域
        BloodItems.addItem(TMMItems.REVOLVER, 3.0, 1.0, 5, 7, new Vec3(0.5, 0.5, 0.5));
        BloodItems.addItem(ModItems.PATROLLER_REVOLVER, 3.0, 1.0, 5, 7, new Vec3(0.5, 0.5, 0.5));
        BloodItems.addItem(HSRItems.BANDIT_REVOLVER, 3.0, 1.0, 5, 7, new Vec3(0.5, 0.5, 0.5));
        BloodItems.addItem(TMMItems.DERRINGER, 3.0, 1.0, 5, 7, new Vec3(0.5, 0.5, 0.5));
        BloodItems.addItem(TMMItems.BAT, 0.5, 0.7, 5, 7, new Vec3(0.8, 0.8, 0.8));
        BloodItems.addItem(TMMItems.KNIFE, 1.0, 0.3, 5, 7, new Vec3(0.3, 0.3, 0.3));

        // 注册客户端tick结束事件监听器，用于检测血液生成
        ClientTickEvents.END_CLIENT_TICK.register((client) -> {
            if (client.level != null) {
                if (this.enabled)
                    this.DetectBloodSpawn(client.level);
            }
        });
    }

    /**
     * 检测并生成血液效果
     * 在每个客户端tick结束时调用，遍历所有实体，检测玩家尸体并生成血液
     * 
     * @param world 客户端世界对象
     */
    private void DetectBloodSpawn(ClientLevel world) {
        boolean isThereBodys = false; // 标记当前世界中是否有玩家尸体
        this.grenadeThisTick = false; // 重置手榴弹标记

        // 遍历所有渲染实体
        var var3 = world.entitiesForRendering().iterator();

        while (true) {
            Entity entity;
            do {
                do {
                    // 如果没有更多实体，执行清理和退出逻辑
                    if (!var3.hasNext()) {
                        // 记录当前tick的手榴弹状态
                        if (this.grenadeThisTick) {
                            this.hasGrenade.add(true);
                        } else {
                            this.hasGrenade.add(false);
                        }

                        // 限制历史记录大小为2（只保留最近两个tick的状态）
                        if (this.hasGrenade.size() > 2) {
                            this.hasGrenade.removeLast();
                        }

                        // 如果没有尸体，清除所有血液粒子
                        if (!isThereBodys) {
                            BloodParticle.clearParticles();
                        }

                        return;
                    }

                    entity = (Entity) var3.next();
                    // 检测手榴弹实体
                    if (entity instanceof GrenadeEntity) {
                        this.grenadeThisTick = true;
                    }
                } while (!(entity instanceof PlayerBodyEntity)); // 跳过非玩家尸体实体

                // 发现玩家尸体
                isThereBodys = true;
            } while (entity.tickCount != 1); // 只在尸体生成的第一tick处理血液效果

            // 寻找距离最近的、持有已配置武器的玩家作为"杀手"
            double closest = Double.MIN_VALUE;
            Player closestPlayer = null;
            var var8 = world.players().iterator();

            while (var8.hasNext()) {
                Player potentialKiller = (Player) var8.next();
                // 检查玩家是否持有已配置的武器且距离最近
                if (BloodItems.getItems().contains(potentialKiller.getInventory().getSelected().getItem())
                        && (double) potentialKiller.distanceTo(entity) > closest) {
                    closestPlayer = potentialKiller;
                }
            }

            // 确定凶手和武器，生成血液效果
            if (closestPlayer != null) {
                // 使用最近玩家手中的武器生成血液
                this.OnBloodSpawn(world, (PlayerBodyEntity) entity, closestPlayer,
                        closestPlayer.getInventory().getSelected().getItem());
            } else {
                // 如果没有找到持有武器的玩家，使用手榴弹作为默认武器
                this.OnBloodSpawn(world, (PlayerBodyEntity) entity, world.getNearestPlayer(entity, 1000.0),
                        TMMItems.GRENADE);
            }
        }
    }

    /**
     * 生成血液效果
     * 根据凶手、武器类型和尸体位置生成相应的血液粒子
     * 
     * @param world  客户端世界
     * @param victim 受害者实体（玩家尸体）
     * @param killer 凶手玩家
     * @param weapon 使用的武器
     */
    private void OnBloodSpawn(ClientLevel world, PlayerBodyEntity victim, Player killer, Item weapon) {
        // 计算血液生成的基础位置（尸体眼睛位置）
        Vec3 pos = victim.getEyePosition();
        Vec3 rot = victim.getLookAngle(); // 尸体的视线方向

        // 调整血液生成位置，稍微偏移以避开尸体模型
        Vec3 pos2 = null;
        Vec3 rot2 = null;
        pos2 = pos.add(-rot.x, 0.8999999761581421, -rot.z);

        // 进行射线投射，找到血液应该命中的表面
        BloodRaycastUtils.RaycastResult raycast = BloodRaycastUtils.raycastToPlayerBox(killer, pos2);
        if (raycast != null) {
            pos2 = raycast.hitPosition; // 命中点位置
            rot2 = raycast.normal; // 反射方向（用于血液飞溅方向）

            // 遍历所有已配置的武器血液效果
            var strange_var = BloodItems.getBloods().iterator();

            while (strange_var.hasNext()) {
                BloodItems.ItemBlood blood = (BloodItems.ItemBlood) strange_var.next();

                // 检查是否有手榴弹爆炸
                if (this.hasGrenade.contains(true)) {
                    // 生成随机方向的爆炸血液效果
                    this.spawnRandomBlood(world, pos2, 5, 25, 50, new Vec3(0.8, 2.0, 0.8));
                    this.hasGrenade.clear(); // 清除手榴弹记录
                } else if (weapon.equals(blood.item)) {
                    // 根据武器类型生成定向血液效果
                    this.spawnDirectionalBlood(world, pos2, rot2, blood.st, blood.dt, blood.minBlood,
                            blood.maxBlood, blood.area);
                    // 生成血液云（无速度的血液粒子）
                    this.spawnBloodCloud(world, pos2, blood.minBlood, blood.maxBlood, blood.area);
                }
            }
        }
    }

    /**
     * 生成血液云效果
     * 在指定位置生成无速度的血液粒子，模拟血液滴落或聚集效果
     * 
     * @param world    客户端世界
     * @param pos      生成中心位置
     * @param minBlood 最小粒子数量
     * @param maxBlood 最大粒子数量
     * @param area     粒子生成区域大小
     */
    public void spawnBloodCloud(ClientLevel world, Vec3 pos, int minBlood, int maxBlood, Vec3 area) {
        // 血液云粒子数量减半（相比飞溅效果）
        maxBlood /= 2;
        minBlood /= 2;

        RandomSource rand = RandomSource.create();
        int bloodAmount = rand.nextInt(maxBlood + 1 - minBlood) + minBlood;

        // 生成指定数量的血液粒子
        for (int i = 1; i <= bloodAmount; ++i) {
            // 在区域内随机生成位置
            double posX = pos.x + (area.x * (double) rand.nextFloat() - area.x / 2.0);
            double posY = pos.y + (area.y * (double) rand.nextFloat() - area.y / 2.0);
            double posZ = pos.z + (area.z * (double) rand.nextFloat() - area.z / 2.0);

            // 血液云粒子无速度
            double velX = 0.0;
            double velY = 0.0;
            double velZ = 0.0;

            world.addParticle(BLOOD_PARTICLE, true, posX, posY, posZ, velX, velY, velZ);
        }
    }

    /**
     * 生成定向血液效果
     * 根据反射方向和武器参数生成有方向性的血液飞溅效果
     * 
     * @param world         客户端世界
     * @param pos           生成起始位置
     * @param rot           反射方向（血液飞溅主方向）
     * @param strength      血液强度（影响粒子速度）
     * @param directiveness 方向性（值越小粒子方向越集中）
     * @param minBlood      最小粒子数量
     * @param maxBlood      最大粒子数量
     * @param area          粒子生成区域大小
     */
    public void spawnDirectionalBlood(ClientLevel world, Vec3 pos, Vec3 rot, double strength, double directiveness,
            int minBlood, int maxBlood, Vec3 area) {
        RandomSource rand = RandomSource.create();
        int bloodAmount = rand.nextInt(maxBlood + 1 - minBlood) + minBlood;

        for (int i = 1; i <= bloodAmount; ++i) {
            // 在区域内随机生成起始位置
            double posX = pos.x + (area.x * (double) rand.nextFloat() - area.x / 2.0);
            double posY = pos.y + (area.y * (double) rand.nextFloat() - area.y / 2.0);
            double posZ = pos.z + (area.z * (double) rand.nextFloat() - area.z / 2.0);

            // 计算粒子速度：基于反射方向，加上随机偏移（受方向性参数控制）
            double velX = (rot.x + (directiveness * (double) rand.nextFloat() - directiveness / 2.0))
                    * (strength / 10.0);
            double velY = (rot.y + (directiveness * (double) rand.nextFloat() - directiveness / 2.0))
                    * (strength / 10.0);
            double velZ = (rot.z + (directiveness * (double) rand.nextFloat() - directiveness / 2.0))
                    * (strength / 10.0);

            world.addParticle(BLOOD_PARTICLE, true, posX, posY, posZ, velX, velY, velZ);
        }
    }

    /**
     * 生成随机血液效果
     * 主要用于手榴弹爆炸等无明确方向的血液效果
     * 
     * @param world    客户端世界
     * @param pos      生成中心位置
     * @param strength 血液强度
     * @param minBlood 最小粒子数量
     * @param maxBlood 最大粒子数量
     * @param area     粒子生成区域大小
     */
    public void spawnRandomBlood(ClientLevel world, Vec3 pos, int strength, int minBlood, int maxBlood, Vec3 area) {
        RandomSource rand = RandomSource.create();
        int bloodAmount = rand.nextInt(maxBlood + 1 - minBlood) + minBlood;

        for (int i = 1; i <= bloodAmount; ++i) {
            // 在区域内随机生成起始位置
            double posX = pos.x + (area.x * (double) rand.nextFloat() - area.x / 2.0);
            double posY = pos.y + (area.y * (double) rand.nextFloat() - area.y / 2.0);
            double posZ = pos.z + (area.z * (double) rand.nextFloat() - area.z / 2.0);

            // 生成随机方向的速度（全方向随机）
            double velX = (double) ((rand.nextFloat() * 2.0F - 1.0F) / 10.0F * (float) strength);
            double velY = (double) ((rand.nextFloat() * 2.0F - 1.0F) / 10.0F * (float) strength);
            double velZ = (double) ((rand.nextFloat() * 2.0F - 1.0F) / 10.0F * (float) strength);

            world.addParticle(BLOOD_PARTICLE, true, posX, posY, posZ, velX, velY, velZ);
        }
    }
}