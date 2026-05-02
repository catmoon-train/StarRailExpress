package io.wifi.events.day_night_fight;

import java.awt.*;
import java.util.*;
import java.util.List;

import io.wifi.events.day_night_fight.cca.DNFUnderworldComponent;
import io.wifi.starrailexpress.api.*;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.event.AllowPlayerOpenLockedDoor;
import io.wifi.starrailexpress.event.CantPlayerOpenDoor;
import io.wifi.starrailexpress.util.ShopEntry;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.events.day_night_fight.cca.DNFPlayerComponent;
import io.wifi.starrailexpress.SRE;
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
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;

import static io.wifi.events.day_night_fight.DNFBloodPurchaseItem.buy;

public class DNFRoles {
    public static final ResourceLocation KILLER_ID = SRE.id("dnf_killer");
    public static final ResourceLocation MANIAC_ID = SRE.id("dnf_maniac");
    public static final ResourceLocation SOLDIER_ID = SRE.id("dnf_soldier");
    public static final ResourceLocation CHEF_ID = SRE.id("dnf_chef");
    public static final ResourceLocation POISONER_ID = SRE.id("dnf_poisoner");
    public static final ResourceLocation PSYCHOLOGIST_ID = SRE.id("dnf_psychologist");
    public static final ResourceLocation LOCKSMITH_ID = SRE.id("dnf_locksmith");
    public static final ResourceLocation CIVILIAN_ID = SRE.id("dnf_civilian");
    public static final ResourceLocation FLYING_KNIFE_DEATH = SRE.id("dnf_flying_knife");

    private static final Map<UUID, Integer> DNF_CHARGING_TICKS = new HashMap<>();
    private static final Map<UUID, Long> DNF_TRAIL_EXPIRE = new HashMap<>();
    public static final ResourceLocation DNF_ABYSS_ID = SRE.id("dnf_abyss");

