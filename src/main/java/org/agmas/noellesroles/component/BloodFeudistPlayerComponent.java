package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.CommonTickingComponent;
import io.wifi.starrailexpress.api.RoleComponent;

/**
 * 仇杀客职业组件
 *
 * 技能说明：
 * - 无限体力
 * - 假心情
 * - 每有一个人因误杀平民而亡，获得100金币
 * - 1人误杀：永久速度1药水效果
 * - 2人误杀：永久急迫2药水效果
 * - 3人误杀：立刻获得150金币
 * - 4人误杀：永久速度2药水效果
 * - 5人误杀：免疫缓慢、挖掘疲劳、反胃、失明、黑暗、霉运、发光
 * - 疯狂模式购买CD只有30秒
 * - 只能购买撬棍、开锁器和疯狂模式
 */
public class BloodFeudistPlayerComponent implements RoleComponent, CommonTickingComponent {

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<BloodFeudistPlayerComponent> KEY = ModComponents.BLOOD_FEUDIST;

    @Override
    public Player getPlayer() {
        return player;
    }

    private final Player player;

    // 误杀计数
    private int accidentalKillCount = 0;

    // 是否已获得各阶段的奖励
    private boolean gotSpeed1 = false;
    private boolean gotHaste2 = false;
    private boolean gotExtra150 = false;
    private boolean gotSpeed2 = false;
    private boolean gotImmunity = false;

    // 药水效果开关状态（默认关闭）
    private boolean speedEnabled = false;
    private boolean hasteEnabled = false;

    public BloodFeudistPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public void init() {
        this.accidentalKillCount = 0;
        this.gotSpeed1 = false;
        this.gotHaste2 = false;
        this.gotExtra150 = false;
        this.gotSpeed2 = false;
        this.gotImmunity = false;
        this.speedEnabled = false;
        this.hasteEnabled = false;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    /**
     * 同步到客户端
     */
    public void sync() {
        ModComponents.BLOOD_FEUDIST.sync(this.player);
    }

    @Override
    public void tick() {
        if (player.level().isClientSide) {
            return;
        }
        var gwc = SREGameWorldComponent.KEY.get(this.player.level());
        if (!gwc.isRole(player, ModRoles.BLOOD_FEUDIST))
            return;
        // 根据开关状态应用药水效果
        if (gotSpeed2 && speedEnabled) {
            if (!player.hasEffect(MobEffects.MOVEMENT_SPEED)
                    || player.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() != 1) {
                player.addEffect(
                        new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Integer.MAX_VALUE, 1, false, false, true));
            }
        } else if (gotSpeed1 && speedEnabled) {
            if (!player.hasEffect(MobEffects.MOVEMENT_SPEED)
                    || player.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() != 0) {
                player.addEffect(
                        new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Integer.MAX_VALUE, 0, false, false, true));
            }
        } else {
            // 如果开关关闭，移除速度效果
            if (player.hasEffect(MobEffects.MOVEMENT_SPEED)) {
                player.removeEffect(MobEffects.MOVEMENT_SPEED);
            }
        }

