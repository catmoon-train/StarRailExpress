package org.agmas.noellesroles.game.night;

import com.sun.source.util.JavacTask;
import org.agmas.noellesroles.game.night.NightUndeadRules.Source;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.util.List;

/** 可直接用 JDK 21 运行的生产规则测试；不启动 Minecraft 或 Gradle。 */
public final class NightUndeadRulesCheck {
    private static int checks;

    public static void main(String[] args) throws Exception {
        for (Source source : Source.values()) {
            check(NightUndeadRules.restoreSource(source.name(), true, true) == source, "来源往返 " + source);
        }
        check(NightUndeadRules.restoreSource("", false, true) == Source.LORD, "普通旧档有主人");
        check(NightUndeadRules.restoreSource("", false, false) == Source.WILD, "普通旧档无主人");
        check(NightUndeadRules.restoreSource("", true, true) == Source.HALLOWEEN, "活动旧档有主人仍非领主");
        check(NightUndeadRules.restoreSource("", true, false) == Source.HALLOWEEN, "活动旧档无主人");
        check(NightUndeadRules.restoreSource("unknown", true, true) == Source.HALLOWEEN, "未知来源合理降级");
        check(NightUndeadRules.restoreSource("NIGHT", false, true) == Source.NIGHT, "黑夜来源不被主人字段覆盖");

        for (Source source : List.of(Source.NIGHT, Source.HALLOWEEN, Source.WILD)) {
            check(NightUndeadRules.canEngage(source, 144, 64, 400), "12 格与出生点 20 格边界 " + source);
            check(!NightUndeadRules.canEngage(source, Math.nextUp(144.0), 0, 0), "超 12 格立即丢失 " + source);
            check(!NightUndeadRules.canEngage(source, 1, Math.nextUp(400.0), 0), "自身越出生约束 " + source);
            check(!NightUndeadRules.canEngage(source, 1, 0, Math.nextUp(400.0)), "目标越出生约束 " + source);
            for (double invalid : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY}) {
                check(!NightUndeadRules.canEngage(source, invalid, 0, 0), "非法目标距离");
                check(!NightUndeadRules.canEngage(source, 0, invalid, 0), "非法自身锚点距离");
                check(!NightUndeadRules.canEngage(source, 0, 0, invalid), "非法目标锚点距离");
            }
            // 实际三维距离：竖直 13 格也不能被指令强行拉入；并非只约束水平面。
            check(!NightUndeadRules.canEngage(source, sq(0) + sq(13) + sq(0), 0, sq(13)), "三维警戒距离");
            // 穷举接近/离开过程，生产规则同样用于实体自然目标与临时指令。
            for (int halfBlocks = 0; halfBlocks <= 80; halfBlocks++) {
                double distance = halfBlocks / 2.0;
                check(NightUndeadRules.canEngage(source, sq(distance), 0, sq(distance)) == (distance <= 12),
                        "连续靠近/离开 " + source + " " + distance);
            }
        }
        check(NightUndeadRules.canEngage(Source.LORD, sq(80), sq(100), sq(180)), "领主号令不受活动距离约束");
        check(NightUndeadRules.isWithinRoam(64), "漫游 8 格边界");
        check(!NightUndeadRules.isWithinRoam(Math.nextUp(64.0)), "漫游不能越界");
        check(!NightUndeadRules.isWithinRoam(Double.NaN), "非法漫游距离");
        // 使用与实体相同的漫游点构造参数，验证整个出生区域而不是字符串断言。
        for (int i = 0; i < 360; i++) {
            double angle = Math.toRadians(i);
            for (double height : new double[]{-1, 0, 1}) {
                double x = Math.cos(angle) * 7;
                double z = Math.sin(angle) * 7;
                check(NightUndeadRules.isWithinRoam(sq(x) + sq(height) + sq(z)), "球内漫游");
            }
        }
        parseChangedSources();
        System.out.println("NightUndeadRulesCheck: " + checks + " checks passed; entity/renderer syntax parsed");
    }

    private static double sq(double n) { return n * n; }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    /** 只做 Java 语法分析，不假装完成 Minecraft 依赖的完整类型检查。 */
    private static void parseChangedSources() throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var files = compiler.getStandardFileManager(diagnostics, null, java.nio.charset.StandardCharsets.UTF_8)) {
            var sources = files.getJavaFileObjectsFromStrings(List.of(
                    "src/main/java/org/agmas/noellesroles/content/entity/UndeadEntity.java",
                    "src/main/java/org/agmas/noellesroles/client/renderer/UndeadEntityRenderer.java"));
            var task = (JavacTask) compiler.getTask(null, files, diagnostics, List.of("-proc:none"), null, sources);
            task.parse();
            for (var diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) throw new AssertionError(diagnostic.toString());
            }
        }
    }
}
