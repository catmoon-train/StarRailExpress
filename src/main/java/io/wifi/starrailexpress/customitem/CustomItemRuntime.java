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

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.content.item.api.SREItemProperties;
import io.wifi.starrailexpress.customitem.CustomItemData.TargetMode;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.util.SkinUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.harpymodloader.events.GameInitializeEvent;
import org.agmas.noellesroles.component.FoodDrinkGlowComponent;
import org.agmas.noellesroles.game.roles.killer.dream.DreamHealthComponent;
import org.agmas.noellesroles.gunfx.GunTracers;
import org.agmas.noellesroles.init.ModItems;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义列车物品行为引擎。
 *
 * <p>
 * 所有行为都通过物品栈上的 {@link SREDataComponentTypes#CUSTOM_ITEM_ID} 找到配置后分发，
 * 五种性质（基础 / 蓄力 / 枪械 / 特殊原版物品 / 食物）共用同一个注册物品。
 *
 * <p>
 * 时间单位统一 tick（20 tick = 1 秒）。
 */
public final class CustomItemRuntime {

    private CustomItemRuntime() {
    }

    /** 命中计数：{@code 目标UUID|物品id} -> 已命中次数。 */
    private static final Map<String, Integer> HIT_COUNTS = new ConcurrentHashMap<>();
    /** 蓄力完成去重：{@code 玩家UUID|物品id} -> 触发时的游戏刻（避免 releaseUsing 与 finishUsingItem 双触发）。 */
    private static final Map<String, Long> CHARGE_FIRED = new ConcurrentHashMap<>();
    /** 自动射击状态：{@code 射手UUID|物品id} -> 状态。 */
    private static final Map<String, AutoFire> AUTO_FIRES = new ConcurrentHashMap<>();

    private static boolean initialized = false;

    /** 自动射击运行时状态。 */
    private static final class AutoFire {
        int fired;
        int total;
        long nextShotTick;
    }

    private static String key(UUID uuid, String itemId) {
        return uuid + "|" + itemId;
    }

    // ==================== 事件注册 ====================

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        // 子弹物品：为「支持子弹物品」的自定义枪械补弹（其它情况原样放行，不影响既有的子弹逻辑）
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (!world.isClientSide() && stack.is(ModItems.BULLET) && tryReloadWithBullet(player, stack)) {
                return InteractionResultHolder.consume(stack);
            }
            return InteractionResultHolder.pass(stack);
        });

        // 自动枪械状态机 + 过期数据清理
        ServerTickEvents.END_SERVER_TICK.register(CustomItemRuntime::tickServer);

        // 玩家断线清理
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> clearPlayer(handler.getPlayer().getUUID()));

        // 每局开始重置命中计数与自动射击状态
        GameInitializeEvent.EVENT.register((serverLevel, gameWorldComponent, players) -> {
            HIT_COUNTS.clear();
            AUTO_FIRES.clear();
            CHARGE_FIRED.clear();
        });
    }

    // ==================== 基础道具 ====================

    /** 基础道具：右键执行指令 + 冷却 + 是否消耗。 */
    public static void executeBasic(ServerPlayer player, ItemStack stack, CustomItemData data) {
        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            return;
        }
        CustomItemLoader.executeCommands(data.commands, player);
        applyCooldown(player, stack, data.cooldownTicks);
        consumeItem(player, stack, data.consumeItem);
    }

    // ==================== 蓄力道具 ====================

    /** 蓄力是否完成（{@code chargedTicks} = 已蓄力刻数）。 */
    public static boolean isChargeComplete(CustomItemData data, int chargedTicks) {
        return chargedTicks >= Math.max(1, data.chargeTicks);
    }

    /**
     * 蓄力完成：对使用者执行指令，并按「是否对其它玩家作用」处理被作用者。
     *
     * @return 是否成功触发（触发了才走冷却与消耗）
     */
    public static boolean completeCharge(ServerPlayer player, ItemStack stack, CustomItemData data) {
        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            return false;
        }
        long now = player.level().getGameTime();
        String dedupeKey = key(player.getUUID(), data.id);
        Long last = CHARGE_FIRED.get(dedupeKey);
        if (last != null && now - last <= 2L) {
            // releaseUsing 与 finishUsingItem 同一次使用内的重复回调
            return false;
        }
        CHARGE_FIRED.put(dedupeKey, now);

        // 使用者自身指令
        CustomItemLoader.executeCommands(data.selfCommands, player);

        // 对其它玩家作用
        if (data.affectOthers) {
            for (ServerPlayer target : findTargets(player, data)) {
                CustomItemLoader.executeCommands(data.targetCommands, target);
            }
        }

        applyCooldown(player, stack, data.cooldownTicks);
        consumeItem(player, stack, data.consumeItem);
        return true;
    }

    /** 蓄力 / 右键作用范围的目标玩家。 */
    public static List<ServerPlayer> findTargets(ServerPlayer player, CustomItemData data) {
        List<ServerPlayer> result = new ArrayList<>();
        TargetMode mode = data.targetMode();
        ServerLevel level = player.serverLevel();
        double range = data.range;

        if (mode == TargetMode.LOOKED_PLAYER) {
            HitResult hit = ProjectileUtil.getHitResultOnViewVector(player,
                    entity -> isValidTarget(player, entity), range);
            if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof ServerPlayer target) {
                result.add(target);
            }
            return result;
        }

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F).normalize();
        double halfAngleCos = Math.cos(Math.toRadians(Math.max(1.0, data.coneAngle) / 2.0));

        for (ServerPlayer other : level.players()) {
            if (other == player || !GameUtils.isPlayerAliveAndSurvival(other)) {
                continue;
            }
            Vec3 targetPos = other.getEyePosition();
            double distance = eye.distanceTo(targetPos);
            if (distance > range) {
                continue;
            }
            if (mode == TargetMode.CIRCLE) {
                result.add(other);
                continue;
            }
            Vec3 direction = targetPos.subtract(eye).normalize();
            double dot = look.dot(direction);
            if (mode == TargetMode.CONE) {
                if (dot >= halfAngleCos) {
                    result.add(other);
                }
            } else if (mode == TargetMode.LINE) {
                // 朝向的直线：近似视线方向内的极窄锥体
                if (dot >= 0.98D) {
                    result.add(other);
                }
            }
        }
        return result;
    }

    // ==================== 枪械道具 ====================

    /** 是否正处于自动射击状态。 */
    public static boolean isAutoFiring(Player player, String itemId) {
        return AUTO_FIRES.containsKey(key(player.getUUID(), itemId));
    }

    /**
     * 右键开火（非自动枪械）或启动自动射击序列（自动枪械）。
     *
     * @return 是否成功射击
     */
    public static boolean useGun(ServerPlayer player, ItemStack stack, CustomItemData data) {
        if (data.autoFire) {
            if (isAutoFiring(player, data.id)) {
                return false;
            }
            return startAutoFire(player, stack, data);
        }
        return fireGun(player, stack, data, false) != null || true;
    }

    /** 启动自动射击：立即打第一发，其余按间隔在 tick 中补齐。 */
    private static boolean startAutoFire(ServerPlayer player, ItemStack stack, CustomItemData data) {
        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            return false;
        }
        AutoFire state = new AutoFire();
        state.total = Math.max(1, data.autoShots);
        state.fired = 0;
        AUTO_FIRES.put(key(player.getUUID(), data.id), state);

        ServerPlayer victim = fireGun(player, stack, data, true);
        state.fired = 1;
        state.nextShotTick = player.level().getGameTime() + Math.max(1, data.autoShotIntervalTicks);
        if (victim != null && state.fired >= Math.max(1, data.autoShotsToFinal)) {
            triggerFinalEffect(player, victim, stack, data);
        }
        if (state.fired >= state.total) {
            AUTO_FIRES.remove(key(player.getUUID(), data.id));
        }
        return true;
    }

    /** 自动射击状态机。 */
    private static void tickServer(MinecraftServer server) {
        if (AUTO_FIRES.isEmpty()) {
            return;
        }
        for (var iterator = AUTO_FIRES.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            String mapKey = entry.getKey();
            AutoFire state = entry.getValue();
            int split = mapKey.lastIndexOf('|');
            if (split <= 0) {
                iterator.remove();
                continue;
            }
            UUID shooterId;
            try {
                shooterId = UUID.fromString(mapKey.substring(0, split));
            } catch (Exception e) {
                iterator.remove();
                continue;
            }
            String itemId = mapKey.substring(split + 1);

            ServerPlayer shooter = server.getPlayerList().getPlayer(shooterId);
            CustomItemData data = CustomItemLoader.get(itemId);
            if (shooter == null || data == null || !GameUtils.isPlayerAliveAndSurvival(shooter)) {
                iterator.remove();
                continue;
            }
            ItemStack stack = findStack(shooter, itemId);
            if (stack.isEmpty()) {
                iterator.remove();
                continue;
            }
            long now = shooter.level().getGameTime();
            if (now < state.nextShotTick) {
                continue;
            }

            ServerPlayer victim = fireGun(shooter, stack, data, true);
            if (victim == null && !canFire(shooter, stack, data)) {
                // 打不出来（冷却 / 没子弹）：结束本次自动射击
                iterator.remove();
                continue;
            }
            CustomItemLoader.executeCommands(data.autoShotCommands, shooter);
            state.fired++;
            state.nextShotTick = now + Math.max(1, data.autoShotIntervalTicks);
            if (victim != null && state.fired >= Math.max(1, data.autoShotsToFinal)) {
                triggerFinalEffect(shooter, victim, stack, data);
            }
            if (state.fired >= state.total) {
                iterator.remove();
            }
        }
    }

    private static boolean canFire(ServerPlayer shooter, ItemStack stack, CustomItemData data) {
        if (shooter.getCooldowns().isOnCooldown(stack.getItem())) {
            return false;
        }
        return !data.ammoSystem || getAmmo(stack, data) > 0;
    }

    /**
     * 开一枪：射线检测 + 弹道 + 命中处理 + 冷却。
     *
     * @return 命中的玩家（未命中 / 未开火返回 null）
     */
    private static ServerPlayer fireGun(ServerPlayer shooter, ItemStack stack, CustomItemData data, boolean autoMode) {
        if (!GameUtils.isPlayerAliveAndSurvival(shooter)) {
            return null;
        }
        if (!autoMode && shooter.getCooldowns().isOnCooldown(stack.getItem())) {
            return null;
        }
        if (data.ammoSystem) {
            int ammo = getAmmo(stack, data);
            if (ammo <= 0) {
                return null;
            }
            setAmmo(stack, ammo - 1);
        }

        // 右键发射时执行的指令
        CustomItemLoader.executeCommands(data.shootCommands, shooter);

        HitResult hit = ProjectileUtil.getHitResultOnViewVector(shooter,
                entity -> isValidTarget(shooter, entity), data.gunRange);

        // 弹道射线
        if (data.showTracer) {
            Entity hitEntity = hit instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
            GunTracers.broadcast(shooter, hitEntity, data.gunRange);
        }

        ServerPlayer victim = null;
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof ServerPlayer target
                && target != shooter && GameUtils.isPlayerAliveAndSurvival(target)) {
            victim = target;
            handleGunHit(shooter, victim, stack, data, autoMode);
        }

        if (!autoMode) {
            applyCooldown(shooter, stack, data.shotCooldownTicks);
        }
        return victim;
    }

    private static void handleGunHit(ServerPlayer shooter, ServerPlayer victim, ItemStack stack, CustomItemData data,
            boolean autoMode) {
        // 被枪械击中的玩家执行的指令
        CustomItemLoader.executeCommands(data.hitCommands, victim);

        // 击退：1 点原版伤害
        if (data.knockbackOnHit) {
            victim.invulnerableTime = 0;
            victim.hurt(victim.damageSources().playerAttack(shooter), 1.0F);
        }

        // 命中回 1 发弹药
        if (data.ammoSystem && data.refillOnHit) {
            setAmmo(stack, Math.min(data.maxAmmo, getAmmo(stack, data) + 1));
        }

        // 非自动枪械：按「被第几次命中」触发最终效果
        if (!autoMode) {
            String hitKey = key(victim.getUUID(), data.id);
            int hits = HIT_COUNTS.merge(hitKey, 1, Integer::sum);
            if (hits >= Math.max(1, data.hitsToFinal)) {
                HIT_COUNTS.remove(hitKey);
                triggerFinalEffect(shooter, victim, stack, data);
            }
        }
    }

    /** 最终效果：被击中玩家的指令 + 可选致死 + 枪械进入冷却。 */
    private static void triggerFinalEffect(ServerPlayer shooter, ServerPlayer victim, ItemStack stack,
            CustomItemData data) {
        CustomItemLoader.executeCommands(data.finalHitCommands, victim);
        if (data.lethalOnHit && GameUtils.isPlayerAliveAndSurvival(victim)) {
            GameUtils.killPlayer(victim, true, shooter, parseDeathReason(data.lethalDeathReason,
                    io.wifi.starrailexpress.game.GameConstants.DeathReasons.REVOLVER));
        }
        applyCooldown(shooter, stack, data.finalCooldownTicks);
    }

    // ==================== 弹药系统 ====================

    public static int getAmmo(ItemStack stack, CustomItemData data) {
        Integer value = stack.get(SREDataComponentTypes.AMMO_COUNT);
        return value == null ? data.maxAmmo : Mth.clamp(value, 0, data.maxAmmo);
    }

    private static void setAmmo(ItemStack stack, int value) {
        stack.set(SREDataComponentTypes.AMMO_COUNT, Math.max(0, value));
    }

    /**
     * 用「子弹」物品为支持弹药系统的自定义枪械补弹。
     *
     * @return 是否拦截了本次右键
     */
    private static boolean tryReloadWithBullet(Player player, ItemStack bulletStack) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        ItemStack gun = findBulletReloadGun(serverPlayer);
        if (gun.isEmpty()) {
            return false;
        }
        CustomItemData data = CustomItemLoader.getData(gun);
        if (data == null || !data.ammoSystem || !data.bulletItemSupport) {
            return false;
        }
        int ammo = getAmmo(gun, data);
        int missing = data.maxAmmo - ammo;
        if (missing <= 0) {
            return true;
        }
        int use = Math.min(missing, bulletStack.getCount());
        if (use <= 0) {
            return true;
        }
        setAmmo(gun, ammo + use);
        if (!serverPlayer.isCreative()) {
            bulletStack.shrink(use);
        }
        return true;
    }

    /** 找一把「支持子弹物品」的自定义枪械（优先手持）。 */
    private static ItemStack findBulletReloadGun(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (isBulletReloadGun(main)) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (isBulletReloadGun(off)) {
            return off;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isBulletReloadGun(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean isBulletReloadGun(ItemStack stack) {
        CustomItemData data = CustomItemLoader.getData(stack);
        return data != null && data.ammoSystem && data.bulletItemSupport;
    }

    // ==================== 特殊原版物品 ====================

    /** 特殊原版物品右键：执行指令 + 冷却。 */
    public static void useVanillaWeapon(ServerPlayer player, ItemStack stack, CustomItemData data) {
        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            return;
        }
        CustomItemLoader.executeCommands(data.weaponRightClickCommands, player);
        applyCooldown(player, stack, data.weaponRightClickCooldownTicks);
    }

    /**
     * 特殊原版物品左键攻击玩家：仅 {@code canUseSpVanillaWeapon} 的职业可用，
     * 伤害扣除目标的虚拟血量（{@link DreamHealthComponent}），不启用原版击杀逻辑。
     *
     * @return 是否启用原版攻击逻辑（始终 false）
     */
    public static boolean onVanillaWeaponAttack(ServerPlayer attacker, ServerPlayer target, ItemStack stack,
            CustomItemData data) {
        if (!GameUtils.isPlayerAliveAndSurvival(attacker) || !GameUtils.isPlayerAliveAndSurvival(target)) {
            return false;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(attacker.level());
        SRERole role = gameWorld == null ? null : gameWorld.getRole(attacker);
        if (role == null || !role.canUseSpVanillaWeapon()) {
            return false;
        }
        if (attacker.getCooldowns().isOnCooldown(stack.getItem())) {
            return false;
        }
        // 与 dream 系列武器一致：需要满蓄力
        if (attacker.getAttackStrengthScale(0.5F) < 1.0F) {
            return false;
        }

        DreamHealthComponent health = DreamHealthComponent.KEY.get(target);
        long now = target.level().getGameTime();
        boolean damaged = health.hurt(attacker, data.virtualDamage,
                parseDeathReason(data.killDeathReason,
                        io.wifi.starrailexpress.game.GameConstants.DeathReasons.GENERAL_ATTACK));

        // 攻击者 / 被攻击者指令
        CustomItemLoader.executeCommands(data.attackerHitCommands, attacker);
        CustomItemLoader.executeCommands(data.victimCommands, target);

        if (damaged) {
            attacker.setLastHurtByMob(target);
            target.setLastHurtByMob(attacker);
            // 成功把虚拟血量削减至 0 → 物品进入冷却
            if (health.getEffectiveHealth(now) <= 0) {
                applyCooldown(attacker, stack, data.killCooldownTicks);
            }
        }
        return false;
    }

    // ==================== 食物道具 ====================

    /** 食物 / 饮料食用后的统一处理（指令 + 冷却 + 与 Cocktail 一致的饮食表现）。 */
    public static void onFoodConsumed(ServerPlayer player, ItemStack stack, CustomItemData data) {
        if (data.isDrink) {
            SREPlayerMoodComponent.KEY.get(player).drinkCocktail();
            FoodDrinkGlowComponent.playerDrink(player, stack);
        } else {
            SREPlayerMoodComponent.KEY.get(player).eatFood();
            FoodDrinkGlowComponent.playerEat(player, stack);
        }
        CustomItemLoader.executeCommands(data.eatCommands, player);
        applyCooldown(player, stack, data.eatCooldownTicks);
    }

    /** 食用但不消耗时，手动结算食物数值（跳过原版的 shrink）。 */
    public static void applyFoodValues(ServerPlayer player, ItemStack stack) {
        FoodProperties properties = stack.get(DataComponents.FOOD);
        if (properties != null) {
            player.getFoodData().eat(properties);
        }
    }

    /** 默认死亡原因（用于左键 / 食用等需要归属攻击者的场景）。 */
    public static ResourceLocation defaultDeathReason(ItemStack stack) {
        return SkinUtils.getItemTypeResourceLocation(stack);
    }

    // ==================== 工具方法 ====================

    private static boolean isValidTarget(ServerPlayer shooter, Entity entity) {
        return entity instanceof ServerPlayer player && player != shooter
                && GameUtils.isPlayerAliveAndSurvival(player);
    }

    private static void applyCooldown(ServerPlayer player, ItemStack stack, int ticks) {
        if (ticks > 0) {
            player.getCooldowns().addCooldown(stack.getItem(), ticks);
        }
    }

    private static void consumeItem(ServerPlayer player, ItemStack stack, boolean consume) {
        if (consume && !player.isCreative()) {
            stack.shrink(1);
        }
    }

    private static ResourceLocation parseDeathReason(String value, ResourceLocation fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(value.trim());
        return parsed == null ? fallback : parsed;
    }

    /** 在玩家物品栏里找指定自定义物品 id 的物品栈。 */
    public static ItemStack findStack(ServerPlayer player, String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack main = player.getMainHandItem();
        if (itemId.equals(CustomItemLoader.getCustomItemId(main))) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (itemId.equals(CustomItemLoader.getCustomItemId(off))) {
            return off;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (itemId.equals(CustomItemLoader.getCustomItemId(stack))) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** 清理某个玩家的运行时状态。 */
    public static void clearPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        String prefix = playerId + "|";
        HIT_COUNTS.keySet().removeIf(key -> key.startsWith(prefix));
        CHARGE_FIRED.keySet().removeIf(key -> key.startsWith(prefix));
        AUTO_FIRES.keySet().removeIf(key -> key.startsWith(prefix));
    }

    /** 供物品类复用：清理玩家全部状态（重载 / 开局）。 */
    public static void clearAll() {
        HIT_COUNTS.clear();
        CHARGE_FIRED.clear();
        AUTO_FIRES.clear();
    }

    /** 记录一条错误日志（避免各处重复判断）。 */
    public static void warn(String message, Object... args) {
        SRE.LOGGER.warn(message, args);
    }

    /** 物品使用入口统一走运行时（保留静态引用，便于将来扩展）。 */
    public static void onItemUsed(Level level, Player player, ItemStack stack) {
        if (level.isClientSide() || !(player instanceof ServerPlayer)) {
            return;
        }
        CustomItemData data = CustomItemLoader.getData(stack);
        if (data == null) {
            return;
        }
        if (data.kind() == CustomItemData.Kind.BASIC) {
            executeBasic((ServerPlayer) player, stack, data);
        }
    }

    /** 允许物品类在左键攻击时统一做合法性校验。 */
    public static boolean acceptsAttack(ServerPlayer attacker, ServerPlayer target, ItemStack stack) {
        if (!GameUtils.isPlayerAliveAndSurvival(attacker) || !GameUtils.isPlayerAliveAndSurvival(target)) {
            return false;
        }
        CustomItemData data = CustomItemLoader.getData(stack);
        return data != null && data.kind() == CustomItemData.Kind.VANILLA_WEAPON;
    }

    /** 供外部（如左键攻击 mixin 之外的自定义实现）判断物品是否为指定性质。 */
    public static boolean isKind(ItemStack stack, CustomItemData.Kind kind) {
        CustomItemData data = CustomItemLoader.getData(stack);
        return data != null && data.kind() == kind;
    }

    /** 便于物品类读取 SREItemProperties 中的常量（保持引用不被优化掉）。 */
    public static Class<?> attackInterface() {
        return SREItemProperties.LeftClickHurtable.class;
    }

    /** 物品栏查找（交互手优先），供需要 InteractionHand 的场景使用。 */
    public static InteractionHand findHand(ServerPlayer player, String itemId) {
        if (itemId != null && itemId.equals(CustomItemLoader.getCustomItemId(player.getMainHandItem()))) {
            return InteractionHand.MAIN_HAND;
        }
        return InteractionHand.OFF_HAND;
    }
}
