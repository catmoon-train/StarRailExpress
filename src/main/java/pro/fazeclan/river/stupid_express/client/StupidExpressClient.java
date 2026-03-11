package pro.fazeclan.river.stupid_express.client;

import java.util.ArrayList;
import java.util.Random;
import java.util.UUID;

import dev.doctor4t.ratatouille.client.util.ambience.AmbienceUtil;
import dev.doctor4t.ratatouille.client.util.ambience.BackgroundAmbience;
import dev.doctor4t.ratatouille.util.TextUtils;
import io.wifi.starrailexpress.client.StatusInit;
import io.wifi.starrailexpress.client.StatusInit.StatusBar;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.AllowOtherCameraType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.client.keybinds.SplitPersonalityKeybinds;
import pro.fazeclan.river.stupid_express.constants.SEItems;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;
import pro.fazeclan.river.stupid_express.network.SplitBackCamera;

public class StupidExpressClient implements ClientModInitializer {

    public static Player target;
    public static PlayerBodyEntity targetBody;

    static boolean isUsedRefugee = false;

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
            if (player != null) {
                if (player.hasEffect(MobEffects.WEAVING)) {
                    isUsedRefugee = true;
                    // 获取玩家当前位置
                    BlockPos playerPos = player.blockPosition();
                    Level level = client.level;

                    // 计时器，确保每秒只执行一次
                    if (client.level.getGameTime() % (20) == 0) { // 20 ticks = 1 second
                        // 随机数生成器
                        Random random = new Random();

                        // 在5x5x5范围内遍历所有方块
                        for (int x = -10; x <= 10; x++) {
                            for (int y = -2; y <= 8; y++) {
                                for (int z = -10; z <= 10; z++) {
                                    BlockPos targetPos = playerPos.offset(x, y, z);

                                    // 检查方块是否为完整方块（非空气且不透明）
                                    if (!level.getBlockState(targetPos).isAir()
                                            && level.getBlockState(targetPos).isSolidRender(level, targetPos)) {
                                        float rand = random.nextFloat();
                                        // 10%概率替换为下界疣块
                                        if (rand < 0.15f) {
                                            level.setBlock(targetPos, Blocks.NETHER_WART_BLOCK.defaultBlockState(), 3);
                                        }
                                        // 5%概率替换为下界岩
                                        else if (rand < 0.20f) {
                                            level.setBlock(targetPos, Blocks.NETHERRACK.defaultBlockState(), 3);
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {

                }
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
    }

    static {

    }

    private static void registerKeyEvents() {
        // 使用 Fabric Events 来处理按键按下事件
        final ArrayList<StatusBar> LOOSE_END_BARs = new ArrayList<>();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_WORLD_TICK.register(clientWorld -> {
            var instance = Minecraft.getInstance();
            if (instance == null)
                return;
            var player = instance.player;
            if (player == null)
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
}
