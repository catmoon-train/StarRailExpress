package io.wifi.events.day_night_fight;

import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.CustomWinnerRole;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class DNFRoles {
    public static final ResourceLocation KILLER_ID = SRE.id("dnf_killer");
    public static final ResourceLocation SOLDIER_ID = SRE.id("dnf_soldier");
    public static final ResourceLocation CHEF_ID = SRE.id("dnf_chef");
    public static final ResourceLocation PSYCHOLOGIST_ID = SRE.id("dnf_psychologist");
    public static final ResourceLocation LOCKSMITH_ID = SRE.id("dnf_locksmith");
    public static final ResourceLocation CIVILIAN_ID = SRE.id("dnf_civilian");
    public static final ResourceLocation FLYING_KNIFE_DEATH = SRE.id("dnf_flying_knife");
    public static final SRERole KILLER = TMMRoles.registerRole(
            new CustomWinnerRole(KILLER_ID, 0x7A1414, false, true, SRERole.MoodType.FAKE, -1, true) {
                @Override
                public void onInit(net.minecraft.server.MinecraftServer server, ServerPlayer serverPlayer) {
                    DNFPlayerComponent.KEY.get(serverPlayer).init();
                    if (DNF.isNight(serverPlayer)) {
                        DNF.equipNightTools(serverPlayer);
                    }
                }

                @Override
                public List<ItemStack> getDefaultItems() {
                    ArrayList<ItemStack> items = new ArrayList<>();
                    items.add(DNFItems.BLOOD_BUY_FLYING_KNIFE.getDefaultInstance());
                    items.add(DNFItems.BLOOD_BUY_LOCKPICK.getDefaultInstance());
                    items.add(DNFItems.ABYSS_VIAL.getDefaultInstance());
                    return items;
                }

                @Override
                public void serverTick(ServerPlayer player) {
                    DNFPlayerComponent component = DNFPlayerComponent.KEY.get(player);
                    if (player.level().getGameTime() % 20 == 0) {
                        DNF.updateNightTools(player);
                        component.checkHunger(player);
                    }
                }

                @Override
                public InteractionResult rightClickEntity(Player player, Entity target) {
                    if (!(player instanceof ServerPlayer serverPlayer) || !(target instanceof PlayerBodyEntity body)) {
                        return InteractionResult.PASS;
                    }
                    if (!GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
                        return InteractionResult.PASS;
                    }
                    return DNF.eatBody(serverPlayer, body);
                }

                @Override
                public boolean onAbilityUse(ServerPlayer player) {
                    if (player instanceof ServerPlayer serverPlayer) {
                        DNF.exchangeBlood(serverPlayer, serverPlayer.isShiftKeyDown());
                    }
                    return true;
                }

                @Override
                public boolean onUseKnife(Player player) {
                    if (!DNF.isNight(player)) {
                        player.displayClientMessage(Component.translatable("message.dnf.killer.night_only")
                                .withStyle(ChatFormatting.DARK_RED), true);
                        return false;
                    }
                    return true;
                }

                @Override
                public boolean onUseKnifeHit(Player player, Player target) {
                    if (!DNF.isNight(player)) {
                        return false;
                    }
                    return true;
                }



                @Override
                public Item getPsychoItem() {
                    return DNFItems.ABYSS_TENTACLE;
                }

                @Override
                public InteractionResult leftClickEntity(Player player, Entity target) {
                    if (!(player instanceof ServerPlayer serverPlayer) || !(target instanceof Player victim)) {
                        return InteractionResult.PASS;
                    }
                    if (!serverPlayer.getMainHandItem().is(DNFItems.ABYSS_TENTACLE)) {
                        return InteractionResult.PASS;
                    }
                    if (io.wifi.starrailexpress.cca.SREPlayerPsychoComponent.KEY.get(serverPlayer).getPsychoTicks() <= 0) {
                        return InteractionResult.PASS;
                    }
                    serverPlayer.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 25, 6, false, false, false));
                    io.wifi.starrailexpress.util.Scheduler.schedule(() -> {
                        if (serverPlayer.isAlive() && victim.isAlive()) {
                            victim.hurt(serverPlayer.damageSources().playerAttack(serverPlayer), 1000f);
                        }
                    }, 5);
                    io.wifi.starrailexpress.util.Scheduler.schedule(() -> {
                        if (serverPlayer.isAlive()) {
                            serverPlayer.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 25, 6, false, false, false));
                        }
                    }, 0);
                    io.wifi.starrailexpress.util.Scheduler.schedule(() -> {
                        if (serverPlayer.isAlive()) {
                            serverPlayer.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 25, 6, false, false, false));
                        }
                    }, 25);
                    return InteractionResult.CONSUME;
                }

                @Override
                public GameUtils.WinStatus checkWin(ServerPlayer player, GameUtils.WinStatus winStatus) {
                    return DNFPlayerComponent.KEY.get(player).hasPersonalEnding()
                            ? GameUtils.WinStatus.CUSTOM
                            : GameUtils.WinStatus.NOT_MODIFY;
                }

                /**
                 * 获取一局里最大可出现此职业数量。-1表示不变。
                 * 
                 * @param gameWorldComponent
                 * @param serverLevel
                 * @param players
                 * @return
                 */
                @Override
                public int getRoundMaxCount(ServerLevel serverLevel, SREGameWorldComponent gameWorldComponent,
                        List<ServerPlayer> players) {
                    if (!gameWorldComponent.gameMode.identifier.equals(SREGameModes.DAY_NIGHT_FIGHT.identifier))
                        return 0;
                    return super.getRoundMaxCount(serverLevel, gameWorldComponent, players);
                }

                @Override
                public void win(ServerPlayer player) {
                    var roundEnd = io.wifi.starrailexpress.cca.SREGameRoundEndComponent.KEY.get(player.serverLevel());
                    roundEnd.CustomWinnerTitle = Component.translatable("game.win.star.dnf_killer");
                    roundEnd.CustomWinnerSubtitle = Component.translatable("message.dnf.killer.ending");
                    RoleUtils.customWinnerWin(player.serverLevel(), this.identifier().getPath(), this.color());
                }

                @Override
                public boolean didPlayerWin(ServerPlayer player, boolean original, GameUtils.WinStatus winStatus) {
                    if (winStatus == GameUtils.WinStatus.CUSTOM || winStatus == GameUtils.WinStatus.CUSTOM_COMPONENT) {
                        return DNFPlayerComponent.KEY.get(player).hasPersonalEnding();
                    }
                    return original;
                }
            }.setComponentKey(DNFPlayerComponent.KEY).setCanSeeCoin(false).setCanUseInstinct(true).setMax(4)
                    .setCanSeeTeammateKiller(false)).setCanBeRandomedByOtherRoles(false);

    public static final SRERole SOLDIER = TMMRoles.registerRole(new DNFNormalRole(SOLDIER_ID, 0x496D89, true, false,
            SRERole.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false).setVigilanteTeam(true).setMax(2))
            .setCanBeRandomedByOtherRoles(false);
    public static final SRERole CHEF = TMMRoles.registerRole(new DNFNormalRole(CHEF_ID, 0x8C6A2D, true, false,
            SRERole.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false) {
        @Override
        public void onInit(net.minecraft.server.MinecraftServer server, ServerPlayer serverPlayer) {
            DNFPlayerComponent.KEY.get(serverPlayer).init();
        }

        @Override
        public boolean onAbilityUse(ServerPlayer player) {
            if (player instanceof ServerPlayer serverPlayer) {
                DNFItems.tryChefWork(serverPlayer, serverPlayer.isShiftKeyDown());
            }
            return true;
        }

        @Override
        public InteractionResult rightClickEntity(Player player, Entity target) {
            if (!(player instanceof ServerPlayer serverPlayer) || !(target instanceof PlayerBodyEntity body)) {
                return InteractionResult.PASS;
            }
            if (!GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
                return InteractionResult.PASS;
            }
            return DNFItems.cookBodyAsChef(serverPlayer, body);
        }
    }.setMax(1)).setCanBeRandomedByOtherRoles(false);
    public static final SRERole PSYCHOLOGIST = TMMRoles.registerRole(new DNFNormalRole(PSYCHOLOGIST_ID, 0x8E6BC6, true,
            false, SRERole.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false).setMax(2))
            .setCanBeRandomedByOtherRoles(false);
    public static final SRERole LOCKSMITH = TMMRoles.registerRole(new DNFNormalRole(LOCKSMITH_ID, 0xD1A448, true, false,
            SRERole.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false) {
        @Override
        public InteractionResult onUseBlock(Player player, net.minecraft.world.level.Level world,
                net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hitResult) {
            return DNFItems.tryRepairLockpickedDoor(player, world, hitResult.getBlockPos());
        }
    }.setMax(4)).setCanBeRandomedByOtherRoles(false);
    public static final SRERole CIVILIAN = TMMRoles.registerRole(new DNFNormalRole(CIVILIAN_ID, 0x719E5B, true, false,
            SRERole.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false)).setCanBeRandomedByOtherRoles(false);

}
