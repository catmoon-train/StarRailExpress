/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.customitem;

import com.google.gson.annotations.SerializedName;
import io.wifi.starrailexpress.game.GameConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个「自定义列车物品」的完整配置数据（Gson + {@code @SerializedName} 持久化）。
 *
 * <p>
 * 与自定义职业 / 自定义修饰符结构对齐，但单独保存在 {@code sre_custom_items.json} 里。
 * 所有自定义列车物品本质上都是同一个注册物品（{@code starrailexpress:custom_item}）加上
 * {@code CUSTOM_ITEM_ID} 数据组件，本类描述的就是「那份物品数据」。
 *
 * <p>
 * 时间类字段单位统一为 <b>tick</b>（20 tick = 1 秒）。
 */
public class CustomItemData {

    /** 自定义列车物品统一命名空间（仅用于展示 / 标识）。 */
    public static final String NAMESPACE = "customitem";

    /** 默认枪械开火音效（左轮手枪开火）。 */
    public static final String DEFAULT_FIRE_SOUND = "starrailexpress:item.revolver.shoot";

    // ==================== 基础数据 ====================

    /** 物品编号（英文，供指令 {@code /sre:givecustomitem} 获取）。 */
    @SerializedName("id")
    public String id = "";

    /** 物品显示名称。 */
    @SerializedName("displayName")
    public String displayName = "";

    /** 物品 tooltip（每行一条）。 */
    @SerializedName("tooltip")
    public List<String> tooltip = new ArrayList<>();

    /** 材质继承：填写物品 id（如 {@code minecraft:diamond_sword}）。 */
    @SerializedName("inheritItemTexture")
    public String inheritItemTexture = "";

    /** 资源包物品材质继承：填写贴图路径，与上一项冲突时优先本项。 */
    @SerializedName("packTexturePath")
    public String packTexturePath = "";

    /** 物品性质（单选）。 */
    @SerializedName("kind")
    public String kind = Kind.BASIC.name();

    // ==================== 性质：基础道具 ====================

    /** 玩家右键执行的指令（<player> = 使用物品的玩家）。 */
    @SerializedName("commands")
    public List<String> commands = new ArrayList<>();

    /** 使用后物品进入的原版冷却（tick）。 */
    @SerializedName("cooldownTicks")
    public int cooldownTicks = 0;

    /** 使用后是否消耗物品。 */
    @SerializedName("consumeItem")
    public boolean consumeItem = false;

    // ==================== 性质：蓄力道具 ====================

    /** 蓄力动作（{@link ChargeAnim} 名称）。 */
    @SerializedName("chargeAnim")
    public String chargeAnim = ChargeAnim.BOW.name();

    /** 蓄力时间（tick）。 */
    @SerializedName("chargeTicks")
    public int chargeTicks = 20;

    /** 蓄力完成后对使用者自己执行的指令。 */
    @SerializedName("selfCommands")
    public List<String> selfCommands = new ArrayList<>();

    /** 是否对其它玩家作用。 */
    @SerializedName("affectOthers")
    public boolean affectOthers = false;

    /** 作用范围（{@link TargetMode} 名称）。 */
    @SerializedName("targetMode")
    public String targetMode = TargetMode.CIRCLE.name();

    /** 扇形角度（度；仅扇形范围使用）。 */
    @SerializedName("coneAngle")
    public double coneAngle = 60.0;

    /** 作用距离（格）。 */
    @SerializedName("range")
    public double range = 5.0;

    /** 对其它玩家作用时，被作用的玩家执行的指令。 */
    @SerializedName("targetCommands")
    public List<String> targetCommands = new ArrayList<>();

    // ==================== 性质：枪械道具 ====================

    /** 枪械开火音效 id（开火与自动开火时播放），默认左轮手枪开火。 */
    @SerializedName("fireSound")
    public String fireSound = DEFAULT_FIRE_SOUND;

    /** 是否显示弹道射线。 */
    @SerializedName("showTracer")
    public boolean showTracer = true;

    /** 枪械射程（格）。 */
    @SerializedName("gunRange")
    public double gunRange = 20.0;

    /** 枪械后坐力（度）。 */
    @SerializedName("recoil")
    public double recoil = 4.0;

