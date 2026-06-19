package io.wifi.starrailexpress.content.item;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.TimedGrenadeEntity;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.content.item.GrenadeItem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 滞时雷 — 经典 FPS 延时雷（cook 机制）。
 * <p>
 * 按住右键开始引信倒计时，倒计时结束前松手抛出，
 * 抛出后在地面反弹，剩余时间归零时爆炸。
 * 若手持超过引信时间，会在手中爆炸。
 */
public class TimedGrenadeItem extends SkinableItem {
    /** 总引信时间（ticks），4.5 秒 */
    public static final int FUSE_TICKS = 90;
    /** 最大蓄力时间（ticks），与引信时间相同 */
    public static final int MAX_CHARGE_TIME = FUSE_TICKS;

    /** 正在烹饪的玩家 → 引爆截止 tick（游戏时间） */
    private static final Map<UUID, Long> COOKING_PLAYERS = new ConcurrentHashMap<>();

    private static boolean tickRegistered = false;

    public TimedGrenadeItem(Item.Properties settings) {
        super(settings);
        registerTickHandler();
    }

    private static void registerTickHandler() {
        if (tickRegistered) return;
        tickRegistered = true;
        ServerTickEvents.END_WORLD_TICK.register(TimedGrenadeItem::onWorldTick);
    }

    /**
     * 每 tick 检测正在烹饪的玩家是否引信到期 → 手中爆炸
     */
    private static void onWorldTick(ServerLevel world) {
        long now = world.getGameTime();
        var iter = COOKING_PLAYERS.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            long detonateAt = entry.getValue();
            if (now >= detonateAt) {
                iter.remove();
                ServerPlayer player = world.getServer().getPlayerList().getPlayer(entry.getKey());
                if (player != null && player.isAlive() && player.isHolding(item ->
                        item.getItem() instanceof TimedGrenadeItem)) {
                    explodeInHand(player, world);
                }
            }
        }
    }

    private static void explodeInHand(ServerPlayer player, ServerLevel world) {
        world.playSound(null, player.blockPosition(), TMMSounds.ITEM_GRENADE_EXPLODE,
                SoundSource.PLAYERS, 5f, 1f);
        world.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                player.getX(), player.getY() + 1.0, player.getZ(), 1, 0, 0, 0, 0);
        // 自爆：清除手中物品并击杀
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof TimedGrenadeItem) {
                stack.shrink(1);
                break;
            }
        }
        GameUtils.killPlayer(player, true, null, GameConstants.DeathReasons.SELF_EXPLOSION);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        user.startUsingItem(hand);

        if (!world.isClientSide && world instanceof ServerLevel serverLevel) {
            long detonateAt = serverLevel.getGameTime() + FUSE_TICKS;
            COOKING_PLAYERS.put(user.getUUID(), detonateAt);
            world.playSound(null, user.blockPosition(), SoundEvents.LEVER_CLICK,
                    SoundSource.PLAYERS, 0.6F, 1.6F);
        }
        return InteractionResultHolder.consume(itemStack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
        UUID uuid = user.getUUID();
        COOKING_PLAYERS.remove(uuid);

        if (!world.isClientSide) {
            if (user instanceof Player player && GrenadeItem.isAnyGrenadeOnCooldown(player))
                return;
            if (user instanceof Player player) {
                if (!player.isCreative()
                        && !SREGameWorldComponent.KEY.get(player.level()).isRole(player,
                                SpecialGameModeRoles.SUPER_LOOSE_END)) {
                    GrenadeItem.addGrenadeCooldown(player);
                }
            }

            // 计算剩余引信时间
            int usedTicks = this.getUseDuration(stack, user) - remainingUseTicks;
            usedTicks = Math.min(usedTicks, FUSE_TICKS);
            int remainingFuse = FUSE_TICKS - usedTicks;

            world.playSound(null, user.getX(), user.getY(), user.getZ(), TMMSounds.ITEM_GRENADE_THROW,
                    SoundSource.NEUTRAL, 0.5F, 1.2F + (world.random.nextFloat() - .5f) / 10f);

            TimedGrenadeEntity grenade = new TimedGrenadeEntity(TMMEntities.TIMED_GRENADE, world);
            grenade.setOwner(user);
            grenade.setPosRaw(user.getX(), user.getEyeY() - 0.1, user.getZ());
            grenade.setFuseTicks(remainingFuse);

            // 固定投掷速度（不依赖蓄力，因为蓄力用于烹饪而非力度）
            float velocity = 0.7F;
            grenade.shootFromRotation(user, user.getXRot(), user.getYRot(), 0.0F, velocity, 0.8F);
            world.addFreshEntity(grenade);

            if (SRE.REPLAY_MANAGER != null) {
                SRE.REPLAY_MANAGER.recordItemUse(user.getUUID(), BuiltInRegistries.ITEM.getKey(this));
            }
        }
        stack.consume(1, user);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack itemStack, Level level, LivingEntity livingEntity) {
        COOKING_PLAYERS.remove(livingEntity.getUUID());
      return   super.finishUsingItem(itemStack, level, livingEntity);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public String getItemSkinType() {
        return "timed_grenade";
    }

    // ─── 客户端查询接口 ───

    /**
     * 获取指定玩家的滞时雷剩余引信 tick 数（客户端 HUD 查询用）
     * @return 剩余 tick，-1 表示未在烹饪
     */
    public static int getRemainingFuse(Level world, UUID playerUUID) {
        Long detonateAt = COOKING_PLAYERS.get(playerUUID);
        if (detonateAt == null) return -1;
        long remaining = detonateAt - world.getGameTime();
        return (int) Math.max(0, remaining);
    }
}
