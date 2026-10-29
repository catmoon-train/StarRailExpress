package org.agmas.noellesroles.game.modes.echo.visual;

import org.agmas.noellesroles.game.modes.echo.cards.EchoSignalView;
import java.util.List;
import java.util.UUID;

/** 无 Minecraft/Gradle 依赖的回归入口：java -ea ...EchoVisualStateTest。 */
public final class EchoVisualStateTest {
    @org.junit.jupiter.api.Test
    void visualRulesRemainBoundedAndPrivate() { main(new String[0]); }
    @org.junit.jupiter.api.Test
    void onlyTrueTeamsShareOutlinesAndDownedViewKeepsOnlyTeammates() {
        for(var observer:org.agmas.noellesroles.game.modes.echo.state.EchoFaction.values())
            for(var target:org.agmas.noellesroles.game.modes.echo.state.EchoFaction.values())
                org.junit.jupiter.api.Assertions.assertEquals(observer==target && observer!=org.agmas.noellesroles.game.modes.echo.state.EchoFaction.BROKER,
                        EchoVisualState.teammates(observer,target));
        org.junit.jupiter.api.Assertions.assertFalse(EchoVisualState.teammates(null,null));
        UUID ally=UUID.randomUUID(), enemy=UUID.randomUUID();
        var signals=List.of(new EchoSignalView("teammate",ally,0,40),new EchoSignalView("tracking",enemy,0,40));
        org.junit.jupiter.api.Assertions.assertTrue(EchoVisualState.authorized(signals,ally,39,true));
        org.junit.jupiter.api.Assertions.assertTrue(EchoVisualState.authorized(signals,ally,39,false));
        org.junit.jupiter.api.Assertions.assertFalse(EchoVisualState.authorized(signals,ally,40,false));
        org.junit.jupiter.api.Assertions.assertFalse(EchoVisualState.authorized(signals,enemy,39,false));
        org.junit.jupiter.api.Assertions.assertFalse(EchoVisualState.authorized(List.of(),ally,39,true));
    }
    @org.junit.jupiter.api.Test
    void nearbyHunterAppliesTrembleOnlyToStandingCarriersDuringPlay() {
        var carrier = org.agmas.noellesroles.game.modes.echo.state.EchoFaction.CARRIER;
        org.junit.jupiter.api.Assertions.assertTrue(EchoVisualState.trembleEligible(carrier, true, true, EchoVisualState.pressure(23.9, 24)));
        org.junit.jupiter.api.Assertions.assertFalse(EchoVisualState.trembleEligible(carrier, true, true, EchoVisualState.pressure(24, 24)));
        org.junit.jupiter.api.Assertions.assertFalse(EchoVisualState.trembleEligible(carrier, false, true, 1F));
        org.junit.jupiter.api.Assertions.assertFalse(EchoVisualState.trembleEligible(carrier, true, false, 1F));
        for (var faction : org.agmas.noellesroles.game.modes.echo.state.EchoFaction.values())
            if (faction != carrier) org.junit.jupiter.api.Assertions.assertFalse(EchoVisualState.trembleEligible(faction, true, true, 1F));
        org.junit.jupiter.api.Assertions.assertFalse(EchoVisualState.trembleEligible(null, true, true, 1F));
        for (float pressure : new float[]{0F, -1F, Float.NaN, Float.POSITIVE_INFINITY})
            org.junit.jupiter.api.Assertions.assertFalse(EchoVisualState.trembleEligible(carrier, true, true, pressure));
    }
    public static void main(String[] args) {
        UUID target = new UUID(1, 2), other = new UUID(3, 4);
        for (String kind : List.of("tracking", "radar", "hearing", "hunt", "outline", "beacon")) {
            var signal = new EchoSignalView(kind, target, 0, 100);
            assert EchoVisualState.authorized(List.of(signal), target, 99);
            assert !EchoVisualState.authorized(List.of(signal), target, 100);
            assert !EchoVisualState.authorized(List.of(signal), other, 99);
            assert !EchoVisualState.authorized(List.of(signal), null, 99);
        }
        for (String kind : List.of("mask", "ally"))
            assert !EchoVisualState.authorized(List.of(new EchoSignalView(kind, target, 0, 100)), target, 1);
        for (String kind : List.of("tracking", "key_contract", "salvage", "cargo_pulse", "key_pulse"))
            assert !EchoVisualState.authorized(List.of(new EchoSignalView(kind, null, 0, 100)), target, 1);
        assert !EchoVisualState.authorized(List.of(), target, 1);
        assert EchoVisualState.INTERCEPTOR_SCALE == 1.25F;
        assert EchoVisualState.pressure(0, 24) == 1F;
        assert EchoVisualState.pressure(12, 24) == 0.25F;
        assert EchoVisualState.pressure(24, 24) == 0F;
        assert EchoVisualState.pressure(25, 24) == 0F;
        assert EchoVisualState.pressure(-1, 24) == 0F;
        assert EchoVisualState.pressure(Double.NaN, 24) == 0F;
        assert EchoVisualState.pressure(1, 0) == 0F;
        for (int frame = 0; frame < 10000; frame++) {
            double t = frame / 10D;
            for (boolean vertical : List.of(false, true)) {
                assert EchoVisualState.shake(0F, t, vertical) == 0D;
                assert EchoVisualState.shake(Float.NaN, t, vertical) == 0D;
                assert Math.abs(EchoVisualState.shake(10F, t, vertical)) <= EchoVisualState.MAX_SHAKE;
                assert EchoVisualState.shake(1F, t, vertical) == EchoVisualState.shake(1F, t, vertical);
                assert Math.abs(EchoVisualState.shake(0.2F, t, vertical)) <= EchoVisualState.MAX_SHAKE * 0.201D;
            }
        }
        System.out.println("EchoVisualStateTest passed (authorization, expiry, exclusions, distance falloff, bounded non-accumulating shake, 1.25 render scale)");
    }
}
