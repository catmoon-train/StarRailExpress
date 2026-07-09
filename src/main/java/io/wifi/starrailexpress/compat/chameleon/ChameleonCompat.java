package io.wifi.starrailexpress.compat.chameleon;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 变色龙模式（meccha_chameleon）的对外门面。
 *
 * <p>
 * 本类不引用任何 {@code com.mecchachameleon.*} 类型，所有真正的交互都转发给
 * {@link ChameleonRoomBridge}，并且只在变色龙 mod 实际存在时才会去加载它。
 * 这样即便服务器没装变色龙，游戏模式与职业的类依然可以正常加载。
 * </p>
 */
public final class ChameleonCompat {
    public static final String MOD_ID = "meccha_chameleon";

    /** 猎人霰弹枪造成的死亡原因，对应语言键 {@code death_reason.canyuesama.chameleon_shotgun}。 */
    public static final ResourceLocation SHOTGUN_DEATH_REASON = SRE.canyueId("chameleon_shotgun");

    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD_ID);

    private ChameleonCompat() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    /**
     * 变色龙房间是否正在由 SRE 托管。变色龙自带的功能全部以此为开关：
     * 为 false 时，方块伪装、匍匐、以及 {@code /meccha} 相关能力全部不可用。
     */
    public static boolean isRoomActive() {
        return LOADED && ChameleonRoomBridge.isActive();
    }

    /**
     * 开局：接管一个变色龙房间，让涂装 / 方块伪装 / 霰弹枪生效。
     *
     * @param players      本局所有玩家
     * @param hunterIds    猎人（seeker）阵营的玩家
     * @param ammoLimit    霰弹枪弹药上限（<=0 为无限）
     * @param shotCooldown 射击冷却（tick）
     */
    public static void openRoom(List<ServerPlayer> players, Collection<UUID> hunterIds, int ammoLimit,
            int shotCooldown) {
        if (LOADED) {
            ChameleonRoomBridge.open(players, hunterIds, ammoLimit, shotCooldown);
        }
    }

    /** 结算：归还被暂存的头盔、清掉变色龙装备，并释放房间。 */
    public static void closeRoom(MinecraftServer server) {
        if (LOADED) {
            ChameleonRoomBridge.close(server);
        }
    }

    /** 这个 {@code com.mecchachameleon.game.Room} 是否由 SRE 托管。 */
    public static boolean ownsRoom(Object room) {
        return LOADED && ChameleonRoomBridge.owns(room);
    }

    /** 给玩家穿上整套变色龙衣服（房间装备，结算时会被自动回收）。 */
    public static void equipGear(ServerPlayer player) {
        if (LOADED) {
            ChameleonRoomBridge.equipGear(player);
        }
    }

    /** 退出方块伪装。死亡时调用，免得尸体和旁观者卡在方块形态里。 */
    public static void exitDisguise(ServerPlayer player) {
        if (LOADED) {
            ChameleonRoomBridge.exitDisguise(player);
        }
    }

    /** 猎人的开局物品（霰弹枪）。变色龙未安装时返回空列表。 */
    public static List<ItemStack> hunterItems() {
        return LOADED ? ChameleonRoomBridge.hunterItems() : List.of();
    }
}
