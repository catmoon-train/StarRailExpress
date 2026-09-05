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

package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.OnGameTrueStarted;
import io.wifi.starrailexpress.event.OnPlayerKilledPlayerIdentifier;
import io.wifi.starrailexpress.event.OnRoleSkillUse;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.SREItemUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.ModifierAssigned;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.modifier.NRModifiers;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;

import java.util.List;

/**
 * 封印物效果：任务收益、SAN、闪避、技能失败、击杀、开局发放。
 * 冷却存在物品 CustomData 上，不使用玩家 CCA。
 */
public final class SealedArtifactHandler {
    public static final ResourceLocation CHOKE = Noellesroles.id("sealed_choke");
    public static final ResourceLocation LIGHTNING = Noellesroles.id("sealed_lightning");
    public static final ResourceLocation MATCH = Noellesroles.id("sealed_match");

    private SealedArtifactHandler() {
    }

    public static void register() {
        OnGameTrueStarted.EVENT.register(SealedArtifactHandler::onGameTrueStarted);
        ModifierAssigned.EVENT.register((player, modifier) -> {
            if (modifier.equals(NRModifiers.SEALED_RELICS)) {
                tryGrant(player);
            }
        });
        OnRoleSkillUse.BEFORE.register((player, role) -> {
            if (!(player instanceof ServerPlayer sp) || !has(sp, ModItems.SEALED_MIRROR_SHARD)) {
                return true;
            }
            if (sp.getRandom().nextFloat() >= 0.12f) {
                return true;
            }
            sp.displayClientMessage(Component.translatable("message.noellesroles.sealed.skill_fail")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        });
        AllowPlayerDeathWithKiller.EVENT.register(SealedArtifactHandler::allowDeath);
        OnPlayerKilledPlayerIdentifier.EVENT.register((victim, killer, deathReason) -> {
            if (killer == null || !has(killer, ModItems.SEALED_THUNDERBOLT_NAIL)) {
                return;
            }
            if (!GameUtils.isPlayerAliveAndSurvival(killer)) {
                return;
            }
            if (killer.getRandom().nextBoolean()) {
                addCoins(killer, 25);
                killer.displayClientMessage(Component.translatable("message.noellesroles.sealed.nail_gold")
                        .withStyle(ChatFormatting.GOLD), true);
            } else {
                killer.addEffect(ModEffects.of(MobEffects.MOVEMENT_SPEED, 5 * 20, 1, false, false, true));
                killer.displayClientMessage(Component.translatable("message.noellesroles.sealed.nail_speed")
                        .withStyle(ChatFormatting.AQUA), true);
            }
        });
    }

    private static void onGameTrueStarted(ServerLevel level) {
        WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(level);
        for (ServerPlayer player : level.players()) {
            if (modifiers.isModifier(player, NRModifiers.SEALED_RELICS)) {
                tryGrant(player);
            }
        }
    }

    public static void tryGrant(Player player) {
        if (!(player instanceof ServerPlayer sp) || !GameUtils.isPlayerAliveAndSurvival(sp) || hasAny(sp)) {
            return;
        }
        ItemStack stack = randomStack(sp.getRandom());
        if (!SREItemUtils.insertStackInFreeSlot(sp, stack)) {
            sp.drop(stack, false);
        }
        sp.displayClientMessage(Component.translatable("message.noellesroles.sealed.granted",
                        stack.getHoverName()).withStyle(ChatFormatting.DARK_PURPLE), false);
    }

    public static ItemStack randomStack(RandomSource random) {
        List<Item> pool = ModItems.SEALED_ARTIFACTS;
        return new ItemStack(pool.get(random.nextInt(pool.size())));
    }

    public static boolean hasAny(Player player) {
        return SREItemUtils.hasItem(player, stack -> stack.getItem() instanceof SealedArtifactItem);
    }

    public static boolean has(Player player, Item item) {
        return SREItemUtils.hasItem(player, item);
    }

    public static void tick(ServerPlayer player, ItemStack stack, SealedArtifactItem item) {
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (game == null || !game.isRunning()) {
            return;
        }
        String key = item.getTranslationKey();
        long gameTime = player.level().getGameTime();
        switch (key) {
            case "sealed_coin_of_echoes" -> tickCoin(player, gameTime);
            case "sealed_blind_lantern" -> tickLantern(player, gameTime);
            case "sealed_rusted_anklet" -> tickAnklet(player, gameTime);
            case "sealed_vanishing_cloak" -> tickCloak(player, stack, gameTime);
            case "sealed_thunderbolt_nail" -> tickNail(player, gameTime);
            case "sealed_hungry_purse" -> tickPurse(player, gameTime);
            case "sealed_whisper_bell" -> tickBell(player, gameTime);
            case "sealed_splintered_compass" -> tickCompass(player, gameTime);
            case "sealed_last_match" -> tickMatch(player, gameTime);
            default -> {
            }
        }
    }

    public static InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand,
            SealedArtifactItem item) {
        ItemStack stack = user.getItemInHand(hand);
        if (level.isClientSide || !(user instanceof ServerPlayer player)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return InteractionResultHolder.fail(stack);
        }
        if (player.getCooldowns().isOnCooldown(item)) {
            return InteractionResultHolder.fail(stack);
        }
        return switch (item.getTranslationKey()) {
            case "sealed_vanishing_cloak" -> useCloak(player, stack, item);
            case "sealed_doorless_key" -> useKey(player, stack, item);
            case "sealed_last_match" -> useMatch(player, stack, item);
            default -> InteractionResultHolder.pass(stack);
        };
    }