    /** 玩家被第几次命中时触发最终效果（默认 1）。 */
    @SerializedName("hitsToFinal")
    public int hitsToFinal = 1;

    /** 射击间隔冷却（tick）。 */
    @SerializedName("shotCooldownTicks")
    public int shotCooldownTicks = 10;

    /** 触发最终效果时枪械进入的冷却（tick）。 */
    @SerializedName("finalCooldownTicks")
    public int finalCooldownTicks = 200;

    /** 枪械右键发射时执行的指令。 */
    @SerializedName("shootCommands")
    public List<String> shootCommands = new ArrayList<>();

    /** 被枪械击中的玩家执行的指令。 */
    @SerializedName("hitCommands")
    public List<String> hitCommands = new ArrayList<>();

    /** 枪械触发最终效果时，被击中的玩家执行的指令。 */
    @SerializedName("finalHitCommands")
    public List<String> finalHitCommands = new ArrayList<>();

    /** 被击中的玩家是否会被击退（用 1 点原版伤害实现）。 */
    @SerializedName("knockbackOnHit")
    public boolean knockbackOnHit = false;

    /** 命中是否致死（默认关闭）。 */
    @SerializedName("lethalOnHit")
    public boolean lethalOnHit = false;

    /** 命中致死使用的死亡原因（默认「左轮手枪」）。 */
    @SerializedName("lethalDeathReason")
    public String lethalDeathReason = GameConstants.DeathReasons.REVOLVER.toString();

    /** 是否为自动枪械。 */
    @SerializedName("autoFire")
    public boolean autoFire = false;

    /** 自动射击次数。 */
    @SerializedName("autoShots")
    public int autoShots = 3;

    /** 自动射击几次后触发最终效果。 */
    @SerializedName("autoShotsToFinal")
    public int autoShotsToFinal = 2;

    /** 自动射击每次的间隔（tick）。 */
    @SerializedName("autoShotIntervalTicks")
    public int autoShotIntervalTicks = 5;

    /** 每次自动射击执行的指令。 */
    @SerializedName("autoShotCommands")
    public List<String> autoShotCommands = new ArrayList<>();

    /** 是否具有弹药系统。 */
    @SerializedName("ammoSystem")
    public boolean ammoSystem = false;

    /** 弹药量上限。 */
    @SerializedName("maxAmmo")
    public int maxAmmo = 6;

    /** 命中玩家时是否恢复 1 个弹药。 */
    @SerializedName("refillOnHit")
    public boolean refillOnHit = false;

    /** 是否支持子弹物品（右键子弹补弹）。 */
    @SerializedName("bulletItemSupport")
    public boolean bulletItemSupport = false;

    /** 手持姿势（{@link HoldPose} 名称，默认「左轮手枪式」）。 */
    @SerializedName("holdPose")
    public String holdPose = HoldPose.REVOLVER.name();

    // ==================== 性质：特殊原版物品 ====================

    /** 原版攻击速度（写入攻击速度属性修饰）。 */
    @SerializedName("attackSpeed")
    public double attackSpeed = -2.4;

    /** 虚拟伤害（扣除 {@code DreamHealthComponent} 的虚拟血量）。 */
    @SerializedName("virtualDamage")
    public int virtualDamage = 4;

    /** 右键物品执行的指令。 */
    @SerializedName("weaponRightClickCommands")
    public List<String> weaponRightClickCommands = new ArrayList<>();

    /** 物品右键后进入的冷却（tick）。 */
    @SerializedName("weaponRightClickCooldownTicks")
    public int weaponRightClickCooldownTicks = 0;

    /** 成功把目标虚拟血量削减至 0 时，物品进入的冷却（tick）。 */
    @SerializedName("killCooldownTicks")
    public int killCooldownTicks = 0;

    /** 削减至 0 时使用的死亡原因。 */
    @SerializedName("killDeathReason")
    public String killDeathReason = GameConstants.DeathReasons.GENERAL_ATTACK.toString();

    /** 被该物品攻击的玩家执行的指令。 */
    @SerializedName("victimCommands")
    public List<String> victimCommands = new ArrayList<>();

