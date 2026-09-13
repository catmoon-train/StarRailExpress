package org.agmas.noellesroles.role.anime.roles;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.modifier.NRModifiers;
import org.agmas.noellesroles.init.FunnyItems;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import org.agmas.noellesroles.role.anime.AnimeRoles;
import org.agmas.noellesroles.role.anime.chino.ChinoHeadRideManager;
import org.agmas.noellesroles.role_data.innocence.ChinoRoleData;
import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.api.AnimeRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.food.FoodProperties.PossibleEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;

public class ChinoRole extends AnimeRole {

    public ChinoRole(ResourceLocation identifier, int color, RoleType roleType, MoodType moodType, int maxSprintTime,
            boolean canSeeTime) {
        super(identifier, color, roleType, moodType, maxSprintTime, canSeeTime);
        this.setRoleData(ChinoRoleData::new);
    }

    @Override
    public void serverTick(ServerPlayer player) {
        if (player.level().getGameTime() % 20 == 0) {
            final var gamecca = SREGameWorldComponent.getInstance(player);
            if (!player.hasEffect(ModEffects.MOOD_DRAIN_REDUCTION)
                    || (player.getEffect(ModEffects.MOOD_DRAIN_REDUCTION) instanceof MobEffectInstance mei
                            && mei.getDuration() <= 20)) {
                for (var t : player.serverLevel().getPlayers(p -> GameUtils.isPlayerAliveAndSurvival(p))) {
                    if (gamecca.isRole(t, AnimeRoles.HOTO_KOKOA)) {
                        if (t.distanceToSqr(player) <= 3 * 3) {
                            player.addEffect(ModEffects.of(ModEffects.MOOD_DRAIN_REDUCTION, 40, 0, false, false, true));
                            break;
                        }
                    }
                }
            }
        }
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        return List.of(new ShopEntry(FunnyItems.CHINO_COFFEE.getDefaultInstance(), 150, ShopEntry.Type.TOOL) {
            @Override
            public boolean onBuy(Player player) {
                var st = stack().copy();

                int time = 1 * 20;
                Holder<MobEffect> effect = MobEffects.GLOWING;
                int amplifier = 0;
                String name = "unknown";
                int id = 0;
                int random = player.getRandom().nextInt(0, 3);
                // \n- 蓝山咖啡：喝下后获得15秒san值恢复。\n- 热可可：喝下后san值降低减缓30s。\n- 卡布奇诺：喝下后获得10s
                // “火眼金睛”药水效果，此时所有玩家伪装对你无效。
                if (random == 0) {
                    name = "blue_mountain";
                    effect = ModEffects.MOOD_REGENERATION;
                    time = 15 * 20;
                    id = 1;
                } else if (random == 1) {
                    name = "hot_koko";
                    effect = ModEffects.MOOD_DRAIN_REDUCTION;
                    time = 30 * 20;
                    id = 2;
                } else if (random == 2) {
                    name = "kabuchino";
                    effect = ModEffects.TRUE_SKIN_OBSERVER;
                    time = 10 * 20;
                    id = 3;
                }
                st.set(DataComponents.ITEM_NAME, Component.translatable("item.noellesroles.chino_coffee." + name));
                st.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(id));
                st.set(DataComponents.FOOD, new FoodProperties(5, 0.6f, true, 0.4f, Optional.empty(),
                        List.of(new PossibleEffect(ModEffects.of(effect, time, amplifier, false, false, true),
                                1f))));
                return RoleUtils.insertOrDropItem(player, st);
            }
        });
    }

    @Override
    public boolean allowDeath(Player victim, @Nullable Player killer, ResourceLocation deathReason, boolean spawnBody) {
        if (killer != null && RoleUtils.isPlayerTheModifier(killer, NRModifiers.RABBIT_SHAPE)) {
            return false;
        }
        return true;
    }

    @Override
    public InteractionResult onDropItem(Player player, ItemStack item) {
        if (item.is(FunnyItems.CHINO_COFFEE) && !item.getOrDefault(SREDataComponentTypes.TRAY_ITEM, false)) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult rightClickEntity(Player player, Entity victim) {
        // 可抱的目标见 ChinoHeadRideManager#isRideableTarget：兔兔 / 矮小 / 侏儒
        if (victim instanceof Player rabbit && ChinoHeadRideManager.isRideableTarget(rabbit)) {
            // 已经在头顶的兔兔由技能放下，右键不再处理
            if (ChinoHeadRideManager.isCarriedBy(rabbit, player)) {
                return InteractionResult.PASS;
            }
            if (player.level().isClientSide) {
                return InteractionResult.SUCCESS;
            }
            if (player instanceof ServerPlayer chino && rabbit instanceof ServerPlayer rabbitPlayer) {
                ChinoHeadRideManager.tryMount(chino, rabbitPlayer);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /**
     * 技能：把头顶的兔兔放到身前。
     * 没抱人时返回 false，按统一技能入口的约定不消耗冷却、不发通告。
     */
    public static void registerSkills() {
        RoleSkill.register(AnimeRoles.KAFU_CHINO,
                RoleSkill.skill(Noellesroles.id("chino_head_ride_release"),
                        "skill.noellesroles.kafu_chino.release", ChinoRole::handleRelease)
                        .noAnnouncement()
                        .build());
    }

    private static boolean handleRelease(RoleSkillContext ctx) {
        final ServerPlayer chino = ctx.player();
        if (!ChinoHeadRideManager.forceRelease(chino)) {
            chino.displayClientMessage(
                    Component.translatable("message.noellesroles.chino_head_ride.no_rider")
                            .withStyle(ChatFormatting.YELLOW),
                    true);
            return false;
        }
        return true;
    }

    @Override
    public void onAssignedModifiers(ServerPlayer player, Set<SREModifier> modifiers) {
        modifiers.remove(SEModifiers.TALL);
        modifiers.remove(SEModifiers.TINY);
        modifiers.add(TraitorAndModifiers.DWARF);
        modifiers.add(SEModifiers.FEATHER);
    };

    /** 每局开始时（{@code SRERole.resetStatic()}）清空乘骑状态与冷却。 */
    @Override
    public void resetVariables() {
        ChinoHeadRideManager.clearRides(null);
    }

}
