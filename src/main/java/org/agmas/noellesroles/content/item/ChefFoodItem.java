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

import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent.TrainTask;
import io.wifi.starrailexpress.customitem.CustomItemCooldownKeys;
import io.wifi.starrailexpress.customitem.CustomItemRuntime;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.ModDataComponentTypes;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.client.scene.SceneTaskManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

public class ChefFoodItem extends Item {
    /** 「减少物品冷却」效果的上限：至多减少 3 秒 */
    private static final int MAX_COOLDOWN_REDUCE_SECONDS = 3;
    private static final int MAX_COOLDOWN_REDUCE_TICKS = MAX_COOLDOWN_REDUCE_SECONDS * 20;

    private static Properties __warp_init(Properties properties) {
        var tag = new CompoundTag();
        var listTag = new ListTag();
        tag.put("effects", listTag);
        properties.component(ModDataComponentTypes.COOKED, tag);
        return properties;
    }

    public ChefFoodItem(Properties properties) {
        super(__warp_init(properties.food(new FoodProperties(10, 10, true, 1, Optional.empty(), List.of()))));
    }

    public static void randomModel(ItemStack cooked_food) {
        Random random = new Random();
        int randomI = random.nextInt(1, 5);
        cooked_food.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(randomI));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
            List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        var cookData = stack.get(ModDataComponentTypes.COOKED);
        if (cookData != null) {
            Map<Integer, Float> effects = ModDataComponentTypes.getCookedFoodInfo(cookData);
            if (!effects.isEmpty()) {
                tooltipComponents.add(Component.translatable("item.noellesroles.cooked_food.effects_title")
                        .withStyle(ChatFormatting.GOLD));
                for (Map.Entry<Integer, Float> entry : effects.entrySet()) {
                    int type = entry.getKey();
                    float duration = Math.min(entry.getValue(), 120f);
                    // 非整数时长（如减少物品冷却的 0.5 秒）保留一位小数，避免被 (int) 截断成 0
                    String seconds = duration == (int) duration
                            ? String.valueOf((int) duration)
                            : String.format("%.1f", duration);
                    tooltipComponents.add(getEffectComponent(type, seconds));
                }
            }
        }
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    private static Component getEffectComponent(int type, String seconds) {
        ChatFormatting color = getEffectColor(type);
        Component component = switch (type) {
            case 1 -> Component.translatable("item.noellesroles.cooked_food.effect.glowing", seconds);
            case 2 -> Component.translatable("item.noellesroles.cooked_food.effect.restore_san");
            case 3 -> Component.translatable("item.noellesroles.cooked_food.effect.reduce_cooldown", seconds);
            case 4 -> Component.translatable("item.noellesroles.cooked_food.effect.speed", seconds);
            case 5 -> Component.translatable("item.noellesroles.cooked_food.effect.skip_task");
            case 6 -> Component.translatable("item.noellesroles.cooked_food.effect.night_vision", seconds);
            case 7 -> Component.translatable("item.noellesroles.cooked_food.effect.add_gold", seconds);
            case -1 -> Component.translatable("item.noellesroles.cooked_food.effect.nausea", seconds);
            case -2 -> Component.translatable("item.noellesroles.cooked_food.effect.darkness", seconds);
            case -3 -> Component.translatable("item.noellesroles.cooked_food.effect.slowness", seconds);
            default -> Component.translatable("item.noellesroles.cooked_food.effect.unknown");
        };
        return component.copy().withStyle(color);
    }

