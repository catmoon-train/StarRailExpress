package pro.fazeclan.river.stupid_express.constants;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnGameTrueStarted;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.SRE;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.ModifierAssigned;
import org.agmas.harpymodloader.events.ModifierRemoved;
import org.agmas.harpymodloader.events.ResetPlayerEvent;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.Modifier;

import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.modifier.lovers.cca.LoversComponent;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent;
import pro.fazeclan.river.stupid_express.modifier.allergist.cca.AllergistComponent;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SkinSplitPersonalityComponent;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;

public class SEModifiers {

    // Attribute modifier for tiny players
    private static AttributeModifier tinyModifier = new AttributeModifier(
            StupidExpress.id("tiny_modifier"), -0.15, AttributeModifier.Operation.ADD_VALUE);

    // Attribute modifier for tall players
    private static AttributeModifier tallModifier = new AttributeModifier(
            StupidExpress.id("tall_modifier"), 0.0763, AttributeModifier.Operation.ADD_VALUE);

    public static Modifier LOVERS = HMLModifiers.registerModifier(new Modifier(
            StupidExpress.id("lovers"),
            0xf38aff,
            null,
            null,
            false,
            false));

    public static Modifier REFUGEE = HMLModifiers.registerModifier(new Modifier(
            StupidExpress.id("refugee"),
            0x55ff55,
            null,
            null,
            false,
            false));

    public static Modifier TINY = HMLModifiers.registerModifier(new Modifier(
            StupidExpress.id("tiny"),
            new Color(255, 166, 0).getRGB(),
            null,
            null,
            false,
            false));

    public static Modifier TALL = HMLModifiers.registerModifier(new Modifier(
            StupidExpress.id("tall"),
            new Color(0, 255, 0).getRGB(),
            null,
            null,
            false,
            false));

    public static Modifier FEATHER = HMLModifiers.registerModifier(new Modifier(
            StupidExpress.id("feather"),
            new Color(255, 236, 161).getRGB(),
            null,
            null,
            false,
            false));

    public static Modifier MAGNATE = HMLModifiers.registerModifier(new Modifier(
            StupidExpress.id("magnate"),
            new Color(255, 255, 0).getRGB(),
            null,
            null,
            false,
            false));

    public static Modifier TASKMASTER = HMLModifiers.registerModifier(new Modifier(
            StupidExpress.id("taskmaster"),
            new Color(255, 51, 153).getRGB(),
            null,
            null,
            false,
            false));

    public static Modifier ALLERGIST = HMLModifiers.registerModifier(new Modifier(
            StupidExpress.id("allergist"),
            new Color(112, 255, 162).getRGB(),
            null,
            null,
            false,
            false));

    public static Modifier CURSED = HMLModifiers.registerModifier(new Modifier(
            StupidExpress.id("cursed"),
            new Color(75, 0, 130).getRGB(),
            null,
            null,
            false,
            false));

    public static Modifier SECRETIVE = HMLModifiers.registerModifier(new Modifier(
            StupidExpress.id("secretive"),
            new Color(50, 50, 50).getRGB(),
            null,
            null,
            false,
            false));

    public static Modifier KNIGHT = HMLModifiers.registerModifier(new Modifier(
            StupidExpress.id("knight"),
            new Color(192, 192, 192).getRGB(),
            null,
            null,
            false,
            false));

    public static Modifier SPLIT_PERSONALITY = HMLModifiers.registerModifier(new Modifier(
            StupidExpress.id("split_personality"),
            new Color(138, 43, 226).getRGB(),
            null,
            null,
            false,
            true));