    /** 攻击者左键命中玩家时，攻击者执行的指令。 */
    @SerializedName("attackerHitCommands")
    public List<String> attackerHitCommands = new ArrayList<>();

    // ==================== 性质：食物道具 ====================

    /** 饥饿值。 */
    @SerializedName("nutrition")
    public int nutrition = 3;

    /** 饱和度。 */
    @SerializedName("saturation")
    public double saturation = 0.3;

    /** 是否为饮料（继承 Cocktail 的饮用表现与逻辑）。 */
    @SerializedName("isDrink")
    public boolean isDrink = false;

    /** 玩家食用时间（tick）。 */
    @SerializedName("eatTicks")
    public int eatTicks = 32;

    /** 玩家食用后执行的指令。 */
    @SerializedName("eatCommands")
    public List<String> eatCommands = new ArrayList<>();

    /** 食用后是否消耗物品（默认是）。 */
    @SerializedName("consumeOnEat")
    public boolean consumeOnEat = true;

    /** 食用后物品进入的冷却（tick）。 */
    @SerializedName("eatCooldownTicks")
    public int eatCooldownTicks = 0;

    // ==================== 工具方法 ====================

    public Kind kind() {
        try {
            return Kind.valueOf(kind);
        } catch (Exception e) {
            return Kind.BASIC;
        }
    }

    public ChargeAnim chargeAnim() {
        try {
            return ChargeAnim.valueOf(chargeAnim);
        } catch (Exception e) {
            return ChargeAnim.BOW;
        }
    }

    public TargetMode targetMode() {
        try {
            return TargetMode.valueOf(targetMode);
        } catch (Exception e) {
            return TargetMode.CIRCLE;
        }
    }

    public HoldPose holdPose() {
        try {
            return HoldPose.valueOf(holdPose);
        } catch (Exception e) {
            return HoldPose.REVOLVER;
        }
    }

    /** 完整的展示用标识：{@code customitem:<id>}。 */
    public String getFullIdentifier() {
        return NAMESPACE + ":" + id;
    }

    /**
     * 数值收敛与空值兜底：把配置里可能出现的非法值（负冷却、负射程、除零风险等）
     * 收敛到合法区间，并保证所有列表非 null。反序列化后与保存前都调用一次。
     */
    public void sanitize() {
        if (id == null) {
            id = "";
        }
        id = id.trim().toLowerCase();
        if (displayName == null) {
            displayName = "";
        }
        if (kind == null) {
            kind = Kind.BASIC.name();
        }
        if (inheritItemTexture == null) {
            inheritItemTexture = "";
        }
        if (packTexturePath == null) {
            packTexturePath = "";
        }
        tooltip = safeList(tooltip);
        commands = safeList(commands);
        selfCommands = safeList(selfCommands);
        targetCommands = safeList(targetCommands);
        shootCommands = safeList(shootCommands);
        hitCommands = safeList(hitCommands);
        finalHitCommands = safeList(finalHitCommands);
        autoShotCommands = safeList(autoShotCommands);
        weaponRightClickCommands = safeList(weaponRightClickCommands);
        victimCommands = safeList(victimCommands);
        attackerHitCommands = safeList(attackerHitCommands);
        eatCommands = safeList(eatCommands);
        if (chargeAnim == null || chargeAnim.isBlank()) {
            chargeAnim = ChargeAnim.BOW.name();
        }
        if (targetMode == null || targetMode.isBlank()) {
            targetMode = TargetMode.CIRCLE.name();
        }
        if (holdPose == null || holdPose.isBlank()) {
            holdPose = HoldPose.REVOLVER.name();
        }
        if (fireSound == null || fireSound.isBlank()) {
            fireSound = DEFAULT_FIRE_SOUND;
        }
        if (lethalDeathReason == null || lethalDeathReason.isBlank()) {
            lethalDeathReason = GameConstants.DeathReasons.REVOLVER.toString();
        }
        if (killDeathReason == null || killDeathReason.isBlank()) {
            killDeathReason = GameConstants.DeathReasons.GENERAL_ATTACK.toString();
        }

        cooldownTicks = clamp(cooldownTicks, 0, 20 * 60 * 10);
        chargeTicks = clamp(chargeTicks, 1, 20 * 60 * 10);
        coneAngle = clampDouble(coneAngle, 1.0, 360.0);
        range = clampDouble(range, 0.0, 256.0);
        gunRange = clampDouble(gunRange, 1.0, 256.0);
        recoil = clampDouble(recoil, 0.0, 90.0);
        hitsToFinal = clamp(hitsToFinal, 1, 1000);
        shotCooldownTicks = clamp(shotCooldownTicks, 0, 20 * 60 * 10);
        finalCooldownTicks = clamp(finalCooldownTicks, 0, 20 * 60 * 10);
        autoShots = clamp(autoShots, 1, 1000);
        autoShotsToFinal = clamp(autoShotsToFinal, 1, 1000);
        autoShotIntervalTicks = clamp(autoShotIntervalTicks, 0, 20 * 60);
        maxAmmo = clamp(maxAmmo, 1, 1000);
        attackSpeed = clampDouble(attackSpeed, -10.0, 10.0);
        virtualDamage = clamp(virtualDamage, 0, 1000);
        weaponRightClickCooldownTicks = clamp(weaponRightClickCooldownTicks, 0, 20 * 60 * 10);
        killCooldownTicks = clamp(killCooldownTicks, 0, 20 * 60 * 10);
        nutrition = clamp(nutrition, 0, 20);
        saturation = clampDouble(saturation, 0.0, 20.0);
        eatTicks = clamp(eatTicks, 1, 20 * 60);
        eatCooldownTicks = clamp(eatCooldownTicks, 0, 20 * 60 * 10);

        if (kind().requiresAmmo()) {
            // 弹药系统依赖物品上的 AMMO_COUNT 组件（组件不存在时按满弹处理）
        }
    }

