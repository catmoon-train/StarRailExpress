package org.agmas.noellesroles.game.modes.echo.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.game.modes.echo.action.EchoRollRequest;
import org.agmas.noellesroles.game.modes.echo.anim.EchoRollPayload;
import org.agmas.noellesroles.game.modes.echo.state.*;
import java.util.*;

/** 翻滚只在服务器批准后播放；复用潜行＋职业技能键，不抢占原来的推挡。 */
public final class EchoRollClient {
    private static final Map<UUID,EchoRollPayload> ACTIONS = new HashMap<>();
    private EchoRollClient() { }
    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(EchoRollPayload.ID,(p,c) -> c.client().execute(() -> {
            var client = c.client(); var view = EchoHud.view();
            if (client.level == null || view == null || !view.match().equals(p.match())
                    || !client.level.dimension().location().equals(p.dimension()) || p.start() < 0) return;
            if (!p.active()) { ACTIONS.remove(p.actor()); return; }
            if (EchoRollState.validDirection(p.x(),p.z())) ACTIONS.put(p.actor(),p);
        }));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            var view = EchoHud.view();
            if (client.level == null || view == null) { ACTIONS.clear(); return; }
            double tick = displayTick(0);
            ACTIONS.values().removeIf(p -> !view.match().equals(p.match()) || !client.level.dimension().location().equals(p.dimension())
                    || tick >= p.start() + EchoRollState.DURATION_TICKS);
        });
    }
    public static boolean handleAbility(Minecraft client) {
        var own = own();
        if (client.player == null || client.screen != null || own == null || own.faction() != EchoFaction.CARRIER || !client.options.keyShift.isDown()) return false;
        if (!own.phase().acceptsActions() || !own.life().standing() || own.controlled() || own.rolling()) return true;
        if (own.rollRemaining() > 0) {
            client.player.displayClientMessage(Component.translatable("echo.roll.cooldown",(own.rollRemaining()+19)/20),true); return true;
        }
        if (!client.player.onGround() || client.player.isPassenger() || client.player.isUsingItem() || client.player.isInWaterOrBubble() || client.player.isInLava()) {
            client.player.displayClientMessage(Component.translatable("echo.roll.grounded"),true); return true;
        }
        float forward = client.player.input.forwardImpulse, side = client.player.input.leftImpulse;
        Vec3 direction = forward == 0 && side == 0 ? new Vec3(0,0,1) : new Vec3(side,0,forward).normalize();
        direction = direction.yRot((float)Math.toRadians(-client.player.getYRot()));
        ClientPlayNetworking.send(new EchoRollRequest(own.matchId(),(float)direction.x,(float)direction.z));
        return true;
    }
    public static boolean localRolling() {
        var own = own();
        return own != null && own.faction() == EchoFaction.CARRIER && own.life().standing() && own.rolling();
    }
    private static EchoRoleData own() {
        var client=Minecraft.getInstance(); var view=EchoHud.view(); var own=EchoBagClient.data();
        if(client.player==null || client.level==null || client.player.isSpectator() || client.player.isCreative() || view==null || own==null
                || !view.match().equals(own.matchId()) || !view.dimension().equals(client.level.dimension().location())
                || view.state().phase()==EchoPhase.FINISHED || view.state().phase()==EchoPhase.SETTLING) return null;
        return own;
    }
    public static EchoRollPayload action(UUID actor) {
        var p = ACTIONS.get(actor); var view = EchoHud.view();
        if (p == null || view == null || !view.match().equals(p.match()) || displayTick(0) >= p.start()+EchoRollState.DURATION_TICKS) return null;
        return p;
    }
    public static float progress(UUID actor, float partial) {
        var p = action(actor); return p == null ? -1 : (float)Math.max(0,Math.min(1,(displayTick(partial)-p.start())/EchoRollState.DURATION_TICKS));
    }
    private static double displayTick(float partial) {
        var view = EchoHud.view(); if (view == null) return 0;
        return view.state().displayTick(EchoHud.age()) + (view.state().paused() || Minecraft.getInstance().isPaused() ? 0 : partial);
    }
    static Component hud(EchoRoleData own) {
        var client = Minecraft.getInstance();
        Component state = own.rollRemaining()>0 ? Component.translatable("echo.roll.seconds",(own.rollRemaining()+19)/20) : Component.translatable("echo.roll.ready");
        return Component.translatable("echo.roll.hud",client.options.keyShift.getTranslatedKeyMessage(),NoellesrolesClient.abilityBind.getTranslatedKeyMessage(),state);
    }
}
