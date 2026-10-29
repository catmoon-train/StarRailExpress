package org.agmas.noellesroles.game.halloween;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.cca.SREPlayerMinigameTaskComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import io.wifi.starrailexpress.content.command.StaminaCommand;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.game.halloween.item.HalloweenItem.Action;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.scene.SceneTaskManager;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 活动消耗品、临时增益和套装。所有状态属于具体世界，不修改职业或全局护盾/体力 buff。 */
public final class HalloweenConsumables {
    private static final Map<ServerLevel, Map<UUID, Buffs>> STATES = new IdentityHashMap<>();
    private static final EquipmentSlot[] COSTUME_SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

    private HalloweenConsumables() {}

    public static boolean canUse(Player player) {
        return !player.level().isClientSide && HalloweenRuntime.isEnabled(player.level())
                && player.isAlive() && GameUtils.isPlayerAliveAndSurvival(player);
    }

    private static Buffs state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.serverLevel(), ignored -> new HashMap<>())
                .computeIfAbsent(player.getUUID(), ignored -> new Buffs(player));
    }

    public static boolean use(ServerPlayer player, Action action) {
        if (!canUse(player)) return false;
        switch (action) {
            case HEART, COIN -> {
                SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
                int gain = action == Action.HEART ? 50 : 5;
                shop.setBalance((int) Math.min(Integer.MAX_VALUE, (long) shop.balance + gain));
                message(player, "coins", gain);
            }
            case CANDY -> {
                Buffs buffs = state(player);
                long until = player.serverLevel().getGameTime() + HalloweenConfig.get().candyDurationTicks;
                buffs.candyUntil = Math.max(buffs.candyUntil, until);
                int outcome = player.getRandom().nextInt(4);
                switch (outcome) {
                    case 0 -> io.wifi.starrailexpress.cca.SREArmorPlayerComponent.KEY.get(player)
                            .addTimedArmor(1, HalloweenConfig.get().candyDurationTicks);
                    case 1 -> skipTask(player);
                    case 2 -> buffs.staminaUntil = Math.max(buffs.staminaUntil, until);
                    case 3 -> buffs.nightUntil = Math.max(buffs.nightUntil, until);
                    default -> throw new IllegalStateException();
                }
                updateEffects(buffs, player.serverLevel().getGameTime());
                message(player, "candy_" + outcome);
            }
            case BAG -> {
                HalloweenConfig.Loot loot = HalloweenConfig.rollLoot(HalloweenConfig.get().loot, player.getRandom().nextDouble());
                if (loot == null) return false;
                ResourceLocation id = ResourceLocation.tryParse(loot.item);
                if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return false;
                Item item = BuiltInRegistries.ITEM.get(id);
                if (item == net.minecraft.world.item.Items.AIR) return false;
                // 先消耗袋子由物品调用方完成；满背包奖励落在脚下，不会吞掉奖励。
                give(player, item, loot.count);
                message(player, "bag", Component.translatable(item.getDescriptionId()), loot.count);
            }
            default -> { return false; }
        }
        return true;
    }

    private static void skipTask(ServerPlayer player) {
        SREPlayerTaskComponent tasks = SREPlayerTaskComponent.KEY.get(player);
        // 跳过而非伪造完成：不发任务奖励、不推进魂契计数，也不清掉其它并行任务。
        SREPlayerTaskComponent.Task type = tasks.tasks.keySet().stream().sorted().findFirst().orElse(null);
        if (type == null) {
            SREPlayerMinigameTaskComponent minigame = SREPlayerMinigameTaskComponent.KEY.get(player);
            if (minigame.pendingMinigameTasks > 0) {
                minigame.pendingMinigameTasks--;
                if (minigame.pendingMinigameTasks == 0) minigame.targetMinigameId = null;
                minigame.sync();
            }
            return;
        }
        tasks.tasks.remove(type);
        tasks.parallelTaskTypes.remove(type);
        SceneTaskManager.Type scene = switch (type) {
            case LIGHT_STOVE -> SceneTaskManager.Type.LIGHT_STOVE;
            case CLEAN_DUST -> SceneTaskManager.Type.CLEAN_DUST;
            case TRANSPORT -> SceneTaskManager.Type.TRANSPORT;
            case PRAY -> SceneTaskManager.Type.PRAY;
            case PRUNE_BUSH -> SceneTaskManager.Type.PRUNE_BUSH;
            case HARVEST_CROP -> SceneTaskManager.Type.HARVEST_CROP;
            default -> null;
        };
        if (scene != null) SceneTaskManager.clear(player, scene);
        if (tasks.tasks.isEmpty()) {
            tasks.currentTaskAge = 0;
            tasks.parallelTaskGenerated = false;
            tasks.parallelTaskTypes.clear();
        }
        tasks.sync();
    }

    public static void tick(ServerLevel level) {
        if (!HalloweenRuntime.isEnabled(level)) {
            clear(level);
            return;
        }
        long now = level.getGameTime();
        Map<UUID, Buffs> states = STATES.computeIfAbsent(level, ignored -> new HashMap<>());
        states.values().removeIf(buffs -> {
            if (buffs.player.serverLevel() != level || !level.players().contains(buffs.player) || !canUse(buffs.player)) {
                restore(buffs);
                return true;
            }
            return false;
        });
        for (ServerPlayer player : level.players()) {
            if (!canUse(player)) continue;
            Buffs buffs = states.get(player.getUUID());
            boolean wearing = hasCostume(player);
            if (wearing && buffs == null) buffs = state(player);
            if (buffs == null) continue;
            if (wearing) {
                // 仅累计实际穿齐的 tick；脱下只暂停，不重置或追补计时，不能靠重穿刷袋。
                if (++buffs.costumeTicks >= HalloweenConfig.get().costumeBagIntervalTicks) {
                    buffs.costumeTicks = 0;
                    give(player, HalloweenItems.CANDY_BAG, 1);
                    message(player, "costume_bag");
                }
            }
            updateEffects(buffs, now);
        }
    }

    /** 由南瓜模块维护；更改时立即同步速度，保留糖果和其它来源的效果。 */
    static void ownerSpeed(ServerPlayer player, boolean inside) {
        Buffs buffs = inside ? state(player) : STATES.getOrDefault(player.serverLevel(), Map.of()).get(player.getUUID());
        if (buffs == null) return;
        buffs.ownerInside = inside && canUse(player);
        if (!canUse(player)) restore(buffs);
        else updateEffects(buffs, player.serverLevel().getGameTime());
    }

    private static void updateEffects(Buffs buffs, long now) {
        int speedDuration = (int) Math.max(0, buffs.candyUntil - now);
        if (buffs.ownerInside) {
            long remaining = buffs.speed.appliedUntil - now;
            speedDuration = buffs.speed.applied && buffs.speed.amplifier == 1 && remaining > 20 ? (int) remaining : 200;
        }
        buffs.speed.update(buffs.player, now, buffs.ownerInside ? 1 : 0, speedDuration);
        buffs.night.update(buffs.player, now, 0, (int) Math.max(0, buffs.nightUntil - now));
        if (buffs.staminaUntil > now) {
            var role = SREGameWorldComponent.KEY.get(buffs.player.level()).getRole(buffs.player);
            if (role != null) {
                int max = role.getMaxSprintTime(buffs.player);
                if (max >= 0 && max != Integer.MAX_VALUE) {
                    // 走现有读写/同步 API，每 tick 补满；不写回其它来源的无限体力开关。
                    StaminaCommand.setStamina(buffs.player, max * ModEffects.getStaminaCapacityMultiplier(buffs.player));
                }
            }
        }
    }

    /** 供统一死亡拦截调用；返回 true 表示本次伤害被本模块消耗的盾或套装挡住。 */
    public static boolean tryBlockDamage(Player player) {
        if (!(player instanceof ServerPlayer sp) || !canUse(player)) return false;
        if (!hasCostume(player)) return false;
        for (EquipmentSlot slot : COSTUME_SLOTS) player.setItemSlot(slot, ItemStack.EMPTY);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        message(sp, "costume_block");
        return true;
    }

    private static boolean hasCostume(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(HalloweenItems.PUMPKIN_HELMET)
                && player.getItemBySlot(EquipmentSlot.CHEST).is(HalloweenItems.PUMPKIN_CHESTPLATE)
                && player.getItemBySlot(EquipmentSlot.LEGS).is(HalloweenItems.PUMPKIN_LEGGINGS)
                && player.getItemBySlot(EquipmentSlot.FEET).is(HalloweenItems.PUMPKIN_BOOTS);
    }

    public static void clear(ServerLevel level) {
        Map<UUID, Buffs> states = STATES.remove(level);
        if (states != null) states.values().forEach(HalloweenConsumables::restore);
    }

    static void removePlayer(Player player) {
        for (Map<UUID, Buffs> states : STATES.values()) {
            Buffs buffs = states.remove(player.getUUID());
            if (buffs != null) restore(buffs);
        }
    }

    private static void restore(Buffs buffs) {
        long now = buffs.player.serverLevel().getGameTime();
        buffs.speed.clear(buffs.player, now);
        buffs.night.clear(buffs.player, now);
    }

    private static void give(ServerPlayer player, Item item, int count) {
        while (count > 0) {
            int amount = Math.min(count, item.getDefaultMaxStackSize());
            ItemStack stack = new ItemStack(item, amount);
            org.agmas.noellesroles.game.halloween.pumpkin.PumpkinSpiritHandler.onRewardStack(player, stack);
            if (!player.getInventory().add(stack)) player.drop(stack, false);
            count -= amount;
        }
        player.containerMenu.broadcastChanges();
    }

    private static void message(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.noellesroles.halloween." + key, args), true);
    }

    private static final class Buffs {
        final ServerPlayer player;
        final EffectLease speed = new EffectLease(MobEffects.MOVEMENT_SPEED);
        final EffectLease night = new EffectLease(MobEffects.NIGHT_VISION);
        long candyUntil, staminaUntil, nightUntil;
        int costumeTicks;
        boolean ownerInside;
        Buffs(ServerPlayer player) { this.player = player; }
    }

    /** 只撤销可确认属于本模块的效果；保留之前的效果（含隐藏效果链），并扣除其自然流逝时间。 */
    private static final class EffectLease {
        final Holder<MobEffect> effect;
        CompoundTag previous;
        long previousAt, appliedUntil;
        int amplifier;
        boolean applied;
        EffectLease(Holder<MobEffect> effect) { this.effect = effect; }

        boolean owns(MobEffectInstance current, long now) {
            return applied && current != null && current.getAmplifier() == amplifier
                    && current.isAmbient() && !current.isVisible() && current.showIcon()
                    && Math.abs((long) current.getDuration() - (appliedUntil - now)) <= 2;
        }

        void update(ServerPlayer player, long now, int desiredAmplifier, int duration) {
            if (duration <= 0) { clear(player, now); return; }
            MobEffectInstance current = player.getEffect(effect);
            boolean owned = owns(current, now);
            if (!owned && current != null && (current.getAmplifier() > desiredAmplifier
                    || current.getAmplifier() == desiredAmplifier && (current.isInfiniteDuration() || current.getDuration() >= duration))) return;
            if (owned && amplifier == desiredAmplifier && current.getDuration() >= duration - 2) return;
            if (owned) clear(player, now);
            current = player.getEffect(effect);
            previous = current == null ? null : (CompoundTag) current.save();
            previousAt = now;
            amplifier = desiredAmplifier;
            appliedUntil = now + duration;
            applied = true;
            player.addEffect(new MobEffectInstance(effect, duration, desiredAmplifier, true, false, true));
        }

        void clear(ServerPlayer player, long now) {
            MobEffectInstance current = player.getEffect(effect);
            if (owns(current, now)) {
                // 期间新加入的低级效果可能藏在原版隐藏链里，也必须保留，不能只恢复进入时的快照。
                CompoundTag currentTag = (CompoundTag) current.save();
                MobEffectInstance hidden = null;
                if (currentTag.contains("hidden_effect")) {
                    CompoundTag hiddenTag = currentTag.getCompound("hidden_effect").copy();
                    hiddenTag.putString("id", currentTag.getString("id"));
                    hidden = MobEffectInstance.load(hiddenTag);
                }
                player.removeEffect(effect);
                if (previous != null) {
                    CompoundTag copy = previous.copy();
                    age(copy, (int) Math.min(Integer.MAX_VALUE, Math.max(0, now - previousAt)));
                    MobEffectInstance old = MobEffectInstance.load(copy);
                    if (old != null && (old.isInfiniteDuration() || old.getDuration() > 0)) player.addEffect(old);
                }
                if (hidden != null && (hidden.isInfiniteDuration() || hidden.getDuration() > 0)) player.addEffect(hidden);
            }
            applied = false;
            previous = null;
        }

        private static void age(CompoundTag tag, int elapsed) {
            if (tag.contains("duration") && tag.getInt("duration") >= 0) tag.putInt("duration", Math.max(0, tag.getInt("duration") - elapsed));
            if (tag.contains("hidden_effect")) age(tag.getCompound("hidden_effect"), elapsed);
        }
    }
}
