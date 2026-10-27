package org.agmas.noellesroles.game.modes.echo.rules;

import com.google.gson.*;
import org.agmas.noellesroles.game.modes.echo.cards.*;
import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EchoCardTuningTest {
    private static final String PREFIX="noellesroles:echo/";
    @Test void strictPerCardWhitelistBoundsAndRelationshipsRejectIgnoredValues() {
        for(String card:List.of("\"missing\":{}","\"steady_flow\":{\"clearStall\":2}","\"beacon_boost\":{\"boostPermille\":0}",
                "\"sound_key\":{\"openSeconds\":16}","\"stall_pulse\":{\"stallSeconds\":30}","\"door_wedge\":{\"extendSeconds\":7}",
                "\"key_disruptor\":{\"dropKey\":0}","\"pack_hook\":{\"packs\":2}","\"short_dash\":{\"durationTicks\":80}",
                "\"short_dash\":{\"durationSeconds\":0.01}","\"short_dash\":{\"speedBonus\":0.6}","\"short_dash\":{\"weight\":0}",
                "\"short_dash\":{\"weight\":1.1}","\"short_dash\":{\"speedBonus\":1e999}","\"team_radar\":{\"range\":129}",
                "\"global_pulse\":{\"maxTargets\":4}","\"silent_rig\":{\"charges\":9}","\"cargo_harness\":{\"cargoCapacity\":5}",
                "\"hunt_mark\":{\"intervalSeconds\":0.5}","\"silent_dummy\":{\"durationSeconds\":1}","\"false_dispatch\":{\"range\":1}"))
            assertThrows(IOException.class,() -> EchoRuleRepository.parse("{\"cards\":{"+card+"}}"),card);
        assertThrows(IOException.class,() -> EchoRuleRepository.parse("{\"trade\":{\"range\":0}}"));
        assertThrows(IOException.class,() -> EchoRuleRepository.parse("{\"cards\":{\"short_dash\":{\"weight\":2,\"weight\":3}}}"));
    }
    @Test void allSupportedDefaultCardValuesRoundTripAndDefaultsNormalizeAway() throws Exception {
        var cards=new JsonObject();
        for(var definition:EchoCardRegistry.all()) {
            var params=new JsonObject(); params.addProperty("weight",definition.weight());
            definition.params().forEach((key,value) -> {
                if(EchoCardTuning.allowed(definition,key)) params.addProperty(key.endsWith("Ticks")?key.substring(0,key.length()-5)+"Seconds":key,key.endsWith("Ticks")?value/20:value);
            }); cards.add(definition.shortId(),params);
        }
        var root=new JsonObject(); root.add("cards",cards);
        var parsed=EchoRuleRepository.parse(root.toString());
        assertEquals(EchoRuleConfig.DEFAULT,parsed); assertTrue(parsed.cards().overrides().isEmpty());
        assertEquals(EchoRuleRepository.hash(EchoRuleConfig.DEFAULT),EchoRuleRepository.hash(parsed));
    }
    @Test void immutableTuningAndClientJsonHaveSameEffectiveNumbersAndHash() throws Exception {
        var fields=new HashMap<>(Map.of("durationTicks",80D,"speedBonus",0.4)); Map<String,Map<String,Double>> outer=new HashMap<>(Map.of("short_dash",fields));
        var tuning=new EchoCardTuning(outer); fields.put("durationTicks",999D); outer.clear();
        assertEquals(80D,tuning.overrides().get("short_dash").get("durationTicks"));
        assertThrows(UnsupportedOperationException.class,() -> tuning.overrides().get("short_dash").clear());
        var rules=EchoRuleRepository.parse("{\"cards\":{\"short_dash\":{\"durationSeconds\":4,\"speedBonus\":0.4}},\"trade\":{\"range\":7,\"offerSeconds\":5,\"proposalCooldownSeconds\":2}}");
        assertEquals(80D,rules.card(PREFIX+"short_dash").params().get("durationTicks")); assertEquals(7,rules.trade().range());
        assertEquals(100,rules.trade().offerTicks()); assertEquals(40,rules.trade().proposalCooldownTicks());
        var client=EchoRuleRepository.parse(EchoRuleRepository.json(rules));
        assertEquals(rules,client); assertEquals(EchoRuleRepository.hash(rules),EchoRuleRepository.hash(client));
        assertNotEquals(EchoRuleRepository.hash(rules),EchoRuleRepository.hash(EchoRuleConfig.DEFAULT));
        assertEquals(60D,EchoCardRegistry.require(PREFIX+"short_dash").params().get("durationTicks"));
    }
    @Test void coreAliasesAreTheSingleSourceForEffectiveCardDescriptionsAndRuntime() throws Exception {
        var rules=EchoRuleRepository.parse("{\"gate\":{\"coreOpenSeconds\":16,\"masterOpenSeconds\":22,\"wedgeExtraSeconds\":6,\"wedgeMaxSeconds\":30},\"beacon\":{\"stallSeconds\":7}}");
        assertEquals(320D,rules.card(PREFIX+"sound_key").params().get("openTicks"));
        assertEquals(440D,rules.card(PREFIX+"master_key").params().get("openTicks"));
        assertEquals(120D,rules.card(PREFIX+"door_wedge").params().get("extendTicks"));
        assertEquals(600D,rules.card(PREFIX+"door_wedge").params().get("maxOpenTicks"));
        assertEquals(140D,rules.card(PREFIX+"stall_pulse").params().get("stallTicks"));
    }
    @Test void actualPackDrawUsesConfiguredWeightsAndCachesFrozenDefinitions() throws Exception {
        var rules=EchoRuleRepository.parse("{\"cards\":{\"silent_collector\":{\"weight\":100,\"durationSeconds\":9}}}");
        var bounds=new ArrayList<Integer>();
        var random=new Random(1) {
            @Override public double nextDouble() { return 0; }
            @Override public int nextInt(int bound) { bounds.add(bound); return 0; }
        };
        var enabled=Set.of(PREFIX+"silent_collector",PREFIX+"decoy_dummy",PREFIX+"short_dash");
        var context=new EchoDraftService.Context(EchoFaction.CARRIER,enabled,true,true); UUID owner=UUID.randomUUID();
        var pack=new EchoPackDraft(UUID.randomUUID(),EchoDraftService.PackQuality.COMMON,rules);
        assertTrue(pack.begin(owner,0,true,context,random)); assertEquals(122,bounds.getFirst());
        assertTrue(pack.cancel(owner,1)); assertTrue(pack.begin(owner,201,true,context,random));
        pack.advance(231); assertEquals(3,bounds.size());
        var chosen=pack.visibleCandidates(owner).stream().filter(c -> c.shortId().equals("silent_collector")).findFirst().orElseThrow();
        assertEquals(100,chosen.weight()); assertEquals(180D,chosen.params().get("durationTicks"));
    }
    @Test void configuredEffectsAndSceneParametersReachPureRuntimeState() throws Exception {
        var rules=EchoRuleRepository.parse("{\"cards\":{\"silent_rig\":{\"durationSeconds\":2,\"charges\":3},\"silent_dummy\":{\"deceptionRange\":4,\"deceptionSeconds\":1},\"decoy_dummy\":{\"soundIntervalSeconds\":1}}}");
        var c=rules.card(PREFIX+"silent_rig"); var effects=new EchoEffectState();
        assertTrue(effects.add(EchoEffectState.Marker.SILENCE,new EchoEffectState.Mark(c.id(),40,c.params().get("charges").intValue(),0,false,0),0));
        assertTrue(effects.consume(EchoEffectState.Marker.SILENCE,1)); assertTrue(effects.consume(EchoEffectState.Marker.SILENCE,2));
        assertEquals(1,effects.mark(EchoEffectState.Marker.SILENCE,39).orElseThrow().charges()); assertTrue(effects.mark(EchoEffectState.Marker.SILENCE,40).isEmpty());
        var state=new EchoSceneState(); var point=new EchoSceneState.Point(0,0,0); var dummy=rules.card(PREFIX+"silent_dummy");
        assertTrue(state.add(UUID.randomUUID(),List.of(new EchoSceneState.Spec(EchoSceneState.Kind.SILENT_DUMMY,point,0,0,400,-1,dummy.params().get("deceptionTicks").intValue(),dummy.params().get("deceptionRange"),0)),0));
        assertTrue(state.deception(new EchoSceneState.Point(4,0,0),19).isPresent()); assertTrue(state.deception(new EchoSceneState.Point(4.1,0,0),19).isEmpty()); assertTrue(state.deception(point,20).isEmpty());
        int interval=rules.card(PREFIX+"decoy_dummy").params().get("soundIntervalTicks").intValue();
        assertTrue(state.add(UUID.randomUUID(),List.of(new EchoSceneState.Spec(EchoSceneState.Kind.DUMMY,point,0,0,300,interval,0,0,9)),0));
        assertEquals(9,state.all().getLast().soundRange()); assertEquals(1,state.dueSounds(0).size()); assertTrue(state.dueSounds(19).isEmpty()); assertEquals(1,state.dueSounds(20).size());
    }
    @Test void everyMechanicAndDisplayedNumericParameterHasThreeLanguageKeys() throws Exception {
        Set<String> previous=null;
        for(String language:List.of("zh_cn","zh_tw","en_us")) {
            var json=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/echo_freight/lang/"+language+".json"),StandardCharsets.UTF_8)).getAsJsonObject();
            if(previous!=null) assertEquals(previous,json.keySet()); previous=new HashSet<>(json.keySet());
            for(var c:EchoCardRegistry.all()) {
                assertTrue(json.has("echo.card."+c.shortId()+".mechanic"),c.id());
                for(String key:c.params().keySet()) if(!Set.of("layers","unlock","dropKey","packs").contains(key)) assertTrue(json.has("echo.param."+key),key);
            }
        }
    }
}
