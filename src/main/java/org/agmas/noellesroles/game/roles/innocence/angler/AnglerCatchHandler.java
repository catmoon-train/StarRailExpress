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

package org.agmas.noellesroles.game.roles.innocence.angler;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.PlayerBodyEntityComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.content.item.component.SREWrittenBookContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.phys.Vec3;
import io.wifi.starrailexpress.content.entity.GrenadeEntity;
import io.wifi.starrailexpress.content.entity.StickyGrenadeEntity;
import io.wifi.starrailexpress.content.entity.TimedGrenadeEntity;
import io.wifi.starrailexpress.content.entity.no_water_influenced.NoHeavyWaterInfluencedThrowableItemProjectile;
import org.agmas.noellesroles.content.item.angler.ErrorAnglerRodItem;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.touhou.THRedHouseRoles;
import org.agmas.noellesroles.role_data.innocence.AnglerRoleData;
import org.agmas.noellesroles.utils.MoneyUtils;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AnglerCatchHandler {
    private AnglerCatchHandler() {
    }

    public static void giveStartingRod(ServerPlayer player) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        int count = game.getStartingPlayerCount();
        if (count <= 0) {
            count = game.getPlayerCount();
        }
        ItemStack rod = new ItemStack(ModItems.ANGLER_ROD);
        int uses = AnglerRules.rodDurability(count);
        rod.set(DataComponents.MAX_DAMAGE, uses);
        rod.set(DataComponents.DAMAGE, 0);
        RoleUtils.insertStackInFreeSlot(player, rod);
        player.displayClientMessage(Component.translatable("message.noellesroles.angler.got_rod", uses)
                .withStyle(ChatFormatting.AQUA), true);
    }

    public static int remainingUses(ItemStack stack) {
        if (!stack.has(DataComponents.MAX_DAMAGE)) {
            return 0;
        }
        return Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
    }

    public static boolean handleRetrieve(ServerPlayer player, ItemStack rod, FishingHook hook) {
        if (!AnglerRodCanFish(player, rod)) {
            return true;
        }
        ServerLevel level = player.serverLevel();
        AnglerWorldMemory.recordCatchSpot(level, hook.blockPosition());
        boolean errorRod = rod.getItem() instanceof ErrorAnglerRodItem;
        if (errorRod) {
            giveErrorCatch(player, hook);
            AnglerItemTags.setErrorUses(rod, AnglerItemTags.errorUses(rod) - 1);
        } else {
            giveGoodCatch(player, hook);
            if (consumeRod(player, rod)) {
                GameUtils.killPlayer(player, true, null, AnglerRules.DEATH_EXHAUSTED);
            }
        }
        playCatchEffects(level, player, hook, errorRod);
        return true;
    }

    private static void playCatchEffects(ServerLevel level, ServerPlayer player, FishingHook hook, boolean errorRod) {
        double x = hook.getX();
        double y = hook.getY();
        double z = hook.getZ();
        level.sendParticles(ParticleTypes.FISHING, x, y, z, 20, 0.28, 0.18, 0.28, 0.09);
        level.sendParticles(ParticleTypes.SPLASH, x, y, z, 18, 0.32, 0.22, 0.32, 0.14);
        level.sendParticles(ParticleTypes.BUBBLE_POP, x, y + 0.12, z, 12, 0.22, 0.16, 0.22, 0.03);
        if (errorRod) {
            level.sendParticles(ParticleTypes.SQUID_INK, x, y + 0.18, z, 14, 0.22, 0.28, 0.22, 0.05);
            level.sendParticles(ParticleTypes.SMOKE, x, y + 0.22, z, 8, 0.16, 0.22, 0.16, 0.012);
        } else {
            level.sendParticles(ParticleTypes.GLOW, x, y + 0.22, z, 10, 0.22, 0.22, 0.22, 0.025);
            level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.05, player.getZ(),
                    14, 0.35, 0.45, 0.35, 0.45);
        }
        level.playSound(null, x, y, z, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS,
                1.05f, 0.85f + level.random.nextFloat() * 0.25f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE,
                SoundSource.PLAYERS, 1.0f, 1.05f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP,
                SoundSource.PLAYERS, 0.75f, 1.15f);
        if (errorRod) {
            level.playSound(null, x, y, z, SoundEvents.SQUID_SQUIRT, SoundSource.PLAYERS, 0.55f, 0.55f);
        }
    }

    private static boolean AnglerRodCanFish(ServerPlayer player, ItemStack rod) {
        return org.agmas.noellesroles.content.item.angler.AnglerRodItem.canPlayerCast(player, rod);
    }

    /** @return true 表示耐久已耗尽 */
    public static boolean consumeRod(ServerPlayer player, ItemStack rod) {
        int next = Math.min(rod.getMaxDamage(), rod.getDamageValue() + 1);
        rod.setDamageValue(next);
        return next >= rod.getMaxDamage();
    }

    public static ItemStack createErrorRod() {
        ItemStack rod = new ItemStack(ModItems.ERROR_ANGLER_ROD);
        rod.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
        AnglerItemTags.setErrorUses(rod, -1);
        return rod;
    }

    private static void giveGoodCatch(ServerPlayer player, FishingHook hook) {
        RandomSource random = player.getRandom();
        if (random.nextInt(AnglerRules.GRENADE_ODDS) == 0) {
            give(player, hook, new ItemStack(TMMItems.GRENADE));
            player.displayClientMessage(Component.translatable("message.noellesroles.angler.catch_grenade")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        int roll = random.nextInt(100);
        Rarity rarity = roll < 55 ? Rarity.COMMON : roll < 83 ? Rarity.UNCOMMON : roll < 97 ? Rarity.RARE : Rarity.VERY_RARE;
        giveRarity(player, hook, rarity, random);
        AnglerRoleData data = RoleData.getNullable(AnglerRoleData.class, player);
        if (data != null && data.bonusRareNext) {
            data.bonusRareNext = false;
            data.sync();
            giveRarity(player, hook, Rarity.RARE, random);
        }
    }

    private enum Rarity {
        COMMON, UNCOMMON, RARE, VERY_RARE
    }

    private static void giveRarity(ServerPlayer player, FishingHook hook, Rarity rarity, RandomSource random) {
        switch (rarity) {
            case COMMON -> giveCommon(player, hook, random);
            case UNCOMMON -> giveUncommon(player, hook, random);
            case RARE -> giveRare(player, hook, random);
            case VERY_RARE -> {
                give(player, hook, oddity(ModItems.ANGLER_ABYSS_BAIT));
                player.displayClientMessage(Component.translatable("message.noellesroles.angler.catch_bait")
                        .withStyle(ChatFormatting.DARK_AQUA), true);
            }
        }
    }

    private static void giveCommon(ServerPlayer player, FishingHook hook, RandomSource random) {
        int pick = random.nextInt(12);
        switch (pick) {
            case 0 -> giveLivingCarp(player, hook);
            case 1 -> give(player, hook, new ItemStack(ModItems.ANGLER_RAGGED_BOOTS));
            case 2 -> give(player, hook, new ItemStack(ModItems.ANGLER_VANILLA_MILK));
            case 3 -> give(player, hook, new ItemStack(ModItems.ANGLER_FLOUNDER));
            case 4 -> give(player, hook, oddity(ModItems.ANGLER_BLINKING_KELP));
            case 5 -> give(player, hook, oddity(ModItems.ANGLER_WET_TICKET));
            case 6 -> give(player, hook, oddity(ModItems.ANGLER_GLOVES));
            case 7 -> give(player, hook, oddity(ModItems.ANGLER_HAIR_REEL));
            case 8 -> give(player, hook, oddity(ModItems.ANGLER_INK));
            case 9 -> give(player, hook, oddity(ModItems.ANGLER_FALSE_TOOTH));
            case 10 -> give(player, hook, oddity(ModItems.ANGLER_TASK_LIST));
            default -> give(player, hook, oddity(ModItems.ANGLER_DRIPPING_WATCH));
        }
    }

    private static void giveUncommon(ServerPlayer player, FishingHook hook, RandomSource random) {
        int pick = random.nextInt(6);
        switch (pick) {
            case 0 -> catchBody(player, hook, false);
            case 1 -> stealCoins(player, hook);
            case 2 -> give(player, hook, patchouliBook(player));
            case 3 -> give(player, hook, new ItemStack(ModItems.ANGLER_ABYSS_SHIELD));
            case 4 -> give(player, hook, new ItemStack(ModItems.ANGLER_SOMEONE_KEY));
            default -> give(player, hook, new ItemStack(ModItems.ANGLER_INVERTED_FISH));
        }
    }

    private static void giveRare(ServerPlayer player, FishingHook hook, RandomSource random) {
        int pick = random.nextInt(3);
        switch (pick) {
            case 0 -> giveHistoricGun(player, hook);
            case 1 -> give(player, hook, oddity(ModItems.ANGLER_JUMPING_HEART));
            default -> give(player, hook, oddity(ModItems.ANGLER_UNADDRESSED_LETTER));
        }
    }

    private static void giveLivingCarp(ServerPlayer player, FishingHook hook) {
        ItemStack carp = new ItemStack(ModItems.ANGLER_LIVING_CARP);
        AnglerItemTags.stampCarp(carp, GameUtils.getTicksFromGameStart(player.level()));
        give(player, hook, carp);
    }

    private static void catchBody(ServerPlayer player, FishingHook hook, boolean fakeLiving) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        List<ServerPlayer> pool = new ArrayList<>();
        for (ServerPlayer other : player.serverLevel().players()) {
            if (game.getRole(other) == null) {
                continue;
            }
            boolean alive = GameUtils.isPlayerAliveAndSurvival(other);
            if (fakeLiving ? alive && other != player : !alive) {
                pool.add(other);
            }
        }
        if (pool.isEmpty()) {
            give(player, hook, oddity(ModItems.ANGLER_EMPTY_COFFIN));
            player.addEffect(ModEffects.of(MobEffects.DARKNESS, AnglerRules.COFFIN_DARK_TICKS, 0, false, true, true));
            player.displayClientMessage(Component.translatable("message.noellesroles.angler.empty_coffin")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
            return;
        }
        ServerPlayer target = pool.get(player.getRandom().nextInt(pool.size()));
        spawnCaughtBody(player, hook, target, fakeLiving);
        player.displayClientMessage(Component.translatable("message.noellesroles.angler.catch_body",
                target.getScoreboardName()).withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    private static void spawnCaughtBody(ServerPlayer angler, FishingHook hook, ServerPlayer target, boolean fake) {
        Vec3 spawnPos = catchOrigin(hook);
        PlayerBodyEntity body = TMMEntities.PLAYER_BODY.create(angler.level());
        if (body == null) {
            return;
        }
        body.setPlayerUuid(target.getUUID());
        body.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, angler.getYRot(), 0f);
        PlayerBodyEntityComponent cca = PlayerBodyEntityComponent.KEY.get(body);
        cca.setOwnerName(target.getScoreboardName(), false);
        cca.setDeathReason(AnglerRules.DEATH_CATCH.toString(), false);
        cca.isFakeBody = fake;
        SRERole role = SREGameWorldComponent.KEY.get(angler.level()).getRole(target);
        if (role != null) {
            cca.playerRole = role.identifier();
        }
        cca.sync();
        angler.level().addFreshEntity(body);
    }

    private static void stealCoins(ServerPlayer player, FishingHook hook) {
        List<ServerPlayer> others = new ArrayList<>();
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other != player && GameUtils.isPlayerAliveAndSurvival(other) && MoneyUtils.getBalance(other) > 0) {
                others.add(other);
            }
        }
        if (others.isEmpty()) {
            give(player, hook, oddity(ModItems.ANGLER_EMPTY_WALLET));
            player.displayClientMessage(Component.translatable("message.noellesroles.angler.empty_wallet")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
            return;
        }
        ServerPlayer target = others.get(player.getRandom().nextInt(others.size()));
        int amount = Math.min(AnglerRules.COIN_STEAL_MAX, MoneyUtils.getBalance(target));
        MoneyUtils.addToBalance(target, -amount);
        MoneyUtils.addToBalance(player, amount);
        player.displayClientMessage(Component.translatable("message.noellesroles.angler.steal_coins", amount)
                .withStyle(ChatFormatting.GOLD), true);
    }

    private static ItemStack patchouliBook(ServerPlayer player) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        boolean pachuriPresent = false;
        List<ServerPlayer> alive = new ArrayList<>();
        for (ServerPlayer other : player.serverLevel().players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(other) || game.getRole(other) == null) {
                continue;
            }
            alive.add(other);
            if (game.isRole(other, THRedHouseRoles.PACHURI)) {
                pachuriPresent = true;
            }
        }
        ItemStack book = ModItems.NEWSPAPER.getDefaultInstance();
        String title = pachuriPresent ? "来自帕秋莉" : "为什么会有这个？";
        Component page;
        if (alive.isEmpty()) {
            page = Component.translatable("item.noellesroles.angler_book.empty");
        } else {
            ServerPlayer target = alive.get(player.getRandom().nextInt(alive.size()));
            SRERole actual = game.getRole(target);
            SRERole fake = randomWrongRole(actual, player.getRandom());
            page = Component.translatable("item.noellesroles.angler_book.page", target.getName(),
                    fake != null ? fake.getName() : Component.literal("????"));
        }
        book.set(SREDataComponentTypes.WRITTEN_BOOK_CONTENT, new SREWrittenBookContent(
                new Filterable<>(title, Optional.of(title)),
                "帕秋莉",
                List.of(new Filterable<>(page, Optional.of(page))),
                true));
        return book;
    }

    private static SRERole randomWrongRole(SRERole actual, RandomSource random) {
        List<SRERole> roles = new ArrayList<>(TMMRoles.ROLES.values());
        roles.removeIf(role -> role == actual);
        if (roles.isEmpty()) {
            return actual;
        }
        return roles.get(random.nextInt(roles.size()));
    }

    private static void giveHistoricGun(ServerPlayer player, FishingHook hook) {
        List<ItemStack> guns = new ArrayList<>();
        for (ItemStack stack : AnglerWorldMemory.itemHistory()) {
            if (stack.is(TMMItemTags.GUNS)) {
                guns.add(stack.copy());
            }
        }
        if (guns.isEmpty()) {
            give(player, hook, oddity(ModItems.ANGLER_EMPTY_HOLSTER));
            player.displayClientMessage(Component.translatable("message.noellesroles.angler.empty_holster")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        give(player, hook, guns.get(player.getRandom().nextInt(guns.size())));
    }

    private static void giveErrorCatch(ServerPlayer player, FishingHook hook) {
        RandomSource random = player.getRandom();
        if (!AnglerWorldMemory.hasHistory()) {
            if (random.nextBoolean()) {
                give(player, hook, oddity(ModItems.ANGLER_EMPTY_HOOK));
                player.displayClientMessage(Component.translatable("message.noellesroles.angler.unwritten")
                        .withStyle(ChatFormatting.DARK_PURPLE), true);
            } else {
                vanishAir(player);
            }
            return;
        }
        if (random.nextInt(AnglerRules.GRENADE_ODDS) == 0) {
            ItemStack grenade = new ItemStack(TMMItems.GRENADE);
            grenade.set(DataComponents.CUSTOM_NAME, Component.translatable("item.noellesroles.angler_wrong_grenade")
                    .withStyle(ChatFormatting.DARK_RED));
            give(player, hook, grenade);
            return;
        }
        List<ItemStack> history = AnglerWorldMemory.itemHistory();
        ItemStack picked = history.get(random.nextInt(history.size())).copy();
        int mutations = 1 + random.nextInt(2);
        for (int i = 0; i < mutations; i++) {
            picked = mutate(player, hook, picked, random);
            if (picked.isEmpty()) {
                return;
            }
        }
        give(player, hook, picked);
    }

    private static ItemStack mutate(ServerPlayer player, FishingHook hook, ItemStack stack, RandomSource random) {
        int type = random.nextInt(8);
        return switch (type) {
            case 0 -> {
                AnglerItemTags.markInverted(stack);
                yield stack;
            }
            case 1 -> {
                stack.set(DataComponents.CUSTOM_NAME, Component.literal(AnglerWorldMemory.corruptName(
                        stack.getHoverName().getString())).withStyle(ChatFormatting.OBFUSCATED, ChatFormatting.DARK_PURPLE));
                yield stack;
            }
            case 2 -> {
                ServerPlayer other = AnglerWorldMemory.nearestOther(player);
                if (other != null) {
                    stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.noellesroles.angler_owned",
                            other.getScoreboardName(), stack.getHoverName()).withStyle(ChatFormatting.GRAY));
                }
                if (stack.has(DataComponents.MAX_DAMAGE)) {
                    stack.set(DataComponents.DAMAGE, stack.getMaxDamage());
                }
                yield stack;
            }
            case 3 -> {
                vanishAir(player);
                yield ItemStack.EMPTY;
            }
            case 4 -> {
                catchBody(player, hook, true);
                yield ItemStack.EMPTY;
            }
            case 5 -> {
                maybeDeleteOriginal(player, stack, random);
                yield stack;
            }
            case 6 -> {
                if (isCaughtGrenade(stack)) {
                    player.displayClientMessage(Component.translatable("message.noellesroles.angler.anti_grenade")
                            .withStyle(ChatFormatting.RED), true);
                    spawnCaughtGrenade(player, hook, new ItemStack(TMMItems.GRENADE));
                    yield ItemStack.EMPTY;
                }
                AnglerItemTags.markInverted(stack);
                yield stack;
            }
            default -> {
                if (stack.getMaxStackSize() > 1) {
                    stack.setCount(1);
                    stack.set(DataComponents.CUSTOM_NAME, Component.literal(stack.getHoverName().getString() + " x99")
                            .withStyle(ChatFormatting.RED));
                }
                yield stack;
            }
        };
    }

    private static void maybeDeleteOriginal(ServerPlayer player, ItemStack copy, RandomSource random) {
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other == player || !GameUtils.isPlayerAliveAndSurvival(other)) {
                continue;
            }
            for (int i = 0; i < other.getInventory().getContainerSize(); i++) {
                ItemStack held = other.getInventory().getItem(i);
                if (!held.isEmpty() && held.is(copy.getItem())) {
                    copy.set(DataComponents.CUSTOM_NAME, Component.translatable("item.noellesroles.angler_copied",
                            other.getScoreboardName()).withStyle(ChatFormatting.DARK_RED));
                    if (random.nextInt(100) < AnglerRules.DUPLICATE_DELETE_CHANCE) {
                        other.getInventory().setItem(i, ItemStack.EMPTY);
                        other.displayClientMessage(Component.translatable("message.noellesroles.angler.item_reeled")
                                .withStyle(ChatFormatting.DARK_PURPLE), true);
                    }
                    return;
                }
            }
        }
    }

    private static void vanishAir(ServerPlayer player) {
        player.playNotifySound(SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.7f, 0.3f);
        player.displayClientMessage(Component.translatable("message.noellesroles.angler.error_air")
                .withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    private static ItemStack oddity(net.minecraft.world.item.Item item) {
        return new ItemStack(item);
    }

    private static void give(ServerPlayer player, FishingHook hook, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        AnglerWorldMemory.recordItem(stack);
        if (isCaughtGrenade(stack)) {
            spawnCaughtGrenade(player, hook, stack);
        } else {
            spawnCaughtDrop(player, hook, stack);
        }
        player.displayClientMessage(Component.translatable("message.noellesroles.angler.catch", stack.getHoverName())
                .withStyle(ChatFormatting.AQUA), true);
    }

    private static boolean isCaughtGrenade(ItemStack stack) {
        return stack.is(TMMItems.GRENADE)
                || stack.is(TMMItems.STICKY_GRENADE)
                || stack.is(TMMItems.TIMED_GRENADE);
    }

    private static Vec3 catchOrigin(FishingHook hook) {
        return new Vec3(hook.getX(), hook.getY() + 0.25, hook.getZ());
    }

    private static Vec3 fishingPull(ServerPlayer player, Vec3 from) {
        double dx = player.getX() - from.x;
        double dy = player.getY() + 0.35 - from.y;
        double dz = player.getZ() - from.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return new Vec3(dx * 0.1, dy * 0.1 + Math.sqrt(Math.max(dist, 0.01)) * 0.08, dz * 0.1);
    }

    private static void spawnCaughtDrop(ServerPlayer player, FishingHook hook, ItemStack stack) {
        Vec3 origin = catchOrigin(hook);
        ItemEntity drop = new ItemEntity(player.serverLevel(), origin.x, origin.y, origin.z, stack.copy());
        drop.setDeltaMovement(fishingPull(player, origin));
        drop.setPickUpDelay(10);
        player.serverLevel().addFreshEntity(drop);
    }

    private static void spawnCaughtGrenade(ServerPlayer player, FishingHook hook, ItemStack stack) {
        ServerLevel level = player.serverLevel();
        NoHeavyWaterInfluencedThrowableItemProjectile grenade;
        if (stack.is(TMMItems.STICKY_GRENADE)) {
            grenade = new StickyGrenadeEntity(TMMEntities.STICKY_GRENADE, level);
        } else if (stack.is(TMMItems.TIMED_GRENADE)) {
            grenade = new TimedGrenadeEntity(TMMEntities.TIMED_GRENADE, level);
        } else {
            grenade = new GrenadeEntity(TMMEntities.GRENADE, level);
        }
        Vec3 origin = catchOrigin(hook);
        grenade.setOwner(player);
        grenade.setPos(origin.x, origin.y, origin.z);
        grenade.setDeltaMovement(fishingPull(player, origin).scale(1.35));
        grenade.hasImpulse = true;
        level.addFreshEntity(grenade);
    }
}