        if (gotHaste2 && hasteEnabled) {
            if (!player.hasEffect(MobEffects.DIG_SPEED) || player.getEffect(MobEffects.DIG_SPEED).getAmplifier() != 1) {
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, Integer.MAX_VALUE, 1, false, false, true));
            }
        } else {
            // 如果开关关闭，移除急迫效果
            if (player.hasEffect(MobEffects.DIG_SPEED)) {
                player.removeEffect(MobEffects.DIG_SPEED);
            }
        }

        // 如果已获得免疫，清理负面效果
        if (gotImmunity) {
            clearNegativeEffects();
        }
    }

    /**
     * 清除负面效果
     */
    private void clearNegativeEffects() {
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN); // 缓慢
        player.removeEffect(MobEffects.DIG_SLOWDOWN); // 挖掘疲劳
        player.removeEffect(MobEffects.CONFUSION); // 反胃
        player.removeEffect(MobEffects.BLINDNESS); // 失明
        player.removeEffect(MobEffects.DARKNESS); // 黑暗
        player.removeEffect(MobEffects.UNLUCK); // 霉运
        player.removeEffect(MobEffects.GLOWING); // 发光
    }

    /**
     * 当发生误杀时调用
     */
    public void onAccidentalKill() {
        accidentalKillCount++;

        // 每次误杀都给100金币
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
        shop.addToBalance(100);

        if (player instanceof ServerPlayer sp) {
            sp.sendSystemMessage(Component.translatable("message.noellesroles.blood_feudist.accidental_kill_bonus", 100)
                    .withStyle(net.minecraft.ChatFormatting.GOLD));
        }

        applyRewards();
        sync();
    }

    /**
     * 应用奖励
     */
    private void applyRewards() {
        // 1人误杀：永久速度1
        if (accidentalKillCount >= 1 && !gotSpeed1) {
            gotSpeed1 = true;
            player.addEffect(
                    new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Integer.MAX_VALUE, 0, false, false, true));
            if (player instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.translatable("message.noellesroles.blood_feudist.speed1")
                        .withStyle(net.minecraft.ChatFormatting.GREEN));
            }
        }

        // 2人误杀：永久急迫2（阶段2）
        if (accidentalKillCount >= 2 && !gotHaste2) {
            gotHaste2 = true;
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, Integer.MAX_VALUE, 1, false, false, true));
            // 播放重生锚充能声音 - 全场播放
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.MASTER, 3.0F, 1.0F);
            if (player instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.translatable("message.noellesroles.blood_feudist.haste2")
                        .withStyle(net.minecraft.ChatFormatting.GREEN));
            }
        }

        // 3人误杀：额外150金币
        if (accidentalKillCount >= 3 && !gotExtra150) {
            gotExtra150 = true;
            SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
            shop.addToBalance(150);
            if (player instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.translatable("message.noellesroles.blood_feudist.extra150")
                        .withStyle(net.minecraft.ChatFormatting.GOLD));
            }
        }

        // 4人误杀：永久速度2（替换速度1）（阶段4）
        if (accidentalKillCount >= 4 && !gotSpeed2) {
            gotSpeed2 = true;
            player.removeEffect(MobEffects.MOVEMENT_SPEED);
            player.addEffect(
                    new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Integer.MAX_VALUE, 1, false, false, true));
            // 播放重生锚能量被消耗声音 - 全场播放
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.MASTER, 3.0F, 1.0F);
            if (player instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.translatable("message.noellesroles.blood_feudist.speed2")
                        .withStyle(net.minecraft.ChatFormatting.GREEN));
            }
        }

        // 5人误杀：免疫负面效果（阶段5）
        if (accidentalKillCount >= 5 && !gotImmunity) {
            gotImmunity = true;
            clearNegativeEffects();
            // 播放重生锚设置重生点声音 - 全场播放
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.MASTER, 3.0F, 1.0F);
            if (player instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.translatable("message.noellesroles.blood_feudist.immunity")
                        .withStyle(net.minecraft.ChatFormatting.GREEN));
            }
        }
    }

    /**
     * 注册事件
     */
    public static void registerEvents() {
        ModdedRoleAssigned.EVENT.register((player, role) -> {
            if (role.identifier().equals(ModRoles.BLOOD_FEUDIST_ID)) {
                // 初始化时确保有负面效果免疫
                BloodFeudistPlayerComponent comp = org.agmas.noellesroles.component.ModComponents.BLOOD_FEUDIST
                        .get(player);
                if (comp.gotImmunity) {
                    comp.clearNegativeEffects();
                }
            }
        });
    }

    // Getter 方法
    public int getAccidentalKillCount() {
        return accidentalKillCount;
    }

    public boolean hasSpeed1() {
        return gotSpeed1;
    }

    public boolean hasHaste2() {
        return gotHaste2;
    }

    public boolean hasSpeed2() {
        return gotSpeed2;
    }

    public boolean hasImmunity() {
        return gotImmunity;
    }

    /**
     * 技能：切换所有药水效果开关
     */
    public void toggleEffects() {
        if (!gotSpeed1 && !gotSpeed2 && !gotHaste2) {
            if (player instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.translatable("message.noellesroles.blood_feudist.no_effects")
                        .withStyle(net.minecraft.ChatFormatting.RED));
            }
            return;
        }

        // 检查当前是否有任何效果开启
        boolean anyEnabled = false;
        if ((gotSpeed1 || gotSpeed2) && speedEnabled) {
            anyEnabled = true;
        }
        if (gotHaste2 && hasteEnabled) {
            anyEnabled = true;
        }

        // 如果有任何一个效果开启，就全部关闭；否则全部打开
        boolean newState = !anyEnabled;

        if (gotSpeed1 || gotSpeed2) {
            speedEnabled = newState;
        }
        if (gotHaste2) {
            hasteEnabled = newState;
        }

        sync();

        if (player instanceof ServerPlayer sp) {
            if (newState) {
                sp.sendSystemMessage(Component.translatable("message.noellesroles.blood_feudist.effects_enabled")
                        .withStyle(net.minecraft.ChatFormatting.GREEN));
            } else {
                sp.sendSystemMessage(Component.translatable("message.noellesroles.blood_feudist.effects_disabled")
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        }
    }

    // Getter 方法用于客户端访问
    public boolean isSpeedEnabled() {
        return speedEnabled;
    }

    public boolean isHasteEnabled() {
        return hasteEnabled;
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
        tag.putInt("accidentalKillCount", this.accidentalKillCount);
        tag.putBoolean("gotSpeed1", this.gotSpeed1);
        tag.putBoolean("gotHaste2", this.gotHaste2);
        tag.putBoolean("gotExtra150", this.gotExtra150);
        tag.putBoolean("gotSpeed2", this.gotSpeed2);
        tag.putBoolean("gotImmunity", this.gotImmunity);
        tag.putBoolean("speedEnabled", this.speedEnabled);
        tag.putBoolean("hasteEnabled", this.hasteEnabled);
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
        this.accidentalKillCount = tag.contains("accidentalKillCount") ? tag.getInt("accidentalKillCount") : 0;
        this.gotSpeed1 = tag.contains("gotSpeed1") && tag.getBoolean("gotSpeed1");
        this.gotHaste2 = tag.contains("gotHaste2") && tag.getBoolean("gotHaste2");
        this.gotExtra150 = tag.contains("gotExtra150") && tag.getBoolean("gotExtra150");
        this.gotSpeed2 = tag.contains("gotSpeed2") && tag.getBoolean("gotSpeed2");
        this.gotImmunity = tag.contains("gotImmunity") && tag.getBoolean("gotImmunity");
        this.speedEnabled = tag.contains("speedEnabled") && tag.getBoolean("speedEnabled");
        this.hasteEnabled = tag.contains("hasteEnabled") && tag.getBoolean("hasteEnabled");
    }
}
