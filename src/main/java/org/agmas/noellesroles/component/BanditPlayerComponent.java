package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.cca.StarPlayerShopComponent;
import org.agmas.noellesroles.role.ModRoles;
import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import io.wifi.starrailexpress.api.RoleComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 强盗玩家组件
 *
 * 被动技能：
 * - 杀人之后可以盗取被杀者一半的钱
 * - 被杀害的玩家会减少一半的钱
 */
public class BanditPlayerComponent implements RoleComponent, ServerTickingComponent {

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<BanditPlayerComponent> KEY = ModComponents.BANDIT;

    private final Player player;

    /**
     * 构造函数
     */
    public BanditPlayerComponent(@NotNull Player player) {
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
    public void reset() {
        this.sync();
    }

    @Override
    public void clear() {
        this.reset();
    }

    /**
     * 处理强盗击杀目标时的金钱盗取
     * 
     * @param victim 被杀的受害者
     */
    public void handleKilledVictim(Player victim) {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        if (!(victim instanceof ServerPlayer victimPlayer))
            return;

        StarGameWorldComponent gameWorld = StarGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.BANDIT))
            return;

        // 获取受害者的金钱
        StarPlayerShopComponent victimShop = StarPlayerShopComponent.KEY.get(victim);
        int victimBalance = victimShop.balance;

        if (victimBalance > 0) {
            // 盗取受害者一半的钱
            int stolenAmount = victimBalance / 2;
            
            // 减少受害者一半的钱
            victimShop.balance = victimBalance / 2;
            victimShop.sync();
            
            // 增加强盗的金钱
            StarPlayerShopComponent killerShop = StarPlayerShopComponent.KEY.get(player);
            killerShop.balance += stolenAmount;
            killerShop.sync();

            // 通知强盗
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.bandit.stole_money",
                            victim.getName().getString(),
                            stolenAmount)
                            .withStyle(ChatFormatting.GOLD),
                    true);

            // 通知受害者
            victimPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.bandit.lost_money",
                            stolenAmount)
                            .withStyle(ChatFormatting.RED),
                    true);
        }
    }

    /**
     * 同步到客户端
     */
    public void sync() {
        ModComponents.BANDIT.sync(this.player);
    }

    @Override
    public void serverTick() {
        // 强盗组件不需要每tick处理
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
        // 强盗组件目前不需要保存额外数据
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
        // 强盗组件目前不需要读取额外数据
    }
}
