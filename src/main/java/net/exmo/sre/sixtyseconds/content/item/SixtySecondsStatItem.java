package net.exmo.sre.sixtyseconds.content.item;

import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsSicknessSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Supplier;

/**
 * 通用状态恢复消耗品（药品/饮品/针剂…）：需按住右键使用，使用完成后按参数恢复
 * 健康/饱食/口渴/理智、降低污染、治愈生病与感染风险，并可附加药水效果。支持提升理智上限（sanityMaxBonus）。
 * tooltip 自动列出全部效果。仅 60s 模式可用。
 */
public class SixtySecondsStatItem extends Item {
    private final int health;
    private final int hunger;
    private final int thirst;
    private final int sanity;
    private final int pollutionReduce;
    private final boolean cure;
    private final Supplier<MobEffectInstance> effect;
    /** 使用时间（tick），治疗类 100-200（5-10秒），非治疗类 40-60（2-3秒） */
    private final int useDuration;
    /** 使用动画类型 */
    private final UseAnim useAnim;
    /** 理智上限永久加成（不为 0 时使用后生效，最高到 120） */
    private final int sanityMaxBonus;
    /** 永久上限加成（健康/饱食/口渴/污染，最高到 120） */
    private final int healthMaxBonus;
    private final int hungerMaxBonus;
    private final int thirstMaxBonus;
    private final int pollutionMaxBonus;
    /** 各上限可达到的最高值 */
    public static final int SANITY_MAX_CAP = 120;
    public static final int GENERIC_MAX_CAP = 120;

    public SixtySecondsStatItem(Properties properties, int health, int hunger, int thirst, int sanity,
            int pollutionReduce, boolean cure, Supplier<MobEffectInstance> effect,
            int useDuration, UseAnim useAnim) {
        this(properties, health, hunger, thirst, sanity, pollutionReduce, cure, effect, useDuration, useAnim, 0);
    }

    /** 含 sanityMaxBonus 的完整构造器（兼容旧代码） */
    public SixtySecondsStatItem(Properties properties, int health, int hunger, int thirst, int sanity,
            int pollutionReduce, boolean cure, Supplier<MobEffectInstance> effect,
            int useDuration, UseAnim useAnim, int sanityMaxBonus) {
        this(properties, health, hunger, thirst, sanity, pollutionReduce, cure, effect,
                useDuration, useAnim, sanityMaxBonus, 0, 0, 0, 0);
    }

    /** 含所有上限加成的完整构造器 */
    public SixtySecondsStatItem(Properties properties, int health, int hunger, int thirst, int sanity,
            int pollutionReduce, boolean cure, Supplier<MobEffectInstance> effect,
            int useDuration, UseAnim useAnim, int sanityMaxBonus,
            int healthMaxBonus, int hungerMaxBonus, int thirstMaxBonus, int pollutionMaxBonus) {
        super(properties);
        this.health = health;
        this.hunger = hunger;
        this.thirst = thirst;
        this.sanity = sanity;
        this.pollutionReduce = pollutionReduce;
        this.cure = cure;
        this.effect = effect;
        this.useDuration = useDuration;
        this.useAnim = useAnim;
        this.sanityMaxBonus = sanityMaxBonus;
        this.healthMaxBonus = healthMaxBonus;
        this.hungerMaxBonus = hungerMaxBonus;
        this.thirstMaxBonus = thirstMaxBonus;
        this.pollutionMaxBonus = pollutionMaxBonus;
    }

    /** 兼容旧构造（无 useDuration 的默认为 40 ticks = 2 秒 DRINK 动画） */
    public SixtySecondsStatItem(Properties properties, int health, int hunger, int thirst, int sanity,
            int pollutionReduce, boolean cure, Supplier<MobEffectInstance> effect) {
        this(properties, health, hunger, thirst, sanity, pollutionReduce, cure, effect, 40, UseAnim.DRINK);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return useDuration;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return useAnim;
    }

