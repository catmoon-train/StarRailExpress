package org.agmas.noellesroles.role.bouns.roles;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.AreasSettingUtils.MapSpecialFeatures;
import io.wifi.starrailexpress.api.EggRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.PlayerStaminaGetter;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.api.ClimbState;
import org.agmas.noellesroles.api.PlayerClimbState;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.bouns.BounsRoles;
import org.agmas.noellesroles.role_data.innocence.ClimbPoseRoleData;

import java.util.Arrays;

/**
 * 墙壁攀爬实现（参考 PEAK）：童子军、森蕈僵尸、童子军队长，以及任何
 * {@code SRERole#setCanClimbWalls(true)} 的职业共用这一份逻辑。
 *
 * <p><b>职业侧只需要打开开关</b>：{@code role.setCanClimbWalls(true)}；
 * 想按玩家状态 / 地图动态判断就覆写 {@code canClimbWalls(Player)}（例如冒险家只在 PEAK 爬山图上能爬）。
 *
 * <p>攀爬的运行状态**不在 RoleData 里**，而是像体力条那样挂在 Player 自己身上
 * （{@link PlayerClimbState} / {@link ClimbState}，见 {@code PlayerClimbStateMixin}），
 * 所以任何职业都不必为了攀爬去绑定 RoleData。
 * 只有「动作姿态」技能还需要 RoleData（{@link ClimbPoseRoleData}）。
 *
 * <p>核心规则：
 * <ol>
 * <li><b>开始攀爬</b>：空手右键一面紧贴着的、有碰撞箱的墙（空格不是触发方式，避免误触发）；</li>
 * <li><b>攀爬消耗体力</b>（复用现有冲刺体力 {@link PlayerStaminaGetter}）：
 * 悬挂/下降最省、左右移动居中、向上最费；体力见底立刻脱手；</li>
 * <li><b>换墙</b>：原来那面墙到头时不会直接掉下去，先看身边是不是还有别的墙可以抓
 * （拐角的两面墙、柱子、矮墙后面紧跟着的高墙台阶），抓到就换过去继续爬，
 * 见 {@link #findHuggedWall}；真的一面墙都没有了才脱手；</li>
 * <li><b>脱手</b>：潜行键主动松手、空格直接取消攀爬（不向外弹开）、手上拿到物品、离开墙面自动结束；</li>
 * <li><b>音效</b>：抓上墙播装备音（pitch 0）、松手播玩家轻落地音，由服务端在权威状态切换时放给附近所有人；</li>
 * <li><b>动作技能</b>：把 {@code player.setPose} 切成站立 / 匍匐两种姿态。</li>
 * </ol>
 *
 * <p>移动本身由客户端负责（原版玩家位移就是客户端权威的），
 * 见 {@code ScoutClimbTravelMixin}；服务端负责权威状态、扣体力、无重力维护与姿态同步。
 */
public class ScoutRole extends EggRole {

    // ==================== 数值 ====================

    /** 攀爬速度（格 / tick，尚未计入空气摩擦） */
    public static final double CLIMB_SPEED = 0.16D;

    /** 判定「这一 tick 真的在动」的位移阈值（格） */
    public static final double CLIMB_MOVE_EPSILON = 0.012D;

    /** 垂直 / 水平位移比例超过这个值才认为是在「向上爬」或「向下爬」 */
    public static final double VERTICAL_BIAS = 0.35D;

    /**
     * 悬挂不动 / 向下：最省体力（每 tick 净消耗）。
     * <p>数值以「疾跑」为参照：原版疾跑是 1.0 / tick，所以攀爬整体压到和奔跑差不多，
     * 其中向上略贵、左右持平、悬挂减半。
     */
    public static final float DRAIN_MIN = 0.50F;
    /** 左右移动：和疾跑基本一致（每 tick 净消耗） */
    public static final float DRAIN_SIDE = 1.00F;
    /** 向上攀爬：略高于疾跑（每 tick 净消耗） */
    public static final float DRAIN_UP = 1.30F;

    /** 原版 {@code PlayerEntityMixin.tmm$limitSprint} 里非冲刺状态的基础恢复量 */
    public static final float VANILLA_STAMINA_RECOVERY = 0.4F;

