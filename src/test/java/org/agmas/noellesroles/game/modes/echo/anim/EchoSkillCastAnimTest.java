package org.agmas.noellesroles.game.modes.echo.anim;

import org.agmas.noellesroles.game.modes.echo.state.EchoSkill;
import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** 施法动作：六专长各有一套、端点归零、误差有界。 */
class EchoSkillCastAnimTest {

    @Test void eachSkillMapsToDistinctStyle() {
        Set<EchoSkillCastAnim.Style> styles = EnumSet.noneOf(EchoSkillCastAnim.Style.class);
        for (EchoSkill skill : EchoSkill.values()) styles.add(EchoSkillCastAnim.style(skill));
        assertEquals(EchoSkill.values().length, styles.size());
    }

    @Test void everyStyleHasItsOwnGlowColor() {
        Set<Integer> colors = new java.util.HashSet<>();
        for (EchoSkillCastAnim.Style style : EchoSkillCastAnim.Style.values()) colors.add(EchoSkillCastAnim.glowColor(style));
        assertEquals(EchoSkillCastAnim.Style.values().length, colors.size());
    }

    @Test void firstPersonStartsAndEndsAtRest() {
        for (EchoSkillCastAnim.Style style : EchoSkillCastAnim.Style.values()) {
            for (int i = 0; i <= 1; i++) {
                EchoSkillCastAnim.Pose pose = EchoSkillCastAnim.firstPerson(i, style, 1);
                assertEquals(0F, pose.tx(), 1.0E-4, style + " tx");
                assertEquals(0F, pose.ty(), 1.0E-4, style + " ty");
                assertEquals(0F, pose.tz(), 1.0E-4, style + " tz");
                assertEquals(0F, pose.rx(), 1.0E-4, style + " rx");
                assertEquals(0F, pose.ry(), 1.0E-4, style + " ry");
                assertEquals(0F, pose.rz(), 1.0E-4, style + " rz");
            }
        }
    }

    @Test void everyStyleActuallyMoves() {
        for (EchoSkillCastAnim.Style style : EchoSkillCastAnim.Style.values()) {
            float peak = 0F;
            for (int i = 1; i < 100; i++) {
                EchoSkillCastAnim.Pose pose = EchoSkillCastAnim.firstPerson(i / 100F, style, 1);
                peak = Math.max(peak, Math.abs(pose.rx()) + Math.abs(pose.ty()) * 100F);
            }
            assertTrue(peak > 15F, style + " 的动作幅度过小");
        }
    }

    @Test void stylesAreNotCopiesOfEachOther() {
        EchoSkillCastAnim.Style[] styles = EchoSkillCastAnim.Style.values();
        for (int a = 0; a < styles.length; a++) {
            for (int b = a + 1; b < styles.length; b++) {
                float diff = 0F;
                for (int i = 1; i < 100; i++) {
                    float t = i / 100F;
                    EchoSkillCastAnim.Pose left = EchoSkillCastAnim.firstPerson(t, styles[a], 1);
                    EchoSkillCastAnim.Pose right = EchoSkillCastAnim.firstPerson(t, styles[b], 1);
                    diff += Math.abs(left.rx() - right.rx()) + Math.abs(left.ry() - right.ry());
                }
                assertTrue(diff > 1F, styles[a] + " 与 " + styles[b] + " 的动作过于相似");
            }
        }
    }

    @Test void sideMirrorsLaterallyOnly() {
        for (EchoSkillCastAnim.Style style : EchoSkillCastAnim.Style.values()) {
            EchoSkillCastAnim.Pose right = EchoSkillCastAnim.firstPerson(0.4F, style, 1);
            EchoSkillCastAnim.Pose left = EchoSkillCastAnim.firstPerson(0.4F, style, -1);
            assertEquals(right.tx(), -left.tx(), 1.0E-5);
            assertEquals(right.ty(), left.ty(), 1.0E-5);
            assertEquals(right.rx(), left.rx(), 1.0E-5);
            assertEquals(right.ry(), -left.ry(), 1.0E-5);
        }
    }

    @Test void thirdPersonCurvesAreFiniteAndZeroAtRest() {
        for (EchoSkillCastAnim.Style style : EchoSkillCastAnim.Style.values()) {
            float[] start = EchoSkillCastAnim.thirdPersonArm(0F, style, 1);
            float[] end = EchoSkillCastAnim.thirdPersonArm(1F, style, 1);
            for (int i = 0; i < 3; i++) {
                assertEquals(0F, start[i], 1.0E-4, style + " arm start " + i);
                assertEquals(0F, end[i], 1.0E-4, style + " arm end " + i);
            }
            EchoSkillCastAnim.Pose itemStart = EchoSkillCastAnim.thirdPersonItem(0F, style, 1);
            EchoSkillCastAnim.Pose itemEnd = EchoSkillCastAnim.thirdPersonItem(1F, style, 1);
            assertEquals(0F, itemStart.rx(), 1.0E-4);
            assertEquals(0F, itemEnd.rx(), 1.0E-4);
        }
    }

    @Test void castDurationIsShortEnoughToNotBlockGameplay() {
        assertTrue(EchoSkillCastAnim.CAST_TICKS > 0);
        assertTrue(EchoSkillCastAnim.CAST_TICKS <= 30);
    }

    @Test void nullSkillFallsBackToPress() {
        assertEquals(EchoSkillCastAnim.Style.PRESS, EchoSkillCastAnim.style(null));
    }
}