    @Override
    public SoundEvent getEatingSound() {
        return useAnim == UseAnim.DRINK ? SoundEvents.GENERIC_DRINK : SoundEvents.GENERIC_EAT;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        if (!SixtySecondsMod.isActive(world)) {
            return InteractionResultHolder.pass(user.getItemInHand(hand));
        }
        return ItemUtils.startUsingInstantly(world, user, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (!(user instanceof ServerPlayer serverPlayer)) {
            return stack;
        }
        if (!SixtySecondsMod.isActive(level)) {
            return stack;
        }
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(serverPlayer);
        stats.health = Math.min(stats.healthMax, stats.health + health);
        stats.hunger = Math.min(stats.hungerMax, stats.hunger + hunger);
        stats.thirst = Math.min(stats.thirstMax, stats.thirst + thirst);
        stats.sanity = Math.min(stats.sanityMax, stats.sanity + sanity);
        if (sanityMaxBonus > 0) {
            stats.sanityMax = Math.min(SANITY_MAX_CAP, stats.sanityMax + sanityMaxBonus);
        }
        if (healthMaxBonus > 0) {
            stats.healthMax = Math.min(GENERIC_MAX_CAP, stats.healthMax + healthMaxBonus);
        }
        if (hungerMaxBonus > 0) {
            stats.hungerMax = Math.min(GENERIC_MAX_CAP, stats.hungerMax + hungerMaxBonus);
        }
        if (thirstMaxBonus > 0) {
            stats.thirstMax = Math.min(GENERIC_MAX_CAP, stats.thirstMax + thirstMaxBonus);
        }
        if (pollutionMaxBonus > 0) {
            stats.pollutionMax = Math.min(GENERIC_MAX_CAP, stats.pollutionMax + pollutionMaxBonus);
        }
        stats.pollution = Math.max(0, stats.pollution - pollutionReduce);
        stats.sync();
        if (cure) {
            SixtySecondsSicknessSystem.cure(serverPlayer);
        }
        if (effect != null) {
            serverPlayer.addEffect(effect.get());
        }
        if (!serverPlayer.isCreative()) {
            stack.shrink(1);
        }
        level.playSound(null, user.getX(), user.getY(), user.getZ(),
                SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.7F, 1.1F);
        CriteriaTriggers.CONSUME_ITEM.trigger(serverPlayer, stack);
        serverPlayer.awardStat(Stats.ITEM_USED.get(this));
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        // 显示使用时间
        float seconds = useDuration / 20.0F;
        tooltip.add(Component.translatable("tooltip.noellesroles.sixty_seconds.use_time",
                String.format("%.1f", seconds)).withStyle(ChatFormatting.GRAY));
        if (health > 0) {
            tooltip.add(line("stat_health", health, ChatFormatting.RED));
        }
        if (hunger > 0) {
            tooltip.add(line("stat_hunger", hunger, ChatFormatting.GOLD));
        }
        if (thirst > 0) {
            tooltip.add(line("stat_thirst", thirst, ChatFormatting.AQUA));
        }
        if (sanity > 0) {
            tooltip.add(line("stat_sanity", sanity, ChatFormatting.LIGHT_PURPLE));
        }
        if (sanityMaxBonus > 0) {
            tooltip.add(Component.translatable("tooltip.noellesroles.sixty_seconds.stat_sanity_max",
                    sanityMaxBonus, SANITY_MAX_CAP).withStyle(ChatFormatting.DARK_PURPLE));
        }
        if (healthMaxBonus > 0) {
            tooltip.add(Component.translatable("tooltip.noellesroles.sixty_seconds.stat_health_max",
                    healthMaxBonus, GENERIC_MAX_CAP).withStyle(ChatFormatting.RED));
        }
        if (hungerMaxBonus > 0) {
            tooltip.add(Component.translatable("tooltip.noellesroles.sixty_seconds.stat_hunger_max",
                    hungerMaxBonus, GENERIC_MAX_CAP).withStyle(ChatFormatting.GOLD));
        }
        if (thirstMaxBonus > 0) {
            tooltip.add(Component.translatable("tooltip.noellesroles.sixty_seconds.stat_thirst_max",
                    thirstMaxBonus, GENERIC_MAX_CAP).withStyle(ChatFormatting.AQUA));
        }
        if (pollutionMaxBonus > 0) {
            tooltip.add(Component.translatable("tooltip.noellesroles.sixty_seconds.stat_pollution_max",
                    pollutionMaxBonus, GENERIC_MAX_CAP).withStyle(ChatFormatting.GREEN));
        }
        if (pollutionReduce > 0) {
            tooltip.add(line("stat_pollution", pollutionReduce, ChatFormatting.GREEN));
        }
        if (cure) {
            tooltip.add(Component.translatable("tooltip.noellesroles.sixty_seconds.stat_cure")
                    .withStyle(ChatFormatting.GREEN));
        }
        if (effect != null) {
            tooltip.add(Component.translatable("tooltip.noellesroles.sixty_seconds.stat_effect")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    private static Component line(String key, int value, ChatFormatting color) {
        return Component.translatable("tooltip.noellesroles.sixty_seconds." + key, value).withStyle(color);
    }
}
