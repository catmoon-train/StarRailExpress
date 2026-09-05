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

package org.agmas.noellesroles.role_data.innocence;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.SRENetworkMessageUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.content.entity.DoomedSinnerBodyEntity;
import org.agmas.noellesroles.game.roles.innocence.great_detective.DetectiveClue;
import org.agmas.noellesroles.game.roles.innocence.great_detective.GreatDetectiveRole;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.InsaneKillerRoleData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class GreatDetectiveRoleData extends SimpleRoleData {

    private static final double MOVE_THRESHOLD = 0.15;

    /** 已勘察过的尸体（实体 UUID）。 */
    public final Set<UUID> usedCorpses = new HashSet<>();
    /** 凶手 UUID -> 已掌握碎片线索（LinkedHashMap 保持页顺序）。 */
    public final LinkedHashMap<UUID, List<DetectiveClue>> clues = new LinkedHashMap<>();
    /** 凶手 UUID -> 各具尸体记下的死亡时间（不计入 3 条线索阈值）。 */
    public final LinkedHashMap<UUID, List<DeathNote>> deathNotes = new LinkedHashMap<>();
    /** 凶手 UUID -> 触发「方位」时的距离快照（格）。 */
    public final HashMap<UUID, Integer> revealedDistance = new HashMap<>();
    /** 凶手 UUID -> 触发「生死」时是否存活。 */
    public final HashMap<UUID, Boolean> revealedVital = new HashMap<>();
    public long cooldown = 0;

    private boolean gaveBook = false;
    private long channelEndTime = 0;
    private UUID channelBodyId;
    private Vec3 channelStartPos;

    public GreatDetectiveRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    public boolean isInCooldown() {
        return cooldown > 0 && cooldown > this.player.level().getGameTime();
    }

    public boolean isChanneling() {
        return channelEndTime > 0 && channelEndTime > this.player.level().getGameTime();
    }

    public long getChannelLeftTime() {
        if (!isChanneling()) {
            return 0;
        }
        return channelEndTime - this.player.level().getGameTime();
    }

    @Override
    public void init() {
        usedCorpses.clear();
        clues.clear();
        deathNotes.clear();
        revealedDistance.clear();
        revealedVital.clear();
        cooldown = 0;
        gaveBook = false;
        cancelChannel();
        sync();
    }

    @Override
    public void clear() {
        init();
        cooldown = 0;
    }

    public boolean hasUsedCorpse(UUID corpseUuid) {
        return usedCorpses.contains(corpseUuid);
    }

    public void markCorpseUsed(UUID corpseUuid) {
        usedCorpses.add(corpseUuid);
    }

    public List<DetectiveClue> getClues(UUID killer) {
        return clues.getOrDefault(killer, Collections.emptyList());
    }

    public List<DeathNote> getDeathNotes(UUID killer) {
        return deathNotes.getOrDefault(killer, Collections.emptyList());
    }

    public boolean hasClue(UUID killer, DetectiveClue clue) {
        for (DetectiveClue c : getClues(killer)) {
            if (c.sameAs(clue)) {
                return true;
            }
        }
        return false;
    }

    /** 添加线索；返回 false 表示重复未添加。 */
    public boolean addClue(UUID killer, DetectiveClue clue) {
        List<DetectiveClue> list = clues.computeIfAbsent(killer, k -> new ArrayList<>());
        for (DetectiveClue c : list) {
            if (c.sameAs(clue)) {
                return false;
            }
        }
        list.add(clue);
        return true;
    }

    public void addDeathNote(UUID killer, DeathNote note) {
        deathNotes.computeIfAbsent(killer, k -> new ArrayList<>()).add(note);
        clues.computeIfAbsent(killer, k -> new ArrayList<>());
    }

    public int clueCount(UUID killer) {
        return getClues(killer).size();
    }

    /** 按页顺序返回所有已记录的凶手 UUID。 */
    public List<UUID> getKillerOrder() {
        LinkedHashSet<UUID> order = new LinkedHashSet<>(clues.keySet());
        order.addAll(deathNotes.keySet());
        return new ArrayList<>(order);
    }

    public boolean hasTargetReveal(UUID killer) {
        return revealedDistance.containsKey(killer) || revealedVital.containsKey(killer);
    }

    public boolean hasRevealedDistance(UUID killer) {
        return revealedDistance.containsKey(killer);
    }

    public int getRevealedDistance(UUID killer) {
        return revealedDistance.getOrDefault(killer, -1);
    }

    public void setRevealedDistance(UUID killer, int distance) {
        revealedDistance.put(killer, distance);
        sync();
    }

    public boolean hasRevealedVital(UUID killer) {
        return revealedVital.containsKey(killer);
    }

    public boolean getRevealedVitalAlive(UUID killer) {
        return revealedVital.getOrDefault(killer, false);
    }

    public void setRevealedVital(UUID killer, boolean alive) {
        revealedVital.put(killer, alive);
        sync();
    }

    /**
     * 对着尸体或伪装活人尝试开始勘察。
     */
    public InteractionResult tryStartChannel(ServerPlayer sp, Entity target) {
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
        if (gw == null || !gw.isRunning() || !gw.isRole(sp, ModRoles.GREAT_DETECTIVE)) {
            return InteractionResult.PASS;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return InteractionResult.PASS;
        }
        if (isChanneling()) {
            return InteractionResult.SUCCESS;
        }
        if (isInCooldown()) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.great_detective.cooldown",
                            String.format("%.1f", getCooldownLeftTime() / 20f)).withStyle(ChatFormatting.RED),
                    true);
            return InteractionResult.FAIL;
        }

        if (target instanceof ServerPlayer tp) {
            if (gw.isRole(tp, ModRoles.INSANE_KILLER) && RoleData.test(InsaneKillerRoleData.class, tp, d -> d.isActive)) {
                GameUtils.killPlayer(tp, true, tp, GameConstants.DeathReasons.KNIFE, true);
                enterCooldown();
                sync();
                playCompleteFx(sp);
                SRENetworkMessageUtils.sendBroadcast(sp,
                        Component.translatable("message.noellesroles.great_detective.insane_killer")
                                .withStyle(ChatFormatting.DARK_RED));
                SRENetworkMessageUtils.sendTitleTime(sp, 8, 60, 20);
                SRENetworkMessageUtils.sendTitle(sp,
                        Component.translatable("message.noellesroles.great_detective.insane_killer.title")
                                .withStyle(ChatFormatting.DARK_RED));
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

        if (!(target instanceof PlayerBodyEntity body) || DoomedSinnerBodyEntity.isDoomedSinnerBody(target)) {
            return InteractionResult.PASS;
        }

        UUID corpseUuid = body.getUUID();
        if (hasUsedCorpse(corpseUuid)) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.great_detective.corpse_used")
                            .withStyle(ChatFormatting.GRAY),
                    true);
            return InteractionResult.SUCCESS;
        }

        NoellesRolesConfig cfg = NoellesRolesConfig.HANDLER.instance();
        this.channelBodyId = corpseUuid;
        this.channelStartPos = sp.position();
        this.channelEndTime = sp.level().getGameTime() + GameConstants.getInTicks(0, cfg.greatDetectiveChannelSeconds);
        playChannelStartFx(sp);
        sp.displayClientMessage(
                Component.translatable("message.noellesroles.great_detective.channel_start")
                        .withStyle(ChatFormatting.GOLD),
                true);
        sync();
        return InteractionResult.SUCCESS;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (cooldown > 0) {
            tag.putLong("cd", cooldown);
        }
        tag.putBoolean("gaveBook", this.gaveBook);
        if (isChanneling() && channelBodyId != null) {
            tag.putLong("chEnd", channelEndTime);
            tag.putUUID("chBody", channelBodyId);
        }
        ListTag used = new ListTag();
        for (UUID u : usedCorpses) {
            CompoundTag t = new CompoundTag();
            t.putUUID("u", u);
            used.add(t);
        }
        tag.put("used", used);

        ListTag killersTag = new ListTag();
        for (UUID killer : getKillerOrder()) {
            CompoundTag kt = new CompoundTag();
            kt.putUUID("killer", killer);
            ListTag cl = new ListTag();
            for (DetectiveClue c : getClues(killer)) {
                cl.add(c.toNbt());
            }
            kt.put("clues", cl);
            ListTag notes = new ListTag();
            for (DeathNote n : getDeathNotes(killer)) {
                notes.add(n.toNbt());
            }
            kt.put("notes", notes);
            if (revealedDistance.containsKey(killer)) {
                kt.putBoolean("hasDist", true);
                kt.putInt("dist", revealedDistance.get(killer));
            }
            if (revealedVital.containsKey(killer)) {
                kt.putBoolean("hasVital", true);
                kt.putBoolean("vital", revealedVital.get(killer));
            }
            killersTag.add(kt);
        }
        tag.put("killers", killersTag);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (tag.contains("cd")) {
            cooldown = tag.getLong("cd");
        } else {
            cooldown = 0;
        }
        gaveBook = tag.contains("gaveBook") && tag.getBoolean("gaveBook");
        if (tag.hasUUID("chBody") && tag.contains("chEnd")) {
            channelEndTime = tag.getLong("chEnd");
            channelBodyId = tag.getUUID("chBody");
        } else {
            channelEndTime = 0;
            channelBodyId = null;
        }
        usedCorpses.clear();
        clues.clear();
        deathNotes.clear();
        revealedDistance.clear();
        revealedVital.clear();

        if (tag.contains("used", Tag.TAG_LIST)) {
            ListTag used = tag.getList("used", Tag.TAG_COMPOUND);
            for (int i = 0; i < used.size(); i++) {
                usedCorpses.add(used.getCompound(i).getUUID("u"));
            }
        }

        if (tag.contains("killers", Tag.TAG_LIST)) {
            ListTag killersTag = tag.getList("killers", Tag.TAG_COMPOUND);
            for (int i = 0; i < killersTag.size(); i++) {
                CompoundTag kt = killersTag.getCompound(i);
                UUID killer = kt.getUUID("killer");
                List<DetectiveClue> list = new ArrayList<>();
                ListTag cl = kt.getList("clues", Tag.TAG_COMPOUND);
                for (int j = 0; j < cl.size(); j++) {
                    list.add(DetectiveClue.fromNbt(cl.getCompound(j)));
                }
                clues.put(killer, list);
                List<DeathNote> notes = new ArrayList<>();
                if (kt.contains("notes", Tag.TAG_LIST)) {
                    ListTag nl = kt.getList("notes", Tag.TAG_COMPOUND);
                    for (int j = 0; j < nl.size(); j++) {
                        notes.add(DeathNote.fromNbt(nl.getCompound(j)));
                    }
                }
                if (!notes.isEmpty()) {
                    deathNotes.put(killer, notes);
                }
                if (kt.getBoolean("hasDist")) {
                    revealedDistance.put(killer, kt.getInt("dist"));
                }
                if (kt.getBoolean("hasVital")) {
                    revealedVital.put(killer, kt.getBoolean("vital"));
                }
            }
        }
    }

    @Override
    public void clientTick() {
        if (this.cooldown > 0 && cooldown <= this.player.level().getGameTime()) {
            cooldown = 0;
        }
        if (channelEndTime > 0 && channelEndTime <= this.player.level().getGameTime()) {
            channelEndTime = 0;
        }
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        if (this.cooldown > 0 && cooldown <= this.player.level().getGameTime()) {
            cooldown = 0;
        }

        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
        if (gw == null || !gw.isRunning() || !gw.isRole(sp, ModRoles.GREAT_DETECTIVE)) {
            return;
        }

        if (!gaveBook && GameUtils.isPlayerAliveAndSurvival(sp)) {
            GreatDetectiveRole.ensureBook(sp);
            gaveBook = true;
            sync();
        }

        tickChannel(sp);
    }

    private void tickChannel(ServerPlayer sp) {
        if (!isChanneling()) {
            return;
        }
        if (channelStartPos == null) {
            channelStartPos = sp.position();
        }
        if (channelStartPos != null && sp.position().distanceTo(channelStartPos) > MOVE_THRESHOLD) {
            cancelChannel();
            sync();
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.great_detective.channel_moved")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }
        if (sp.level().getGameTime() >= channelEndTime) {
            UUID bodyId = channelBodyId;
            cancelChannel();
            Entity target = bodyId != null && sp.level() instanceof ServerLevel sl ? sl.getEntity(bodyId) : null;
            if (!(target instanceof PlayerBodyEntity body) || DoomedSinnerBodyEntity.isDoomedSinnerBody(body)) {
                sync();
                sp.displayClientMessage(
                        Component.translatable("message.noellesroles.great_detective.corpse_gone")
                                .withStyle(ChatFormatting.RED),
                        true);
                return;
            }
            GreatDetectiveRole.finishInvestigation(sp, this, body);
        }
    }

    private void cancelChannel() {
        this.channelEndTime = 0;
        this.channelBodyId = null;
        this.channelStartPos = null;
    }

    public void enterCooldown() {
        int sec = NoellesRolesConfig.HANDLER.instance().greatDetectiveCooldownSeconds;
        this.cooldown = this.player.level().getGameTime() + GameConstants.getInTicks(0, sec);
    }

    public long getCooldownLeftTime() {
        if (this.cooldown > 0) {
            long res = this.cooldown - this.player.level().getGameTime();
            if (res < 0)
                res = 0;
            return res;
        }
        return 0;
    }

    private void playChannelStartFx(ServerPlayer sp) {
        if (sp.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SMOKE, sp.getX(), sp.getY() + 1.2, sp.getZ(),
                    6, 0.2, 0.2, 0.2, 0.01);
            sl.playSound(null, sp.blockPosition(), SoundEvents.BOOK_PAGE_TURN,
                    SoundSource.PLAYERS, 0.7f, 0.9f);
        }
    }

    public void playCompleteFx(ServerPlayer sp) {
        if (sp.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.ENCHANT, sp.getX(), sp.getY() + 1.2, sp.getZ(),
                    16, 0.3, 0.4, 0.3, 0.12);
            sl.playSound(null, sp.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE,
                    SoundSource.PLAYERS, 0.6f, 1.2f);
        }
    }

    public record DeathNote(String victimName, int secondsAgo) {
        public CompoundTag toNbt() {
            CompoundTag t = new CompoundTag();
            t.putString("n", victimName);
            t.putInt("s", secondsAgo);
            return t;
        }

        public static DeathNote fromNbt(CompoundTag t) {
            return new DeathNote(t.getString("n"), t.getInt("s"));
        }
    }
}
