package org.agmas.noellesroles.game.roles.killer.nostalgist;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeath;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;
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
        sync();
    }

    @Override
    public void clear() {
        this.inBackWorld = false;
        this.converted = false;
        this.aliveKillerCount = 0;
        if (player.hasEffect(MobEffects.INVISIBILITY)) {
            player.removeEffect(MobEffects.INVISIBILITY);
        }
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

        // 维持里世界：保持隐身
        if (!inBackWorld) {
            inBackWorld = true;
            sync();
        }
        MobEffectInstance invis = player.getEffect(MobEffects.INVISIBILITY);
        if (invis == null || invis.getDuration() <= 40) {
            // ambient=true，不显示粒子/图标
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, true, false, false));
        }
    }

    /** 里世界崩塌：移除隐身并切换为普通杀手。 */
    private void collapseBackWorld(ServerPlayer serverPlayer) {
        converted = true;
        inBackWorld = false;
        if (player.hasEffect(MobEffects.INVISIBILITY)) {
            player.removeEffect(MobEffects.INVISIBILITY);
        }
        sync();

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.nostalgist.collapse")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                false);

        RoleUtils.changeRole(player, TMMRoles.KILLER);
    }

    public static void registerEvents() {
        // 里世界中：怀旧者无法被有击杀者的死亡杀死，且其本人也无法击杀他人
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            // 怀旧者在里世界中无敌
            NostalgistPlayerComponent victimComp = KEY.maybeGet(victim).orElse(null);
            if (victimComp != null && victimComp.isActiveBackWorld()) {
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

        // 里世界中：怀旧者免疫无击杀者的死亡（如毒、坠落等）
        AllowPlayerDeath.EVENT.register((victim, deathReason) -> {
            NostalgistPlayerComponent victimComp = KEY.maybeGet(victim).orElse(null);
            if (victimComp != null && victimComp.isActiveBackWorld()) {
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
