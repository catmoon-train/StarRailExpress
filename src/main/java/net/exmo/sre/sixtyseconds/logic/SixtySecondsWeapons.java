package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.index.TMMItems;
import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 60s 武器伤害表：枪械和职业武器改为<b>按武器扣除对应健康值</b>，不再固定 50。
 * 护甲（废料/铁制，各件百分比减伤，封顶 {@link #ARMOR_REDUCTION_CAP}）在
 * {@link #reduceByArmor} 统一结算。数值供 {@link SixtySecondsHealthSystem} 使用。
 */
public final class SixtySecondsWeapons {
    /** 未知武器/环境伤害的缺省健康伤害（旧固定值，仅模组击杀路径兜底用）。 */
    public static final int DEFAULT_DAMAGE = 50;
    /** 徒手/非武器物品攻击的满蓄力健康伤害（原版近战路径，按攻击间隔充能削减）。 */
    public static final int UNARMED_DAMAGE = 5;
    /** 非玩家生物（夜袭怪等）攻击的健康伤害。 */
    public static final int MOB_DAMAGE = 20;
    public static final double ARMOR_REDUCTION_CAP = 0.45;

    private static Map<Item, Integer> gunTable;
    private static Map<Item, Double> armorTable;

    private SixtySecondsWeapons() {
    }

    /** 攻击者主手武器 → 基础健康伤害。 */
    public static int injuryDamage(@Nullable ServerPlayer attacker) {
        if (attacker == null) {
            return DEFAULT_DAMAGE;
        }
        ItemStack held = attacker.getMainHandItem();
        Integer mapped = table().get(held.getItem());
        if (mapped != null) {
            return mapped;
        }
        if (held.getItem() instanceof net.exmo.sre.sixtyseconds.content.item.SixtySecondsMeleeWeaponItem melee) {
            return melee.healthDamage();
        }
        return DEFAULT_DAMAGE;
    }

    /**
     * 攻击者主手武器 → 基础健康伤害（带原版伤害量）：查表 / 60s 近战武器同 {@link #injuryDamage(ServerPlayer)}；
     * <b>持有未登记非空手物品</b>（tacz 枪/弹、原版剑/斧/弓等）按"150 健康 ≈ 50 原版HP"深度绑定换算
     * （{@link SixtySecondsBalance#HEALTH_PER_VANILLA_HP}）——原版伤害量 ×3 即得健康伤害；
     * <b>空手</b>仍走满蓄力 {@link #UNARMED_DAMAGE}=5，并按攻击间隔充能等比削减（连点刮痧、满蓄力 5）。
     * <p>修复要点：tacz 子弹原版伤害量很小（如 glock≈4-5、ak47≈7-8、awp≈12-15），原兜底走
     * {@code UNARMED_DAMAGE × (vanillaAmount/attackDamage)} 后被压成 1-2 健康伤害（攻击力默认=1），
     * 全枪械皆"2点伤害"。改为 vanillaAmount ×3 后，glock→12-15、ak47→21-24、awp→36-45，符合
     * "150:50"换算比例，与武器面板直观对应。
     */
    public static int injuryDamage(@Nullable ServerPlayer attacker, float vanillaAmount) {
        if (attacker == null) {
            return DEFAULT_DAMAGE;
        }
        ItemStack held = attacker.getMainHandItem();
        Integer mapped = table().get(held.getItem());
        if (mapped != null) {
            return mapped;
        }
        if (held.getItem() instanceof net.exmo.sre.sixtyseconds.content.item.SixtySecondsMeleeWeaponItem melee) {
            return melee.healthDamage();
        }
        // 持有未登记的非空手物品（tacz 枪/弹、原版剑/斧/弓等）：按 150:50 深度绑定换算
        if (!held.isEmpty()) {
            return Math.max(1, Math.round(vanillaAmount * SixtySecondsBalance.HEALTH_PER_VANILLA_HP));
        }
        // 空手：保留"连点刮痧"机制 - vanillaAmount 已含充能系数（0.2 + h²×0.8），
        // 除以满蓄力攻击力即得充能比；满蓄力 5、连点 1，避免徒手两下带走玩家
        double full = attacker.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        double charge = full > 1.0E-3 ? Math.min(1.0, vanillaAmount / full) : 1.0;
        return Math.max(1, (int) Math.round(UNARMED_DAMAGE * charge));
    }

    /** 按受害者穿戴的 60s 护甲做百分比减伤（封顶 45%）；防暴盾手持另乘 25% 减免（不占护甲上限）。 */
    public static int reduceByArmor(LivingEntity victim, int baseDamage) {
        double reduction = 0;
        for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
            Double piece = armorTable().get(victim.getItemBySlot(slot).getItem());
            if (piece != null) {
                reduction += piece;
            }
        }
        reduction = Math.min(ARMOR_REDUCTION_CAP, reduction);
        double result = baseDamage * (1.0 - reduction);
        if (victim.getMainHandItem().is(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_RIOT_SHIELD)
                || victim.getOffhandItem().is(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_RIOT_SHIELD)) {
            result *= 0.75;
        }
        return Math.max(1, (int) Math.round(result));
    }

    private static synchronized Map<Item, Integer> table() {
        if (gunTable == null) {
            Map<Item, Integer> map = new HashMap<>();
            map.put(TMMItems.REVOLVER, 60);
            map.put(TMMItems.STANDARD_REVOLVER, 60);
            map.put(TMMItems.DERRINGER, 45);
            map.put(TMMItems.SNIPER_RIFLE, 90);
            map.put(org.agmas.noellesroles.init.ModItems.SHERIFF_REVOLVER, 60);
            map.put(org.agmas.noellesroles.init.ModItems.EXECUTIONER_GUN, 100);
            map.put(org.agmas.noellesroles.init.ModItems.ZERO_ONE_FIVE_GUN, 35);
            map.put(org.agmas.noellesroles.init.ModItems.BANDIT_REVOLVER, 60);
            gunTable = map;
        }
        return gunTable;
    }

    private static synchronized Map<Item, Double> armorTable() {
        if (armorTable == null) {
            Map<Item, Double> map = new HashMap<>();
            map.put(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_SCRAP_HELMET, 0.08);
            map.put(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_SCRAP_CHESTPLATE, 0.08);
            map.put(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_SCRAP_LEGGINGS, 0.06);
            map.put(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_SCRAP_BOOTS, 0.05);
            map.put(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_IRON_HELMET, 0.15);
            map.put(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_IRON_CHESTPLATE, 0.15);
            map.put(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_IRON_LEGGINGS, 0.12);
            map.put(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_IRON_BOOTS, 0.10);
            map.put(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_GAS_MASK, 0.03);
            map.put(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_HAZMAT_SUIT, 0.05);
            // 钢制护甲套（优于铁制）+ 防弹背心（单件最高减伤）
            map.put(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_STEEL_HELMET, 0.18);
            map.put(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_STEEL_CHESTPLATE, 0.18);
            map.put(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_STEEL_LEGGINGS, 0.15);
            map.put(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_STEEL_BOOTS, 0.12);
            map.put(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_BALLISTIC_VEST, 0.25);
            // 塑料护甲套（废料与铁之间）
            map.put(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_PLASTIC_HELMET, 0.11);
            map.put(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_PLASTIC_CHESTPLATE, 0.11);
            map.put(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_PLASTIC_LEGGINGS, 0.09);
            map.put(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_PLASTIC_BOOTS, 0.07);
            // 合金护甲套（优于钢制；原版侧另有韧性/击退抗性）
            map.put(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_ALLOY_HELMET, 0.22);
            map.put(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_ALLOY_CHESTPLATE, 0.22);
            map.put(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_ALLOY_LEGGINGS, 0.18);
            map.put(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_ALLOY_BOOTS, 0.15);
            armorTable = map;
        }
        return armorTable;
    }

}
