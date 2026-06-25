package org.agmas.noellesroles.game.roles.innocent.diviner;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.game.roles.killer.insane_killer.InsaneKillerPlayerComponent;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 占卜家（Diviner，乘客阵营）组件。
 *
 * <p>使用【晶球】右键对准一具尸体进行占卜，得知该死者的职业与名字；60 秒冷却，已占卜的尸体不可再次占卜。
 * 若占卜对象是【亡语杀手】伪装的尸体（即处于伪装状态的活体亡语杀手），视为其用刀刺死了自己（强制死亡）。
 */
public class DivinerPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<DivinerPlayerComponent> KEY = ModComponents.DIVINER;

    private final Player player;

    public int cooldown = 0;
    /** 已占卜过的尸体 UUID。 */
    private final Set<UUID> divinedCorpses = new HashSet<>();
    /** 是否已发放开局晶球。 */
    private boolean gaveItem = false;

    public DivinerPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) {
        return p == this.player;
    }

    @Override
    public void init() {
        this.cooldown = 0;
        this.divinedCorpses.clear();
        this.gaveItem = false;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    // ==================== 占卜 ====================

    /** 由【晶球】物品调用：对准尸体占卜。 */
    public void divine(ServerPlayer sp) {
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
        if (!gw.isRole(sp, ModRoles.DIVINER) || !GameUtils.isPlayerAliveAndSurvival(sp)) {
            return;
        }
        NoellesRolesConfig cfg = NoellesRolesConfig.HANDLER.instance();
        if (cooldown > 0) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.cooldown",
                    (cooldown + 19) / 20).withStyle(ChatFormatting.RED), true);
            return;
        }

        HitResult hr = ProjectileUtil.getHitResultOnViewVector(sp,
                e -> e instanceof PlayerBodyEntity || (e instanceof Player p && p != sp),
                cfg.divinerRange);
        if (!(hr instanceof EntityHitResult ehr)) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.no_corpse")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Entity target = ehr.getEntity();

        // 亡语杀手伪装的尸体：服务端看到的是处于伪装状态的活体玩家
        if (target instanceof ServerPlayer tp) {
            if (gw.isRole(tp, ModRoles.INSANE_KILLER) && InsaneKillerPlayerComponent.KEY.get(tp).isActive) {
                // 视为亡语杀手用刀刺死了自己（强制死亡，绕过濒死转化）
                GameUtils.killPlayer(tp, true, tp, GameConstants.DeathReasons.KNIFE, true);
                setCooldown(cfg);
                castFx(sp);
                sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.insane_killer")
                        .withStyle(ChatFormatting.DARK_RED), false);
                return;
            }
            // 活人不是占卜对象
            sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.not_corpse")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }

        if (target instanceof PlayerBodyEntity body) {
            UUID bodyId = body.getUUID();
            if (divinedCorpses.contains(bodyId)) {
                sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.already")
                        .withStyle(ChatFormatting.GRAY), true);
                return;
            }
            divinedCorpses.add(bodyId);
            setCooldown(cfg);

            ResourceLocation roleId = body.getComponent().playerRole;
            Component roleName = Component.translatable("announcement.star.role." + roleId.getPath());
            Component deadName = resolveName(sp, body);
            castFx(sp);
            sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.result", deadName, roleName)
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }
    }

    private Component resolveName(ServerPlayer sp, PlayerBodyEntity body) {
        UUID id = body.getPlayerUuid();
        if (id != null && sp.getServer() != null) {
            ServerPlayer dead = sp.getServer().getPlayerList().getPlayer(id);
            if (dead != null) {
                return dead.getDisplayName();
            }
        }
        if (body.getCustomName() != null) {
            return body.getCustomName();
        }
        return Component.translatable("message.noellesroles.diviner.unknown");
    }

    private void setCooldown(NoellesRolesConfig cfg) {
        this.cooldown = GameConstants.getInTicks(0, cfg.divinerCooldown);
        sync();
    }

    private void castFx(ServerPlayer sp) {
        if (sp.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.ENCHANT, sp.getX(), sp.getY() + 1.2, sp.getZ(),
                    24, 0.4, 0.6, 0.4, 0.2);
            sl.playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.4f);
        }
    }

    // ==================== tick ====================

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
        if (!gw.isRunning() || !gw.isRole(sp, ModRoles.DIVINER)) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            if (cooldown % 200 == 0 || cooldown == 0) {
                sync();
            }
        }
        if (!gaveItem && GameUtils.isPlayerAliveAndSurvival(sp)) {
            sp.addItem(ModItems.CRYSTAL_BALL.getDefaultInstance().copy());
            gaveItem = true;
        }
    }

    // ==================== NBT ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("cooldown", this.cooldown);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.cooldown = tag.getInt("cooldown");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