    /**
     * 「紧贴」允许的最大缝隙（格）。
     * 玩家包围盒表面到墙面的空隙超过这个值就抓不住、攀爬中也会自动脱手——
     * 也就是必须真的把身体贴在墙上。
     */
    private static final double WALL_HUG_TOLERANCE = 0.06D;

    /**
     * 贴墙射线的高度（相对脚底）。
     *
     * <p>取在身体下部，玩家就能一直爬到「脚已经高过墙顶」才判定脱手，
     * 紧接着由 {@link #tryClimbOntoBlock} 把他抬上墙顶 —— 这是「能爬上/翻上墙顶方块」的关键：
     * 如果用身体中心的射线，玩家在脚还给墙顶差大半格时就会脱手，永远上不去。
     */
    private static final double WALL_RAY_HEIGHT = 0.25D;

    /**
     * 翻上墙顶时朝墙里挪的水平距离（格，从**身体中心**算起）。
     *
     * <p>取 0.99 会让玩家落到墙顶靠里的一侧、身体背面几乎贴上后面那一面墙
     * （紧贴判定的容差是 {@link #WALL_HUG_TOLERANCE}，0.99 刚好落在里面）：
     * <ul>
     * <li>台阶式攀爬（一格矮墙后面紧跟着一格更高的墙）能直接续着往上爬，不用松手重新右键；</li>
     * <li>留的那 0.01 格是防止包围盒和后面那面墙擦边判定成碰撞，把翻上墙顶整个搞失败；</li>
     * <li>后面是同一高度的方块（两格厚的墙顶）时也只是站上去，射线打不到墙就自动结束。</li>
     * </ul>
     */
    private static final double CLIMB_OVER_FORWARD = 0.99D;
    /** 翻上墙顶时允许的最大抬升高度（格） */
    private static final double CLIMB_OVER_MAX_UP = 1.25D;

    /** {@code 1 / sqrt(2)}：斜向探测方向的水平分量 */
    private static final double SQRT_HALF = Math.sqrt(0.5D);

    /**
     * 找墙用的水平探测方向：4 个正面 + 4 个斜向。
     *
     * <p>斜向是给「包围盒角顶在墙上」用的（贴墙角时正面射线可能正好从两块墙的缝里穿过去）。
     * 顺序只是默认顺序，{@link #findHuggedWall} 会按与当前墙面法线的接近程度重排。
     */
    private static final Vec3[] WALL_PROBE_DIRECTIONS = {
            new Vec3(1.0D, 0.0D, 0.0D), new Vec3(-1.0D, 0.0D, 0.0D),
            new Vec3(0.0D, 0.0D, 1.0D), new Vec3(0.0D, 0.0D, -1.0D),
            new Vec3(SQRT_HALF, 0.0D, SQRT_HALF), new Vec3(SQRT_HALF, 0.0D, -SQRT_HALF),
            new Vec3(-SQRT_HALF, 0.0D, SQRT_HALF), new Vec3(-SQRT_HALF, 0.0D, -SQRT_HALF),
    };

    // ==================== 音效 ====================

    /** 抓上墙：装备音（{@code item.armor.equip_generic}） */
    public static final SoundEvent GRAB_SOUND = SoundEvents.ARMOR_EQUIP_GENERIC.value();
    /** 松手 / 脱手：玩家轻落地音（{@code entity.player.small_fall}） */
    public static final SoundEvent RELEASE_SOUND = SoundEvents.PLAYER_SMALL_FALL;
    /** 抓上墙的音量与音高（pitch 0 = 让原版压到最低音，闷一点） */
    private static final float GRAB_SOUND_VOLUME = 0.8F;
    private static final float GRAB_SOUND_PITCH = 0.0F;
    /** 松手的音量与音高 */
    private static final float RELEASE_SOUND_VOLUME = 0.8F;
    private static final float RELEASE_SOUND_PITCH = 1.0F;

