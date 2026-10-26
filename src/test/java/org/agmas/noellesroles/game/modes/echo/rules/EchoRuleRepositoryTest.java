package org.agmas.noellesroles.game.modes.echo.rules;

import org.agmas.noellesroles.game.modes.echo.cards.*;
import org.agmas.noellesroles.game.modes.echo.combat.EchoCombatState;
import org.agmas.noellesroles.game.modes.echo.contract.EchoBlackCrystalState;
import org.agmas.noellesroles.game.modes.echo.objective.*;
import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EchoRuleRepositoryTest {
    @TempDir Path root;
    @Test void slowerBeaconAndLongerDownedDefaultsKeepExplicitOverrides() throws Exception {
        assertEquals(1000,EchoRuleConfig.DEFAULT.beacon().chargeTicks(),"默认充能 50 秒（原 100 秒的一半）");
        assertEquals(1350,EchoRuleConfig.DEFAULT.combat().bleedTicks());
        var custom=EchoRuleRepository.parse("{\"beacon\":{\"chargeSeconds\":60},\"combat\":{\"bleedSeconds\":45}}");
        assertEquals(1200,custom.beacon().chargeTicks()); assertEquals(900,custom.combat().bleedTicks());
    }
    @Test void defaultsAndFullSecondsExampleRoundTripWithStableEffectiveHash() throws Exception {
        assertEquals(EchoRuleConfig.DEFAULT,EchoRuleRepository.parse("{}"));
        assertEquals(EchoRuleConfig.DEFAULT,EchoRuleRepository.parse(EchoRuleRepository.example()));
        var a=EchoRuleRepository.parse("{\"logistics\":{\"capacity\":3},\"gate\":{\"coreOpenSeconds\":16}}");
        var b=EchoRuleRepository.parse("{\"gate\":{\"coreOpenSeconds\":16.0},\"logistics\":{\"capacity\":3.0}}");
        assertEquals(a,b); assertEquals(EchoRuleRepository.hash(a),EchoRuleRepository.hash(b));
        assertNotEquals(EchoRuleRepository.hash(a),EchoRuleRepository.hash(EchoRuleConfig.DEFAULT));
    }
    @Test void checkedInRuleExampleMatchesActualDefaultParser() throws Exception {
        assertEquals(EchoRuleConfig.DEFAULT,EchoRuleRepository.parse(Files.readString(Path.of("docs/echo-freight/rules.example.json"),StandardCharsets.UTF_8)));
    }
    @Test void strictSchemaRejectsUnknownDuplicateMalformedAndWrongTypes() {
        for(String json:List.of("", "[]", "null", "{\"roster\":6}", "{\"schemaVersion\":2}", "{\"logistics\":{\"capacity\":2,\"capacity\":3}}",
                "{\"logistics\":{\"capacity\":\"3\"}}", "{\"logistics\":null}", "{\"gate\":{\"coreOpenTicks\":300}}", "{} {}", "\uFEFF{}", "{\"signals\":{\"directionRange\":true}}"))
            assertThrows(IOException.class,() -> EchoRuleRepository.parse(json),json);
    }
    @Test void durationsMustBeBoundedExactTicksAndRangesCapacityFinite() throws Exception {
        for(String json:List.of("{\"draft\":{\"channelSeconds\":0.01}}", "{\"draft\":{\"channelSeconds\":0}}", "{\"combat\":{\"bleedSeconds\":-1}}",
                "{\"logistics\":{\"capacity\":5}}", "{\"logistics\":{\"capacity\":2.5}}", "{\"signals\":{\"directionRange\":129}}", "{\"signals\":{\"directionRange\":1e999}}",
                "{\"draft\":{\"choiceSeconds\":3601}}", "{\"draft\":{\"openingSpeed\":0.4}}"))
            assertThrows(IOException.class,() -> EchoRuleRepository.parse(json),json);
        assertEquals(1,EchoRuleRepository.parse("{\"draft\":{\"channelSeconds\":0.05}}").draft().channelTicks());
    }
    @Test void relationshipsAndProbabilityGuaranteesCannotBeBroken() {
        for(String json:List.of("{\"gate\":{\"wedgeMaxSeconds\":14}}", "{\"gate\":{\"overloadSeconds\":60}}",
                "{\"beacon\":{\"stallSeconds\":0}}", "{\"combat\":{\"rescueSeconds\":68}}", "{\"combat\":{\"environmentProtectionSeconds\":1}}",
                "{\"draft\":{\"common\":{\"common\":0.9}}}", "{\"draft\":{\"rareFirst\":{\"common\":1,\"rare\":0}}}"))
            assertThrows(IOException.class,() -> EchoRuleRepository.parse(json),json);
    }
    @Test void missingFileUsesDefaultsButInvalidFileAndUnsafeTargetNeverDo() throws Exception {
        assertEquals("absent",EchoRuleRepository.load(root).sourceDigest());
        Path dir=Files.createDirectory(root.resolve("echo_freight")),file=dir.resolve("rules.json");
        Files.writeString(file,"{bad",StandardCharsets.UTF_8); assertThrows(IOException.class,() -> EchoRuleRepository.load(root));
        Files.write(file,new byte[]{(byte)0xc3,(byte)0x28}); assertThrows(IOException.class,() -> EchoRuleRepository.load(root));
        Files.writeString(file," ".repeat(65537),StandardCharsets.UTF_8); assertThrows(IOException.class,() -> EchoRuleRepository.load(root));
        Files.delete(file); Files.createDirectory(file); assertThrows(IOException.class,() -> EchoRuleRepository.load(root));
    }
    @Test void loadedSnapshotIsFrozenAndSourceDigestChangesOnExternalEdits() throws Exception {
        Path file=Files.createDirectory(root.resolve("echo_freight")).resolve("rules.json");
        Files.writeString(file,"{\"logistics\":{\"capacity\":3}}",StandardCharsets.UTF_8); var first=EchoRuleRepository.load(root);
        Files.writeString(file,"{\"logistics\":{\"capacity\":4}}",StandardCharsets.UTF_8); var second=EchoRuleRepository.load(root);
        assertEquals(3,first.values().logistics().capacity()); assertEquals(4,second.values().logistics().capacity());
        assertNotEquals(first.hash(),second.hash()); assertNotEquals(first.sourceDigest(),EchoRuleRepository.digest(root));
    }
    @Test void beaconUsesConfiguredCrystalCountRateAndStallDeadline() throws Exception {
        var rules=EchoRuleRepository.parse("{\"beacon\":{\"requiredCrystals\":2,\"chargeSeconds\":2,\"escortBonus\":0.5,\"stallSeconds\":2}}").beacon();
        var beacon=new EchoBeaconState(rules); assertTrue(beacon.submit()); assertTrue(beacon.submit()); assertFalse(beacon.submit());
        for(int n=0;n<10;n++) assertFalse(beacon.tick(n,0)); assertEquals(250,beacon.progressPermille());
        assertTrue(beacon.stall(10,40)); assertEquals(250,beacon.progressPermille(),"迟滞不扣进度"); assertEquals(50,beacon.stalledUntil());
        assertFalse(beacon.tick(11,3)); assertFalse(beacon.canStall(11));
        for(int n=0;n<14;n++) beacon.tick(50+n,2); assertFalse(beacon.active()); assertTrue(beacon.tick(64,2));
    }
    @Test void combatUsesConfiguredChargeCooldownBleedAndRescueProtection() throws Exception {
        var rules=EchoRuleRepository.parse("{\"combat\":{\"cutterChargeSeconds\":0.1,\"cutterCooldownSeconds\":0.5,\"bleedSeconds\":2,\"rescueSeconds\":1,\"rescueProtectionSeconds\":3}}").combat();
        var attacker=new EchoCombatState(rules); assertTrue(attacker.beginCutter(0)); assertTrue(attacker.releaseCutter(16)); assertEquals(76,attacker.snapshot().cutterReadyAt(),"配置可以更长，但不能低于 3 秒");
        var victim=new EchoCombatState(rules); assertEquals(EchoCombatState.Hit.DAMAGED,victim.hit(10,false,false)); assertEquals(EchoCombatState.Hit.DOWNED,victim.hit(10,false,false)); assertEquals(50,victim.bleedUntil());
        assertTrue(victim.rescue(20)); assertEquals(80,victim.snapshot().protectedUntil()); assertEquals(EchoCombatState.Hit.IGNORED,victim.hit(79,false,false));
        var bleeding=new EchoCombatState(rules); bleeding.hit(0,false,false); bleeding.hit(0,false,false); assertFalse(bleeding.expireBleed(39)); assertTrue(bleeding.expireBleed(40));
    }
    @Test void draftUsesConfiguredDeadlinesAndCachedCandidatesAcrossCancellation() throws Exception {
        var rules=EchoRuleRepository.parse("{\"draft\":{\"channelSeconds\":0.1,\"cancelCooldownSeconds\":0.3,\"choiceSeconds\":0.2}}").draft();
        var owner=UUID.randomUUID(); var pack=new EchoPackDraft(UUID.randomUUID(),EchoDraftService.PackQuality.COMMON,rules); var random=new Random(7);
        var context=EchoDraftService.Context.all(EchoFaction.CARRIER,true,true); assertTrue(pack.begin(owner,0,true,context,random)); assertTrue(pack.cancel(owner,1)); assertEquals(7,pack.readyAt());
        assertFalse(pack.begin(owner,6,true,context,random)); assertTrue(pack.begin(owner,7,true,context,random));
        pack.advance(8); assertEquals(EchoPackDraft.State.CHANNELING,pack.state()); pack.advance(9); assertEquals(13,pack.choiceEnd());
        assertEquals(EchoPackDraft.State.OFFERED,pack.state()); assertTrue(pack.advance(12).isEmpty()); assertTrue(pack.advance(13).orElseThrow().automatic());
    }
    @Test void configuredDistributionsKeepDistinctCandidatesAndRealRarity() throws Exception {
        var rules=EchoRuleRepository.parse("{\"draft\":{\"blackFirst\":{\"rare\":0,\"epic\":1},\"blackOther\":{\"common\":0,\"rare\":0,\"epic\":1}}}").draft();
        for(var faction:EchoFaction.values()) {
            var offer=EchoDraftService.draw(EchoDraftService.PackQuality.BLACK_MARKET,EchoDraftService.Context.all(faction,true,true),new Random(12),rules);
            assertEquals(3,offer.candidates().stream().map(EchoCardDefinition::id).distinct().count());
            assertTrue(offer.candidates().stream().allMatch(c -> c.rarity()==EchoCardDefinition.Rarity.EPIC));
        }
    }
    @Test void keySupplyBlackRecoveryGateAndOpeningSpeedUseInjectedValues() {
        var keys=new EchoKeySchedule(List.of("a","b","c"),0,20); assertTrue(keys.due(19).isEmpty()); assertTrue(keys.due(20).isPresent()); assertTrue(keys.due(39).isEmpty());
        var black=new EchoBlackCrystalState(List.of("node"),40); var id=UUID.randomUUID(); black.collect(UUID.randomUUID(),"node",id,0); assertFalse(black.available("node",39)); assertTrue(black.available("node",40));
        var gate=new EchoGateState(80); gate.beginEvacuation(); assertTrue(gate.overload(0)); assertFalse(gate.evacuationOpen(79)); assertTrue(gate.evacuationOpen(80));
        assertEquals(-0.2,new EchoEffectState().speedDelta(0,0,true,0.8),1e-9);
    }
}
