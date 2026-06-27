package org.agmas.noellesroles.game.roles.killer.nostalgist;

import io.github.mortuusars.exposure.integration.Mods;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeath;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

/**
 * 怀旧者组件 —— 「里世界」状态机。
 *
 * <p>当场上存活的杀手不止一名时，怀旧者处于里世界：每 tick 维持隐身（{@link MobEffects#INVISIBILITY}），
 * 对所有阵营不可见/不可听/不可攻击（见 {@link #registerEvents()} 的死亡拦截，以及
 * {@code EntityMixin} 中对脚步声/疾跑粒子的抑制、{@code InvisiblePlayer} mixin 对模型的隐藏），
 * 视角灰白（客户端 {@code TimeStopShader} 检测 {@link #inBackWorld}）。身处里世界时无法击杀任何人。
 *
 * <p>当怀旧者成为场上唯一存活的杀手时，里世界崩塌：移除隐身并把职业切换为普通杀手
 * （{@link TMMRoles#KILLER}），此后可正常击杀。
 */
public class NostalgistPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<NostalgistPlayerComponent> KEY = ModComponents.NOSTALGIST;

    private final Player player;

    /** 是否处于里世界（同步给玩家自己，用于客户端灰白视角/HUD）。 */
    public boolean inBackWorld = false;

    /** 是否已现身为普通杀手（避免重复转换/重复提示）。 */
    public boolean converted = false;

    /** 同步用：当前存活杀手数（仅展示给自己）。 */
    public int aliveKillerCount = 0;

    /** 里世界被动收入计时器（服务端），每达到间隔发放一次金币。 */
    private int backWorldIncomeTimer = 0;

    /** 主动退出里世界的前摇计时（服务端，>0 表示正在退出，倒数至 0 时真正崩塌）。 */
    private int collapseWindup = 0;

    public NostalgistPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer target) {
        return this.player == target;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void init() {
        this.inBackWorld = true;
        this.converted = false;
        this.aliveKillerCount = 0;
        this.backWorldIncomeTimer = 0;
        this.collapseWindup = 0;
        sync();
    }

    @Override
    public void clear() {
        this.inBackWorld = false;
        this.converted = false;
        this.aliveKillerCount = 0;
        this.backWorldIncomeTimer = 0;
        this.collapseWindup = 0;
        removeBackWorldEffects();
        sync();
    }

    /** 怀旧者当前是否处于活跃的里世界状态（仍是怀旧者职业 + 标记为里世界）。 */
    public boolean isActiveBackWorld() {
        SREGameWorldComponent gwc = SREGameWorldComponent.KEY.get(player.level());
        if (gwc == null || !gwc.isRole(player, ModRoles.NOSTALGIST)) {
            return false;
        }
        return inBackWorld && !converted;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        SREGameWorldComponent gwc = SREGameWorldComponent.KEY.get(player.level());
        if (gwc == null || !gwc.isRunning()) {
            return;
        }
        if (!gwc.isRole(player, ModRoles.NOSTALGIST)) {
            return;
        }
        if (converted) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
            return;
        }

        // 统计存活杀手数量（getAllKillerPlayers 已排除中立，且包含怀旧者自己）
        ServerLevel level = serverPlayer.serverLevel();
        int alive = 0;
        for (UUID uuid : gwc.getAllKillerPlayers()) {
            Player killer = level.getPlayerByUUID(uuid);
            if (killer != null && GameUtils.isPlayerAliveAndSurvival(killer)) {
                alive++;
            }
        }
        if (alive != aliveKillerCount) {
            aliveKillerCount = alive;
            sync();
        }

        // 仅剩怀旧者一名杀手 -> 里世界崩塌，现身为普通杀手
        if (alive <= 1) {
            collapseBackWorld(serverPlayer);
            return;
        }

        // 维持里世界：隐身 + 禁止说话/使用物品 + 灰白滤镜标记
        if (!inBackWorld) {
            inBackWorld = true;
            sync();
        }
        applyBackWorldEffects();

        // 主动退出里世界的前摇：倒数期间持续粒子，归零时崩塌现身
        if (collapseWindup > 0) {
            spawnWindupParticles(serverPlayer);
            if (--collapseWindup <= 0) {
                collapseBackWorld(serverPlayer);
                return;
            }
        }

        // 里世界被动收入：每隔配置的间隔发放金币
        int interval = GameConstants.getInTicks(0,
                NoellesRolesConfig.HANDLER.instance().nostalgistBackWorldIncomeInterval);
        if (++backWorldIncomeTimer >= interval) {
            backWorldIncomeTimer = 0;
            int amount = NoellesRolesConfig.HANDLER.instance().nostalgistBackWorldIncomeAmount;
            SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
            shop.balance += amount;
            shop.sync();
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.nostalgist.income", amount)
                            .withStyle(ChatFormatting.GOLD),
                    true);
        }
    }

    /**
     * 主动从里世界崩塌：玩家在里世界中按下技能键时调用。
     * 仅在仍处于活跃里世界时生效；触发后进入 1.5 秒前摇（伴随音效与粒子），前摇结束才真正现身。
     */
    public void tryManualCollapse(ServerPlayer serverPlayer) {
        if (!isActiveBackWorld()) {
            return;
        }
        if (collapseWindup > 0) {
            return; // 已在退出过程中
        }
        startCollapseWindup(serverPlayer);
    }

    /** 开始主动退出里世界的前摇：起手音效 + 提示，倒数在 {@link #serverTick()} 中推进。 */
    private void startCollapseWindup(ServerPlayer serverPlayer) {
        collapseWindup = Math.max(1, NoellesRolesConfig.HANDLER.instance().nostalgistCollapseWindupTicks);
        ServerLevel level = serverPlayer.serverLevel();
        level.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.8f, 0.6f);
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.nostalgist.collapsing")
                        .withStyle(ChatFormatting.GRAY),
                true);
    }

    /** 前摇期间每 tick 在玩家周身散发粒子。 */
    private void spawnWindupParticles(ServerPlayer serverPlayer) {
        ServerLevel level = serverPlayer.serverLevel();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                serverPlayer.getX(), serverPlayer.getY() + 1.0, serverPlayer.getZ(),
                6, 0.4, 0.7, 0.4, 0.03);
    }

    /** 里世界崩塌：移除里世界相关效果、播放完成音效/粒子并切换为普通杀手。 */
    private void collapseBackWorld(ServerPlayer serverPlayer) {
        converted = true;
        inBackWorld = false;
        backWorldIncomeTimer = 0;
        collapseWindup = 0;
        removeBackWorldEffects();

        // 退出里世界的完成音效与粒子爆发
        ServerLevel level = serverPlayer.serverLevel();
        level.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.7f, 1.0f);
        level.sendParticles(ParticleTypes.PORTAL,
                serverPlayer.getX(), serverPlayer.getY() + 1.0, serverPlayer.getZ(),
                40, 0.5, 1.0, 0.5, 0.4);

        player.removeEffect(ModEffects.NO_COLLIDE;
        // 离开里世界奖励金币
        int reward = NoellesRolesConfig.HANDLER.instance().nostalgistCollapseReward;
        if (reward > 0) {
            SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
            shop.balance += reward;
            shop.sync();
        }
        sync();

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.nostalgist.collapse", reward)
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                false);

        RoleUtils.changeRole(player, TMMRoles.KILLER);
    }

    /**
     * 维持里世界的全部药水效果：隐身、灰白滤镜标记、禁止使用物品、禁止文字/语音说话。
     * 全部以 ambient=true、不显示粒子/图标的方式施加，并在剩余时长偏低时统一续期。
     */
    private void applyBackWorldEffects() {
        MobEffectInstance marker = player.getEffect(ModEffects.NOSTALGIST_BACKWORLD);
        if (marker != null && marker.getDuration() > 40) {
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, true, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.NOSTALGIST_BACKWORLD, 60, 0, true, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.USED_BANED, 60, 0, true, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.CHAT_BAN, 60, 0, true, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.VOICE_SILENCE, 60, 0, true, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.FOOTSTEP_VANISH, 60, 0, true, false, false));
    }

    /** 移除里世界相关的全部药水效果（现身或游戏结束时调用）。 */
    private void removeBackWorldEffects() {
        if (player.hasEffect(MobEffects.INVISIBILITY)) {
            player.removeEffect(MobEffects.INVISIBILITY);
        }
        if (player.hasEffect(ModEffects.NOSTALGIST_BACKWORLD)) {
            player.removeEffect(ModEffects.NOSTALGIST_BACKWORLD);
        }
        if (player.hasEffect(ModEffects.USED_BANED)) {
            player.removeEffect(ModEffects.USED_BANED);
        }
        if (player.hasEffect(ModEffects.CHAT_BAN)) {
            player.removeEffect(ModEffects.CHAT_BAN);
        }
        if (player.hasEffect(ModEffects.VOICE_SILENCE)) {
            player.removeEffect(ModEffects.VOICE_SILENCE);
        }
        if (player.hasEffect(ModEffects.FOOTSTEP_VANISH)) {
            player.removeEffect(ModEffects.FOOTSTEP_VANISH);
        }
    }

    public static void registerEvents() {
        // 里世界中：怀旧者无法被有击杀者的死亡杀死，且其本人也无法击杀他人
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            // 怀旧者在里世界中无敌（但掉出列车的环境即死除外，否则会卡在列车下永不死亡）
            NostalgistPlayerComponent victimComp = KEY.maybeGet(victim).orElse(null);
            if (victimComp != null && victimComp.isActiveBackWorld()
                    && !deathReason.equals(GameConstants.DeathReasons.FELL_OUT_OF_TRAIN)) {
                return false;
            }
            // 怀旧者在里世界中无法击杀（幽灵观察者）
            if (killer != null) {
                NostalgistPlayerComponent killerComp = KEY.maybeGet(killer).orElse(null);
                if (killerComp != null && killerComp.isActiveBackWorld()) {
                    return false;
                }
            }
            return true;
        });

        // 里世界中：怀旧者免疫无击杀者的死亡（如毒等），但掉出列车的环境即死除外
        AllowPlayerDeath.EVENT.register((victim, deathReason) -> {
            NostalgistPlayerComponent victimComp = KEY.maybeGet(victim).orElse(null);
            if (victimComp != null && victimComp.isActiveBackWorld()
                    && !deathReason.equals(GameConstants.DeathReasons.FELL_OUT_OF_TRAIN)) {
                return false;
            }
            return true;
        });
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("inBackWorld", inBackWorld);
        tag.putBoolean("converted", converted);
        tag.putInt("aliveKillerCount", aliveKillerCount);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        inBackWorld = tag.getBoolean("inBackWorld");
        converted = tag.getBoolean("converted");
        aliveKillerCount = tag.getInt("aliveKillerCount");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
