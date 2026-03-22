package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 钟表匠组件
 *
 * 管理"时间削减"技能：
 * - 能看到游戏时间
 * - 仅在12人以上对局出现
 * - 按下技能键花费125金币，减少游戏时间45秒
 * - 世界时间加快2000tick
 * - 游戏时间最多减少至1分30秒
 */
public class ClockmakerPlayerComponent implements RoleComponent, ServerTickingComponent {

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<ClockmakerPlayerComponent> KEY = ModComponents.CLOCKMAKER;

    // ==================== 常量定义 ====================

    /** 技能消耗金币数 */
    public static final int SKILL_COST = 125;

    /** 每次减少的局内游戏时间（45秒 = 900 tick） */
    public static final int TIME_REDUCTION_TICKS = 900;

    /** 世界时间加快的tick数（2000 tick = 100秒） */
    public static final int WORLD_TIME_BOOST_TICKS = 2000;

    /** 游戏时间最小值（1分30秒 = 90秒 = 1800 tick） */
    public static final int MIN_GAME_TIME_TICKS = 1800;

    // ==================== 状态变量 ====================

    private final Player player;

    /** 是否正在使用技能 */
    public boolean isUsingSkill = false;

    /**
     * 构造函数
     */
    public ClockmakerPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    /**
     * 重置组件状态
     * 在游戏开始时或角色分配时调用
     */
    @Override
    public void init() {
        this.isUsingSkill = false;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    /**
     * 使用技能：削减时间
     * @return 是否成功使用
     */
    public boolean useSkill() {
        if (!(player instanceof ServerPlayer)) {
            return false;
        }

        // 验证是钟表匠
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, org.agmas.noellesroles.role.ModRoles.CLOCKMAKER)) {
            return false;
        }

        // 检查是否正在使用技能
        if (isUsingSkill) {
            player.displayClientMessage(Component.translatable("message.noellesroles.clockmaker.already_using").withStyle(ChatFormatting.RED), true);
            return true;
        }

        // 检查游戏时间是否已到最小值
        SREGameTimeComponent gameTime = SREGameTimeComponent.KEY.get(player.level());
        long currentTime = gameTime.getTime();
        if (currentTime <= MIN_GAME_TIME_TICKS) {
            player.displayClientMessage(Component.translatable("message.noellesroles.clockmaker.min_time_reached").withStyle(ChatFormatting.RED), true);
            return true;
        }

        // 检查金币
        SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(player);
        if (shopComponent.balance < SKILL_COST) {
            player.displayClientMessage(Component.translatable("message.noellesroles.clockmaker.insufficient_funds", SKILL_COST).withStyle(ChatFormatting.RED), true);
            return true;
        }

        // 扣除金币
        shopComponent.balance -= SKILL_COST;
        shopComponent.sync();

        // 执行时间削减
        executeTimeReduction();

        this.isUsingSkill = true;

        // 1秒后重置状态（允许连续使用）
        player.getServer().tell(new net.minecraft.server.TickTask(player.getServer().getTickCount() + 20, () -> {
            isUsingSkill = false;
            sync();
        }));

        this.sync();
        return true;
    }

    /**
     * 执行时间削减逻辑
     */
    private void executeTimeReduction() {
        // 获取当前游戏时间
        Level level = player.level();

        SREGameTimeComponent gameTime = SREGameTimeComponent.KEY.get(level);
        long currentTime = gameTime.getTime();

        // 计算新的游戏时间
        long newTime = Math.max(MIN_GAME_TIME_TICKS, currentTime - TIME_REDUCTION_TICKS);

        // 设置新的游戏时间
        gameTime.setTime((int) newTime);
//        level.getServer().tickRateManager().requestGameToSprint((int) (currentTime-newTime));
        // 加快世界时间（Minecraft原版时间）
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            long currentDayTime = serverLevel.getDayTime();
            serverLevel.setDayTime(currentDayTime + WORLD_TIME_BOOST_TICKS);
        }

        // 播放音效
        level.playSound(null, player.blockPosition(),
                SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0F, 1.5F);

        // 发送成功消息
        if (player instanceof ServerPlayer serverPlayer) {
            long reducedSeconds = TIME_REDUCTION_TICKS / 20;
            long newTimeSeconds = newTime / 20;
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.clockmaker.time_reduced",
                            reducedSeconds, newTimeSeconds / 60, newTimeSeconds % 60)
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    true);
        }

        this.sync();
    }

    /**
     * 获取当前游戏时间（秒）
     */
    public long getCurrentGameTimeSeconds() {
        SREGameTimeComponent gameTime = SREGameTimeComponent.KEY.get(player.level());
        return gameTime.getTime() / 20;
    }

    /**
     * 检查技能是否可以使用
     */
    public boolean canUseSkill() {
        if (isUsingSkill) {
            return false;
        }

        // 检查游戏时间是否已到最小值
        SREGameTimeComponent gameTime = SREGameTimeComponent.KEY.get(player.level());
        if (gameTime.getTime() <= MIN_GAME_TIME_TICKS) {
            return false;
        }

        // 检查金币
        SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(player);
        return shopComponent.balance >= SKILL_COST;
    }

    /**
     * 同步到客户端
     */
    public void sync() {
        ModComponents.CLOCKMAKER.sync(this.player);
    }

    @Override
    public void serverTick() {
        // 验证是钟表匠
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, org.agmas.noellesroles.role.ModRoles.CLOCKMAKER)) {
            return;
        }

        // Tick处理目前为空，技能是即时触发的
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("isUsingSkill", this.isUsingSkill);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.isUsingSkill = tag.contains("isUsingSkill") && tag.getBoolean("isUsingSkill");
    }
    
    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
