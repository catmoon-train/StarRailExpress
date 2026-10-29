package org.agmas.noellesroles.game.modes.echo.combat;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class EchoCutterStrikeTest {
    @Test void releaseRequiresSixteenTicksAndStartsNormalCooldown() {
        var state=new EchoCombatState();
        assertEquals(16,EchoCutterStrike.CHARGE_TICKS);
        assertFalse(EchoCutterStrike.commit(state,100));
        assertTrue(state.beginCutter(100));
        assertFalse(EchoCutterStrike.commit(state,115));
        assertEquals(0,state.snapshot().cutterReadyAt());
        assertFalse(EchoCutterStrike.commit(state,116),"提前松手已取消，不能沿用起点");
        assertTrue(state.beginCutter(116));
        assertTrue(EchoCutterStrike.commit(state,132));
        assertEquals(192,state.snapshot().cutterReadyAt());
        assertFalse(state.beginCutter(191));
        assertTrue(state.beginCutter(192));
        assertTrue(EchoCutterStrike.commit(state,208));
        state.recordCutterHit(208,EchoCombatState.Hit.DOWNED);
        assertFalse(state.beginCutter(327));
        assertTrue(state.beginCutter(328));
        assertTrue(EchoCutterStrike.commit(state,344));
    }
    @Test void queuedReleaseCannotGainChargeTimeAndCooldownStartsOnStrike() {
        var state=new EchoCombatState();
        assertTrue(state.beginCutter(0));
        assertFalse(EchoCutterStrike.commit(state,16,15));
        assertEquals(0,state.snapshot().cutterReadyAt());
        assertTrue(state.beginCutter(20));
        assertTrue(EchoCutterStrike.commit(state,37,36));
        assertEquals(97,state.snapshot().cutterReadyAt());
    }
    @Test void cancellationAndControlInvalidateEvenFullyChargedRelease() {
        var state=new EchoCombatState();
        assertTrue(state.beginCutter(0)); state.cancelCharge();
        assertFalse(EchoCutterStrike.commit(state,16));
        assertTrue(state.beginCutter(20)); state.control(35,25);
        assertFalse(EchoCutterStrike.commit(state,36));
        assertEquals(0,state.snapshot().cutterReadyAt());
    }
    @Test void feedbackOnlyForSuccessfulUnforcedWeaponDamage() {
        for(var hit:new EchoCombatState.Hit[]{EchoCombatState.Hit.DAMAGED,EchoCombatState.Hit.DOWNED,EchoCombatState.Hit.ELIMINATED}) {
            assertTrue(EchoCutterStrike.feedbackEligible(hit,false,false));
            assertFalse(EchoCutterStrike.feedbackEligible(hit,true,false));
            assertFalse(EchoCutterStrike.feedbackEligible(hit,false,true));
        }
        assertFalse(EchoCutterStrike.feedbackEligible(EchoCombatState.Hit.IGNORED,false,false));
        assertFalse(EchoCutterStrike.feedbackEligible(EchoCombatState.Hit.RESET_INTERCEPTOR,false,false));
    }
    @Test void shortImpulsePushesAwayAndStopsAtExpiryOrPause() {
        var impulse=EchoCutterStrike.impulse(100,0,0,1,0);
        assertNotNull(impulse); assertTrue(impulse.x()>0); assertEquals(0,impulse.z(),1e-8);
        assertTrue(EchoCutterStrike.pushing(impulse,103,false));
        assertFalse(EchoCutterStrike.pushing(impulse,104,false));
        assertFalse(EchoCutterStrike.pushing(impulse,100,true));
        assertNull(EchoCutterStrike.impulse(100,1,1,1,1));
    }
    @Test void inheritedChargeReleaseIsConsumedAndServerExecutesWithoutWindup() throws Exception {
        var source=Files.readString(Path.of("src/main/java/org/agmas/noellesroles/game/modes/echo/content/EchoCutterItem.java"));
        assertFalse(source.contains("InteractionResultHolder<ItemStack> use("));
        assertTrue(source.contains("match.combat().beginCutter(server)"));
        assertTrue(source.contains("getUseDuration(ItemStack stack,LivingEntity user) { return 72000; }"));
        assertTrue(source.contains("usedTicks<EchoCutterStrike.CHARGE_TICKS"));
        var release=source.substring(source.indexOf("onKnifeChargeReleased("));
        assertTrue(release.contains("match.releaseCutter(server)"));
        assertTrue(release.contains("return true;"));
        var service=Files.readString(Path.of("src/main/java/org/agmas/noellesroles/game/modes/echo/combat/EchoCombatService.java"));
        var strike=service.substring(service.indexOf("public void releaseCutter("),service.indexOf("private void executeStrike("));
        assertTrue(strike.contains("executeStrike(player)"));
        assertFalse(strike.contains("beginWindup"));
        var match=Files.readString(Path.of("src/main/java/org/agmas/noellesroles/game/modes/echo/runtime/EchoMatch.java"));
        assertTrue(match.contains("long releasedAt=matchTick()"));
        assertTrue(match.contains("combat.releaseCutter(player,releasedAt)"));
    }
    @Test void serverHitPenaltyUsesSlownessOneForThreeSeconds() throws Exception {
        var source=Files.readString(Path.of("src/main/java/org/agmas/noellesroles/game/modes/echo/combat/EchoCombatService.java"));
        var hook=source.substring(source.indexOf("private void recordCutterHit"),source.indexOf("public boolean canRescue"));
        assertTrue(hook.contains("state.recordCutterHit(match.matchTick(),hit)"));
        assertTrue(hook.contains("new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,60,0,false,false,true)"));
        assertTrue(hook.contains("project(player,true)"));
    }
}
