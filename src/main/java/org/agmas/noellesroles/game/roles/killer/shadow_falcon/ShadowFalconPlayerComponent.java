package org.agmas.noellesroles.game.roles.killer.shadow_falcon;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * 影隼组件
 * 
 * 管理"掠食"技能：
 * - 开局60秒冷却
 * - 使用后获得20秒创造模式飞行
 * - 浮空时获得1层临时护盾（被打掉就没了）
 * - 技能持续20秒，冷却240秒
 * - 死亡后为所有存活杀手提供喷气背包
 */
public class ShadowFalconPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    /** 组件键 */
    public static final ComponentKey<ShadowFalconPlayerComponent> KEY = ModComponents.SHADOW_FALCON;

    // ==================== 常量定义 ====================
    
    /** 开局冷却时间（60秒 = 1200 tick） */
    public static final int INITIAL_COOLDOWN = 1200;
    
    /** 技能持续时间（20秒 = 400 tick） */
    public static final int SKILL_DURATION = 400;
    
    /** 技能冷却时间（240秒 = 4800 tick） */
    public static final int SKILL_COOLDOWN = 4800;
    
    /** 浮空检测高度阈值（脚下方块检测距离） */
    private static final double AIR_CHECK_DISTANCE = 0.1;

    // ==================== 状态变量 ====================
    
    private final Player player;
    
    /** 技能冷却时间（tick） */
    public int cooldown = INITIAL_COOLDOWN;
    
    /** 技能持续时间（tick），用于HUD显示 */
    public int skillTicks = 0;
    
    /** 是否正在使用掠食技能 */
    public boolean isPredationActive = false;
    
    /** 临时护盾层数（0或1） */
    public int temporaryShield = 0;
    
    /** 护盾是否被打掉（用于技能结束时判断） */
    public boolean shieldBroken = false;
    
    /** 上一tick是否在空中（用于检测从空中着陆） */
    private boolean wasInAir = false;

    public ShadowFalconPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    /**
     * 重置组件状态
     */
    @Override
    public void init() {
        this.cooldown = INITIAL_COOLDOWN;
        this.skillTicks = 0;
        this.isPredationActive = false;
        this.temporaryShield = 0;
        this.shieldBroken = false;
        this.wasInAir = false;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    /**
     * 检查技能是否可用
     */
    public boolean canUseAbility() {
        return cooldown <= 0 && !isPredationActive;
    }

    /**
     * 使用掠食技能（浮空时给盾 + 开启创造模式飞行）
     * 
     * @return 是否成功使用
     */
    public boolean useAbility() {
        if (!canUseAbility()) {
            return false;
        }

        // 验证是影隼
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.SHADOW_FALCON)) {
            return false;
        }

        // 检查玩家是否存活
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }

        // 给予虚弱效果（限制攻击能力）
        if (player instanceof ServerPlayer serverPlayer) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.WEAKNESS,
                    SKILL_DURATION,
                    0,
                    false,
                    false,
                    false
            ));
            
            // 开启创造模式飞行能力
            serverPlayer.getAbilities().mayfly = true;
            serverPlayer.getAbilities().flying = true;
            serverPlayer.onUpdateAbilities();
        }

        // 设置状态
        this.isPredationActive = true;
        this.skillTicks = SKILL_DURATION;
        this.temporaryShield = 0;
        this.shieldBroken = false;

        // 设置冷却
        this.cooldown = SKILL_COOLDOWN;

        // 发送消息
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.shadow_falcon.predation_activated"),
                    true);
        }

        this.sync();
        return true;
    }

    /**
     * 脱下喷气背包和鞘翅
     * 
     * @return 是否成功脱下
     */
    public boolean removeJetpack() {
        // 检查玩家是否存活
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }

        boolean removedSomething = false;
        StringBuilder messageBuilder = new StringBuilder();

        // 检查身上是否有喷气背包
        ItemStack chestplate = player.getInventory().getArmor(2); // 胸甲槽位
        if (chestplate.is(ModItems.JETPACK)) {
            // 尝试将喷气背包放入背包
            ItemStack jetpack = chestplate.copy();
            player.getInventory().armor.set(2, ItemStack.EMPTY);
            
            if (!player.getInventory().add(jetpack)) {
                // 背包满了，恢复装备并提示
                player.getInventory().armor.set(2, jetpack);
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.displayClientMessage(
                            Component.translatable("message.noellesroles.shadow_falcon.inventory_full"),
                            true);
                }
                return false;
            }
            
            removedSomething = true;
            messageBuilder.append(Component.translatable("message.noellesroles.shadow_falcon.jetpack_removed").getString());
        }

        // 检查身上是否有鞘翅（胸甲位置）
        ItemStack currentChest = player.getInventory().getArmor(2);
        if (currentChest.is(net.minecraft.world.item.Items.ELYTRA)) {
            // 尝试将鞘翅放入背包
            ItemStack elytra = currentChest.copy();
            player.getInventory().armor.set(2, ItemStack.EMPTY);
            
            if (!player.getInventory().add(elytra)) {
                // 背包满了，恢复装备并提示
                player.getInventory().armor.set(2, elytra);
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.displayClientMessage(
                            Component.translatable("message.noellesroles.shadow_falcon.inventory_full"),
                            true);
                }
                return false;
            }
            
            if (removedSomething) {
                messageBuilder.append(", ");
            }
            messageBuilder.append(Component.translatable("message.noellesroles.shadow_falcon.elytra_removed").getString());
            removedSomething = true;
        }

        if (!removedSomething) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.shadow_falcon.no_jetpack"),
                        true);
            }
            return false;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.literal(messageBuilder.toString()),
                    true);
        }

        this.sync();
        return true;
    }

    /**
     * 检查玩家是否在空中（脚下没有方块）
     */
    public boolean isInAir() {
        // 检查脚下是否有方块
        double feetY = player.getY();
        double checkY = feetY - AIR_CHECK_DISTANCE;
        
        // 获取玩家所在的区块
        int x = (int) Math.floor(player.getX());
        int z = (int) Math.floor(player.getZ());
        
        // 检查脚下是否有固体方块
        var level = player.level();
        var blockPos = new net.minecraft.core.BlockPos(x, (int) Math.floor(checkY), z);
        var blockState = level.getBlockState(blockPos);
        
        // 如果脚下没有方块或者是空气，则认为在空中
        return !blockState.isSolid() || blockState.isAir();
    }

    /**
     * 给予临时护盾（通过 SREArmorPlayerComponent 的限时护盾实现）
     */
    public void giveTemporaryShield() {
        if (isPredationActive && skillTicks > 0 && temporaryShield == 0 && !shieldBroken) {
            temporaryShield = 1;
            // 通过 SRE 护盾组件给予真正的限时护盾（1层，剩余技能持续时间内有效）
            SREArmorPlayerComponent armorComp = SREArmorPlayerComponent.KEY.get(player);
            armorComp.addTimedArmor(1, skillTicks, false);
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.shadow_falcon.shield_gained"),
                        true);
            }
            this.sync();
        }
    }

    /**
     * 护盾被打破
     * @param landed 是否因为着陆而失去护盾
     */
    public void onShieldBroken(boolean landed) {
        if (temporaryShield > 0) {
            temporaryShield = 0;
            shieldBroken = true;
            // 清除 SRE 限时护盾
            SREArmorPlayerComponent armorComp = SREArmorPlayerComponent.KEY.get(player);
            armorComp.setTimedArmor(0, 0);
            if (player instanceof ServerPlayer serverPlayer) {
                if (landed) {
                    serverPlayer.displayClientMessage(
                            Component.translatable("message.noellesroles.shadow_falcon.shield_lost_landed"),
                            true);
                }
            }
            this.sync();
        }
    }
    
    /**
     * 护盾被打破（兼容旧调用）
     */
    public void onShieldBroken() {
        onShieldBroken(false);
    }

    /**
     * 获取冷却时间（秒）
     */
    public float getCooldownSeconds() {
        return cooldown / 20.0f;
    }

    /**
     * 获取技能剩余时间（秒）
     */
    public float getSkillSeconds() {
        return skillTicks / 20.0f;
    }

    /**
     * 同步到客户端
     */
    public void sync() {
        ModComponents.SHADOW_FALCON.sync(this.player);
    }

    // ==================== Tick 处理 ====================

    @Override
    public void serverTick() {
        // 减少冷却时间
        if (this.cooldown > 0) {
            this.cooldown--;
            // 每秒同步一次
            if (this.cooldown % 20 == 0 || this.cooldown == 0) {
                this.sync();
            }
        }

        // 处理技能效果
        if (this.skillTicks > 0) {
            this.skillTicks--;

            boolean currentlyInAir = isInAir();
            
            // 检查是否从空中着陆 - 如果之前在空中，现在不在，且有护盾
            if (wasInAir && !currentlyInAir && temporaryShield > 0) {
                onShieldBroken(true);
            }
            
            // 检查是否在空中
            if (currentlyInAir && temporaryShield == 0 && !shieldBroken) {
                giveTemporaryShield();
            }
            
            // 更新浮空状态
            wasInAir = currentlyInAir;

            // 技能结束
            if (this.skillTicks <= 0) {
                endPredation();
            }
        } else {
            // 技能未激活时也更新浮空状态，避免技能激活瞬间误判
            wasInAir = isInAir();
        }
    }

    /**
     * 结束掠食技能
     */
    private void endPredation() {
        this.isPredationActive = false;
        
        // 清除虚弱效果和创造模式飞行
        if (player instanceof ServerPlayer serverPlayer) {
            player.removeEffect(MobEffects.WEAKNESS);
            
            // 关闭创造模式飞行能力
            serverPlayer.getAbilities().mayfly = false;
            serverPlayer.getAbilities().flying = false;
            serverPlayer.onUpdateAbilities();
            
            // 如果护盾还在，移除护盾
            if (temporaryShield > 0 && !shieldBroken) {
                temporaryShield = 0;
                // 清除 SRE 限时护盾
                SREArmorPlayerComponent armorComp = SREArmorPlayerComponent.KEY.get(player);
                armorComp.setTimedArmor(0, 0);
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.shadow_falcon.predation_ended"),
                        true);
            } else {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.shadow_falcon.predation_ended_shield_broken"),
                        true);
            }
        }
        
        this.shieldBroken = false;
        this.sync();
    }

    /**
     * 处理影隼死亡 - 为存活杀手提供喷气背包
     */
    public static void onDeathGiveJetpacks(Player deadPlayer) {
        if (!(deadPlayer instanceof ServerPlayer)) return;
        
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(deadPlayer.level());
        if (gameWorld == null) return;
        
        // 检查是否是影隼
        if (!gameWorld.isRole(deadPlayer, ModRoles.SHADOW_FALCON)) return;
        
        // 获取所有存活的杀手阵营玩家
        List<ServerPlayer> killerPlayers = new ArrayList<>();
        for (Player p : deadPlayer.level().players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(p)) continue;
            if (p == deadPlayer) continue;
            if (!(p instanceof ServerPlayer sp)) continue;
            
            // 检查是否是杀手阵营
            SREGameWorldComponent pGameWorld = SREGameWorldComponent.KEY.get(p.level());
            if (pGameWorld != null) {
                var role = pGameWorld.getRole(p);
                if (role != null && !role.isInnocent() && role.canUseKiller()) {
                    killerPlayers.add((ServerPlayer) p);
                }
            }
        }
        
        // 为每个存活杀手提供喷气背包
        for (ServerPlayer killer : killerPlayers) {
            // 给予喷气背包
            var jetpackStack = ModItems.JETPACK.getDefaultInstance();
            killer.addItem(jetpackStack);
            
            killer.displayClientMessage(
                    Component.translatable("message.noellesroles.shadow_falcon.jetpack_gifted", deadPlayer.getName())
                        .withStyle(net.minecraft.ChatFormatting.GOLD),
                    true);
        }
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("cooldown", this.cooldown);
        tag.putInt("skillTicks", this.skillTicks);
        tag.putBoolean("isPredationActive", this.isPredationActive);
        tag.putInt("temporaryShield", this.temporaryShield);
        tag.putBoolean("shieldBroken", this.shieldBroken);
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.cooldown = tag.contains("cooldown") ? tag.getInt("cooldown") : INITIAL_COOLDOWN;
        this.skillTicks = tag.contains("skillTicks") ? tag.getInt("skillTicks") : 0;
        this.isPredationActive = tag.contains("isPredationActive") && tag.getBoolean("isPredationActive");
        this.temporaryShield = tag.contains("temporaryShield") ? tag.getInt("temporaryShield") : 0;
        this.shieldBroken = tag.contains("shieldBroken") && tag.getBoolean("shieldBroken");
    }

    @Override
    public void clientTick() {
        // 客户端每tick减少冷却和技能时间（用于HUD显示）
        if (this.cooldown > 1) {
            this.cooldown--;
        }
        if (this.skillTicks > 0) {
            this.skillTicks--;
        }
    }
}
