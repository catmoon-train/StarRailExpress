package org.agmas.noellesroles.role.bouns.roles;

import io.wifi.starrailexpress.api.EggRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.PlayerStaminaGetter;
import io.wifi.starrailexpress.util.ShopEntry;
import io.wifi.starrailexpress.api.data.RoleData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.bouns.BounsRoles;
import org.agmas.noellesroles.role_data.innocence.FatFishRoleData;

import java.util.ArrayList;
import java.util.List;

/**
 * 大肥鱼（DeepSeek 娘化形象）—— 彩蛋职业 · 乘客阵营，全地图刷新。
 *
 * <p>
 * 六项玩法：
 * <ol>
 * <li><b>鲸歌</b>：按技能发出一声鲸叫，半径内的其他玩家被震慑（定身 + 禁用手持 + 减速）；</li>
 * <li><b>摆尾冲刺</b>：向前摆尾冲刺，撞到的人被顶开；</li>
 * <li><b>浑水鱼</b>：被动 —— 在水里不缺氧、游得更快、体力恢复更快（见 {@code FatFishRoleData}）；</li>
 * <li><b>圆滚滚</b>：被动 —— 抗击退，且移动时会把撞到的人顶开（见 {@code FatFishRoleData}）；</li>
 * <li><b>投喂团子</b>：其他玩家拿着食物右键它，给它回体力并挂短时再生（{@link #feed}）；</li>
 * <li><b>专属商店</b>：{@link #getShopEntries()} 卖鱼干 / 茶</li>
 * </ol>
 *
 * <p>
 * 另外大肥鱼**免疫阴谋家的猜测**（{@code ConspiratorRoleData#makeGuess} 里直接拒绝）。
 */
public class FatFishRole extends EggRole {

    // ── 鲸歌 ──
    /** 鲸歌作用半径（格） */
    public static final double SONG_RADIUS = 8.0D;
    /** 震慑时长（定身 / 禁用手持） */
    public static final int SONG_STUN_TICKS = GameConstants.getInTicks(0, 2);
    /** 减速时长 */
    public static final int SONG_SLOW_TICKS = GameConstants.getInTicks(0, 5);

    // ── 摆尾冲刺 ──
    /** 冲刺初速度（格 / tick） */
    public static final double DASH_SPEED = 1.15D;
    /** 冲刺撞飞判定范围（格） */
    public static final double DASH_HIT_RANGE = 2.4D;
    /** 冲刺撞飞力度 */
    public static final double DASH_PUSH_STRENGTH = 1.5D;

    // ── 投喂团子 ──
    /** 被投喂一次给大肥鱼补的体力 */
    public static final float EAT_AURA_STAMINA = 80.0F;
    /** 吃东西给范围内每个人挂的时长 */
    public static final int EAT_AURA_SPEED_TICKS = GameConstants.getInTicks(0, 5);
    /** 被投喂后的「消化」冷却（秒）：这段时间内不能再被投喂 */
    public static final int FEED_COOLDOWN_SECONDS = 15;
    /** 消化冷却（tick） */
    public static final int FEED_COOLDOWN_TICKS = FEED_COOLDOWN_SECONDS * 20;

