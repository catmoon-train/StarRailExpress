package org.agmas.noellesroles.init;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.*;
import io.wifi.starrailexpress.content.item.KnifeItem;
import io.wifi.starrailexpress.content.item.component.SREWrittenBookContent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.KillerKnifeShopEntry;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.util.SREItemUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.*;
import org.agmas.noellesroles.commands.BroadcastCommand;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.content.item.ToxinShopEntry;
import org.agmas.noellesroles.game.roles.innocence.singer.SingerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.executioner.ExecutionerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.executioner.ShootingFrenzyPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.ma_chen_xu.MaChenXuPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.stalker.StalkerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.watcher.WatcherPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.water_ghost.WaterGhostPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.wraith_assassin.WraithAssassinPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.candlebearer.CandleBearerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.mercenary.MercenaryPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.phantom_musician.PhantomMusicianPlayerComponent;
import org.agmas.noellesroles.role.BounsRoles;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import net.exmo.sre.repair.role.RepairRoles;
import org.agmas.noellesroles.role.touhou.RedHouseRoles;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import pro.fazeclan.river.stupid_express.constants.SERoles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class RoleShopHandler {

    private static final String OLDMAN_EASTER_EGG_TAG = "sre_oldman_easter_egg";
    private static final String OLDMAN_EASTER_EGG_USED_TAG = "sre_oldman_easter_egg_used";
    public static final String OLDMAN_EASTER_EGG_PIG_NO_STEP_TAG = "sre_oldman_easter_egg_pig_no_step";
    private static boolean oldmanEasterEggTriggeredInRound = false;

    public static boolean haveRegistered = false;

    private static int banditBlackoutPrice() {
        return (int) (SREConfig.instance().blackoutPrice * 1.275);
    }

    private static List<ShopEntry> createPoisonerShopEntries() {
        var entries = new ArrayList<ShopEntry>();
        entries.add(new ToxinShopEntry(80));
        entries.add(new ShopEntry(TMMItems.POISON_VIAL.getDefaultInstance(), 50, ShopEntry.Type.POISON));
        entries.add(new ShopEntry(ModItems.TOILET_POISON.getDefaultInstance(), 40, ShopEntry.Type.POISON));
        entries.add(new ShopEntry(TMMItems.SCORPION.getDefaultInstance(), 15, ShopEntry.Type.POISON));
        entries.add(new ShopEntry(ModItems.CATALYST.getDefaultInstance(), 100, ShopEntry.Type.TOOL));
        entries.add(new ShopEntry(ModItems.createPillStack(true), 20, ShopEntry.Type.TOOL));
        entries.add(new ShopEntry(ModItems.CHLORINE_BOMB.getDefaultInstance(), 275, ShopEntry.Type.POISON));
        entries.add(new ShopEntry(ModItems.POISON_GAS_TANK.getDefaultInstance(), 215, ShopEntry.Type.POISON));
        entries.add(new ShopEntry(TMMItems.FIRECRACKER.getDefaultInstance(), 10, ShopEntry.Type.TOOL));
        entries.add(new ShopEntry(new ItemStack(TMMItems.NOTE, 4), 10, ShopEntry.Type.TOOL));
        entries.add(new ShopEntry(TMMItems.CROWBAR.getDefaultInstance(), 35, ShopEntry.Type.TOOL));
        entries.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 100, ShopEntry.Type.TOOL));
        entries.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), 100, ShopEntry.Type.TOOL) {
            public boolean onBuy(@NotNull Player player) {
                return SREPlayerShopComponent.useBlackout(player);
            }
        });
        entries.add(new ShopEntry(TMMItems.MONITOR_BROKEN.getDefaultInstance(), 100, ShopEntry.Type.TOOL) {
            public boolean onBuy(@NotNull Player player) {
                return SREPlayerShopComponent.useMonitorBroken(player, SREConfig.instance().monitorBrokenDuration * 20);
            }
        });
        return entries;
    }

    public static void resetOldmanEasterEggState() {
        oldmanEasterEggTriggeredInRound = false;
    }

    public static @NotNull ItemStack createOldmanEasterEggRod() {
        ItemStack rod = Items.CARROT_ON_A_STICK.getDefaultInstance();
        rod.set(DataComponents.UNBREAKABLE, new Unbreakable(true));

        CompoundTag tag = new CompoundTag();
        tag.putBoolean(OLDMAN_EASTER_EGG_TAG, true);
        tag.putBoolean(OLDMAN_EASTER_EGG_USED_TAG, false);
        rod.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return rod;
    }

    private static void registerRepairModeShops() {
        var hunterCommon = new ArrayList<ShopEntry>();
        hunterCommon.add(new ShopEntry(ModItems.HUNTER_WEAPON.getDefaultInstance(), 45, ShopEntry.Type.WEAPON));
        hunterCommon.add(new ShopEntry(ModItems.HUNTER_HAMMER.getDefaultInstance(), 58, ShopEntry.Type.WEAPON));
        hunterCommon.add(new ShopEntry(ModItems.HUNTER_HOOK.getDefaultInstance(), 52, ShopEntry.Type.WEAPON));
        hunterCommon.add(new ShopEntry(ModItems.HUNTER_CHAIN.getDefaultInstance(), 42, ShopEntry.Type.TOOL));
        hunterCommon.add(new ShopEntry(new ItemStack(ModBlocks.HUNTER_SNARE.asItem(), 2), 30, ShopEntry.Type.TOOL));
        hunterCommon
                .add(new ShopEntry(ModItems.HUNTER_PLUGIN_LACERATION.getDefaultInstance(), 32, ShopEntry.Type.TOOL));

        var warden = new ArrayList<>(hunterCommon);
        warden.add(new ShopEntry(ModItems.HUNTER_JAMMER.getDefaultInstance(), 60, ShopEntry.Type.TOOL));
        warden.add(new ShopEntry(new ItemStack(ModBlocks.HUNTER_SNARE.asItem(), 3), 45, ShopEntry.Type.TOOL));
        warden.add(new ShopEntry(ModItems.HUNTER_PLUGIN_SUPPRESSION.getDefaultInstance(), 38, ShopEntry.Type.TOOL));

        var brute = new ArrayList<>(hunterCommon);
        brute.add(new ShopEntry(ModItems.HUNTER_BLINK.getDefaultInstance(), 70, ShopEntry.Type.TOOL));
        brute.add(new ShopEntry(ModItems.ROPE.getDefaultInstance(), 40, ShopEntry.Type.TOOL));
        brute.add(new ShopEntry(ModItems.HUNTER_PLUGIN_CONCUSSION.getDefaultInstance(), 38, ShopEntry.Type.TOOL));

        var tracker = new ArrayList<>(hunterCommon);
        tracker.add(new ShopEntry(ModItems.HUNTER_PULSE.getDefaultInstance(), 70, ShopEntry.Type.TOOL));
        tracker.add(new ShopEntry(ModItems.ROPE.getDefaultInstance(), 40, ShopEntry.Type.TOOL));
        tracker.add(new ShopEntry(ModItems.HUNTER_PLUGIN_TRACKING.getDefaultInstance(), 34, ShopEntry.Type.TOOL));

        {
            ShopContent.customEntries.put(RepairRoles.REPAIR_WARDEN_ID, warden);
        }
        {
            ShopContent.customEntries.put(RepairRoles.REPAIR_BRUTE_ID, brute);
        }
        {
            ShopContent.customEntries.put(RepairRoles.REPAIR_TRACKER_ID, tracker);
        }

        var mechanic = new ArrayList<ShopEntry>();
        mechanic.add(new ShopEntry(ModItems.REPAIR_TOOLBOX.getDefaultInstance(), 45, ShopEntry.Type.TOOL));
        mechanic.add(new ShopEntry(new ItemStack(ModItems.SPARE_PARTS, 4), 30, ShopEntry.Type.TOOL));
        mechanic.add(new ShopEntry(ModItems.REPAIR_CROWBAR.getDefaultInstance(), 40, ShopEntry.Type.TOOL));
        mechanic.add(new ShopEntry(ModItems.REPAIR_BOLT_CUTTER.getDefaultInstance(), 45, ShopEntry.Type.TOOL));
        mechanic.add(new ShopEntry(ModItems.REPAIR_FUSE.getDefaultInstance(), 55, ShopEntry.Type.TOOL));
        mechanic.add(new ShopEntry(ModItems.SMOKE_PELLET.getDefaultInstance(), 25, ShopEntry.Type.TOOL));
        {
            ShopContent.customEntries.put(RepairRoles.REPAIR_MECHANIC_ID, mechanic);
        }

        var medic = new ArrayList<ShopEntry>();
        medic.add(new ShopEntry(ModItems.RESCUE_FLARE.getDefaultInstance(), 45, ShopEntry.Type.TOOL));
        medic.add(new ShopEntry(new ItemStack(ModItems.SMOKE_PELLET, 2), 40, ShopEntry.Type.TOOL));
        medic.add(new ShopEntry(new ItemStack(ModItems.SPARE_PARTS, 3), 25, ShopEntry.Type.TOOL));
        medic.add(new ShopEntry(ModItems.ESCAPE_GRAPPLE.getDefaultInstance(), 55, ShopEntry.Type.TOOL));
        {
            ShopContent.customEntries.put(RepairRoles.REPAIR_MEDIC_ID, medic);
        }

        var runner = new ArrayList<ShopEntry>();
        runner.add(new ShopEntry(ModItems.ESCAPE_GRAPPLE.getDefaultInstance(), 45, ShopEntry.Type.TOOL));
        runner.add(new ShopEntry(ModItems.DECOY_BEACON.getDefaultInstance(), 35, ShopEntry.Type.TOOL));
        runner.add(new ShopEntry(ModItems.SMOKE_PELLET.getDefaultInstance(), 30, ShopEntry.Type.TOOL));
        runner.add(new ShopEntry(ModItems.REPAIR_OLD_KEY.getDefaultInstance(), 60, ShopEntry.Type.TOOL));
        runner.add(new ShopEntry(ModItems.REPAIR_LOCKPICK.getDefaultInstance(), 36, ShopEntry.Type.TOOL));
        runner.add(new ShopEntry(new ItemStack(ModItems.SPARE_PARTS, 3), 28, ShopEntry.Type.TOOL));
        {
            ShopContent.customEntries.put(RepairRoles.REPAIR_RUNNER_ID, runner);
        }

        var archivist = new ArrayList<ShopEntry>();
        archivist.add(new ShopEntry(new ItemStack(ModItems.SPARE_PARTS, 5), 35, ShopEntry.Type.TOOL));
        archivist.add(new ShopEntry(ModItems.REPAIR_TOOLBOX.getDefaultInstance(), 50, ShopEntry.Type.TOOL));
        archivist.add(new ShopEntry(ModItems.REPAIR_AREA_KEY.getDefaultInstance(), 45, ShopEntry.Type.TOOL));
        archivist.add(new ShopEntry(ModItems.RESCUE_FLARE.getDefaultInstance(), 45, ShopEntry.Type.TOOL));
        {
            ShopContent.customEntries.put(RepairRoles.REPAIR_ARCHIVIST_ID, archivist);
        }

        var saboteur = new ArrayList<ShopEntry>();
        saboteur.add(new ShopEntry(ModItems.SMOKE_PELLET.getDefaultInstance(), 30, ShopEntry.Type.TOOL));
        saboteur.add(new ShopEntry(ModItems.DECOY_BEACON.getDefaultInstance(), 35, ShopEntry.Type.TOOL));
        saboteur.add(new ShopEntry(new ItemStack(ModItems.SPARE_PARTS, 4), 35, ShopEntry.Type.TOOL));
        {
            ShopContent.customEntries.put(RepairRoles.REPAIR_SABOTEUR_ID, saboteur);
        }

        var collector = new ArrayList<ShopEntry>();
        collector.add(new ShopEntry(new ItemStack(ModItems.SPARE_PARTS, 4), 28, ShopEntry.Type.TOOL));
        collector.add(new ShopEntry(ModItems.REPAIR_GEAR_HANDLE.getDefaultInstance(), 50, ShopEntry.Type.TOOL));
        collector.add(new ShopEntry(ModItems.REPAIR_BATTERY.getDefaultInstance(), 45, ShopEntry.Type.TOOL));
        collector.add(new ShopEntry(ModItems.REPAIR_VALVE_HANDLE.getDefaultInstance(), 45, ShopEntry.Type.TOOL));
        collector.add(new ShopEntry(ModItems.RESCUE_FLARE.getDefaultInstance(), 45, ShopEntry.Type.TOOL));
        collector.add(new ShopEntry(ModItems.DECOY_BEACON.getDefaultInstance(), 35, ShopEntry.Type.TOOL));
        collector.add(new ShopEntry(new ItemStack(ModItems.SMOKE_PELLET, 2), 40, ShopEntry.Type.TOOL));
        {
            ShopContent.customEntries.put(RepairRoles.REPAIR_COLLECTOR_ID, collector);
        }
    }

    public static boolean isOldmanEasterEggRod(@NotNull ItemStack stack) {
        if (!stack.is(Items.CARROT_ON_A_STICK))
            return false;
        var customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return customData.copyTag().getBoolean(OLDMAN_EASTER_EGG_TAG);
    }

    public static boolean hasUsedOldmanEasterEggRod(@NotNull ItemStack stack) {
        if (!isOldmanEasterEggRod(stack))
            return false;
        var customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return customData.copyTag().getBoolean(OLDMAN_EASTER_EGG_USED_TAG);
    }

    public static void markOldmanEasterEggRodUsed(@NotNull ItemStack stack) {
        if (!isOldmanEasterEggRod(stack))
            return;
        var customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        tag.putBoolean(OLDMAN_EASTER_EGG_USED_TAG, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static ArrayList<ShopEntry> FRAMING_ROLES_SHOP = new ArrayList<>();
    public static ArrayList<ShopEntry> BLOOD_FEUDIST_SHOP = new ArrayList<>();
    public static ArrayList<ShopEntry> WATER_GHOST_SHOP = new ArrayList<>();

    /**
     * 初始化框架角色商店
     */
    public static void initializeFramingShop() {
        FRAMING_ROLES_SHOP.clear();
        FRAMING_ROLES_SHOP
                .add(new ShopEntry(ModItems.MASTER_KEY_P.getDefaultInstance(), 50,
                        ShopEntry.Type.TOOL));
        FRAMING_ROLES_SHOP
                .add(new ShopEntry(ModItems.DELUSION_VIAL.getDefaultInstance(), 30,
                        ShopEntry.Type.POISON));
        FRAMING_ROLES_SHOP.add(new ShopEntry(TMMItems.FIRECRACKER.getDefaultInstance(), 50,
                ShopEntry.Type.TOOL));
        FRAMING_ROLES_SHOP
                .add(new ShopEntry(TMMItems.NOTE.getDefaultInstance(), 15, ShopEntry.Type.TOOL));
    }

    /**
     * 初始化水鬼商店
     * - 开锁器：100金币
     * - 下雨：150金币
     */
    public static void initializeWaterGhostShop() {
        // 开锁器 - 100金币
        WATER_GHOST_SHOP.clear();
        WATER_GHOST_SHOP.add(new ShopEntry(
                TMMItems.LOCKPICK.getDefaultInstance(),
                100,
                ShopEntry.Type.TOOL));

        // 下雨 - 150金币（参考ma_chen_xu的狂热下雨实现，但只保留下雨能力）
        ItemStack rainItem = Items.WATER_BUCKET.getDefaultInstance();
        rainItem.set(DataComponents.ITEM_NAME,
                Component.translatable("item.noellesroles.water_ghost.rain")
                        .withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD));
        var rainLore = new ArrayList<Component>();
        rainLore.add(Component.translatable("item.noellesroles.water_ghost.rain.lore1")
                .setStyle(Style.EMPTY.withItalic(false))
                .withStyle(ChatFormatting.GRAY));
        rainLore.add(Component.translatable("item.noellesroles.water_ghost.rain.lore2")
                .setStyle(Style.EMPTY.withItalic(false))
                .withStyle(ChatFormatting.GRAY));
        rainLore.add(Component.translatable("item.noellesroles.water_ghost.rain.lore3")
                .setStyle(Style.EMPTY.withItalic(false))
                .withStyle(ChatFormatting.GRAY));
        rainItem.set(DataComponents.LORE, new ItemLore(rainLore));

        WATER_GHOST_SHOP.add(new ShopEntry(rainItem, 150, ShopEntry.Type.TOOL) {
            @Override
            public boolean onBuy(@NotNull Player player) {
                var component = WaterGhostPlayerComponent.KEY.get(player);
                if (component != null) {
                    return component.buyRain();
                }
                return false;
            }
        });
    }

    /**
     * 初始化仇杀客商店
     * - 撬棍：35金币
     * - 开锁器：80金币
     * - 疯狂模式：275金币（冷却30秒）
     */
    public static void initializeBloodFeudistShop() {
        BLOOD_FEUDIST_SHOP.clear();
        // 撬棍 - 35金币
        BLOOD_FEUDIST_SHOP.add(new ShopEntry(
                TMMItems.CROWBAR.getDefaultInstance(),
                35,
                ShopEntry.Type.TOOL));

        // 开锁器 - 80金币
        BLOOD_FEUDIST_SHOP.add(new ShopEntry(
                TMMItems.LOCKPICK.getDefaultInstance(),
                80,
                ShopEntry.Type.TOOL));

        // 疯狂模式 - 275金币
        BLOOD_FEUDIST_SHOP.add(new ShopEntry(
                TMMItems.PSYCHO_MODE.getDefaultInstance(),
                275,
                ShopEntry.Type.WEAPON) {
            @Override
            public boolean onBuy(Player player) {
                var psycc = SREPlayerPsychoComponent.KEY.get(player);
                boolean success = psycc.startPsycho();
                if (success) {
                    player.getCooldowns().addCooldown(TMMItems.PSYCHO_MODE, 20 * 60);
                }
                return success;
            }
        });
    }

    public static void shopRegister() {
        SRE.LOGGER.info("[SHOP] Register Role Shops");
        haveRegistered = true;
        ShopContent.customEntries.clear();
        ShopContent.register();
        // 初始化其他角色商店
        // 初始化框架角色商店
        initializeFramingShop();

        // 初始化仇杀客商店
        initializeBloodFeudistShop();

        registerRepairModeShops();

        ArrayList<ShopEntry> PELICAN_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> CONSPIRATOR_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> 柜子区的商店 = new ArrayList<>();
        ArrayList<ShopEntry> SLIPPERY_GHOST_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> ENGINEER_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> BOXER_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> PSYCHOLOGIST_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> BOMBER_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> DOCTOR_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> SINGER_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> ATTENDANT_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> VETERAN_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> PATROLLER_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> NIAN_SHOU_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> MAGICIAN_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> BANDIT_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> CLOCKMAKER_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> WRITER_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> RESCUER_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> FIREFIGHTER_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> ACCOUNTANT_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> SWAST_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> MARTIAL_ARTS_INSTRUCTOR_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> SEA_KING_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> CANDLE_BEARER_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> SUPER_LOOSE_END_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> PILOT_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> SHADOW_FALCON_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> GANGSTERS_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> FITTER_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> GODFATHER_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> MAFIOSO_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> JANITOR_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> NUTRITIONIST_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> PARASOL_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> WARLOCK_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> DREAM_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> EMBALMER_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> PHANTOM_MUSICIAN_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> WRAITH_ASSASSIN_SHOP = new ArrayList<>();
        ArrayList<ShopEntry> REASONER_SHOP = new ArrayList<>();

        // ---- 柜子区的商店（执行者） ----
        {
            柜子区的商店.add(new ShopEntry(
                    ModItems.BANDIT_REVOLVER.getDefaultInstance(),
                    130,
                    ShopEntry.Type.TOOL));
            柜子区的商店.add(new ShopEntry(TMMItems.FIRECRACKER.getDefaultInstance(), SREConfig.instance().firecrackerPrice,
                    ShopEntry.Type.TOOL));
            柜子区的商店.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 80, ShopEntry.Type.TOOL));
            柜子区的商店.add(new ShopEntry(TMMItems.BODY_BAG.getDefaultInstance(), SREConfig.instance().bodyBagPrice,
                    ShopEntry.Type.TOOL));
            柜子区的商店.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), SREConfig.instance().blackoutPrice,
                    ShopEntry.Type.TOOL) {
                public boolean onBuy(@NotNull Player player) {

                    boolean triggered = SREPlayerShopComponent.useBlackout(player);
                    if (triggered) {
                        player.getCooldowns().addCooldown(TMMItems.BLACKOUT,
                                Math.max(60 * 20, GameConstants.getBlackoutCooldownGlobal()));
                        return true;
                    }
                    return triggered;
                }
            });
            // 监控失灵 - 60金币（刽子手专属）
            柜子区的商店.add(new ShopEntry(TMMItems.MONITOR_BROKEN.getDefaultInstance(), 60, ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    return SREPlayerShopComponent.useMonitorBroken(player,
                            SREConfig.instance().monitorBrokenDuration * 20);
                }
            });
            {
                // 射击狂热 - 275金币（魔改psycho，狂暴模式）
                var 柜子区疯魔 = TMMItems.PSYCHO_MODE.getDefaultInstance();
                柜子区疯魔.set(DataComponents.ITEM_NAME,
                        Component.translatable("itemstack.executioner.psychoitem.item_name"));
                var lore = new ItemLore(List.of(
                        Component.translatable("itemstack.executioner.psychoitem.item_lore.1")
                                .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY)),
                        Component.translatable("itemstack.executioner.psychoitem.item_lore.2")
                                .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY))));
                柜子区疯魔.set(DataComponents.LORE, lore);
                柜子区的商店.add(new ShopEntry(
                        柜子区疯魔,
                        325,
                        ShopEntry.Type.WEAPON) {
                    @Override
                    public boolean onBuy(@NotNull Player player) {
                        ShootingFrenzyPlayerComponent frenzyComponent = ShootingFrenzyPlayerComponent.KEY.get(player);
                        boolean success = frenzyComponent.startFrenzy();
                        if (success) {
                            player.getCooldowns().addCooldown(TMMItems.PSYCHO_MODE, 20 * 60);
                        }
                        return success;
                    }
                });
            }
            {
                // 切换目标 - 200金币
                var 柜子区切换目标 = Items.PAPER.getDefaultInstance();
                柜子区切换目标.set(DataComponents.ITEM_NAME,
                        Component.translatable("itemstack.executioner.change_target.item_name"));
                var lore = new ItemLore(List.of(
                        Component.translatable("itemstack.executioner.change_target.item_lore.1")
                                .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY)),
                        Component.translatable("itemstack.executioner.change_target.item_lore.2")
                                .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY))));
                柜子区切换目标.set(DataComponents.LORE, lore);
                柜子区的商店.add(new ShopEntry(
                        柜子区切换目标,
                        200,
                        ShopEntry.Type.WEAPON) {
                    @Override
                    public boolean onBuy(@NotNull Player player) {
                        boolean success = false;
                        var cca = ExecutionerPlayerComponent.KEY.get(player);
                        success = cca.assignRandomTarget(true);
                        if (success) {
                            player.getCooldowns().addCooldown(Items.PAPER, 20);
                        }
                        return success;
                    }
                });
            }
        }

        // 阴谋家商店
        {
            CONSPIRATOR_SHOP.add(new ShopEntry(
                    ModItems.CONSPIRACY_PAGE.getDefaultInstance(),
                    100,
                    ShopEntry.Type.TOOL));

            CONSPIRATOR_SHOP.add(new KillerKnifeShopEntry(

                    120));

            CONSPIRATOR_SHOP.add(new ShopEntry(
                    io.wifi.starrailexpress.index.TMMItems.REVOLVER.getDefaultInstance(),
                    200,
                    ShopEntry.Type.WEAPON));

            CONSPIRATOR_SHOP.add(new ShopEntry(
                    io.wifi.starrailexpress.index.TMMItems.LOCKPICK.getDefaultInstance(),
                    50,
                    ShopEntry.Type.TOOL));
        }

        // 捣蛋鬼商店
        {
            // 空包弹 - 150金币
            SLIPPERY_GHOST_SHOP.add(new ShopEntry(
                    ModItems.BLANK_CARTRIDGE.getDefaultInstance(),
                    150,
                    ShopEntry.Type.TOOL));

            // 烟雾弹 - 150金币
            SLIPPERY_GHOST_SHOP.add(new ShopEntry(
                    ModItems.SMOKE_GRENADE.getDefaultInstance(),
                    150,
                    ShopEntry.Type.TOOL));

            // 撬锁器 - 50金币 (原版杀手商店物品)
            SLIPPERY_GHOST_SHOP.add(new ShopEntry(
                    io.wifi.starrailexpress.index.TMMItems.LOCKPICK.getDefaultInstance(),
                    50,
                    ShopEntry.Type.TOOL));

            // 闪光弹 - 175金币（捣蛋鬼专用）
            SLIPPERY_GHOST_SHOP
                    .add(new ShopEntry(ModItems.FLASH_GRENADE.getDefaultInstance(), 175, ShopEntry.Type.TOOL) {
                        @Override
                        public boolean canBuy(@NotNull Player player) {
                            return !(MCItemsUtils.countItem(player, ModItems.FLASH_GRENADE) > 0);
                        }
                    });

            // 诱饵弹 - 25金币（捣蛋鬼专用）
            SLIPPERY_GHOST_SHOP
                    .add(new ShopEntry(ModItems.DECOY_GRENADE.getDefaultInstance(), 25, ShopEntry.Type.TOOL));

            // 监控失灵 - 50金币（捣蛋鬼专属）
            SLIPPERY_GHOST_SHOP
                    .add(new ShopEntry(TMMItems.MONITOR_BROKEN.getDefaultInstance(), 50, ShopEntry.Type.TOOL) {
                        @Override
                        public boolean onBuy(@NotNull Player player) {
                            return SREPlayerShopComponent.useMonitorBroken(player,
                                    SREConfig.instance().monitorBrokenDuration * 20);
                        }
                    });

            // 关灯 - 300金币 (原版杀手商店物品)
            SLIPPERY_GHOST_SHOP.add(
                    new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), SREConfig.instance().blackoutPrice,
                            ShopEntry.Type.TOOL) {
                        public boolean onBuy(@NotNull Player player) {
                            return SREPlayerShopComponent.useBlackout(player);
                        }
                    });
        }

        // 工程师商店
        {
            // 加固门 - 30金币
            ENGINEER_SHOP.add(new ShopEntry(
                    ModItems.REINFORCEMENT.getDefaultInstance(),
                    30,
                    ShopEntry.Type.TOOL));

            // 警报陷阱 - 15金币
            ENGINEER_SHOP.add(new ShopEntry(
                    ModItems.ALARM_TRAP.getDefaultInstance(),
                    15,
                    ShopEntry.Type.TOOL));

            ENGINEER_SHOP.add(new ShopEntry(
                    ModItems.MASTER_KEY_P.getDefaultInstance(),
                    90,
                    ShopEntry.Type.TOOL));

            ENGINEER_SHOP.add(new ShopEntry(
                    ModItems.LOCK_ITEM.getDefaultInstance(),
                    100,
                    ShopEntry.Type.TOOL));
        }

        // 年兽商店
        {
            // 关灯 - 200金币
            NIAN_SHOU_SHOP.add(
                    new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), 200, ShopEntry.Type.TOOL) {
                        public boolean onBuy(@NotNull Player player) {
                            return SREPlayerShopComponent.useBlackout(player);
                        }
                    });
        }

        // 斗士商店
        {
            BOXER_SHOP.add(new ShopEntry(
                    ModItems.BOXING_GLOVE.getDefaultInstance(),
                    150,
                    ShopEntry.Type.WEAPON));
        }

        // 心理学家商店
        {
            // 薄荷糖 - 75金币
            PSYCHOLOGIST_SHOP.add(new ShopEntry(
                    ModItems.MINT_CANDIES.getDefaultInstance(),
                    75,
                    ShopEntry.Type.TOOL));
            // 维生素 - 125金币
            PSYCHOLOGIST_SHOP.add(new ShopEntry(
                    ModItems.ALCHEMIST_BUFF_POTION.getDefaultInstance(),
                    125,
                    ShopEntry.Type.TOOL));
        }

        // 炸弹客商店
        {
            BOMBER_SHOP.add(new ShopEntry(
                    TMMItems.GRENADE.getDefaultInstance(),
                    290,
                    ShopEntry.Type.WEAPON));
            BOMBER_SHOP.add(new ShopEntry(
                    TMMItems.TIMED_GRENADE.getDefaultInstance(),
                    290, ShopEntry.Type.WEAPON));
            BOMBER_SHOP.add(new ShopEntry(
                    TMMItems.STICKY_GRENADE.getDefaultInstance(),
                    290,
                    ShopEntry.Type.WEAPON));
            BOMBER_SHOP.add(new ShopEntry(
                    TMMItems.FIRECRACKER.getDefaultInstance(),
                    25,
                    ShopEntry.Type.TOOL));
            BOMBER_SHOP.add(new ShopEntry(
                    TMMItems.LOCKPICK.getDefaultInstance(),
                    80,
                    ShopEntry.Type.TOOL));
        }

        // 巡警商店
        {
            // 左轮手枪 - 325金币
            PATROLLER_SHOP.add(new ShopEntry(
                    TMMItems.REVOLVER.getDefaultInstance(),
                    325,
                    ShopEntry.Type.WEAPON));
        }

        // 歌手商店
        {
            for (int i = 1; i <= 5; i++) {
                ItemStack singer_shop_item = ModItems.SINGER_MUSIC_DISC.getDefaultInstance();
                singer_shop_item.set(DataComponents.ITEM_NAME,
                        Component.translatable("item.noellesroles.shop.singer.display_name.root",
                                Component.translatable(
                                        "item.noellesroles.shop.singer.display_name."
                                                + i)
                                        .withStyle(ChatFormatting.GOLD))
                                .withStyle(ChatFormatting.AQUA));
                var lores = new ArrayList<Component>();
                lores.add(Component.translatable("item.noellesroles.shop.singer.lore",
                        Component.translatable("item.noellesroles.shop.singer.effect." + i)
                                .withStyle(ChatFormatting.YELLOW))
                        .withStyle(ChatFormatting.GRAY));
                singer_shop_item.set(DataComponents.LORE, new ItemLore(lores));
                final int idx = i;
                // 第5张唱片(Lupinus)价格为500金币，其他为100金币
                int price = (i == 5) ? 500 : 100;
                SINGER_SHOP.add(new ShopEntry(singer_shop_item, price, ShopEntry.Type.TOOL) {
                    public boolean onBuy(@NotNull Player player) {
                        return SingerPlayerComponent.buyDisc(player, idx);
                    }
                });
            }
        }

        // 医生商店
        {
            DOCTOR_SHOP.add(new ShopEntry(
                    ModItems.ANTIDOTE_REAGENT.getDefaultInstance(),
                    50,
                    ShopEntry.Type.TOOL));
            // 针管 - 75金币
            DOCTOR_SHOP.add(new ShopEntry(
                    ModItems.ANTIDOTE.getDefaultInstance(),
                    75,
                    ShopEntry.Type.TOOL));
            // 药丸 - 75金币
            DOCTOR_SHOP.add(new ShopEntry(
                    ModItems.createPillStack(false),
                    75,
                    ShopEntry.Type.TOOL));
            // 净化弹 - 300金币
            DOCTOR_SHOP.add(new ShopEntry(
                    ModItems.PURIFY_BOMB.getDefaultInstance(),
                    300,
                    ShopEntry.Type.TOOL));
        }

        // 乘务员商店
        {
            // 乘务员钥匙 - 50金币
            ATTENDANT_SHOP.add(new ShopEntry(ModItems.MASTER_KEY_P.getDefaultInstance(), 50, ShopEntry.Type.TOOL));

            // 铁门钥匙 - 75金币
            ATTENDANT_SHOP
                    .add(new ShopEntry(
                            io.wifi.starrailexpress.index.TMMItems.IRON_DOOR_KEY
                                    .getDefaultInstance(),
                            75, ShopEntry.Type.TOOL));
            // 手电筒（moonlight_lamp） - 150金币
            if (BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("handheldmoon:moonlight_lamp"))) {
                final var moonlightLampItem = BuiltInRegistries.ITEM
                        .get(ResourceLocation.parse("handheldmoon:moonlight_lamp"));
                if (moonlightLampItem != null) {
                    final var defaultInstance = moonlightLampItem.getDefaultInstance();
                    ATTENDANT_SHOP.add(new ShopEntry(defaultInstance, 150, ShopEntry.Type.TOOL));
                }
            }
            // 乘务员专属商店 - 添加原版灯笼，价格 75 金币
            ItemStack attendantLantern = Items.LANTERN.getDefaultInstance();
            ATTENDANT_SHOP.add(new ShopEntry(attendantLantern, 75, ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    return RoleUtils.insertStackInFreeSlot(player, attendantLantern.copy());
                }
            });
        }

        // 魔术师商店
        {
            // 假刀 - 50金币
            MAGICIAN_SHOP.add(new ShopEntry(
                    ModItems.FAKE_KNIFE.getDefaultInstance(),
                    50,
                    ShopEntry.Type.WEAPON));

            // 假撬棍 - 35金币
            MAGICIAN_SHOP.add(new ShopEntry(
                    ModItems.FAKE_CROWBAR.getDefaultInstance(),
                    35,
                    ShopEntry.Type.WEAPON));

            // 假开锁器 - 80金币
            MAGICIAN_SHOP.add(new ShopEntry(
                    ModItems.FAKE_LOCKPICK.getDefaultInstance(),
                    80,
                    ShopEntry.Type.WEAPON));

            // 鞭炮 - 30金币
            MAGICIAN_SHOP.add(new ShopEntry(
                    TMMItems.FIRECRACKER.getDefaultInstance(),
                    30,
                    ShopEntry.Type.WEAPON));

            // 假裹尸袋 - 100金币
            MAGICIAN_SHOP.add(new ShopEntry(
                    ModItems.FAKE_BODY_BAG.getDefaultInstance(),
                    100,
                    ShopEntry.Type.WEAPON));

            // 便签 - 100金币
            MAGICIAN_SHOP.add(new ShopEntry(
                    TMMItems.NOTE.getDefaultInstance(),
                    100,
                    ShopEntry.Type.WEAPON));

            // 假枪 - 175金币
            MAGICIAN_SHOP.add(new ShopEntry(
                    ModItems.FAKE_REVOLVER.getDefaultInstance(),
                    175,
                    ShopEntry.Type.WEAPON));

            // 假手雷 - 200金币
            MAGICIAN_SHOP.add(new ShopEntry(
                    ModItems.FAKE_GRENADE.getDefaultInstance(),
                    200,
                    ShopEntry.Type.WEAPON));

            // 假疯狂模式 - 325金币
            MAGICIAN_SHOP.add(new ShopEntry(
                    ModItems.FAKE_PSYCHO_MODE.getDefaultInstance(),
                    325,
                    ShopEntry.Type.WEAPON) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    // 获得假球棒并启动假疯狂模式
                    var magicianComponent = org.agmas.noellesroles.component.ModComponents.MAGICIAN
                            .get(player);
                    if (magicianComponent != null) {
                        if (!magicianComponent.startFakePsycho()) {
                            return false;
                        }
                    }
                    return true;
                }
            });
        }

        // 强盗商店（已调整价格与条目）
        {
            BANDIT_SHOP.add(new KillerKnifeShopEntry(

                    200));

            // 匪徒短管霰弹枪 - 450金币
            BANDIT_SHOP.add(new ShopEntry(
                    ModItems.SHORT_SHOTGUN.getDefaultInstance(),
                    450,
                    ShopEntry.Type.WEAPON));

            // 飞斧 - 首购115金币，二次起70%折扣（可穿透击杀2人、撞墙钉住5秒后消失）
            BANDIT_SHOP.add(new org.agmas.noellesroles.content.item.ThrowingAxeShopEntry());

            // 手榴弹 - 600金币
            BANDIT_SHOP.add(new ShopEntry(
                    TMMItems.TIMED_GRENADE.getDefaultInstance(),
                    600,
                    ShopEntry.Type.WEAPON));

            // 关灯 - 配置价格 * 1.275 后取整
            BANDIT_SHOP.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), banditBlackoutPrice(),
                    ShopEntry.Type.TOOL) {
                public boolean onBuy(@NotNull Player player) {
                    return SREPlayerShopComponent.useBlackout(player);
                }
            });

            // 监控失灵 - 75金币（强盗专属）
            BANDIT_SHOP.add(new ShopEntry(TMMItems.MONITOR_BROKEN.getDefaultInstance(), 75, ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    return SREPlayerShopComponent.useMonitorBroken(player,
                            SREConfig.instance().monitorBrokenDuration * 20);
                }
            });

            // 闪光弹
            BANDIT_SHOP.add(new ShopEntry(ModItems.FLASH_GRENADE.getDefaultInstance(), 30, ShopEntry.Type.TOOL) {
                @Override
                public boolean canBuy(@NotNull Player player) {
                    return !(MCItemsUtils.countItem(player, ModItems.FLASH_GRENADE) > 0);
                }
            });

            // 诱饵弹 - 15金币
            BANDIT_SHOP.add(new ShopEntry(ModItems.DECOY_GRENADE.getDefaultInstance(), 15, ShopEntry.Type.TOOL));
        }

        // 保安商店：远程监控台 - 150金币
        {
            var GUARD_SHOP = new java.util.ArrayList<ShopEntry>();
            GUARD_SHOP.add(new ShopEntry(ModItems.MONITORING_TERMINAL.getDefaultInstance(), 150, ShopEntry.Type.TOOL));
            ShopContent.customEntries.put(ModRoles.GUARD.getIdentifier(), GUARD_SHOP);
        }

        // 驯马师商店：随机彩虹马/残月萨马(各50%) - 100金币；前人留下的马铠 - 200金币；超级猪马蹄铁 - 200金币
        {
            var TAMER_SHOP = new java.util.ArrayList<ShopEntry>();
            TAMER_SHOP.add(new ShopEntry(new ItemStack(Items.HORSE_SPAWN_EGG), 100, ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(Player player) {
                    ItemStack horse = player.getRandom().nextBoolean()
                            ? FunnyItems.RAINBOW_HORSESHOE.getDefaultInstance()
                            : FunnyItems.CANYUESA_HORSESHOE.getDefaultInstance();
                    return RoleUtils.insertStackInFreeSlot(player, horse);
                }
            });
            TAMER_SHOP.add(
                    new ShopEntry(ModItems.PREDECESSOR_HORSE_ARMOR.getDefaultInstance(), 200, ShopEntry.Type.TOOL));
            TAMER_SHOP
                    .add(new ShopEntry(FunnyItems.SUPER_PIG_HORSESHOE.getDefaultInstance(), 200, ShopEntry.Type.TOOL));
            ShopContent.customEntries.put(ModRoles.TAMER.getIdentifier(), TAMER_SHOP);
        }

        // 猎人商店：随机马(40%彩虹/40%残月萨/20%超级猪) - 50金币；前人留下的马铠 - 185金币；毒箭 - 90金币；关灯 - 配置价格；监控失灵
        // - 配置价格
        {
            var HUNTER_SHOP = new java.util.ArrayList<ShopEntry>();
            // 原本的马匹相关商品
            HUNTER_SHOP.add(new ShopEntry(new ItemStack(Items.HORSE_SPAWN_EGG), 50, ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(Player player) {
                    int r = player.getRandom().nextInt(100);
                    ItemStack horse;
                    if (r < 40)
                        horse = FunnyItems.RAINBOW_HORSESHOE.getDefaultInstance();
                    else if (r < 80)
                        horse = FunnyItems.CANYUESA_HORSESHOE.getDefaultInstance();
                    else
                        horse = FunnyItems.SUPER_PIG_HORSESHOE.getDefaultInstance();
                    return RoleUtils.insertStackInFreeSlot(player, horse);
                }
            });
            HUNTER_SHOP.add(
                    new ShopEntry(ModItems.PREDECESSOR_HORSE_ARMOR.getDefaultInstance(), 185, ShopEntry.Type.TOOL));

            // 毒箭 - 90金币，参考游侠毒箭模式
            final var PoisonArrow = Items.TIPPED_ARROW.getDefaultInstance();
            PoisonArrow.set(DataComponents.ITEM_NAME, Component.translatable("item.poison_arrow.name"));
            PoisonArrow.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.POISON));
            PoisonArrow.set(DataComponents.MAX_STACK_SIZE, 1);

            HUNTER_SHOP.add(new ShopEntry(PoisonArrow.copy(), 90, ShopEntry.Type.WEAPON) {
                @Override
                public boolean onBuy(Player player) {
                    int itemCount = SREItemUtils.countItem(player, Items.TIPPED_ARROW);
                    if (itemCount >= 2) {
                        if (player instanceof ServerPlayer sp) {
                            sp.sendSystemMessage(Component.translatable("message.noellesroles.shop.max_poison_arrow"),
                                    true);
                        }
                        return false;
                    }
                    return RoleUtils.insertStackInFreeSlot(player, PoisonArrow.copy());
                }
            });

            // 关灯 - 配置价格
            HUNTER_SHOP.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), SREConfig.instance().blackoutPrice,
                    ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(Player player) {
                    return SREPlayerShopComponent.useBlackout(player);
                }
            });

            // 监控失灵 - 配置价格
            HUNTER_SHOP.add(new ShopEntry(TMMItems.MONITOR_BROKEN.getDefaultInstance(),
                    SREConfig.instance().monitorBrokenPrice, ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(Player player) {
                    return SREPlayerShopComponent.useMonitorBroken(player,
                            SREConfig.instance().monitorBrokenDuration * 20);
                }
            });

            ShopContent.customEntries.put(ModRoles.HUNTER.getIdentifier(), HUNTER_SHOP);
        }

        // 小偷商店（注释部分，保留）
        // ... (原THIEF_SHOP内容被注释，不影响)

        // 钟表匠商店
        {
            // 时钟（原版） - 100金币
            CLOCKMAKER_SHOP.add(new ShopEntry(
                    Items.CLOCK.getDefaultInstance(),
                    100,
                    ShopEntry.Type.TOOL));

            // 怀表 - 150金币
            CLOCKMAKER_SHOP.add(new ShopEntry(
                    ModItems.POCKET_WATCH.getDefaultInstance(),
                    150,
                    ShopEntry.Type.TOOL));
        }

        // 作家商店
        {
            // 书与笔（原版） - 100金币
            WRITER_SHOP.add(new ShopEntry(
                    Items.WRITABLE_BOOK.getDefaultInstance(),
                    100,
                    ShopEntry.Type.TOOL));
        }

        // 搜救员商店
        {
            // 绳索 - 150金币
            RESCUER_SHOP.add(new ShopEntry(
                    ModItems.ROPE.getDefaultInstance(),
                    150,
                    ShopEntry.Type.TOOL));

            // 裹尸袋 - 75金币
            RESCUER_SHOP.add(new ShopEntry(
                    TMMItems.BODY_BAG.getDefaultInstance(),
                    75,
                    ShopEntry.Type.TOOL));
        }

        // 消防员商店
        {
            // 消防斧 - 150金币
            FIREFIGHTER_SHOP.add(new ShopEntry(
                    ModItems.FIRE_AXE.getDefaultInstance(),
                    150,
                    ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(Player player) {
                    if (SREItemUtils.countItem(player, Items.CHAIN_COMMAND_BLOCK) > 0) {
                        return false;
                    }
                    if (RoleUtils.insertStackInFreeSlot(player, this.stack().copy())) {
                        player.getInventory().setItem(14, Items.CHAIN_COMMAND_BLOCK.getDefaultInstance());
                        return true;
                    }
                    return false;
                }
            });

            // 灭火器 - 150金币
            FIREFIGHTER_SHOP.add(new ShopEntry(
                    ModItems.EXTINGUISHER.getDefaultInstance(),
                    150,
                    ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(Player player) {
                    if (SREItemUtils.countItem(player, Items.REPEATING_COMMAND_BLOCK) > 0) {
                        return false;
                    }
                    if (RoleUtils.insertStackInFreeSlot(player, this.stack().copy())) {
                        player.getInventory().setItem(15, Items.REPEATING_COMMAND_BLOCK.getDefaultInstance());
                        return true;
                    }
                    return false;
                }
            });
        }

        // 会计商店
        {
            // 存折 - 100金币（只能购买1次）
            ACCOUNTANT_SHOP.add(new ShopEntry(
                    ModItems.PASSBOOK.getDefaultInstance(),
                    100,
                    ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(Player player) {
                    if (SREItemUtils.countItem(player, Items.COMMAND_BLOCK) > 0) {
                        return false;
                    }
                    if (RoleUtils.insertStackInFreeSlot(player, this.stack().copy())) {
                        player.getInventory().setItem(16, Items.COMMAND_BLOCK.getDefaultInstance());
                        return true;
                    }
                    return false;
                }
            });
        }

        // 特警商店
        {
            // 马格南子弹 - 125金币
            SWAST_SHOP.add(new ShopEntry(
                    TMMItems.MAGNUM_BULLET.getDefaultInstance(),
                    125,
                    ShopEntry.Type.WEAPON));

            // 瞄准镜 - 25金币
            SWAST_SHOP.add(new ShopEntry(
                    TMMItems.SCOPE.getDefaultInstance(),
                    25,
                    ShopEntry.Type.WEAPON));

            // 铁门钥匙 - 75金币
            SWAST_SHOP.add(new ShopEntry(
                    TMMItems.IRON_DOOR_KEY.getDefaultInstance(),
                    75,
                    ShopEntry.Type.TOOL));

            // 狙击枪 - 400金币（已拥有狙击枪时无法购买）
            SWAST_SHOP.add(new ShopEntry(
                    TMMItems.SNIPER_RIFLE.getDefaultInstance(),
                    400,
                    ShopEntry.Type.WEAPON) {
                @Override
                public boolean canBuy(@NotNull Player player) {
                    return super.canBuy(player) && !SREItemUtils.hasItem(player, TMMItems.SNIPER_RIFLE);
                }
            });
        }

        // 武术教官商店
        {
            // 双节棍 - 400金币（已拥有双节棍时无法购买）
            MARTIAL_ARTS_INSTRUCTOR_SHOP.add(new ShopEntry(
                    TMMItems.NUNCHUCK.getDefaultInstance(),
                    400,
                    ShopEntry.Type.WEAPON) {
                @Override
                public boolean canBuy(@NotNull Player player) {
                    return super.canBuy(player) && !SREItemUtils.hasItem(player, TMMItems.NUNCHUCK);
                }
            });
        }

        // 海王商店
        {
            // 普通三叉戟 - 150金币
            var trident = Items.TRIDENT.getDefaultInstance();
            trident.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
            SEA_KING_SHOP.add(new ShopEntry(
                    trident,
                    150,
                    ShopEntry.Type.WEAPON));
        }

        // 水鬼商店
        initializeWaterGhostShop();

        // 秉烛人商店
        {
            // 撬锁器 - 75金币

            CANDLE_BEARER_SHOP.add(new ShopEntry(
                    TMMItems.LOCKPICK.getDefaultInstance(),
                    75,
                    ShopEntry.Type.TOOL));

            // 隐身机会 - 125金币（图标为药水，购买后隐身机会+1）
            {
                var invisItem = Items.POTION.getDefaultInstance();
                invisItem.set(DataComponents.ITEM_NAME,
                        Component.translatable("item.noellesroles.candlebearer.shop.invisibility_charge")
                                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
                var invisLore = new ArrayList<Component>();
                invisLore.add(Component.translatable("item.noellesroles.candlebearer.shop.invisibility_charge.lore1")
                        .setStyle(Style.EMPTY.withItalic(false)).withStyle(ChatFormatting.GRAY));
                invisItem.set(DataComponents.LORE, new ItemLore(invisLore));

                CANDLE_BEARER_SHOP.add(new ShopEntry(invisItem, 125, ShopEntry.Type.TOOL) {
                    @Override
                    public boolean onBuy(@NotNull Player player) {
                        var comp = CandleBearerPlayerComponent.KEY.get(player);
                        if (comp == null)
                            return false;
                        if (comp.invisibilityCharges >= CandleBearerPlayerComponent.MAX_INVISIBILITY_CHARGES)
                            return false;
                        comp.invisibilityCharges++;
                        if (player instanceof ServerPlayer sp) {
                            sp.displayClientMessage(
                                    Component.translatable("message.noellesroles.candlebearer.charge_gained",
                                            comp.invisibilityCharges,
                                            CandleBearerPlayerComponent.MAX_INVISIBILITY_CHARGES)
                                            .withStyle(ChatFormatting.GOLD),
                                    true);
                        }
                        comp.sync();
                        return true;
                    }
                });
            }

            // 蜡烛 - 50金币
            {
                var candleItem = Items.CANDLE.getDefaultInstance();
                candleItem.set(DataComponents.ITEM_NAME,
                        Component.translatable("item.noellesroles.candlebearer.shop.candle")
                                .withStyle(ChatFormatting.YELLOW));
                var candleLore = new ArrayList<Component>();
                candleLore.add(Component.translatable("item.noellesroles.candlebearer.shop.candle.lore1")
                        .setStyle(Style.EMPTY.withItalic(false)).withStyle(ChatFormatting.GRAY));
                candleItem.set(DataComponents.LORE, new ItemLore(candleLore));

                CANDLE_BEARER_SHOP.add(new ShopEntry(candleItem, 50, ShopEntry.Type.TOOL) {
                    @Override
                    public boolean onBuy(@NotNull Player player) {
                        return RoleUtils.insertStackInFreeSlot(player, this.stack().copy());
                    }
                });
            }
        }

        // 超级亡命徒商店
        {
            // 飞刀
            SUPER_LOOSE_END_SHOP.add(new ShopEntry(
                    ModItems.THROWING_KNIFE.getDefaultInstance(),
                    100,
                    ShopEntry.Type.TOOL));
            // 绳索（拉人）
            SUPER_LOOSE_END_SHOP.add(new ShopEntry(
                    ModItems.ROPE.getDefaultInstance(),
                    150,
                    ShopEntry.Type.TOOL));
        }

        // 飞行员商店
        {
            // 喷气背包 - 150金币
            PILOT_SHOP.add(new ShopEntry(
                    ModItems.JETPACK.getDefaultInstance(),
                    150,
                    ShopEntry.Type.TOOL));
            // 鞘翅 - 400金币
            PILOT_SHOP.add(new ShopEntry(
                    Items.ELYTRA.getDefaultInstance(),
                    400,
                    ShopEntry.Type.TOOL));
            // 烟花火箭 - 75金币
            PILOT_SHOP.add(new ShopEntry(
                    new ItemStack(Items.FIREWORK_ROCKET, 1),
                    75,
                    ShopEntry.Type.TOOL));
        }

        // 影隼商店
        {
            SHADOW_FALCON_SHOP.add(new KillerKnifeShopEntry(

                    130));
            SHADOW_FALCON_SHOP.add(new ShopEntry(
                    ModItems.THROWING_KNIFE.getDefaultInstance(),
                    145,
                    ShopEntry.Type.TOOL));
            SHADOW_FALCON_SHOP.add(new ShopEntry(
                    TMMItems.CROWBAR.getDefaultInstance(),
                    35,
                    ShopEntry.Type.TOOL));
            SHADOW_FALCON_SHOP.add(new ShopEntry(
                    TMMItems.LOCKPICK.getDefaultInstance(),
                    100,
                    ShopEntry.Type.TOOL));
            SHADOW_FALCON_SHOP.add(new ShopEntry(
                    TMMItems.GRENADE.getDefaultInstance(),
                    350,
                    ShopEntry.Type.WEAPON));
            // 跳跃提升2药水 - 100金币，购买后给予30秒效果（图标为poison）
            {
                ItemStack jumpBoostPotion = Items.SPLASH_POTION.getDefaultInstance();
                jumpBoostPotion.set(DataComponents.ITEM_NAME,
                        Component.translatable("item.noellesroles.shadow_falcon.jump_boost")
                                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                var jumpBoostLore = new ArrayList<Component>();
                jumpBoostLore.add(Component.translatable("item.noellesroles.shadow_falcon.jump_boost.lore1")
                        .setStyle(Style.EMPTY.withItalic(false))
                        .withStyle(ChatFormatting.GRAY));
                jumpBoostPotion.set(DataComponents.LORE, new ItemLore(jumpBoostLore));

                SHADOW_FALCON_SHOP.add(new ShopEntry(jumpBoostPotion, 100, ShopEntry.Type.TOOL) {
                    @Override
                    public boolean onBuy(@NotNull Player player) {
                        // 给予30秒跳跃提升2效果
                        player.addEffect(new MobEffectInstance(
                                MobEffects.JUMP,
                                30 * 20, // 30秒
                                1, // 等级1 = 跳跃提升2
                                false, // ambient
                                true, // showParticles
                                true // showIcon
                        ));
                        return true;
                    }
                });
            }
            // 鞘翅 - 250金币，购买时额外给予10个烟花火箭
            {
                SHADOW_FALCON_SHOP.add(new ShopEntry(Items.ELYTRA.getDefaultInstance(), 250, ShopEntry.Type.TOOL) {
                    @Override
                    public boolean onBuy(@NotNull Player player) {
                        // 给予鞘翅
                        if (!RoleUtils.insertStackInFreeSlot(player, Items.ELYTRA.getDefaultInstance().copy())) {
                            player.displayClientMessage(
                                    Component.translatable("message.noellesroles.shadow_falcon.elytra_inventory_full"),
                                    true);
                            return false;
                        }
                        // 额外给予10个烟花火箭
                        ItemStack fireworks = new ItemStack(Items.FIREWORK_ROCKET, 10);
                        if (!player.getInventory().add(fireworks)) {
                            // 背包满了就丢在地上
                            player.drop(fireworks, true);
                        }
                        return true;
                    }
                });
            }
        }

        // ==================== 悍匪商店 ====================
        {
            // 刀 - 160金币
            GANGSTERS_SHOP.add(new KillerKnifeShopEntry(

                    160));

            // 短管霰弹枪 - 185金币
            GANGSTERS_SHOP.add(new ShopEntry(
                    ModItems.SHORT_SHOTGUN.getDefaultInstance(),
                    185,
                    ShopEntry.Type.WEAPON));

            // C4炸药 - 280金币
            GANGSTERS_SHOP.add(new ShopEntry(
                    ModItems.C4.getDefaultInstance(),
                    280,
                    ShopEntry.Type.TOOL));

            // 撬棍 - 25金币
            GANGSTERS_SHOP.add(new ShopEntry(
                    TMMItems.CROWBAR.getDefaultInstance(),
                    25,
                    ShopEntry.Type.TOOL));

            // 开锁器 - 80金币
            GANGSTERS_SHOP.add(new ShopEntry(
                    TMMItems.LOCKPICK.getDefaultInstance(),
                    80,
                    ShopEntry.Type.TOOL));

            // 关灯 - 使用配置价格
            GANGSTERS_SHOP.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), SREConfig.instance().blackoutPrice,
                    ShopEntry.Type.TOOL) {
                public boolean onBuy(@NotNull Player player) {
                    return SREPlayerShopComponent.useBlackout(player);
                }
            });
        }

        // ==================== 钳工商店 ====================
        {
            // 开灯 - 225金币（购买后立即结束关灯时间并清除全场黑暗与失明药水效果，未处于关灯时间无法购买）
            FITTER_SHOP.add(new ShopEntry(ModItems.LIGHTUP.getDefaultInstance(), 225, ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    SREWorldBlackoutComponent blackCCA = SREWorldBlackoutComponent.KEY.get(player.level());
                    if (blackCCA.blackOutRemainingTicks <= 0)
                        return false;
                    blackCCA.reset();
                    // 清除全场黑暗与失明药水效果
                    for (Player p : player.level().players()) {
                        p.removeEffect(MobEffects.BLINDNESS);
                        p.removeEffect(MobEffects.DARKNESS);
                    }
                    // 全场播放 block.smithing_table.use 音效
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.SMITHING_TABLE_USE, SoundSource.MASTER, 1.0F, 1.0F);
                    // 冷却与关灯一致
                    player.level().players().forEach(
                            p -> p.getCooldowns().addCooldown(ModItems.LIGHTUP,
                                    GameConstants.getBlackoutCooldownGlobal()));
                    player.getCooldowns().addCooldown(ModItems.LIGHTUP,
                            GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.BLACKOUT, 0));
                    return true;
                }
            });

            // 监控恢复 - 75金币（购买后立即结束监控失灵时间，未处于监控失灵期间无法购买）
            FITTER_SHOP.add(new ShopEntry(ModItems.MONITOR_RECOVERY.getDefaultInstance(), 75, ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    SREMonitorWorldComponent monitorCCA = SREMonitorWorldComponent.KEY.get(player.level());
                    if (monitorCCA.brokenTime <= 0)
                        return false;
                    monitorCCA.reset();
                    // 全场播放 ui.loom.take_result 音效
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.UI_LOOM_TAKE_RESULT, SoundSource.MASTER, 1.0F, 1.0F);
                    // 冷却与监控失灵一致
                    player.level().players().forEach(
                            p -> p.getCooldowns().addCooldown(ModItems.MONITOR_RECOVERY,
                                    GameConstants.getMonitorBrokenCooldownGlobal()));
                    player.getCooldowns().addCooldown(ModItems.MONITOR_RECOVERY,
                            GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.MONITOR_BROKEN, 0));
                    return true;
                }
            });
        }

        // ==================== 鹈鹕商店 ====================
        {
            // 开锁器 - 150金币
            PELICAN_SHOP.add(new ShopEntry(
                    TMMItems.LOCKPICK.getDefaultInstance(),
                    150,
                    ShopEntry.Type.TOOL));
        }

        // ==================== 教父商店 ====================
        {
            // 开锁器 - 150金币
            GODFATHER_SHOP.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 150, ShopEntry.Type.TOOL));
            // 子弹 - 275金币（右键装填或购买时自动装填）
            GODFATHER_SHOP.add(new ShopEntry(
                    ModItems.BULLET.getDefaultInstance(),
                    275,
                    ShopEntry.Type.WEAPON) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    return org.agmas.noellesroles.game.roles.neutral.mafia.MafiaManager
                            .tryLoadBullet((ServerPlayer) player);
                }
            });
        }

        // ==================== 家族教徒商店 ====================
        {
            // 刀 - 200金币
            MAFIOSO_SHOP.add(new KillerKnifeShopEntry(200));
            // 左轮手枪 - 300金币
            MAFIOSO_SHOP.add(new ShopEntry(TMMItems.REVOLVER.getDefaultInstance(), 300, ShopEntry.Type.WEAPON));
        }

        // ==================== 家族侍卫商店 ====================
        {
            // 飞刀 - 225金币
            JANITOR_SHOP.add(new ShopEntry(ModItems.THROWING_KNIFE.getDefaultInstance(), 225, ShopEntry.Type.WEAPON));
            // 关灯 - 200金币
            JANITOR_SHOP.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), 200, ShopEntry.Type.TOOL) {
                public boolean onBuy(@NotNull Player player) {
                    return SREPlayerShopComponent.useBlackout(player);
                }
            });
            // 短管霰弹枪 - 250金币
            JANITOR_SHOP.add(new ShopEntry(ModItems.SHORT_SHOTGUN.getDefaultInstance(), 250, ShopEntry.Type.WEAPON));
        }

        // ==================== 家族保姆商店 ====================
        {
            // 护盾试剂 - 325金币
            NUTRITIONIST_SHOP
                    .add(new ShopEntry(TMMItems.DEFENSE_VIAL.getDefaultInstance(), 325, ShopEntry.Type.WEAPON));
            // 毒药试剂 - 200金币
            NUTRITIONIST_SHOP.add(new ShopEntry(TMMItems.POISON_VIAL.getDefaultInstance(), 200, ShopEntry.Type.WEAPON));
            // 喷溅型速度3 - 275金币 (持续5秒)
            {
                var SPEED_SPLASH = Items.SPLASH_POTION.getDefaultInstance();
                var speedList = List.of(new MobEffectInstance(
                        MobEffects.MOVEMENT_SPEED,
                        5 * 20,
                        2,
                        false,
                        true,
                        true));
                var speedContent = new PotionContents(Optional.empty(), Optional.of(53503),
                        speedList);
                SPEED_SPLASH.set(DataComponents.POTION_CONTENTS, speedContent);
                NUTRITIONIST_SHOP.add(new ShopEntry(SPEED_SPLASH, 275, ShopEntry.Type.WEAPON));
            }
            // 喷溅型禁止移动药水 - 275金币 (持续2.5秒)
            {
                var IMMOBILE_SPLASH = Items.SPLASH_POTION.getDefaultInstance();
                var immobileList = List.of(new MobEffectInstance(
                        ModEffects.MOVE_BANED,
                        50, // 2.5秒 = 50 ticks
                        0,
                        false,
                        true,
                        true));
                var immobileContent = new PotionContents(Optional.empty(), Optional.of(0x8B0000),
                        immobileList);
                IMMOBILE_SPLASH.set(DataComponents.POTION_CONTENTS, immobileContent);
                NUTRITIONIST_SHOP.add(new ShopEntry(IMMOBILE_SPLASH, 275, ShopEntry.Type.WEAPON));
            }
            // 喷溅型无碰撞药水 - 175金币 (持续20秒)
            {
                var NOCLIP_SPLASH = Items.SPLASH_POTION.getDefaultInstance();
                var noclipList = List.of(new MobEffectInstance(
                        ModEffects.NO_COLLIDE,
                        20 * 20, // 20秒
                        0,
                        false,
                        true,
                        true));
                var noclipContent = new PotionContents(Optional.empty(), Optional.of(0x00FF7F),
                        noclipList);
                NOCLIP_SPLASH.set(DataComponents.POTION_CONTENTS, noclipContent);
                NUTRITIONIST_SHOP.add(new ShopEntry(NOCLIP_SPLASH, 175, ShopEntry.Type.WEAPON));
            }
            // 喷溅型无限体力药水 - 200金币 (持续15秒)
            {
                var STAMINA_SPLASH = Items.SPLASH_POTION.getDefaultInstance();
                var staminaList = List.of(new MobEffectInstance(
                        ModEffects.INFINITE_STAMINA,
                        15 * 20, // 15秒
                        0,
                        false,
                        true,
                        true));
                var staminaContent = new PotionContents(Optional.empty(), Optional.of(0x00CED1),
                        staminaList);
                STAMINA_SPLASH.set(DataComponents.POTION_CONTENTS, staminaContent);
                NUTRITIONIST_SHOP.add(new ShopEntry(STAMINA_SPLASH, 200, ShopEntry.Type.WEAPON));
            }
        }

        // ==================== 家族保护伞商店 ====================
        {
            // 手榴弹 - 450金币
            PARASOL_SHOP.add(new ShopEntry(TMMItems.GRENADE.getDefaultInstance(), 450, ShopEntry.Type.WEAPON));
            // 烟雾弹 - 175金币
            PARASOL_SHOP.add(new ShopEntry(ModItems.SMOKE_GRENADE.getDefaultInstance(), 175, ShopEntry.Type.WEAPON));
            // 闪光弹 - 125金币
            PARASOL_SHOP.add(new ShopEntry(ModItems.FLASH_GRENADE.getDefaultInstance(), 125, ShopEntry.Type.WEAPON));
        }

        // ==================== 咒法师商店 ====================
        {
            // 刀 - 130金币
            WARLOCK_SHOP.add(new KillerKnifeShopEntry(130));
            // 撬棍 - 35金币
            WARLOCK_SHOP.add(new ShopEntry(TMMItems.CROWBAR.getDefaultInstance(), 35, ShopEntry.Type.TOOL));
            // 开锁器 - 80金币
            WARLOCK_SHOP.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 80, ShopEntry.Type.TOOL));
            // 疯狂模式 - 400金币（冷却与通用杀手商店一致）
            WARLOCK_SHOP.add(new ShopEntry(TMMItems.PSYCHO_MODE.getDefaultInstance(), 400, ShopEntry.Type.WEAPON) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    return SREPlayerShopComponent.usePsychoMode(player);
                }
            });
            // 关灯 - 使用配置价格
            WARLOCK_SHOP.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), SREConfig.instance().blackoutPrice,
                    ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    return SREPlayerShopComponent.useBlackout(player);
                }
            });
            // 监控失灵 - 60金币
            WARLOCK_SHOP.add(new ShopEntry(TMMItems.MONITOR_BROKEN.getDefaultInstance(), 60, ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    return SREPlayerShopComponent.useMonitorBroken(player,
                            SREConfig.instance().monitorBrokenDuration * 20);
                }
            });
        }

        // ==================== 嬉命人商店 ====================
        {
            // 开锁器 - 100金币
            EMBALMER_SHOP.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 100, ShopEntry.Type.TOOL));
        }

        // ==================== Dream（梦魇）商店 ====================
        {
            var dreamConfig = NoellesRolesConfig.HANDLER.instance();
            // 铁斧 - 135金币，12耐久，第二次购买半价（动态价格，同杀手刀首购折扣）
            DREAM_SHOP.add(new ShopEntry(ModItems.DREAM_AXE.getDefaultInstance(),
                    dreamConfig.dreamAxePrice, ShopEntry.Type.WEAPON) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    if (!SREItemUtils.insertStackInFreeSlot(player, this.stack().copy())) {
                        return false;
                    }
                    DynamicShopComponent dynamicShop = DynamicShopComponent.KEY.get(player);
                    ResourceLocation axeId = BuiltInRegistries.ITEM.getKey(ModItems.DREAM_AXE);
                    if (dynamicShop.getPurchaseCount(axeId) == 0) {
                        // 首购后挂 -50%：从第二把起半价
                        dynamicShop.setPercentDiscount(axeId, 50);
                    }
                    dynamicShop.recordPurchase(axeId);
                    return true;
                }
            });
            // 巨幕面具 - 350金币：购买即进入狂暴（Psycho 逻辑，不给球棒/面具），冷却挂在面具物品上
            ItemStack maskDisplay = ModItems.DREAM_MASK.getDefaultInstance();
            var maskLore = new ArrayList<Component>();
            maskLore.add(Component.translatable("item.noellesroles.dream_mask.lore1")
                    .setStyle(Style.EMPTY.withItalic(false)).withStyle(ChatFormatting.GRAY));
            maskLore.add(Component.translatable("item.noellesroles.dream_mask.lore2")
                    .setStyle(Style.EMPTY.withItalic(false)).withStyle(ChatFormatting.GRAY));
            maskLore.add(Component.translatable("item.noellesroles.dream_mask.lore3")
                    .setStyle(Style.EMPTY.withItalic(false)).withStyle(ChatFormatting.GRAY));
            maskDisplay.set(DataComponents.LORE, new ItemLore(maskLore));
            DREAM_SHOP.add(new ShopEntry(maskDisplay, dreamConfig.dreamMaskPrice, ShopEntry.Type.WEAPON) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)) {
                        return false;
                    }
                    return org.agmas.noellesroles.game.roles.killer.dream.DreamPlayerComponent
                            .activateMaskBerserk(sp);
                }
            });
            // 钻石镐 - 90金币（右键像开锁器一样开门；shift撬门50s冷却；无法锁门）
            DREAM_SHOP.add(new ShopEntry(ModItems.DREAM_PICKAXE.getDefaultInstance(),
                    dreamConfig.dreamPickaxePrice, ShopEntry.Type.TOOL));
            // 船 - 100金币（强制乘坐陷阱，60s冷却）
            DREAM_SHOP.add(new ShopEntry(ModItems.DREAM_BOAT.getDefaultInstance(),
                    dreamConfig.dreamBoatPrice, ShopEntry.Type.TOOL));
            // 范围关灯 - 150金币：购买即以自己为中心熄灭半径30格的灯，冷却与普通关灯一致
            ItemStack blackoutDisplay = ModItems.DREAM_BLACKOUT.getDefaultInstance();
            var blackoutLore = new ArrayList<Component>();
            blackoutLore.add(Component.translatable("item.noellesroles.dream_blackout.lore1")
                    .setStyle(Style.EMPTY.withItalic(false)).withStyle(ChatFormatting.GRAY));
            blackoutLore.add(Component.translatable("item.noellesroles.dream_blackout.lore2")
                    .setStyle(Style.EMPTY.withItalic(false)).withStyle(ChatFormatting.GRAY));
            blackoutDisplay.set(DataComponents.LORE, new ItemLore(blackoutLore));
            DREAM_SHOP.add(new ShopEntry(blackoutDisplay, dreamConfig.dreamBlackoutPrice, ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    if (player.getCooldowns().isOnCooldown(ModItems.DREAM_BLACKOUT)) {
                        return false;
                    }
                    SREWorldBlackoutComponent blackout = SREWorldBlackoutComponent.KEY.get(player.level());
                    if (blackout.isBlackoutActive()) {
                        return false;
                    }
                    blackout.triggerBlackout(player.blockPosition(),
                            NoellesRolesConfig.HANDLER.instance().dreamBlackoutRadius, true,
                            SREWorldBlackoutComponent.getMaxDuration(player.level()));
                    // 冷却与普通关灯一致
                    player.getCooldowns().addCooldown(ModItems.DREAM_BLACKOUT,
                            Math.max(60 * 20, GameConstants.getBlackoutCooldownGlobal()));
                    return true;
                }
            });
        }

        // ==================== 影杀者商店（WRAITH_ASSASSIN） ====================
        {
            {
                ItemStack s = new ItemStack(Items.SCULK);
                s.set(DataComponents.ITEM_NAME,
                        Component.translatable("item.noellesroles.wraith_assassin.energy_exchange"));
                WRAITH_ASSASSIN_SHOP.add(new ShopEntry(s, 25, ShopEntry.Type.TOOL) {
                    @Override
                    public boolean onBuy(@NotNull Player player) {
                        if (!(player instanceof ServerPlayer sp)) {
                            return false;
                        }
                        WraithAssassinPlayerComponent.KEY.get(sp).addEnergy(25);
                        WraithAssassinPlayerComponent.KEY.get(sp).playConversionCue(sp);
                        sp.displayClientMessage(
                                Component.translatable("message.noellesroles.wraith_assassin.energy_gain", 25)
                                        .withStyle(ChatFormatting.DARK_AQUA),
                                true);
                        return true;
                    }
                });
            }
            WRAITH_ASSASSIN_SHOP
                    .add(new ShopEntry(ModItems.INFERIOR_LOCKPICK.getDefaultInstance(), 25, ShopEntry.Type.TOOL));
            {
                ItemStack s = new ItemStack(Items.ECHO_SHARD);
                s.set(DataComponents.ITEM_NAME, Component.translatable("item.noellesroles.wraith_assassin.san_drain"));
                WRAITH_ASSASSIN_SHOP.add(new ShopEntry(s, 80, ShopEntry.Type.TOOL) {
                    @Override
                    public boolean onBuy(@NotNull Player player) {
                        return player instanceof ServerPlayer sp
                                && WraithAssassinPlayerComponent.KEY.get(sp).buyDrain(sp);
                    }
                });
            }
        }

        // ==================== 推理师商店 ====================
        {
            // 罗盘 - 100金币（游戏时间>=2分钟且没有罗盘时才能购买）
            REASONER_SHOP.add(new ShopEntry(ModItems.REASONER_COMPASS.getDefaultInstance(), 100, ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    var timeComp = io.wifi.starrailexpress.cca.SREGameTimeComponent.KEY.get(player.level());
                    int elapsed = timeComp.getResetTime() - timeComp.getTime();
                    if (elapsed < 2 * 60 * 20) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.reasoner.shop.time_not_ready")
                                        .withStyle(ChatFormatting.YELLOW),
                                true);
                        return false;
                    }
                    for (ItemStack stack : player.getInventory().items) {
                        if (stack.is(ModItems.REASONER_COMPASS)) {
                            player.displayClientMessage(
                                    Component.translatable("message.noellesroles.reasoner.shop.already_has_compass")
                                            .withStyle(ChatFormatting.YELLOW),
                                    true);
                            return false;
                        }
                    }
                    return true;
                }
            });
        }

        // ==================== 幻音师商店 ====================
        {
            // 出刀的声音 - 50金币, 冷却30秒
            {
                ItemStack s = new ItemStack(Items.NOTE_BLOCK);
                s.set(DataComponents.ITEM_NAME,
                        Component.translatable("item.noellesroles.phantom_musician.knife_sound"));
                PHANTOM_MUSICIAN_SHOP.add(new ShopEntry(s, 50, ShopEntry.Type.TOOL) {
                    @Override
                    public boolean onBuy(@NotNull Player p) {
                        var c = org.agmas.noellesroles.game.roles.neutral.phantom_musician.PhantomMusicianPlayerComponent.KEY
                                .get(p);
                        if (c.knifeSoundCooldown > 0)
                            return false;
                        c.knifeSoundCooldown = PhantomMusicianPlayerComponent.KNIFE_SOUND_COOLDOWN;
                        c.sync();
                        p.level().playSound(null, p.blockPosition(), TMMSounds.ITEM_KNIFE_STAB, SoundSource.PLAYERS, 1F,
                                1F);
                        return true;
                    }
                });
            }
            // 左轮手枪开火的声音 - 75金币, 冷却30秒
            {
                ItemStack s = new ItemStack(Items.NOTE_BLOCK);
                s.set(DataComponents.ITEM_NAME,
                        Component.translatable("item.noellesroles.phantom_musician.revolver_sound"));
                PHANTOM_MUSICIAN_SHOP.add(new ShopEntry(s, 75, ShopEntry.Type.TOOL) {
                    @Override
                    public boolean onBuy(@NotNull Player p) {
                        var c = org.agmas.noellesroles.game.roles.neutral.phantom_musician.PhantomMusicianPlayerComponent.KEY
                                .get(p);
                        if (c.revolverSoundCooldown > 0)
                            return false;
                        c.revolverSoundCooldown = PhantomMusicianPlayerComponent.REVOLVER_SOUND_COOLDOWN;
                        c.sync();
                        p.level().playSound(null, p.blockPosition(), TMMSounds.ITEM_REVOLVER_SHOOT, SoundSource.PLAYERS,
                                1F, 1F);
                        return true;
                    }
                });
            }
            // 潜行者觉醒的声音 - 100金币, 冷却120秒, MASTER全场
            {
                ItemStack s = new ItemStack(Items.NOTE_BLOCK);
                s.set(DataComponents.ITEM_NAME,
                        Component.translatable("item.noellesroles.phantom_musician.stalker_sound"));
                PHANTOM_MUSICIAN_SHOP.add(new ShopEntry(s, 100, ShopEntry.Type.TOOL) {
                    @Override
                    public boolean onBuy(@NotNull Player p) {
                        var c = org.agmas.noellesroles.game.roles.neutral.phantom_musician.PhantomMusicianPlayerComponent.KEY
                                .get(p);
                        if (c.stalkerSoundCooldown > 0)
                            return false;
                        c.stalkerSoundCooldown = PhantomMusicianPlayerComponent.STALKER_SOUND_COOLDOWN;
                        c.sync();
                        if (p instanceof ServerPlayer sp)
                            for (var pp : sp.serverLevel().players())
                                if (pp != null)
                                    pp.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.MASTER, 1F, 1.5F);
                        return true;
                    }
                });
            }
            // 疯狂模式的声音 - 350金币, 冷却5分钟, 持续播放30秒
            {
                ItemStack s = new ItemStack(Items.NOTE_BLOCK);
                s.set(DataComponents.ITEM_NAME,
                        Component.translatable("item.noellesroles.phantom_musician.psycho_sound"));
                PHANTOM_MUSICIAN_SHOP.add(new ShopEntry(s, 350, ShopEntry.Type.TOOL) {
                    @Override
                    public boolean onBuy(@NotNull Player p) {
                        var c = org.agmas.noellesroles.game.roles.neutral.phantom_musician.PhantomMusicianPlayerComponent.KEY
                                .get(p);
                        if (c.psychoSoundCooldown > 0)
                            return false;
                        c.psychoSoundCooldown = PhantomMusicianPlayerComponent.PSYCHO_SOUND_COOLDOWN;
                        c.psychoSoundPlayTimer = PhantomMusicianPlayerComponent.PSYCHO_SOUND_PLAY_DURATION;

                        p.level().playSound(null, p.blockPosition(),
                                io.wifi.starrailexpress.index.TMMSounds.AMBIENT_PSYCHO_DRONE, SoundSource.PLAYERS, 0.5F,
                                1F);
                        c.sync();
                        return true;
                    }
                });
            }
            // 撬棍撬门的声音 - 25金币, 冷却1分钟
            {
                ItemStack s = new ItemStack(Items.NOTE_BLOCK);
                s.set(DataComponents.ITEM_NAME,
                        Component.translatable("item.noellesroles.phantom_musician.crowbar_sound"));
                PHANTOM_MUSICIAN_SHOP.add(new ShopEntry(s, 25, ShopEntry.Type.TOOL) {
                    @Override
                    public boolean onBuy(@NotNull Player p) {
                        var c = org.agmas.noellesroles.game.roles.neutral.phantom_musician.PhantomMusicianPlayerComponent.KEY
                                .get(p);
                        if (c.crowbarSoundCooldown > 0)
                            return false;
                        c.crowbarSoundCooldown = PhantomMusicianPlayerComponent.CROWBAR_SOUND_COOLDOWN;
                        c.sync();
                        p.level().playSound(null, p.blockPosition(), TMMSounds.ITEM_CROWBAR_PRY, SoundSource.PLAYERS,
                                1F, 1F);
                        return true;
                    }
                });
            }
            // 随机播放音效 - 50金币, 冷却40秒, 图标为音乐唱片
            {
                ItemStack s = new ItemStack(Items.MUSIC_DISC_RELIC);
                s.set(DataComponents.ITEM_NAME,
                        Component.translatable("item.noellesroles.phantom_musician.random_sound"));
                PHANTOM_MUSICIAN_SHOP.add(new ShopEntry(s, 50, ShopEntry.Type.TOOL) {
                    private final java.util.List<Object> allSounds = java.util.List.of(
                            // === TMMSounds (starrailexpress) ===
                            TMMSounds.ITEM_KNIFE_STAB, TMMSounds.ITEM_KNIFE_PREPARE,
                            TMMSounds.ITEM_REVOLVER_SHOOT, TMMSounds.ITEM_REVOLVER_CLICK,
                            TMMSounds.ITEM_CROWBAR_PRY, TMMSounds.ITEM_SNIPER_RIFLE_SHOOT,
                            TMMSounds.ITEM_SNIPER_RIFLE_RELOAD, TMMSounds.ITEM_DERRINGER_RELOAD,
                            TMMSounds.ITEM_GRENADE_THROW, TMMSounds.ITEM_GRENADE_EXPLODE,
                            TMMSounds.ITEM_BAT_HIT, TMMSounds.ITEM_PSYCHO_ARMOUR,
                            TMMSounds.ITEM_SCOPE_ATTACH, TMMSounds.ITEM_SCOPE_DETACH,
                            TMMSounds.ITEM_LOCKPICK_DOOR, TMMSounds.ITEM_KEY_DOOR,
                            TMMSounds.BLOCK_DOOR_LOCKED, TMMSounds.BLOCK_DOOR_TOGGLE,
                            TMMSounds.BLOCK_CARGO_BOX_OPEN, TMMSounds.BLOCK_CARGO_BOX_CLOSE,
                            TMMSounds.BLOCK_LIGHT_TOGGLE, TMMSounds.BLOCK_SPRINKLER_RUN,
                            TMMSounds.BLOCK_PRIVACY_PANEL_TOGGLE, TMMSounds.BLOCK_SPACE_BUTTON_TOGGLE,
                            TMMSounds.BLOCK_BUTTON_TOGGLE_NO_POWER,
                            TMMSounds.UI_SHOP_BUY, TMMSounds.UI_SHOP_BUY_FAIL,
                            TMMSounds.UI_PIANO, TMMSounds.UI_PIANO_WIN,
                            TMMSounds.UI_PIANO_LOSE, TMMSounds.UI_PIANO_STINGER,
                            TMMSounds.UI_RISER,
                            TMMSounds.AMBIENT_TRAIN_HORN, TMMSounds.AMBIENT_TRAIN_INSIDE,
                            TMMSounds.AMBIENT_TRAIN_OUTSIDE, TMMSounds.AMBIENT_PSYCHO_DRONE,
                            TMMSounds.AMBIENT_BLACKOUT,
                            // === NRSounds (noellesroles) ===
                            NRSounds.SHOTGUN_FIRE, NRSounds.SHOTGUNU_COCK,
                            NRSounds.SHORT_CIRCUIT, NRSounds.BEEP, NRSounds.C4_BEEP,
                            NRSounds.SYRINGE_STAB, NRSounds.INFECTED_COUGH, NRSounds.INFECTED_INFECT,
                            NRSounds.MAFIA, NRSounds.PARTY_SKILL,
                            NRSounds.TIME_STOP, NRSounds.TIME_START, NRSounds.DIO_SPAWN,
                            NRSounds.WIND, NRSounds.GAMBER_DEATH, NRSounds.MUSIC_CLOCK,
                            NRSounds.GONGXI_FACAI, NRSounds.TO_BE_CONTINUED,
                            NRSounds.HARPY_WELCOME, NRSounds.JESTER_AMBIENT,
                            NRSounds.NYAN_CAT, NRSounds.THMUSIC_UN_OWEN, NRSounds.BAKA_BAKA,
                            // === Vanilla SoundEvents ===
                            SoundEvents.WITHER_SPAWN, SoundEvents.WITHER_DEATH,
                            SoundEvents.LIGHTNING_BOLT_THUNDER, SoundEvents.ENDER_DRAGON_GROWL,
                            SoundEvents.ENDER_DRAGON_DEATH, SoundEvents.EVOKER_PREPARE_SUMMON,
                            SoundEvents.EVOKER_CAST_SPELL, SoundEvents.GENERIC_EXPLODE,
                            SoundEvents.FIREWORK_ROCKET_LARGE_BLAST,
                            SoundEvents.WARDEN_ROAR, SoundEvents.WARDEN_HEARTBEAT,
                            SoundEvents.WARDEN_AGITATED, SoundEvents.GHAST_SCREAM,
                            SoundEvents.RAVAGER_ROAR, SoundEvents.ANVIL_DESTROY,
                            SoundEvents.ANVIL_PLACE, SoundEvents.ANVIL_USE,
                            SoundEvents.BEACON_POWER_SELECT, SoundEvents.CONDUIT_ATTACK_TARGET,
                            SoundEvents.TRIDENT_THROW, SoundEvents.TRIDENT_RETURN,
                            SoundEvents.SHIELD_BLOCK, SoundEvents.CHAIN_HIT,
                            SoundEvents.END_PORTAL_SPAWN, SoundEvents.IRON_GOLEM_REPAIR,
                            SoundEvents.SMITHING_TABLE_USE, SoundEvents.BELL_BLOCK,
                            SoundEvents.TOTEM_USE, SoundEvents.EXPERIENCE_ORB_PICKUP,
                            SoundEvents.PLAYER_BURP, SoundEvents.ITEM_BREAK,
                            SoundEvents.FLINTANDSTEEL_USE, SoundEvents.EGG_THROW,
                            SoundEvents.VILLAGER_YES, SoundEvents.TOTEM_USE,
                            SoundEvents.UI_BUTTON_CLICK, SoundEvents.UI_LOOM_TAKE_RESULT,
                            SoundEvents.NOTE_BLOCK_BELL, SoundEvents.NOTE_BLOCK_HARP,
                            SoundEvents.NOTE_BLOCK_BASEDRUM, SoundEvents.NOTE_BLOCK_PLING,
                            SoundEvents.RESPAWN_ANCHOR_DEPLETE, SoundEvents.GENERIC_DRINK,
                            SoundEvents.IRON_DOOR_OPEN, SoundEvents.PANDA_SNEEZE);

                    @Override
                    public boolean onBuy(@NotNull Player p) {
                        var c = org.agmas.noellesroles.game.roles.neutral.phantom_musician.PhantomMusicianPlayerComponent.KEY
                                .get(p);
                        if (c.randomSoundCooldown > 0)
                            return false;
                        c.randomSoundCooldown = PhantomMusicianPlayerComponent.RANDOM_SOUND_COOLDOWN;
                        c.sync();
                        Object obj = allSounds.get(new java.util.Random().nextInt(allSounds.size()));
                        net.minecraft.sounds.SoundEvent sound;
                        if (obj instanceof net.minecraft.sounds.SoundEvent se) {
                            sound = se;
                        } else if (obj instanceof net.minecraft.core.Holder<?> h
                                && h.value() instanceof net.minecraft.sounds.SoundEvent se2) {
                            sound = se2;
                        } else {
                            return false;
                        }
                        p.level().playSound(null, p.blockPosition(), sound, SoundSource.PLAYERS, 1F, 1F);
                        return true;
                    }
                });
            }
        }

        // 正片开始 (Spellbreaker)
        {
            var SHOP = new ArrayList<ShopEntry>();
            SHOP.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), SREConfig.instance().lockpickPrice,
                    ShopEntry.Type.TOOL));
            SHOP.add(
                    new ShopEntry(TMMItems.CROWBAR.getDefaultInstance(), SREConfig.instance().crowbarPrice,
                            ShopEntry.Type.TOOL));
            SHOP.add(
                    new KillerKnifeShopEntry(SREConfig.instance().knifePrice));
            SHOP.add(new ShopEntry(TMMItems.REVOLVER.getDefaultInstance(), SREConfig.instance().revolverPrice,
                    ShopEntry.Type.WEAPON));
            SHOP.add(new ShopEntry(ModItems.SHORT_SHOTGUN.getDefaultInstance(), SREConfig.instance().shortShotgunPrice,
                    ShopEntry.Type.WEAPON));
            SHOP.add(new ShopEntry(TMMItems.GRENADE.getDefaultInstance(), SREConfig.instance().grenadePrice,
                    ShopEntry.Type.WEAPON));
            SHOP.add(new ShopEntry(ModItems.SPELLBREAKER_POTION.getDefaultInstance(), 75, ShopEntry.Type.TOOL));
            SHOP.add(new ShopEntry(ModItems.SILENCE_TOTEM.getDefaultInstance(), 130, ShopEntry.Type.TOOL));
            // 关灯 - 使用配置价格
            SHOP.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), SREConfig.instance().blackoutPrice,
                    ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    return SREPlayerShopComponent.useBlackout(player);
                }
            });
            ShopContent.customEntries.put(ModRoles.SPELLBREAKER.getIdentifier(), SHOP);
        }

        // 布袋鬼商店（诡舍·缚灵）
        {
            // 设计要求：无法购买刀、枪、狂暴模式，只能购买强化领域的道具
            var MA_CHEN_XU_SHOP = new ArrayList<ShopEntry>();

            // 诡舍·浊雨 - 100金币
            // 效果：30秒小雨，恐惧范围外好人每5秒掉3SAN，可与大招叠加
            ItemStack turbidRainItem = Items.BARRIER.getDefaultInstance();
            turbidRainItem.set(DataComponents.ITEM_NAME,
                    Component.translatable("item.noellesroles.ma_chen_xu.turbid_rain")
                            .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD));
            var turbidRainLore = new ArrayList<Component>();
            turbidRainLore.add(Component.translatable("item.noellesroles.ma_chen_xu.turbid_rain.lore1")
                    .setStyle(Style.EMPTY.withItalic(false))
                    .withStyle(ChatFormatting.GRAY));
            turbidRainLore.add(Component.translatable("item.noellesroles.ma_chen_xu.turbid_rain.lore2")
                    .setStyle(Style.EMPTY.withItalic(false))
                    .withStyle(ChatFormatting.GRAY));
            turbidRainItem.set(DataComponents.LORE, new ItemLore(turbidRainLore));

            MA_CHEN_XU_SHOP.add(new ShopEntry(turbidRainItem, 180, ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    var component = org.agmas.noellesroles.component.ModComponents.MA_CHEN_XU.get(player);
                    if (component != null) {
                        return component.useTurbidRain();
                    }
                    return false;
                }
            });

            // 诡舍·镇魂铃 - 150金币
            // 效果：20格AoE，好人获得10秒耳鸣效果
            ItemStack soulBellItem = Items.BELL.getDefaultInstance();
            soulBellItem.set(DataComponents.ITEM_NAME,
                    Component.translatable("item.noellesroles.ma_chen_xu.soul_bell")
                            .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
            var soulBellLore = new ArrayList<Component>();
            soulBellLore.add(Component.translatable("item.noellesroles.ma_chen_xu.soul_bell.lore1")
                    .setStyle(Style.EMPTY.withItalic(false))
                    .withStyle(ChatFormatting.GRAY));
            soulBellLore.add(Component.translatable("item.noellesroles.ma_chen_xu.soul_bell.lore2")
                    .setStyle(Style.EMPTY.withItalic(false))
                    .withStyle(ChatFormatting.GRAY));
            soulBellItem.set(DataComponents.LORE, new ItemLore(soulBellLore));

            MA_CHEN_XU_SHOP.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 75, ShopEntry.Type.TOOL));
            MA_CHEN_XU_SHOP.add(new ShopEntry(soulBellItem, 105, ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    var component = org.agmas.noellesroles.component.ModComponents.MA_CHEN_XU.get(player);
                    if (component != null) {
                        return component.useSoulBell();
                    }
                    return false;
                }
            });

            ShopContent.customEntries.put(ModRoles.MA_CHEN_XU.getIdentifier(), MA_CHEN_XU_SHOP);
        }

        // FURANDORU的商店
        {
            var SHOP = new ArrayList<ShopEntry>();
            SHOP.add(new ShopEntry(
                    TMMItems.KNIFE.getDefaultInstance(),
                    130,
                    ShopEntry.Type.TOOL));
            SHOP.add(new ShopEntry(ModItems.FAKE_REVOLVER.getDefaultInstance(), 50,
                    ShopEntry.Type.TOOL));
            SHOP.add(new ShopEntry(TMMItems.DERRINGER.getDefaultInstance(), 400,
                    ShopEntry.Type.TOOL));
            SHOP.add(new ShopEntry(TMMItems.BODY_BAG.getDefaultInstance(), SREConfig.instance().bodyBagPrice,
                    ShopEntry.Type.TOOL));
            SHOP.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), SREConfig.instance().blackoutPrice,
                    ShopEntry.Type.TOOL) {
                public boolean onBuy(@NotNull Player player) {
                    player.getCooldowns().addCooldown(TMMItems.BLACKOUT,
                            60 * 20);
                    boolean triggered = ((SREWorldBlackoutComponent) SREWorldBlackoutComponent.KEY
                            .get(player.level()))
                            .triggerBlackout();
                    if (triggered) {
                        SRE.REPLAY_MANAGER.recordSkillUsed(player.getUUID(),
                                BuiltInRegistries.ITEM.getKey(TMMItems.BLACKOUT));
                    }
                    return triggered;
                }
            });

            SHOP.add(new ShopEntry(Items.WIND_CHARGE.getDefaultInstance(), 50,
                    ShopEntry.Type.TOOL));
            ShopContent.customEntries.put(RedHouseRoles.FURANDORU.getIdentifier(), SHOP);
        }

        // 滞时鬼（Delayer）商店
        {
            var SHOP = new ArrayList<ShopEntry>();
            SHOP.add(new KillerKnifeShopEntry(130));
            SHOP.add(new ShopEntry(io.wifi.starrailexpress.index.TMMItems.REVOLVER.getDefaultInstance(), 285,
                    ShopEntry.Type.TOOL));
            SHOP.add(new ShopEntry(org.agmas.noellesroles.init.ModItems.SHORT_SHOTGUN.getDefaultInstance(), 300,
                    ShopEntry.Type.TOOL));
            SHOP.add(new ShopEntry(TMMItems.PSYCHO_MODE.getDefaultInstance(), 400, ShopEntry.Type.WEAPON) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    return SREPlayerShopComponent.usePsychoMode(player);
                }
            });
            SHOP.add(new ShopEntry(TMMItems.MONITOR_BROKEN.getDefaultInstance(), 40, ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    return SREPlayerShopComponent.useMonitorBroken(player,
                            SREConfig.instance().monitorBrokenDuration * 20);
                }
            });
            // 关灯 - 使用配置价格
            SHOP.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), SREConfig.instance().blackoutPrice,
                    ShopEntry.Type.TOOL) {
                public boolean onBuy(@NotNull Player player) {
                    return SREPlayerShopComponent.useBlackout(player);
                }
            });
            SHOP.add(new ShopEntry(ModItems.CAMERA_SHEARS.getDefaultInstance(), 25, ShopEntry.Type.TOOL));
            SHOP.add(new ShopEntry(TMMItems.FIRECRACKER.getDefaultInstance(), 15, ShopEntry.Type.TOOL));
            SHOP.add(new ShopEntry(TMMItems.CROWBAR.getDefaultInstance(), 35, ShopEntry.Type.TOOL));
            SHOP.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 80, ShopEntry.Type.TOOL));

            ShopContent.customEntries.put(ModRoles.DELAYER.getIdentifier(), SHOP);
        }

        // 大明星的商店 (Superstar)
        {
            var SHOP = new ArrayList<ShopEntry>();
            SHOP.add(new ShopEntry(ModItems.SIGNATURE_PAPER.getDefaultInstance(), 100, ShopEntry.Type.TOOL));
            ShopContent.customEntries.put(ModRoles.SUPERSTAR.getIdentifier(), SHOP);
        }
        // 大妖精的商店
        {
            var SHOP = new ArrayList<ShopEntry>();
            SHOP.add(new ShopEntry(ModItems.CALMING_TEA.getDefaultInstance(), 200, ShopEntry.Type.TOOL));
            SHOP.add(new ShopEntry(ModItems.SHILIJIA.getDefaultInstance(), 200, ShopEntry.Type.TOOL));
            ShopContent.customEntries.put(RedHouseRoles.DAIYOUSEI_ID, SHOP);
        }
        // INITIATE的商店
        {
            var SHOP = new ArrayList<ShopEntry>();
            SHOP.add(new KillerKnifeShopEntry(200));
            ShopContent.customEntries.put(SERoles.INITIATE.getIdentifier(), SHOP);
        }

        // BAKA的商店 (RedHouse)
        {
            var SHOP = new ArrayList<ShopEntry>();
            SHOP.add(new ShopEntry(FunnyItems.PROBLEM_SET.getDefaultInstance(), 100, ShopEntry.Type.TOOL));
            ShopContent.customEntries.put(RedHouseRoles.BAKA.getIdentifier(), SHOP);
        }

        // EXAMPLER的商店
        {
            var SHOP = new ArrayList<ShopEntry>();
            SHOP.add(new KillerKnifeShopEntry(

                    120));
            SHOP.add(new ShopEntry(
                    io.wifi.starrailexpress.index.TMMItems.REVOLVER.getDefaultInstance(),
                    400,
                    ShopEntry.Type.TOOL));
            SHOP.add(new ShopEntry(
                    io.wifi.starrailexpress.index.TMMItems.LOCKPICK.getDefaultInstance(),
                    50,
                    ShopEntry.Type.TOOL));
            SHOP.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), SREConfig.instance().blackoutPrice,
                    ShopEntry.Type.TOOL) {
                public boolean onBuy(@NotNull Player player) {
                    player.getCooldowns().addCooldown(TMMItems.BLACKOUT,
                            Math.min((Integer) GameConstants.ITEM_COOLDOWNS
                                    .getOrDefault(TMMItems.BLACKOUT, 0), 60));
                    boolean triggered = ((SREWorldBlackoutComponent) SREWorldBlackoutComponent.KEY
                            .get(player.level()))
                            .triggerBlackout();
                    if (triggered) {
                        SRE.REPLAY_MANAGER.recordSkillUsed(player.getUUID(),
                                BuiltInRegistries.ITEM.getKey(TMMItems.BLACKOUT));
                    }
                    return triggered;
                }
            });
            var psychoItem = TMMItems.PSYCHO_MODE.getDefaultInstance();
            var examplerPsychoLore = new ItemLore(
                    List.of(Component.translatable(
                            "itemstack.exampler.shop.psychoitem.item_lore.1")));
            psychoItem.set(DataComponents.LORE, examplerPsychoLore);

            SHOP.add(new ShopEntry(psychoItem, 0, ShopEntry.Type.WEAPON) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    // 启动疯狂模式
                    if (SREItemUtils.countItem(player, TMMItems.PSYCHO_MODE) > 0) {
                        if (SREPlayerPsychoComponent.KEY.get(player).startPsycho()) {
                            SREItemUtils.clearItem(player, TMMItems.PSYCHO_MODE, 1);
                            return true;
                        }
                    }
                    return false;
                }
            });

            // 监控失灵 - 60金币（小镇做题家专属）
            SHOP.add(new ShopEntry(TMMItems.MONITOR_BROKEN.getDefaultInstance(), 60, ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    return SREPlayerShopComponent.useMonitorBroken(player,
                            SREConfig.instance().monitorBrokenDuration * 20);
                }
            });

            ShopContent.customEntries.put(ModRoles.EXAMPLER.getIdentifier(), SHOP);
        }

        // 广播员商店 - RADIO 可购买，150金币
        {
            var SHOP = new java.util.ArrayList<ShopEntry>();
            SHOP.add(new ShopEntry(ModItems.RADIO.getDefaultInstance(), 150, ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    return RoleUtils.insertStackInFreeSlot(player, ModItems.RADIO.getDefaultInstance().copy());
                }
            });
            ShopContent.customEntries.put(ModRoles.BROADCASTER.getIdentifier(), SHOP);
        }

        // 占卜家商店 - 晶球（水晶球）可购买
        {
            var SHOP = new java.util.ArrayList<ShopEntry>();
            SHOP.add(new ShopEntry(ModItems.CRYSTAL_BALL.getDefaultInstance(),
                    org.agmas.noellesroles.config.NoellesRolesConfig.HANDLER.instance().divinerCrystalBallPrice,
                    ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    return RoleUtils.insertStackInFreeSlot(player, ModItems.CRYSTAL_BALL.getDefaultInstance().copy());
                }
            });
            ShopContent.customEntries.put(ModRoles.DIVINER.getIdentifier(), SHOP);
        }

        // 老人的商店
        {
            var SHOP = new ArrayList<ShopEntry>();
            SHOP.add(new ShopEntry(ModItems.WHEELCHAIR.getDefaultInstance(), 150, ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    if (!oldmanEasterEggTriggeredInRound && player.getRandom().nextFloat() < 0.2f) {
                        var easterEggRod = createOldmanEasterEggRod();
                        boolean inserted = RoleUtils.insertStackInFreeSlot(player, easterEggRod);
                        if (inserted) {
                            oldmanEasterEggTriggeredInRound = true;
                            return true;
                        }
                        return false;
                    }
                    return super.onBuy(player);
                }
            });
            ShopContent.customEntries.put(ModRoles.OLDMAN.getIdentifier(), SHOP);
        }

        // 监察员的商店
        {
            var SHOP = new ArrayList<ShopEntry>();
            var displayer = Items.BARRIER.getDefaultInstance();
            displayer.set(DataComponents.ITEM_NAME,
                    Component.translatable("gui.noellesroles.monitor.cooldown_item")
                            .withStyle(ChatFormatting.RED));
            SHOP.add(new ShopEntry(displayer, 0, ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(Player player) {
                    return false;
                }
            });
            SHOP.add(new ShopEntry(ModItems.FLARE.getDefaultInstance(), 90, ShopEntry.Type.TOOL));
            ShopContent.customEntries.put(ModRoles.MONITOR.getIdentifier(), SHOP);
        }

        // 死灵法师/纵火者/猫死灵法师 商店 (共享列表)
        {
            var NECROMANCER_SHOP = new ArrayList<ShopEntry>();
            NECROMANCER_SHOP.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 100,
                    ShopEntry.Type.TOOL));
            ShopContent.customEntries.put(SERoles.NECROMANCER.getIdentifier(), NECROMANCER_SHOP);
            ShopContent.customEntries.put(SERoles.ARSONIST.getIdentifier(), NECROMANCER_SHOP);
            ShopContent.customEntries.put(BounsRoles.CAT_NECROMANCER.getIdentifier(), NECROMANCER_SHOP);

            {
                var CAT_KILLER_SHOP = new ArrayList<>(NECROMANCER_SHOP);
                // 只是给某些特定情况下启用，比如赌徒。
                CAT_KILLER_SHOP.add(new ShopEntry(TMMItems.PSYCHO_MODE.getDefaultInstance(), 0, ShopEntry.Type.WEAPON) {
                    @Override
                    public boolean onBuy(@NotNull Player player) {
                        return SREPlayerShopComponent.usePsychoMode(player, 1.1);
                    }
                });
                ShopContent.customEntries.put(BounsRoles.CAT_KILLER.getIdentifier(), CAT_KILLER_SHOP);
            }
        }

        // 忍者商店
        {
            var NINJA_SHOP = new ArrayList<ShopEntry>();

            // 苦无 - 130金币
            NINJA_SHOP.add(new ShopEntry(ModItems.NINJA_KNIFE.getDefaultInstance(), 130, ShopEntry.Type.WEAPON));

            // 手里剑 - 275金币
            NINJA_SHOP.add(new ShopEntry(ModItems.NINJA_SHURIKEN.getDefaultInstance(), 275, ShopEntry.Type.WEAPON));

            // 关灯 - 50金币
            NINJA_SHOP.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), 50, ShopEntry.Type.TOOL) {
                public boolean onBuy(@NotNull Player player) {
                    if (SREPlayerShopComponent.useBlackoutWithMultiplier(player, 0.4)) {
                        player.getCooldowns().addCooldown(TMMItems.BLACKOUT,
                                Math.max(GameConstants.getBlackoutCooldownGlobal(),
                                        GameConstants.ITEM_COOLDOWNS.get(TMMItems.BLACKOUT) / 5));
                        return true;
                    }
                    return false;
                }
            });

            // 撬锁器 - 75金币
            NINJA_SHOP.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 75, ShopEntry.Type.TOOL));

            ShopContent.customEntries.put(ModRoles.NINJA_ID, NINJA_SHOP);
        }

        // 厨师的商店
        {
            var shop = new ArrayList<ShopEntry>();
            shop.add(new ShopEntry(ModItems.A_BOTTLE_OF_WATER.getDefaultInstance(), 50,
                    ShopEntry.Type.TOOL));
            shop.add(new ShopEntry(ModItems.LINGSHI.getDefaultInstance(), 50, ShopEntry.Type.TOOL));
            shop.add(new ShopEntry(ModItems.PAN.getDefaultInstance(), 100, ShopEntry.Type.TOOL));
            ShopContent.customEntries.put(ModRoles.CHEF_ID, shop);
        }

        // 蛋糕师的商店
        {
            var shop = new ArrayList<ShopEntry>();
            shop.add(new ShopEntry(ModItems.CAKE_INGREDIENTS.getDefaultInstance(), 100, ShopEntry.Type.TOOL));
            shop.add(new ShopEntry(Items.SMOKER.getDefaultInstance(), 100, ShopEntry.Type.TOOL) {
                @Override
                public boolean canBuy(Player player) {
                    for (int i = 0; i < 9; i++)
                        if (player.getInventory().getItem(i).is(Items.SMOKER))
                            return false;
                    return true;
                }
            });
            ShopContent.customEntries.put(ModRoles.CAKE_MAKER_ID, shop);
        }

        // 指挥官的商店
        {
            var _SHOP = new ArrayList<ShopEntry>();
            _SHOP.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 100, ShopEntry.Type.TOOL));
            ShopContent.customEntries.put(ModRoles.COMMANDER_ID, _SHOP);
        }

        // 雇佣兵商店
        {
            var shop = new ArrayList<ShopEntry>();

            // 未签订契约 - 75金币
            shop.add(new ShopEntry(ModItems.MERCENARY_CONTRACT.getDefaultInstance(), 75, ShopEntry.Type.TOOL));

            // 护盾层 - 150金币
            ItemStack shieldItem = Items.SHIELD.getDefaultInstance();
            shieldItem.set(DataComponents.ITEM_NAME,
                    Component.translatable("item.noellesroles.mercenary_guard").withStyle(ChatFormatting.AQUA));
            shop.add(new ShopEntry(shieldItem, 150, ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    var gameWorld = SREGameWorldComponent.KEY.get(player.level());
                    if (!gameWorld.isRole(player, ModRoles.MERCENARY)) {
                        return false;
                    }
                    var mercenary = MercenaryPlayerComponent.KEY.get(player);
                    return mercenary.onBoughtShieldLayer();
                }
            });

            // 撬锁器 - 100金币
            shop.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 100, ShopEntry.Type.TOOL));

            // 德林加 - 300金币
            shop.add(new ShopEntry(TMMItems.DERRINGER.getDefaultInstance(), 300, ShopEntry.Type.WEAPON));

            // 刀 - 130金币
            shop.add(new KillerKnifeShopEntry(130));

            ShopContent.customEntries.put(ModRoles.MERCENARY_ID, shop);
        }

        // 布谷鸟（Cuckoo）商店：撬锁器 - 250金币
        {
            var SHOP = new ArrayList<ShopEntry>();
            SHOP.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 250, ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    // 将撬锁器放入玩家空位
                    return RoleUtils.insertStackInFreeSlot(player, TMMItems.LOCKPICK.getDefaultInstance());
                }
            });
            ShopContent.customEntries.put(ModRoles.CUCKOO.getIdentifier(), SHOP);
        }

        // 游侠商店
        {
            var shopEntries = new ArrayList<ShopEntry>();
            shopEntries.add(new ShopEntry(Items.CROSSBOW.getDefaultInstance(), 300, ShopEntry.Type.WEAPON) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    int itemCount = SREItemUtils.countItem(player, Items.CROSSBOW);
                    if (itemCount > 0)
                        return false;
                    ItemStack item = Items.CROSSBOW.getDefaultInstance();
                    item.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
                    return RoleUtils.insertStackInFreeSlot(player, item);
                }
            });

            final var PoisonArrow = Items.TIPPED_ARROW.getDefaultInstance();
            PoisonArrow.set(DataComponents.ITEM_NAME, Component.translatable("item.poison_arrow.name"));
            PoisonArrow.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.POISON));
            PoisonArrow.set(DataComponents.MAX_STACK_SIZE, 1);
            shopEntries.add(new ShopEntry(PoisonArrow, 75, ShopEntry.Type.WEAPON) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    int itemCount = SREItemUtils.countItem(player, Items.TIPPED_ARROW);
                    if (itemCount >= 2)
                        return false;
                    return RoleUtils.insertStackInFreeSlot(player, PoisonArrow.copy());
                }
            });

            final var SpectralArrow = Items.SPECTRAL_ARROW.getDefaultInstance();
            SpectralArrow.set(DataComponents.MAX_STACK_SIZE, 1);

            shopEntries.add(new ShopEntry(SpectralArrow, 50, ShopEntry.Type.WEAPON) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    int itemCount = SREItemUtils.countItem(player, Items.SPECTRAL_ARROW);
                    if (itemCount >= 2)
                        return false;
                    return RoleUtils.insertStackInFreeSlot(player, SpectralArrow.copy());
                }
            });
            ShopContent.customEntries.put(
                    ModRoles.ELF_ID, shopEntries);
        }

        // 丘比特商店
        {
            var shopEntries = new ArrayList<ShopEntry>();
            final var RegenerationArrow = Items.TIPPED_ARROW.getDefaultInstance();
            RegenerationArrow.set(DataComponents.ITEM_NAME, Component.translatable("item.regeneration_arrow.name"));
            RegenerationArrow.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.REGENERATION));
            RegenerationArrow.set(DataComponents.MAX_STACK_SIZE, 1);
            shopEntries.add(new ShopEntry(RegenerationArrow, 100, ShopEntry.Type.WEAPON) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    int itemCount = SREItemUtils.countItem(player, Items.TIPPED_ARROW);
                    if (itemCount >= 2)
                        return false;
                    return RoleUtils.insertStackInFreeSlot(player, RegenerationArrow.copy());
                }
            });
            ShopContent.customEntries.put(ModRoles.CUPID_ID, shopEntries);
        }

        // 操纵师商店（默认刀具）
        {
            ShopContent.customEntries.put(
                    ModRoles.MANIPULATOR_ID, ShopContent.getDefaultKnifeEntries());
        }

        // 运动员商店（速度药水）
        {
            var SPEED_SPLASH_POITION = Items.SPLASH_POTION.getDefaultInstance();
            var speedPotionList = List.of(new MobEffectInstance(
                    MobEffects.MOVEMENT_SPEED,
                    60 * 20, // 持续时间（tick）
                    2, // 等级（0 = 速度 I）
                    false, // ambient（环境效果，如信标）
                    true, // showParticles（显示粒子）
                    true // showIcon（显示图标）
            ));
            var speedPotionContent = new PotionContents(Optional.empty(), Optional.of(53503),
                    speedPotionList);
            SPEED_SPLASH_POITION.set(DataComponents.POTION_CONTENTS, speedPotionContent);
            var shopEntries = new ArrayList<ShopEntry>();
            shopEntries.add(new ShopEntry(SPEED_SPLASH_POITION, 275, ShopEntry.Type.WEAPON));
            ShopContent.customEntries.put(
                    ModRoles.ATHLETE_ID, shopEntries);
        }

        // 执行者商店（柜子区的商店）
        {
            ShopContent.customEntries.put(
                    ModRoles.EXECUTIONER_ID, 柜子区的商店);
        }

        // 变形者商店
        {
            List<ShopEntry> entries = ShopContent.getDefaultKnifeEntries();
            entries.add(new ShopEntry(
                    ModItems.HALLUCINATION_BOTTLE.getDefaultInstance(),
                    120,
                    ShopEntry.Type.TOOL));

            ShopContent.customEntries.put(
                    ModRoles.MORPHLING_ID, entries);
        }

        // 静语者商店：默认杀手刀具列表
        {
            ShopContent.customEntries.put(
                    ModRoles.SILENCER_ID, ShopContent.getDefaultKnifeEntries());
        }

        // 毒师商店
        {
            ShopContent.customEntries.put(
                    ModRoles.POISONER_ID, createPoisonerShopEntries());
        }

        // 交换者商店
        {
            ShopContent.customEntries.put(
                    ModRoles.SWAPPER_ID, ShopContent.getDefaultKnifeEntries());
        }

        // 仇杀客商店
        {
            ShopContent.customEntries.put(
                    ModRoles.BLOOD_FEUDIST_ID, BLOOD_FEUDIST_SHOP);
        }

        // 小丑商店（框架角色）
        {
            ShopContent.customEntries.put(
                    ModRoles.JESTER_ID, FRAMING_ROLES_SHOP);
        }

        // DIO 商店
        {
            List<ShopEntry> entries = new ArrayList<>();
            entries.add(new ShopEntry(ModItems.THROWING_KNIFE.getDefaultInstance(), 120,
                    ShopEntry.Type.TOOL));
            entries.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 95,
                    ShopEntry.Type.TOOL));
            entries.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), 175,
                    ShopEntry.Type.TOOL) {
                public boolean onBuy(@NotNull Player player) {
                    player.getCooldowns().addCooldown(TMMItems.BLACKOUT,
                            Math.min((Integer) GameConstants.ITEM_COOLDOWNS
                                    .getOrDefault(TMMItems.BLACKOUT, 0), 60));
                    boolean triggered = ((SREWorldBlackoutComponent) SREWorldBlackoutComponent.KEY
                            .get(player.level()))
                            .triggerBlackout();
                    if (triggered) {
                        SRE.REPLAY_MANAGER.recordSkillUsed(player.getUUID(),
                                BuiltInRegistries.ITEM.getKey(TMMItems.BLACKOUT));
                    }
                    return triggered;
                }
            });
            ShopContent.customEntries.put(
                    ModRoles.DIO_ID, entries);
        }

        // 女仆咲夜商店
        {
            List<ShopEntry> entries = new ArrayList<>();
            var SAKUYA_KNIFE = ModItems.THROWING_KNIFE.getDefaultInstance();
            SAKUYA_KNIFE.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(1));
            entries.add(new ShopEntry(SAKUYA_KNIFE, 250,
                    ShopEntry.Type.TOOL));
            entries.add(new ShopEntry(FunnyItems.SHISIYE.getDefaultInstance(), 440,
                    ShopEntry.Type.TOOL));
            ShopContent.customEntries.put(
                    RedHouseRoles.MAID_SAKUYA_ID, entries);
        }

        // 调酒师商店
        {
            List<ShopEntry> entries = new ArrayList<>();
            entries.add(new ShopEntry(TMMItems.DEFENSE_VIAL.getDefaultInstance(), 200,
                    ShopEntry.Type.POISON));

            ShopContent.customEntries.put(
                    ModRoles.BARTENDER_ID, entries);
        }

        // 摄影师商店
        {
            List<ShopEntry> entries = new ArrayList<>();
            // 拍立得相机 - 75金币
            {
                final var itemNamespace = "exposure_polaroid";
                final var itemId = "instant_camera";
                {
                    Item item = BuiltInRegistries.ITEM
                            .get(ResourceLocation.fromNamespaceAndPath(itemNamespace, itemId));
                    if (item != null && item != Items.AIR) {
                        final var defaultInstance = item.getDefaultInstance();
                        entries.add(new ShopEntry(defaultInstance, 75, ShopEntry.Type.TOOL) {
                            @Override
                            public boolean onBuy(@NotNull Player player) {
                                player.addItem(defaultInstance.copy());
                                return true;
                            }
                        });
                    }
                }
            }
            // 拍立得相纸 - 50
            {
                final var itemNamespace = "exposure_polaroid";
                final var itemId = "instant_color_slide";
                {
                    Item item = BuiltInRegistries.ITEM
                            .get(ResourceLocation.fromNamespaceAndPath(itemNamespace, itemId));
                    if (item != null && item != Items.AIR) {
                        final var defaultInstance = item.getDefaultInstance();
                        entries.add(new ShopEntry(defaultInstance, 50, ShopEntry.Type.TOOL) {
                            @Override
                            public boolean onBuy(@NotNull Player player) {
                                player.addItem(defaultInstance.copy());
                                return true;
                            }
                        });
                    }
                }
            }
            // 画框（照片框） - 默认200金币，一局最多购买两次
            {
                var frameItem = BuiltInRegistries.ITEM
                        .get(ResourceLocation.fromNamespaceAndPath("exposure", "photograph_frame"));
                if (frameItem != null && frameItem != Items.AIR) {
                    final var frameStack = frameItem.getDefaultInstance();
                    int price = 200;
                    entries.add(new ShopEntry(frameStack, price, ShopEntry.Type.TOOL) {
                        @Override
                        public boolean onBuy(@NotNull Player player) {
                            var comp = org.agmas.noellesroles.game.roles.innocence.photographer.PhotographerPlayerComponent.KEY
                                    .get(player);
                            if (comp == null || !comp.canBuyFrame()) {
                                return false;
                            }
                            player.addItem(frameStack.copy());
                            comp.recordFrameBought();
                            return true;
                        }
                    });
                }
            }

            ShopContent.customEntries.put(
                    ModRoles.PHOTOGRAPHER_ID, entries);
        }

        // 牛逼
        {
            ShopContent.customEntries.put(
                    ModRoles.AWESOME_BINGLUS_ID,
                    List.of(
                            new ShopEntry(TMMItems.NOTE.getDefaultInstance(), 10, ShopEntry.Type.TOOL),
                            new ShopEntry(ModItems.GIANT_NOTE.getDefaultInstance(), 75, ShopEntry.Type.TOOL)));
        }

        // 阴谋家商店
        {
            ShopContent.customEntries.put(
                    ModRoles.CONSPIRATOR_ID, CONSPIRATOR_SHOP);
        }

        // 捣蛋鬼商店
        {
            ShopContent.customEntries.put(
                    ModRoles.PRANKSTER_ID, SLIPPERY_GHOST_SHOP);

            // 小恶魔的商店。继承捣蛋鬼
            {
                var SHOP = new ArrayList<ShopEntry>();
                SHOP.addAll(SLIPPERY_GHOST_SHOP);
                ShopContent.customEntries.put(RedHouseRoles.KOAKUMA_ID, SHOP);
            }
        }

        // PACHURI 商店
        {
            var displayStack = ModItems.NEWSPAPER.getDefaultInstance();
            String title = "\u00a7d\u00a7lPachuri Knowledge Book";
            displayStack.set(SREDataComponentTypes.WRITTEN_BOOK_CONTENT,
                    new SREWrittenBookContent(new Filterable<String>(title, Optional.of(title)), "Pachuri Knowledge",
                            List.of(),
                            true));
            var SHOP = new ArrayList<ShopEntry>();
            SHOP.add(new ShopEntry(
                    displayStack,
                    125,
                    ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(Player player) {
                    var itemStack = ModItems.NEWSPAPER.getDefaultInstance();
                    var players = new ArrayList<Player>(player.level().players());
                    var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());

                    players.removeIf((p) -> {
                        return gameWorldComponent.getRole(p) == null || p.isSpectator();
                    });
                    var koakumaOptional = players.stream()
                            .filter((p) -> gameWorldComponent.isRole(p, RedHouseRoles.KOAKUMA))
                            .findAny();
                    if (koakumaOptional.isPresent()) {
                        Player koakumaPlayer = koakumaOptional.get();
                        var koakumaCCA = SREAbilityPlayerComponent.KEY.get(koakumaPlayer);
                        if (koakumaCCA.targetUUID != null) {
                            players.removeIf((p) -> {
                                return p.getUUID() != koakumaCCA.targetUUID;
                            });
                            if (players.isEmpty()) {
                                players.add(koakumaPlayer);
                            }
                            koakumaCCA.targetUUID = null;
                            // 不需要同步因为客户端不显示东西。
                        }
                    }
                    Collections.shuffle(players);
                    int count = 1;
                    var contents = new ArrayList<Filterable<Component>>();
                    // {
                    var fstct = Component.translatable("%s\n%s\n\n",
                            Component.translatable("item.written_book.role_title"),
                            Component.translatable("item.written_book.role_intro"));
                    // var fstcontent = new Filterable<Component>(fstct, Optional.of(fstct));
                    // contents.add(fstcontent);
                    // }
                    for (int i = 0; i < count; i++) {
                        var p = players.get(i);

                        var ct = Component.translatable("%s%s",
                                Component.translatable("item.written_book.per_role_title", i + 1),
                                Component
                                        .translatable("item.written_book.per_role_content",
                                                p.getName(),
                                                RoleUtils.getRoleOrModifierName(gameWorldComponent.getRole(p))));
                        if (i == 0) {
                            ct = fstct.append(ct);
                        }
                        var content = new Filterable<Component>(ct, Optional.of(ct));
                        contents.add(content);
                        if (p instanceof ServerPlayer sp)
                            BroadcastCommand.BroadcastMessage(sp,
                                    Component.translatable("message.pachuri.be_known_role")
                                            .withStyle(ChatFormatting.RED));
                    }
                    String title = "Pachuri Knowledge Book";
                    String shortTitle = title;
                    if (shortTitle.length() >= 10) {
                        shortTitle = shortTitle.substring(0, 8) + "...";
                    }
                    itemStack.set(SREDataComponentTypes.WRITTEN_BOOK_CONTENT,
                            new SREWrittenBookContent(new Filterable<String>(title, Optional.of(title)),
                                    player.getScoreboardName(),
                                    contents, true));
                    itemStack.set(DataComponents.ITEM_NAME,
                            Component.translatable("item.noellesroles.newspaper.name",
                                    Component.translatable("item.noellesroles.newspaper.title.warp", shortTitle)
                                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
                    return RoleUtils.insertStackInFreeSlot(player, itemStack);
                }
            });
            ShopContent.customEntries.put(
                    RedHouseRoles.PACHURI_ID, SHOP);
        }

        // 锁匠商店
        {
            var SHOP = new ArrayList<ShopEntry>();
            SHOP.add(new ShopEntry(
                    ModItems.SCREWDRIVER.getDefaultInstance(),
                    100,
                    ShopEntry.Type.TOOL));
            SHOP.add(new ShopEntry(
                    ModItems.NOELL_KEY_BLANK.getDefaultInstance(),
                    150,
                    ShopEntry.Type.TOOL));
            SHOP.add(new ShopEntry(
                    ModItems.NOELL_PAPERCLIP.getDefaultInstance(),
                    75,
                    ShopEntry.Type.TOOL));
            ShopContent.customEntries.put(
                    ModRoles.LOCKSMITH_ID, SHOP);
        }

        // 船长商店
        {
            var SHOP = new ArrayList<ShopEntry>();
            SHOP.add(new ShopEntry(
                    ModItems.SCREWDRIVER.getDefaultInstance(),
                    100,
                    ShopEntry.Type.TOOL));
            SHOP.add(new ShopEntry(
                    ModItems.MASTER_KEY.getDefaultInstance(),
                    400,
                    ShopEntry.Type.TOOL));
            ShopContent.customEntries.put(
                    ModRoles.CONDUCTOR_ID, SHOP);
        }

        // 工程师商店
        {
            ShopContent.customEntries.put(
                    ModRoles.ENGINEER_ID, ENGINEER_SHOP);
        }

        // 斗士商店
        {
            ShopContent.customEntries.put(
                    ModRoles.FIGHTER_ID, BOXER_SHOP);
        }

        // 清道夫商店
        {
            var shopEntries = new ArrayList<ShopEntry>();
            shopEntries.add(new KillerKnifeShopEntry(220));
            shopEntries.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 100,
                    ShopEntry.Type.TOOL));
            shopEntries.add(new ShopEntry(TMMItems.GRENADE.getDefaultInstance(), 500, ShopEntry.Type.TOOL));
            shopEntries.add(new ShopEntry(TMMItems.NOTE.getDefaultInstance(), 15, ShopEntry.Type.TOOL));
            shopEntries.add(new ShopEntry(TMMItems.FIRECRACKER.getDefaultInstance(), 15,
                    ShopEntry.Type.TOOL));
            // 监控失灵 - 60金币（清道夫专属）
            shopEntries.add(new ShopEntry(TMMItems.MONITOR_BROKEN.getDefaultInstance(), 60, ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    return SREPlayerShopComponent.useMonitorBroken(player,
                            SREConfig.instance().monitorBrokenDuration * 20);
                }
            });
            ShopContent.customEntries.put(
                    ModRoles.CLEANER_ID,
                    shopEntries);
        }

        // 仰慕者商店
        {
            ShopContent.customEntries.put(
                    ModRoles.ADMIRER_ID,
                    List.of(new ShopEntry(ModItems.MASTER_KEY_P.getDefaultInstance(), 150,
                            ShopEntry.Type.TOOL)));
        }

        // 潜行者商店
        {
            ShopContent.customEntries.put(
                    ModRoles.STALKER_ID,
                    List.of(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 75,
                            ShopEntry.Type.TOOL),
                            new ShopEntry(ModItems.STALKER_KNIFE_OFFHAND.getDefaultInstance(), 325,
                                    ShopEntry.Type.WEAPON) {
                                @Override
                                public boolean canBuy(@NotNull Player player) {
                                    return !(player.getOffhandItem().getItem() instanceof KnifeItem);
                                }

                                @Override
                                public boolean canDisplay(@NotNull Player player) {
                                    return StalkerPlayerComponent.KEY.get(player).phase >= 2;
                                }

                                @Override
                                public boolean onBuy(@NotNull Player player) {

                                    boolean b = player.getOffhandItem().getItem() instanceof KnifeItem;
                                    if (!b) {
                                        player.setItemInHand(InteractionHand.OFF_HAND,
                                                ModItems.STALKER_KNIFE_OFFHAND.getDefaultInstance());
                                    }
                                    return b;
                                }
                            }));
        }

        // 心理学家商店
        {
            ShopContent.customEntries.put(
                    ModRoles.PSYCHOLOGIST_ID, PSYCHOLOGIST_SHOP);
        }

        // 乘务员商店
        {
            ShopContent.customEntries.put(
                    ModRoles.ATTENDANT_ID, ATTENDANT_SHOP);
        }

        // 炸弹客商店
        {
            ShopContent.customEntries.put(
                    ModRoles.BOMBER_ID, BOMBER_SHOP);
        }

        // 医生商店
        {
            ShopContent.customEntries.put(
                    ModRoles.DOCTOR_ID, DOCTOR_SHOP);
        }

        // 歌手商店
        {
            ShopContent.customEntries.put(
                    ModRoles.SINGER_ID, SINGER_SHOP);
        }

        // 退伍军人商店
        {
            VETERAN_SHOP.add(new ShopEntry(
                    TMMItems.KNIFE.getDefaultInstance(),
                    250,
                    ShopEntry.Type.WEAPON));
            ShopContent.customEntries.put(
                    ModRoles.VETERAN_ID, VETERAN_SHOP);
        }

        // 年兽商店
        {
            ShopContent.customEntries.put(
                    ModRoles.NIAN_SHOU_ID, NIAN_SHOU_SHOP);
        }

        // 魔术师商店
        {
            ShopContent.customEntries.put(
                    ModRoles.MAGICIAN_ID, MAGICIAN_SHOP);
        }

        // 强盗商店
        {
            ShopContent.customEntries.put(
                    ModRoles.BANDIT_ID, BANDIT_SHOP);
        }

        // 悍匪商店
        {
            ShopContent.customEntries.put(
                    ModRoles.GANGSTERS_ID, GANGSTERS_SHOP);
        }

        // 钳工商店
        {
            ShopContent.customEntries.put(
                    ModRoles.FITTER_ID, FITTER_SHOP);
        }

        // 鹈鹕商店
        {
            ShopContent.customEntries.put(
                    ModRoles.PELICAN_ID, PELICAN_SHOP);
        }

        // 教父商店
        {
            ShopContent.customEntries.put(
                    ModRoles.GODFATHER_ID, GODFATHER_SHOP);
        }

        // 家族教徒商店
        {
            ShopContent.customEntries.put(
                    ModRoles.MAFIOSO_ID, MAFIOSO_SHOP);
        }

        // 家族侍卫商店
        {
            ShopContent.customEntries.put(
                    ModRoles.JANITOR_ID, JANITOR_SHOP);
        }

        // 家族保姆商店
        {
            ShopContent.customEntries.put(
                    ModRoles.NUTRITIONIST_ID, NUTRITIONIST_SHOP);
        }

        // 家族保护伞商店
        {
            ShopContent.customEntries.put(
                    ModRoles.PARASOL_ID, PARASOL_SHOP);
        }

        // 咒法师商店
        {
            ShopContent.customEntries.put(
                    ModRoles.WARLOCK_ID, WARLOCK_SHOP);
        }

        // Dream（梦魇）商店
        {
            ShopContent.customEntries.put(
                    ModRoles.DREAM_ID, DREAM_SHOP);
        }

        // 嬉命人商店
        {
            ShopContent.customEntries.put(
                    ModRoles.EMBALMER_ID, EMBALMER_SHOP);
        }

        // 幻音师商店
        {
            ShopContent.customEntries.put(
                    ModRoles.PHANTOM_MUSICIAN_ID, PHANTOM_MUSICIAN_SHOP);
        }

        // 影杀者商店
        {
            ShopContent.customEntries.put(
                    ModRoles.WRAITH_ASSASSIN_ID, WRAITH_ASSASSIN_SHOP);
        }

        // 推理师商店
        {
            ShopContent.customEntries.put(
                    ModRoles.REASONER_ID, REASONER_SHOP);
        }

        // 小偷商店（注释部分，保留）
        // 暂未启用

        // 钟表匠商店
        {
            ShopContent.customEntries.put(
                    ModRoles.CLOCKMAKER_ID, CLOCKMAKER_SHOP);
        }

        // 作家商店
        {
            ShopContent.customEntries.put(
                    BounsRoles.WRITER_ID, WRITER_SHOP);
        }

        // 搜救员商店
        {
            ShopContent.customEntries.put(
                    ModRoles.RESCUER_ID, RESCUER_SHOP);
        }

        // 消防员商店
        {
            ShopContent.customEntries.put(
                    ModRoles.FIREFIGHTER_ID, FIREFIGHTER_SHOP);
        }

        // 会计商店
        {
            ShopContent.customEntries.put(
                    ModRoles.ACCOUNTANT_ID, ACCOUNTANT_SHOP);
        }

        // 风精灵
        {
            List<ShopEntry> entries = new ArrayList<>();
            entries.add(new ShopEntry(Items.WIND_CHARGE.getDefaultInstance(), 50, ShopEntry.Type.TOOL));
            ShopContent.customEntries.put(
                    ModRoles.WIND_YAOSE_ID, entries);
        }

        // 警卫商店
        {
            List<ShopEntry> entries = new ArrayList<>();
            entries.add(new ShopEntry(ModItems.HANDCUFFS.getDefaultInstance(), 150, ShopEntry.Type.TOOL));
            ShopContent.customEntries.put(
                    ModRoles.SHERIFF_ID, entries);
        }

        // 巡警商店
        {
            ShopContent.customEntries.put(
                    ModRoles.PATROLLER_ID, PATROLLER_SHOP);
        }

        // 特警商店
        {
            ShopContent.customEntries.put(
                    ModRoles.SWAST_ID, SWAST_SHOP);
        }

        // 武术教官商店
        {
            ShopContent.customEntries.put(
                    ModRoles.MARTIAL_ARTS_INSTRUCTOR_ID, MARTIAL_ARTS_INSTRUCTOR_SHOP);
        }

        // 海王商店
        {
            ShopContent.customEntries.put(
                    ModRoles.SEA_KING_ID, SEA_KING_SHOP);
        }

        // 水鬼商店
        {
            ShopContent.customEntries.put(
                    ModRoles.WATER_GHOST_ID, WATER_GHOST_SHOP);
        }

        // 秉烛人商店
        {
            ShopContent.customEntries.put(
                    ModRoles.CANDLE_BEARER_ID, CANDLE_BEARER_SHOP);
        }

        // 超级亡命徒商店
        {
            ShopContent.customEntries.put(
                    SpecialGameModeRoles.SUPER_LOOSE_END.identifier(), SUPER_LOOSE_END_SHOP);
        }

        // 飞行员商店
        {
            ShopContent.customEntries.put(ModRoles.PILOT_ID, PILOT_SHOP);
        }

        // 影隼商店
        {
            ShopContent.customEntries.put(ModRoles.SHADOW_FALCON_ID, SHADOW_FALCON_SHOP);
        }

        // 故障机器人商店
        {
            List<ShopEntry> glitchRobotShop = new ArrayList<>();
            // 夜视仪 - 100金币
            glitchRobotShop
                    .add(new ShopEntry(ModItems.NIGHT_VISION_GLASSES.getDefaultInstance(), 100,
                            ShopEntry.Type.TOOL));
            // 萤石粉 - 25金币（修复夜视仪）
            glitchRobotShop.add(new ShopEntry(Items.GLOWSTONE_DUST.getDefaultInstance(), 25,
                    ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    var head = player.getSlot(103).get();
                    if (head.is(ModItems.NIGHT_VISION_GLASSES)) {
                        int damage = head.getDamageValue();
                        if (damage >= 25) {
                            head.setDamageValue(damage - 25);
                        } else {
                            return false;
                        }
                    } else {
                        return false;
                    }
                    return true;
                }
            });
            ShopContent.customEntries.put(ModRoles.GLITCH_ROBOT_ID, glitchRobotShop);
        }

        // 潜水员商店
        {
            List<ShopEntry> diverShop = new ArrayList<>();
            // 潜水头盔 - 125金币
            diverShop.add(new ShopEntry(ModItems.DIVING_HELMET.getDefaultInstance(), 125,
                    ShopEntry.Type.TOOL));
            // 潜水靴 - 225金币
            diverShop.add(new ShopEntry(ModItems.DIVING_BOOTS.getDefaultInstance(), 225,
                    ShopEntry.Type.TOOL));
            ShopContent.customEntries.put(ModRoles.DIVER_ID, diverShop);
        }

        // 诡客的商店
        {
            List<ShopEntry> shop = new ArrayList<>();
            // 净雨符
            {
                ItemStack it = Items.POTION.getDefaultInstance();
                it.set(DataComponents.ITEM_NAME, Component.translatable("item.noellesroles.guest_ghost.stop_raining"));
                var rainLore = new ArrayList<Component>();
                rainLore.add(Component.translatable("item.noellesroles.guest_ghost.stop_raining.tooltip1")
                        .setStyle(Style.EMPTY.withItalic(false))
                        .withStyle(ChatFormatting.GRAY));
                it.set(DataComponents.LORE, new ItemLore(rainLore));
                shop.add(new ShopEntry(it, 200,
                        ShopEntry.Type.TOOL) {
                    @Override
                    public boolean onBuy(Player player) {
                        if (player.getCooldowns().isOnCooldown(Items.BARRIER)) {
                            return false;
                        }
                        player.getCooldowns().addCooldown(Items.BARRIER, 60 * 20);
                        if (player instanceof ServerPlayer sp) {
                            final SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(sp.level());
                            List<ServerPlayer> players = sp.serverLevel().players();
                            for (var p : players) {
                                if (GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p)) {
                                    p.removeEffect(ModEffects.INFINITE_STAMINA);
                                    if (gameWorldComponent.isRole(p, ModRoles.MA_CHEN_XU)) {
                                        var mapc = MaChenXuPlayerComponent.KEY.get(p);
                                        if (mapc.otherworldActive) {
                                            mapc.otherworldDuration = 1;
                                        }
                                    } else {
                                        SRERole role = gameWorldComponent.getRole(p);
                                        if (role != null) {
                                            if (role.getMoodType().equals(SRERole.MoodType.REAL)) {
                                                SREPlayerMoodComponent.KEY.get(p).addMood(0.2f);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        return true;
                    }
                });
            }
            // 桃木钉
            {
                ItemStack it = ModItems.TAOMUDING.getDefaultInstance();
                it.set(DataComponents.ITEM_NAME, Component.translatable("item.noellesroles.guest_ghost.taomuding"));
                var rainLore = new ArrayList<Component>();
                rainLore.add(Component.translatable("item.noellesroles.guest_ghost.taomuding.tooltip1")
                        .setStyle(Style.EMPTY.withItalic(false))
                        .withStyle(ChatFormatting.GRAY));
                it.set(DataComponents.LORE, new ItemLore(rainLore));
                shop.add(new ShopEntry(it, 150,
                        ShopEntry.Type.TOOL) {
                    @Override
                    public boolean onBuy(Player player) {
                        if (player.getCooldowns().isOnCooldown(ModItems.TAOMUDING)) {
                            return false;
                        }
                        player.getCooldowns().addCooldown(ModItems.TAOMUDING, 60 * 20);
                        if (player instanceof ServerPlayer sp) {
                            final SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(sp.level());
                            List<ServerPlayer> players = sp.serverLevel().players();
                            for (var p : players) {
                                if (GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p)) {
                                    if (gameWorldComponent.isRole(p, ModRoles.MA_CHEN_XU)) {
                                        var mapc = MaChenXuPlayerComponent.KEY.get(p);
                                        // 增加所有鬼术冷却30秒
                                        mapc.veilCooldown += 20 * 30;
                                        mapc.effigyCooldown += 20 * 30;
                                        mapc.wailCooldown += 20 * 30;
                                        mapc.seizeCooldown += 20 * 30;
                                        mapc.ultimateCooldown += 20 * 30;
                                        // 移除护盾
                                        mapc.permanentShield = false;
                                        p.displayClientMessage(
                                                Component.translatable(
                                                        "message.noellesroles.ma_chen_xu.into_cooldown_by_guest")
                                                        .withStyle(ChatFormatting.RED),
                                                true);
                                        p.playNotifySound(TMMSounds.ITEM_PSYCHO_ARMOUR, SoundSource.MASTER, 1f, 1f);
                                    }
                                }
                            }
                        }
                        return true;
                    }
                });
            }

            ShopContent.customEntries.put(ModRoles.GUEST_GHOST_ID, shop);
        }

        // 观察者商店 (Watcher)
        {
            var shop = new ArrayList<ShopEntry>();
            shop.add(
                    new KillerKnifeShopEntry(SREConfig.instance().knifePrice) {
                        @Override
                        public boolean canDisplay(Player player) {
                            return !WatcherPlayerComponent.KEY.get(player).isInCalmStance();
                        }
                    });
            // 左轮手枪 - 285金币（愤怒姿态）
            shop.add(new ShopEntry(ModItems.ZERO_ONE_FIVE_GUN.getDefaultInstance(), 285, ShopEntry.Type.WEAPON) {
                @Override
                public boolean canDisplay(Player player) {
                    return !WatcherPlayerComponent.KEY.get(player).isInCalmStance();
                }
            });
            // 手榴弹
            shop.add(new ShopEntry(TMMItems.GRENADE.getDefaultInstance(), SREConfig.instance().grenadePrice,
                    ShopEntry.Type.WEAPON) {
                @Override
                public boolean canDisplay(Player player) {
                    return !WatcherPlayerComponent.KEY.get(player).isInCalmStance();
                }
            });
            shop.add(new ShopEntry(TMMItems.PSYCHO_MODE.getDefaultInstance(),
                    SREConfig.instance().psychoModePrice, ShopEntry.Type.WEAPON) {
                @Override
                public boolean canDisplay(Player player) {
                    return !WatcherPlayerComponent.KEY.get(player).isInCalmStance();
                }

                @Override
                public boolean onBuy(@NotNull Player player) {
                    return SREPlayerShopComponent.usePsychoMode(player);
                }
            });
            shop.add(new ShopEntry(TMMItems.FIRECRACKER.getDefaultInstance(),
                    SREConfig.instance().firecrackerPrice, ShopEntry.Type.TOOL) {
                @Override
                public boolean canDisplay(Player player) {
                    return !WatcherPlayerComponent.KEY.get(player).isInCalmStance();
                }
            });
            shop.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), SREConfig.instance().lockpickPrice,
                    ShopEntry.Type.TOOL) {
                @Override
                public boolean canDisplay(Player player) {
                    return true;
                }
            });
            shop.add(
                    new ShopEntry(TMMItems.CROWBAR.getDefaultInstance(), SREConfig.instance().crowbarPrice,
                            ShopEntry.Type.TOOL) {
                        @Override
                        public boolean canDisplay(Player player) {
                            return !WatcherPlayerComponent.KEY.get(player).isInCalmStance();
                        }
                    });
            shop.add(new ShopEntry(TMMItems.BODY_BAG.getDefaultInstance(), SREConfig.instance().bodyBagPrice,
                    ShopEntry.Type.TOOL) {
                @Override
                public boolean canDisplay(Player player) {
                    return !WatcherPlayerComponent.KEY.get(player).isInCalmStance();
                }
            });
            shop.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), SREConfig.instance().blackoutPrice,
                    ShopEntry.Type.TOOL) {
                @Override
                public boolean canDisplay(Player player) {
                    return !WatcherPlayerComponent.KEY.get(player).isInCalmStance();
                }

                @Override
                public boolean onBuy(@NotNull Player player) {
                    return SREPlayerShopComponent.useBlackout(player);
                }
            });
            shop
                    .add(new ShopEntry(new ItemStack(TMMItems.NOTE, 4), SREConfig.instance().notePrice,
                            ShopEntry.Type.TOOL) {
                        @Override
                        public boolean canDisplay(Player player) {
                            return true;
                        }
                    });
            ShopContent.customEntries.put(ModRoles.WATCHER_ID, shop);
        }

        // 叛徒商店 - 屏障商品，不可交互、不可购买、不可显示，阻止默认杀手商店出现
        {
            var TRAITOR_SHOP = new ArrayList<ShopEntry>();
            // 添加一个屏障商品：不可显示、不可购买，仅用于阻止 getRoleShopEntries 回退到默认杀手商店
            TRAITOR_SHOP.add(new ShopEntry(Items.BARRIER.getDefaultInstance(), Integer.MAX_VALUE, ShopEntry.Type.TOOL) {
                @Override
                public boolean canDisplay(@NotNull Player player) {
                    return false;
                }

                @Override
                public boolean canBuy(@NotNull Player player) {
                    return false;
                }
            });
            ShopContent.customEntries.put(TraitorAndModifiers.TRAITOR.identifier(), TRAITOR_SHOP);
        }

        // 巫师商店：法术快捷施法（金币恒为 0，实际消耗法力；便于不切法术轮盘直接施放）
        {
            var WIZARD_SHOP = new ArrayList<ShopEntry>();
            WIZARD_SHOP.add(wizardSpellEntry(Items.SNOWBALL, "frost",
                    org.agmas.noellesroles.game.roles.killer.wizard.WizardPlayerComponent.Spell.FROST));
            WIZARD_SHOP.add(wizardSpellEntry(Items.COAL, "shadow",
                    org.agmas.noellesroles.game.roles.killer.wizard.WizardPlayerComponent.Spell.SHADOW));
            WIZARD_SHOP.add(wizardSpellEntry(Items.FIRE_CHARGE, "explosion",
                    org.agmas.noellesroles.game.roles.killer.wizard.WizardPlayerComponent.Spell.EXPLOSION));
            WIZARD_SHOP.add(wizardSpellEntry(Items.ENDER_PEARL, "blink",
                    org.agmas.noellesroles.game.roles.killer.wizard.WizardPlayerComponent.Spell.BLINK));
            ShopContent.customEntries.put(ModRoles.WIZARD_ID, WIZARD_SHOP);
        }

        // 疫使商店
        {
            var INFECTED_SHOP_LIST = new ArrayList<ShopEntry>();
            // 催化剂 - 450金币
            // 使所有感染玩家和中毒玩家致死，但不会使杀手阵营/杀手方中立玩家致死
            INFECTED_SHOP_LIST.add(new ShopEntry(ModItems.CATALYST.getDefaultInstance(), 450, ShopEntry.Type.TOOL));
            // 乘务员钥匙 - 100金币
            INFECTED_SHOP_LIST.add(new ShopEntry(ModItems.MASTER_KEY_P.getDefaultInstance(), 100, ShopEntry.Type.TOOL));
            ShopContent.customEntries.put(ModRoles.INFECTED.getIdentifier(), INFECTED_SHOP_LIST);
        }

        // 葬仪商店
        {
            ArrayList<ShopEntry> MORTICIAN_BODYMAKER_SHOP = new ArrayList<>();
            // 葬仪商店
            // 乘务员钥匙 - 100金币
            MORTICIAN_BODYMAKER_SHOP.add(new ShopEntry(
                    ModItems.MASTER_KEY_P.getDefaultInstance(),
                    100,
                    ShopEntry.Type.TOOL));
            // 裹尸袋 - 150金币
            MORTICIAN_BODYMAKER_SHOP.add(new ShopEntry(
                    TMMItems.BODY_BAG.getDefaultInstance(),
                    150,
                    ShopEntry.Type.TOOL));
            // 血瓶 - 75金币
            MORTICIAN_BODYMAKER_SHOP.add(new ShopEntry(
                    ModItems.BLOOD_BOTTLE.getDefaultInstance(),
                    75,
                    ShopEntry.Type.TOOL));
            ShopContent.customEntries.put(ModRoles.MORTICIAN_BODYMAKER_ID, MORTICIAN_BODYMAKER_SHOP);
        }
    }

    /**
     * 巫师法术快捷施法条目：标价 0 金币（巫师的金币每 tick 会自动炼成法力，无法囤积），
     * 点击后直接调用 {@code WizardPlayerComponent.quickCast} 施放法术 —— 法力不足 /
     * 冷却中 / 前置条件不满足（如暗影需停电）时施放失败、购买取消。
     */
    private static ShopEntry wizardSpellEntry(net.minecraft.world.item.Item icon, String nameKey,
            org.agmas.noellesroles.game.roles.killer.wizard.WizardPlayerComponent.Spell spell) {
        ItemStack stack = new ItemStack(icon);
        stack.set(DataComponents.CUSTOM_NAME,
                Component.translatable("shop.noellesroles.wizard." + nameKey));
        stack.set(DataComponents.LORE, new ItemLore(
                java.util.List.of(Component.translatable("shop.noellesroles.wizard." + nameKey + ".desc")
                        .withStyle(ChatFormatting.GRAY))));
        return new ShopEntry(stack, 0, ShopEntry.Type.TOOL) {
            @Override
            public boolean onBuy(@NotNull Player player) {
                if (!(player instanceof ServerPlayer serverPlayer)) {
                    return false;
                }
                SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
                if (!gameWorldComponent.isRole(player, ModRoles.WIZARD)) {
                    return false;
                }
                var comp = org.agmas.noellesroles.component.ModComponents.WIZARD.maybeGet(serverPlayer).orElse(null);
                if (comp == null) {
                    return false;
                }
                return comp.quickCast(spell);
            }
        };
    }
}