    public static void init() {
        REFUGEE.civilianOnly = true;
        SPLIT_PERSONALITY.civilianOnly = true;
        assignModifierComponents();
        pro.fazeclan.river.stupid_express.modifier.magnate.MagnatePassiveIncomeHandler.init();
        pro.fazeclan.river.stupid_express.modifier.cursed.CursedHandler.init();
        pro.fazeclan.river.stupid_express.modifier.knight.KnightHandler.init();
        pro.fazeclan.river.stupid_express.modifier.split_personality.SplitPersonalityHandler.init();

        ResetPlayerEvent.EVENT.register(player -> {
            var splitPersonalityComponent2 = pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent.KEY
                    .get(player);
            splitPersonalityComponent2.init();
            SkinSplitPersonalityComponent skinSplitPersonalityComponent2 = SkinSplitPersonalityComponent.KEY
                    .get(player);
            skinSplitPersonalityComponent2.clear();
        });
        SRE.canCollide.add(p -> {
            var modifiers = WorldModifierComponent.KEY.get(p.level());
            if (modifiers.isModifier(p.getUUID(), FEATHER)) {
                return true;
            }
            return false;
        });
    }

    public static void assignModifierComponents() {
        ModifierRemoved.EVENT.register((player, modifier) -> {
            if (modifier.equals(SPLIT_PERSONALITY)) {
                var a = SplitPersonalityComponent.KEY.get(player);
                if (a != null) {
                    a.init();
                }
                var b = SkinSplitPersonalityComponent.KEY.get(player);
                if (b != null) {
                    b.clear();
                }
            }
        });
        /// LOVERS
        ModifierAssigned.EVENT.register(((player, modifier) -> {
            if (!modifier.equals(LOVERS)) {
                return;
            }
            if (!(player instanceof ServerPlayer lover)) {
                return;
            }

            var level = lover.serverLevel();

            // choose second lover
            ServerPlayer loverTwo = null;
            var arrs = new ArrayList<>(level.players());
            Collections.shuffle(arrs);
            for (var can_i_love : arrs) {
                if (GameUtils.isPlayerAliveAndSurvival(can_i_love)) {
                    if (!lover.equals(can_i_love)) {
                        loverTwo = can_i_love;
                        break;
                    }
                }
            }
            if (loverTwo == null) {
                // 没有就不分配啦！
                WorldModifierComponent.KEY.get(player.level()).removeModifier(player.getUUID(), modifier);
                StupidExpress.LOGGER.info("{} couldn't find it's lover, remove it's modifier.",
                        player.getScoreboardName());
                return;
            }
            // assign both lovers
            var loverComponentOne = LoversComponent.KEY.get(lover);

            loverComponentOne.setLover(loverTwo.getUUID());
            loverComponentOne.sync();

            var loverComponentTwo = LoversComponent.KEY.get(loverTwo);

            loverComponentTwo.setLover(lover.getUUID());
            loverComponentTwo.sync();

            var worldModifierComponent = WorldModifierComponent.KEY.get(level);
            worldModifierComponent.addModifier(loverTwo.getUUID(), LOVERS); // visually show lovers on the other player
        }));
        OnGameTrueStarted.EVENT.register((serverLevel)->{
            // var wmc = WorldModifierComponent.KEY.get(serverLevel);
            // for (ServerPlayer player : serverLevel.players()) {
            //     if(wmc.isModifier(player, SEModifiers.SPLIT_PERSONALITY)){
            //         // SplitPersonalityComponent.KEY.get(player);
            //     }
            // }
        });
        /// SPLIT_PERSONALITY
        ModifierAssigned.EVENT.register(((player, modifier) -> {
            if (!modifier.equals(SPLIT_PERSONALITY)) {
                return;
            }
            if (!(player instanceof ServerPlayer person)) {
                return;
            }

            var level = person.serverLevel();
            var gameComponent = SREGameWorldComponent.KEY.get(level);

            // 选择另一个同阵营作为第二人格
            var fatherRole = gameComponent.getRole(player);
            /**
             * 0:平民,
             * 1:中立
             * 2:杀手
             * 3:偏狼中立
             */
            int fatherRoleType = 0;
            if (fatherRole != null) {
                if (fatherRole.isInnocent()) {
                    fatherRoleType = 0;
                } else if (fatherRole.isNeutralForKiller() && fatherRole.isNeutrals()) {
                    fatherRoleType = 3;
                } else if (SREGameWorldComponent.isKillerTeamRoleStatic(fatherRole)) {
                    fatherRoleType = 2;
                } else {
                    fatherRoleType = 1;
                }
            }
            ServerPlayer secondPersonality = null;
            var arrs = new ArrayList<>(level.players());
            Collections.shuffle(arrs);
            for (var candidate : arrs) {
                if (GameUtils.isPlayerAliveAndSurvival(candidate)) {
                    if (!person.equals(candidate)) {
                        if (gameComponent != null) {
                            var role = gameComponent.getRole(candidate);
                            if (role != null) {
                                if (fatherRoleType == 0) {
                                    if (role.isInnocent() && !role.isVigilanteTeam()) {
                                        secondPersonality = candidate;
                                        break;
                                    }
                                } else if (fatherRoleType == 1) {
                                    if ((!role.isInnocent() && !role.canUseKiller())
                                            || (role.isNeutrals()) && !role.isNeutralForKiller()) {
                                        secondPersonality = candidate;
                                        break;
                                    }
                                } else if (fatherRoleType == 3) {
                                    if ((role.isNeutrals() && role.isNeutralForKiller())) {
                                        secondPersonality = candidate;
                                        break;
                                    }
                                } else {
                                    if (!role.isInnocent() && role.canUseKiller() && !role.isNeutrals()) {
                                        secondPersonality = candidate;
                                        break;
                                    }
                                }

                            }
                        }
                    }
                }
            }
            if (secondPersonality == null) {
                // 没有就不分配啦！
                WorldModifierComponent.KEY.get(player.level()).removeModifier(player.getUUID(), modifier);
                StupidExpress.LOGGER.info("{} couldn't find it's split personality, remove it's modifier.",
                        player.getScoreboardName());
                return;
            }
            // 给第二人格添加修饰符
            var worldModifierComponent = WorldModifierComponent.KEY.get(level);
            worldModifierComponent.addModifier(secondPersonality.getUUID(), SPLIT_PERSONALITY); 

            // 为两个人格都设置SplitPersonalityComponent
            var componentOne = SplitPersonalityComponent.KEY.get(person);
            componentOne.setMainPersonality(person.getUUID());
            componentOne.setSecondPersonality(secondPersonality.getUUID());
            componentOne.setCurrentActivePerson(person.getUUID()); // 主人格是第一个被激活的
            componentOne.setMainPersonalityChoice(SplitPersonalityComponent.ChoiceType.SACRIFICE);
            componentOne.setSecondPersonalityChoice(SplitPersonalityComponent.ChoiceType.SACRIFICE);
            componentOne.sync();

            var componentTwo = SplitPersonalityComponent.KEY.get(secondPersonality);
            componentTwo.setMainPersonality(person.getUUID());
            componentTwo.setSecondPersonality(secondPersonality.getUUID());
            componentTwo.setMainPersonalityChoice(SplitPersonalityComponent.ChoiceType.SACRIFICE);
            componentTwo.setSecondPersonalityChoice(SplitPersonalityComponent.ChoiceType.SACRIFICE);
            componentTwo.setCurrentActivePerson(person.getUUID()); // 主人格是第一个被激活的
            componentTwo.sync();

            final var skinSplitPersonalityComponent = SkinSplitPersonalityComponent.KEY.get(secondPersonality);
            skinSplitPersonalityComponent.setSkinToAppearAs(player.getUUID());
            skinSplitPersonalityComponent.sync();
        }));

        /// TINY & TALL & FEATHER & ALLERGIST & CURSED & SECRETIVE & KNIGHT &
        /// SPLIT_PERSONALITY TINY & FEATHER & ALLERGIST & CURSED & SECRETIVE & KNIGHT &
        ModifierAssigned.EVENT.register(((player, modifier) -> {
            var worldModifierComponent = WorldModifierComponent.KEY.get(player.level());
            if (modifier.equals(TINY)) {
                // Cannot assign TALL if player has TINY
                if (worldModifierComponent.isModifier(player.getUUID(), TALL)) {
                    worldModifierComponent.removeModifier(player.getUUID(), TALL);
                    player.getAttribute(Attributes.SCALE).removeModifier(tallModifier);
                }
                player.getAttribute(Attributes.SCALE).removeModifier(tinyModifier);
                player.getAttribute(Attributes.SCALE).addPermanentModifier(tinyModifier);
            }
            if (modifier.equals(TALL)) {
                // Cannot assign TINY if player has TALL
                if (worldModifierComponent.isModifier(player.getUUID(), TINY)) {
                    worldModifierComponent.removeModifier(player.getUUID(), TINY);
                    player.getAttribute(Attributes.SCALE).removeModifier(tinyModifier);
                }
                player.getAttribute(Attributes.SCALE).removeModifier(tallModifier);
                player.getAttribute(Attributes.SCALE).addPermanentModifier(tallModifier);
            }
            // Double-check: ensure TINY and TALL are never both present
            if (worldModifierComponent.isModifier(player.getUUID(), TINY)
                    && worldModifierComponent.isModifier(player.getUUID(), TALL)) {
                // If both are present, remove TALL (arbitrary choice)
                worldModifierComponent.removeModifier(player.getUUID(), TALL);
                player.getAttribute(Attributes.SCALE).removeModifier(tallModifier);
            }
            if (modifier.equals(FEATHER)) {
                // Feather modifier no longer has slow falling effect
            }
            if (modifier.equals(ALLERGIST)) {
                var allergistComponent = AllergistComponent.KEY.get(player);
                allergistComponent.setAllergist(player.getUUID());
                allergistComponent.sync();
            }
            if (modifier.equals(CURSED)) {
                var cursedComponent = pro.fazeclan.river.stupid_express.modifier.cursed.cca.CursedComponent.KEY
                        .get(player);
                cursedComponent.setCursed(player.getUUID());
                cursedComponent.sync();
            }
            if (modifier.equals(SECRETIVE)) {
                var secretiveComponent = pro.fazeclan.river.stupid_express.modifier.secretive.cca.SecretiveComponent.KEY
                        .get(player);
                secretiveComponent.setSecretive(player.getUUID());
                secretiveComponent.sync();
            }
            if (modifier.equals(KNIGHT)) {
                var knightComponent = pro.fazeclan.river.stupid_express.modifier.knight.cca.KnightComponent.KEY
                        .get(player);
                knightComponent.setKnight(player.getUUID());
                knightComponent.sync();
            }

        }));

        ResetPlayerEvent.EVENT.register(player -> {
            // Remove tiny modifier
            player.getAttribute(Attributes.SCALE).removeModifier(tinyModifier);
            // Remove tall modifier
            player.getAttribute(Attributes.SCALE).removeModifier(tallModifier);
            // Reset lovers component
            var component = LoversComponent.KEY.get(player);
            component.reset();
            component.sync();
            // Reset allergist component
            var allergistComponent = AllergistComponent.KEY.get(player);
            allergistComponent.init();
            allergistComponent.sync();
            // Reset cursed component
            var cursedComponent = pro.fazeclan.river.stupid_express.modifier.cursed.cca.CursedComponent.KEY.get(player);
            cursedComponent.init();
            cursedComponent.sync();
            // Reset secretive component
            var secretiveComponent = pro.fazeclan.river.stupid_express.modifier.secretive.cca.SecretiveComponent.KEY
                    .get(player);
            secretiveComponent.reset();
            secretiveComponent.sync();
            // Reset knight component
            var knightComponent = pro.fazeclan.river.stupid_express.modifier.knight.cca.KnightComponent.KEY.get(player);
            knightComponent.init();
            knightComponent.sync();
            // Reset split personality component
            var splitPersonalityComponent = pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent.KEY
                    .get(player);
            // 清理库存数据
            splitPersonalityComponent.init();
            SkinSplitPersonalityComponent skinSplitPersonalityComponent = SkinSplitPersonalityComponent.KEY.get(player);
            skinSplitPersonalityComponent.clear();
            splitPersonalityComponent.sync();
            // Reset refugee component
            var refugeeC = RefugeeComponent.KEY.get(player.level());
            if (refugeeC != null) {
                refugeeC.reset();
            }
        });

    }
}
