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
import io.wifi.starrailexpress.api.RoleTeam;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.CustomItemCooldownComponent;
import io.wifi.starrailexpress.cca.CustomItemHitMarkerComponent;
import io.wifi.starrailexpress.cca.ExtraSlotComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.content.item.api.SREItemProperties;
import io.wifi.starrailexpress.customitem.CustomItemData.TargetMode;
import io.wifi.starrailexpress.custommodifier.CustomModifierLoader;
import io.wifi.starrailexpress.event.AllowItemShowInHand;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.ShouldDropOnDeath;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.rules.DropRules;
import io.wifi.starrailexpress.util.SkinUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.GameInitializeEvent;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.component.FoodDrinkGlowComponent;
import org.agmas.noellesroles.content.item.HandCuffsItem;
import org.agmas.noellesroles.game.roles.killer.dream.DreamHealthComponent;
import org.agmas.noellesroles.gunfx.GunTracers;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    /** 蓄力完成去重：{@code 玩家UUID|物品id} -> 触发时的游戏刻（避免 releaseUsing 与 finishUsingItem 双触发）。 */
    private static final Map<String, Long> CHARGE_FIRED = new ConcurrentHashMap<>();
    /** 自动射击状态：{@code 射手UUID|物品id} -> 状态。 */
    private static final Map<String, AutoFire> AUTO_FIRES = new ConcurrentHashMap<>();
    /**
     * 客户端自动射击镜像：{@code 玩家UUID|物品id} -> 窗口截止游戏刻。
     *
     * <p>
     * 客户端没有服务端的 tick 状态，用它判断「自动射击期间右键」从而不给任何反馈与后坐力。
     */
    private static final Map<String, Long> CLIENT_AUTO_FIRE = new ConcurrentHashMap<>();

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

        // 自动枪械状态机 + 手铐结算 + 过期数据清理
        ServerTickEvents.END_SERVER_TICK.register(CustomItemRuntime::tickServer);

        // 空手右键被铐玩家：按「能被什么阵营 / 职业 / 修饰符取下」的配置取下手铐
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer remover)
                    || !(entity instanceof ServerPlayer holder)) {
                return InteractionResult.PASS;
            }
            if (hand != InteractionHand.MAIN_HAND || !remover.getMainHandItem().isEmpty()) {
                return InteractionResult.PASS;
            }
            return takeOffCuff(remover, holder);
        });

        // 拆弹钳：右键投掷物实体开始拆除（拆除时间 / 失败概率按配置）
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer user)) {
                return InteractionResult.PASS;
            }
            if (!user.getMainHandItem().is(ModItems.PLIERS)) {
                return InteractionResult.PASS;
            }
            if (!(entity instanceof CustomThrowableEntity charge)) {
                return InteractionResult.PASS;
            }
            return beginDefuse(user, charge);
        });

        // 投掷物区域（粒子区域 / 燃烧弹式持续生效区域）
        ServerTickEvents.END_SERVER_TICK.register(CustomThrowableAreas::tick);

        // 玩家断线清理
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> clearPlayer(handler.getPlayer().getUUID()));

        // 每局开始重置命中标记与自动射击状态
        GameInitializeEvent.EVENT.register((serverLevel, gameWorldComponent, players) -> {
            // 命中标记记在玩家身上（{@link CustomItemHitMarkerComponent}）：开局把所有人的标记清干净
            for (ServerPlayer online : serverLevel.getServer().getPlayerList().getPlayers()) {
                CustomItemHitMarkerComponent.KEY.get(online).clearAllMarkers();
                // 冷却同样要清：否则上一局残留的冷却会被带进新的一局
                CustomItemCooldownComponent.KEY.get(online).clearAllCooldowns();
            }
            AUTO_FIRES.clear();
            CHARGE_FIRED.clear();
            CustomThrowableAreas.clear();
        });

        // 游戏结束时清空所有在线玩家的自定义物品冷却（冷却过的物品不该把冷却带出本局）
        OnGameEnd.EVENT.register((serverLevel, gameWorldComponent) -> {
            for (ServerPlayer online : serverLevel.getServer().getPlayerList().getPlayers()) {
                CustomItemCooldownComponent.KEY.get(online).clearAllCooldowns();
            }
        });

        // 丢弃限制：是否可丢弃 / 仅特定职业可丢弃（与物品自身规则一起决定，其它物品不受影响）
        DropRules.canDrop.add(CustomItemRuntime::canDropCustomItem);

        // 死亡掉落：配置了「死后掉落」的自定义物品
        ShouldDropOnDeath.EVENT.register(stack -> {
            CustomItemData data = CustomItemLoader.getData(stack);
            return data != null && data.dropOnDeath;
        });

        // 死亡传递：持有者的职业匹配时，把物品交给附近指定阵营的玩家
        OnPlayerDeath.EVENT.register((player, reason) -> handlePassOnDeath(player));
        OnPlayerDeathWithKiller.EVENT.register((player, killer, reason) -> handlePassOnDeath(player));

        // 被自定义手铐铐住的玩家死亡：手铐自动消失（不掉落），也不再对已成为旁观者的他生效
        OnPlayerDeath.EVENT.register((player, reason) -> removeCuffOnDeath(player));
        OnPlayerDeathWithKiller.EVENT.register((player, killer, reason) -> removeCuffOnDeath(player));

        // 手持不可见：客户端事件，返回 EMPTY 即可让第一人称 / 第三人称 / 手臂姿势全部隐藏该物品
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            AllowItemShowInHand.EVENT.register((player, stack, mainHand) -> {
                CustomItemData data = CustomItemLoader.getData(stack);
                return data != null && data.invisibleInHand ? ItemStack.EMPTY : null;
            });
            // 投掷物区域粒子（客户端自己渲染）+ 钳子拆除进度条 HUD
            io.wifi.starrailexpress.client.CustomAreaParticleClient.register();
            io.wifi.starrailexpress.client.CustomThrowableDefuseHud.register();
        }
    }

    // ==================== 丢弃 / 死亡处理 ====================

    /**
     * 自定义列车物品是否允许被该玩家主动丢弃。
     *
     * <p>
     * 非自定义物品直接返回 false（不影响原有 DropRules 判定）；自定义物品遵循：
     * 填写了「仅特定职业可丢弃」→ 只有该职业能丢；否则看「是否可丢弃」。
     */
    private static boolean canDropCustomItem(Player player) {
        ItemStack stack = player.getMainHandItem();
        CustomItemData data = CustomItemLoader.getData(stack);
        if (data == null) {
            return false;
        }
        if (!data.dropOnlyRole.isEmpty()) {
            return roleMatches(player, data.dropOnlyRole);
        }
        return data.canDropItem;
    }

    /** 玩家当前职业是否匹配配置里填的职业 id（支持 {@code ns:path} 与纯 path，大小写不敏感）。 */
    public static boolean roleMatches(Player player, String configuredRoleId) {
        if (player == null || configuredRoleId == null || configuredRoleId.isBlank()) {
            return false;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        SRERole role = gameWorld == null ? null : gameWorld.getRole(player);
        if (role == null || role.identifier() == null) {
            return false;
        }
        String wanted = configuredRoleId.trim();
        ResourceLocation parsed = ResourceLocation.tryParse(wanted);
        if (parsed != null && parsed.equals(role.identifier())) {
            return true;
        }
        String path = wanted.contains(":") ? wanted.substring(wanted.indexOf(':') + 1) : wanted;
        return role.identifier().getPath().equalsIgnoreCase(path);
    }

    // ==================== 使用限制（仅指定职业 / 修饰符 / 阵营） ====================

    /**
     * 玩家是否满足这件物品的使用限制。
     *
     * <p>
     * 三项限制都为空 = 不限制；任意一项非空即要求玩家命中该项（同项内多项之间是「或」）。
     * 三项之间是「与」的关系：全部满足才能使用。
     */
    public static boolean canUse(Player player, CustomItemData data) {
        if (player == null || data == null) {
            return false;
        }
        // 职业：命中任意一个职业 id 即可
        List<String> roles = CustomItemData.splitIds(data.useOnlyRoles);
        if (!roles.isEmpty()) {
            boolean matched = false;
            for (String roleId : roles) {
                if (roleMatches(player, roleId)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        // 修饰符：带有任意一个修饰符即可
        List<String> modifiers = CustomItemData.splitIds(data.useOnlyModifiers);
        if (!modifiers.isEmpty()) {
            WorldModifierComponent modifierComponent = WorldModifierComponent.KEY.get(player.level());
            boolean matched = false;
            for (String modifierId : modifiers) {
                SREModifier modifier = CustomModifierLoader.findModifier(modifierId);
                if (modifier != null && modifierComponent != null
                        && modifierComponent.isModifier(player, modifier)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        // 阵营：属于列表中的任意一个阵营即可
        if (data.useOnlyTeams != null && !data.useOnlyTeams.isEmpty()) {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
            SRERole role = gameWorld == null ? null : gameWorld.getRole(player);
            boolean matched = false;
            for (String teamName : data.useOnlyTeams) {
                if (teamName == null || teamName.isBlank()) {
                    continue;
                }
                try {
                    if (RoleTeam.valueOf(teamName.trim().toUpperCase(Locale.ROOT)).matches(role)) {
                        matched = true;
                        break;
                    }
                } catch (IllegalArgumentException ignored) {
                    // 配置里填了不存在的阵营：跳过该项
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    /** 使用限制不满足时向玩家提示并返回 false；满足时返回 true。 */
    public static boolean ensureCanUse(ServerPlayer player, CustomItemData data) {
        if (canUse(player, data)) {
            return true;
        }
        player.displayClientMessage(Component.translatable("sre.custom_item.use_denied"), true);
        return false;
    }

    /**
     * 持有者死亡时执行「死亡传递」：职业匹配时把物品交给附近目标阵营的玩家。
     *
     * <p>
     * 与会计传递存折一致：先从死亡玩家背包里取出，再交给目标玩家；
     * 附近找不到目标阵营玩家时退化为原地掉落，避免物品凭空消失。
     */
    private static void handlePassOnDeath(Player victim) {
        if (!(victim instanceof ServerPlayer serverVictim) || victim.level().isClientSide()) {
            return;
        }
        List<ItemStack> toPass = new ArrayList<>();
        for (int i = 0; i < serverVictim.getInventory().getContainerSize(); i++) {
            ItemStack stack = serverVictim.getInventory().getItem(i);
            CustomItemData data = CustomItemLoader.getData(stack);
            if (data == null || data.passOnDeathRole.isEmpty()) {
                continue;
            }
            if (!roleMatches(serverVictim, data.passOnDeathRole)) {
                continue;
            }
            toPass.add(stack.copy());
            serverVictim.getInventory().setItem(i, ItemStack.EMPTY);
        }
        if (toPass.isEmpty()) {
            return;
        }
        serverVictim.containerMenu.broadcastChanges();

        for (ItemStack stack : toPass) {
            CustomItemData data = CustomItemLoader.getData(stack);
            if (data == null) {
                continue;
            }
            ServerPlayer target = findPassTarget(serverVictim, data.passOnDeathTeam());
            if (target == null) {
                serverVictim.drop(stack, true, false);
                continue;
            }
            if (!target.getInventory().add(stack)) {
                target.drop(stack, false);
            }
            target.containerMenu.broadcastChanges();
            target.displayClientMessage(Component
                    .translatable("sre.custom_item.pass_received", serverVictim.getName())
                    .withStyle(style -> style.withColor(0xFFB300)), true);
        }
    }

    /** 在附近（32 格内优先）随机挑一名目标阵营的存活玩家；附近没有则在全场同阵营里随机。 */
    private static ServerPlayer findPassTarget(ServerPlayer victim, RoleTeam team) {
        if (team == null) {
            return null;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());
        if (gameWorld == null) {
            return null;
        }
        List<ServerPlayer> nearby = new ArrayList<>();
        List<ServerPlayer> all = new ArrayList<>();
        double radiusSqr = 32.0D * 32.0D;
        for (ServerPlayer other : victim.serverLevel().players()) {
            if (other == victim || !GameUtils.isPlayerAliveAndSurvival(other)) {
                continue;
            }
            SRERole role = gameWorld.getRole(other);
            if (role == null || !team.matches(role)) {
                continue;
            }
            all.add(other);
            if (victim.distanceToSqr(other) <= radiusSqr) {
                nearby.add(other);
            }
        }
        List<ServerPlayer> pool = nearby.isEmpty() ? all : nearby;
        if (pool.isEmpty()) {
            return null;
        }
        return pool.get(victim.getRandom().nextInt(pool.size()));
    }

    // ==================== 基础道具 ====================

    /** 基础道具：右键执行指令 + 冷却 + 是否消耗。 */
    public static void executeBasic(ServerPlayer player, ItemStack stack, CustomItemData data) {
        if (isOnCooldown(player, stack)) {
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
        if (isOnCooldown(player, stack)) {
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
        boolean hitAnyone = false;
        if (data.affectOthers) {
            List<ServerPlayer> targets = findTargets(player, data);
            hitAnyone = !targets.isEmpty();
            for (ServerPlayer target : targets) {
                // <attacker> = 手持该道具的使用者
                CustomItemLoader.executeCommands(data.targetCommands, target, player);
            }
        }

        // 空放：配置了「对其它玩家作用」，但蓄力完成时没有命中任何玩家
        if (data.affectOthers && !hitAnyone) {
            // 空放提示：把配置的文本通过 actionbar 提示使用者
            if (data.emptyFireMessageEnabled && data.emptyFireMessage != null
                    && !data.emptyFireMessage.isBlank()) {
                player.displayClientMessage(Component.literal(data.emptyFireMessage), true);
            }
            // 空放走「独立冷却」：与命中后的冷却无关，默认 0 = 空放不进入冷却
            applyCooldown(player, stack, data.emptyFireCooldownTicks);
            consumeItem(player, stack, data.consumeItem);
            return true;
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
            // 与枪械命中同一条判定，避免「射线不认、范围技能也不认」这类静默跳过
            if (!isValidTarget(player, other)) {
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

    /** 服务端：是否正处于自动射击状态（权威）。 */
    public static boolean isServerAutoFiring(Player player, String itemId) {
        return AUTO_FIRES.containsKey(key(player.getUUID(), itemId));
    }

    /** 客户端：记录自动射击窗口（右键启动自动射击时调用）。 */
    public static void beginClientAutoFire(Player player, CustomItemData data) {
        if (!data.autoFire) {
            return;
        }
        int shots = Math.max(1, data.autoShots);
        long until = player.level().getGameTime()
                + (long) shots * Math.max(1, data.autoShotIntervalTicks) + 2L;
        CLIENT_AUTO_FIRE.put(key(player.getUUID(), data.id), until);
    }

    /**
     * 客户端：是否仍在自动射击窗口内（切换掉手持物品会立即失效）。
     */
    public static boolean isClientAutoFiring(Player player, String itemId) {
        String mapKey = key(player.getUUID(), itemId);
        Long until = CLIENT_AUTO_FIRE.get(mapKey);
        if (until == null) {
            return false;
        }
        if (player.level().getGameTime() > until) {
            CLIENT_AUTO_FIRE.remove(mapKey);
            return false;
        }
        if (!isStillHolding(player, itemId)) {
            CLIENT_AUTO_FIRE.remove(mapKey);
            return false;
        }
        return true;
    }

    /** 物品是否仍在手持（主手或副手）。 */
    public static boolean isStillHolding(Player player, String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return false;
        }
        return itemId.equals(CustomItemLoader.getCustomItemId(player.getMainHandItem()))
                || itemId.equals(CustomItemLoader.getCustomItemId(player.getOffhandItem()));
    }

    /**
     * 右键开火（非自动枪械）或启动自动射击序列（自动枪械）。
     *
     * @return 是否成功射击
     */
    public static boolean useGun(ServerPlayer player, ItemStack stack, CustomItemData data) {
        if (!ensureCanUse(player, data)) {
            return false;
        }
        if (data.autoFire) {
            if (isServerAutoFiring(player, data.id)) {
                // 自动射击期间右键不再触发射击与其它效果
                return false;
            }
            return startAutoFire(player, stack, data);
        }
        if (!canFire(player, stack, data)) {
            return false;
        }
        fireGun(player, stack, data, false);
        return true;
    }

    /**
     * 「左键发射」枪械：客户端左键时调用，处理后坐力与自动射击窗口。
     *
     * <p>
     * 与右键路径（{@link CustomItem#use}）的客户端分支保持一致：不出手时就完全不给反馈，
     * 所以返回 {@code false} 时调用方不要发包。
     *
     * @return 是否需要向服务端发送开火请求
     */
    public static boolean clientLeftClickFire(Player player, ItemStack stack, CustomItemData data) {
        if (data == null || data.kind() != CustomItemData.Kind.GUN
                || data.fireButton() != CustomItemData.FireButton.LEFT) {
            return false;
        }
        if (!isStillHolding(player, data.id)) {
            return false;
        }
        // 自动射击期间不再给任何反馈（不摆臂、无后坐力、无音效）
        if (isClientAutoFiring(player, data.id)) {
            return false;
        }
        // 弹药不足：空枪，不给后坐力
        if (data.ammoSystem && getAmmo(stack, data) <= 0) {
            return false;
        }
        CustomItem.applyRecoil(player, data);
        if (data.autoFire) {
            // 记录客户端自动射击窗口，窗口内左键不再给任何反馈
            beginClientAutoFire(player, data);
        }
        return true;
    }

    /** 启动自动射击：立即打第一发，其余按间隔在 tick 中补齐。 */
    private static boolean startAutoFire(ServerPlayer player, ItemStack stack, CustomItemData data) {
        if (!canFire(player, stack, data)) {
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

    /** 自动射击状态机 + 手铐类结算。 */
    private static void tickServer(MinecraftServer server) {
        tickCuffs(server);
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
            // 与 fireGun 的射手判定保持一致：创造模式也能继续连发，旁观（含死亡）才中断
            if (shooter == null || data == null || shooter.isSpectator()) {
                iterator.remove();
                continue;
            }
            ItemStack stack = findStack(shooter, itemId);
            if (stack.isEmpty()) {
                iterator.remove();
                continue;
            }
            // 自动射击期间切换了手持物品 → 立即中断本次自动射击并进入冷却
            if (!isStillHolding(shooter, itemId)) {
                applyCooldown(shooter, stack,
                        data.finalCooldownTicks > 0 ? data.finalCooldownTicks : data.shotCooldownTicks);
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
        if (isOnCooldown(shooter, stack)) {
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
        // 与模组自带枪械（{@code GunShootPayload} 只拦旁观）一致：创造模式也能开枪，
        // 否则在创造模式下测试时枪会完全没有反馈（无音效 / 无弹道 / 不命中），很难排查
        if (shooter.isSpectator()) {
            return null;
        }
        if (!autoMode && isOnCooldown(shooter, stack)) {
            return null;
        }
        if (data.ammoSystem) {
            int ammo = getAmmo(stack, data);
            if (ammo <= 0) {
                return null;
            }
            setAmmo(stack, ammo - 1);
        }

        // 开火音效（手动开火与自动射击都会播放）
        playFireSound(shooter, data);

        // 右键发射时执行的指令
        CustomItemLoader.executeCommands(data.shootCommands, shooter);

        // 射线是否允许穿过屏障：开启后与狙击枪一致，忽略屏障类方块继续向后命中
        HitResult hit = data.tracerThroughBarrier
                ? io.wifi.starrailexpress.util.SniperProjectileUtil.getSniperHitResult(shooter,
                        entity -> isValidTarget(shooter, entity), data.gunRange)
                : ProjectileUtil.getHitResultOnViewVector(shooter,
                        entity -> isValidTarget(shooter, entity), data.gunRange);

        // 弹道射线：是否显示 / 样式完全由物品自身配置决定，不受服务端总开关影响
        if (data.showTracer) {
            Entity hitEntity = hit instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
            if (data.tracerStyle() == CustomItemData.TracerStyle.SNIPER) {
                GunTracers.broadcastSniper(shooter, hitEntity, data.gunRange);
            } else {
                GunTracers.broadcast(shooter, hitEntity, data.gunRange, true);
            }
        }

        ServerPlayer victim = null;
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof ServerPlayer target
                && isValidTarget(shooter, target)) {
            victim = target;
            handleGunHit(shooter, victim, stack, data, autoMode);
            applyDistanceRules(shooter, victim, data);
        }

        if (!autoMode) {
            applyCooldown(shooter, stack, data.shotCooldownTicks);
        }
        return victim;
    }

    private static void handleGunHit(ServerPlayer shooter, ServerPlayer victim, ItemStack stack, CustomItemData data,
            boolean autoMode) {
        // 被枪械击中的玩家执行的指令（<attacker> = 开枪的玩家）
        CustomItemLoader.executeCommands(data.hitCommands, victim, shooter);

        // 击退：1 点原版伤害
        if (data.knockbackOnHit) {
            victim.invulnerableTime = 0;
            victim.hurt(victim.damageSources().playerAttack(shooter), 1.0F);
        }

        // 命中回 1 发弹药
        if (data.ammoSystem && data.refillOnHit) {
            setAmmo(stack, Math.min(data.maxAmmo, getAmmo(stack, data) + 1));
        }

        // 射线命中即致死：这一枪就把目标打死，不再累计命中次数（也就不会有最终效果）
        if (data.lethalOnRayHit) {
            applyLethal(shooter, victim, data);
            return;
        }

        // 非自动枪械：按「被第几次命中」触发最终效果。
        // 命中标记记在被击中的玩家身上、按物品 id 分开存，超过 hitMarkerTicks 没触发就自动消失
        if (!autoMode) {
            CustomItemHitMarkerComponent hitMarkers = CustomItemHitMarkerComponent.KEY.get(victim);
            int hits = hitMarkers.addHit(data.id, data.hitMarkerTicks, victim.level().getGameTime());
            if (hits >= Math.max(1, data.hitsToFinal)) {
                hitMarkers.clearMarker(data.id);
                triggerFinalEffect(shooter, victim, stack, data);
            }
        }
    }

    /**
     * 距离检测：命中「distance 格以外」的玩家时，对被击中的玩家执行配置的指令。
     *
     * <p>
     * 同时满足多条规则时全部执行（参考狙击枪：命中 50 格外的玩家会穿盾）。
     */
    private static void applyDistanceRules(ServerPlayer shooter, ServerPlayer victim, CustomItemData data) {
        if (data.distanceRules == null || data.distanceRules.isEmpty()) {
            return;
        }
        double distance = shooter.distanceTo(victim);
        for (CustomItemData.DistanceRule rule : data.distanceRules) {
            if (rule == null || rule.command == null || rule.command.isBlank()) {
                continue;
            }
            if (distance >= rule.distance) {
                CustomItemLoader.executeCommands(List.of(rule.command), victim, shooter);
            }
        }
    }

    /** 最终效果：被击中玩家的指令 + 可选致死 + 枪械进入冷却。 */
    private static void triggerFinalEffect(ServerPlayer shooter, ServerPlayer victim, ItemStack stack,
            CustomItemData data) {
        CustomItemLoader.executeCommands(data.finalHitCommands, victim, shooter);
        // 「只有触发最终效果时才致死」：致死时机是这里（射线命中致死是另一条独立开关）
        if (data.lethalOnFinal) {
            applyLethal(shooter, victim, data);
        }
        applyCooldown(shooter, stack, data.finalCooldownTicks);
    }

    /**
     * 命中致死（两个致死开关共用）：按配置的死因打死被击中的玩家，
     * <b>并把开枪者作为击杀者记录下来</b>。
     *
     * <p>
     * 击杀者必须填 {@code shooter}：小脑（误杀）惩罚挂在 {@code OnTeammateKilledTeammate} 上，
     * 而那条链在 {@code killer == null} 时直接 return —— 不记攻击者就等于绕开小脑惩罚
     * （原版左轮打死好人触发的那条判定就是这么走的）。{@code GameUtils.killPlayer} 会把
     * {@code killer} 一路带给该事件、击杀统计与回放，所以这里不能传 null。
     */
    private static void applyLethal(ServerPlayer shooter, ServerPlayer victim, CustomItemData data) {
        if (victim == null || !GameUtils.isPlayerAliveAndSurvival(victim)) {
            return;
        }
        GameUtils.killPlayer(victim, true, shooter,
                parseDeathReason(data.lethalDeathReason, GameConstants.DeathReasons.REVOLVER));
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
        if (isOnCooldown(player, stack)) {
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
        if (isOnCooldown(attacker, stack)) {
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
        CustomItemLoader.executeCommands(data.victimCommands, target, attacker);

        if (damaged) {
            attacker.setLastHurtByMob(target);
            target.setLastHurtByMob(attacker);
            // 耐久：每次成功命中消耗 1 点（与 dream 铁斧一致），耗尽即碎裂（此后不再处理冷却）
            if (consumeDurability(attacker, stack, data, 1)) {
                return false;
            }
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

    // ==================== 能不能被小偷偷窃 ====================

    /** 该物品堆是否被配置为「可被小偷窃取」（小偷白名单用，见 {@code ThiefRoleData}）。 */
    public static boolean isStealable(ItemStack stack) {
        CustomItemData data = CustomItemLoader.getData(stack);
        return data != null && data.stealable;
    }

    // ==================== 耐久 ====================

    /** 配置里的耐久上限（0 = 不消耗耐久、不显示耐久条）。 */
    public static int maxDurability(CustomItemData data) {
        return data == null ? 0 : Math.max(0, data.durability);
    }

    /** 物品堆上已经用掉的次数。 */
    public static int usedDurability(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        return Math.max(0, stack.getOrDefault(SREDataComponentTypes.CUSTOM_ITEM_DAMAGE, 0));
    }

    /** 剩余耐久（未配置耐久时返回 0）。 */
    public static int remainingDurability(ItemStack stack, CustomItemData data) {
        int max = maxDurability(data);
        return max <= 0 ? 0 : Math.max(0, max - usedDurability(stack));
    }

    /** 是否显示耐久条（用掉至少一次才显示，与原版一致）。 */
    public static boolean hasDurabilityBar(ItemStack stack) {
        CustomItemData data = CustomItemLoader.getData(stack);
        return data != null && maxDurability(data) > 0 && usedDurability(stack) > 0;
    }

    /** 耐久条宽度（13 格制，与原版一致）。 */
    public static int durabilityBarWidth(ItemStack stack) {
        CustomItemData data = CustomItemLoader.getData(stack);
        int max = maxDurability(data);
        if (max <= 0) {
            return 13;
        }
        int remaining = remainingDurability(stack, data);
        return remaining <= 0 ? 1 : Math.max(1, Math.round(13.0F * remaining / max));
    }

    /** 耐久条颜色（绿 → 黄 → 红，与原版 {@code Item#getBarColor} 同一算法）。 */
    public static int durabilityBarColor(ItemStack stack) {
        CustomItemData data = CustomItemLoader.getData(stack);
        int max = maxDurability(data);
        if (max <= 0) {
            return 0xFF00FF00;
        }
        float ratio = (float) remainingDurability(stack, data) / (float) max;
        return Mth.hsvToRgb(Math.max(0.0F, Math.min(1.0F, ratio)) / 3.0F, 1.0F, 1.0F);
    }

    /**
     * 消耗耐久。
     *
     * <p>
     * 耐久上限实时读配置（改完配置重载立即生效），已用次数存在物品堆的
     * {@link SREDataComponentTypes#CUSTOM_ITEM_DAMAGE} 组件上。
     *
     * @return 本次消耗后物品是否已损坏（损坏时物品堆已被清空）
     */
    public static boolean consumeDurability(ServerPlayer player, ItemStack stack, CustomItemData data, int amount) {
        int max = maxDurability(data);
        if (max <= 0 || stack == null || stack.isEmpty() || amount <= 0) {
            return false;
        }
        int used = usedDurability(stack) + amount;
        if (used < max) {
            stack.set(SREDataComponentTypes.CUSTOM_ITEM_DAMAGE, used);
            if (player != null) {
                player.containerMenu.broadcastChanges();
            }
            return false;
        }
        breakByDurability(player, stack);
        return true;
    }

    /** 耐久耗尽：播放碎裂音效 + 提示使用提示 + 移除整个物品堆。 */
    private static void breakByDurability(ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (player != null) {
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8F, 1.0F);
            player.displayClientMessage(
                    Component.translatable("sre.custom_item.broken"),
                    true);
        }
        stack.remove(SREDataComponentTypes.CUSTOM_ITEM_DAMAGE);
        stack.shrink(stack.getCount());
    }

    // ==================== 手铐类物品 ====================

    /** 自定义手铐占用的特殊栏位键前缀（每种自定义手铐一个槽位，互不覆盖）。 */
    public static final String CUFF_SLOT_PREFIX = "custom_cuff_";

    /** 手铐类结算间隔（「每秒」的换算基准）。 */
    private static final int CUFF_TICK_INTERVAL = 20;

    /** 「移动时消耗耐久」用：记录上一秒所在位置（走路与疾跑都会产生位移）。 */
    private static final Map<UUID, Vec3> LAST_POS = new ConcurrentHashMap<>();

    /** 某个自定义手铐对应的特殊栏位键。 */
    public static ResourceLocation cuffSlot(CustomItemData data) {
        return SRE.id(CUFF_SLOT_PREFIX + (data == null || data.id == null ? "unknown" : data.id));
    }

    /** 玩家身上被铐住的那份自定义手铐（没有则 {@link ItemStack#EMPTY}）。 */
    public static ItemStack getCuffOn(Player player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        for (Map.Entry<ResourceLocation, ItemStack> entry : ExtraSlotComponent.KEY.get(player).SLOTS.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().getPath().startsWith(CUFF_SLOT_PREFIX)) {
                continue;
            }
            CustomItemData data = CustomItemLoader.getData(entry.getValue());
            if (data != null && data.kind() == CustomItemData.Kind.CUFF) {
                return entry.getValue();
            }
        }
        return ItemStack.EMPTY;
    }

    /** 玩家身上自定义手铐的配置（没有则 null）。 */
    public static CustomItemData getCuffData(Player player) {
        return CustomItemLoader.getData(getCuffOn(player));
    }

    /** 是否被「自定义手铐」铐住（不含原版手铐）。 */
    public static boolean isCuffed(Player player) {
        return getCuffData(player) != null;
    }

    /**
     * 玩家死亡时把身上的自定义手铐取下来（手铐<b>自动消失</b>，不掉落）。
     *
     * <p>
     * 光靠 {@code ExtraSlotComponent} 的 {@code NEVER_COPY} 不够：那是复活 / 重置时才清空，
     * 而死亡到复活这段时间玩家已经是旁观者，手铐还挂在他身上会继续给他挂药水效果。
     * 两个死亡事件（有 / 无击杀者）都登记，第二次调用取不到手铐，直接返回。
     */
    private static void removeCuffOnDeath(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.level().isClientSide()) {
            return;
        }
        ItemStack cuff = getCuffOn(serverPlayer);
        CustomItemData data = CustomItemLoader.getData(cuff);
        if (data == null || data.kind() != CustomItemData.Kind.CUFF) {
            return;
        }
        ExtraSlotComponent.removeSlot(serverPlayer, cuffSlot(data));
        clearCuffEffects(serverPlayer, data);
        LAST_POS.remove(serverPlayer.getUUID());
    }

    /** 右键玩家：把这份自定义手铐铐进目标玩家的特殊栏位。 */
    public static InteractionResult cuffPlayer(ServerPlayer user, ItemStack stack, CustomItemData data, Player target) {
        if (!(target instanceof ServerPlayer targetPlayer) || user == targetPlayer) {
            return InteractionResult.PASS;
        }
        // 旁观者（含已死亡的玩家）不能被铐住
        if (targetPlayer.isSpectator()) {
            return InteractionResult.PASS;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(user) || !GameUtils.isPlayerAliveAndSurvival(targetPlayer)) {
            return InteractionResult.PASS;
        }
        if (isCuffed(targetPlayer) || HandCuffsItem.hasHandCuff(targetPlayer)) {
            user.displayClientMessage(
                    Component.translatable("sre.custom_item.cuff.already"), true);
            return InteractionResult.FAIL;
        }
        // 拷过去的是手上这份的副本（已消耗的耐久跟着走）
        ItemStack cuffStack = stack.copy();
        cuffStack.setCount(1);
        // 记录施加者：被铐住玩家的定时指令里 <attacker> 指他
        cuffStack.set(SREDataComponentTypes.CUFF_APPLIER, user.getUUID().toString());
        ExtraSlotComponent.setSlot(targetPlayer, cuffSlot(data), cuffStack);
        stack.shrink(1);

        applyCuffEffects(targetPlayer, data);
        applyCuffRestriction(targetPlayer, data);

        user.displayClientMessage(
                Component.translatable("sre.custom_item.cuff.put", targetPlayer.getName().getString()), true);
        targetPlayer.displayClientMessage(
                Component.translatable("sre.custom_item.cuff.received", user.getName().getString()), true);
        return InteractionResult.SUCCESS;
    }

    /** 给被拷住玩家挂上「直到被解除」的药水效果（缺了就补）。 */
    public static void applyCuffEffects(ServerPlayer holder, CustomItemData data) {
        for (CustomItemData.EffectData effect : effectList(data.cuffEffects)) {
            Holder<MobEffect> effectHolder = resolveEffect(effect.effectId);
            if (effectHolder == null) {
                continue;
            }
            int amplifier = Math.max(0, effect.amplifier);
            MobEffectInstance current = holder.getEffect(effectHolder);
            if (current != null && current.isInfiniteDuration() && current.getAmplifier() == amplifier) {
                continue;
            }
            holder.addEffect(new MobEffectInstance(effectHolder, MobEffectInstance.INFINITE_DURATION,
                    amplifier, false, false, true));
        }
    }

    /** 解除手铐时移除配置里带来的药水效果。 */
    public static void clearCuffEffects(ServerPlayer holder, CustomItemData data) {
        for (CustomItemData.EffectData effect : effectList(data.cuffEffects)) {
            Holder<MobEffect> effectHolder = resolveEffect(effect.effectId);
            if (effectHolder != null) {
                holder.removeEffect(effectHolder);
            }
        }
    }

    /** 「是否限制玩家行为」为是时持续施加手铐同款减速（按键屏蔽在客户端 mixin 里）。 */
    private static void applyCuffRestriction(ServerPlayer holder, CustomItemData data) {
        if (data.cuffRestrict && !holder.isSpectator()) {
            holder.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, CUFF_TICK_INTERVAL, 3,
                    false, true, true));
        }
    }

    /**
     * 取下权限：阵营 / 职业 / 修饰符任一命中即可。
     *
     * <p>
     * 三样都没配 = 无人能取（默认）。
     */
    public static boolean canTakeOffCuff(ServerPlayer remover, CustomItemData data) {
        if (remover == null || data == null) {
            return false;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(remover.level());
        SRERole removerRole = gameWorld == null ? null : gameWorld.getRole(remover);
        // 阵营
        String teamName = data.cuffTakeOffTeam == null ? "" : data.cuffTakeOffTeam.trim();
        if (!teamName.isEmpty()) {
            try {
                if (RoleTeam.valueOf(teamName.toUpperCase(Locale.ROOT)).matches(removerRole)) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // 配置里填了不存在的阵营：按没配处理
            }
        }
        // 职业
        for (String roleId : stringList(data.cuffTakeOffRoles)) {
            if (roleMatches(remover, roleId)) {
                return true;
            }
        }
        // 修饰符
        WorldModifierComponent modifierComponent = WorldModifierComponent.KEY.get(remover.level());
        for (String modifierId : stringList(data.cuffTakeOffModifiers)) {
            SREModifier modifier = CustomModifierLoader.findModifier(modifierId);
            if (modifier != null && modifierComponent.isModifier(remover, modifier)) {
                return true;
            }
        }
        return false;
    }

    /** 空手右键被铐玩家：按权限取下，物品交给取下者。 */
    public static InteractionResult takeOffCuff(ServerPlayer remover, ServerPlayer holder) {
        if (remover == null || holder == null || remover == holder) {
            return InteractionResult.PASS;
        }
        ItemStack cuff = getCuffOn(holder);
        CustomItemData data = CustomItemLoader.getData(cuff);
        if (data == null) {
            return InteractionResult.PASS;
        }
        if (!canTakeOffCuff(remover, data)) {
            remover.displayClientMessage(
                    Component.translatable("sre.custom_item.cuff.denied"), true);
            return InteractionResult.FAIL;
        }
        ExtraSlotComponent.removeSlot(holder, cuffSlot(data));
        clearCuffEffects(holder, data);
        if (!RoleUtils.insertStackInFreeSlot(remover, cuff)) {
            // 背包放不下就还给被铐的人，避免物品凭空消失
            holder.getInventory().placeItemBackInInventory(cuff);
        }
        remover.displayClientMessage(
                Component.translatable("sre.custom_item.cuff.takeoff", holder.getName().getString()), true);
        holder.displayClientMessage(
                Component.translatable("sre.custom_item.cuff.takeoff.self", remover.getName().getString()), true);
        return InteractionResult.SUCCESS;
    }

    /** 被铐住玩家的每 tick 处理：药水维持 / 限行 / 定时指令 / 耐久消耗。 */
    private static void tickCuffs(MinecraftServer server) {
        long now = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ItemStack cuff = getCuffOn(player);
            CustomItemData data = CustomItemLoader.getData(cuff);
            if (data == null || data.kind() != CustomItemData.Kind.CUFF) {
                LAST_POS.remove(player.getUUID());
                continue;
            }
            // 旁观者（含已死亡的玩家）：手铐自动消失，且不再给他挂效果 / 限行 / 定时指令
            if (player.isSpectator()) {
                ExtraSlotComponent.removeSlot(player, cuffSlot(data));
                clearCuffEffects(player, data);
                LAST_POS.remove(player.getUUID());
                continue;
            }
            applyCuffEffects(player, data);
            applyCuffRestriction(player, data);

            // 定时指令：目标就是被铐住者本人，<attacker> = 铐住他的人
            int interval = Math.max(1, data.cuffCommandIntervalTicks);
            if (now % interval == 0) {
                CustomItemLoader.executeCommands(data.cuffCommands, player, cuffApplier(server, cuff));
            }

            // 耐久消耗：每秒最多结算一次
            if (now % CUFF_TICK_INTERVAL != 0) {
                continue;
            }
            if (consumeCuffDurability(player, cuff, data)) {
                // 耐久耗尽 → 手铐碎裂，自动解除
                ExtraSlotComponent.removeSlot(player, cuffSlot(data));
                clearCuffEffects(player, data);
            }
        }
    }

    /**
     * 手铐的施加者（谁把它拷上去的）。
     *
     * <p>
     * 施加时会把施加者 UUID 写在手铐物品堆上，这里按 UUID 取在线玩家；
     * 没记录（旧存档 / 手改物品）或施加者已离线时返回 null，此时 {@code <attacker>} 退化为被铐者本人。
     */
    private static ServerPlayer cuffApplier(MinecraftServer server, ItemStack cuff) {
        if (cuff == null || cuff.isEmpty()) {
            return null;
        }
        String uuid = cuff.get(SREDataComponentTypes.CUFF_APPLIER);
        if (uuid == null || uuid.isBlank()) {
            return null;
        }
        try {
            return server.getPlayerList().getPlayer(UUID.fromString(uuid));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 手铐耐久消耗（每秒结算一次）。
     *
     * @return 手铐是否已因耐久耗尽而损坏
     */
    private static boolean consumeCuffDurability(ServerPlayer holder, ItemStack cuff, CustomItemData data) {
        boolean consume = switch (data.cuffWearMode()) {
            case NONE -> false;
            case WORN -> true;
            case CROUCH -> holder.isShiftKeyDown();
            case MOVE -> {
                // 走路与疾跑都算：比较上一秒的位置位移
                Vec3 last = LAST_POS.put(holder.getUUID(), holder.position());
                yield last != null && last.distanceTo(holder.position()) > 0.05D;
            }
        };
        return consume && consumeDurability(holder, cuff, data, 1);
    }

    /** 药水效果列表（null 安全）。 */
    private static List<CustomItemData.EffectData> effectList(List<CustomItemData.EffectData> list) {
        return list == null ? List.of() : list;
    }

    /** 字符串列表（null 安全）。 */
    private static List<String> stringList(List<String> list) {
        return list == null ? List.of() : list;
    }

    /** 把配置里填的药水效果 id 解析成效果（解析失败返回 null）。 */
    private static Holder<MobEffect> resolveEffect(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        ResourceLocation location = ResourceLocation.tryParse(id.trim());
        if (location == null) {
            return null;
        }
        return BuiltInRegistries.MOB_EFFECT.getHolder(location).orElse(null);
    }

    // ==================== 工具方法 ====================

    /** 播放枪械开火音效（配置里填的是音效 id，默认左轮手枪开火）。 */
    private static void playFireSound(ServerPlayer shooter, CustomItemData data) {
        SoundEvent sound = resolveSound(data.fireSound, TMMSounds.ITEM_REVOLVER_SHOOT);
        if (sound == null) {
            return;
        }
        shooter.serverLevel().playSound(null, shooter.getX(), shooter.getEyeY(), shooter.getZ(),
                sound, SoundSource.PLAYERS, 5.0F, 0.7F + shooter.getRandom().nextFloat() * 0.1F - 0.05F);
    }

    /** 把配置里填的音效 id 解析成 {@link SoundEvent}（解析失败回退默认音效）。 */
    public static SoundEvent resolveSound(String id, SoundEvent fallback) {
        if (id == null || id.isBlank()) {
            return fallback;
        }
        ResourceLocation location = ResourceLocation.tryParse(id.trim());
        if (location == null) {
            return fallback;
        }
        return BuiltInRegistries.SOUND_EVENT.getOptional(location).orElse(fallback);
    }

    /**
     * 自定义枪械 / 指向型道具的合法目标。
     *
     * <p>
     * 判定与模组自带枪械对齐（见 {@code GunShootPayload.Receiver}：只要求目标是别的
     * {@link ServerPlayer}），<b>不再</b>额外要求 {@code GameUtils.isPlayerAliveAndSurvival}
     * （= 非创造 / 非旁观）。之前多这一层会让「创造 / 旁观状态的真实玩家」被射线静默穿过：
     * 不执行命中指令、不计命中次数，于是 {@code hitsToFinal} 永远到不了，最终效果与最终冷却都不会触发，
     * 而同一个目标用原版左轮是打得到的，排查时极易被误判成「物品没生效」。
     *
     * <p>
     * 只有<b>旁观者</b>是例外：旁观（含死亡后的玩家）永远不是合法目标，所有自定义道具
     * （枪械射线 / 范围与指向型道具）默认都不对旁观者生效，避免「人已经出局却还被道具打」。
     * 创造模式的真实玩家仍然可被命中（保持上面的口径）。
     */
    private static boolean isValidTarget(ServerPlayer shooter, Entity entity) {
        return entity instanceof ServerPlayer player && player != shooter && !player.isSpectator();
    }

    /**
     * 让某件自定义物品进入冷却。
     *
     * <p>
     * 所有自定义物品共用同一个注册物品，用原版 {@code ItemCooldowns}（按 {@code Item} 记）会让
     * 不同自定义物品互相顶掉冷却，所以这里按「玩家 + 物品 id」记在
     * {@link CustomItemCooldownComponent} 上；只有拿不到配置的异常数据才退回原版冷却。
     */
    private static void applyCooldown(ServerPlayer player, ItemStack stack, int ticks) {
        if (ticks <= 0) {
            return;
        }
        CustomItemData data = CustomItemLoader.getData(stack);
        if (data == null || data.id == null || data.id.isEmpty()) {
            player.getCooldowns().addCooldown(stack.getItem(), ticks);
            return;
        }
        CustomItemCooldownComponent.KEY.get(player).setCooldown(data.id, ticks);
    }

    /** 该玩家手上这件自定义物品是否在冷却中（按物品 id 查，不共用原版冷却）。 */
    public static boolean isOnCooldown(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return false;
        }
        CustomItemData data = CustomItemLoader.getData(stack);
        if (data == null || data.id == null || data.id.isEmpty()) {
            return player.getCooldowns().isOnCooldown(stack.getItem());
        }
        return CustomItemCooldownComponent.KEY.get(player).isOnCooldown(data.id);
    }

    private static void consumeItem(ServerPlayer player, ItemStack stack, boolean consume) {
        if (consume && !player.isCreative()) {
            stack.shrink(1);
        }
    }

    /** 把配置里填的死亡原因 id 解析成 {@link ResourceLocation}（解析失败回退默认）。 */
    public static ResourceLocation parseDeathReason(String value, ResourceLocation fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(value.trim());
        return parsed == null ? fallback : parsed;
    }

    // ==================== 投掷物 ====================

    /** 投出（需要拉栓时由蓄力完成触发，否则直接投掷，两条路都走这里）。 */
    public static void throwCustom(ServerPlayer user, ItemStack stack, CustomItemData data) {
        if (isOnCooldown(user, stack)) {
            return;
        }
        // 与手榴弹一致的投掷 / 拉栓音效
        user.serverLevel().playSound(null, user.getX(), user.getY(), user.getZ(), TMMSounds.ITEM_GRENADE_THROW,
                SoundSource.PLAYERS, 0.5F, 1.0F + (user.getRandom().nextFloat() - 0.5F) / 10.0F);

        CustomThrowableEntity entity = new CustomThrowableEntity(TMMEntities.CUSTOM_THROWABLE, user, user.level());
        entity.setItem(stack.copyWithCount(1));
        entity.shootFromRotation(user, user.getXRot(), user.getYRot(), 0.0F, 1.0F, 1.0F);
        user.level().addFreshEntity(entity);

        // 耐久：每次投出算一次使用（耗尽时物品已碎裂）
        if (consumeDurability(user, stack, data, 1)) {
            return;
        }
        stack.shrink(1);
    }

    /** 钳子拆除入口（由空手 / 钳子右键投掷物触发）。 */
    public static InteractionResult beginDefuse(ServerPlayer user, CustomThrowableEntity charge) {
        CustomItemData data = CustomItemLoader.getData(charge.getItem());
        if (data == null) {
            return InteractionResult.PASS;
        }
        if (!data.throwDefusable) {
            user.displayClientMessage(
                    Component.translatable("sre.custom_item.throw.not_defusable"),
                    true);
            return InteractionResult.FAIL;
        }
        if (charge.isDefusing()) {
            return InteractionResult.FAIL;
        }
        if (data.throwDefuseTicks <= 0) {
            // 拆除时间为 0：直接拆除（同粘性炸弹）
            charge.defuseInstantly();
            user.displayClientMessage(
                    Component.translatable("sre.custom_item.throw.defused"), true);
            return InteractionResult.SUCCESS;
        }
        charge.beginDefuse(user, defuseFailPercent(user, data));
        user.displayClientMessage(
                Component.translatable("sre.custom_item.throw.defusing"), true);
        return InteractionResult.SUCCESS;
    }

    /** 拆除失败概率：按配置的职业 id 取第一个命中的规则，没配则不会失败。 */
    private static int defuseFailPercent(ServerPlayer user, CustomItemData data) {
        for (CustomItemData.DefuseFailRule rule : defuseRules(data.throwDefuseFailRules)) {
            String roleId = rule.roleId == null ? "" : rule.roleId.trim();
            if (roleId.isEmpty() || roleMatches(user, roleId)) {
                return Math.max(0, Math.min(100, rule.failPercent));
            }
        }
        return 0;
    }

    private static List<CustomItemData.DefuseFailRule> defuseRules(List<CustomItemData.DefuseFailRule> rules) {
        return rules == null ? List.of() : rules;
    }

    /** 对目标施加一批药水效果（投掷物触发 / 区域触发共用）。 */
    public static void applyEffects(ServerPlayer target, List<CustomItemData.EffectData> effects) {
        for (CustomItemData.EffectData effect : effectList(effects)) {
            Holder<MobEffect> effectHolder = resolveEffect(effect.effectId);
            if (effectHolder == null) {
                continue;
            }
            target.addEffect(new MobEffectInstance(effectHolder, Math.max(1, effect.durationSeconds) * 20,
                    Math.max(0, effect.amplifier), false, true, true));
        }
    }

    /** 把配置里填的粒子 id 解析成粒子（解析失败回退默认粒子）。 */
    public static ParticleOptions resolveParticle(String id, ParticleOptions fallback) {
        if (id == null || id.isBlank()) {
            return fallback;
        }
        ResourceLocation location = ResourceLocation.tryParse(id.trim());
        if (location == null) {
            return fallback;
        }
        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.get(location);
        return type instanceof ParticleOptions options ? options : fallback;
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
        // 命中标记挂在玩家自己的组件上，随实体一起消失，这里只需要清理按 UUID 建表的运行时状态
        CHARGE_FIRED.keySet().removeIf(key -> key.startsWith(prefix));
        AUTO_FIRES.keySet().removeIf(key -> key.startsWith(prefix));
        CLIENT_AUTO_FIRE.keySet().removeIf(key -> key.startsWith(prefix));
    }

    /** 供物品类复用：清理玩家全部状态（重载 / 开局）。 */
    public static void clearAll() {
        CHARGE_FIRED.clear();
        AUTO_FIRES.clear();
        CLIENT_AUTO_FIRE.clear();
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
