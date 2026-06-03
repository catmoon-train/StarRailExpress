package org.agmas.noellesroles.game.roles.special.super_loose_end;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.utils.Pair;
import org.agmas.noellesroles.utils.RoleUtils;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

public class SuperLooseEndPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static class Cooldown {
        public int cooldown;
        public Cooldown(int cooldown) {
            this.cooldown = cooldown;
        }
    }
    public interface SuperLooseEndAbility {
        void useAbility(Cooldown cooldown);
    }

    public static final ComponentKey<SuperLooseEndPlayerComponent> KEY = ModComponents.SUPER_LOOSE_END;
    /** 技能列表:冷却-技能 */
    public final List<Pair<Cooldown, SuperLooseEndAbility>> superLooseEndAbilities = new ArrayList<>();
    public Player player;

    /** 技能默认cd */
    public static final int DEFAULT_COOLDOWN = 20 * 10;
    /* =========传送技能========= */
    public static final int RECALL_COOLDOWN = 20 * 10;
    public static final int RECALL_COST = 1;
    public boolean placed = false;
    public double x = 0;
    public double y = 0;
    public double z = 0;
    /* =========爆炸技能========= */
    /* =========交换技能========= */
    /** 使用技能后的速度持续时间 */
    public static final int RESULT_SPEED_DURATION = 20 * 15;
    /** 使用技能后的速度等级 */
    public static final int RESULT_SPEED_LEVEL = 1;

    public int curAbilityIdx = -1;

    public SuperLooseEndPlayerComponent(Player player) {
        this.player = player;
        // 添加技能，只需要添加一次（初始化清空cd即可）
        // 添加爆炸技能，独立cd
        superLooseEndAbilities.add(new Pair<>(new Cooldown(0), defaultUseWithCooldown(() -> {
            int explodeLvl = getExplodeLvl();
            if (explodeLvl > 0) {
                Vec3 pos = player.position();
                double radius = getExplosionRange();
                // 伤害玩家
                for (Player target : player.level().players()) {
                    if (GameUtils.isPlayerEliminated(target))
                        continue;
                    if (target != player && target.distanceToSqr(pos) <= radius * radius) {
                        // 杀死玩家 : 杀死次数为爆炸等级
                        for (int i = 0; i < explodeLvl; ++i) {
                            // 玩家已被淘汰则停止击杀
                            if (GameUtils.isPlayerEliminated(target))
                                break;
                            GameUtils.killPlayer(target, true, player,
                                    io.wifi.starrailexpress.game.GameConstants.DeathReasons.GRENADE);
                        }
                    }
                }

                // 播放苦力怕爆炸声音
                player.level().playSound(null, pos.x, pos.y, pos.z,
                        SoundEvents.GENERIC_EXPLODE, SoundSource.MASTER, 4.0F, 1.0F);

                // 移除护盾，进入冷却
                SREArmorPlayerComponent armorPlayerComponent = SREArmorPlayerComponent.KEY.get(player);
                armorPlayerComponent.removeArmor(armorPlayerComponent.getArmor());
                return true;
            } else {
                player.displayClientMessage(Component.translatable("message.super_loose_end.not_enough_armor")
                        .withStyle(ChatFormatting.RED), true);
            }
            return false;
        }, DEFAULT_COOLDOWN
        )));
        // 添加传送能力：消耗护盾进行传送
        superLooseEndAbilities.add(new Pair<>(new Cooldown(0), cooldown -> {
            if (cooldown.cooldown > 0)
                return;

            SREArmorPlayerComponent armorPlayerComponent = SREArmorPlayerComponent.KEY.get(player);
            if (placed) {
                if (armorPlayerComponent.getArmor() >= RECALL_COST) {
                    armorPlayerComponent.removeArmor(RECALL_COST);
                    teleport();
                    cooldown.cooldown = RECALL_COOLDOWN;
                } else {
                    player.displayClientMessage(Component.translatable("message.super_loose_end.not_enough_armor")
                            .withStyle(ChatFormatting.RED), true);
                }
            }
            // 放置传送点后有1/10的cd
            else {
                setPosition();
                cooldown.cooldown = RECALL_COOLDOWN / 10;
            }
        }));
        // 添加随机互换技能：消耗速度buff等级和时间使所有玩家互换位置
        superLooseEndAbilities.add(new Pair<>(new Cooldown(0), defaultUseWithCooldown( () -> {
            // 消耗速度及等级和时间
            if (player.hasEffect(MobEffects.MOVEMENT_SPEED))
                player.removeEffect(MobEffects.MOVEMENT_SPEED);
            player.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SPEED,  // 速度效果
                    RESULT_SPEED_DURATION,      // 持续时间（tick）
                    RESULT_SPEED_LEVEL,         // 等级
                    false,                      // 是否显示粒子效果
                    false                       // 是否显示图标
            ));

            // 互换位置
            Level level = player.level();
            var allPlayers = level.players();
            List<Vec3> lastPlayerPositions = new ArrayList<>();
            List<Player> players = new ArrayList<>();
            // 记录所有未淘汰玩家
            for (Player p : allPlayers)
                if (!GameUtils.isPlayerEliminated(p))
                    players.add(p);

            for (Player p : players) {
                lastPlayerPositions.add(p.position());
            }
            Collections.shuffle(lastPlayerPositions);
            for (int i = 0; i < players.size(); ++i) {
                if (i < lastPlayerPositions.size()) {
                    players.get(i).teleportTo(
                            lastPlayerPositions.get(i).x,
                            lastPlayerPositions.get(i).y,
                            lastPlayerPositions.get(i).z
                    );
                }
            }
            return true;
        }, DEFAULT_COOLDOWN
        )));

    }

    /**
     * 使用默认冷却处理方式的经
     * @param ability 实际生效的技能
     * @param CD 技能冷却
     */
    protected static SuperLooseEndAbility defaultUseWithCooldown(BooleanSupplier ability, int CD) {
        return cooldown -> {
            if (cooldown.cooldown > 0)
                return;
            // 技能生效则将进入CD
            if (ability.getAsBoolean())
                cooldown.cooldown = CD;
        };
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        // 重置数据
        placed = false;
        x = 0;
        y = 0;
        z = 0;
        curAbilityIdx = 0;

        // 重置技能cd
        for (Pair<Cooldown, SuperLooseEndAbility> ability : superLooseEndAbilities) {
            ability.first.cooldown = 0;
        }

        sync();
    }

    @Override
    public void clear() {
        init();
        sync();
    }

    public Pair<Cooldown, SuperLooseEndAbility> getCurAbility() {
        if (curAbilityIdx < 0 || curAbilityIdx >= superLooseEndAbilities.size())
            return null;
        return superLooseEndAbilities.get(curAbilityIdx);
    }

    public void setPosition() {
        x = player.getX();
        y = player.getY();
        z = player.getZ();
        placed = true;
        this.sync();
    }

    public void teleport() {
        double fromX = player.getX();
        double fromY = player.getY();
        double fromZ = player.getZ();

        if (player.level() instanceof ServerLevel serverLevel) {
            ConfigWorldComponent.onPlayerUsedSkill((ServerPlayer) player);
            playTeleportEffects(serverLevel, fromX, fromY, fromZ);
        }

        player.teleportTo(x, y, z);

        if (player.level() instanceof ServerLevel serverLevel) {
            playTeleportEffects(serverLevel, x, y, z);
        }

        placed = false;
        this.sync();
    }

    private void playTeleportEffects(ServerLevel serverLevel, double centerX, double centerY, double centerZ) {
        double particleY = centerY + 0.9D;

        for (int i = 0; i < 16; i++) {
            double angle = Math.PI * 2D * i / 16D;
            double offsetX = Math.cos(angle) * 0.8D;
            double offsetZ = Math.sin(angle) * 0.8D;
            serverLevel.sendParticles(ParticleTypes.PORTAL,
                    centerX + offsetX, particleY, centerZ + offsetZ,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }

        serverLevel.sendParticles(ParticleTypes.PORTAL,
                centerX, particleY, centerZ,
                10, 0.25D, 0.35D, 0.25D, 0.05D);

        serverLevel.playSound(null, centerX, centerY, centerZ,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    public int getExplodeLvl() {
        SREArmorPlayerComponent armorPlayerComponent = SREArmorPlayerComponent.KEY.get(player);
        // 当护盾为3即可达到2级爆炸
        return (armorPlayerComponent.getArmor() + 1) / 2;
    }
    public double getExplosionRange() {
        SREArmorPlayerComponent armorPlayerComponent = SREArmorPlayerComponent.KEY.get(player);
        return (armorPlayerComponent.getArmor() > 3 ? (armorPlayerComponent.getArmor() - 3) * 0.5d : 0) + 2.5d;
    }

    public void useAbility(boolean isShiftPressed) {
        if (superLooseEndAbilities.isEmpty())
            return;
        if (isShiftPressed) {
            ++curAbilityIdx;
            curAbilityIdx %= superLooseEndAbilities.size();
        }
        else {
            if (curAbilityIdx >= 0 && curAbilityIdx < superLooseEndAbilities.size())
                // 使用技能时传入冷却时间以作具体判断和修改
                superLooseEndAbilities.get(curAbilityIdx).second.useAbility(superLooseEndAbilities.get(curAbilityIdx).first);
        }
        sync();
    }

    @Override
    public void serverTick() {
        if (!GameUtils.isGameRunning(player)) {
            return;
        }
        if (!RoleUtils.isPlayerTheJob(player, SpecialGameModeRoles.SUPER_LOOSE_END))
            return;
        // 服务端每 tick 减少冷却时间
        for (Pair<Cooldown, SuperLooseEndAbility> superLooseEndAbility : superLooseEndAbilities) {
            if (superLooseEndAbility.first.cooldown > 0) {
                --superLooseEndAbility.first.cooldown;
            }
        }
        // 10s -> sync
        if (this.player.level().getGameTime() % 200 == 0) {
            sync();
        }
    }

    @Override
    public void clientTick() {
        if (!GameUtils.isGameRunning(player)) {
            return;
        }
        if (!RoleUtils.isPlayerTheJob(player, SpecialGameModeRoles.SUPER_LOOSE_END))
            return;
        // 客户端也进行冷却计算（用于预测显示）
        for (Pair<Cooldown, SuperLooseEndAbility> superLooseEndAbility : superLooseEndAbilities) {
            if (superLooseEndAbility.first.cooldown > 0) {
                --superLooseEndAbility.first.cooldown;
            }
        }
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("ability_num", superLooseEndAbilities.size());
        for (int i = 0; i < superLooseEndAbilities.size(); ++i) {
            tag.putInt("cooldown" + i, this.superLooseEndAbilities.get(i).first.cooldown);
        }
        tag.putInt("cur_ability", this.curAbilityIdx);
        tag.putDouble("x", this.x);
        tag.putDouble("y", this.y);
        tag.putDouble("z", this.z);
        tag.putBoolean("placed", this.placed);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        int abilityNum = tag.contains("ability_num") ? tag.getInt("ability_num") : 0;
        for (int i = 0; i < abilityNum; ++i) {
            if (i < superLooseEndAbilities.size())
                superLooseEndAbilities.get(i).first.cooldown = tag.contains("cooldown" + i) ? tag.getInt("cooldown" + i) : 0;
        }
        curAbilityIdx = tag.contains("cur_ability") ? tag.getInt("cur_ability") : -1;
        this.x = tag.contains("x") ? tag.getDouble("x") : 0;
        this.y = tag.contains("y") ? tag.getDouble("y") : 0;
        this.z = tag.contains("z") ? tag.getDouble("z") : 0;
        this.placed = tag.contains("placed") && tag.getBoolean("placed");
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {

    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {

    }
}
