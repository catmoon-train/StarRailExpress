package pro.fazeclan.river.stupid_express.client;

import dev.doctor4t.ratatouille.client.util.ambience.AmbienceUtil;
import dev.doctor4t.ratatouille.client.util.ambience.BackgroundAmbience;
import dev.doctor4t.ratatouille.util.TextUtils;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.StatusInit;
import io.wifi.starrailexpress.client.StatusInit.StatusBar;
import io.wifi.starrailexpress.event.AllowOtherCameraType;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.PlayerSkin.Model;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.utils.RoleUtils;

import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.client.keybinds.SplitPersonalityKeybinds;
import pro.fazeclan.river.stupid_express.constants.SEItems;
import pro.fazeclan.river.stupid_express.constants.SERoles;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;
import pro.fazeclan.river.stupid_express.network.SplitBackCamera;
import pro.fazeclan.river.stupid_express.role.arsonist.cca.DousedPlayerComponent;

import java.util.*;

public class StupidExpressClient implements ClientModInitializer {

    public static boolean isSplitPerson = false;
    static boolean isUsedRefugee = false;

    private static final Random WEAVING_RANDOM = new Random();
    private static final Map<BlockPos, BlockState> WEAVING_ORIGINAL_BLOCKS = new LinkedHashMap<>();
    private static final List<Map.Entry<BlockPos, BlockState>> WEAVING_RESTORE_QUEUE = new ArrayList<>();

    private static final int WEAVING_NORMAL_XZ_RADIUS = 10;
    private static final int WEAVING_START_XZ_RADIUS = 22;
    private static final int WEAVING_MIN_Y = -2;
    private static final int WEAVING_MAX_Y = 8;
    private static final int WEAVING_RESTORE_PER_TICK = 120;
    private static final float WEAVING_START_WART_CHANCE = 0.32f;
    private static final float WEAVING_START_NETHERRACK_CHANCE = 0.42f;
    private static final float WEAVING_NORMAL_WART_CHANCE = 0.15f;
    private static final float WEAVING_NORMAL_NETHERRACK_CHANCE = 0.20f;
    private static final float WEAVING_SHADER_FADE_IN_SPEED = 0.08f;
    private static final float WEAVING_SHADER_FADE_OUT_SPEED = 0.025f;

    private static boolean weavingActiveLastTick = false;
    private static float weavingShaderStrength = 0.0f;

    public static float getWeavingShaderStrength() {
        return weavingShaderStrength;
    }