    public FatFishRole(ResourceLocation identifier, int color, RoleType roleType,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, roleType, moodType, maxSprintTime, canSeeTime);
    }

    public static boolean isFatFish(Player player) {
        if (player == null || player.level() == null) {
            return false;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        return game != null && game.isRole(player, BounsRoles.FAT_FISH);
    }

    // ==================== 专属商店 ====================

    @Override
    public List<ShopEntry> getShopEntries() {
        // 双端都会调用（客户端画商店 / 图标），所以这里不要碰服务端专用东西
        List<ShopEntry> shop = new ArrayList<>();
        shop.add(new ShopEntry(new ItemStack(ModItems.SMALL_DRIED_FISH), 100, ShopEntry.Type.TOOL));
        shop.add(new ShopEntry(new ItemStack(ModItems.CALMING_TEA), 150, ShopEntry.Type.TOOL));
        return shop;
    }

    // ==================== 技能：鲸歌 ====================

    public static boolean triggerSong(RoleSkill.RoleSkillContext ctx) {
        ServerPlayer player = ctx.player();
        if (player.isSpectator()) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.6F, 0.65F);
        player.displayClientMessage(
                Component.translatable("message.noellesroles.fat_fish.song").withStyle(ChatFormatting.AQUA), true);
        for (ServerPlayer target : level.players()) {
            if (target == player || !GameUtils.isPlayerAliveAndSurvival(target)) {
                continue;
            }
            if (target.distanceTo(player) > SONG_RADIUS) {
                continue;
            }
            target.addEffect(ModEffects.of(ModEffects.MOVE_BANED, SONG_STUN_TICKS, 0, false, true, true));
            target.addEffect(ModEffects.of(ModEffects.USED_BANED, SONG_STUN_TICKS, 0, false, true, true));
            target.addEffect(ModEffects.of(MobEffects.MOVEMENT_SLOWDOWN, SONG_SLOW_TICKS, 1, false, true, true));
        }
        return true;
    }

    // ==================== 技能：摆尾冲刺 ====================

    public static boolean triggerTailDash(RoleSkill.RoleSkillContext ctx) {
        ServerPlayer player = ctx.player();
        if (player.isSpectator()) {
            return false;
        }
        Vec3 look = player.getLookAngle();
        Vec3 dash = new Vec3(look.x, 0.0D, look.z);
        if (dash.lengthSqr() < 1.0E-4D) {
            return false;
        }
        dash = dash.normalize();
        // 冲刺位移：服务端改速度 + 发包，客户端才会真的被推出去（同击退的处理）
        player.setDeltaMovement(dash.x * DASH_SPEED, Math.max(player.getDeltaMovement().y, 0.2D),
                dash.z * DASH_SPEED);
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));

        // 撞飞正前方的人
        for (ServerPlayer target : player.serverLevel().players()) {
            if (target == player || !GameUtils.isPlayerAliveAndSurvival(target)) {
                continue;
            }
            Vec3 to = new Vec3(target.getX() - player.getX(), 0.0D, target.getZ() - player.getZ());
            double dist = to.length();
            if (dist > DASH_HIT_RANGE || dist < 1.0E-4D) {
                continue;
            }
            if (dash.dot(to.scale(1.0D / dist)) < 0.35D) {
                continue; // 只撞正前方约 ±70°
            }
            target.push(dash.x * DASH_PUSH_STRENGTH, 0.35D, dash.z * DASH_PUSH_STRENGTH);
            target.hurtMarked = true;
            target.connection.send(new ClientboundSetEntityMotionPacket(target));
        }
        return true;
    }

    // ==================== 被动：投喂团子（别人拿食物右键喂它） ====================

    /**
     * 注册「投喂团子」：**其他玩家拿着食物右键大肥鱼本体**，给它回体力并挂短时再生。
     *
     * <p>
     * 沿用仓库里 {@code UseEntityCallback} 的惯例：客户端直接 PASS（让交互包正常发出去、
     * 正常摆手），只在服务端真正结算，并消耗投喂者手里的一份食物。
     */
    public static void registerEvents() {
        UseEntityCallback.EVENT.register((feeder, level, hand, entity, hitResult) -> {
            if (level.isClientSide()) {
                return InteractionResult.PASS;
            }
            if (hand != InteractionHand.MAIN_HAND || !(entity instanceof Player fish) || fish == feeder) {
                return InteractionResult.PASS;
            }
            if (!isFatFish(fish) || !GameUtils.isPlayerAliveAndSurvival(fish)) {
                return InteractionResult.PASS;
            }
            ItemStack food = feeder.getItemInHand(hand);
            if (food.isEmpty() || !food.has(DataComponents.FOOD)) {
                // 必须是能吃的：防呆也防「空手白嫖治疗」
                return InteractionResult.PASS;
            }
            FatFishRoleData data = RoleData.getNullable(FatFishRoleData.class, fish);
            if (data == null) {
                return InteractionResult.PASS;
            }
            if (!data.canBeFed()) {
                // 大肥鱼还在消化：不消耗食物，只告诉投喂者还要等多久
                feeder.displayClientMessage(
                        Component.translatable("message.noellesroles.fat_fish.digesting",
                                String.format("%d", (data.feedCooldownRemainingTicks() + 19L) / 20L))
                                .withStyle(ChatFormatting.RED),
                        true);
                return InteractionResult.FAIL;
            }
            data.markFed();
            feed(fish, feeder, food);
            return InteractionResult.SUCCESS;
        });
    }

    /**
     * 投喂结算：给大肥鱼回体力 + 速度光环（体力两端各自模拟，所以只在服务端改，
     * 由原版把体力/效果同步给客户端），并扣掉投喂者手里的一份食物。
     */
    public static void feed(Player fish, Player feeder, ItemStack food) {
        restoreStamina(fish, EAT_AURA_STAMINA);
        for (final var p : fish.level().players()) {
            p.addEffect(ModEffects.of(MobEffects.MOVEMENT_SPEED, EAT_AURA_SPEED_TICKS, 1,
                    false, true, true));
        }
        fish.level().playSound(null, fish.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.MASTER, 1f, 1f);
        fish.displayClientMessage(
                Component.translatable("message.noellesroles.fat_fish.fed", feeder.getName())
                        .withStyle(ChatFormatting.LIGHT_PURPLE),
                true);
        feeder.displayClientMessage(
                Component.translatable("message.noellesroles.fat_fish.feed", fish.getName())
                        .withStyle(ChatFormatting.AQUA),
                true);
        if (!feeder.isCreative()) {
            food.shrink(1);
        }
    }

    // ==================== 体力小工具 ====================

    /** 补充体力（不含体力上限效果时的上限也要算进去），两端都会调用 */
    public static void restoreStamina(Player player, float amount) {
        if (!(player instanceof PlayerStaminaGetter stamina) || ModEffects.hasInfiniteStamina(player)) {
            return;
        }
        SRERole role = ScoutRole.roleOf(player);
        float max = Float.MAX_VALUE;
        if (role != null) {
            int base = role.getMaxSprintTime(player);
            if (base >= 0 && base != Integer.MAX_VALUE) {
                max = base * ModEffects.getStaminaCapacityMultiplier(player);
            }
        }
        if (max == Float.MAX_VALUE) {
            return;
        }
        float current = stamina.starrailexpress$getStamina();
        if (current < 0f) {
            current = max;
        }
        stamina.starrailexpress$setStamina(Math.min(max, current + amount));
    }

    /** 体力上限（供被动恢复判断），不受限制时返回 {@link Float#MAX_VALUE} */
    public static float maxStamina(Player player) {
        SRERole role = ScoutRole.roleOf(player);
        if (role == null) {
            return Float.MAX_VALUE;
        }
        int base = role.getMaxSprintTime(player);
        if (base < 0 || base == Integer.MAX_VALUE) {
            return Float.MAX_VALUE;
        }
        return base * ModEffects.getStaminaCapacityMultiplier(player);
    }

    /** 兼容工具：让外部也能按职业对象判断 */
    public static boolean isFatFish(SRERole role) {
        return role != null && role.identifier().equals(BounsRoles.FAT_FISH_ID);
    }

    /** 供被动逻辑复用：给玩家挂短时效果 */
    public static void addTimedEffect(Player player,
            net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
            int ticks, int amplifier) {
        player.addEffect(ModEffects.of(effect, ticks, amplifier, false, false, true));
    }

    /** 供被动逻辑复用：一条无粒子、无图标的药水效果（省得每处都写 6 个参数） */
    public static MobEffectInstance quietEffect(
            net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int ticks, int amplifier) {
        return ModEffects.of(effect, ticks, amplifier, false, false, false);
    }
}