    static {
        CantPlayerOpenDoor.EVENT.register(player -> {
            if (!(player instanceof ServerPlayer serverPlayer))return false;
            if (player.level() instanceof ServerLevel sl) {
                if (SREGameWorldComponent.KEY.get( sl).gameMode== SREGameModes.DAY_NIGHT_FIGHT) {
                    if (!DNF.isDNFKiller(serverPlayer)) {
                        if (!DNFPlayerComponent.KEY.get(serverPlayer).hasSafeRoomSan(serverPlayer)){
                            return true;
                        }

                    }
                }

            }
            return  false;
        });
    }
    public static SRERole DNF_ABYSS = TMMRoles.registerRole(new NormalRole(
            DNF_ABYSS_ID,
            new Color(120, 0, 0).getRGB(),
            false,
            true,
            SRERole.MoodType.FAKE,
            Integer.MAX_VALUE,
            true) {
        @Override
        public InteractionResult leftClickEntity(Player player, Entity target) {
            if (!(player instanceof ServerPlayer serverPlayer) || !(target instanceof Player victim)) {
                return InteractionResult.PASS;
            }
            if (!serverPlayer.getMainHandItem().is(DNFItems.ABYSS_TENTACLE)) {
                return InteractionResult.PASS;
            }

            serverPlayer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 6, false, false, false));
            if (serverPlayer.isAlive() && victim.isAlive() && serverPlayer.distanceTo(victim) <= 4.0) {
                // 修改:让玩家进入里世界而不是直接死亡
                if (victim instanceof ServerPlayer serverVictim) {
                    DNFUnderworldComponent.KEY.get(serverVictim).reduceTime();
                }
            }
            if (serverPlayer.isAlive()) {
                serverPlayer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 6, false, false, false));
            }
            serverPlayer.serverLevel().playSound(null, serverPlayer.blockPosition().above(1), SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, SoundSource.PLAYERS, 1.0f, 1.0f);

            // 添加少量粒子效果
            if (serverPlayer.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.CRIMSON_SPORE,
                        serverPlayer.getX(), serverPlayer.getY() + 1, serverPlayer.getZ(),
                        5, 0.3, 0.5, 0.3, 0.05);
            }

            return InteractionResult.CONSUME;
        }
        @Override
        public InteractionResultHolder<ItemStack> onItemUse(Player player, Level world, InteractionHand hand) {
            player.startUsingItem(hand);
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }

        @Override
        public List<ItemStack> getDefaultItems() {
            return List.of(DNFItems.ABYSS_TENTACLE.getDefaultInstance());
        }

        @Override
        public void serverTick(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, false, false, false));
            if (player.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.CRIMSON_SPORE, player.getX(), player.getY() + 1, player.getZ(), 5, 0.4, 0.8, 0.4, 0.01);
                sl.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1, player.getZ(), 5, 0.4, 0.8, 0.4, 0.01);
            }
            UUID id = player.getUUID();
            if (player.isUsingItem()) {
                DNF_CHARGING_TICKS.put(id, DNF_CHARGING_TICKS.getOrDefault(id, 0) + 1);
                player.setDeltaMovement(0, player.getDeltaMovement().y, 0);
                if (DNF_CHARGING_TICKS.get(id) >= 10) {
                    DNF_CHARGING_TICKS.put(id, 0);
                    player.stopUsingItem();
                    var hitBox = player.getBoundingBox().inflate(2.5).move(player.getLookAngle().scale(2.0));
                    for (Player p2 : player.level().getEntitiesOfClass(Player.class, hitBox, p -> p != player && GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p))) {
                        // 修改:让玩家进入里世界而不是直接死亡
                        if (p2 instanceof ServerPlayer serverP2) {
                            DNF.sendPlayerToUnderworld(serverP2);
                        }
                    }
                }
            } else {
                DNF_CHARGING_TICKS.remove(id);
            }
            DNF_TRAIL_EXPIRE.put(id, player.level().getGameTime() + 60);
        }

        @Override
        public List<ShopEntry> getShopEntries() {
            ArrayList<ShopEntry> shopEntries = new ArrayList<>();
            shopEntries.add(new ShopEntry(Items.BARRIER.getDefaultInstance(),0, dev.doctor4t.wathe.util.ShopEntry.Type.TOOL){
                @Override
                public boolean canBuy(@NotNull Player player) {
                    return false;
                }
            });
            return shopEntries;
        }
    }).setCanSeeCoin(false).setCanUseInstinct(false).setCanSeeTime(false).setCanGetBodyItems(true);
    public static final SRERole MANIAC = TMMRoles.registerRole(new DNFNormalRole(MANIAC_ID, 0x3A0010, false, true,
            SRERole.MoodType.FAKE, Integer.MAX_VALUE, false) {
        @Override
        public void onInit(MinecraftServer server, ServerPlayer serverPlayer) {
            DNFPlayerComponent.KEY.get(serverPlayer).init();
            DNF.applyPhaseState(serverPlayer, DNF.isNight(serverPlayer));
        }

        @Override
        public void serverTick(ServerPlayer player) {
            DNF.applyManiacTick(player);
        }


        @Override
        public InteractionResult leftClickEntity(Player player, Entity target) {
            if (!(player instanceof ServerPlayer serverPlayer) || !(target instanceof ServerPlayer victim)) {
                return InteractionResult.PASS;
            }
            if (!DNF.isNight(serverPlayer) || DNFPlayerComponent.KEY.get(serverPlayer).isManiacStunned()) {
                return InteractionResult.CONSUME;
            }
            if (!GameUtils.isPlayerAliveAndSurvival(victim) || DNF.isTargetProtectedFromManiac(victim)) {
                serverPlayer.displayClientMessage(Component.translatable("message.dnf.maniac.safe_room")
                        .withStyle(ChatFormatting.DARK_PURPLE), true);
                return InteractionResult.CONSUME;
            }
            if (serverPlayer.distanceTo(victim) <= 4.0) {
                GameUtils.killPlayer(victim, true, serverPlayer, SRE.id("dnf_maniac"));
                serverPlayer.level().playSound(null, victim.blockPosition(), SoundEvents.WARDEN_ATTACK_IMPACT,
                        SoundSource.PLAYERS, 1.0f, 0.6f);
            }
            return InteractionResult.CONSUME;
        }

        @Override
        public boolean allowDeath(Player victim, Player killer, ResourceLocation deathReason, boolean spawnBody) {
            if (victim instanceof ServerPlayer serverVictim
                    && (deathReason.equals(GameConstants.DeathReasons.REVOLVER)
                            || deathReason.equals(GameConstants.DeathReasons.DERRINGER)
                            || deathReason.equals(GameConstants.DeathReasons.SNIPER_RIFLE))) {
                DNFPlayerComponent.KEY.get(serverVictim).stunManiac(serverVictim);
                return false;
            }
            return true;
        }

        @Override
        public void onKill(Player victim, boolean spawnBody, Player killer, ResourceLocation deathReason) {
            if (killer instanceof ServerPlayer serverKiller) {
                DNFPlayerComponent.KEY.get(serverKiller).recordKill(serverKiller);
            }
        }
    }.setMax(2).setCanSeeCoin(false).setCanUseInstinct(false).setCanGetBodyItems(false))
            .setCanBeRandomedByOtherRoles(false);

    public static final SRERole KILLER = TMMRoles.registerRole(
            new CustomWinnerRole(KILLER_ID, 0x7A1414, false, true, SRERole.MoodType.FAKE, -1, true) {
                @Override
                public void onInit(MinecraftServer server, ServerPlayer serverPlayer) {
                    DNFPlayerComponent.KEY.get(serverPlayer).init();
                    if (DNF.isNight(serverPlayer)) {
                        DNF.equipNightTools(serverPlayer);
                    }
                }

                @Override
                public List<ShopEntry> getShopEntries() {
                    ArrayList<ShopEntry> shopEntries = new ArrayList<>();
                    shopEntries.add(new ShopEntry(new ItemStack(DNFItems.BLOOD_BUY_FLYING_KNIFE), 10, dev.doctor4t.wathe.util.ShopEntry.Type.TOOL){
                        @Override
                        public boolean onBuy(@NotNull Player player) {
                            return false;
                        }

                        @Override
                        public boolean canBuy(@NotNull Player player) {
                            if (!DNF.isDNFKiller(player)) {
                                player.displayClientMessage(Component.translatable("message.dnf.item.killer_only")
                                        .withStyle(ChatFormatting.RED), true);
                                return false;
                            }
                            DNFBloodPurchaseItem bloodBuyLockpick = (DNFBloodPurchaseItem) DNFItems.BLOOD_BUY_LOCKPICK;
                            return buy(player, bloodBuyLockpick.price, bloodBuyLockpick.purchase.get(), bloodBuyLockpick.nameKey);
                        }
                    });
                    shopEntries.add(new ShopEntry(new ItemStack(DNFItems.BLOOD_BUY_LOCKPICK), 10, dev.doctor4t.wathe.util.ShopEntry.Type.TOOL){
                        @Override
                        public boolean onBuy(@NotNull Player player) {
                            return false;
                        }
                        @Override
                        public boolean canBuy(@NotNull Player player) {
                            if (!DNF.isDNFKiller(player)) {
                                player.displayClientMessage(Component.translatable("message.dnf.item.killer_only")
                                        .withStyle(ChatFormatting.RED), true);
                                return false;
                            }
                            DNFBloodPurchaseItem bloodBuyFlyingKnife = (DNFBloodPurchaseItem) DNFItems.BLOOD_BUY_FLYING_KNIFE;
                            return buy(player, bloodBuyFlyingKnife.price, bloodBuyFlyingKnife.purchase.get(), bloodBuyFlyingKnife.nameKey);
                        }
                    });
                    return shopEntries;
                }

                @Override
                public List<ItemStack> getDefaultItems() {
                    ArrayList<ItemStack> items = new ArrayList<>();

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
                        DNFPlayerComponent.KEY.get(serverPlayer).requestAid(serverPlayer, serverPlayer.isShiftKeyDown());
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
                    return player instanceof ServerPlayer serverPlayer
                            && DNFPlayerComponent.KEY.get(serverPlayer).canUseKnife(serverPlayer);
                }

                @Override
                public boolean onUseKnifeHit(Player player, Player target) {
                    if (!DNF.isNight(player)) {
                        return false;
                    }
                    if (player instanceof ServerPlayer serverPlayer) {
                        DNFPlayerComponent.KEY.get(serverPlayer).consumeKnifeUse(serverPlayer);
                    }
                    return true;
                }

                @Override
                public void onKill(Player victim, boolean spawnBody, Player killer, ResourceLocation deathReason) {
                    if (killer instanceof ServerPlayer serverKiller) {
                        DNFPlayerComponent.KEY.get(serverKiller).recordKill(serverKiller);
                    }
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
                    if (SREPlayerPsychoComponent.KEY.get(serverPlayer).getPsychoTicks() <= 0) {
                        return InteractionResult.PASS;
                    }
                    serverPlayer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 6, false, false, false));
                    if (serverPlayer.isAlive() && victim.isAlive() && serverPlayer.distanceTo(victim) <= 4.0) {
                        GameUtils.killPlayer(victim, true, serverPlayer, Noellesroles.id("dnf_tentacle"));
                    }
                    if (serverPlayer.isAlive()) {
                        serverPlayer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 6, false, false, false));
                    }
                    serverPlayer.serverLevel().playSound(null, serverPlayer.blockPosition().above(1), SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, SoundSource.PLAYERS, 1.0f, 1.0f);
                    
                    // 添加少量粒子效果
                    if (serverPlayer.level() instanceof ServerLevel sl) {
                        sl.sendParticles(ParticleTypes.CRIMSON_SPORE,
                                serverPlayer.getX(), serverPlayer.getY() + 1, serverPlayer.getZ(), 
                                5, 0.3, 0.5, 0.3, 0.05);
                    }
                    
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
                    var roundEnd = SREGameRoundEndComponent.KEY.get(player.serverLevel());
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
            }.setComponentKey(DNFPlayerComponent.KEY).setCanSeeCoin(false).setCanUseInstinct(false).setMax(4)
                    .setCanSeeTeammateKiller(false)).setCanBeRandomedByOtherRoles(false).setCanGetBodyItems(false);

    public static final SRERole SOLDIER = TMMRoles.registerRole(new DNFNormalRole(SOLDIER_ID, 0x496D89, true, false,
            SRERole.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false) {
        @Override
        public List<ItemStack> getDefaultItems() {
            return List.of(ModItems.BATON.getDefaultInstance(), TMMItems.REVOLVER.getDefaultInstance());
        }

        @Override
        public boolean onUseGun(Player player) {
            return !(player instanceof ServerPlayer serverPlayer)
                    || DNFPlayerComponent.KEY.get(serverPlayer).consumeSoldierShot(serverPlayer);
        }

        @Override
        public boolean onGunHit(Player killer, Player victim) {
            if (killer instanceof ServerPlayer serverKiller && victim instanceof ServerPlayer serverVictim
                    && !DNF.isNight(serverKiller) && !SREGameWorldComponent.KEY.get(serverKiller.level())
                            .isRole(serverVictim, DNFRoles.MANIAC)) {
                RoleUtils.changeRole(serverKiller, DNFRoles.CIVILIAN);
                serverKiller.displayClientMessage(Component.translatable("message.dnf.soldier.demoted")
                        .withStyle(ChatFormatting.RED), false);
            }
            return true;
        }

        @Override
        public void onKill(Player victim, boolean spawnBody, Player killer, ResourceLocation deathReason) {
            if (killer instanceof ServerPlayer serverKiller) {
                DNFPlayerComponent.KEY.get(serverKiller).recordKill(serverKiller);
            }
        }
    }.setVigilanteTeam(true).setMax(2))
            .setCanBeRandomedByOtherRoles(false).setCanGetBodyItems( true);
    public static final SRERole CHEF = TMMRoles.registerRole(new DNFNormalRole(CHEF_ID, 0x8C6A2D, true, false,
            SRERole.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false) {
        @Override
        public void onInit(MinecraftServer server, ServerPlayer serverPlayer) {
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
        public List<ItemStack> getDefaultItems() {
            ArrayList<ItemStack> itemStacks = new ArrayList<>();
            itemStacks.add(DNFItems.CHEF_HAT.getDefaultInstance());
            return itemStacks;
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
    }.setMax(1)).setCanBeRandomedByOtherRoles(false).setCanGetBodyItems(true);
    public static final SRERole POISONER = TMMRoles.registerRole(new DNFNormalRole(POISONER_ID, 0x355F2D, false, true,
            SRERole.MoodType.FAKE, TMMRoles.CIVILIAN.getMaxSprintTime(), false) {
        @Override
        public void onInit(MinecraftServer server, ServerPlayer serverPlayer) {
            DNFPlayerComponent.KEY.get(serverPlayer).init();
        }
                @Override
                public List<ShopEntry> getShopEntries() {
                    ArrayList<ShopEntry> shopEntries = new ArrayList<>();
                    shopEntries.add(new ShopEntry(Items.BARRIER.getDefaultInstance(),0, dev.doctor4t.wathe.util.ShopEntry.Type.TOOL){
                        @Override
                        public boolean canBuy(@NotNull Player player) {
                            return false;
                        }
                    });
                    return shopEntries;
                }
        @Override
        public boolean onAbilityUse(ServerPlayer player) {
            return DNF.tryPoisonerAbility(player);
        }

        @Override
        public void onKill(Player victim, boolean spawnBody, Player killer, ResourceLocation deathReason) {
            if (killer instanceof ServerPlayer serverKiller && deathReason.equals(GameConstants.DeathReasons.POISON)) {
                DNFPlayerComponent component = DNFPlayerComponent.KEY.get(serverKiller);
                component.recordKill(serverKiller);
                component.recordPoisonKill();
                component.giveToxicHeart(serverKiller);
            }
        }
    }.setMax(1).setCanSeeCoin(false).setCanUseInstinct(false).setCanSeeTeammateKiller(false)).setCanBeRandomedByOtherRoles(false)
            .setCanGetBodyItems(false);

    public static final SRERole PSYCHOLOGIST = TMMRoles.registerRole(new DNFNormalRole(PSYCHOLOGIST_ID, 0x8E6BC6, true,
            false, SRERole.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false) {
        @Override
        public boolean onAbilityUse(ServerPlayer player) {
            if (!DNFPlayerComponent.KEY.get(player).spendSan(player, 0.8f, "message.dnf.psychologist.not_enough_san")) {
                return false;
            }
            ServerPlayer target = DNF.findLookedAtPlayer(player, 8.0);
            if (target == null) {
                player.displayClientMessage(Component.translatable("message.dnf.psychologist.no_target")
                        .withStyle(ChatFormatting.GRAY), true);
                return false;
            }
            boolean killed = DNFPlayerComponent.KEY.get(target).getKilledPlayers() > 0;
            player.displayClientMessage(Component.translatable(killed
                    ? "message.dnf.psychologist.result_killer"
                    : "message.dnf.psychologist.result_clean", target.getDisplayName())
                    .withStyle(killed ? ChatFormatting.RED : ChatFormatting.GREEN), false);
            return true;
        }
    }.setMax(2))
            .setCanBeRandomedByOtherRoles(false).setCanGetBodyItems(true);
    public static final SRERole LOCKSMITH = TMMRoles.registerRole(new DNFNormalRole(LOCKSMITH_ID, 0xD1A448, true, false,
            SRERole.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false) {
        @Override
        public boolean onAbilityUse(ServerPlayer player) {
            if (!DNFPlayerComponent.KEY.get(player).spendSan(player, 0.25f, "message.dnf.locksmith.not_enough_san")) {
                return false;
            }
            DNFItems.giveOrDrop(player, DNFItems.REPAIR_TOOL.getDefaultInstance());
            player.displayClientMessage(Component.translatable("message.dnf.locksmith.tool_created")
                    .withStyle(ChatFormatting.YELLOW), true);
            return true;
        }

        @Override
        public InteractionResult onUseBlock(Player player, Level world,
                InteractionHand hand, BlockHitResult hitResult) {
            return DNFItems.tryRepairLockpickedDoor(player, world, hitResult.getBlockPos());
        }
    }.setMax(4)).setCanBeRandomedByOtherRoles(false).setCanGetBodyItems(true);
    public static final SRERole CIVILIAN = TMMRoles.registerRole(new DNFNormalRole(CIVILIAN_ID, 0x719E5B, true, false,
            SRERole.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false)).setCanBeRandomedByOtherRoles(false).setCanGetBodyItems(true);

}
