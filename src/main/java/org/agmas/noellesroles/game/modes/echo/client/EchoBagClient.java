package org.agmas.noellesroles.game.modes.echo.client;

import io.wifi.starrailexpress.api.data.RoleData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.agmas.noellesroles.game.modes.echo.inventory.EchoInventoryPayload;
import org.agmas.noellesroles.game.modes.echo.inventory.EchoRequestWindow;
import org.agmas.noellesroles.game.modes.echo.state.*;
import java.util.UUID;

/** 每连接顺序请求；不预测扣物，超时只读重同步，不重发写操作。 */
public final class EchoBagClient {
    private static UUID epoch;
    private static long sequence,pending;
    private static int waiting;
    private static UUID openingPack;
    private static EchoInventoryPayload.Operation pendingOperation;
    private static boolean cancelRequested;
    private static String localError = "";
    private EchoBagClient() { }
    public static EchoRoleData data() {
        var player=Minecraft.getInstance().player;
        return player==null?null:RoleData.getNullable(EchoRoleData.class,player);
    }
    public static void initialize() {
        EchoRulesClient.initialize();
        EchoSpecializationClient.initialize();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            var data=data();
            // 同一 epoch 的投影可能短暂不可用；保留在途请求与序号，等真正换连接再重置。
            if(data==null || data.epoch()==null) return;
            if(!data.epoch().equals(epoch)) { epoch=data.epoch(); sequence=0; pending=0; openingPack=null; pendingOperation=null; cancelRequested=false; localError=""; }
            if(pending!=0 && data.acknowledged()>=pending) {
                pending=0; waiting=0; pendingOperation=null;
                if(data.draft()==null || !data.draft().state().equals("CHANNELING")) openingPack=null;
            } else if(pending!=0 && ++waiting>100) {
                pending=0; waiting=0; pendingOperation=null; localError="timeout";
                send(EchoInventoryPayload.Operation.SNAPSHOT,null,null,0);
            }
        });
    }
    public static boolean waiting() { var data=data(); return pending!=0 || data!=null && !data.rulesReady(); }
    public static boolean beginning() { return pending != 0 && pendingOperation == EchoInventoryPayload.Operation.BEGIN_PACK; }
    public static String localError() { return localError; }
    public static boolean send(EchoInventoryPayload.Operation operation,UUID first,UUID second,int option) {
        return send(operation,first,second,option,net.minecraft.world.phys.Vec3.ZERO,net.minecraft.world.phys.Vec3.ZERO);
    }
    public static boolean send(EchoInventoryPayload.Operation operation,UUID first,UUID second,int option,net.minecraft.world.phys.Vec3 point,net.minecraft.world.phys.Vec3 secondPoint) {
        var data=data();
        if(data==null || data.matchId()==null || data.epoch()==null || data.bag()==null) { localError="unavailable"; return false; }
        if(!data.rulesReady() && operation!=EchoInventoryPayload.Operation.SNAPSHOT && operation!=EchoInventoryPayload.Operation.CANCEL_PACK && operation!=EchoInventoryPayload.Operation.TRADE_CANCEL) { localError="syncing"; return false; }
        if(pending!=0 && operation!=EchoInventoryPayload.Operation.SNAPSHOT && operation!=EchoInventoryPayload.Operation.CANCEL_PACK && operation!=EchoInventoryPayload.Operation.TRADE_CANCEL) { localError="busy"; return false; }
        if(!data.epoch().equals(epoch)) { epoch=data.epoch(); sequence=0; pending=0; }
        sequence=EchoRequestWindow.nextRequestId(sequence,data.acknowledged());
        long id=sequence; pending=id; waiting=0; pendingOperation=operation;
        if(operation!=EchoInventoryPayload.Operation.SNAPSHOT) localError="";
        if(operation==EchoInventoryPayload.Operation.BEGIN_PACK) { openingPack=first; cancelRequested=false; }
        var trade=data.trade();
        ClientPlayNetworking.send(new EchoInventoryPayload(1,data.matchId(),epoch,id,data.bag().revision(),operation,
                first==null?EchoInventoryPayload.NONE:first,second==null?EchoInventoryPayload.NONE:second,option,point,secondPoint,
                operation.trade() && trade!=null?trade.id():EchoInventoryPayload.NONE,operation.trade() && trade!=null?trade.version():0,java.util.List.of()));
        return true;
    }
    public static boolean tradeOffer(java.util.List<UUID> offer) {
        var data=data(); if(data==null || data.trade()==null || data.epoch()==null || data.bag()==null || waiting()) return false;
        if(!data.epoch().equals(epoch)) { epoch=data.epoch(); sequence=0; }
        sequence=EchoRequestWindow.nextRequestId(sequence,data.acknowledged());
        pending=sequence; waiting=0; var trade=data.trade();
        ClientPlayNetworking.send(new EchoInventoryPayload(1,data.matchId(),epoch,pending,data.bag().revision(),EchoInventoryPayload.Operation.TRADE_OFFER,
                EchoInventoryPayload.NONE,EchoInventoryPayload.NONE,0,net.minecraft.world.phys.Vec3.ZERO,net.minecraft.world.phys.Vec3.ZERO,trade.id(),trade.version(),offer));
        return true;
    }
    /** 显式取消与关闭界面共用同一幂等入口，避免旧投影导致重复取消。 */
    public static void cancelOpening() {
        if(cancelRequested) return;
        var data=data();
        if(data==null || data.draft()!=null && data.draft().state().equals("OFFERED")) return;
        UUID target=data.draft()!=null && data.draft().state().equals("CHANNELING") ? data.draft().offerId() : openingPack;
        if(target!=null && send(EchoInventoryPayload.Operation.CANCEL_PACK,target,null,0)) cancelRequested=true;
    }
    public static void close() {
        cancelOpening();
        openingPack=null;
    }
}
