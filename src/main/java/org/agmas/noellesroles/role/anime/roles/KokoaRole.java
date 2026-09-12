package org.agmas.noellesroles.role.anime.roles;

import java.util.List;
import java.util.Optional;

import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.anime.AnimeRoles;
import org.agmas.noellesroles.utils.MCItemsUtils;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.AnimeRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.food.FoodProperties.PossibleEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

public class KokoaRole extends AnimeRole {

    public KokoaRole(ResourceLocation identifier, int color, RoleType roleType, MoodType moodType, int maxSprintTime,
            boolean canSeeTime) {
        super(identifier, color, roleType, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public void serverTick(ServerPlayer player) {
        if (player.level().getGameTime() % 20 == 0) {
            final var gamecca = SREGameWorldComponent.getInstance(player);
            if (!player.hasEffect(ModEffects.MOOD_DRAIN_REDUCTION)) {
                for (var t : player.serverLevel().getPlayers(p -> GameUtils.isPlayerAliveAndSurvival(p))) {
                    if (gamecca.isRole(t, AnimeRoles.KAFU_CHINO)) {
                        if (t.distanceToSqr(player) <= 3 * 3) {
                            player.addEffect(ModEffects.of(ModEffects.MOOD_DRAIN_REDUCTION, 40, 0, false, false, true));
                            player.addEffect(ModEffects.of(MobEffects.MOVEMENT_SPEED, 40, 0, false, false, true));
                            break;
                        }
                    }
                }
            }
        }
    }

    public static void registerSkills() {
        RoleSkill.register(AnimeRoles.HOTO_KOKOA,
                RoleSkill.skill(SRE.id("kokoa"), "skill.noellesroles.hoto_kokoa", (ctx) -> handleSkill(ctx))
                        .showOnHud(true).cooldownSeconds(90).announceToSelf().build());
    }

    private static boolean handleSkill(RoleSkillContext ctx) {
        ItemStack stack = Items.BREAD.getDefaultInstance();
        // 10s 缓慢II、10s 玩家隔离、10s 扩大语音、10s 夜视、10s
        // san值恢复、10s速度I、10s速度II、3s无敌、10s无限体力、10s体力回复效率提升II
        int time = 10 * 20;
        Holder<MobEffect> effect;
        int amplifier = 0;
        int random = ctx.player().getRandom().nextInt(0, 100);

        // 权重表（总和 100，坏效果 55 > 好效果 45）
        int acc = 0;
        if ((acc += 25) > random) { // 00~24 缓慢 II
            effect = MobEffects.MOVEMENT_SLOWDOWN/* 缓慢 */;
            amplifier = 1;
        } else if ((acc += 20) > random) { // 25~44 玩家隔离
            effect = ModEffects.PLAYER_ISOLATION /* 玩家隔离 */;
        } else if ((acc += 10) > random) { // 45~54 扩大语音
            effect = ModEffects.VOICE_RANGE_BOOST /* 扩大语音 */;
        } else if ((acc += 8) > random) { // 55~62 夜视
            effect = MobEffects.NIGHT_VISION/* 夜视 */;
        } else if ((acc += 10) > random) { // 63~72 san 值恢复
            effect = ModEffects.MOOD_REGENERATION/* san值恢复 */;
        } else if ((acc += 8) > random) { // 73~80 速度 I
            effect = MobEffects.MOVEMENT_SPEED /* 速度 */;
            amplifier = 0;
        } else if ((acc += 5) > random) { // 81~85 速度 II
            effect = MobEffects.MOVEMENT_SPEED/* 速度 */;
            amplifier = 1;
        } else if ((acc += 3) > random) { // 86~88 3s 无敌
            effect = ModEffects.INVINCIBLE/* 无敌 */;
            time = 3 * 20; // 无敌单独改时长
        } else if ((acc += 4) > random) { // 89~92 无限体力
            effect = ModEffects.INFINITE_STAMINA/* 无限体力 */;
        } else { // 93~99 体力回复效率提升 II
            effect = ModEffects.STAMINA_RECOVERY /* 体力回复效率提升 */;
            amplifier = 1;
        }
        stack.set(DataComponents.FOOD, new FoodProperties(5, 0.6f, true, 0.4f, Optional.empty(),
                List.of(new PossibleEffect(ModEffects.of(effect, time, amplifier, false, false, true), 1f))));
        stack.set(DataComponents.LORE,
                new ItemLore(List.of(Component
                        .translatable("%s %s: %ss", effect.value().getDisplayName(), amplifier + 1,
                                String.format("%.1f", time / 20f))
                        .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GREEN)))));
        return MCItemsUtils.insertOrDropItem(ctx.player(), stack);
    }
}
