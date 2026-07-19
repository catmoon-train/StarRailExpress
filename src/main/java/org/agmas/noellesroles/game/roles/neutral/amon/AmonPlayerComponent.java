package org.agmas.noellesroles.game.roles.neutral.amon;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.PlayerBodyEntityComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.content.item.CocktailItem;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.network.TriggerStatusBarPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoneyBottleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.AmonFinaleS2CPacket;
import org.agmas.noellesroles.packet.AmonSkinS2CPacket;
import org.agmas.noellesroles.packet.BroadcastMessageS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;
import pro.fazeclan.river.stupid_express.constants.SEItems;

import java.util.*;

/**
 * 阿蒙（诡秘之主）—— 中立独立胜利角色，核心机制为「寄生」。
 *
 * <p>
 * 阿蒙隐秘地给附近活人种下「时之虫」（受害者无感），时之虫潜伏成熟后该宿主成为可「夺舍」的载体。
 * 阿蒙可主动夺舍，也会在受到致命伤时自动夺舍续命（{@link AmonEventHandler}）；只有在没有成熟宿主时才会真正死亡。
 * 胜利条件「夺舍并幸存」：游戏结算时若阿蒙存活且至少夺舍过一次，则独立获胜
 * （在 {@code CustomWinnerClass} 中判定）。
 *
 * <p>
 * 隐私要点：时之虫状态仅同步给阿蒙本人，且绝不给受害者任何可见效果。
 */
