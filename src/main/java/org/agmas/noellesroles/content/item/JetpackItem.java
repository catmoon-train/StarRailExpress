package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 喷气背包
 * - 穿在身上（渲染为铁胸甲）
 * - 蹲下时给予漂浮效果：
 *   - 普通人：漂浮1
 *   - 飞行员：漂浮5
 *   - 杀手阵营：漂浮3
 *   - 影隼：漂浮7，不消耗耐久
 * - 普通玩家每秒消耗1点耐久，60点耐久
 * - 可丢弃
 */
public class JetpackItem extends ArmorItem {

    public static final int MAX_DURABILITY = 60;
    
    /** 漂浮效果持续时间（10分钟 = 12000 tick） */
    private static final int LEVITATION_DURATION = 20 * 60 * 10;
    
    /** 耐久消耗计时器（1秒 = 20 tick） */
    private int durabilityTickCounter = 0;
    
    /** 玩家是否在上一次检测时处于蹲下状态 */
    private static final Map<UUID, Boolean> wasSneaking = new HashMap<>();
    
    /** 玩家是否在上一次检测时穿着喷气背包（用于过渡检测，只清除一次） */
    private static final Map<UUID, Boolean> wasWearingJetpack = new HashMap<>();

    public JetpackItem(Holder<ArmorMaterial> holder, Type type, Properties properties) {
        super(holder, type, properties);
    }

    @Override
    public Holder<SoundEvent> getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_IRON;
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = super.getDefaultInstance();
        stack.setDamageValue(0);
        return stack;
    }

    /**
     * 检查玩家是否是飞行员
     */
    private static boolean isPilot(Player player) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null) {
            return false;
        }
        return gameWorld.isRole(player, ModRoles.PILOT);
    }

    /**
     * 检查玩家是否是影隼
     */
    private static boolean isShadowFalcon(Player player) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null) {
            return false;
        }
        return gameWorld.isRole(player, ModRoles.SHADOW_FALCON);
    }

    /**
     * 检查玩家是否是杀手阵营（含杀手方中立，不含影隼）
     */
    private static boolean isKillerTeam(Player player) {
        if (isShadowFalcon(player)) return false;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null) return false;
        var role = gameWorld.getRole(player);
        return role != null && role.isKillerTeam();
    }

    /**
     * 处理喷气背包效果
     * 应该在玩家装备喷气背包时每tick调用
     */
    public static void tickJetpackEffect(Player player) {
        if (player == null) {
            return;
        }
        
        // 获取胸甲槽位的物品
        ItemStack chestplate = player.getInventory().getArmor(2);
        
        // 检查是否是喷气背包
        boolean isWearingJetpack = chestplate.is(ModItems.JETPACK);
        
        if (!isWearingJetpack) {
            // 没有喷气背包 -> 只在从穿到脱的过渡瞬间清除一次漂浮（不要每tick持续清除，会误删其他来源的漂浮）
            boolean prevWearing = wasWearingJetpack.getOrDefault(player.getUUID(), false);
            if (prevWearing) {
                if (player.hasEffect(MobEffects.LEVITATION)) {
                    MobEffectInstance levitation = player.getEffect(MobEffects.LEVITATION);
                    if (levitation != null && levitation.getDuration() > 100) {
                        player.removeEffect(MobEffects.LEVITATION);
                    }
                }
            }
            wasWearingJetpack.put(player.getUUID(), false);
            return;
        }
        
        // 穿着喷气背包，更新状态
        wasWearingJetpack.put(player.getUUID(), true);
        
        // 如果玩家正在蹲下
        if (player.isShiftKeyDown()) {
            // 检查耐久（影隼不消耗耐久）
            int currentDamage = chestplate.getDamageValue();
            int remainingDurability = MAX_DURABILITY - currentDamage;
            
            if (remainingDurability <= 0 && !isShadowFalcon(player)) {
                // 耐久耗尽，移除效果并破坏物品（非影隼才会破坏）
                if (player.hasEffect(MobEffects.LEVITATION)) {
                    player.removeEffect(MobEffects.LEVITATION);
                }
                if (player instanceof ServerPlayer) {
                    chestplate.shrink(1);
                }
                return;
            }
            
            // 给予漂浮效果
            MobEffectInstance levitation = player.getEffect(MobEffects.LEVITATION);
            
            // 检查漂浮效果的来源和等级
            if (levitation == null || levitation.getDuration() < LEVITATION_DURATION / 2) {
                // 确定漂浮等级
                if (isShadowFalcon(player)) {
                    // 影隼获得漂浮7
                    player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, LEVITATION_DURATION, 6, 
                            false, false, true));
                } else if (isPilot(player)) {
                    // 飞行员获得漂浮5
                    player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, LEVITATION_DURATION, 4, 
                            false, false, true));
                } else if (isKillerTeam(player)) {
                    // 杀手阵营获得漂浮3
                    player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, LEVITATION_DURATION, 2, 
                            false, false, true));
                } else {
                    // 普通人获得漂浮1
                    player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, LEVITATION_DURATION, 0, 
                            false, false, true));
                }
            }
            
            // 消耗耐久（每秒1点），影隼不消耗
            if (!isShadowFalcon(player) && player.level().getGameTime() % 20 == 0) {
                chestplate.setDamageValue(currentDamage + 1);
                
                // 如果耐久耗尽，破坏物品
                if (chestplate.getDamageValue() >= MAX_DURABILITY) {
                    player.removeEffect(MobEffects.LEVITATION);
                    chestplate.shrink(1);
                    
                    // 发送消息
                    if (player instanceof ServerPlayer sp) {
                        sp.displayClientMessage(
                                net.minecraft.network.chat.Component.translatable("message.noellesroles.jetpack_broken"),
                                true);
                    }
                }
            }
        } else {
            // 玩家没有蹲下，只在从蹲下变为不蹲下的瞬间清除一次漂浮效果
            Boolean prevSneaking = wasSneaking.getOrDefault(player.getUUID(), false);
            if (prevSneaking) {
                // 刚从蹲下变为不蹲下，清除一次漂浮效果
                if (player.hasEffect(MobEffects.LEVITATION)) {
                    MobEffectInstance levitation = player.getEffect(MobEffects.LEVITATION);
                    if (levitation != null && levitation.getDuration() > 100) { // 长时间持续的是喷气背包给的
                        player.removeEffect(MobEffects.LEVITATION);
                    }
                }
                wasSneaking.put(player.getUUID(), false);
            }
        }
        // 更新蹲下状态记录
        wasSneaking.put(player.getUUID(), player.isShiftKeyDown());
    }
}
