package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent.TrainTask;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

public class ChefFoodItem extends Item {
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
                    int seconds = (int) duration;
                    tooltipComponents.add(getEffectComponent(type, seconds));
                }
            }
        }
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    private static Component getEffectComponent(int type, int seconds) {
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
        // buff3 减少技能冷却
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
                case 3:
                    if (livingEntity instanceof Player p) {
                        SREAbilityPlayerComponent pa = SREAbilityPlayerComponent.KEY.get(p);
                        if (pa.cooldown > 0) {
                            pa.cooldown -= duration;
                            if (pa.cooldown < 0)
                                pa.cooldown = 0;
                        }
                        pa.sync();
                    }
                    break;
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
                    var mm2 = SREPlayerTaskComponent.KEY.maybeGet(livingEntity).orElse(null);
                    if (mm2 != null) {
                        mm2.tasks.clear();
                        TrainTask task = mm2.generateTask();
                        if (task != null) {
                            mm2.tasks.put(task.getType(), task);
                            mm2.timesGotten.putIfAbsent(task.getType(), 1);
                            mm2.timesGotten.put(task.getType(), (Integer) mm2.timesGotten.get(task.getType()) + 1);
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
