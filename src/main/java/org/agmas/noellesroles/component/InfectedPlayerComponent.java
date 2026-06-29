package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

/**
 * 感染玩家组件
 * 跟踪玩家的感染状态
 */
public class InfectedPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    // KEY 在 ModComponents.INFECTED 中定义

    private final Player player;
    private SREGameWorldComponent gameWorldComponent;

    // 感染状态
    public int infectedTicks = 0; // 感染时间（tick）
    public UUID infector = null; // 感染源玩家
    public int lastSpreadTick = 0; // 上次传播时间
    public boolean spreadAccelerated = false; // 加速传播标志
    
    // 缓存的感染源玩家引用（减少每tick的UUID查找）
    private Player cachedInfectorPlayer = null;
    private int infectorCheckCounter = 0;
    private static final int INFECTOR_CHECK_INTERVAL = 40; // 每2秒检查一次感染源状态（原来每tick检查）

    // 死亡原因标识
    public static final net.minecraft.resources.ResourceLocation INFECTION_DEATH_REASON = GameConstants.DeathReasons.INFECTION;

    // 配置值
    private static final int INFECTED_KILL_TIME = 240 * 20; // 240秒致死，硬编码
    private static final int SPREAD_INTERVAL_NORMAL = 1000; // 正常情况50秒传播一次（20fps）
    private static final int SPREAD_INTERVAL_ACCELERATED = 200; // 加速情况10秒传播一次
    private static final int SPREAD_RADIUS = 3; // 传播范围3格
    private static final int MAX_SPREAD_COUNT = 1; // 最多传播1人
    private static final int PROGRESS_SYNC_INTERVAL = 200; // 感染倒计时每10秒同步一次

    // 获取当前传播间隔（根据是否加速）
    private int getSpreadInterval() {
        return spreadAccelerated ? SPREAD_INTERVAL_ACCELERATED : SPREAD_INTERVAL_NORMAL;
    }

    /**
     * 设置加速传播模式（供外部调用）
     * 
     * @param level       服务器世界
     * @param accelerated 是否加速
     */
    public static void setSpreadAcceleratedForAll(net.minecraft.server.level.ServerLevel level, boolean accelerated) {
        for (Player p : level.players()) {
            InfectedPlayerComponent comp = ModComponents.INFECTED.get(p);
            if (comp != null && comp.infectedTicks > 0 && comp.spreadAccelerated != accelerated) {
                comp.spreadAccelerated = accelerated;
                comp.sync(); // shouldSyncWith 自动处理向疫使的广播
            }
        }
    }

    public InfectedPlayerComponent(Player player) {
        this.player = player;
        this.gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
    }

    @Override
    public Player getPlayer() {
        return this.player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        // 同步给组件所有者（被感染者）和所有疫使
        // 注意：常规进度同步每10秒一次（PROGRESS_SYNC_INTERVAL=200tick），
        // 最频繁的tick处理已在 InfectedWinChecker 中节流至1次/秒
        return player == this.player ||
                (gameWorldComponent != null && gameWorldComponent.isRole(player, ModRoles.INFECTED));
    }

    public void sync() {
        ModComponents.INFECTED.sync(this.player);
    }

    public void sync_with_all() {
        this.sync();
    }

    @Override
    public void init() {
        this.infectedTicks = 0;
        this.infector = null;
        this.lastSpreadTick = 0;
        this.spreadAccelerated = false;
        this.cachedInfectorPlayer = null;
        this.infectorCheckCounter = 0;
    }

    @Override
    public void clear() {
        this.init();
    }

    /**
     * 感染玩家
     */
    public void infect(Player infectorPlayer) {
        if (this.infectedTicks <= 0) {
            this.infectedTicks = 1;
            this.infector = infectorPlayer.getUUID();
            this.lastSpreadTick = 0;
            this.cachedInfectorPlayer = infectorPlayer;
            this.infectorCheckCounter = 0;
            this.sync(); // 同步给所有者+所有疫使
        }
    }

    /**
     * 治愈感染
     */
    public void cure() {
        if (this.infectedTicks <= 0 && this.infector == null && !this.spreadAccelerated) {
            return;
        }
        this.infectedTicks = 0;
        this.infector = null;
        this.lastSpreadTick = 0;
        this.spreadAccelerated = false;
        this.cachedInfectorPlayer = null;
        this.infectorCheckCounter = 0;
        this.sync(); // 同步给所有者+所有疫使
    }

    /**
     * 检查玩家是否可以被感染致死
     * 杀手阵营、杀手方中立、故障机器人无法因感染致死
     */
    public static boolean canDieFromInfection(Player player) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null)
            return true;

        var role = gameWorld.getRole(player);
        if (role == null)
            return true;

        // 杀手阵营无法因感染致死
        if (role.isKillerTeam() || role.isKiller()) {
            return false;
        }

        // 故障机器人免疫病毒感染
        if (gameWorld.isRole(player, ModRoles.GLITCH_ROBOT)) {
            return false;
        }

        return true;
    }

    @Override
    public void clientTick() {
        // 未感染者无需处理（客户端大多数玩家的状态）
        if (this.infectedTicks <= 0) {
            return;
        }

        if (gameWorldComponent == null) {
            gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        }

        if (!gameWorldComponent.gameStatus.equals(SREGameWorldComponent.GameStatus.ACTIVE)) {
            return;
        }

        // 如果玩家是疫使自身，重置感染状态
        if (gameWorldComponent.isRole(player, ModRoles.INFECTED)) {
            this.infectedTicks = 0;
            return;
        }

        // 客户端进度预测（咳嗽音效已移至服务端）
        this.infectedTicks++;
    }

    @Override
    public void serverTick() {
        // 延迟初始化 gameWorldComponent（必须在所有分支前确保可用）
        if (gameWorldComponent == null) {
            gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        }

        // 快速退出：未感染者直接返回（大多数玩家的状态，避免后续开销）
        if (this.infectedTicks <= 0) {
            // 但需要检查疫使自身（只有在游戏活跃时才需要）
            if (!gameWorldComponent.gameStatus.equals(SREGameWorldComponent.GameStatus.ACTIVE)) {
                return;
            }
            // 如果玩家是疫使自身，重置感染状态（只执行一次）
            if (gameWorldComponent.isRole(player, ModRoles.INFECTED)) {
                if (this.infectedTicks > 0) {
                    this.cure();
                }
            }
            return;
        }

        // 以下是已感染玩家的逻辑

        if (!gameWorldComponent.gameStatus.equals(SREGameWorldComponent.GameStatus.ACTIVE)) {
            return;
        }

        // 如果玩家已死亡，立即清除感染状态
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            this.cure();
            return;
        }

        // 如果玩家是疫使自身，重置感染状态
        if (gameWorldComponent.isRole(player, ModRoles.INFECTED)) {
            this.cure();
            return;
        }

        // 检查感染源是否还存在（节流：每2秒检查一次，原来每tick检查）
        if (this.infector != null) {
            infectorCheckCounter++;
            if (infectorCheckCounter >= INFECTOR_CHECK_INTERVAL) {
                infectorCheckCounter = 0;
                cachedInfectorPlayer = player.level().getPlayerByUUID(this.infector);
                if (cachedInfectorPlayer == null || !GameUtils.isPlayerAliveAndSurvival(cachedInfectorPlayer)) {
                    // 感染源已死亡或不存在，治愈感染
                    this.cure();
                    return;
                }
            }
        }

        // 增加感染时间
        this.infectedTicks++;

        // 播放咳嗽音效（服务端确保可靠，20tick间隔/18%几率，或快死前10秒必触发）
        if ((this.infectedTicks % 20 == 0 && this.player.getRandom().nextInt(100) < 18) ||
                this.infectedTicks == INFECTED_KILL_TIME - 200) {
            this.player.level().playSound(null, this.player.getX(), this.player.getY(), this.player.getZ(),
                    NRSounds.INFECTED_COUGH, SoundSource.PLAYERS, 1.5f,
                    1f + (this.player.getRandom().nextInt(5) - 2) * 0.1f);
        }

        // 检查是否致死
        if (this.infectedTicks >= INFECTED_KILL_TIME) {
            // 检查是否可以致死
            if (canDieFromInfection(this.player)) {
                Player killer = this.infector != null ? player.level().getPlayerByUUID(this.infector) : null;
                GameUtils.killPlayer(this.player, true, killer, INFECTION_DEATH_REASON);

                // 清除感染状态，防止玩家复活后再次触发死亡
                this.cure();

                // 清除中毒状态（感染致死时不清除中毒会导致问题）
                io.wifi.starrailexpress.cca.SREPlayerPoisonComponent poisonComponent = io.wifi.starrailexpress.cca.SREPlayerPoisonComponent.KEY
                        .get(this.player);
                if (poisonComponent.poisonTicks > 0) {
                    poisonComponent.clear();
                }

                // 重置疫使的技能冷却
                if (killer != null) {
                    SREAbilityPlayerComponent abilityComponent = SREAbilityPlayerComponent.KEY.get(killer);
                    if (abilityComponent != null) {
                        abilityComponent.cooldown = 0;
                        abilityComponent.sync();
                    }
                }
            } else {
                // 杀手阵营不因感染致死，但清除感染状态
                this.cure();
            }
            return; // 已死亡，无需继续处理传播
        }

        // 病毒传播逻辑（根据是否加速，每50秒或每10秒传播给附近玩家）
        if (this.infectedTicks % getSpreadInterval() == 0 && this.infectedTicks > 0) {
            this.spreadVirus();
        }

        // 定期同步（每10秒同步一次感染进度）
        if (this.infectedTicks % PROGRESS_SYNC_INTERVAL == 0) {
            this.sync();
        }
    }

    /**
     * 传播病毒给附近玩家
     */
    private void spreadVirus() {
        Player infectorPlayer = this.infector != null ? player.level().getPlayerByUUID(this.infector) : null;

        if (infectorPlayer == null)
            return;

        // 获取附近3格范围内的玩家
        int spreadCount = 0;
        for (Player nearby : player.level().players()) {
            if (nearby == this.player)
                continue;
            if (!GameUtils.isPlayerAliveAndSurvival(nearby))
                continue;

            double distance = this.player.distanceTo(nearby);
            if (distance <= SPREAD_RADIUS) {
                // 检查目标是否已被感染
                InfectedPlayerComponent targetComponent = ModComponents.INFECTED.get(nearby);
                if (targetComponent.infectedTicks <= 0) {
                    // 检查目标是否可以被感染
                    if (canDieFromInfection(nearby)) {
                        // 感染目标
                        targetComponent.infect(infectorPlayer);
                        spreadCount++;

                        // 播放熊猫打喷嚏音效 - 表示病毒传播
                        nearby.level().playSound(null, nearby.getX(), nearby.getY(), nearby.getZ(),
                                SoundEvents.PANDA_SNEEZE, SoundSource.PLAYERS, 0.8f, 1f);

                        if (spreadCount >= MAX_SPREAD_COUNT) {
                            break;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (this.infectedTicks > 0) {
            tag.putInt("infectedTicks", this.infectedTicks);
        }
        if (this.infector != null) {
            tag.putUUID("infector", this.infector);
        }
        if (this.spreadAccelerated) {
            tag.putBoolean("spreadAccelerated", true);
        }
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.infectedTicks = tag.contains("infectedTicks") ? tag.getInt("infectedTicks") : 0;
        this.infector = tag.contains("infector") ? tag.getUUID("infector") : null;
        this.spreadAccelerated = tag.getBoolean("spreadAccelerated");
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("infectedTicks", this.infectedTicks);
        if (this.infector != null) {
            tag.putUUID("infector", this.infector);
        }
        tag.putBoolean("spreadAccelerated", this.spreadAccelerated);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.infectedTicks = tag.contains("infectedTicks") ? tag.getInt("infectedTicks") : 0;
        this.infector = tag.contains("infector") ? tag.getUUID("infector") : null;
        this.spreadAccelerated = tag.getBoolean("spreadAccelerated");
    }
}
