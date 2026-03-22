package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent.Task;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.SRE;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
        if (!SREGameWorldComponent.KEY.get(this.player.level()).isRunning() || !SREClient.isPlayerAliveAndInSurvival())
            return;
        if (this.playerTaskComponent == null) {
            this.playerTaskComponent = SREPlayerTaskComponent.KEY.get(this.player);
        }
        if (!this.playerTaskComponent.tasks.isEmpty()) {
            if (this.mood > 0)
                this.mood = this.mood - this.playerTaskComponent.tasks.size() * GameConstants.MOOD_DRAIN;
            if (this.mood < 0)
                this.mood = 0;
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
        if (!gameWorldComponent.isRunning() || !GameUtils.isPlayerAliveAndSurvival(this.player))
            return;
        boolean shouldSync = false;
        if (!this.playerTaskComponent.tasks.isEmpty()) {
            if (this.mood > 0)
                this.mood = this.mood - this.playerTaskComponent.tasks.size() * GameConstants.MOOD_DRAIN;// 替换setMood避免高频率同步
            if (this.mood < 0)
                this.mood = 0;
            if (this.playerTaskComponent.nextTaskTimer % 100 == 0) { // 5s一次同步
                shouldSync = true;
            }
        }

        if (shouldSync)
            this.sync();

        // 根据情绪值调整玩家速度
        updatePlayerMovementSpeed();
    }

    public float getMood() {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());

        SRERole role = gameWorldComponent.getRole(player);
        if (gameWorldComponent.isRunning() && role != null && role.getMoodType() == SRERole.MoodType.REAL) {
            return this.mood;
        } else
            return 1;
    }

    public void setMood(float mood) {
        SRERole role = SREGameWorldComponent.KEY.get(this.player.level()).getRole(player);
        if (this.mood > 1f)
            this.mood = 1f;
        else if (this.mood < 0f)
            this.mood = 0f;

        if (role != null && role.getMoodType() == SRERole.MoodType.REAL) {
            float clampedMood = Math.clamp(mood, 0, 1);
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

    /**
     * 根据玩家的情绪值更新其移动速度
     */
    private void updatePlayerMovementSpeed() {
        if (this.player instanceof ServerPlayer) {

            // 获取当前玩家的移动速度属性
            var speedAttribute = this.player.getAttribute(Attributes.MOVEMENT_SPEED);

            if (speedAttribute != null) {
                // 移除之前可能添加的修饰符
                speedAttribute.removeModifier(SRE.id("mood_speed_modifier"));
                if (SRE.isLobby) {
                    // 删除速度
                    return;
                }
                // 根据情绪值添加新的修饰符
                if (this.isLowerThanDepressed()) {
                    // 抑郁状态 - 降低20%速度
                    AttributeModifier modifier = new AttributeModifier(
                            SRE.id("mood_speed_modifier"),
                            -0.2,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
                    speedAttribute.addTransientModifier(modifier);
                } else if (this.isHigherThanAngry()) {
                    // 愤怒状态 - 提高15%速度
                    AttributeModifier modifier = new AttributeModifier(
                            SRE.id("mood_speed_modifier"),
                            0.15,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
                    speedAttribute.addTransientModifier(modifier);
                }
                // 正常情绪范围内保持默认速度
            }
        }
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