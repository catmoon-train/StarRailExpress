package org.agmas.noellesroles.modifier.expedition;

import io.wifi.starrailexpress.api.Role;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import io.wifi.starrailexpress.game.GameFunctions;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;
import io.wifi.starrailexpress.index.TMMSounds;
import org.agmas.noellesroles.Noellesroles;

/**
 * 远征队修饰符组件
 *
 * 当场上还剩下2名好人时，将角色的职业切换为红海军
 */
public class ExpeditionComponent implements RoleComponent, ServerTickingComponent {

    /** 组件键 */
    public static final ComponentKey<ExpeditionComponent> KEY = ComponentRegistry.getOrCreate(
            Noellesroles.id("expedition"), ExpeditionComponent.class);

    private final Player player;

    /** 是否已激活角色切换 */
    public boolean roleSwitched = false;

    /** 检查间隔计时器 */
    public int checkTimer = 0;

    @Override
    public Player getPlayer() {
        return player;
    }

    public ExpeditionComponent(Player player) {
        this.player = player;
    }

    @Override
    public void reset() {
        this.roleSwitched = false;
        this.checkTimer = 0;
        this.sync();
    }

    @Override
    public void clear() {
        clearAll();
    }

    public void clearAll() {
        this.roleSwitched = false;
        this.checkTimer = 0;
        this.sync();
    }

    /**
     * 检查是否是远征队修饰符持有者
     */
    public boolean isExpedition() {
        return true; // 只要有这个组件就是远征队
    }

    /**
     * 检查场上存活的平民（好人和警长阵营）数量
     */
    public int countAliveGoodPlayers() {
        if (player.level().isClientSide())
            return 0;

        StarGameWorldComponent gameWorld = StarGameWorldComponent.KEY.get(player.level());
        ServerLevel serverWorld = (ServerLevel) player.level();

        int count = 0;
        for (Player p : serverWorld.players()) {
            if (!GameFunctions.isPlayerAliveAndSurvival(p))
                continue;

            Role role = gameWorld.getRole(p);
            if (role == null)
                continue;

            // 检查是否是好人（包括乘客阵营和警长阵营）
            // isInnocent = true 的角色（包括普通平民和警长阵营）
            if (role.isInnocent()) {
                count++;
            }
        }

        return count;
    }

    /**
     * 激活角色切换
     */
    public void activateRoleSwitch() {
        if (roleSwitched)
            return;
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        // 检查玩家是否真的有远征队修饰符
        if (!hasExpeditionModifier())
            return;

        // 检查玩家是否是好人阵营（额外保险）
        if (!isGoodFaction())
            return;

        // 切换角色为红海军
        StarGameWorldComponent gameWorld = StarGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.GHOST)) { // 确保不是小透明
            RoleUtils.changeRole(player, ModRoles.BETTER_VIGILANTE);

            // 播放音效
            player.level().playSound(null, player.blockPosition(),
                    TMMSounds.ITEM_PSYCHO_ARMOUR,
                    SoundSource.MASTER, 2.0F, 0.8F);

            // 发送消息
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.expedition.role_switched")
                            .withStyle(style -> style.withColor(0x00FFFF).withBold(true)),
                    true);

            // 显示登车欢迎消息
            RoleUtils.sendWelcomeAnnouncement(serverPlayer);
        }

        roleSwitched = true;
        this.sync();
    }

    /**
     * 检查玩家是否有远征队修饰符
     */
    private boolean hasExpeditionModifier() {
        try {
            var worldModifierComponent = org.agmas.harpymodloader.component.WorldModifierComponent.KEY
                    .get(player.level());
            return worldModifierComponent != null && worldModifierComponent.isModifier(player,
                    org.agmas.noellesroles.modifier.NRModifiers.EXPEDITION);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查玩家是否是好人阵营（乘客阵营或警长阵营）
     */
    private boolean isGoodFaction() {
        try {
            StarGameWorldComponent gameWorld = StarGameWorldComponent.KEY.get(player.level());
            Role role = gameWorld.getRole(player);
            return role != null && role.isInnocent() && !role.canUseKiller() && !role.isNeutrals();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 同步到客户端
     */
    public void sync() {
        KEY.sync(this.player);
    }

    // ==================== Tick 处理 ====================

    @Override
    public void serverTick() {
        // 检查玩家是否存活
        if (!GameFunctions.isPlayerAliveAndSurvival(player))
            return;

        // 如果已经切换过角色，不需要再检测
        if (roleSwitched)
            return;

        // 检查玩家是否有远征队修饰符
        if (!hasExpeditionModifier())
            return;

        // 检查玩家是否是好人阵营（额外保险）
        if (!isGoodFaction())
            return;

        StarGameWorldComponent gameWorld = StarGameWorldComponent.KEY.get(player.level());

        // 只有游戏进行中才检测
        if (!gameWorld.isRunning())
            return;

        // 每秒检查一次（20 tick）
        ++checkTimer;
        if (checkTimer % 20 == 0) {
            int aliveGoodCount = countAliveGoodPlayers();
            if (aliveGoodCount <= 2) {
                activateRoleSwitch();
            }
        }
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("roleSwitched", this.roleSwitched);
        tag.putInt("checkTimer", this.checkTimer);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.roleSwitched = tag.contains("roleSwitched") && tag.getBoolean("roleSwitched");
        this.checkTimer = tag.getInt("checkTimer");
    }
}
