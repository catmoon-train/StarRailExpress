package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.cca.WorldBlackoutComponent;
import io.wifi.starrailexpress.game.GameFunctions;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.role.ModRoles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import io.wifi.starrailexpress.api.RoleComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class NianShouPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<NianShouPlayerComponent> KEY = ModComponents.NIAN_SHOU;

    private final Player player;

    // 红包数量
    private int redPacketCount = 0;

    // 任务完成计数（每完成2个任务获得1个红包）
    private int tasksCompleted = 0;

    // 黑暗护盾试剂触发标志（一局游戏只能触发一次）
    private boolean darknessShieldTriggered = false;

    // 黑暗速度效果冷却（ticks）
    private int speedEffectCooldown = 0;

    // 黑暗状态标志
    private boolean inDarkness = false;

    // 恭喜发财播放状态
    private boolean gongXiFaCaiPlaying = false;

    // 恭喜发财播放计时
    private int gongXiFaCaiTimer = 0;

    public NianShouPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public void reset() {
        this.redPacketCount = 0;
        this.tasksCompleted = 0;
        this.darknessShieldTriggered = false;
        this.speedEffectCooldown = 0;
        this.inDarkness = false;
        this.gongXiFaCaiPlaying = false;
        this.gongXiFaCaiTimer = 0;
        ModComponents.NIAN_SHOU.sync(this.player);
    }

    @Override
    public void clear() {
        this.reset();
    }

    public int getRedPacketCount() {
        return redPacketCount;
    }

    public boolean isGongXiFaCaiPlaying() {
        return gongXiFaCaiPlaying;
    }

    public void addRedPacket() {
        this.redPacketCount++;
        ModComponents.NIAN_SHOU.sync(this.player);
    }

    public void useRedPacket() {
        if (redPacketCount > 0) {
            redPacketCount--;
            ModComponents.NIAN_SHOU.sync(this.player);
        }
    }

    public void onTaskCompleted() {
        this.tasksCompleted++;
        if (this.tasksCompleted >= 2) {
            this.tasksCompleted = 0;
            this.redPacketCount++;
            ModComponents.NIAN_SHOU.sync(this.player);
            if (player instanceof ServerPlayer sp) {
                sp.displayClientMessage(
                        Component.translatable("message.noellesroles.nianshou.red_packet_earned")
                                .withStyle(ChatFormatting.GOLD),
                        true);
            }
        }
        sync();
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        GameWorldComponent gameWorld = GameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.NIAN_SHOU))
            return;
        if (!GameFunctions.isPlayerAliveAndSurvival(serverPlayer))
            return;
        // 检查黑暗环境并触发护盾和速度
        checkDarkness();

        // 检查鞭炮数量
        checkFirecrackers();

        // 检查游戏时间，触发恭喜发财
        checkGongXiFaCai();

        // 处理恭喜发财播放计时
        if (gongXiFaCaiPlaying) {
            gongXiFaCaiTimer++;
            // 1分13秒 = 73秒 = 73 * 20 = 1460 ticks
            if (gongXiFaCaiTimer >= 1460) {
                // 停止播放
                gongXiFaCaiPlaying = false;
                gongXiFaCaiTimer = 0;
                ModComponents.NIAN_SHOU.sync(this.player);
            }
        }
    }

    public void sync() {
        ModComponents.NIAN_SHOU.sync(this.player);
    }

    private void checkDarkness() {
        // 检查是否在黑暗环境下（光照等级 <= 5）
        int lightLevel = player.level().getRawBrightness(player.blockPosition(),
                net.minecraft.world.level.LightLayer.BLOCK.ordinal());
        // Noellesroles.LOGGER.info("LightLevel:" + lightLevel);
        var blackOut = WorldBlackoutComponent.KEY.maybeGet(player.level()).orElse(null);
        if (lightLevel <= 5 || (blackOut != null && blackOut.isBlackoutActive())) {
            if (!inDarkness) {
                // 刚进入黑暗
                inDarkness = true;
                // Noellesroles.LOGGER.info("Trigger darkness");

                // 进入黑暗时，给予护盾试剂（一局一次）
                if (!darknessShieldTriggered) {

                    darknessShieldTriggered = true;
                    player.getInventory().add(new ItemStack(TMMItems.DEFENSE_VIAL));

                    if (player instanceof ServerPlayer sp) {
                        sp.displayClientMessage(
                                Component.translatable("message.noellesroles.nianshou.darkness_shield")
                                        .withStyle(ChatFormatting.GOLD),
                                true);
                    }
                    ModComponents.NIAN_SHOU.sync(this.player);
                }
            }

            // 在黑暗中持续给予速度二效果（每10秒刷新一次）
            if (speedEffectCooldown <= 0) {
                if (player instanceof ServerPlayer sp) {
                    // 给予速度二效果（10秒 = 200 ticks）
                    sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED,
                            220, // 11秒
                            1 // 速度等级2
                    ));
                    sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.NIGHT_VISION,
                            400, // 20秒
                            1 // 速度等级2
                    ));
                    sp.displayClientMessage(
                            Component.translatable("message.noellesroles.nianshou.darkness_speed")
                                    .withStyle(ChatFormatting.GOLD),
                            true);
                }
                // 重置冷却时间（10秒 = 200 ticks）
                speedEffectCooldown = 200;
            } else {
                speedEffectCooldown--;
            }
        } else {
            if (inDarkness) {
                // 离开黑暗环境
                player.removeEffect(MobEffects.NIGHT_VISION);
                player.removeEffect(MobEffects.MOVEMENT_SPEED);
                inDarkness = false;
                speedEffectCooldown = 0;
            }
        }
    }

    private void checkFirecrackers() {
        // 检查年兽周围x和z轴5格、y轴±1格内的鞭炮实体数量
        if (player.level() instanceof ServerLevel serverLevel) {
            int firecrackerCount = 0;
            for (var entity : serverLevel.getEntities(TMMEntities.FIRECRACKER, entity -> true)) {
                // x和z轴5格，y轴±1格
                double dx = entity.getX() - player.getX();
                double dz = entity.getZ() - player.getZ();
                double dy = entity.getY() - player.getY();
                if (dx * dx + dz * dz <= 25 && Math.abs(dy) <= 1) {
                    firecrackerCount++;
                }
            }

            // 计算所需的鞭炮数量：总人数的三分之二向上取整
            int totalPlayers = serverLevel.players().size();
            int requiredFirecrackers = (int) Math.ceil(totalPlayers * 2.0 / 3.0);

            // 如果鞭炮数量达到要求，年兽死亡（除岁成功）
            if (firecrackerCount >= requiredFirecrackers) {
                if (player instanceof ServerPlayer) {
                    // 发送死亡消息
                    for (Player p : player.level().players()) {
                        p.displayClientMessage(
                                Component
                                        .translatable("message.noellesroles.nianshou.death_by_firecrackers",
                                                player.getName())
                                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                                true);
                    }
                    // 杀死年兽（使用鞭炮死亡原因）
                    GameFunctions.killPlayer(player, true, null, Noellesroles.id("nianshou_firecrackers"));
                }
            }
        }
    }

    private void checkGongXiFaCai() {
        // 检查游戏时间是否剩余5分钟（300秒 = 6000 ticks）
        if (player.level() instanceof ServerLevel serverLevel) {
            // 获取游戏剩余时间
            int remainingTime = io.wifi.starrailexpress.cca.GameTimeComponent.KEY.get(serverLevel).getTime();

            // 剩余5分钟（300秒 = 6000 ticks）且未播放过
            // 只有在剩余时间刚变为6000 ticks时触发（避免重复触发）
            if (remainingTime == 6000 && !gongXiFaCaiPlaying) {
                // 检查年兽是否存活
                GameWorldComponent gameWorld = GameWorldComponent.KEY.get(serverLevel);
                boolean hasAliveNianShou = false;
                for (Player p : serverLevel.players()) {
                    if (gameWorld.isRole(p, ModRoles.NIAN_SHOU) && GameFunctions.isPlayerAliveAndSurvival(p)) {
                        hasAliveNianShou = true;
                        break;
                    }
                }

                if (hasAliveNianShou) {
                    // 播放恭喜发财
                    gongXiFaCaiPlaying = true;
                    gongXiFaCaiTimer = 0;

                    // 播放音乐

                    // 为所有存活玩家发放100金币并回满san值
                    for (Player p : serverLevel.players()) {
                        if (GameFunctions.isPlayerAliveAndSurvival(p)) {
                            if (p instanceof ServerPlayer sp) {

                                io.wifi.starrailexpress.cca.PlayerShopComponent shopComponent = io.wifi.starrailexpress.cca.PlayerShopComponent.KEY
                                        .get(p);
                                shopComponent.addToBalance(100);

                                // 回满san值
                                io.wifi.starrailexpress.cca.PlayerMoodComponent moodComponent = io.wifi.starrailexpress.cca.PlayerMoodComponent.KEY
                                        .get(p);
                                moodComponent.setMood(1f);

                                sp.displayClientMessage(
                                        Component.translatable("message.noellesroles.nianshou.gongxi_facai_reward")
                                                .withStyle(ChatFormatting.GOLD),
                                        true);
                                sp.playNotifySound(NRSounds.GONGXI_FACAI, SoundSource.AMBIENT,
                                        0.3F,
                                        1.0F);
                            }
                        }
                    }

                    // 年兽获胜检查会在游戏结算时处理
                    ModComponents.NIAN_SHOU.sync(this.player);
                }
            }
        }
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.redPacketCount = tag.getInt("redPacketCount");
        this.tasksCompleted = tag.getInt("tasksCompleted");
        this.darknessShieldTriggered = tag.getBoolean("darknessShieldTriggered");
        this.speedEffectCooldown = tag.getInt("speedEffectCooldown");
        this.inDarkness = tag.getBoolean("inDarkness");
        this.gongXiFaCaiPlaying = tag.getBoolean("gongXiFaCaiPlaying");
        this.gongXiFaCaiTimer = tag.getInt("gongXiFaCaiTimer");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("redPacketCount", redPacketCount);
        tag.putInt("tasksCompleted", tasksCompleted);
        tag.putBoolean("darknessShieldTriggered", darknessShieldTriggered);
        tag.putInt("speedEffectCooldown", speedEffectCooldown);
        tag.putBoolean("inDarkness", inDarkness);
        tag.putBoolean("gongXiFaCaiPlaying", gongXiFaCaiPlaying);
        tag.putInt("gongXiFaCaiTimer", gongXiFaCaiTimer);
    }

    @Override
    public Player getPlayer() {
        return player;
    }
}