public final class AmonPlayerComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<AmonPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            Noellesroles.id("amon"), AmonPlayerComponent.class);

    /** 时之虫潜伏成熟时间：90 秒。 */
    public static final int INCUBATION_TICKS = 75 * 20;
    private static final int POSSESSION_REQUIRED_TICKS = 60 * 20;
    /** 终幕「寻找阿蒙」持续时间：80 秒。 */
    public static final int FINALE_TICKS = 80 * 20;
    /** 种植半径：15 格。 */
    private static final double PLANT_RADIUS_SQR = 15.0 * 15.0;
    /** 夺舍半径：15 格（玩家主动夺舍时，目标须在此范围内）。 */
    private static final double USURP_RADIUS_SQR = 15.0 * 15.0;
    /** 食物/饮料标签（noellesroles:food_drink），用于窃取豁免。 */
    private static final TagKey<net.minecraft.world.item.Item> FOOD_DRINK_TAG = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath("noellesroles", "food_drink"));

    private final Player player;

    // ===== 服务端权威状态 =====
    /** 宿主 UUID -> 已潜伏 tick。 */
    private final Map<UUID, Integer> seeds = new HashMap<>();
    /** 已成熟（可夺舍）宿主 UUID。 */
    private final Set<UUID> maturedHosts = new HashSet<>();
    /** 当前伪装目标（夺舍后顶替其皮肤+名字）。 */
    private UUID disguiseTarget;
    /** 正在附身的目标（须为成熟宿主）；非空即处于附身中，按 G 完成夺舍。 */
    public UUID possessTarget;
    private int possessTicks;
    /** 附身开始时记录的「本体」位置（完成夺舍时在此生成阿蒙自己的尸体）。 */
    private Vec3 homePos;
    private float homeYRot;
    private double prePossessionScale = Double.NaN;

    public int usurpCount;
    public boolean hasUsurped;
    public int seedCap = 2;
    /** 已控制玩家数（上限 3）；达到上限后不再走附身流程，成熟宿主直接寄生杀死。 */
    public int controlCount;
    public static final int MAX_CONTROL_COUNT = 3;

    // ===== 终幕状态 =====
    /** 是否处于「终幕·寻找阿蒙」阶段。 */
    public boolean finalePhase;
    /** 终幕剩余 tick。 */
    public int finaleTicks;
    /** 备用能力（续命/逃脱）剩余次数 = 回归的寄宿体数。 */
    public int reserveLives;

    // ===== 客户端镜像（仅用于 HUD 显示）=====
    public int clientSeeds;
    public int clientMatured;
    /** 客户端镜像：成熟宿主 UUID（仅同步给阿蒙本人，供背包选目标界面显示）。 */
    public final Set<UUID> clientMaturedHosts = new HashSet<>();
    /** 客户端镜像：当前附身的目标。 */
    public UUID clientPossessTarget;

    public AmonPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    /** 隐私：时之虫状态只对阿蒙本人可见。 */
    @Override
    public boolean shouldSyncWith(ServerPlayer target) {
        return this.player == target;
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public void init() {
        seeds.clear();
        maturedHosts.clear();
        disguiseTarget = null;
        possessTarget = null;
        possessTicks = 0;
        homePos = null;
        prePossessionScale = Double.NaN;
        usurpCount = 0;
        hasUsurped = false;
        seedCap = 2;
        controlCount = 0;
        finalePhase = false;
        finaleTicks = 0;
        reserveLives = 0;
        clientSeeds = 0;
        clientMatured = 0;
        clientMaturedHosts.clear();
        clientPossessTarget = null;
        sync();
    }

    @Override
    public void clear() {
        clearDisguise();
        // 若附身进行中被重置，清掉隐身/无敌表现。
        if (possessTarget != null && player instanceof ServerPlayer amon) {
            clearPossessionEffects(amon);
        }
        // 若终幕进行中被重置，关闭全服表现（音乐/滤镜/状态栏）并还原体型。
        if (finalePhase && player instanceof ServerPlayer amon && amon.level() instanceof ServerLevel level) {
            if (amon.getAttribute(Attributes.SCALE) != null) {
                amon.getAttribute(Attributes.SCALE).setBaseValue(1.0D);
            }
            broadcastFinale(level, false);
        }
        seeds.clear();
        maturedHosts.clear();
        disguiseTarget = null;
        possessTarget = null;
        possessTicks = 0;
        homePos = null;
        prePossessionScale = Double.NaN;
        usurpCount = 0;
        hasUsurped = false;
        seedCap = 2;
        controlCount = 0;
        finalePhase = false;
        finaleTicks = 0;
        reserveLives = 0;
        clientSeeds = 0;
        clientMatured = 0;
        clientMaturedHosts.clear();
        clientPossessTarget = null;
        sync();
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer amon))
            return;
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(amon.level());
        // 角色被中途更换：清理残留状态与伪装。
        if (!game.isRole(amon, ModRoles.AMON)) {
            if (!seeds.isEmpty() || !maturedHosts.isEmpty() || disguiseTarget != null || possessTarget != null) {
                clearDisguise();
                if (possessTarget != null)
                    clearPossessionEffects(amon);
                seeds.clear();
                maturedHosts.clear();
                disguiseTarget = null;
                possessTarget = null;
                homePos = null;
                possessTicks = 0;
                prePossessionScale = Double.NaN;
                sync();
            }
            return;
        }
        if (!game.isRunning())
            return;
        boolean alive = GameUtils.isPlayerAliveAndSurvival(amon);

        // 终幕阶段：驱动倒计时、发光与胜利判定（结束不再潜伏新虫）。
        if (finalePhase) {
            if (alive)
                tickFinale(amon, game);
            return;
        }
        if (!alive)
            return;

        // 安全网：若游戏本应结束（平民或杀手胜利）但 handleGameEnd 未能触发终幕，
        // 由服务端 tick 兜底启动，确保持有成熟宿主的阿蒙不会因时序问题错过终幕。
        if (!maturedHosts.isEmpty() && isGameEnding(amon, game)) {
            startFinale();
            return;
        }

        // 附身中：跟随目标、维持隐身/无敌；目标失效则解除附身。
        if (possessTarget != null) {
            handlePossessionTick(amon);
        }

        seedCap = Math.max(1, (game.getPlayerCount()) / 6);

        boolean changed = false;

        // 潜伏与成熟
        Iterator<Map.Entry<UUID, Integer>> it = seeds.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            Player host = amon.level().getPlayerByUUID(entry.getKey());
            if (host == null || !GameUtils.isPlayerAliveAndSurvival(host)) {
                it.remove();
                changed = true;
                continue;
            }
            int ticks = entry.getValue() + 1;
            if (ticks >= INCUBATION_TICKS) {
                it.remove();
                // 控制名额已满（≥3）：不再走附身流程，直接寄生杀死宿主
                if (controlCount >= MAX_CONTROL_COUNT) {
                    Player hostPlayer = amon.level().getPlayerByUUID(entry.getKey());
                    if (hostPlayer instanceof ServerPlayer sh && GameUtils.isPlayerAliveAndSurvival(sh)) {
                        directParasiteKill(sh);
                        changed = true;
                    }
                } else {
                    maturedHosts.add(entry.getKey());
                    amon.displayClientMessage(Component.translatable("message.noellesroles.amon.seed_matured")
                            .withStyle(ChatFormatting.DARK_PURPLE), true);
                }
                changed = true;
            } else {
                entry.setValue(ticks);
            }
        }

        // 剪枝离线/死亡的成熟宿主
        if (maturedHosts.removeIf(uuid -> {
            Player host = amon.level().getPlayerByUUID(uuid);
            return host == null || !GameUtils.isPlayerAliveAndSurvival(host);
        })) {
            changed = true;
        }

        if (changed || amon.tickCount % 200 == 0)
            sync();
    }

    /** 种下时之虫（静默）。target 为准星指向的玩家。 */
    public boolean plantSeed(ServerPlayer target) {
        if (!(player instanceof ServerPlayer amon))
            return false;
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(amon.level());
        if (!game.isRunning() || !game.isRole(amon, ModRoles.AMON) || !GameUtils.isPlayerAliveAndSurvival(amon)) {
            return false;
        }
        if (seeds.size() + maturedHosts.size() >= seedCap) {
            amon.displayClientMessage(Component.translatable("message.noellesroles.amon.cap_reached", seedCap)
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (target == null || target == amon || !GameUtils.isPlayerAliveAndSurvival(target)
                || seeds.containsKey(target.getUUID()) || maturedHosts.contains(target.getUUID())
                || amon.distanceToSqr(target) > PLANT_RADIUS_SQR || !amon.hasLineOfSight(target)) {
            amon.displayClientMessage(Component.translatable("message.noellesroles.amon.no_target")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        seeds.put(target.getUUID(), 0);
        amon.displayClientMessage(Component.translatable("message.noellesroles.amon.seed_planted")
                .withStyle(ChatFormatting.DARK_PURPLE), true);
        // 被寄生者获得音效与提示。
        target.playNotifySound(SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 0.8f, 0.6f);
        target.displayClientMessage(Component.translatable("message.noellesroles.amon.parasitized")
                .withStyle(ChatFormatting.DARK_PURPLE), true);
        sync();
        return true;
    }

    /**
     * 背包点选玩家：附身到该成熟宿主身上（阿蒙隐身+无敌并贴附跟随目标），记录本体位置。
     * 由 {@code AmonSelectTargetC2SPacket} 服务端接收后调用。
     */
    public boolean setPossessTarget(UUID targetUuid) {
        if (!(player instanceof ServerPlayer amon) || !(amon.level() instanceof ServerLevel level))
            return false;
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(amon.level());
        if (!game.isRunning() || !game.isRole(amon, ModRoles.AMON) || !GameUtils.isPlayerAliveAndSurvival(amon)) {
            return false;
        }
        if (finalePhase || possessTarget != null)
            return false;
        // 控制名额用完：不再允许附身，成熟宿主已在 serverTick 中自动直接寄生
        if (controlCount >= MAX_CONTROL_COUNT) {
            amon.displayClientMessage(Component.translatable("message.noellesroles.amon.control_limit_reached")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        // 只能附身已成熟、仍存活的宿主。
        if (targetUuid == null || !maturedHosts.contains(targetUuid)) {
            return false;
        }
        Player hostPlayer = level.getPlayerByUUID(targetUuid);
        if (!(hostPlayer instanceof ServerPlayer host) || !GameUtils.isPlayerAliveAndSurvival(host)) {
            maturedHosts.remove(targetUuid);
            sync();
            return false;
        }
        // 夺舍距离限制：目标须在 15 格以内。
        if (amon.distanceToSqr(host) > USURP_RADIUS_SQR) {
            amon.displayClientMessage(Component.translatable("message.noellesroles.amon.usurp_too_far")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        // 记录本体位置（完成夺舍时在此生成阿蒙自己的尸体）。
        homePos = amon.position();
        homeYRot = amon.getYHeadRot();
        possessTarget = targetUuid;
        possessTicks = 0;
        refreshPossessionEffects(amon);
        shrinkForPossession(amon);
        // 立即贴附到目标身上。
        amon.teleportTo(level, host.getX(), host.getY(), host.getZ(), host.getYRot(), host.getXRot());
        amon.displayClientMessage(Component.translatable("message.noellesroles.amon.possess_start", host.getName())
                .withStyle(ChatFormatting.DARK_PURPLE), true);
        // 被夺舍宿主获得音效与提示。
        host.playNotifySound(SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.9f, 0.7f);
        host.displayClientMessage(Component.translatable("message.noellesroles.amon.possessed_victim")
                .withStyle(ChatFormatting.DARK_PURPLE), false);
        sync();
        return true;
    }

    /** 是否正在附身（此时按 G 完成夺舍）。 */
    public boolean isPossessing() {
        return possessTarget != null;
    }

    /** 附身每 tick：跟随目标并维持隐身/无敌；目标失效则解除附身。 */
    private void handlePossessionTick(ServerPlayer amon) {
        if (!(amon.level() instanceof ServerLevel level))
            return;
        Player t = level.getPlayerByUUID(possessTarget);
        if (!(t instanceof ServerPlayer target) || !GameUtils.isPlayerAliveAndSurvival(target)) {
            cancelPossession(amon, "message.noellesroles.amon.possession_lost");
            return;
        }
        possessTicks++;
        refreshPossessionEffects(amon);
        // 阿蒙掌控目标移动：阿蒙隐身自由移动，目标被锁定移动/视角并每 tick 牵引到阿蒙位置；
        // 二者均无碰撞箱，避免互相拥挤。目标被致盲失明，无法看见阿蒙。
        target.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, 10, 0, false, false, false));
        target.addEffect(new MobEffectInstance(ModEffects.TURN_BANED, 10, 0, false, false, false));
        target.addEffect(new MobEffectInstance(ModEffects.NO_COLLIDE, 10, 0, false, false, false));
        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 10, 0, false, false, false));
        target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 10, 0, false, false, false));
        target.teleportTo(level, amon.getX(), amon.getY(), amon.getZ(), amon.getYRot(), amon.getXRot());
    }

    /**
     * 附身期间按 潜行+技能键：完成夺舍——变成目标（伪装其皮肤/名字）、令其死亡，并在本体处生成阿蒙自己的尸体。
     */
    public boolean finalizePossession() {
        if (!(player instanceof ServerPlayer amon) || !(amon.level() instanceof ServerLevel level))
            return false;
        if (possessTarget == null)
            return false;
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(amon.level());
        if (!game.isRunning() || !game.isRole(amon, ModRoles.AMON) || !GameUtils.isPlayerAliveAndSurvival(amon)) {
            return false;
        }
        UUID targetUuid = possessTarget;
        Player t = level.getPlayerByUUID(targetUuid);
        if (!(t instanceof ServerPlayer target) || !GameUtils.isPlayerAliveAndSurvival(target)) {
            cancelPossession(amon, "message.noellesroles.amon.possession_lost");
            return false;
        }
        if (possessTicks < POSSESSION_REQUIRED_TICKS) {
            int seconds = Math.max(1, (POSSESSION_REQUIRED_TICKS - possessTicks + 19) / 20);
            amon.displayClientMessage(Component.translatable("message.noellesroles.amon.possess_wait", seconds)
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
            return false;
        }
        Vec3 targetPos = target.position();
        float targetYRot = target.getYRot();
        float targetXRot = target.getXRot();

        // 本体处生成阿蒙自己的尸体。
        Vec3 home = homePos != null ? homePos : amon.position();
        spawnOwnBody(amon, level, home, homeYRot);
        // 令目标死亡（不生成目标尸体——躯体被阿蒙鸠占）。
        GameUtils.forceKillPlayer(target, false, null, GameConstants.DeathReasons.AMON_USURP);

        // 解除附身表现，变成目标，停留在目标死亡处。
        clearPossessionEffects(amon);
        amon.teleportTo(level, targetPos.x, targetPos.y, targetPos.z, targetYRot, targetXRot);
        setDisguise(targetUuid);

        usurpCount++;
        hasUsurped = true;
        controlCount++;
        seeds.remove(targetUuid);
        maturedHosts.remove(targetUuid);
        possessTarget = null;
        possessTicks = 0;
        homePos = null;

        amon.displayClientMessage(Component.translatable("message.noellesroles.amon.usurp_done")
                .withStyle(ChatFormatting.DARK_PURPLE), true);
        sync();
        return true;
    }

    /** 解除附身（目标失效/重置）：清表现、传回本体、清状态。 */
    private void cancelPossession(ServerPlayer amon, String msgKey) {
        clearPossessionEffects(amon);
        if (homePos != null && amon.level() instanceof ServerLevel level) {
            amon.teleportTo(level, homePos.x, homePos.y, homePos.z, homeYRot, 0f);
        }
        possessTarget = null;
        possessTicks = 0;
        homePos = null;
        if (msgKey != null) {
            amon.displayClientMessage(Component.translatable(msgKey).withStyle(ChatFormatting.RED), true);
        }
        sync();
    }

    /** 维持附身表现：隐身 + 无敌 + 无碰撞（短时长，每 tick 续期，附身结束自然消失）。 */
    private void refreshPossessionEffects(ServerPlayer amon) {
        amon.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, false));
        amon.addEffect(new MobEffectInstance(ModEffects.INVINCIBLE, 40, 0, false, false, false));
        amon.addEffect(new MobEffectInstance(ModEffects.NO_COLLIDE, 40, 0, false, false, false));
    }

    private void clearPossessionEffects(ServerPlayer amon) {
        amon.removeEffect(MobEffects.INVISIBILITY);
        amon.removeEffect(ModEffects.INVINCIBLE);
        amon.removeEffect(ModEffects.NO_COLLIDE);
        restorePossessionScale(amon);
    }

    private void shrinkForPossession(ServerPlayer amon) {
        if (amon.getAttribute(Attributes.SCALE) == null || !Double.isNaN(prePossessionScale)) {
            return;
        }
        prePossessionScale = amon.getAttribute(Attributes.SCALE).getBaseValue();
        amon.getAttribute(Attributes.SCALE).setBaseValue(prePossessionScale * 0.45D);
    }

    private void restorePossessionScale(ServerPlayer amon) {
        if (amon.getAttribute(Attributes.SCALE) == null || Double.isNaN(prePossessionScale)) {
            return;
        }
        amon.getAttribute(Attributes.SCALE).setBaseValue(prePossessionScale);
        prePossessionScale = Double.NaN;
    }

    /**
     * 致命伤时的死亡转移：消耗一名成熟宿主续命。
     *
     * @return true 表示已夺舍（死亡应被取消）；false 表示无成熟宿主（真正死亡）。
     */
    public boolean tryDeathTransfer() {
        if (!(player instanceof ServerPlayer amon))
            return false;
        while (true) {
            UUID host = pickAliveMaturedHost(amon);
            if (host == null)
                return false;
            if (usurp(host)) {
                amon.displayClientMessage(Component.translatable("message.noellesroles.amon.death_transfer")
                        .withStyle(ChatFormatting.DARK_PURPLE), true);
                return true;
            }
            // usurp 失败已将该宿主移除，继续尝试下一个。
        }
    }

    private UUID pickAliveMaturedHost(ServerPlayer amon) {
        UUID best = null;
        double bestDist = Double.MAX_VALUE;
        Iterator<UUID> it = maturedHosts.iterator();
        while (it.hasNext()) {
            UUID uuid = it.next();
            Player host = amon.level().getPlayerByUUID(uuid);
            if (host == null || !GameUtils.isPlayerAliveAndSurvival(host)) {
                it.remove();
                continue;
            }
            double d = amon.distanceToSqr(host);
            if (d < bestDist) {
                bestDist = d;
                best = uuid;
            }
        }
        return best;
    }

    /** 夺舍指定宿主：杀死宿主、传送到其位置、顶替其皮肤与名字。 */
    private boolean usurp(UUID hostUuid) {
        if (!(player instanceof ServerPlayer amon))
            return false;
        if (!(amon.level() instanceof ServerLevel level))
            return false;
        Player hostPlayer = level.getPlayerByUUID(hostUuid);
        if (!(hostPlayer instanceof ServerPlayer host) || !GameUtils.isPlayerAliveAndSurvival(host)) {
            seeds.remove(hostUuid);
            maturedHosts.remove(hostUuid);
            return false;
        }

        // 阿蒙抛下的旧躯壳位置（用于生成阿蒙自己的尸体）。
        Vec3 amonOldPos = amon.position();
        float amonOldYRot = amon.getYHeadRot();

        // 宿主被夺舍：不生成宿主尸体（躯体被阿蒙鸠占）。
        GameUtils.forceKillPlayer(host, false, null, GameConstants.DeathReasons.AMON_USURP);
        // 阿蒙抛下原来的躯壳：在旧位置生成阿蒙自己的尸体。
        spawnOwnBody(amon, level, amonOldPos, amonOldYRot);

        // 阿蒙夺舍后传送到一个随机房间（而非自己的房间或宿主旁边），只顶替宿主外观。
        GameUtils.teleportToRandomRoom(amon);
        setDisguise(hostUuid);

        usurpCount++;
        hasUsurped = true;
        controlCount++;
        seeds.remove(hostUuid);
        maturedHosts.remove(hostUuid);

        amon.displayClientMessage(Component.translatable("message.noellesroles.amon.usurp_done")
                .withStyle(ChatFormatting.DARK_PURPLE), true);
        sync();
        return true;
    }

    /**
     * 控制名额已满时的直接寄生杀死：跳过附身/操控，直接令成熟宿主死亡（彻底寄生状态），
     * 不生成宿主尸体（被寄生占据），在宿主位置生成阿蒙的寄生躯壳。
     */
    private void directParasiteKill(ServerPlayer target) {
        if (!(player instanceof ServerPlayer amon) || !(amon.level() instanceof ServerLevel level))
            return;
        if (!GameUtils.isPlayerAliveAndSurvival(target))
            return;
        Vec3 targetPos = target.position();
        float targetYRot = target.getYRot();
        // 令宿主死亡，不生成宿主尸体（已被寄生占据）
        GameUtils.forceKillPlayer(target, false, null, GameConstants.DeathReasons.AMON_USURP);
        // 在宿主位置生成阿蒙的寄生躯壳
        spawnOwnBody(amon, level, targetPos, targetYRot);
        amon.displayClientMessage(Component.translatable("message.noellesroles.amon.direct_parasite")
                .withStyle(ChatFormatting.DARK_PURPLE), true);
        sync();
    }

    /** 在指定位置生成阿蒙自己的尸体（夺舍时抛下的旧躯壳，死因 AMON_USURP）。 */
    private void spawnOwnBody(ServerPlayer amon, ServerLevel level, Vec3 pos, float yRot) {
        PlayerBodyEntity body = TMMEntities.PLAYER_BODY.create(level);
        if (body == null)
            return;
        body.getAttribute(Attributes.SCALE).setBaseValue(amon.getAttributeValue(Attributes.SCALE));
        PlayerBodyEntityComponent bodycca = body.getComponent();
        bodycca.setDeathReason(GameConstants.DeathReasons.AMON_USURP.toString(), false);
        body.setPlayerUuid(amon.getUUID());
        bodycca.setOwnerName(amon.getScoreboardName(), false);

        body.moveTo(pos.x, pos.y, pos.z, yRot, 0f);
        body.setYRot(yRot);
        body.setYHeadRot(yRot);
        level.addFreshEntity(body);
        bodycca.playerRole = ModRoles.AMON.identifier();
        bodycca.sync();
    }

    private void setDisguise(UUID hostUuid) {
        disguiseTarget = hostUuid;
        if (!(player instanceof ServerPlayer amon) || !(amon.level() instanceof ServerLevel level))
            return;
        AmonSkinS2CPacket packet = new AmonSkinS2CPacket(amon.getUUID(), hostUuid);
        for (ServerPlayer p : level.players()) {
            ServerPlayNetworking.send(p, packet);
        }
    }

    /** 清除阿蒙伪装（向所有客户端广播）。在真正死亡 / 重置 / 角色变更时调用。 */
    public void clearDisguise() {
        if (disguiseTarget == null)
            return;
        disguiseTarget = null;
        if (!(player instanceof ServerPlayer amon) || !(amon.level() instanceof ServerLevel level))
            return;
        AmonSkinS2CPacket packet = new AmonSkinS2CPacket(amon.getUUID(), null);
        for (ServerPlayer p : level.players()) {
            ServerPlayNetworking.send(p, packet);
        }
    }

    /**
     * 游戏结算钩子（供 CustomWinnerClass 调用）。
     * 若存在持有 ≥1 寄宿体的存活阿蒙：开启/维持「终幕」，返回 {@code NONE} 阻止常规结算；
     * 否则返回 {@code NOT_MODIFY} 让常规结算继续（阿蒙按常规失败）。
     * 阿蒙的最终胜利由 {@link #tickFinale} 在终幕结束时通过 customWinnerWin 宣布。
     */
    public static GameUtils.WinStatus handleGameEnd(ServerLevel level, GameUtils.WinStatus pendingStatus) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level);
        boolean block = false;
        for (ServerPlayer p : level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(p) || !game.isRole(p, ModRoles.AMON))
                continue;
            AmonPlayerComponent comp = KEY.get(p);
            if (comp.finalePhase) {
                block = true;
            } else if (pendingStatus != GameUtils.WinStatus.NONE && comp.maturedHosts.size() >= 1) {
                // 仅在常规游戏「本应结束」时（杀手/乘客/超时等结算）才进入终幕；
                // 若 pendingStatus 为 NONE（场上平民与杀手都还活着），不可提前触发阿蒙时刻。
                comp.startFinale();
                block = true;
            }
        }
        return block ? GameUtils.WinStatus.NONE : GameUtils.WinStatus.NOT_MODIFY;
    }

    /**
     * 检查游戏是否即将结束（杀手全灭或平民全灭），作为 handleGameEnd 的兜底安全网。
     * 确保持有时之虫的阿蒙不会因 AllowGameEnd.EVENT 时序/游戏模式差异而错过终幕。
     */
    private static boolean isGameEnding(ServerPlayer amon, SREGameWorldComponent game) {
        boolean killerAlive = false;
        boolean innocentAlive = false;
        for (ServerPlayer p : amon.serverLevel().players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(p))
                continue;
            if (p == amon)
                continue; // 阿蒙自己是中立，不计入平民或杀手
            if (game.isKillerTeam(p))
                killerAlive = true;
            else if (game.isInnocent(p))
                innocentAlive = true;
        }
        // 平民全灭（杀手胜利）或杀手全灭（平民胜利）→ 游戏应结束
        return !innocentAlive || !killerAlive;
    }

    /** 开启终幕：寄宿体回归为备用能力、窃取全场物品、发光、全服播报。 */
    private void startFinale() {
        if (!(player instanceof ServerPlayer amon) || !(amon.level() instanceof ServerLevel level))
            return;
        finalePhase = true;
        finaleTicks = FINALE_TICKS;
        reserveLives = maturedHosts.size();
        // 若有附身残留状态（缩形等），先清理
        if (possessTarget != null) {
            clearPossessionEffects(amon);
            possessTarget = null;
            possessTicks = 0;
            homePos = null;
        }
        // 终幕形态：8 倍体型
        amon.getAttribute(Attributes.SCALE).setBaseValue(1.0D);
        prePossessionScale = Double.NaN;
        // 寄宿体回归阿蒙：清空潜伏/成熟状态。
        maturedHosts.clear();
        seeds.clear();
        applyGlow(amon);
        stealItems(amon, level);
        broadcastFinale(level, true);
        sync();
    }

    /**
     * 全服「阿蒙时刻」表现层：
     * active=true → 播报「杀死阿蒙」、播放音效、触发全局状态栏与偏灰滤镜、播放小丑音乐；
     * active=false → 关闭上述表现（客户端据此停止音乐/滤镜/状态栏）。
     */
    private void broadcastFinale(ServerLevel level, boolean active) {
        AmonFinaleS2CPacket finalePacket = new AmonFinaleS2CPacket(active);
        for (ServerPlayer p : level.players()) {
            ServerPlayNetworking.send(p, finalePacket);
            if (active) {
                ServerPlayNetworking.send(p, new BroadcastMessageS2CPacket(
                        Component.translatable("message.noellesroles.amon.finale_start")
                                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)));
                ServerPlayNetworking.send(p, new TriggerStatusBarPayload("AmonFinale"));
                // 登场音效：在每名玩家所在位置播放，确保全员可闻（音乐由客户端环境音循环播放）。
                level.playSound(null, p.getX(), p.getY(), p.getZ(),
                        SoundEvents.WITHER_SPAWN, SoundSource.MASTER, 1.0F, 0.8F);
            }
        }
    }

    private void tickFinale(ServerPlayer amon, SREGameWorldComponent game) {
        applyGlow(amon);
        // 杀光其余所有人 → 立即获胜。
        if (countOtherAlive(amon) == 0) {
            declareWin(amon);
            return;
        }
        finaleTicks--;
        if (finaleTicks <= 0) {
            declareWin(amon);
            return;
        }
        if (finaleTicks % 20 == 0)
            sync();
    }

    private void applyGlow(ServerPlayer amon) {
        if (!amon.hasEffect(MobEffects.GLOWING)) {
            amon.addEffect(new MobEffectInstance(MobEffects.GLOWING, FINALE_TICKS + 40, 0, false, false, false));
        }
    }

    private int countOtherAlive(ServerPlayer amon) {
        int n = 0;
        for (ServerPlayer p : amon.serverLevel().players()) {
            if (p != amon && GameUtils.isPlayerAliveAndSurvival(p))
                n++;
        }
        return n;
    }

    private void declareWin(ServerPlayer amon) {
        if (!(amon.level() instanceof ServerLevel level))
            return;
        finalePhase = false;
        broadcastFinale(level, false);
        RoleUtils.customWinnerWin(level, GameUtils.WinStatus.CUSTOM,
                ModRoles.AMON_ID.getPath(), OptionalInt.of(ModRoles.AMON.color()));
    }

    /**
     * 阿蒙在终幕中真正死亡（无备用能力可逃脱）：关闭全服终幕表现，
     * 否则「杀死阿蒙」状态栏/音乐/滤镜会残留。
     */
    public void endFinaleOnDeath() {
        if (!finalePhase)
            return;
        finalePhase = false;
        finaleTicks = 0;
        if (player instanceof ServerPlayer amon && amon.level() instanceof ServerLevel level) {
            if (amon.getAttribute(Attributes.SCALE) != null) {
                amon.getAttribute(Attributes.SCALE).setBaseValue(1.0D);
            }
            broadcastFinale(level, false);
        }
        sync();
    }

    /** 终幕续命/逃脱：消耗一个备用能力，瞬移逃脱并短暂无敌。无备用能力则真正死亡。 */
    public boolean tryFinaleEscape() {
        if (!(player instanceof ServerPlayer amon) || reserveLives <= 0)
            return false;
        reserveLives--;
        blinkAway(amon);
        amon.addEffect(new MobEffectInstance(ModEffects.INVINCIBLE, 40, 0, false, false, false));
        amon.addEffect(new MobEffectInstance(ModEffects.NO_COLLIDE, 40, 0, false, false, false));
        amon.displayClientMessage(Component.translatable("message.noellesroles.amon.finale_escape", reserveLives)
                .withStyle(ChatFormatting.DARK_PURPLE), true);
        sync();
        return true;
    }

    private void blinkAway(ServerPlayer amon) {
        // 终幕被击中时传送到随机房间，而非往旁边随机移动
        GameUtils.teleportToRandomRoom(amon);
    }

    /** 窃取全场存活玩家的随机一件物品（钥匙/信件除外；枪械则复制而非夺走）。 */
    private void stealItems(ServerPlayer amon, ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            if (p == amon || !GameUtils.isPlayerAliveAndSurvival(p))
                continue;
            Inventory inv = p.getInventory();
            List<Integer> slots = new ArrayList<>();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s.isEmpty() || isProtectedItem(s))
                    continue;
                slots.add(i);
            }
            if (slots.isEmpty())
                continue;
            int slot = slots.get(amon.getRandom().nextInt(slots.size()));
            ItemStack stack = inv.getItem(slot);
            ItemStack stolen = stack.copy();
            // 枪/左轮：复制给阿蒙，目标保留；其他物品：夺走。
            if (!stack.is(TMMItemTags.GUNS) && !stack.is(Items.BOW)) {
                inv.setItem(slot, ItemStack.EMPTY);
            }
            insertStolenItem(amon, stolen);
        }
    }

    private boolean insertStolenItem(ServerPlayer amon, ItemStack stolen) {
        if (RoleUtils.insertStackInFreeSlot(amon, stolen)) {
            return true;
        }
        return insertIntoBundle(amon, stolen);
    }

    private boolean insertIntoBundle(ServerPlayer amon, ItemStack stack) {
        for (int i = 0; i < amon.getInventory().getContainerSize(); i++) {
            ItemStack bundle = amon.getInventory().getItem(i);
            if (!bundle.is(Items.BUNDLE)) {
                continue;
            }
            BundleContents.Mutable contents = new BundleContents.Mutable(
                    bundle.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY));
            int inserted = contents.tryInsert(stack);
            if (inserted > 0) {
                bundle.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
                return stack.isEmpty();
            }
        }
        return false;
    }

    private boolean isProtectedItem(ItemStack s) {
        return s.is(TMMItems.KEY) || s.is(TMMItems.IRON_DOOR_KEY) || s.is(TMMItems.LETTER)
                || s.is(ModItems.LETTER_ITEM) || s.is(ModItems.COURIER_MAIL) || s.is(ModItems.RECEIVED_MAIL)
                || isFoodOrDrink(s);
    }

    /** 食物与饮料不予窃取（与 MapStatusBarRuntime 的判定保持一致）。 */
    private boolean isFoodOrDrink(ItemStack s) {
        return s.get(DataComponents.FOOD) != null
                || s.getItem() instanceof CocktailItem
                || s.getItem() instanceof PotionItem
                || s.getItem() instanceof HoneyBottleItem
                || s.is(FOOD_DRINK_TAG)
                || s.is(SEItems.DRINKS);
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider provider) {
        // 仅同步给阿蒙本人（见 shouldSyncWith）：写计数避免对其他人泄露；
        // 成熟宿主 UUID 与锁定目标也仅发往阿蒙本人，供其背包选目标界面使用。
        tag.putInt("Seeds", seeds.size());
        tag.putInt("Matured", maturedHosts.size());
        net.minecraft.nbt.ListTag maturedList = new net.minecraft.nbt.ListTag();
        for (UUID u : maturedHosts) {
            maturedList.add(net.minecraft.nbt.StringTag.valueOf(u.toString()));
        }
        tag.put("MaturedHosts", maturedList);
        if (possessTarget != null)
            tag.putUUID("Possess", possessTarget);
        tag.putInt("UsurpCount", usurpCount);
        tag.putBoolean("HasUsurped", hasUsurped);
        tag.putInt("SeedCap", seedCap);
        tag.putInt("ControlCount", controlCount);
        tag.putBoolean("Finale", finalePhase);
        tag.putInt("FinaleTicks", finaleTicks);
        tag.putInt("Reserve", reserveLives);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider provider) {
        clientSeeds = tag.getInt("Seeds");
        clientMatured = tag.getInt("Matured");
        clientMaturedHosts.clear();
        net.minecraft.nbt.ListTag maturedList = tag.getList("MaturedHosts", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < maturedList.size(); i++) {
            try {
                clientMaturedHosts.add(UUID.fromString(maturedList.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        clientPossessTarget = tag.hasUUID("Possess") ? tag.getUUID("Possess") : null;
        usurpCount = tag.getInt("UsurpCount");
        hasUsurped = tag.getBoolean("HasUsurped");
        seedCap = tag.getInt("SeedCap");
        controlCount = tag.getInt("ControlCount");
        finalePhase = tag.getBoolean("Finale");
        finaleTicks = tag.getInt("FinaleTicks");
        reserveLives = tag.getInt("Reserve");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
    }
}
