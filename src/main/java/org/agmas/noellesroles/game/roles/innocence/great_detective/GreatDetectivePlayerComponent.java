package org.agmas.noellesroles.game.roles.innocence.great_detective;

import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.*;

/**
 * 大侦探（平民阵营）玩家组件。
 *
 * <p>
 * 仅同步给玩家自己。存储：
 * <ul>
 * <li>已经使用过推理技能的尸体（实体 UUID，一具尸体只能用一次）；</li>
 * <li>每名凶手（UUID）已掌握的线索列表（保持插入顺序，对应推理之书的页顺序）；</li>
 * <li>每名凶手触发"目标情况"时记录的距离快照（格，缺失=未触发，-1=无法定位，触发后冻结）。</li>
 * </ul>
 */
public class GreatDetectivePlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    public static final ComponentKey<GreatDetectivePlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "great_detective"),
            GreatDetectivePlayerComponent.class);

    private static final int COOLDOWN_TIME = 20 * 30;

    private final Player player;

    /** 已使用过技能的尸体（实体 UUID）。 */
    public final Set<UUID> usedCorpses = new HashSet<>();
    /** 凶手 UUID -> 已掌握线索（LinkedHashMap 保持页顺序）。 */
    public final LinkedHashMap<UUID, List<DetectiveClue>> clues = new LinkedHashMap<>();
    /** 凶手 UUID -> 触发"目标情况"时的距离快照（格）。 */
    public final HashMap<UUID, Integer> revealedDistance = new HashMap<>();
    public long cooldown = 0;

    public GreatDetectivePlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    public boolean isInCooldown() {
        return cooldown > 0 && cooldown > this.player.level().getGameTime();
    }

    @Override
    public void init() {
        usedCorpses.clear();
        clues.clear();
        revealedDistance.clear();
        cooldown = 0;
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

    public int clueCount(UUID killer) {
        return getClues(killer).size();
    }

    /** 按页顺序返回所有已记录的凶手 UUID。 */
    public List<UUID> getKillerOrder() {
        return new ArrayList<>(clues.keySet());
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

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (cooldown > 0) {
            tag.putLong("cd", cooldown);
        }
        ListTag used = new ListTag();
        for (UUID u : usedCorpses) {
            CompoundTag t = new CompoundTag();
            t.putUUID("u", u);
            used.add(t);
        }
        tag.put("used", used);

        ListTag killersTag = new ListTag();
        for (Map.Entry<UUID, List<DetectiveClue>> e : clues.entrySet()) {
            CompoundTag kt = new CompoundTag();
            kt.putUUID("killer", e.getKey());
            ListTag cl = new ListTag();
            for (DetectiveClue c : e.getValue()) {
                cl.add(c.toNbt());
            }
            kt.put("clues", cl);
            if (revealedDistance.containsKey(e.getKey())) {
                kt.putBoolean("hasDist", true);
                kt.putInt("dist", revealedDistance.get(e.getKey()));
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
        usedCorpses.clear();
        clues.clear();
        revealedDistance.clear();

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
                if (kt.getBoolean("hasDist")) {
                    revealedDistance.put(killer, kt.getInt("dist"));
                }
            }
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void clientTick() {
        if (this.cooldown > 0) {
            if (cooldown <= this.player.level().getGameTime()) {
                cooldown = 0;
            }
        }
    }

    @Override
    public void serverTick() {
        if (this.cooldown > 0) {
            if (cooldown <= this.player.level().getGameTime()) {
                cooldown = 0;
            }
        }
    }

    public void enterCooldown() {
        this.cooldown = this.player.level().getGameTime() + COOLDOWN_TIME;
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
}