    public static void onFinishQuest(Player player, String quest) {
        if (!(player instanceof ServerPlayer sp) || !GameUtils.isPlayerAliveAndSurvival(sp)) {
            return;
        }
        if (has(sp, ModItems.SEALED_COIN_OF_ECHOES)) {
            addCoins(sp, 12);
            sp.displayClientMessage(Component.translatable("message.noellesroles.sealed.echo_gold")
                    .withStyle(ChatFormatting.GOLD), true);
        }
        if (has(sp, ModItems.SEALED_BREATHLESS_BREAD) && ("eat".equals(quest) || "drink".equals(quest))) {
            addCoins(sp, 18);
            SREPlayerMoodComponent.KEY.get(sp).addMood(0.08f);
            sp.displayClientMessage(Component.translatable("message.noellesroles.sealed.bread_task")
                    .withStyle(ChatFormatting.GREEN), true);
        }
    }

    public static void onEat(ServerPlayer player) {
        if (!has(player, ModItems.SEALED_BREATHLESS_BREAD) || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }
        SREPlayerMoodComponent.KEY.get(player).addMood(0.05f);
        RandomSource random = player.getRandom();
        if (random.nextFloat() < 0.012f) {
            player.displayClientMessage(Component.translatable("message.noellesroles.sealed.choke")
                    .withStyle(ChatFormatting.DARK_RED), true);
            GameUtils.killPlayer(player, true, null, CHOKE);
            return;
        }
        if (random.nextFloat() < 0.08f) {
            player.addEffect(ModEffects.of(MobEffects.CONFUSION, 60, 0, false, true, true));
            player.displayClientMessage(Component.translatable("message.noellesroles.sealed.choke_mild")
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    private static boolean allowDeath(Player victim, Player killer, ResourceLocation deathReason) {
        if (!(victim instanceof ServerPlayer sp) || killer == null || killer == victim) {
            return true;
        }
        if (CHOKE.equals(deathReason) || LIGHTNING.equals(deathReason) || MATCH.equals(deathReason)) {
            return true;
        }
        if (has(sp, ModItems.SEALED_HUNGRY_PURSE)) {
            loseCoins(sp, 12);
        }
        if (!has(sp, ModItems.SEALED_MIRROR_SHARD)) {
            return true;
        }
        ItemStack mirror = find(sp, ModItems.SEALED_MIRROR_SHARD);
        if (mirror == null) {
            return true;
        }
        long now = GameUtils.getTicksFromGameStart(sp.level());
        if (now < getLong(mirror, "dodge_until")) {
            return true;
        }
        if (sp.getRandom().nextFloat() >= 0.18f) {
            return true;
        }
        setLong(mirror, "dodge_until", now + 160);
        sp.displayClientMessage(Component.translatable("message.noellesroles.sealed.dodge")
                .withStyle(ChatFormatting.AQUA), true);
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 0.8f, 1.4f);
        return false;
    }

    private static void tickCoin(ServerPlayer player, long gameTime) {
        if (gameTime % 20 != 0) {
            return;
        }
        SREPlayerMoodComponent.KEY.get(player).addMood(-GameConstants.MOOD_DRAIN * 20);
    }

    private static void tickLantern(ServerPlayer player, long gameTime) {
        if (gameTime % 160 != player.getId() % 160) {
            return;
        }
        float mood = SREPlayerMoodComponent.KEY.get(player).getMood();
        int darkTicks = mood < 0.4f ? 80 : 40;
        int amp = mood < 0.4f ? 1 : 0;
        player.addEffect(ModEffects.of(MobEffects.DARKNESS, darkTicks, amp, false, false, true));
        AABB box = player.getBoundingBox().inflate(12);
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other == player || !GameUtils.isPlayerAliveAndSurvival(other) || !box.contains(other.position())) {
                continue;
            }
            if (other.hasEffect(MobEffects.INVISIBILITY) || other.isShiftKeyDown()) {
                other.addEffect(ModEffects.of(MobEffects.GLOWING, 40, 0, false, false, true));
            }
        }
        player.displayClientMessage(Component.translatable("message.noellesroles.sealed.lantern")
                .withStyle(ChatFormatting.GRAY), true);
    }

