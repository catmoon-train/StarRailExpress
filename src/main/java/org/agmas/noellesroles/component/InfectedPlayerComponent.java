package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
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
    public int infectedTicks = 0;      // 感染时间（tick）
    public UUID infector = null;       // 感染源玩家
    public int lastSpreadTick = 0;      // 上次传播时间
    public boolean spreadAccelerated = false;  // 加速传播标志
    
    // 死亡原因标识
    public static final net.minecraft.resources.ResourceLocation INFECTION_DEATH_REASON = 
        Noellesroles.id("infection");
    
    // 配置值
    private static final int INFECTED_KILL_TIME = GameConstants.getInTicks(3, 0);  // 180秒致死
    private static final int SPREAD_INTERVAL_NORMAL = 1000;  // 正常情况50秒传播一次（20fps）
    private static final int SPREAD_INTERVAL_ACCELERATED = 200;  // 加速情况10秒传播一次
    private static final int SPREAD_RADIUS = 3;                                    // 传播范围3格
    private static final int MAX_SPREAD_COUNT = 3;                                  // 最多传播3人
    private static final int PROGRESS_SYNC_INTERVAL = 20;                           // 感染倒计时每秒同步一次
    
    // 获取当前传播间隔（根据是否加速）
    private int getSpreadInterval() {
        return spreadAccelerated ? SPREAD_INTERVAL_ACCELERATED : SPREAD_INTERVAL_NORMAL;
    }
    
    /**
     * 设置加速传播模式（供外部调用）
     * @param level 服务器世界
     * @param accelerated 是否加速
     */
    public static void setSpreadAcceleratedForAll(net.minecraft.server.level.ServerLevel level, boolean accelerated) {
        for (Player p : level.players()) {
            InfectedPlayerComponent comp = ModComponents.INFECTED.get(p);
            if (comp != null && comp.infectedTicks > 0 && comp.spreadAccelerated != accelerated) {
                comp.spreadAccelerated = accelerated;
                comp.sync();
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
            this.sync_with_all();
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
        this.sync_with_all();
    }
    
    /**
     * 检查玩家是否可以被感染致死
     * 杀手阵营、杀手方中立、故障机器人无法因感染致死
     */
    public static boolean canDieFromInfection(Player player) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null) return true;
        
        var role = gameWorld.getRole(player);
        if (role == null) return true;
        
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
        if (gameWorldComponent == null) {
            gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        }
    }
    
    @Override
    public void serverTick() {
        if (gameWorldComponent == null) {
            gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        }
        
        if (!gameWorldComponent.gameStatus.equals(SREGameWorldComponent.GameStatus.ACTIVE)) {
            return;
        }
        
        // 如果玩家是疫使自身，重置感染状态
        if (gameWorldComponent.isRole(player, ModRoles.INFECTED)) {
            if (this.infectedTicks > 0) {
                this.cure();
            }
            return;
        }
        
        if (this.infectedTicks <= 0) {
            return;
        }
        
        // 检查感染源是否还存在
        if (this.infector != null) {
            Player infectorPlayer = player.level().getPlayerByUUID(this.infector);
            if (infectorPlayer == null || !GameUtils.isPlayerAliveAndSurvival(infectorPlayer)) {
                // 感染源已死亡或不存在，治愈感染
                this.cure();
                return;
            }
        }
        
        // 增加感染时间
        this.infectedTicks++;
        
        // 播放咳嗽音效（每20tick有几率触发，或快死前10秒）- 附近所有人都能听到
        if ((this.infectedTicks % 20 == 0 && this.player.getRandom().nextInt(100) < 5) || 
            this.infectedTicks == INFECTED_KILL_TIME - 200) {
            this.player.level().playSound(null, this.player.getX(), this.player.getY(), this.player.getZ(),
                NRSounds.INFECTED_COUGH, SoundSource.PLAYERS, 1.5f, 
                1f + (this.player.getRandom().nextInt(5) - 2) * 0.1f);
        }
        
        // 检查是否致死
        if (this.infectedTicks >= INFECTED_KILL_TIME) {
            // 检查是否可以致死
            if (canDieFromInfection(this.player)) {
                Player killer = this.infector != null ?
                    player.level().getPlayerByUUID(this.infector) : null;
                GameUtils.killPlayer(this.player, true, killer, INFECTION_DEATH_REASON);

                // 清除感染状态，防止玩家复活后再次触发死亡
                this.cure();
                
                // 清除中毒状态（感染致死时不清除中毒会导致问题）
                io.wifi.starrailexpress.cca.SREPlayerPoisonComponent poisonComponent = 
                    io.wifi.starrailexpress.cca.SREPlayerPoisonComponent.KEY.get(this.player);
                if (poisonComponent.poisonTicks > 0) {
                    poisonComponent.init();
                    poisonComponent.sync();
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
        }
        
        // 病毒传播逻辑（根据是否加速，每50秒或每10秒传播给附近玩家）
        if (this.infectedTicks % getSpreadInterval() == 0 && this.infectedTicks > 0) {
            this.spreadVirus();
        }
        
        if (this.infectedTicks > 0 && this.infectedTicks % PROGRESS_SYNC_INTERVAL == 0) {
            this.sync();
        }
    }
    
    /**
     * 传播病毒给附近玩家
     */
    private void spreadVirus() {
        Player infectorPlayer = this.infector != null ? 
            player.level().getPlayerByUUID(this.infector) : null;
        
        if (infectorPlayer == null) return;
        
        // 获取附近3格范围内的玩家
        int spreadCount = 0;
        for (Player nearby : player.level().players()) {
            if (nearby == this.player) continue;
            if (!GameUtils.isPlayerAliveAndSurvival(nearby)) continue;
            
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
