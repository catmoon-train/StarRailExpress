/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.agmas.noellesroles.role_data.killer;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.event.OnPlayerDeathWithBody;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.game.roles.innocence.builder.BuilderWallPositions;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.BuilderRemoveWallS2CPacket;
import org.agmas.noellesroles.packet.BuilderWallS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 鸟兽兽的服务端状态：耐力、尸体火焰、全家福、掩体充能和复活。 */
public class NiaoshoushouRoleData extends SimpleRoleData {

    public static final int COVER_COOLDOWN_TICKS = 120 * 20;
    public static final int COVER_DURATION_TICKS = 20 * 20;
    public static final int COVER_WIDTH = 3;
    public static final int COVER_HEIGHT = 2;
    public static final int SPRINT_THRESHOLD_TICKS = 5 * 20;
    public static final int BODY_BURN_TICKS = 5 * 20;
    public static final int FAMILY_SHARE_PERCENT = 30;
    public static final net.minecraft.resources.ResourceLocation COVER_SKILL_ID =
            org.agmas.noellesroles.Noellesroles.id("niaoshoushou_cover");

    private int sprintTicks;
    private int lastCoverCooldown;
    private boolean familyPhotoPurchased;
    private boolean familyFormed;
    private boolean familyRevived;
    private final List<UUID> familyMembers = new ArrayList<>();
    private final Map<UUID, Integer> burningBodies = new HashMap<>();
    private final Map<UUID, WallState> walls = new LinkedHashMap<>();

    static {
        OnPlayerDeathWithBody.EVENT.register(NiaoshoushouRoleData::onPlayerDeathWithBody);
        OnPlayerDeath.EVENT.register(NiaoshoushouRoleData::onPlayerDeath);
    }

    public NiaoshoushouRoleData(RoleDataContext context) {
        super(context);
    }

    public static NiaoshoushouRoleData get(Player player) {
        return RoleData.getNullable(NiaoshoushouRoleData.class, player);
    }

    @Override
    public void init() {
        sprintTicks = 0;
        lastCoverCooldown = 0;
        familyPhotoPurchased = false;
        familyFormed = false;
        familyRevived = false;
        familyMembers.clear();
        burningBodies.clear();
        clearWalls();
        sync();
    }

    @Override
    public void clear() {
        clearWalls();
        burningBodies.clear();
        familyMembers.clear();
        sprintTicks = 0;
        lastCoverCooldown = 0;
        familyPhotoPurchased = false;
        familyFormed = false;
        familyRevived = false;
        sync();
    }

    public boolean isFamilyPhotoPurchased() {
        return familyPhotoPurchased;
    }

    public boolean isFamilyFormed() {
        return familyFormed;
    }

    public void markFamilyPhotoPurchased() {
        familyPhotoPurchased = true;
        sync();
    }

    public boolean useCoverAbility(ServerPlayer serverPlayer) {
        if (serverPlayer != player || !(player.level() instanceof ServerLevel serverLevel)
                || !SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.NIAOSHOU_SHOU)
                || !GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
            return false;
        }

        List<BlockPos> positions = calculateCoverPositions();
        if (positions.isEmpty()) {
            return false;
        }

        UUID wallId = UUID.randomUUID();
        Set<BlockPos> allPositions = new HashSet<>(positions);
        BuilderWallPositions.addWall(allPositions);
        walls.put(wallId, new WallState(allPositions, COVER_DURATION_TICKS));

        int minY = positions.stream().mapToInt(BlockPos::getY).min().orElse(serverPlayer.blockPosition().getY());
        List<BlockPos> bricks = new ArrayList<>();
        List<BlockPos> cobwebs = new ArrayList<>();
        for (BlockPos position : positions) {
            if (position.getY() == minY + COVER_HEIGHT - 1) {
                cobwebs.add(position);
            } else {
                bricks.add(position);
            }
        }

