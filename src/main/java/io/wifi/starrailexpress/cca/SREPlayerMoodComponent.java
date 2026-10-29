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

package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent.Task;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.*;

public class SREPlayerMoodComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<SREPlayerMoodComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("mood"),
            SREPlayerMoodComponent.class);
    private final Player player;
    private float mood = 1f;
    /** 默认心情值上限。 */
    public static final float DEFAULT_MOOD_MAX = 1f;
    /**
     * 玩家心情值上限的临时覆盖值。
     * 负数（-1f）表示未覆盖，使用职业定义的上限（默认 1.0）；
     * 覆盖值本身会被夹取到 &gt;=0，因此允许设为 0（或 0~1 之间的任意合法上限）。
     */
    private float customMaxMood = -1f;
    public SREPlayerTaskComponent playerTaskComponent;
    private final HashMap<UUID, ItemStack> psychosisItems = new HashMap<>();
    private static List<Item> cachedPsychosisItems = null;
    public static ArrayList<String> canSyncedRolePaths = new ArrayList<>();

    public SREPlayerMoodComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return this.player;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorldComponent.isRunning()) {
            var role = gameWorldComponent.getRole(player);
            if (role != null)
                if (canSyncedRolePaths.contains(role.identifier().getPath())) {
                    return true;
                }
        }
        return player == this.player;
    }

    @Override
    public void init() {
        if (playerTaskComponent == null) {
            this.playerTaskComponent = SREPlayerTaskComponent.KEY.get(player);
        }

        this.playerTaskComponent.init();
        this.psychosisItems.clear();
        this.setMood(1f);
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    private List<Item> getPsychosisItemPool() {
        if (cachedPsychosisItems == null) {
            cachedPsychosisItems = this.player.registryAccess()
                    .asGetterLookup()
                    .lookupOrThrow(Registries.ITEM)
                    .get(TMMItemTags.PSYCHOSIS_ITEMS)
                    .map(HolderSet.ListBacked::stream)
                    .map(stream -> stream.map(Holder::value).toList())
                    .orElseGet(() -> {
                        SRE.LOGGER.error("Server provided empty tag {}", TMMItemTags.PSYCHOSIS_ITEMS.location());
                        return List.of();
                    });
        }
        return cachedPsychosisItems;
    }

    @Override
    public void clientTick() {
        if (!SREGameWorldComponent.KEY.get(this.player.level()).isRunning()
                || !SREClient.isPlayerAliveAndInSurvival()) {
            if (this.mood < 1f) {
                this.mood = 1f;
            }
            return;
        }

        if (SREClient.gameComponent == null) {
            return;
        }
        if (this.playerTaskComponent == null) {
            this.playerTaskComponent = SREPlayerTaskComponent.KEY.get(this.player);
        }
        if (!SREClient.gameComponent.getGameMode().hasMood()) {
            this.mood = 1f;
            return;
        }
        if (ModEffects.hasFullMoodLock(this.player)) {
            this.mood = 1f;
            return;
        }
        if (!this.playerTaskComponent.tasks.isEmpty()) {
            float drainMultiplier = ModEffects.getMoodDrainMultiplier(this.player);
            if (this.mood > 0) {
                this.mood = this.mood
                        - this.playerTaskComponent.tasks.size() * GameConstants.MOOD_DRAIN * drainMultiplier;
            }
            if (this.mood < 0)
                this.mood = 0;
        }

        float moodRegen = ModEffects.getMoodRegenPerTick(this.player);
        float maxMood = getMoodMax();
        if (moodRegen > 0f && this.mood < maxMood) {
            this.mood = Math.min(maxMood, this.mood + moodRegen);
        }

        if (this.isLowerThanMid()) {
            // imagine random items for players
            for (Player playerEntity : this.player.level().players()) {
                if (!playerEntity.equals(this.player)
                        && this.player.level().getRandom().nextInt(GameConstants.ITEM_PSYCHOSIS_REROLL_TIME) == 0) {
                    ItemStack psychosisStack;
                    List<Item> taggedItems = getPsychosisItemPool();

                    if (!taggedItems.isEmpty()
                            && this.player.getRandom().nextFloat() < GameConstants.ITEM_PSYCHOSIS_CHANCE) {
                        Item item = Util.getRandom(taggedItems, this.player.getRandom());
                        psychosisStack = new ItemStack(item);
                    } else {
                        psychosisStack = playerEntity.getMainHandItem();
                    }

                    // this.psychosisItems.put(playerEntity.getUuid(),
                    // playerEntity.getRandom().nextFloat() < GameConstants.ITEM_PSYCHOSIS_CHANCE ?
                    // PSYCHOSIS_ITEM_POOL[playerEntity.getRandom().nextInt(PSYCHOSIS_ITEM_POOL.length)].getDefaultStack()
                    // : playerEntity.getMainHandStack());
                    this.psychosisItems.put(playerEntity.getUUID(), psychosisStack);
                }
            }
        } else {
            if (!this.psychosisItems.isEmpty())
                this.psychosisItems.clear();
        }
    }

    @Override
    public void serverTick() {
        if (this.playerTaskComponent == null) {
            this.playerTaskComponent = SREPlayerTaskComponent.KEY.get(this.player);
        }
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());
        if (!gameWorldComponent.isRunning() || !GameUtils.isPlayerAliveAndSurvival(this.player)) {
            if (this.mood < 1f) {
                this.mood = 1f;
            }
            return;
        }
        boolean shouldSync = false;
        if (gameWorldComponent.gameMode.hasMood()) {
            if (ModEffects.hasFullMoodLock(this.player)) {
                if (this.mood != 1f) {
                    this.mood = 1f;
                    shouldSync = true;
                }
            } else if (!this.playerTaskComponent.tasks.isEmpty()) {
                float drainMultiplier = ModEffects.getMoodDrainMultiplier(this.player);
                if (this.mood > 0) {
                    this.mood = this.mood
                            - this.playerTaskComponent.tasks.size() * GameConstants.MOOD_DRAIN * drainMultiplier;
                }
                if (this.mood < 0)
                    this.mood = 0;
                if (this.playerTaskComponent.nextTaskTimer % 100 == 0) { // 5s一次同步
                    shouldSync = true;
                }
            }

            float moodRegen = ModEffects.getMoodRegenPerTick(this.player);
            float maxMood = getMoodMax();
            if (moodRegen > 0f && this.mood < maxMood) {
                this.mood = Math.min(maxMood, this.mood + moodRegen);
                if (this.player.tickCount % 200 == 0) {
                    shouldSync = true;
                }
            }

        } else {
            this.mood = 1f;
        }

        if (shouldSync)
            this.sync();

    }

    public float getMood() {
        if (ModEffects.hasFullMoodLock(this.player)) {
            return 1f;
        }
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());

        SRERole role = gameWorldComponent.getRole(player);
        if (gameWorldComponent.isRunning() && role != null && role.getMoodType() == SRERole.MoodType.REAL) {
            return this.mood;
        } else
            return 1;
    }

    /**
     * 获取该玩家当前的心情值上限（与当前心情值无关）。
     * <p>
     * 计算规则：
     * <ol>
     * <li>若通过 {@link #setMoodMax(float)} 覆盖了上限，则以覆盖值为基础；</li>
     * <li>否则取当前职业通过 {@link SRERole#setMaxMood(float)} 定义的上限（默认 1.0）；</li>
     * <li>再叠加「san值上限提升」药水效果：每级 +0.5。</li>
     * </ol>
     * 该值仅作为心情值的夹取上限，不会主动改变当前心情值。
     */
    public float getMoodMax() {
        float max;
        if (this.customMaxMood >= 0f) {
            // 已通过 setMoodMax 显式覆盖：可为 0~1 之间的任意上限（含 0）
            max = this.customMaxMood;
        } else {
            SRERole role = SREGameWorldComponent.KEY.get(this.player.level()).getRole(this.player);
            max = role != null ? role.getMaxMood() : DEFAULT_MOOD_MAX;
        }
        if (max < 0f) {
            // 职业定义异常（负数）时回退到默认上限
            max = DEFAULT_MOOD_MAX;
        }
        int amp = ModEffects.getMoodMaxBonusAmplifier(this.player);
        if (amp >= 0) {
            max += ModEffects.MOOD_MAX_BONUS_PER_LEVEL * (amp + 1);
        }
        // 保险：心情值上限永远不小于 0
        return Math.max(0f, max);
    }

    /**
     * 通用方法：设置该玩家的心情值上限（覆盖职业默认值）。
     * <p>
     * 传入负数可清除覆盖，恢复为职业定义的上限；传入 0 或 0~1 之间的数值则作为新的上限。
     * 任何覆盖值都会被夹取到 &gt;=0。修改后会把当前心情值夹取到新上限内
     * （只降不升，不会主动填充心情）。
     */
    public void setMoodMax(float maxMood) {
        if (maxMood < 0f) {
            // 负数视为清除覆盖，恢复职业默认上限
            this.customMaxMood = -1f;
        } else {
            // 保险：显式设置的上限不小于 0
            this.customMaxMood = Math.max(0f, maxMood);
        }
        float max = getMoodMax();
        if (this.mood > max) {
            this.mood = max;
        } else if (this.mood < 0f) {
            this.mood = 0f;
        }
        this.sync();
    }

    /** 清除 {@link #setMoodMax(float)} 设置的上限覆盖，恢复职业默认上限。 */
    public void resetMoodMax() {
        setMoodMax(-1f);
    }

    public void setMood(float mood) {
        if (ModEffects.hasFullMoodLock(this.player)) {
            if (this.mood != 1f) {
                this.mood = 1f;
                this.sync();
            }
            return;
        }
        SRERole role = SREGameWorldComponent.KEY.get(this.player.level()).getRole(player);
        float maxMood = getMoodMax();
        if (this.mood > maxMood)
            this.mood = maxMood;
        else if (this.mood < 0f)
            this.mood = 0f;

        if (role != null && role.getMoodType() == SRERole.MoodType.REAL) {
            float clampedMood = Math.clamp(mood, 0f, maxMood);
            // 只有当情绪变化超过0.05时才同步（减少网络占用）
            if (Math.abs(this.mood - clampedMood) > 0.01f) {
                this.mood = clampedMood;
                this.sync();
            } else {
                this.mood = clampedMood;
            }
        } else {
            if (this.mood != 1f) {
                this.mood = 1f;
                this.sync();
            }
        }
    }

    public void eatFood() {
        if (this.playerTaskComponent == null) {
            this.playerTaskComponent = SREPlayerTaskComponent.KEY.get(this.player);
        }
        this.playerTaskComponent.eatFood();
    }

    public void playNoteBlock() {
        if (this.playerTaskComponent == null) {
            this.playerTaskComponent = SREPlayerTaskComponent.KEY.get(this.player);
        }
        this.playerTaskComponent.playNoteBlock();
    }

    public void drinkCocktail() {
        if (this.playerTaskComponent == null) {
            this.playerTaskComponent = SREPlayerTaskComponent.KEY.get(this.player);
        }
        this.playerTaskComponent.drinkCocktail();
    }

    public boolean isLowerThanMid() {
        return this.getMood() < GameConstants.MID_MOOD_THRESHOLD;
    }

    public boolean isLowerThanDepressed() {
        return this.getMood() < GameConstants.DEPRESSIVE_MOOD_THRESHOLD;
    }

    public boolean isHigherThanAngry() {
        return this.getMood() > GameConstants.ANGRY_MOOD_THRESHOLD;
    }

    public HashMap<UUID, ItemStack> getPsychosisItems() {
        return this.psychosisItems;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registryLookup) {
        tag.putFloat("mood", this.mood);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registryLookup) {
        this.mood = tag.contains("mood", Tag.TAG_FLOAT) ? tag.getFloat("mood") : 1f;
    }

    public void addMood(float value) {
        var gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());
        SRERole role = gameWorldComponent.getRole(player);
        if (role != null && role.getMoodType() == SRERole.MoodType.REAL)
            this.setMood(this.mood + value);
        else {
            this.mood = 1f;
        }
    }

    public Map<Task, SREPlayerTaskComponent.TrainTask> getTasks() {
        if (this.playerTaskComponent == null) {
            this.playerTaskComponent = SREPlayerTaskComponent.KEY.get(this.player);
        }
        return this.playerTaskComponent.tasks;
    }

    @Override
    public void readFromNbt(CompoundTag tag, Provider registryLookup) {
    }

    @Override
    public void writeToNbt(CompoundTag tag, Provider registryLookup) {
    }

}