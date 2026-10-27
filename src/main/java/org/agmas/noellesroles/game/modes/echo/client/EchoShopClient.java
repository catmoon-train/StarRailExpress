package org.agmas.noellesroles.game.modes.echo.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.agmas.noellesroles.game.modes.echo.economy.EchoEconomy;
import org.agmas.noellesroles.game.modes.echo.economy.EchoPackOffer;
import org.agmas.noellesroles.game.modes.echo.economy.EchoShopPayload;
import org.agmas.noellesroles.game.modes.echo.economy.EchoShopService;

import java.util.HashSet;
import java.util.Set;

/** 商店客户端：只发请求与缓存「已售出」，不本地扣款。 */
public final class EchoShopClient {
    private static final Set<String> sold = new HashSet<>();
    private static long match;

    private EchoShopClient() { }

    public static void initialize() {
        // 售出状态在购买成功后就地登记；重连后由服务端拒绝兜底。
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                org.agmas.noellesroles.game.modes.echo.economy.EchoVendingOpenPayload.ID,
                (payload, context) -> context.client().execute(() -> openVending(null, payload.pos().asLong())));
    }

    public static int balance() {
        // 余额由服务端钱包同步到本人组件；客户端只读本地投影，不做任何扣减。
        var player = Minecraft.getInstance().player;
        if (player == null) return 0;
        return io.wifi.starrailexpress.cca.SREPlayerSkinsComponent.KEY.get(player).getCoinNum();
    }

    public static boolean soldOut(long machinePos, EchoShopService.VendingGood good) {
        return sold.contains(key(machinePos, good));
    }

    public static void openPacks(net.minecraft.client.gui.screens.Screen parent) {
        var client = Minecraft.getInstance();
        client.setScreen(new EchoShopScreen(parent, EchoShopScreen.Mode.PACKS, Long.MIN_VALUE));
    }

    public static void openVending(net.minecraft.client.gui.screens.Screen parent, long machinePos) {
        var client = Minecraft.getInstance();
        client.setScreen(new EchoShopScreen(parent, EchoShopScreen.Mode.VENDING, machinePos));
    }

    public static void buyPack(EchoPackOffer offer) {
        var data = EchoBagClient.data();
        if (data == null || data.matchId() == null) return;
        ClientPlayNetworking.send(EchoShopPayload.pack(data.matchId(), offer));
    }

    public static void buyGood(long machinePos, EchoShopService.VendingGood good) {
        var data = EchoBagClient.data();
        if (data == null || data.matchId() == null) return;
        // 本地先标记，避免连点；服务端拒绝时下次开界面会重新校验。
        sold.add(key(machinePos, good));
        ClientPlayNetworking.send(EchoShopPayload.good(data.matchId(), machinePos, good));
    }

    /** 换局时清掉缓存，避免上一局的售出状态带到新局。 */
    public static void tick() {
        var data = EchoBagClient.data();
        if (data == null || data.matchId() == null) { sold.clear(); match = 0; return; }
        if (data.matchId().getMostSignificantBits() != match) {
            sold.clear();
            match = data.matchId().getMostSignificantBits();
        }
    }

    private static String key(long machinePos, EchoShopService.VendingGood good) {
        return machinePos + "|" + good.id();
    }
}
