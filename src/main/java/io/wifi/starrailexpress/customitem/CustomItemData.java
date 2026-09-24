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

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import io.wifi.starrailexpress.api.RoleTeam;
import io.wifi.starrailexpress.game.GameConstants;
import net.minecraft.resources.ResourceLocation;

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

    /** 物品编号（英文，供指令 {@code /sre:give item <id>} 获取）。 */
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

    /**
     * 材质来源（单选，{@link TextureMode}）。
     *
     * <p>
     * 四种来源<b>相互独立</b>，只生效当前选中的这一种：
     * {@link TextureMode#PACK} 用 {@link #packTexturePath}（平面 PNG）、
     * {@link TextureMode#ANIMATED} 用 {@link #animatedTextures}（多帧循环，帧数不限）、
     * {@link TextureMode#MODEL} 用 {@link #modelPath}（渲染资源包模型 json；留空时退回
     * {@link #inheritItemTexture}，借某个物品的模型）、
     * {@link TextureMode#INHERIT} 用 {@link #inheritItemTexture}（整份借用该物品的模型与材质）。
     */
    @SerializedName("textureMode")
    public String textureMode = TextureMode.PACK.name();

    /**
     * 动态贴图的帧（{@link TextureMode#ANIMATED} 用）。
     *
     * <p>
     * 按填写顺序循环播放，<b>帧数不限</b>；空串会被跳过。
     */
    @SerializedName("animatedTextures")
    public List<String> animatedTextures = new ArrayList<>();

    /** 动态模型的帧（{@link TextureMode#ANIMATED} 用）。每一帧填写资源包里的模型 json 地址。 */
    @SerializedName("animatedModels")
    public List<String> animatedModels = new ArrayList<>();

    /** 动态贴图每帧停留的 tick（{@link TextureMode#ANIMATED} 用）。 */
    @SerializedName("animatedFrameTicks")
    public int animatedFrameTicks = 8;

    /**
     * 模型地址（{@link TextureMode#MODEL} 用）：资源包里<b>模型 json</b> 的路径。
     *
     * <p>
     * 写法：{@code mypack:item/my_gun}、{@code mypack:models/item/my_gun.json}、
     * {@code models/item/my_gun.json}（默认 minecraft）、{@code assets/mypack/models/item/my_gun.json}
     * —— 对应文件都是 {@code assets/mypack/models/item/my_gun.json}。
     *
     * <p>
     * 与材质地址完全独立：材质地址管贴图，模型地址管立体模型，两者都一直显示、都保留。
     */
    @SerializedName("modelPath")
    public String modelPath = "";

    /** 是否手持时不可见（与项目内「大侦探的笔记」「占卜师的水晶球」同类效果）。 */
    @SerializedName("invisibleInHand")
    public boolean invisibleInHand = false;

    /** 物品性质（单选）。 */
    @SerializedName("kind")
    public String kind = Kind.BASIC.name();

    /**
     * 是否允许「左键攻击玩家」（默认关）。
     *
     * <p>
     * 关闭时：拿着这件自定义物品左键点玩家不会造成任何伤害与击退（也不会拉仇恨）；
     * 需要近战的物品（例如枪托砸人、道具打人）可以打开。
     * 特殊原版物品（{@link Kind#VANILLA_WEAPON}）走自己的虚拟血量逻辑，不受本项影响。
     */
    @SerializedName("allowLeftClickAttack")
    public boolean allowLeftClickAttack = false;

    /**
     * 手持方向（{@link HoldOrientation} 名称，默认 {@link HoldOrientation#VERTICAL}「竖着拿」）。
     *
     * <p>
     * 只影响第一 / 第三人称的手持姿态：竖着拿用物品模型自带 display（原版 {@code item/handheld}
     * 那套，像工具 / 剑那样立着），横着拿会抵掉它、换成原版普通物品（{@code item/generated}，
     * 苹果那类）的姿态。物品栏 / 掉落物 / 展示框不受影响。
     */
    @SerializedName("holdOrientation")
    public String holdOrientation = HoldOrientation.VERTICAL.name();

    /**
     * 手持微调：在物品<b>自己的贴图平面</b>上平移（单位 1/16 格，即一个「像素」）。
     *
     * <p>
     * X = 贴图右方为正、Y = 贴图上方为正、Z = 贴图的厚度方向（+ 朝观察者）。
     *
     * <p>
     * 为什么需要它：平面贴图的物品方框（16×16）是把<b>中心</b>对准手持姿态原点的，
     * 所以「贴图最底端」并不等于手的位置 —— 枪柄画到最底端仍然会浮在手外面。
     * 用这三个数把整张贴图往手的方向推即可，不必重画贴图。
     */
    @SerializedName("holdOffsetX")
    public double holdOffsetX = 0.0D;

    /** 手持微调 X（见 {@link #holdOffsetX}）。 */
    @SerializedName("holdOffsetY")
    public double holdOffsetY = 0.0D;

    /** 手持微调 Z（见 {@link #holdOffsetX}）。 */
    @SerializedName("holdOffsetZ")
    public double holdOffsetZ = 0.0D;

    /**
     * 手持微调：绕贴图横向轴（X 轴）旋转（度）—— 前后倾，用来摆平「竖着拿」的倾斜。
     *
     * <p>
     * 「竖着拿」用的原版 {@code item/handheld} 姿态在世界里等价于「绕贴图横向轴转了 55°（第三人称）
     * / 25°（第一人称）」，所以竖着画在贴图里的枪，在手上一定是斜着的；填
     * {@code -55}（第三人称）/ {@code -25}（第一人称）就能把贴图摆正。
     * 一个数值只能对准一个视角（两个视角的倾斜角不同），另一边想也精确可以拆成两套。
     */
    @SerializedName("holdRotateX")
    public double holdRotateX = 0.0D;

    /**
     * 手持微调：绕贴图平面法线（Z 轴）旋转（度）—— 平面内转正。
     *
     * <p>
     * 就是「在画面里把这张贴图转个角度」，不影响上面两个轴的含义。
     */
    @SerializedName("holdRotateZ")
    public double holdRotateZ = 0.0D;

    /** 手持微调是否有内容（全 0 时渲染器不必多做一次矩阵运算）。 */
    public boolean hasHoldTuning() {
        return holdOffsetX != 0.0D || holdOffsetY != 0.0D || holdOffsetZ != 0.0D
                || holdRotateX != 0.0D || holdRotateZ != 0.0D;
    }

    // ==================== 基础数据：丢弃 / 死亡 ====================

    /** 是否可丢弃（默认否）：为是时玩家可在游戏内主动丢弃该物品。 */
    @SerializedName("canDropItem")
    public boolean canDropItem = false;

    /** 仅能被该职业丢弃（职业 id，留空表示不做职业限制，只看 {@link #canDropItem}）。 */
    @SerializedName("dropOnlyRole")
    public String dropOnlyRole = "";

    /** 持有者（任意玩家）死亡时是否掉落该物品（默认否）。 */
    @SerializedName("dropOnDeath")
    public boolean dropOnDeath = false;

    /** 持有者死亡时把该物品传递给附近玩家的职业条件（职业 id，留空则此项不生效）。 */
    @SerializedName("passOnDeathRole")
    public String passOnDeathRole = "";

    /** 死亡传递的目标阵营（{@link RoleTeam} 名称）。 */
    @SerializedName("passOnDeathTeam")
    public String passOnDeathTeam = RoleTeam.CIVILIAN.name();

    // ==================== 基础数据：使用限制 ====================

    /**
     * 仅指定职业允许使用（逗号分隔的职业 id，留空 = 不限制）。
     *
     * <p>
     * 只有当前职业命中其中任意一个 id 的玩家才能使用该物品；职业 id 支持
     * {@code ns:path} 与纯 {@code path}，大小写不敏感（与「仅特定职业可丢弃」同一套匹配）。
     */
    @SerializedName("useOnlyRoles")
    public String useOnlyRoles = "";

    /**
     * 仅指定带有修饰符的玩家允许使用（逗号分隔的修饰符 id，留空 = 不限制）。
     *
     * <p>
     * 只有身上带有其中任意一个修饰符的玩家才能使用该物品。
     */
    @SerializedName("useOnlyModifiers")
    public String useOnlyModifiers = "";

    /**
     * 仅指定阵营允许使用（{@link RoleTeam} 名称列表，空列表 = 不限制）。
     *
     * <p>
     * 只要玩家所属阵营命中列表中的任意一项即可使用；多项之间是「或」的关系。
     */
    @SerializedName("useOnlyTeams")
    public List<String> useOnlyTeams = new ArrayList<>();

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

    /**
     * 基础道具是否「右键什么都不会发生」：没配指令、没有冷却、也不消耗。
     *
     * <p>
     * 这种情况服务端 {@code executeBasic} 什么都不做，所以客户端不该摆臂。
     */
    public boolean basicDoesNothing() {
        return (commands == null || commands.isEmpty()) && cooldownTicks <= 0 && !consumeItem;
    }

    // ==================== 性质：蓄力道具 ====================

    /**
     * 蓄力动作（{@link ChargeAnim} 名称）：<b>第一人称</b>那套 —— 自己屏幕上看到的
     * 手部 / 手持动作（走原版 {@code UseAnim}）。
     */
    @SerializedName("chargeAnim")
    public String chargeAnim = ChargeAnim.BOW.name();

    /**
     * <b>第三人称</b>蓄力姿势（{@link ThirdPose} 名称）：别人看到的手臂姿势。
     *
     * <p>
     * 可选值是一整套原版手臂姿势（比第一人称那栏多出端弩瞄准 / 举望远镜 / 吹号角），
     * 留空（或写了非法值）时等于 {@link ThirdPose#FOLLOW}，跟随第一人称动作 —— 与老配置行为一致。
     * 客户端渲染见 {@code mixin.client.PlayerEntityRendererMixin}。
     */
    @SerializedName("chargeAnimThird")
    public String chargeAnimThird = "";

    /** 蓄力时间（tick）。 */
    @SerializedName("chargeTicks")
    public int chargeTicks = 20;

    /** 蓄力完成后对使用者自己执行的指令。 */
    @SerializedName("selfCommands")
    public List<String> selfCommands = new ArrayList<>();

    /** 是否对其它玩家作用。 */
    @SerializedName("affectOthers")
    public boolean affectOthers = false;

    /** 是否只影响作用范围最外侧一格内的玩家。 */
    @SerializedName("affectOnlyMaxRange")
    public boolean affectOnlyMaxRange = false;

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

    /**
     * 空放冷却：蓄力完成但<b>没有命中任何玩家</b>时使用的「独立冷却」（tick）。
     *
     * <p>
     * 与 {@link #cooldownTicks}（命中后的冷却）相互独立：默认 {@code 0} = 空放不进入冷却。
     * 仅在 {@link #affectOthers} 为是时有意义（否则不存在「命中玩家」这个概念）。
     */
    @SerializedName("emptyFireCooldownTicks")
    public int emptyFireCooldownTicks = 0;

    /** 是否启用空放提示：为是时，蓄力完成但没有命中任何玩家会通过 actionbar 提示 {@link #emptyFireMessage}。 */
    @SerializedName("emptyFireMessageEnabled")
    public boolean emptyFireMessageEnabled = false;

    /** 空放提示文本（actionbar 显示）。{@link #emptyFireMessageEnabled} 为是时生效。 */
    @SerializedName("emptyFireMessage")
    public String emptyFireMessage = "";

    // ==================== 性质：枪械道具 ====================

    /** 枪械开火音效 id（开火与自动开火时播放），默认左轮手枪开火。 */
    @SerializedName("fireSound")
    public String fireSound = DEFAULT_FIRE_SOUND;

    /** 是否显示弹道射线。 */
    @SerializedName("showTracer")
    public boolean showTracer = true;

    /** 射线样式（{@link TracerStyle} 名称，仅在 {@link #showTracer} 开启时生效）。 */
    @SerializedName("tracerStyle")
    public String tracerStyle = TracerStyle.YELLOW.name();

    /** 射线是否允许穿过屏障（默认否；开启后同狙击枪，可穿过屏障类方块命中后方目标）。 */
    @SerializedName("tracerThroughBarrier")
    public boolean tracerThroughBarrier = false;

    /** 枪械射程（格）。 */
    @SerializedName("gunRange")
    public double gunRange = 20.0;

    /** 枪械后坐力（度）。 */
    @SerializedName("recoil")
    public double recoil = 4.0;

    /** 玩家被第几次命中时触发最终效果（默认 1）。 */
    @SerializedName("hitsToFinal")
    public int hitsToFinal = 1;

    /**
     * 命中标记的持续时间（tick，默认 100 = 5 秒）。
     *
     * <p>
     * 命中标记记在<b>被击中的玩家</b>身上：如果迟迟没凑够 {@link #hitsToFinal} 次，超过这段时间
     * 标记就自动消失、计数从 0 重新开始（同警棍的命中窗口）。标记按物品 id 分开存，
     * 不同物品的计数互不影响，不会错误叠加。
     */
    @SerializedName("hitMarkerTicks")
    public int hitMarkerTicks = 100;

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

    /** 距离检测：命中距离 ≥ {@code distance} 格以外的玩家时，对其执行 {@code command}。 */
    @SerializedName("distanceRules")
    public List<DistanceRule> distanceRules = new ArrayList<>();

    /** 被击中的玩家是否会被击退（用 1 点原版伤害实现）。 */
    @SerializedName("knockbackOnHit")
    public boolean knockbackOnHit = false;

    /**
     * 射线命中即致死（默认关闭）。
     *
     * <p>
     * 只要射线打中玩家就立刻致死（自动射击时第一发就会打死目标），因此不会再累计命中次数，
     * 也不会触发最终效果。
     */
    @SerializedName("lethalOnRayHit")
    public boolean lethalOnRayHit = false;

    /**
     * 是否只有触发最终效果时才致死（默认关闭）。
     *
     * <p>
     * 累计命中次数达到「命中几次触发最终效果」时把被击中的玩家致死。
     * 与 {@link #lethalOnRayHit} 互相独立，两个都开时射线命中那一条先生效。
     */
    @SerializedName("lethalOnFinal")
    public boolean lethalOnFinal = false;

    /** 命中致死使用的死亡原因（默认「左轮手枪」）。 */
    @SerializedName("lethalDeathReason")
    public String lethalDeathReason = GameConstants.DeathReasons.REVOLVER.toString();

    /**
     * 旧字段「命中是否致死」（只用于兼容老配置，不再写回 JSON）。
     *
     * <p>
     * 老配置里它等价于「触发最终效果时致死」，{@link #sanitize()} 会把它并入
     * {@link #lethalOnFinal} 后置空，于是老配置行为不变、新配置也不会留下多余的键。
     */
    @SerializedName("lethalOnHit")
    private Boolean legacyLethalOnHit;

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

    /** 发射按键（{@link FireButton} 名称，默认右键）。设为左键时同狙击枪：左键发射。 */
    @SerializedName("fireButton")
    public String fireButton = FireButton.RIGHT.name();

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

    /**
     * 特殊原版物品的右键是否「什么都不会发生」：没配右键指令。
     *
     * <p>
     * 这种情况服务端 {@code useVanillaWeapon} 只是空转，所以客户端不该摆臂。
     */
    public boolean weaponDoesNothing() {
        return weaponRightClickCommands == null || weaponRightClickCommands.isEmpty();
    }

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

    // ==================== 耐久 ====================

    /**
     * 耐久上限：这个物品使用多少次后会损坏。
     *
     * <p>
     * {@code 0} = 不消耗耐久、不显示耐久条。由「特殊原版物品」「手铐类」「投掷物」共用：
     * 特殊原版物品按「每次命中玩家」计一次，手铐按 {@link #cuffWearMode} 持续消耗，
     * 投掷物按「每次投出」计一次。
     */
    @SerializedName("durability")
    public int durability = 0;

    // ==================== 能否被小偷偷窃 ====================

    /** 是否能被小偷窃取：为是时这个物品会被加入小偷的可偷白名单（默认为否）。 */
    @SerializedName("stealable")
    public boolean stealable = false;

    // ==================== 手铐类物品 ====================

    /** 耐久消耗形式（{@link CuffWearMode} 名称）。 */
    @SerializedName("cuffWearMode")
    public String cuffWearMode = CuffWearMode.CROUCH.name();

    /** 被拷住时的姿势（{@link CuffPose} 名称）。 */
    @SerializedName("cuffPose")
    public String cuffPose = CuffPose.CUFFS.name();

    /** 是否限制玩家行为：为是时自带手铐效果（禁止跳跃/丢物品/换手 + 减速）。 */
    @SerializedName("cuffRestrict")
    public boolean cuffRestrict = false;

    /** 能被取下的阵营（{@link io.wifi.starrailexpress.api.RoleTeam} 名称）；留空 = 无人能取。 */
    @SerializedName("cuffTakeOffTeam")
    public String cuffTakeOffTeam = "";

    /** 能被取下的职业 id（可多个，{@code +} 添加）。 */
    @SerializedName("cuffTakeOffRoles")
    public List<String> cuffTakeOffRoles = new ArrayList<>();

    /** 能被取下的修饰符 id（可多个，{@code +} 添加）。 */
    @SerializedName("cuffTakeOffModifiers")
    public List<String> cuffTakeOffModifiers = new ArrayList<>();

    /**
     * 施加给被拷住玩家的药水效果。
     *
     * <p>
     * 持续时间固定为「直到被解除」（{@link EffectData#durationSeconds} 在此用途下被忽略），
     * 解除手铐时会被移除。
     */
    @SerializedName("cuffEffects")
    public List<EffectData> cuffEffects = new ArrayList<>();

    /** 被拷住玩家执行指令的间隔（tick）。 */
    @SerializedName("cuffCommandIntervalTicks")
    public int cuffCommandIntervalTicks = 100;

    /** 被拷住玩家执行的指令（{@code <player>} = 被拷住者本人，仅被拷住时运行）。 */
    @SerializedName("cuffCommands")
    public List<String> cuffCommands = new ArrayList<>();

    // ==================== 投掷物 ====================

    /** 是否需要拉栓：为是时按住右键拉栓蓄力、松手投出（蓄满可继续举着瞄准）；为否时右键直接投出并播放拉栓音效。 */
    @SerializedName("throwNeedPin")
    public boolean throwNeedPin = true;

    /** 拉栓/蓄力时间（tick）：蓄满该时间＝最大投掷力度，未蓄满也能投出、只是更近；仅 {@link #throwNeedPin} 为是时有意义。 */
    @SerializedName("throwPinTicks")
    public int throwPinTicks = 20;

    /** 是否会粘附在玩家/方块上直到生效（同粘性炸弹 / C4）。 */
    @SerializedName("throwSticky")
    public boolean throwSticky = false;

    /** 粘附后多久生效（tick）。 */
    @SerializedName("throwStickTicks")
    public int throwStickTicks = 60;

    /** 是否能被钳子拆除（默认否）。 */
    @SerializedName("throwDefusable")
    public boolean throwDefusable = false;

    /** 拆除所需时间（tick）：0 = 直接拆除（同粘性炸弹），>0 需要按住对应时间（同 C4）。 */
    @SerializedName("throwDefuseTicks")
    public int throwDefuseTicks = 0;

    /** 非特定职业拆除失败概率（职业 id + 百分比），留空不生效。 */
    @SerializedName("throwDefuseFailRules")
    public List<DefuseFailRule> throwDefuseFailRules = new ArrayList<>();

    /** 是否延迟生效（同滞时雷：落地会弹起，过一段时间才生效）。 */
    @SerializedName("throwDelayed")
    public boolean throwDelayed = false;

    /** 延迟生效的时间（秒）。 */
    @SerializedName("throwDelaySeconds")
    public double throwDelaySeconds = 1.0;

    /** 生效半径：以落点为圆心的球形范围，同时决定下面所有范围（含爆炸）的大小。 */
    @SerializedName("throwRadius")
    public double throwRadius = 4.0;

    /** 是否触发爆炸（同手榴弹）。 */
    @SerializedName("throwExplode")
    public boolean throwExplode = true;

    /** 爆炸是否无视墙体。 */
    @SerializedName("throwIgnoreWalls")
    public boolean throwIgnoreWalls = false;

    /** 无视多少格墙体（仅 {@link #throwIgnoreWalls} 为是时生效）。 */
    @SerializedName("throwWallIgnoreBlocks")
    public int throwWallIgnoreBlocks = 1;

    /** 投掷物触发爆炸时的粒子效果 id（留空 = 手榴弹默认粒子）。 */
    @SerializedName("throwExplosionParticle")
    public String throwExplosionParticle = "";

    /** 投掷物触发爆炸时的音效 id（留空 = 手榴弹默认音效）。 */
    @SerializedName("throwExplosionSound")
    public String throwExplosionSound = "";

    /** 爆炸时的死亡原因 id（默认手榴弹）。 */
    @SerializedName("throwDeathReason")
    public String throwDeathReason = "starrailexpress:grenade";

    /** 触发时对生效范围内玩家执行的指令（{@code <player>} = 范围内玩家）。 */
    @SerializedName("throwHitCommands")
    public List<String> throwHitCommands = new ArrayList<>();

    /** 触发时对生效范围内玩家施加的药水效果（id / 等级 / 持续秒数）。 */
    @SerializedName("throwHitEffects")
    public List<EffectData> throwHitEffects = new ArrayList<>();

    /** 触发时是否释放粒子区域（同烟雾弹）。 */
    @SerializedName("throwParticleArea")
    public boolean throwParticleArea = false;

    /** 粒子区域的粒子 id（留空 = 烟雾弹默认粒子）。 */
    @SerializedName("throwParticleAreaId")
    public String throwParticleAreaId = "";

    /** 粒子区域持续时间（tick）。 */
    @SerializedName("throwParticleAreaTicks")
    public int throwParticleAreaTicks = 200;

    /** 落地后是否留下持续生效范围（同燃烧弹，平面范围）。 */
    @SerializedName("throwPersistentArea")
    public boolean throwPersistentArea = false;

    /** 持续生效范围内持续释放的粒子 id（留空 = 燃烧弹默认粒子）。 */
    @SerializedName("throwAreaParticleId")
    public String throwAreaParticleId = "";

    /** 区域效果滞留时间（秒）。 */
    @SerializedName("throwAreaDurationSeconds")
    public int throwAreaDurationSeconds = 7;

    /** 玩家在区域内停留多少 tick 后触发效果。 */
    @SerializedName("throwAreaStayTicks")
    public int throwAreaStayTicks = 40;

    /** 区域内触发的药水效果。 */
    @SerializedName("throwAreaEffects")
    public List<EffectData> throwAreaEffects = new ArrayList<>();

    /** 区域内触发时对触发者执行的指令（{@code <player>} = 触发者本人）。 */
    @SerializedName("throwAreaCommands")
    public List<String> throwAreaCommands = new ArrayList<>();

    // ==================== 工具方法 ====================

    public Kind kind() {
        try {
            return Kind.valueOf(kind);
        } catch (Exception e) {
            return Kind.BASIC;
        }
    }

    /** 材质来源（大小写不敏感；解析失败回退资源包贴图，保证老数据行为不变）。 */
    public TextureMode textureMode() {
        if (textureMode == null) {
            return TextureMode.PACK;
        }
        for (TextureMode mode : TextureMode.values()) {
            if (mode.name().equalsIgnoreCase(textureMode.trim())) {
                return mode;
            }
        }
        return TextureMode.PACK;
    }

    /** 动态贴图的帧路径：按填写顺序去掉空串（帧数不限）。 */
    public List<String> animatedFramePaths() {
        List<String> result = new ArrayList<>();
        if (animatedTextures == null) {
            return result;
        }
        for (String path : animatedTextures) {
            if (path == null || path.isBlank()) {
                continue;
            }
            result.add(path.trim());
        }
        return result;
    }

    /** 动态模型的帧路径：按填写顺序循环，自动跳过空行。 */
    public List<String> animatedModelFramePaths() {
        List<String> result = new ArrayList<>();
        if (animatedModels == null) {
            return result;
        }
        for (String path : animatedModels) {
            if (path == null || path.isBlank()) {
                continue;
            }
            result.add(path.trim());
        }
        return result;
    }

    /**
     * 把「模型地址」解析成模型 id（Fabric extra model / {@code ModelResourceLocation} 用的那个 id）。
     *
     * <p>
     * 支持：{@code ns:item/x}、{@code ns:models/item/x.json}、{@code models/item/x.json}（默认 minecraft）、
     * {@code assets/ns/models/item/x.json}；反斜杠、前导斜杠、大小写命名空间都会归一。
     *
     * @return 解析不出来（空串 / 非法字符）返回 null
     */
    public static ResourceLocation resolveModelId(String configured) {
        if (configured == null) {
            return null;
        }
        String raw = configured.trim();
        if (raw.isEmpty()) {
            return null;
        }
        String namespace;
        String path;
        int split = raw.indexOf(':');
        if (split >= 0) {
            namespace = raw.substring(0, split).toLowerCase();
            path = raw.substring(split + 1);
        } else {
            namespace = "minecraft";
            path = raw;
        }
        path = path.replace('\\', '/');
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        String assetsPrefix = "assets/" + namespace + "/";
        if (path.startsWith(assetsPrefix)) {
            path = path.substring(assetsPrefix.length());
        }
        if (path.startsWith("models/")) {
            path = path.substring("models/".length());
        }
        if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - ".json".length());
        }
        if (path.isEmpty()) {
            return null;
        }
        return ResourceLocation.tryBuild(namespace, path);
    }

    public ChargeAnim chargeAnim() {
        try {
            return ChargeAnim.valueOf(chargeAnim);
        } catch (Exception e) {
            return ChargeAnim.BOW;
        }
    }

    /**
     * 第三人称蓄力姿势：留空 / 非法值 → {@link ThirdPose#FOLLOW}（跟随第一人称动作），
     * 所以老配置的行为不变。
     */
    public ThirdPose thirdPose() {
        return ThirdPose.parse(chargeAnimThird);
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

    /** 手持方向（解析失败回退「竖着拿」= 物品模型自带 display，与老数据行为一致）。 */
    public HoldOrientation holdOrientation() {
        try {
            return HoldOrientation.valueOf(holdOrientation);
        } catch (Exception e) {
            return HoldOrientation.VERTICAL;
        }
    }

    /** 枪械发射按键（解析失败回退右键）。 */
    public FireButton fireButton() {
        try {
            return FireButton.valueOf(fireButton);
        } catch (Exception e) {
            return FireButton.RIGHT;
        }
    }

    /** 射线样式（解析失败回退黄色射线）。 */
    public TracerStyle tracerStyle() {
        try {
            return TracerStyle.valueOf(tracerStyle);
        } catch (Exception e) {
            return TracerStyle.YELLOW;
        }
    }

    /** 手铐耐久消耗形式（解析失败回退「蹲下时减少」）。 */
    public CuffWearMode cuffWearMode() {
        try {
            return CuffWearMode.valueOf(cuffWearMode);
        } catch (Exception e) {
            return CuffWearMode.CROUCH;
        }
    }

    /** 被拷住时的姿势（解析失败回退手铐姿势）。 */
    public CuffPose cuffPose() {
        try {
            return CuffPose.valueOf(cuffPose);
        } catch (Exception e) {
            return CuffPose.CUFFS;
        }
    }

    /** 死亡传递的目标阵营（解析失败回退平民阵营）。 */
    public RoleTeam passOnDeathTeam() {
        try {
            return RoleTeam.valueOf(passOnDeathTeam);
        } catch (Exception e) {
            return RoleTeam.CIVILIAN;
        }
    }

    /** 把「逗号分隔的 id 串」拆成列表（去掉空白与空项）。 */
    public static List<String> splitIds(String csv) {
        List<String> result = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return result;
        }
        for (String part : csv.split(",")) {
            String id = part.trim();
            if (!id.isEmpty()) {
                result.add(id);
            }
        }
        return result;
    }

    /** 完整的展示用标识：{@code customitem:<id>}。 */
    public String getFullIdentifier() {
        return NAMESPACE + ":" + id;
    }

    /**
     * 深拷贝。
     *
     * <p>
     * 编辑界面打开已有条目时必须用拷贝，否则在界面里改 id 会直接改到配置列表里的那个对象，
     * 导致「重名检查把自己算作冲突」而永远提示编号已被占用。
     */
    public CustomItemData copy() {
        try {
            CustomItemData copy = new Gson().fromJson(new Gson().toJson(this), CustomItemData.class);
            if (copy != null) {
                copy.sanitize();
                return copy;
            }
        } catch (Exception ignored) {
        }
        return new CustomItemData();
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
        if (modelPath == null) {
            modelPath = "";
        }
        modelPath = modelPath.trim();
        if (textureMode == null || textureMode.isBlank()) {
            textureMode = TextureMode.PACK.name();
        } else {
            // 手工改过的 JSON 可能写成小写，统一成枚举名
            textureMode = textureMode().name();
        }
        animatedTextures = safeList(animatedTextures);
        animatedModels = safeList(animatedModels);
        tooltip = safeList(tooltip);
        commands = safeList(commands);
        selfCommands = safeList(selfCommands);
        targetCommands = safeList(targetCommands);
        if (emptyFireMessage == null) {
            emptyFireMessage = "";
        }
        emptyFireCooldownTicks = clamp(emptyFireCooldownTicks, 0, 20 * 60 * 10);
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
        if (chargeAnimThird != null && !chargeAnimThird.isBlank()) {
            // 手工改过的 JSON 可能写成小写 / 老的第一人称动作名（EAT / DRINK → 跟随第一人称），统一成枚举名
            chargeAnimThird = thirdPose().name();
        }
        if (targetMode == null || targetMode.isBlank()) {
            targetMode = TargetMode.CIRCLE.name();
        }
        if (holdPose == null || holdPose.isBlank()) {
            holdPose = HoldPose.REVOLVER.name();
        }
        if (holdOrientation == null || holdOrientation.isBlank()) {
            holdOrientation = HoldOrientation.VERTICAL.name();
        } else {
            // 手工改过的 JSON 可能写成小写，统一成枚举名
            holdOrientation = holdOrientation().name();
        }
        // 手持微调：位置最多 ±4 格（64 像素），旋转按整圈收口
        holdOffsetX = clampDouble(holdOffsetX, -64.0D, 64.0D);
        holdOffsetY = clampDouble(holdOffsetY, -64.0D, 64.0D);
        holdOffsetZ = clampDouble(holdOffsetZ, -64.0D, 64.0D);
        holdRotateX = clampDouble(holdRotateX, -360.0D, 360.0D);
        holdRotateZ = clampDouble(holdRotateZ, -360.0D, 360.0D);
        if (fireButton == null || fireButton.isBlank()) {
            fireButton = FireButton.RIGHT.name();
        }
        if (tracerStyle == null || tracerStyle.isBlank()) {
            tracerStyle = TracerStyle.YELLOW.name();
        }
        if (distanceRules == null) {
            distanceRules = new ArrayList<>();
        }
        distanceRules.removeIf(rule -> rule == null);
        for (DistanceRule rule : distanceRules) {
            rule.distance = clampDouble(rule.distance, 0.0D, 256.0D);
            if (rule.command == null) {
                rule.command = "";
            }
        }
        if (fireSound == null || fireSound.isBlank()) {
            fireSound = DEFAULT_FIRE_SOUND;
        }
        if (dropOnlyRole == null) {
            dropOnlyRole = "";
        }
        dropOnlyRole = dropOnlyRole.trim();
        if (passOnDeathRole == null) {
            passOnDeathRole = "";
        }
        passOnDeathRole = passOnDeathRole.trim();
        if (passOnDeathTeam == null || passOnDeathTeam.isBlank()) {
            passOnDeathTeam = RoleTeam.CIVILIAN.name();
        }
        if (useOnlyRoles == null) {
            useOnlyRoles = "";
        }
        useOnlyRoles = useOnlyRoles.trim();
        if (useOnlyModifiers == null) {
            useOnlyModifiers = "";
        }
        useOnlyModifiers = useOnlyModifiers.trim();
        useOnlyTeams = safeList(useOnlyTeams);
        useOnlyTeams.removeIf(team -> team == null || team.isBlank());
        if (lethalDeathReason == null || lethalDeathReason.isBlank()) {
            lethalDeathReason = GameConstants.DeathReasons.REVOLVER.toString();
        }
        // 老配置的「命中是否致死」= 触发最终效果时致死：并入 lethalOnFinal（两个新开关都关着时才迁移）
        if (legacyLethalOnHit != null) {
            if (legacyLethalOnHit && !lethalOnRayHit && !lethalOnFinal) {
                lethalOnFinal = true;
            }
            legacyLethalOnHit = null;
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
        hitMarkerTicks = clamp(hitMarkerTicks, 1, 20 * 60 * 10);
        animatedFrameTicks = clamp(animatedFrameTicks, 1, 20 * 60);
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
        durability = clamp(durability, 0, 100000);

        // 手铐
        cuffCommandIntervalTicks = clamp(cuffCommandIntervalTicks, 1, 20 * 60 * 10);

        // 投掷物
        throwPinTicks = clamp(throwPinTicks, 1, 20 * 60);
        throwStickTicks = clamp(throwStickTicks, 1, 20 * 60 * 10);
        throwDefuseTicks = clamp(throwDefuseTicks, 0, 20 * 60 * 10);
        throwDelaySeconds = clampDouble(throwDelaySeconds, 0.0, 600.0);
        throwRadius = clampDouble(throwRadius, 0.5, 64.0);
        throwWallIgnoreBlocks = clamp(throwWallIgnoreBlocks, 1, 16);
        throwParticleAreaTicks = clamp(throwParticleAreaTicks, 1, 20 * 60 * 10);
        throwAreaDurationSeconds = clamp(throwAreaDurationSeconds, 1, 600);
        throwAreaStayTicks = clamp(throwAreaStayTicks, 1, 20 * 60 * 10);

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

    /** 材质来源（单选）：三种来源相互独立，只生效当前选中的那一种。 */
    public enum TextureMode {
        /**
         * 资源包贴图：{@link #packTexturePath}（材质地址）指到一张 PNG，画成平面（前后两层 = 1 像素厚）。
         */
        PACK,
        /**
         * 导入动态贴图：{@link #animatedTextures}（材质地址，帧数不限）按
         * {@link #animatedFrameTicks} 循环播放。
         */
        ANIMATED,
        /**
         * 导入立体模型：{@link #modelPath}（模型地址）指向资源包里的模型 json，
         * 直接渲染该模型（立体 elements / 图集动画贴图都跟着走）；
         * 模型地址留空时退回 {@link #inheritItemTexture}（借某个物品的模型）。
         */
        MODEL,
        /**
         * 继承现有物品贴图：{@link #inheritItemTexture} 填一个物品 id，
         * 直接借用该物品的<b>模型与材质</b>（拿在手上 / 背包里和它长得一模一样）。
         */
        INHERIT
    }

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
        FOOD,
        /** 手铐类物品：右键玩家，把物品铐进目标玩家的特殊栏位。 */
        CUFF,
        /** 投掷物：右键投出，落地 / 粘附后按配置生效。 */
        THROWABLE;

        /** 是否与「弹药 / 命中计数」这类物品自身状态有关（用于保存 AMMO_COUNT）。 */
        public boolean requiresAmmo() {
            return this == GUN;
        }
    }

    /** 手铐类物品的耐久消耗形式（单选）。 */
    public enum CuffWearMode {
        /** 玩家蹲下时减少耐久（同手铐）。 */
        CROUCH,
        /** 玩家被穿戴时每秒扣除耐久（无论是否蹲下）。 */
        WORN,
        /** 玩家移动时消耗耐久（走路与疾跑都算）。 */
        MOVE,
        /** 不消耗耐久（只能被其它玩家取下）。 */
        NONE
    }

    /**
     * 被拷住时的姿势（单选）。
     *
     * <p>
     * 全部对应项目里已有的姿势实现，客户端按该值套用对应预设：
     * {@link #CUFFS} = 手铐姿势，{@link #SIT}/{@link #TREMBLE} = 恐惧坐姿与发抖，
     * {@link #LIMP} = 腿瘸，{@link #ORA} = 双臂前伸乱挥，{@link #HOLD} = 持枪举起，
     * {@link #SWIM} = 游泳姿势。
     */
    public enum CuffPose {
        /** 手铐姿势（双臂相交于身后，默认）。 */
        CUFFS,
        /** 不改变姿势。 */
        NONE,
        /** 坐下。 */
        SIT,
        /** 发抖。 */
        TREMBLE,
        /** 腿瘸。 */
        LIMP,
        /** 双臂前伸乱挥。 */
        ORA,
        /** 持枪举起。 */
        HOLD,
        /** 游泳姿势。 */
        SWIM
    }

    /** 非特定职业拆除失败概率规则。 */
    public static class DefuseFailRule {
        /** 职业 id（留空表示「其它所有职业」）。 */
        @SerializedName("roleId")
        public String roleId = "";
        /** 拆除失败概率（百分比，0~100）。 */
        @SerializedName("failPercent")
        public int failPercent = 0;
    }

    /** 一条「药水效果」（手铐 / 投掷物共用）。 */
    public static class EffectData {
        /** 效果 id，如 {@code minecraft:speed}。 */
        @SerializedName("effectId")
        public String effectId = "";
        /** 0 = 等级 I。 */
        @SerializedName("amplifier")
        public int amplifier = 0;
        /** 持续时间（秒）；手铐的持续时间固定到解除为止，该字段在那种用途下被忽略。 */
        @SerializedName("durationSeconds")
        public int durationSeconds = 10;
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

    /**
     * 蓄力物品的<b>第三人称</b>手臂姿势（{@link #chargeAnimThird} 用）。
     *
     * <p>
     * 一一对应原版 {@code HumanoidModel.ArmPose}，所以选项比第一人称那栏
     * （{@link ChargeAnim}，只能映射到原版 {@code UseAnim} 有的几种）更多，
     * 多出「端弩瞄准」「举望远镜」「吹号角」。
     */
    public enum ThirdPose {
        /** 跟随第一人称动作（默认，留空即此值）。 */
        FOLLOW,
        /** 原版默认手持（没有特殊手臂姿势）。 */
        NONE,
        /** 拉弓：双手举弓。 */
        BOW,
        /** 举矛：抬臂举矛（小刀 / 拳套同款）。 */
        SPEAR,
        /** 弩蓄力：双手端弩（带蓄力进度）。 */
        CROSSBOW,
        /** 端弩瞄准：像端着上膛的弩。 */
        CROSSBOW_HOLD,
        /** 举盾格挡。 */
        BLOCK,
        /** 刷子：抬臂擦拭（御币同款）。 */
        BRUSH,
        /** 举望远镜。 */
        SPYGLASS,
        /** 吹号角。 */
        TOOT_HORN;

        /** 解析配置字符串（大小写不敏感）。空 / 非法值回退 {@link #FOLLOW}。 */
        public static ThirdPose parse(String value) {
            if (value == null || value.isBlank()) {
                return FOLLOW;
            }
            for (ThirdPose pose : values()) {
                if (pose.name().equalsIgnoreCase(value.trim())) {
                    return pose;
                }
            }
            return FOLLOW;
        }
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

    /**
     * 手持方向（第一 / 第三人称的朝向，{@link #holdOrientation} 用）。
     *
     * <p>
     * 物品模型 {@code custom_item.json} 的 display 取自原版 {@code item/handheld}（工具 / 剑那套），
     * 所以默认看起来是「竖着拿」；选「横着拿」会抵掉那套旋转与偏移，换成原版普通物品
     * （{@code item/generated}，苹果那类）的姿态。
     */
    public enum HoldOrientation {
        /** 竖着拿：用物品模型自带 display（原版 {@code item/handheld}），像工具 / 剑那样立着。 */
        VERTICAL,
        /** 横着拿：换成原版普通物品（{@code item/generated}，苹果那类）的手持姿态。 */
        HORIZONTAL
    }

    /** 枪械发射按键（默认右键，可切换为左键）。 */
    public enum FireButton {
        /** 右键发射（默认，同左轮手枪）。 */
        RIGHT,
        /** 左键发射（同狙击枪：左键即开火）。 */
        LEFT
    }

    /** 枪械射线样式（仅在「显示枪械射线」开启时生效）。 */
    public enum TracerStyle {
        /** 目前的琥珀色轨迹线（默认）。 */
        YELLOW,
        /** 狙击枪发射时的表现：轨迹线之外再沿弹道生成烟雾。 */
        SNIPER
    }

    /** 距离检测规则：命中「distance」格以外的玩家时，对其执行 command。 */
    public static class DistanceRule {
        /** 触发距离（格）：命中距离 ≥ 该值的玩家时生效。 */
        @SerializedName("distance")
        public double distance = 50.0D;
        /** 被击中的玩家（{@code <player>}）执行的指令。 */
        @SerializedName("command")
        public String command = "";
    }
}
