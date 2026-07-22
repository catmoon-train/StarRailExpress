package org.agmas.noellesroles.game.roles.neutral.musician_phantom;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 幻音师玩家组件
 *
 * 杀手方中立阵营 (setMax=1)
 *
 * 被动：每30秒获得50金币
 *
 * 商店音效：
 * - 出刀的声音 (50金币, 冷却30秒)
 * - 左轮手枪开火的声音 (75金币, 冷却30秒)
 * - 潜行者觉醒的声音 (100金币, 冷却120秒, MASTER类型全场播放)
 * - 疯狂模式的声音 (450金币, 冷却5分钟)
 * - 撬棍撬门的声音 (75金币, 冷却1分钟)
 * - 随机播放音效 (100金币, 冷却40秒, 图标为音乐唱片)
 *
 * 技能：花费100金币传送到30格外随机一人的身边，并播放传送音效和粒子效果，冷却120秒
 */
public class PhantomMusicianPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    public static final ComponentKey<PhantomMusicianPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "phantom_musician"),
            PhantomMusicianPlayerComponent.class);

    private final Player player;

    /** 被动收入计时器 (ticks) */
    public int passiveIncomeTimer = 0;
    public static final int PASSIVE_INCOME_INTERVAL = 30 * 20; // 30秒

    /** 传送技能冷却 */
    public int teleportCooldown = 0;
    public static final int TELEPORT_COOLDOWN = 120 * 20; // 120秒
    public static final int TELEPORT_COST = 100; // 100金币
    public static final double TELEPORT_RANGE = 30.0; // 30格范围

    // ==================== 商店音效冷却 ====================
    public int knifeSoundCooldown = 0;
    public static final int KNIFE_SOUND_COOLDOWN = 30 * 20; // 30秒
    public static final int KNIFE_SOUND_COST = 50;

    public int revolverSoundCooldown = 0;
    public static final int REVOLVER_SOUND_COOLDOWN = 30 * 20; // 30秒
    public static final int REVOLVER_SOUND_COST = 75;

    public int stalkerSoundCooldown = 0;
    public static final int STALKER_SOUND_COOLDOWN = 120 * 20; // 120秒
    public static final int STALKER_SOUND_COST = 100;

    public int psychoSoundCooldown = 0;
    public static final int PSYCHO_SOUND_COOLDOWN = 5 * 60 * 20; // 5分钟
    public static final int PSYCHO_SOUND_COST = 450;

    /** 疯狂模式音效播放计时器 (ticks)，购买后持续播放30秒 */
    public int psychoSoundPlayTimer = 0;
    public static final int PSYCHO_SOUND_PLAY_DURATION = 30 * 20; // 30秒

    public int crowbarSoundCooldown = 0;
    public static final int CROWBAR_SOUND_COOLDOWN = 60 * 20; // 1分钟
    public static final int CROWBAR_SOUND_COST = 75;

    public int randomSoundCooldown = 0;
    public static final int RANDOM_SOUND_COOLDOWN = 40 * 20; // 40秒
    public static final int RANDOM_SOUND_COST = 100;

    public PhantomMusicianPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        this.passiveIncomeTimer = 0;
        this.teleportCooldown = 0;
        this.knifeSoundCooldown = 0;
        this.revolverSoundCooldown = 0;
        this.stalkerSoundCooldown = 0;
        this.psychoSoundCooldown = 0;
        this.crowbarSoundCooldown = 0;
        this.randomSoundCooldown = 0;
        this.psychoSoundPlayTimer = 0;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void serverTick() {
        // 检查玩家是否为幻音师（只有幻音师自己才能获得被动金币）
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.PHANTOM_MUSICIAN)) {
            return;
        }

        // 检查游戏是否进行中
        if (!gameWorld.isRunning()) {
            return;
        }

        if (player.isSpectator())
            return;
        if (!GameUtils.isPlayerAliveAndSurvival(player))
            return;

        // 传送冷却递减
        if (teleportCooldown > 0) {
            teleportCooldown--;
        }
        // 商店音效冷却递减
        if (knifeSoundCooldown > 0)
            knifeSoundCooldown--;
        if (revolverSoundCooldown > 0)
            revolverSoundCooldown--;
        if (stalkerSoundCooldown > 0)
            stalkerSoundCooldown--;
        if (psychoSoundCooldown > 0)
            psychoSoundCooldown--;
        if (crowbarSoundCooldown > 0)
            crowbarSoundCooldown--;
        if (randomSoundCooldown > 0)
            randomSoundCooldown--;
    }

    @Override
    public void clientTick() {
        // 客户端预测冷却递减（与服务端保持同步）
        if (teleportCooldown > 1)
            teleportCooldown--;
        if (knifeSoundCooldown > 1)
            knifeSoundCooldown--;
        if (revolverSoundCooldown > 1)
            revolverSoundCooldown--;
        if (stalkerSoundCooldown > 1)
            stalkerSoundCooldown--;
        if (psychoSoundCooldown > 1)
            psychoSoundCooldown--;
        if (crowbarSoundCooldown > 1)
            crowbarSoundCooldown--;
        if (randomSoundCooldown > 1)
            randomSoundCooldown--;
    }

    /**
     * 传送技能：花费100金币传送到30格外随机一人的身边
     */
    public void useTeleport() {
        if (teleportCooldown > 0)
            return;

        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
        if (shop.balance < TELEPORT_COST)
            return;

        if (!(player.level() instanceof ServerLevel serverLevel))
            return;

        // 收集30格外的存活玩家
        List<ServerPlayer> targets = new ArrayList<>();
        for (ServerPlayer p : serverLevel.players()) {
            if (p == player)
                continue;
            if (!GameUtils.isPlayerAliveAndSurvival(p))
                continue;
            if (player.distanceToSqr(p) <= TELEPORT_RANGE * TELEPORT_RANGE)
                continue;
            targets.add(p);
        }

        if (targets.isEmpty())
            return;

        // 随机选择一个目标
        Collections.shuffle(targets);
        ServerPlayer target = targets.get(0);

        // 扣除金币并记录
        shop.setBalance(shop.balance - TELEPORT_COST);

        ConfigWorldComponent.onPlayerUsedSkill((ServerPlayer) player);

        // 起点特效
        double fromX = player.getX();
        double fromY = player.getY();
        double fromZ = player.getZ();
        playTeleportEffects(serverLevel, fromX, fromY, fromZ);

        // 传送到目标身边
        player.teleportTo(target.getX(), target.getY(), target.getZ());

        // 终点特效
        playTeleportEffects(serverLevel, player.getX(), player.getY(), player.getZ());

        // 设置冷却
        teleportCooldown = TELEPORT_COOLDOWN;
        this.sync();
    }

    /**
     * 播放传送特效（和召回者一样的传送音效和粒子效果）
     */
    private void playTeleportEffects(ServerLevel serverLevel, double centerX, double centerY, double centerZ) {
        double particleY = centerY + 0.9D;

        // 16个PORTAL粒子围成圆圈
        for (int i = 0; i < 16; i++) {
            double angle = Math.PI * 2D * i / 16D;
            double offsetX = Math.cos(angle) * 0.8D;
            double offsetZ = Math.sin(angle) * 0.8D;
            serverLevel.sendParticles(ParticleTypes.PORTAL,
                    centerX + offsetX, particleY, centerZ + offsetZ,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }

        // 中心点更多粒子
        serverLevel.sendParticles(ParticleTypes.PORTAL,
                centerX, particleY, centerZ,
                10, 0.25D, 0.35D, 0.25D, 0.05D);

        // 传送音效
        serverLevel.playSound(null, centerX, centerY, centerZ,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("passiveIncomeTimer", this.passiveIncomeTimer);
        tag.putInt("teleportCooldown", this.teleportCooldown);
        tag.putInt("knifeSoundCooldown", this.knifeSoundCooldown);
        tag.putInt("revolverSoundCooldown", this.revolverSoundCooldown);
        tag.putInt("stalkerSoundCooldown", this.stalkerSoundCooldown);
        tag.putInt("psychoSoundCooldown", this.psychoSoundCooldown);
        tag.putInt("psychoSoundPlayTimer", this.psychoSoundPlayTimer);
        tag.putInt("crowbarSoundCooldown", this.crowbarSoundCooldown);
        tag.putInt("randomSoundCooldown", this.randomSoundCooldown);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.passiveIncomeTimer = tag.contains("passiveIncomeTimer") ? tag.getInt("passiveIncomeTimer") : 0;
        this.teleportCooldown = tag.contains("teleportCooldown") ? tag.getInt("teleportCooldown") : 0;
        this.knifeSoundCooldown = tag.contains("knifeSoundCooldown") ? tag.getInt("knifeSoundCooldown") : 0;
        this.revolverSoundCooldown = tag.contains("revolverSoundCooldown") ? tag.getInt("revolverSoundCooldown") : 0;
        this.stalkerSoundCooldown = tag.contains("stalkerSoundCooldown") ? tag.getInt("stalkerSoundCooldown") : 0;
        this.psychoSoundCooldown = tag.contains("psychoSoundCooldown") ? tag.getInt("psychoSoundCooldown") : 0;
        this.psychoSoundPlayTimer = tag.contains("psychoSoundPlayTimer") ? tag.getInt("psychoSoundPlayTimer") : 0;
        this.crowbarSoundCooldown = tag.contains("crowbarSoundCooldown") ? tag.getInt("crowbarSoundCooldown") : 0;
        this.randomSoundCooldown = tag.contains("randomSoundCooldown") ? tag.getInt("randomSoundCooldown") : 0;
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