        BuilderWallS2CPacket packet = new BuilderWallS2CPacket(wallId, bricks, cobwebs, COVER_DURATION_TICKS);
        for (ServerPlayer target : serverLevel.players()) {
            ServerPlayNetworking.send(target, packet);
        }
        serverLevel.playSound(null, serverPlayer.blockPosition(), SoundEvents.STONE_PLACE,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.niaoshoushou.cover_created")
                .withStyle(ChatFormatting.GREEN), true);
        return true;
    }

    private List<BlockPos> calculateCoverPositions() {
        Direction facing = player.getDirection();
        Direction extend = facing.getCounterClockWise();
        BlockPos base = player.blockPosition().relative(facing);
        List<BlockPos> positions = new ArrayList<>(COVER_WIDTH * COVER_HEIGHT);
        for (int width = 0; width < COVER_WIDTH; width++) {
            for (int height = 0; height < COVER_HEIGHT; height++) {
                positions.add(base.relative(extend, width - 1).above(height));
            }
        }
        return positions;
    }

    @Override
    public void serverTick() {
        tickBurningBodies();
        tickWalls();
        tickCoverCharge();

        if (!GameUtils.isPlayerAliveAndSurvival(player) || !player.isSprinting()) {
            sprintTicks = 0;
            return;
        }

        sprintTicks = Math.min(SPRINT_THRESHOLD_TICKS, sprintTicks + 1);
        if (sprintTicks >= SPRINT_THRESHOLD_TICKS && sprintTicks % 10 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0,
                    false, false, true));
        }
    }

    private void tickCoverCharge() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(serverPlayer);
        SREAbilityPlayerComponent.SkillState state = ability.getSkillState(COVER_SKILL_ID);
        boolean changed = false;
        // 通用技能系统会在每次释放后设置冷却；只要还留有充能，就立即解锁第二次释放。
        if (state.maxCharges > 0 && state.charges > 0 && state.charges < state.maxCharges
                && state.cooldown > 0) {
            state.cooldown = 0;
            changed = true;
        } else if (state.maxCharges > 0 && lastCoverCooldown > 0 && state.cooldown == 0
                && state.charges < state.maxCharges) {
            state.charges++;
            changed = true;
        }
        if (changed) {
            ability.charges = state.charges;
            ability.maxCharges = state.maxCharges;
            ability.cooldown = state.cooldown;
            ability.sync();
        }
        lastCoverCooldown = state.cooldown;
    }

    private void tickBurningBodies() {
        if (!(player.level() instanceof ServerLevel serverLevel) || burningBodies.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Integer>> iterator = burningBodies.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            Entity entity = serverLevel.getEntity(entry.getKey());
            int remaining = entry.getValue() - 1;
            if (entity == null || remaining <= 0) {
                if (entity != null) {
                    entity.discard();
                }
                iterator.remove();
            } else {
                entry.setValue(remaining);
            }
        }
    }

    private void tickWalls() {
        if (walls.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, WallState>> iterator = walls.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, WallState> entry = iterator.next();
            WallState wall = entry.getValue();
            if (--wall.remainingTicks <= 0) {
                BuilderWallPositions.removeWall(wall.positions);
                sendRemoveWall(entry.getKey());
                iterator.remove();
            }
        }
    }

    private void sendRemoveWall(UUID wallId) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        BuilderRemoveWallS2CPacket packet = new BuilderRemoveWallS2CPacket(wallId);
        for (ServerPlayer target : serverLevel.players()) {
            ServerPlayNetworking.send(target, packet);
        }
    }

    private void clearWalls() {
        if (walls.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, WallState> entry : walls.entrySet()) {
            BuilderWallPositions.removeWall(entry.getValue().positions);
            sendRemoveWall(entry.getKey());
        }
        walls.clear();
    }

    private void burnBody(PlayerBodyEntity body) {
        if (body == null) {
            return;
        }
        body.igniteForSeconds(5);
        burningBodies.put(body.getUUID(), BODY_BURN_TICKS);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.niaoshoushou.body_burning")
                    .withStyle(ChatFormatting.GOLD), true);
        }
    }

    public boolean formFamily(ServerPlayer owner, ServerPlayer first, ServerPlayer second) {
        if (owner != player || familyFormed || first == null || second == null
                || owner == first || owner == second || first == second) {
            return false;
        }
        familyMembers.clear();
        familyMembers.add(owner.getUUID());
        familyMembers.add(first.getUUID());
        familyMembers.add(second.getUUID());
        familyFormed = true;

        ServerPlayer[] members = {owner, first, second};
        int[] balances = new int[members.length];
        int total = 0;
        for (int i = 0; i < members.length; i++) {
            balances[i] = SREPlayerShopComponent.KEY.get(members[i]).balance;
            total += balances[i];
        }
        for (int i = 0; i < members.length; i++) {
            int others = total - balances[i];
            SREPlayerShopComponent.KEY.get(members[i]).addToBalance(others * FAMILY_SHARE_PERCENT / 100);
            members[i].displayClientMessage(Component.translatable("message.noellesroles.niaoshoushou.family_formed")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
        sync();
        return true;
    }

    private static void onPlayerDeathWithBody(Player victim, Player killer,
            net.minecraft.resources.ResourceLocation deathReason, PlayerBodyEntity body) {
        if (!(killer instanceof ServerPlayer serverKiller)
                || !SREGameWorldComponent.KEY.get(killer.level()).isRole(killer, ModRoles.NIAOSHOU_SHOU)) {
            return;
        }
        NiaoshoushouRoleData data = get(serverKiller);
        if (data != null) {
            data.burnBody(body);
        }
    }

    private static void onPlayerDeath(Player deadPlayer, net.minecraft.resources.ResourceLocation deathReason) {
        if (!(deadPlayer instanceof ServerPlayer dead) || !(dead.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        var gamecca = SREGameWorldComponent.KEY.get(serverLevel);
        for (ServerPlayer candidate : serverLevel.players()) {
            if (!gamecca.isRole(candidate, ModRoles.NIAOSHOU_SHOU)) {
                continue;
            }
            NiaoshoushouRoleData data = get(candidate);
            if (data != null) {
                data.tryReviveFamily(candidate);
            }
        }
    }

    private void tryReviveFamily(ServerPlayer trigger) {
        if (!familyFormed || familyRevived || familyMembers.size() != 3) {
            return;
        }
        List<ServerPlayer> members = new ArrayList<>();
        for (UUID uuid : familyMembers) {
            ServerPlayer member = trigger.getServer() == null
                    ? null
                    : trigger.getServer().getPlayerList().getPlayer(uuid);
            if (member == null || GameUtils.isPlayerAliveAndSurvival(member)) {
                return;
            }
            members.add(member);
        }

        ServerPlayer bird = members.stream()
                .filter(member -> SREGameWorldComponent.KEY.get(member.level()).isRole(member, ModRoles.NIAOSHOU_SHOU))
                .findFirst().orElse(null);
        if (bird == null) {
            return;
        }

        familyRevived = true;
        GameUtils.revivePlayerToItsRoom(bird);
        GameUtils.teleportToRandomRoom(bird);
        bird.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Integer.MAX_VALUE, 1,
                false, false, true));
        ItemStack grenade = ModItems.NIAOSHOU_SHOU_INCENDIARY_GRENADE.getDefaultInstance();
        MCItemsUtils.insertOrDropItem(bird, grenade);
        bird.displayClientMessage(Component.translatable("message.noellesroles.niaoshoushou.family_revived")
                .withStyle(ChatFormatting.GREEN).append(Component.translatable("message.noellesroles.niaoshoushou.revival_reward")
                .withStyle(ChatFormatting.GOLD)), true);
        sync();
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("sprintTicks", sprintTicks);
        tag.putBoolean("familyPhotoPurchased", familyPhotoPurchased);
        tag.putBoolean("familyFormed", familyFormed);
        ListTag members = new ListTag();
        for (UUID member : familyMembers) {
            members.add(StringTag.valueOf(member.toString()));
        }
        tag.put("familyMembers", members);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        sprintTicks = getIntTag(tag, "sprintTicks", 0);
        familyPhotoPurchased = tag.getBoolean("familyPhotoPurchased");
        familyFormed = tag.getBoolean("familyFormed");
        familyMembers.clear();
        if (tag.contains("familyMembers", Tag.TAG_LIST)) {
            ListTag members = tag.getList("familyMembers", Tag.TAG_STRING);
            for (int i = 0; i < members.size(); i++) {
                try {
                    familyMembers.add(UUID.fromString(members.getString(i)));
                } catch (IllegalArgumentException ignored) {
                    // 忽略损坏的同步数据，避免整个职业数据失效。
                }
            }
        }
    }

    private static final class WallState {
        private final Set<BlockPos> positions;
        private int remainingTicks;

        private WallState(Set<BlockPos> positions, int remainingTicks) {
            this.positions = positions;
            this.remainingTicks = remainingTicks;
        }
    }
}