    private static ChatFormatting getEffectColor(int type) {
        if (type > 0) {
            return switch (type) {
                case 2 -> ChatFormatting.GREEN;
                case 3, 6 -> ChatFormatting.BLUE;
                case 4 -> ChatFormatting.YELLOW;
                case 5 -> ChatFormatting.LIGHT_PURPLE;
                case 7 -> ChatFormatting.GOLD;
                default -> ChatFormatting.AQUA;
            };
        } else {
            return ChatFormatting.RED;
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack itemStack, Level level, LivingEntity livingEntity) {
        var map = ModDataComponentTypes.getCookedFoodInfo(itemStack.get(ModDataComponentTypes.COOKED));
        Noellesroles.LOGGER.info("Size" + map.size());
        // buff1 发光
        // buff2 回san
        // buff3 减少物品冷却
        // buff4 速度1
        // buff5 跳过当前任务
        // buff6 夜视
        // buff7 加钱

        // buff-1 反胃
        // buff-2 黑暗
        // buff-3 缓慢1
        itemStack = super.finishUsingItem(itemStack, level, livingEntity);
        if (level.isClientSide)
            return itemStack;
        for (var it : map.entrySet()) {
            int type = it.getKey();
            float duration = it.getValue();
            if (duration >= 120f) {
                duration = 120f;
            }
            switch (type) {
                case 1:
                    livingEntity.addEffect(new MobEffectInstance(
                            MobEffects.GLOWING,
                            (int) (duration * 20), // 持续时间（tick）
                            0, // 等级（0 = 速度 I）
                            false, // ambient（环境效果，如信标）
                            true, // showParticles（显示粒子）
                            true // showIcon（显示图标）
                    ));
                    break;
                case 2:
                    var mm = SREPlayerMoodComponent.KEY.maybeGet(livingEntity).orElse(null);
                    if (mm != null) {
                        float nowMood = mm.getMood();
                        nowMood += (duration / 20);
                        if (nowMood >= 1)
                            nowMood = 1;
                        mm.setMood(nowMood);
                    }
                    break;
                case 3: {
                    // 减少物品冷却：duration 是「秒」，冷却是「tick」，直接相减会差 20 倍。
                    // 至多减少 MAX_COOLDOWN_REDUCE_SECONDS 秒；不减角色技能冷却（SREAbilityPlayerComponent）。
                    // 自定义物品的冷却键是未注册物品，交给 CustomItemRuntime 单独处理 + 同步。
                    final int reduceTicks = Math.min(Math.round(duration * 20), MAX_COOLDOWN_REDUCE_TICKS);
                    if (reduceTicks > 0 && livingEntity instanceof Player p) {
                        final net.minecraft.world.item.ItemCooldowns cooldowns = p.getCooldowns();
                        if (!cooldowns.cooldowns.isEmpty()) {
                            for (net.minecraft.world.item.Item item : new ArrayList<>(cooldowns.cooldowns.keySet())) {
                                if (CustomItemCooldownKeys.isCustomKey(item)) {
                                    continue;
                                }
                                final var instance = cooldowns.cooldowns.get(item);
                                if (instance == null) {
                                    continue;
                                }
                                final int next = instance.endTime - cooldowns.tickCount - reduceTicks;
                                if (next <= 0) {
                                    cooldowns.removeCooldown(item);
                                } else {
                                    cooldowns.addCooldown(item, next);
                                }
                            }
                        }
                        if (p instanceof net.minecraft.server.level.ServerPlayer sp) {
                            CustomItemRuntime.reduceCooldowns(sp, reduceTicks);
                        }
                    }
                    break;
                }
                case 4:
                    livingEntity.addEffect(new MobEffectInstance(
                            MobEffects.MOVEMENT_SPEED,
                            (int) (duration * 20), // 持续时间（tick）
                            0, // 等级（0 = 速度 I）
                            false, // ambient（环境效果，如信标）
                            true, // showParticles（显示粒子）
                            true // showIcon（显示图标）
                    ));
                    break;
                case 5:
                    // 跳过当前任务：只刷新任务本身，绝不走「任务完成」奖励流程
                    // （不发任务奖励金币、不加理智——跳过不算完成任务）。
                    var mm2 = SREPlayerTaskComponent.KEY.maybeGet(livingEntity).orElse(null);
                    // 只有当前实际拥有任务时才跳过；没有任务就什么都不做，
                    // 不额外刷新出一个新任务（否则等于白送一个任务）。
                    if (mm2 != null && !mm2.tasks.isEmpty()) {
                        mm2.tasks.clear();
                        // 与其它「清空任务」实现保持一致：重置并列任务相关状态，
                        // 否则残留的 parallelTaskGenerated 会让下一个真正完成的任务
                        // 额外吃一份并列任务理智加成。
                        mm2.parallelTaskTypes.clear();
                        mm2.parallelTaskGenerated = false;
                        mm2.currentTaskAge = 0;
                        // 同步清理场景任务状态：SceneTaskManager 的表不随任务组件清空，
                        // 残留条目后续仍会判定「完成」并直接发放任务奖励（金币+理智）。
                        if (livingEntity instanceof Player skipPlayer) {
                            SceneTaskManager.clear(skipPlayer);
                        }
                        TrainTask task = mm2.generateTask();
                        if (task != null) {
                            mm2.tasks.put(task.getType(), task);
                            mm2.timesGotten.putIfAbsent(task.getType(), 1);
                            mm2.timesGotten.put(task.getType(), (Integer) mm2.timesGotten.get(task.getType()) + 1);
                            var refreshedMood = SREPlayerMoodComponent.KEY.maybeGet(livingEntity).orElse(null);
                            if (refreshedMood != null) {
                                mm2.moodWhenTaskAssigned = refreshedMood.getMood();
                            }
                        }
                        mm2.sync();
                    }
                    break;
                case 6:
                    livingEntity.addEffect(new MobEffectInstance(
                            MobEffects.NIGHT_VISION,
                            (int) (duration * 20), // 持续时间（tick）
                            0, // 等级（0 = 速度 I）
                            false, // ambient（环境效果，如信标）
                            true, // showParticles（显示粒子）
                            true // showIcon（显示图标）
                    ));
                    break;
                case 7:
                    var pmmc = SREPlayerShopComponent.KEY.maybeGet(livingEntity).orElse(null);
                    pmmc.addToBalance((int) duration);
                    break;
                case -1:
                    livingEntity.addEffect(new MobEffectInstance(
                            MobEffects.CONFUSION,
                            (int) (duration * 20), // 持续时间（tick）
                            0, // 等级（0 = 速度 I）
                            false, // ambient（环境效果，如信标）
                            true, // showParticles（显示粒子）
                            true // showIcon（显示图标）
                    ));
                    break;
                case -2:
                    livingEntity.addEffect(new MobEffectInstance(
                            MobEffects.DARKNESS,
                            (int) (duration * 20), // 持续时间（tick）
                            0, // 等级（0 = 速度 I）
                            false, // ambient（环境效果，如信标）
                            true, // showParticles（显示粒子）
                            true // showIcon（显示图标）
                    ));
                    break;
                case -3:
                    livingEntity.addEffect(new MobEffectInstance(
                            MobEffects.MOVEMENT_SLOWDOWN,
                            (int) (duration * 20), // 持续时间（tick）
                            0, // 等级（0 = 速度 I）
                            false, // ambient（环境效果，如信标）
                            true, // showParticles（显示粒子）
                            true // showIcon（显示图标）
                    ));
                    break;
                default:
                    break;
            }
        }

        return itemStack;
    };
}