    @Override
    public void onInitializeClient() {

        // p.playNotifySound(StupidExpress.SOUND_REGUGEE, SoundSource.AMBIENT, 0.5f,
        // 1.0f);
        AmbienceUtil.registerBackgroundAmbience(
                new BackgroundAmbience(StupidExpress.SOUND_REGUGEE,
                        player -> {
                            if (SREClient.gameComponent == null)
                                return false;
                            var refugeeC = RefugeeComponent.KEY.get(player.level());
                            if (refugeeC.isAnyRevivals) {
                                return true;
                            }
                            return false;
                        },
                        1));

        ItemTooltipCallback.EVENT.register((itemStack, tooltipContext, tooltipFlag, list) -> {
            if (itemStack.is(SEItems.JERRY_CAN))
                list.addAll(TextUtils.getTooltipForItem(itemStack.getItem(), Style.EMPTY.withColor(8421504)));
            if (itemStack.is(SEItems.LIGHTER))
                list.addAll(TextUtils.getTooltipForItem(itemStack.getItem(), Style.EMPTY.withColor(8421504)));
        });

        // 初始化按键绑定
        SplitPersonalityKeybinds.registerKeyPressCallbacks();

        // 注册按键事件监听
        registerKeyEvents();

        // 注册网络接收器
        registerClientNetworkReceivers();

        // 注册背包界面事件
        registerInventoryEvents();
        ClientPlayNetworking.registerGlobalReceiver(SplitBackCamera.TYPE, (payload, context) -> {
            Minecraft.getInstance().setCameraEntity(Minecraft.getInstance().player);
        });

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            LocalPlayer player = client.player;
            if (SREClient.gameComponent == null)
                return;
            if (player != null) {
                var component = SplitPersonalityComponent.KEY.get(player);

                // 如果是旁观者（非活跃人格），完全隐藏其渲染
                if (component != null && component.getMainPersonality() != null && !component.isCurrentlyActive()) {
                    AbstractClientPlayer mainPlayer = (AbstractClientPlayer) player.level()
                            .getPlayerByUUID(component.getMainPersonality());

                    // 只在主人格存在时隐藏旁观者
                    if (mainPlayer != null && mainPlayer != player) {
                        isSplitPerson = true;

                    } else
                        isSplitPerson = false;
                } else
                    isSplitPerson = false;
                boolean weavingActive = player.hasEffect(MobEffects.WEAVING);
                if (weavingActive) {
                    isUsedRefugee = true;
                    BlockPos playerPos = player.blockPosition();
                    Level level = client.level;

                    if (!weavingActiveLastTick) {
                        applyWeavingPollution(level, playerPos, WEAVING_START_XZ_RADIUS,
                                WEAVING_START_WART_CHANCE,
                                WEAVING_START_NETHERRACK_CHANCE);
                    }

                    if (client.level.getGameTime() % 20 == 0) {
                        applyWeavingPollution(level, playerPos, WEAVING_NORMAL_XZ_RADIUS,
                                WEAVING_NORMAL_WART_CHANCE,
                                WEAVING_NORMAL_NETHERRACK_CHANCE);
                    }

                    weavingShaderStrength = Math.min(1.0f, weavingShaderStrength + WEAVING_SHADER_FADE_IN_SPEED);
                } else {
                    if (weavingActiveLastTick) {
                        beginWeavingRestore();
                    }
                    tickWeavingRestore(client.level);
                    weavingShaderStrength = Math.max(0.0f, weavingShaderStrength - WEAVING_SHADER_FADE_OUT_SPEED);

                }

                weavingActiveLastTick = weavingActive;
            }
        });

        AllowOtherCameraType.EVENT.register((original, localPlayer) -> {
            final var splitPersonalityComponent = SplitPersonalityComponent.KEY.get(Minecraft.getInstance().player);
            if (splitPersonalityComponent != null && splitPersonalityComponent.getMainPersonality() != null
                    && splitPersonalityComponent.getSecondPersonality() != null) {
                UUID currentActive = splitPersonalityComponent.getCurrentActivePerson();
                if (!currentActive.equals(localPlayer.getUUID())) {
                    switch (original) {
                        case FIRST_PERSON:
                            return AllowOtherCameraType.ReturnCameraType.FIRST_PERSON;

                        case THIRD_PERSON_BACK:
                            return AllowOtherCameraType.ReturnCameraType.THIRD_PERSON_BACK;

                        case THIRD_PERSON_FRONT:
                            return AllowOtherCameraType.ReturnCameraType.THIRD_PERSON_FRONT;
                        default:
                            break;
                    }
                }
            }
            return AllowOtherCameraType.ReturnCameraType.NO_CHANGE;
        });

        // 注册纵火犯HUD
        RoleHudRenderCallback.EVENT.register(SERoles.ARSONIST.identifier(), (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null)
                return;
            if (SREClient.gameComponent == null)
                return;
            if (!SREClient.isRole(SERoles.ARSONIST))
                return;
            if (!SREClient.isPlayerAliveAndInSurvival())
                return;

            DousedPlayerComponent ownComp = DousedPlayerComponent.KEY.get(client.player);
            int dousedCount = ownComp.dousedCount;

            // 计算存活玩家数（排除观察者和创造模式）
            long alivePlayers = client.player.level().players().stream()
                    .filter(p -> !p.isSpectator() && !p.isCreative() && p.getHealth() > 0)
                    .count();
            int requiredCount = (int) Math.ceil(alivePlayers * 0.3);

            var font = client.font;
            int xBase = context.guiWidth() - 10;
            int yBase = context.guiHeight() - font.lineHeight - 10;
            if (dousedCount >= requiredCount) {
                Component readyText = Component.translatable("hud.stupid_express.arsonist.ignition_ready");
                context.drawString(font, readyText, xBase - font.width(readyText), yBase, 0xfc9526);
            } else {
                Component douseText = Component.translatable("hud.stupid_express.arsonist.douse_progress",
                        dousedCount, requiredCount);
                context.drawString(font, douseText, xBase - font.width(douseText), yBase, 0xfc9526);
            }
        });
    }

    static {

    }

    private static void registerKeyEvents() {
        // 伪装效果：拥有 DISGUISE 效果的玩家皮肤替换为对应变体的伪装皮肤
        // 效果等级（amplifier）决定使用 DisguiseVariants 中的哪套皮肤
        OnGettingPlayerSkin.EVENT.register((player, originalSkin) -> {
            if (player.hasEffect(org.agmas.noellesroles.init.ModEffects.DISGUISE)) {
                var instance = player.getEffect(org.agmas.noellesroles.init.ModEffects.DISGUISE);
                if (instance != null) {
                    var variant = io.wifi.starrailexpress.content.item.DisguiseVariants
                            .byAmplifier(instance.getAmplifier());
                    if (variant != null) {
                        return OnGettingPlayerSkin.PlayerSkinResult.playerSkin(
                                SRE.id(variant.skinPath()),
                                variant.slim() ? Model.SLIM : Model.WIDE);
                    }
                }
            }
            return null;
        });
        // 难民时旁观看时全员皮肤改为默认的皮肤
        OnGettingPlayerSkin.EVENT.register((player, originalSkin) -> {
            if (RoleUtils.isPlayerTheJob(player, TMMRoles.LOOSE_END)
                    || RoleUtils.isPlayerTheJob(player, SpecialGameModeRoles.SUPER_LOOSE_END)) {
                return OnGettingPlayerSkin.PlayerSkinResult
                        .playerSkin(SRE.id("textures/entity/custom_psycho/th_sariel.png"), Model.SLIM);
            }
            if (SREClient.getLooseEndPenalty()) {
                PlayerSkin.Model model = originalSkin.model();
                boolean isSLIM = (model == PlayerSkin.Model.SLIM);
                if (isSLIM) {
                    return OnGettingPlayerSkin.PlayerSkinResult.alexSlim();
                } else {
                    return OnGettingPlayerSkin.PlayerSkinResult.steveWide();
                }
            }
            return null;
        });
        // 使用 Fabric Events 来处理按键按下事件
        final ArrayList<StatusBar> LOOSE_END_BARs = new ArrayList<>();
        ClientTickEvents.END_WORLD_TICK.register(clientWorld -> {
            var instance = Minecraft.getInstance();
            if (instance == null)
                return;
            var player = instance.player;
            if (player == null)
                return;
            if (SREClient.gameComponent == null)
                return;
            if (LOOSE_END_BARs.size() == 0) {
                String loose_end_bar_name = Component.translatable("gui.stupid_express.refugee.loose_end_time")
                        .getString();
                // StupidExpress.LOGGER.info(loose_end_bar_name);
                LOOSE_END_BARs.add(StatusInit.statusBars.put(
                        "loose_end",
                        new StatusInit.StatusBar(
                                "loose_end",
                                loose_end_bar_name,
                                () -> {
                                    final var level = Minecraft.getInstance().player.level();
                                    var refugeeC = RefugeeComponent.KEY.get(level);
                                    var refugeeList = refugeeC.getPendingRevivals();
                                    if (refugeeList.size() > 0) {
                                        var data = refugeeList.get(0);
                                        return (float) (level.getGameTime() - data.getRevivalTime()) / 3000f;
                                    } else {
                                        return 0f;
                                    }
                                })));
            }

            // 处理人格切换按键
            if (SplitPersonalityKeybinds.SWITCH_PERSONALITY_KEY.consumeClick()) {
                SplitPersonalityKeybinds.handleSwitchPersonalityKey(player);
            }
        });
    }

    private static void registerClientNetworkReceivers() {
        // 客户端网络接收器注册
        // 实际的网络包处理已在SplitPersonalityPackets中注册
    }

    private static void registerInventoryEvents() {

    }

    private static void applyWeavingPollution(Level level, BlockPos playerPos, int xzRadius,
            float wartChance, float netherrackChance) {
        for (int x = -xzRadius; x <= xzRadius; x++) {
            for (int y = WEAVING_MIN_Y; y <= WEAVING_MAX_Y; y++) {
                for (int z = -xzRadius; z <= xzRadius; z++) {
                    BlockPos targetPos = playerPos.offset(x, y, z);
                    BlockState state = level.getBlockState(targetPos);
                    if (state.isAir() || !state.isSolidRender(level, targetPos)) {
                        continue;
                    }

                    float rand = WEAVING_RANDOM.nextFloat();
                    BlockState replacement = null;
                    if (rand < wartChance) {
                        replacement = Blocks.NETHER_WART_BLOCK.defaultBlockState();
                    } else if (rand < netherrackChance) {
                        replacement = Blocks.NETHERRACK.defaultBlockState();
                    }

                    if (replacement == null || state.is(replacement.getBlock())) {
                        continue;
                    }

                    WEAVING_ORIGINAL_BLOCKS.putIfAbsent(targetPos.immutable(), state);
                    level.setBlock(targetPos, replacement, 3);
                }
            }
        }
    }

    private static void beginWeavingRestore() {
        if (WEAVING_ORIGINAL_BLOCKS.isEmpty()) {
            return;
        }
        WEAVING_RESTORE_QUEUE.clear();
        WEAVING_RESTORE_QUEUE.addAll(WEAVING_ORIGINAL_BLOCKS.entrySet());
    }

    private static void tickWeavingRestore(Level level) {
        if (level == null || WEAVING_RESTORE_QUEUE.isEmpty()) {
            if (WEAVING_RESTORE_QUEUE.isEmpty()) {
                WEAVING_ORIGINAL_BLOCKS.clear();
            }
            return;
        }

        int restoreCount = Math.min(WEAVING_RESTORE_PER_TICK, WEAVING_RESTORE_QUEUE.size());
        for (int i = 0; i < restoreCount; i++) {
            Map.Entry<BlockPos, BlockState> entry = WEAVING_RESTORE_QUEUE.remove(WEAVING_RESTORE_QUEUE.size() - 1);
            level.setBlock(entry.getKey(), entry.getValue(), 3);
            WEAVING_ORIGINAL_BLOCKS.remove(entry.getKey());
        }
    }
}
