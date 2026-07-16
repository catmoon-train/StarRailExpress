package net.exmo.sre.sixtyseconds.content.item;

import io.wifi.starrailexpress.content.item.api.SREItemProperties;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.network.PacketTracker;
import io.wifi.starrailexpress.network.original.ShootMuzzleS2CPayload;
import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.logic.GunTracers;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsDefenseSystem;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsHealthSystem;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsWhisperSystem;
import net.exmo.sre.sixtyseconds.network.SixtySecondsGunShootC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;

/**
 * 60s 枪械模板（猎枪/手枪/步枪共用）：右键开火，须消耗背包内 {@link ModItems#SIXTY_SECONDS_AMMO 子弹}。
 * <ul>
 *   <li>命中<b>怪物</b>（低语怪/夜袭者）→ 立即死亡。</li>
 *   <li>命中<b>玩家</b>→ {@link SixtySecondsBalance#GUN_PLAYER_DAMAGE} 点健康伤害（受护甲减免），
 *       走 {@link SixtySecondsHealthSystem} 倒地/处决路径。</li>
 *   <li>对<b>倒地玩家</b>再开一枪（再耗一发子弹）→ 处决致死；倒地者未被处决则 2.5 分钟后流血而死。</li>
 * </ul>
 * 每把枪有独立<b>攻击冷却</b>；枪声<b>降噪</b>（音量 {@link SixtySecondsBalance#GUN_SOUND_VOLUME}）。
 * 客户端只做准星射线 + 表现，命中结算全在服务端 {@link #handleShoot}（server-authoritative）。
 */
public class SixtySecondsGunItem extends Item implements SREItemProperties.HeldLikeRevolver {
    /** 全部已注册的枪械（构造时自动登记），用于「开一枪→所有枪共享冷却」。 */
    private static final java.util.List<SixtySecondsGunItem> ALL_GUNS = new java.util.ArrayList<>();

    private final int cooldownTicks;
    protected final double range;
    /** 命中玩家的健康伤害（狙击枪=100 直接倒地）。 */
    protected final int playerDamage;
    /** 每次射击消耗的子弹数（RPG=5）。 */
    protected final int ammoPerShot;
    /** 是否带瞄准镜（狙击枪）：首次右键开镜（复用原版 ScopeOverlayRenderer），开镜后右键射击并关镜。 */
    protected final boolean hasScope;
    /** 本枪使用的弹药物品（懒取，规避 ModItems 静态初始化顺序）；默认简制子弹。 */
    protected final java.util.function.Supplier<Item> ammoSupplier;
    /** 连发容量（冲锋枪）：>0 时连续打满 burstSize 发后进入 burstCooldownTicks 长冷却。 */
    private final int burstSize;
    private final int burstCooldownTicks;

    public SixtySecondsGunItem(Properties properties, int cooldownTicks, double range) {
        this(properties, cooldownTicks, range, SixtySecondsBalance.GUN_PLAYER_DAMAGE, 1, false);
    }

    public SixtySecondsGunItem(Properties properties, int cooldownTicks, double range, int playerDamage,
            int ammoPerShot) {
        this(properties, cooldownTicks, range, playerDamage, ammoPerShot, false);
    }

    public SixtySecondsGunItem(Properties properties, int cooldownTicks, double range, int playerDamage,
            int ammoPerShot, boolean hasScope) {
        this(properties, cooldownTicks, range, playerDamage, ammoPerShot, hasScope,
                () -> ModItems.SIXTY_SECONDS_AMMO, 0, 0);
    }

    public SixtySecondsGunItem(Properties properties, int cooldownTicks, double range, int playerDamage,
            int ammoPerShot, boolean hasScope, java.util.function.Supplier<Item> ammoSupplier,
            int burstSize, int burstCooldownTicks) {
        super(properties);
        this.cooldownTicks = cooldownTicks;
        this.range = range;
        this.playerDamage = playerDamage;
        this.ammoPerShot = ammoPerShot;
        this.hasScope = hasScope;
        this.ammoSupplier = ammoSupplier;
        this.burstSize = burstSize;
        this.burstCooldownTicks = burstCooldownTicks;
        ALL_GUNS.add(this);
    }

    /** 本枪的弹药物品。 */
    public Item ammoItem() {
        return ammoSupplier.get();
    }

    public boolean hasScope() {
        return hasScope;
    }