    /**
     * 抓墙 / 松手的提示音，放给附近所有人听（和开枪一样属于「世界里的动静」）。
     *
     * <p>只在服务端的权威状态切换时播（{@link #startClimb} / {@link #stopClimb}），
     * 所以客户端本地预判但被服务端拒绝时不会有假提示。
     */
    private static void playClimbSound(ServerPlayer player, boolean grabbing) {
        Level level = player.level();
        if (level == null) {
            return;
        }
        level.playSound(null, player.getX(), player.getY() + 0.9D, player.getZ(),
                grabbing ? GRAB_SOUND : RELEASE_SOUND, SoundSource.PLAYERS,
                grabbing ? GRAB_SOUND_VOLUME : RELEASE_SOUND_VOLUME,
                grabbing ? GRAB_SOUND_PITCH : RELEASE_SOUND_PITCH);
    }

    public ScoutRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    // ==================== 判定 ====================

    /**
     * 该玩家此刻是否被允许攀爬。
     *
     * <p>完全交给职业自己的 {@link SRERole#canClimbWalls(Player)} 决定：
     * 默认只看 {@code setCanClimbWalls} 的开关，职业也可以覆写成「按玩家状态 / 当前地图判断」
     * —— 例如冒险家只在 PEAK 爬山图上能爬。
     *
     * <p>两端都会调用（攀爬状态两端各自维护、不额外同步），所以这里用到的信息两端都要有：
     * 地图特性已经随 {@code AreasWorldComponent} 同步到客户端，{@link #isPeakMap} 两端都能算。
     */
    public static boolean roleAllowsClimb(Player player) {
        SRERole role = roleOf(player);
        return role != null && role.canClimbWalls(player);
    }

    /**
     * 当前地图是不是 PEAK 爬山图。两端都可以判断：
     * 地图自身声明的特性（{@code AreasSettings.customMapFeatures}）已经随
     * {@link AreasWorldComponent} 同步到客户端。
     */
    public static boolean isPeakMap(Level level) {
        if (level == null) {
            return false;
        }
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        if (areas == null || areas.mapName == null || areas.areasSettings == null) {
            return false;
        }
        return SRERole.getMapFeatures(areas.mapName, areas.areasSettings).contains(MapSpecialFeatures.PEAK);
    }

