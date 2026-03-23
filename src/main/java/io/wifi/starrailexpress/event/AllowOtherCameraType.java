package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.CameraType;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 事件接口：决定是否覆盖客户端的摄像机视角类型。
 * 首个返回非 {@link ReturnCameraType#NO_CHANGE} 结果的监听器决定最终视角。
 *
 * <p>Event interface to override the client-side camera type.
 * The first listener returning a value other than {@link ReturnCameraType#NO_CHANGE}
 * determines the final camera type.
 */
public interface AllowOtherCameraType {

    /**
     * 摄像机视角返回类型枚举。
     *
     * <p>Enum representing the camera type to switch to.
     */
    public enum ReturnCameraType {
        /** 不改变视角（默认） / Do not change the camera type (default). */
        NO_CHANGE,
        /** 切换至第一人称视角 / Switch to first-person view. */
        FIRST_PERSON,
        /** 切换至第三人称背面视角 / Switch to third-person back view. */
        THIRD_PERSON_BACK,
        /** 切换至第三人称正面视角 / Switch to third-person front view. */
        THIRD_PERSON_FRONT
    }

    /**
     * 决定是否覆盖摄像机视角类型的事件。
     * 首个返回非 {@link ReturnCameraType#NO_CHANGE} 的监听器结果生效。
     *
     * <p>Event to override the client-side camera type.
     * The first listener returning a value other than {@link ReturnCameraType#NO_CHANGE} wins.
     */
    Event<AllowOtherCameraType> EVENT = createArrayBacked(AllowOtherCameraType.class,
            listeners -> (original, localPlayer) -> {
                ReturnCameraType returnvalue = ReturnCameraType.NO_CHANGE;
                for (AllowOtherCameraType listener : listeners) {
                    ReturnCameraType temp = listener.onGetCameraType(original, localPlayer);
                    if (temp != null) {
                        if (temp != ReturnCameraType.NO_CHANGE) {
                            return temp;
                        }
                    }
                }
                return returnvalue;
            });

    /**
     * 决定是否覆盖本地玩家的摄像机视角类型。
     *
     * <p>Determines whether to override the local player's camera type.
     *
     * @param original   当前摄像机视角类型 / the current camera type
     * @param localplayer 本地玩家 / the local player
     * @return 目标摄像机视角类型；返回 {@link ReturnCameraType#NO_CHANGE} 则不改变 /
     *         the target camera type; return {@link ReturnCameraType#NO_CHANGE} to leave it unchanged
     */
    ReturnCameraType onGetCameraType(CameraType original, LocalPlayer localplayer);
}