    public double range() {
        return range;
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player user,
            @NotNull InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) {
            // 冷却中直接不响应（避免空发包）
            if (user.getCooldowns().isOnCooldown(stack.getItem())) {
                return InteractionResultHolder.fail(stack);
            }
            // 带镜枪（狙击）：未开镜时右键=开镜（同原版狙击枪 ScopeOverlayRenderer），开镜后右键=射击并关镜
            if (hasScope && !io.wifi.starrailexpress.client.gui.ScopeOverlayRenderer.isInScopeView()) {
                io.wifi.starrailexpress.client.gui.ScopeOverlayRenderer.setInScopeView(true);
                return InteractionResultHolder.consume(stack);
            }
            HitResult result = getGunTarget(user, range);
            int targetId = result instanceof EntityHitResult ehr ? ehr.getEntity().getId() : -1;
            ClientPlayNetworking.send(new SixtySecondsGunShootC2SPacket(targetId));
            user.setXRot(user.getXRot() - 3.0F); // 后坐力
            if (hasScope) {
                io.wifi.starrailexpress.client.gui.ScopeOverlayRenderer.setInScopeView(false);
            }
        }
        return InteractionResultHolder.consume(stack);
    }

    /** 服务端统一开火结算：冷却 → PvP 门控 → 弹药 → 音效/轨迹/枪口 → 命中判定。 */
    public static void handleShoot(ServerPlayer player, int targetId) {
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof SixtySecondsGunItem gun)) {
            return;
        }
        if (player.getCooldowns().isOnCooldown(held.getItem())) {
            return;
        }
        ServerLevel level = player.serverLevel();
        boolean active = SixtySecondsMod.isActive(level);

        // 命中目标（server-authoritative）：优先信任客户端上报的命中（含客户端插值、更跟手），
        // 但服务端做校验/兜底——客户端射线偶发漏报会回传 -1，导致「枪械打不中人」；此时服务端
        // 按玩家当前准星自己再射一条线补判。
        Entity hit = resolveTarget(player, level, gun, targetId);

        // PvP 受限（清晨/准备阶段 + 跨队一律禁）：瞄准玩家开火直接拒绝（不耗弹、不进冷却）
        if (active && hit instanceof ServerPlayer targetPlayer
                && SixtySecondsHealthSystem.isPvpBlocked(level, player, targetPlayer)) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.pvp_blocked"), true);
            return;
        }

        // 弹药：非创造须有对应弹药，否则空枪「咔哒」并提示
        if (!player.isCreative() && !consumeAmmo(player, gun.ammoItem(), gun.ammoPerShot)) {
            level.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                    TMMSounds.ITEM_REVOLVER_CLICK, SoundSource.PLAYERS, 0.6F, 1.3F);
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.gun_no_ammo"), true);
            return;
        }

        // 攻击冷却：开一枪 → 全部枪械进入相同冷却（防止多枪轮换速射）；
        // 连发枪（冲锋枪）：打满 burstSize 发才进入长冷却，期间只有极短的射击间隔
        int cooldown = gun.cooldownTicks;
        if (gun.burstSize > 0) {
            cooldown = gun.tickBurst(player) ? gun.burstCooldownTicks : gun.cooldownTicks;
        }
        for (SixtySecondsGunItem other : ALL_GUNS) {
            player.getCooldowns().addCooldown(other, cooldown);
        }

        // 耐久：成功开火一次损耗 1 点，耗尽枪损坏（创造模式 hurtAndBreak 内部豁免）
        held.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);

        // 降噪枪声（原枪声 5f 过响，这里低音量 + 略高音高的「噗」）
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                TMMSounds.ITEM_REVOLVER_SHOOT, SoundSource.PLAYERS, SixtySecondsBalance.GUN_SOUND_VOLUME,
                1.4F + player.getRandom().nextFloat() * 0.1F - 0.05F);

        // 弹道轨迹 + 枪口闪光
        GunTracers.broadcast(player, hit, gun.range);
        for (ServerPlayer tracking : PlayerLookup.tracking(player)) {
            PacketTracker.sendToClient(tracking, new ShootMuzzleS2CPayload(player.getId()));
        }
        PacketTracker.sendToClient(player, new ShootMuzzleS2CPayload(player.getId()));

        // 命中结算（仅本模式运行时；RPG 等子类可覆写为范围爆炸）
        if (active) {
            gun.resolveHit(player, level, hit);
        }
    }

    /** 命中结算（默认：单体——玩家扣 {@link #playerDamage} 走倒地路径，怪物即死）。 */
    protected void resolveHit(ServerPlayer shooter, ServerLevel level, Entity hit) {
        if (hit == null) {
            return;
        }
        if (hit instanceof ServerPlayer target && GameUtils.isPlayerAliveAndSurvival(target)) {
            // applyInjury：怪物玩家正常扣血（250 血），普通玩家扣健康（护甲减免），归零则倒地
            SixtySecondsHealthSystem.applyInjury(target, shooter, playerDamage);
        } else if (hit instanceof LivingEntity living) {
            // 所有非玩家生物——枪击即死
            living.hurt(shooter.damageSources().playerAttack(shooter), 1000.0F);
        }
    }

    /** 连发计数（服务端）：玩家 → {连发内已射发数, 上次射击 gameTime}。间隔超时自动重置。 */
    private static final java.util.Map<java.util.UUID, long[]> BURST_STATE = new java.util.HashMap<>();
    /** 两发间隔超过该值视为一轮新连发。 */
    private static final long BURST_RESET_TICKS = 60;

    /** 推进连发计数；返回 true 表示本发打满一轮、应进入长冷却。 */
    private boolean tickBurst(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        long[] state = BURST_STATE.computeIfAbsent(player.getUUID(), k -> new long[]{0, 0});
        if (now - state[1] > BURST_RESET_TICKS) {
            state[0] = 0;
        }
        state[0]++;
        state[1] = now;
        if (state[0] >= burstSize) {
            state[0] = 0;
            return true;
        }
        return false;
    }

    /** 从背包扣 {@code count} 发指定弹药（先全量检查再扣，不足则不动返回 false）。 */
    private static boolean consumeAmmo(ServerPlayer player, Item ammoItem, int count) {
        int have = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack s = player.getInventory().getItem(slot);
            if (s.is(ammoItem)) {
                have += s.getCount();
            }
        }
        if (have < count) {
            return false;
        }
        int left = count;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && left > 0; slot++) {
            ItemStack s = player.getInventory().getItem(slot);
            if (s.is(ammoItem)) {
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
        return true;
    }

    /** 准星射线：可命中 存活玩家（含倒地者/怪物玩家）/ 所有怪物。客户端与服务端共用（谓词一致）。 */
    public static HitResult getGunTarget(Player user, double range) {
        return ProjectileUtil.getHitResultOnViewVector(user, SixtySecondsGunItem::isGunTargetable, (float) range);
    }

    /** 枪械可命中判定：存活玩家（含倒地者/怪物玩家）或任意生物（低语怪/夜袭者/其他 mod 怪物）。 */
    private static boolean isGunTargetable(Entity entity) {
        if (entity instanceof Player player) {
            return GameUtils.isPlayerAliveAndSurvival(player);
        }
        return entity instanceof LivingEntity;
    }

    /**
     * 命中容忍距离：客户端按<b>视线（眼睛）</b>距离判定命中，服务端按<b>实体（脚）</b>距离校验，
     * 二者在俯仰/近身时会有几格差，留出容差抹平，避免边缘距离的合法命中被服务端误判超距丢弃。
     */
    private static final double AIM_TOLERANCE = 4.0;

    /**
     * 解析本次开火的实际命中目标（server-authoritative）：
     * <ol>
     *   <li>客户端上报了命中且该实体<b>可命中</b>、在<b>射程 + 容差</b>内 → 采信（更跟手、含客户端插值）；</li>
     *   <li>否则（客户端漏报 -1 / 目标失效 / 超距）→ 服务端按玩家当前准星<b>自射一条线</b>兜底，
     *       杜绝客户端漏报导致的「打不中人」。</li>
     * </ol>
     */
    private static Entity resolveTarget(ServerPlayer player, ServerLevel level, SixtySecondsGunItem gun,
            int clientTargetId) {
        Entity clientHit = clientTargetId >= 0 ? level.getEntity(clientTargetId) : null;
        if (clientHit != null && isGunTargetable(clientHit)
                && clientHit.distanceTo(player) <= gun.range + AIM_TOLERANCE) {
            return clientHit;
        }
        HitResult server = getGunTarget(player, gun.range);
        return server instanceof EntityHitResult ehr ? ehr.getEntity() : null;
    }
}