    public static SRERole roleOf(Player player) {
        if (player == null || player.level() == null) {
            return null;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        return game == null ? null : game.getRole(player);
    }

    // ==================== 墙面探测 ====================

    /**
     * 这个方块的这一面能不能攀爬。
     *
     * <p>要求：面是竖直的、方块有碰撞箱（用户要求），并且不是门 / 活板门 / 栅栏门 / 按钮
     * 这类「右键是用来交互」的方块 —— 免得想开门却抓上了墙。
     */
    public static boolean isClimbableWallBlock(Level level, BlockPos pos, BlockState state, Direction face) {
        if (face.getAxis() == Direction.Axis.Y) {
            return false;
        }
        if (state.getCollisionShape(level, pos).isEmpty()) {
            return false;
        }
        return !state.is(BlockTags.DOORS) && !state.is(BlockTags.TRAPDOORS)
                && !state.is(BlockTags.FENCE_GATES) && !state.is(BlockTags.BUTTONS);
    }

    /**
     * 沿 {@code dir} 从身体中心探一小段，看是不是顶着一面能攀爬的墙。
     *
     * <p>探测长度按方向自适应：玩家包围盒截面近似正方形，中心沿单位方向 {@code dir}
     * 到包围盒表面的距离就是 {@code 半宽 / max(|dx|,|dz|)}（正面 = 半宽，斜向 = 半宽 × √2），
     * 再加一个 {@link #WALL_HUG_TOLERANCE} 的容差 —— 所以「必须紧贴」对斜向一样成立。
     *
     * @return 命中可攀爬墙面时返回命中结果，否则 {@code null}
     */
    private static BlockHitResult probeWall(Player player, Vec3 dir) {
        Level level = player.level();
        if (level == null || dir == null) {
            return null;
        }
        double flatLength = Math.max(Math.abs(dir.x), Math.abs(dir.z));
        if (flatLength < 1.0E-6D) {
            return null;
        }
        double reach = player.getBbWidth() * 0.5D / flatLength + WALL_HUG_TOLERANCE;
        // 射线取在身体下部（见 WALL_RAY_HEIGHT）：一路爬到脚高过墙顶才判定脱手
        Vec3 from = player.position().add(0.0D, WALL_RAY_HEIGHT, 0.0D);
        BlockHitResult hit = level.clip(new ClipContext(from, from.add(dir.scale(reach)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos pos = hit.getBlockPos();
        return isClimbableWallBlock(level, pos, level.getBlockState(pos), hit.getDirection()) ? hit : null;
    }

    /**
     * 玩家是否**紧贴**着这面墙（法线由墙指向玩家，水平单位向量）。
     *
     * <p>判定方式：从身体中心沿「指向墙」的方向做一条短射线，长度正好是
     * {@code 包围盒半宽 + WALL_HUG_TOLERANCE}。贴着墙时包围盒表面正好落在墙面上，
     * 射线必然穿进墙里；离墙超过容差就够不着 —— 所以这是「必须紧贴」的判定，
     * 开始攀爬和攀爬中的维持都用它。
     */
    public static boolean isHuggingWall(Player player, Vec3 normal) {
        if (player == null || normal == null) {
            return false;
        }
        Vec3 flat = new Vec3(normal.x, 0.0D, normal.z);
        if (flat.lengthSqr() < 1.0E-6D) {
            return false;
        }
        // 墙在「中心 - 法线」方向上
        return probeWall(player, flat.normalize().scale(-1.0D)) != null;
    }

    /**
     * 找出玩家此刻正贴着的墙（攀爬中「换一面墙」用）。
     *
     * <p>拐角、台阶、柱子都会让「原来那面墙」的射线先失效，而旁边其实还有墙可以抓；
     * 这个方法把身边一圈墙面都探一遍，命中优先级是「与 {@code prefer}（当前攀爬的墙）
     * 夹角越小越先试」：先原方向，再垂直的侧墙，最后才是背后的对墙，斜向穿插其间。
     *
     * <p>返回的是**方块面的法线**（轴对齐的水平单位向量），所以即使靠斜向探测命中，
     * 拿到的也是和原来一样规整的法线，能直接交给 {@link #tryClimbOntoBlock} 等逻辑。
     *
     * @return 贴着的那面墙的法线（由墙指向玩家），一面都没贴到返回 {@code null}
     */
    public static Vec3 findHuggedWall(Player player, Vec3 prefer) {
        if (player == null) {
            return null;
        }
        Vec3 current = null;
        if (prefer != null && prefer.x * prefer.x + prefer.z * prefer.z > 1.0E-6D) {
            current = new Vec3(prefer.x, 0.0D, prefer.z).normalize();
        }
        Vec3[] candidates = WALL_PROBE_DIRECTIONS.clone();
        if (current != null) {
            final Vec3 ref = current;
            // 稳定排序：点积大的（更接近当前墙面）先试
            Arrays.sort(candidates, (a, b) -> Double.compare(b.dot(ref), a.dot(ref)));
        }
        for (Vec3 dir : candidates) {
            BlockHitResult hit = probeWall(player, dir);
            if (hit != null) {
                return Vec3.atLowerCornerOf(hit.getDirection().getNormal());
            }
        }
        return null;
    }

    /**
     * 攀爬上岸 / 爬上墙顶：在紧贴的墙顶找一个能站住的位置，把玩家抬上去。
     *
     * <p>调用时机：正在向上爬、并且已经爬到「脚高过墙顶」而判定脱手的那一刻
     * （见 {@link #isHuggingWall} 里射线高度的说明）。成功后玩家就站在墙顶方块上，
     * 形如「玩家[方块] --攀爬--> 玩家 / [方块]」。
     *
     * <p>位置由客户端计算：原版玩家位移本来就是客户端权威，服务端不需要再算一次。
     *
     * @return 是否成功翻上墙顶
     */
    public static boolean tryClimbOntoBlock(Player player, Vec3 normal) {
        Level level = player.level();
        if (level == null || normal == null) {
            return false;
        }
        Vec3 flat = new Vec3(normal.x, 0.0D, normal.z);
        if (flat.lengthSqr() < 1.0E-6D) {
            return false;
        }
        flat = flat.normalize();
        // 目标点：朝墙里挪一段，正好落在墙顶方块上
        Vec3 base = player.position().add(flat.scale(-CLIMB_OVER_FORWARD));
        for (double dy = 0.0D; dy <= CLIMB_OVER_MAX_UP; dy += 0.25D) {
            Vec3 target = base.add(0.0D, dy, 0.0D);
            if (!hasStandableSupport(level, target)) {
                continue;
            }
            AABB box = player.getBoundingBox().move(target.x - player.getX(), target.y - player.getY(),
                    target.z - player.getZ());
            if (!level.noCollision(player, box)) {
                continue;
            }
            player.teleportTo(target.x, target.y, target.z);
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
            player.setNoGravity(false);
            return true;
        }
        return false;
    }

    /** 目标位置下方是否踩着实体方块（能站住） */
    private static boolean hasStandableSupport(Level level, Vec3 pos) {
        BlockPos below = BlockPos.containing(pos.x, pos.y - 0.05D, pos.z);
        return !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }

    // ==================== 移动与体力 ====================

    /**
     * 客户端每 tick 的攀爬位移：沿视线方向（前后）与水平左手方向（左右）合成，
     * 即「抬头 + 前进 = 向上爬」。没有按键输入时返回 {@link Vec3#ZERO}（悬挂不动）。
     *
     * @param input 原版 travel 收到的输入向量（x = 左移，z = 前进）
     */
    public static Vec3 climbVelocity(Player player, Vec3 input) {
        double forward = input.z;
        double strafe = input.x;
        if (Math.abs(forward) < 1.0E-4D && Math.abs(strafe) < 1.0E-4D) {
            return Vec3.ZERO;
        }
        Vec3 look = player.getLookAngle();
        double yaw = Math.toRadians(player.getYRot());
        // 原版 getInputVector 的映射：左移正方向 = (cos yaw, 0, sin yaw)
        Vec3 left = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw));
        Vec3 dir = look.scale(forward).add(left.scale(strafe));
        if (dir.lengthSqr() < 1.0E-8D) {
            return Vec3.ZERO;
        }
        return dir.normalize().scale(CLIMB_SPEED);
    }

    /**
     * 按这一 tick 的真实位移判断动作，给出净体力消耗。
     * 向上 &gt; 左右 &gt; 不动 / 向下。
     *
     * <p>用「垂直分量占水平分量的比例」判断，而不是绝对位移量：轻微抬头往前爬
     * 仍然算左右，只有明显朝上 / 朝下才会切换到对应档位。
     */
    public static float climbDrain(double dx, double dy, double dz) {
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        boolean moving = horizontal > CLIMB_MOVE_EPSILON || Math.abs(dy) > CLIMB_MOVE_EPSILON;
        if (!moving) {
            return DRAIN_MIN; // 悬挂不动
        }
        double verticalRatio = horizontal < 1.0E-4D ? Double.MAX_VALUE : Math.abs(dy) / horizontal;
        if (dy > 0.0D && verticalRatio > VERTICAL_BIAS) {
            return DRAIN_UP;
        }
        if (dy < 0.0D && verticalRatio > VERTICAL_BIAS) {
            return DRAIN_MIN; // 向下和不动一样最省
        }
        return DRAIN_SIDE;
    }

    /**
     * 攀爬中每 tick 都要重申的物理状态：无重力、不疾跑、清坠落距离。
     *
     * <p>无重力既支撑贴墙悬停，也让服务端不会因为「浮空太久」把人踢下线
     * （{@code ServerGamePacketListenerImpl#getMaximumFlyingTicks} 在
     * {@code Entity#getGravity() == 0} 时返回 {@code Integer.MAX_VALUE}）。
     */
    public static void applyClimbPhysics(Player player) {
        player.setNoGravity(true);
        player.setSprinting(false);
        player.resetFallDistance();
    }

    /** 玩家当前体力；未初始化（-1）视为满 */
    public static float staminaOf(Player player) {
        if (!(player instanceof PlayerStaminaGetter stamina)) {
            return Integer.MAX_VALUE;
        }
        float current = stamina.starrailexpress$getStamina();
        if (current < 0f) {
            return maxStaminaOf(player);
        }
        return current;
    }

    /**
     * 该玩家当前的体力上限（含体力上限效果）；不受体力限制的职业 / 无限体力返回
     * {@link Float#MAX_VALUE}。
     */
    public static float maxStaminaOf(Player player) {
        SRERole role = roleOf(player);
        if (role == null) {
            return Float.MAX_VALUE;
        }
        int max = role.getMaxSprintTime(player);
        if (max < 0 || max == Integer.MAX_VALUE) {
            return Float.MAX_VALUE;
        }
        return max * ModEffects.getStaminaCapacityMultiplier(player);
    }

    /** 是否还允许开始攀爬（体力必须大于 0，不受体力限制的职业/效果直接放行） */
    public static boolean hasClimbStamina(Player player) {
        if (player == null) {
            return false;
        }
        if (ModEffects.hasInfiniteStamina(player)) {
            return true;
        }
        SRERole role = roleOf(player);
        if (role == null) {
            return false;
        }
        int max = role.getMaxSprintTime(player);
        if (max < 0 || max == Integer.MAX_VALUE) {
            return true;
        }
        return staminaOf(player) > 0f;
    }

    /**
     * 扣掉这一 tick 的攀爬体力。
     *
     * <p>原版 {@code aiStep} 在非冲刺状态下每 tick 会恢复
     * {@code 0.4 * 恢复倍率}，所以这里把「净消耗」再叠上这段恢复量，
     * 保证净结果与配置的数值一致。
     *
     * @return 扣完之后还有体力则为 {@code true}
     */
    public static boolean consumeClimbStamina(Player player, float netDrain) {
        if (player == null || ModEffects.hasInfiniteStamina(player)) {
            return true;
        }
        SRERole role = roleOf(player);
        if (role == null) {
            return false;
        }
        int max = role.getMaxSprintTime(player);
        if (max < 0 || max == Integer.MAX_VALUE) {
            return true;
        }
        if (!(player instanceof PlayerStaminaGetter stamina)) {
            return true;
        }
        float recovery = VANILLA_STAMINA_RECOVERY * ModEffects.getStaminaRecoveryMultiplier(player);
        float current = stamina.starrailexpress$getStamina();
        if (current < 0f) {
            current = max;
        }
        float next = Math.max(0f, current - (netDrain + recovery));
        stamina.starrailexpress$setStamina(next);
        return next > 0f;
    }

    // ==================== 服务端状态 ====================

    /** 服务端开始攀爬：写入状态、上无重力、采样起点（调用方负责校验） */
    public static void startClimb(ServerPlayer player, ClimbState state, Vec3 normal) {
        state.climbing = true;
        state.normal = normal;
        state.markPosition(player.getX(), player.getY(), player.getZ());
        applyClimbPhysics(player);
        playClimbSound(player, true);
    }

    /** 服务端结束攀爬：清状态与无重力（真的从「攀爬中」结束才会放松手音） */
    public static void stopClimb(ServerPlayer player, ClimbState state) {
        boolean wasClimbing = state.climbing;
        state.climbing = false;
        state.hasLastPos = false;
        if (player != null) {
            player.setNoGravity(false);
            player.resetFallDistance();
            if (wasClimbing) {
                playClimbSound(player, false);
            }
        }
    }

    /** 服务端每 tick 维护攀爬状态（全局 tick 里只对正在攀爬的玩家调用） */
    public static void tickClimb(ServerPlayer player, ClimbState state) {
        if (!roleAllowsClimb(player) || !GameUtils.isPlayerAliveAndSurvival(player)) {
            stopClimb(player, state);
            return;
        }
        if (!player.getMainHandItem().isEmpty()) {
            // 抓墙必须空手：手上拿到物品就自动松手
            stopClimb(player, state);
            return;
        }
        applyClimbPhysics(player);

        double[] move = state.pollMovement(player.getX(), player.getY(), player.getZ());
        // 一 tick 挪了两格以上只可能是被传送 / 会议复位，不按动作扣体力，直接判定为离开墙面
        if (move[0] * move[0] + move[1] * move[1] + move[2] * move[2] > 4.0D) {
            stopClimb(player, state);
            return;
        }
        float drain = climbDrain(move[0], move[1], move[2]);
        if (!consumeClimbStamina(player, drain)) {
            stopClimb(player, state);
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.climb.stamina_out").withStyle(ChatFormatting.RED),
                    true);
        }
    }

    /** 处理客户端的攀爬开始 / 结束请求（服务端二次校验） */
    public static void handleClimbPacket(ServerPlayer player, boolean start, Vec3 normal) {
        ClimbState state = PlayerClimbState.of(player);
        if (state == null) {
            return;
        }
        if (!start) {
            stopClimb(player, state);
            return;
        }
        if (state.climbing) {
            // 已经在攀爬：又收到一次 start 只可能是客户端「换了另一面墙」
            // （拐角 / 台阶），校验一下确实贴着墙再更新法线，别的一概不动
            if (normal != null && normal.lengthSqr() > 1.0E-6D && isHuggingWall(player, normal)) {
                state.normal = normal;
            }
            return;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (game == null || !game.isRunning()) {
            return;
        }
        if (!roleAllowsClimb(player) || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }
        if (!player.getMainHandItem().isEmpty()) {
            // 抓墙必须空手
            return;
        }
        if (normal == null || normal.lengthSqr() < 1.0E-6D) {
            return;
        }
        if (!isHuggingWall(player, normal)) {
            return;
        }
        if (!hasClimbStamina(player)) {
            return;
        }
        startClimb(player, state, normal);
    }

    // ==================== 技能：切换动作姿态 ====================

    /**
     * 注册攀爬相关的全局逻辑与「动作」技能。
     *
     * <p>攀爬状态挂在 Player 上、不再由 RoleData 驱动，所以服务端需要一个全局 tick
     * 来维护「正在攀爬的玩家」；反过来说，没在攀爬的玩家这里不做任何事。
     */
    public static void registerEvents() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ClimbState state = PlayerClimbState.of(player);
                if (state == null || !state.climbing) {
                    continue;
                }
                SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
                if (game == null || !game.isRunning()) {
                    stopClimb(player, state);
                    continue;
                }
                tickClimb(player, state);
            }
        });

        // 注意：同一个职业的技能必须一次性注册完（RoleSkill.register 会覆盖前一次）
        registerPoseSkills(BounsRoles.SCOUT, "scout");
        registerPoseSkills(BounsRoles.FOREST_MUSHROOM_ZOMBIE, "forest_mushroom_zombie");
        registerPoseSkills(BounsRoles.SCOUT_CAPTAIN, "scout_captain");
    }

    private static void registerPoseSkills(SRERole role, String path) {
        RoleSkill.register(role,
                poseSkill(path + "_pose_stand", "skill.noellesroles.climb.pose_stand",
                        ClimbPoseRoleData.POSE_DEFAULT),
                poseSkill(path + "_pose_prone", "skill.noellesroles.climb.pose_prone",
                        ClimbPoseRoleData.POSE_PRONE));
    }

    private static RoleSkill.Definition poseSkill(String skillId, String nameKey, int poseId) {
        // 提示文案由 setPose() 自己发（带姿态名），这里关掉默认播报避免刷两条
        return RoleSkill.skill(SRE.id(skillId), nameKey, ctx -> setPose(ctx, poseId))
                .showOnHud(true)
                .announceToSelf(false)
                .build();
    }

    public static boolean setPose(RoleSkill.RoleSkillContext ctx, int poseId) {
        ServerPlayer player = ctx.player();
        if (player.isSpectator()) {
            return false;
        }
        ClimbPoseRoleData data = RoleData.getNullable(ClimbPoseRoleData.class, player);
        if (data == null) {
            return false;
        }
        data.setPoseId(poseId);
        player.displayClientMessage(Component.translatable(
                "message.noellesroles.climb.pose_changed",
                Component.translatable(poseNameKey(poseId))).withStyle(ChatFormatting.GREEN), true);
        return true;
    }

    public static String poseNameKey(int poseId) {
        return poseId == ClimbPoseRoleData.POSE_PRONE
                ? "skill.noellesroles.climb.pose_prone"
                : "skill.noellesroles.climb.pose_stand";
    }
}