    private static void tickAnklet(ServerPlayer player, long gameTime) {
        player.addEffect(ModEffects.of(MobEffects.MOVEMENT_SPEED, 40, 0, false, false, false));
        if (!player.isSprinting() || gameTime % 40 != 0) {
            return;
        }
        RandomSource random = player.getRandom();
        if (random.nextFloat() < 0.20f) {
            player.addEffect(ModEffects.of(MobEffects.MOVEMENT_SPEED, 25, 1, false, false, true));
        } else if (random.nextFloat() < 0.18f) {
            player.addEffect(ModEffects.of(MobEffects.MOVEMENT_SLOWDOWN, 16, 1, false, true, true));
            player.addEffect(ModEffects.of(MobEffects.CONFUSION, 16, 0, false, false, true));
            player.addEffect(ModEffects.of(ModEffects.MOVE_BANED, 10, 0, false, false, true));
            player.displayClientMessage(Component.translatable("message.noellesroles.sealed.stumble")
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    private static void tickCloak(ServerPlayer player, ItemStack stack, long gameTime) {
        if (gameTime % 20 == 0) {
            SREPlayerMoodComponent.KEY.get(player).addMood(-GameConstants.MOOD_DRAIN * 15);
        }
        long now = GameUtils.getTicksFromGameStart(player.level());
        long until = getLong(stack, "cloak_until");
        if (until > 0 && now >= until) {
            setLong(stack, "cloak_until", 0);
            player.removeEffect(MobEffects.INVISIBILITY);
            player.addEffect(ModEffects.of(MobEffects.GLOWING, 80, 0, false, true, true));
            SREPlayerMoodComponent.KEY.get(player).addMood(-0.12f);
            player.displayClientMessage(Component.translatable("message.noellesroles.sealed.cloak_expose")
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    private static void tickNail(ServerPlayer player, long gameTime) {
        if (gameTime % 160 != player.getId() % 160) {
            return;
        }
        if (!player.level().canSeeSky(player.blockPosition()) || player.getRandom().nextFloat() >= 0.04f) {
            return;
        }
        spawnLightning(player.serverLevel(), player.position());
        if (player.getRandom().nextFloat() < 0.45f) {
            player.displayClientMessage(Component.translatable("message.noellesroles.sealed.struck")
                    .withStyle(ChatFormatting.GOLD), true);
            GameUtils.killPlayer(player, true, null, LIGHTNING);
        } else {
            player.displayClientMessage(Component.translatable("message.noellesroles.sealed.near_strike")
                    .withStyle(ChatFormatting.YELLOW), true);
        }
    }

    private static void tickPurse(ServerPlayer player, long gameTime) {
        if (gameTime % 200 != 0) {
            return;
        }
        addCoins(player, 4);
    }

    private static void tickBell(ServerPlayer player, long gameTime) {
        if (gameTime % 240 != player.getId() % 240) {
            return;
        }
        ServerPlayer nearest = nearestAlive(player);
        if (nearest != null) {
            player.displayClientMessage(Component.translatable("message.noellesroles.sealed.bell_whisper",
                            nearest.getName(), worldDir(player, nearest))
                    .withStyle(ChatFormatting.DARK_AQUA), true);
        }
        if (player.getRandom().nextFloat() < 0.12f) {
            player.addEffect(ModEffects.of(MobEffects.DARKNESS, 40, 0, false, false, true));
            player.displayClientMessage(Component.translatable("message.noellesroles.sealed.bell_curse")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
        }
    }

    private static void tickCompass(ServerPlayer player, long gameTime) {
        if (gameTime % 160 != player.getId() % 160) {
            return;
        }
        ServerPlayer killer = nearestKiller(player);
        if (killer == null) {
            player.displayClientMessage(Component.translatable("message.noellesroles.sealed.compass_empty")
                    .withStyle(ChatFormatting.GRAY), true);
        } else {
            player.displayClientMessage(Component.translatable("message.noellesroles.sealed.compass_dir",
                            worldDir(player, killer)).withStyle(ChatFormatting.RED), true);
        }
        if (player.getRandom().nextFloat() < 0.015f) {
            Vec3 dest = player.position().add(
                    (player.getRandom().nextDouble() - 0.5) * 8,
                    0,
                    (player.getRandom().nextDouble() - 0.5) * 8);
            BlockPos stand = findStand(player.serverLevel(), BlockPos.containing(dest));
            if (stand != null) {
                player.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
                player.displayClientMessage(Component.translatable("message.noellesroles.sealed.compass_drift")
                        .withStyle(ChatFormatting.RED), true);
            } else {
                GameUtils.teleportToRandomRoom(player);
            }
            player.level().playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                    SoundSource.PLAYERS, 0.6f, 0.8f);
        }
    }

    private static void tickMatch(ServerPlayer player, long gameTime) {
        if (gameTime % 160 != player.getId() % 160 || player.getRandom().nextFloat() >= 0.05f) {
            return;
        }
        player.setRemainingFireTicks(80);
        player.displayClientMessage(Component.translatable("message.noellesroles.sealed.match_self")
                .withStyle(ChatFormatting.GOLD), true);
        if (player.getRandom().nextFloat() < 0.08f) {
            GameUtils.killPlayer(player, true, null, MATCH);
        }
    }

    private static InteractionResultHolder<ItemStack> useCloak(ServerPlayer player, ItemStack stack, Item item) {
        long now = GameUtils.getTicksFromGameStart(player.level());
        setLong(stack, "cloak_until", now + 6 * 20);
        player.addEffect(ModEffects.of(MobEffects.INVISIBILITY, 6 * 20, 0, false, false, true));
        player.getCooldowns().addCooldown(item, 45 * 20);
        player.displayClientMessage(Component.translatable("message.noellesroles.sealed.cloak_use")
                .withStyle(ChatFormatting.GRAY), true);
        return InteractionResultHolder.success(stack);
    }

    private static InteractionResultHolder<ItemStack> useKey(ServerPlayer player, ItemStack stack, Item item) {
        boolean cursed = player.getRandom().nextFloat() < 0.28f;
        if (cursed && player.getRandom().nextFloat() < 0.35f) {
            GameUtils.teleportToRandomRoom(player);
            player.displayClientMessage(Component.translatable("message.noellesroles.sealed.key_danger")
                    .withStyle(ChatFormatting.DARK_RED), true);
        } else {
            Vec3 look = player.getLookAngle();
            Vec3 dest = player.position().add(look.scale(10));
            if (cursed) {
                dest = dest.add((player.getRandom().nextDouble() - 0.5) * 10,
                        (player.getRandom().nextDouble() - 0.3) * 3,
                        (player.getRandom().nextDouble() - 0.5) * 10);
            }
            BlockPos stand = findStand(player.serverLevel(), BlockPos.containing(dest));
            if (stand == null) {
                GameUtils.teleportToRandomRoom(player);
                player.displayClientMessage(Component.translatable("message.noellesroles.sealed.key_danger")
                        .withStyle(ChatFormatting.DARK_RED), true);
            } else {
                player.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
                player.displayClientMessage(Component.translatable(cursed
                                ? "message.noellesroles.sealed.key_offset"
                                : "message.noellesroles.sealed.key_use")
                        .withStyle(cursed ? ChatFormatting.RED : ChatFormatting.AQUA), true);
            }
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.8f, 1.1f);
        player.getCooldowns().addCooldown(item, 35 * 20);
        return InteractionResultHolder.success(stack);
    }

    private static InteractionResultHolder<ItemStack> useMatch(ServerPlayer player, ItemStack stack, Item item) {
        AABB box = player.getBoundingBox().inflate(4);
        for (LivingEntity living : player.serverLevel().getEntitiesOfClass(LivingEntity.class, box)) {
            if (living != player) {
                living.setRemainingFireTicks(100);
            }
        }
        if (player.getRandom().nextFloat() < 0.18f) {
            player.setRemainingFireTicks(80);
            player.displayClientMessage(Component.translatable("message.noellesroles.sealed.match_backfire")
                    .withStyle(ChatFormatting.RED), true);
        } else {
            player.displayClientMessage(Component.translatable("message.noellesroles.sealed.match_use")
                    .withStyle(ChatFormatting.GOLD), true);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.FLINTANDSTEEL_USE,
                SoundSource.PLAYERS, 1.0f, 0.8f);
        player.getCooldowns().addCooldown(item, 50 * 20);
        return InteractionResultHolder.success(stack);
    }

    private static void spawnLightning(ServerLevel level, Vec3 pos) {
        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
        if (lightning == null) {
            return;
        }
        lightning.moveTo(pos.x, pos.y, pos.z);
        lightning.setVisualOnly(true);
        level.addFreshEntity(lightning);
    }

    private static BlockPos findStand(ServerLevel level, BlockPos origin) {
        for (int dy = 3; dy >= -4; dy--) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (canStand(level, pos)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private static boolean canStand(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
    }

    private static ServerPlayer nearestAlive(ServerPlayer player) {
        ServerPlayer best = null;
        double bestDist = Double.MAX_VALUE;
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other == player || !GameUtils.isPlayerAliveAndSurvival(other)) {
                continue;
            }
            double dist = other.distanceToSqr(player);
            if (dist < bestDist) {
                bestDist = dist;
                best = other;
            }
        }
        return best;
    }

    private static ServerPlayer nearestKiller(ServerPlayer player) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        ServerPlayer best = null;
        double bestDist = Double.MAX_VALUE;
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other == player || !GameUtils.isPlayerAliveAndSurvival(other)) {
                continue;
            }
            SRERole role = game.getRole(other);
            if (role == null || !role.canUseKiller() || role.isInnocent()) {
                continue;
            }
            double dist = other.distanceToSqr(player);
            if (dist < bestDist) {
                bestDist = dist;
                best = other;
            }
        }
        return best;
    }

    private static Component worldDir(Player from, Player to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double angle = Mth.wrapDegrees(Math.toDegrees(Math.atan2(-dx, dz)));
        String key;
        if (angle >= -22.5 && angle < 22.5) {
            key = "south";
        } else if (angle >= 22.5 && angle < 67.5) {
            key = "southwest";
        } else if (angle >= 67.5 && angle < 112.5) {
            key = "west";
        } else if (angle >= 112.5 && angle < 157.5) {
            key = "northwest";
        } else if (angle >= -67.5 && angle < -22.5) {
            key = "southeast";
        } else if (angle >= -112.5 && angle < -67.5) {
            key = "east";
        } else if (angle >= -157.5 && angle < -112.5) {
            key = "northeast";
        } else {
            key = "north";
        }
        return Component.translatable("message.noellesroles.sealed.dir." + key);
    }

    private static ItemStack find(Player player, Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                return stack;
            }
        }
        return null;
    }

    private static void addCoins(Player player, int amount) {
        SREPlayerShopComponent.KEY.get(player).addToBalance(amount);
    }

    private static void loseCoins(Player player, int amount) {
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
        shop.setBalance(Math.max(0, shop.balance - amount));
        player.displayClientMessage(Component.translatable("message.noellesroles.sealed.purse_loss")
                .withStyle(ChatFormatting.RED), true);
    }

    private static long getLong(ItemStack stack, String key) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getLong(key);
    }

    private static void setLong(ItemStack stack, String key, long value) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putLong(key, value);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