    private static List<String> safeList(List<String> list) {
        return list == null ? new ArrayList<>() : list;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // ==================== 枚举 ====================

    /** 物品性质（单选）。 */
    public enum Kind {
        /** 基础道具：右键执行指令。 */
        BASIC,
        /** 蓄力道具：蓄力完成后触发。 */
        CHARGE,
        /** 枪械道具：右键射击。 */
        GUN,
        /** 特殊原版物品：可左键攻击玩家，扣除虚拟血量。 */
        VANILLA_WEAPON,
        /** 食物道具。 */
        FOOD;

        /** 是否与「弹药 / 命中计数」这类物品自身状态有关（用于保存 AMMO_COUNT）。 */
        public boolean requiresAmmo() {
            return this == GUN;
        }
    }

    /** 蓄力动作（对应原版 {@code UseAnim}）。 */
    public enum ChargeAnim {
        /** 无动作。 */
        NONE,
        /** 拉弓（验毒试剂同款）。 */
        BOW,
        /** 举矛（小刀 / 飞斧同款）。 */
        SPEAR,
        /** 弩（霰弹枪 / 灭火器同款）。 */
        CROSSBOW,
        /** 饮用（鸡尾酒同款）。 */
        DRINK,
        /** 进食。 */
        EAT,
        /** 举盾格挡。 */
        BLOCK,
        /** 刷子。 */
        BRUSH
    }

    /** 蓄力 / 右键作用范围。 */
    public enum TargetMode {
        /** 以玩家为中心的圆形范围。 */
        CIRCLE,
        /** 玩家朝向的扇形范围。 */
        CONE,
        /** 玩家朝向的直线距离。 */
        LINE,
        /** 指向的玩家（视线命中的单个玩家）。 */
        LOOKED_PLAYER
    }

    /**
     * 枪械手持姿势（决定手持该物品时的手臂姿势与枪口位置追踪）。
     *
     * <p>
     * 与项目内既有物品保持一致：{@code HeldLikeRevolver}（左轮手枪）、{@code HeldLikeBat}（球棒/弩蓄力）。
     */
    public enum HoldPose {
        /** 左轮手枪式：手臂伸直持枪 + 枪口位置追踪（默认）。 */
        REVOLVER,
        /** 举起式：像球棒 / 弩蓄力那样举起。 */
        RAISED,
        /** 瞄准式：像端弩瞄准那样。 */
        AIM,
        /** 原版默认手持。 */
        DEFAULT
    }
}
