package org.agmas.noellesroles.game.roles.vigilante.ghost_eye;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 鬼眼·杨间组件（警长阵营）。
 *
 * <p>被动·鬼眼：每隔固定间隔（默认 16 秒）自动扫描周身 {@link #SCAN_RADIUS} 格范围，
 * 短暂（默认 2 秒）以白色直觉轮廓显示范围内所有玩家。轮廓判定在客户端
 * {@code InstinctRenderer} 中完成，本组件只负责服务端的扫描计时与同步
 * {@link #revealTicks} 计时器。
 *
 * <p>主动·诡域（按技能键释放，冷却见 {@link NoellesRolesConfig#ghostEyeDomainCooldown}）：
 * 在脚下展开一片半径 {@link NoellesRolesConfig#ghostEyeDomainRadius} 格、持续
 * {@link NoellesRolesConfig#ghostEyeDomainDuration} 秒的领域。领域内所有人减速（缓慢 II）；
 * 领域内的杀手无法开启透视（{@link ModEffects#EERIE_DOMAIN} 标记，由
 * {@code InstinctMixin} 拦截）；除杨间本人外的所有人陷入失明与黑暗。
 */
public class GhostEyePlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    public static final ComponentKey<GhostEyePlayerComponent> KEY = ModComponents.GHOST_EYE;

    /** 被动扫描半径（格）。客户端轮廓判定与服务端一致。 */
    public static final int SCAN_RADIUS = 20;
    /** 单次扫描的轮廓显示时长（tick）= 2 秒。 */
    public static final int REVEAL_TICKS = 40;
    /** 领域内效果每 tick 续期的时长（tick），离开后约 0.5 秒自然消退。 */
    private static final int EFFECT_REFRESH = 10;

    private final Player player;

    /** 距下一次扫描的剩余 tick（同步给本人客户端，供 HUD 显示）。 */
    public int scanCountdown = REVEAL_TICKS;
    /** 当前轮廓显示剩余 tick（同步给本人客户端）。 */
    public int revealTicks = 0;

    /** 诡域剩余 tick（仅服务端）。 */
    private int domainTicks = 0;
    private double domainX, domainY, domainZ;

    public GhostEyePlayerComponent(Player player) {
        this.player = player;
    }

    @Override public Player getPlayer() { return player; }
    @Override public boolean shouldSyncWith(ServerPlayer p) { return p == player; }
    public void sync() { KEY.sync(player); }

    @Override public void init() {
        scanCountdown = GameConstants.getInTicks(0, NoellesRolesConfig.HANDLER.instance().ghostEyeScanInterval);
        revealTicks = 0;
        domainTicks = 0;
        sync();
    }

    @Override public void clear() { init(); }

    // ==================== 主动·诡域入口 ====================

    /** 由 {@code AbilityHandler} 调用：在脚下展开诡域。成功返回 true。 */
    public boolean deployDomain() {
        if (!(player instanceof ServerPlayer sp) || !GameUtils.isPlayerAliveAndSurvival(player)) return false;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.GHOST_EYE)) return false;

        this.domainX = sp.getX();
        this.domainY = sp.getY();
        this.domainZ = sp.getZ();
        this.domainTicks = GameConstants.getInTicks(0, NoellesRolesConfig.HANDLER.instance().ghostEyeDomainDuration);
        sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.8f, 0.6f);
        return true;
    }

    // ==================== 每 tick 处理 ====================

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) return;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.GHOST_EYE)) return;
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return;

        // 被动·鬼眼：周期性扫描
        if (scanCountdown > 0) scanCountdown--;
        if (scanCountdown <= 0) {
            scanCountdown = GameConstants.getInTicks(0, NoellesRolesConfig.HANDLER.instance().ghostEyeScanInterval);
            revealTicks = REVEAL_TICKS;
            // 仅向杨间本人播放扫描提示音，避免周期性暴露自身位置
            sp.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 1.4f);
            sync();
        }
        if (revealTicks > 0) {
            revealTicks--;
            if (revealTicks == 0) {
                sync();
            }
        }
        // 每 20 tick（1 秒）同步一次，保证 HUD 倒计时平滑显示
        if (sp.serverLevel().getGameTime() % 20 == 0) {
            sync();
        }

        // 主动·诡域：持续施加领域效果
        if (domainTicks > 0) {
            domainTicks--;
            applyDomain(sp);
        }
    }

    private void applyDomain(ServerPlayer sp) {
        if (!(sp.level() instanceof ServerLevel level)) return;
        double radius = NoellesRolesConfig.HANDLER.instance().ghostEyeDomainRadius;
        double radiusSq = radius * radius;
        AABB box = new AABB(domainX - radius, domainY - radius, domainZ - radius,
                domainX + radius, domainY + radius, domainZ + radius);

        for (ServerPlayer target : level.getEntitiesOfClass(ServerPlayer.class, box,
                GameUtils::isPlayerAliveAndSurvival)) {
            if (target.distanceToSqr(domainX, domainY, domainZ) > radiusSq) continue;


            target.addEffect(new MobEffectInstance(ModEffects.VISION_FOG, EFFECT_REFRESH, 5, false, false, false));
            if (target == player) continue; // 杨间本人保留视野与透视
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EFFECT_REFRESH, 1, false, false, false));

            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, EFFECT_REFRESH, 0, false, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, EFFECT_REFRESH, 0, false, false, false));
            target.addEffect(new MobEffectInstance(ModEffects.VISION_FOG, EFFECT_REFRESH, 1, false, false, false));
            // 标记效果：使其无法开启杀手透视（InstinctMixin 拦截）
            target.addEffect(new MobEffectInstance(ModEffects.EERIE_DOMAIN, EFFECT_REFRESH, 0, false, false, false));
        }

        if (level.getGameTime() % 5 == 0) {
            level.sendParticles(ParticleTypes.SCULK_SOUL, domainX, domainY + 1.0, domainZ,
                    8, radius * 0.5, 1.0, radius * 0.5, 0.01);
        }
    }

    @Override
    public void clientTick() {
        if (revealTicks > 0) {
            revealTicks--;
            if (revealTicks == 0) {
                SREClient.cachedHighLightMap.clear();
            }
        }
        if (scanCountdown > 0) scanCountdown--;
    }

    // ==================== NBT ====================

    @Override public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider p) {
        tag.putInt("revealTicks", revealTicks);
        tag.putInt("scanCountdown", scanCountdown);
    }

    @Override public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider p) {
        revealTicks = tag.getInt("revealTicks");
        scanCountdown = tag.getInt("scanCountdown");
        // 立即刷新本能高亮缓存，使白色轮廓的出现/消失更跟手
        SREClient.cachedHighLightMap.clear();
    }

    @Override public void writeToNbt(CompoundTag tag, HolderLookup.Provider p) {}
    @Override public void readFromNbt(CompoundTag tag, HolderLookup.Provider p) {}
}
