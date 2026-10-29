package org.agmas.noellesroles.game.roles.killer.undead_lord;

import com.sun.source.util.JavacTask;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/** 仅依赖 JDK 的模块检查：解析源码语法、验证接线契约，并执行生产代码中的纯逻辑灵魂池。 */
public final class UndeadLordReworkCheck {
    private static final Path JAVA = Path.of("src/main/java/org/agmas/noellesroles");
    private static int checks;

    public static void main(String[] args) throws Exception {
        String scratch = args.length > 0 ? args[0] : System.getenv("PI_SCRATCH_DIR");
        if (scratch == null) throw new IllegalArgumentException("请传入会话 scratch 目录");
        Path output = Files.createTempDirectory(Path.of(scratch), "undead-lord-check-");
        List<Path> files = List.of(
                JAVA.resolve("game/roles/killer/undead_lord/UndeadLordRole.java"),
                JAVA.resolve("game/roles/killer/undead_lord/UndeadLordHandler.java"),
                JAVA.resolve("role_data/killer/UndeadLordRoleData.java"),
                JAVA.resolve("client/hud/roles/UndeadLordHud.java"),
                JAVA.resolve("config/NoellesRolesConfig.java"));
        parseSyntax(files);
        String role = Files.readString(files.get(0));
        String handler = Files.readString(files.get(1));
        String data = Files.readString(files.get(2));
        String hud = Files.readString(files.get(3));
        String config = Files.readString(files.get(4));

        for (String[] entry : new String[][]{
                {"InitialSouls", "2"}, {"MaxSouls", "6"}, {"CommandCooldownSeconds", "20"},
                {"CommandDurationSeconds", "8"}, {"CommandRange", "24"}, {"SummonCooldownSeconds", "30"},
                {"SummonLifetimeSeconds", "60"}, {"ReviveCooldownSeconds", "20"}, {"MaxActive", "4"}}) {
            require(Pattern.compile("public (?:int|double) undeadLord" + entry[0] + " = " + entry[1] + ";")
                    .matcher(config).find(), "配置默认值 " + entry[0]);
        }
        require(count(role, "RoleSkill.register(") == 1 && role.contains(".shifted(true)")
                && !role.contains("EVENT.register"), "构造器一次固定注册两技能，不注册实例事件");
        require(count(role, ".noCastCCA(true)") == 2, "主动技能使用各自冷却，不污染旧全局技能冷却");
        String command = block(role, "public static boolean commandSkill(");
        require(!command.contains("context.target()") && command.contains("aimedPlayer(player, range)")
                && command.contains("isKillerTeamRoleStatic") && command.contains("player.isAlliedTo(target)"),
                "号令服务器选目标并排除阵营/记分板队友");
        require(role.contains("ClipContext.Block.COLLIDER") && role.contains("candidate.getBoundingBox()")
                && role.contains("GameUtils.isPlayerAliveAndSurvival(candidate)")
                && role.contains("visibleRayEnd(player, 3.0)"), "服务器射线含墙体、活人和三格尸体距离");
        require(before(command, "data.commandUndead(", "context.setSkillCooldown(")
                && !command.contains("soulPool.spend"), "无亡灵/非法目标不消费号令冷却，号令不扣魂");
        String summon = block(role, "public static boolean summonSkill(");
        require(before(summon, "data.trySoulSummon()", "context.setSkillCooldown("), "招魂失败不消费技能冷却");
        String soulSummon = block(data, "public boolean trySoulSummon(");
        require(before(soulSummon, "spawnUndeadAt(", "soulPool.spend()")
                && soulSummon.contains("!canRaiseFromCorpse()") && soulSummon.contains("noCollision(bounds)"),
                "招魂仅生成成功才扣魂，检查上限及可用空间");
        require(handler.contains("bodyData.isFakeBody") && handler.contains("bodyData.nonInteractable")
                && handler.contains("canReachCorpse(lord, body)") && handler.contains("body.getPlayerUuid() == null")
                && handler.contains("GameUtils.isPlayerAliveAndSurvival(deceased)"), "仅合法真实死者尸体可复苏");
        require(before(handler, "bodyData.nonInteractable = true", "data.spawnUndeadAt(")
                && before(handler, "data.spawnUndeadAt(", "body.discard()")
                && before(handler, "body.discard()", "data.finishCorpseRaise()")
                && handler.contains("finally") && handler.contains("!raised && !body.isRemoved()")
                && handler.contains("lord.server.isSameThread()"), "尸体主线程原子占用、成功销毁、失败回滚");
        require(!handler.contains("onSuccessfulKill") && role.contains("victim instanceof ServerPlayer killed")
                && role.contains("GameUtils.isPlayerAliveAndSurvival(killed)"), "尸体和已死玩家不能刷魂");
        require(handler.contains("OnGameEnd.EVENT.register") && handler.contains("data.clear()")
                && block(data, "private void clearState()").contains("soulPool.clear()")
                && block(data, "public void init()").contains("soulPool.reset("), "赋职重置与局末清理灵魂");
        String spawn = block(data, "public boolean spawnUndeadAt(");
        require(spawn.contains("!canRaiseFromCorpse()") && spawn.contains("!serverLevel.addFreshEntity(undead)")
                && spawn.contains("if (boostUndeadSpeed)"), "全部旧生成路径统一上限且保留邪恶战争速度");
        require(block(data, "public int maxActiveUndead()").contains("ignoreUndeadCap ? Integer.MAX_VALUE")
                && data.contains("Math.min(4,") && data.contains("undead.setHalloweenTarget(target, durationTicks)"),
                "普通上限四只、战争无限与指定目标追击接口");
        for (String method : List.of("getActiveUndeadCount", "maxActiveUndead", "canRaiseFromCorpse", "spawnUndeadAt",
                "onUndeadRemoved", "addInfection", "trySummonCharm", "summonTemporaryUndead", "startInfectionAmp",
                "releasePlagueFog", "canDieFromInfection")) {
            require(Pattern.compile("public (?:static )?\\w+ " + method + "\\(").matcher(data).find(),
                    "保留 public 旧 API " + method);
        }
        for (String item : List.of("BONE_STAFF", "plague_fog", "summon_charm", "infection_amp")) {
            require(role.contains(item), "保留旧商店物品 " + item);
        }
        for (String key : List.of("souls", "maxSouls", "raiseReadyAt")) {
            require(data.contains("\"" + key + "\""), "RoleData 同步字段 " + key);
        }
        require(hud.contains("comp.getSouls(), comp.maxSouls()") && hud.contains("syncedUndeadCount")
                && hud.contains("COMMAND_SKILL_ID") && hud.contains("SUMMON_SKILL_ID")
                && hud.contains("getRaiseCooldownTicks()") && hud.contains("Component.keybind"),
                "沿用文字 HUD 展示资源、上限、两个技能及各自冷却，并跟随改键");
        runSoulPool(data, output);
        System.out.println("Undead Lord: " + checks + " static checks + actual SoulPool logic passed (no Gradle/Minecraft).");
    }

    private static void parseSyntax(List<Path> files) throws Exception {
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var compiler = ToolProvider.getSystemJavaCompiler();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            var task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                    List.of("-proc:none", "-encoding", "UTF-8"), null, manager.getJavaFileObjectsFromPaths(files));
            task.parse(); // 不 analyze/生成类，不解析 Minecraft 依赖，不触发项目编译。
        }
        for (var diagnostic : diagnostics.getDiagnostics()) {
            require(diagnostic.getKind() != Diagnostic.Kind.ERROR, "Java 语法 " + diagnostic);
        }
        checks++;
    }

    private static void runSoulPool(String data, Path output) throws Exception {
        String pool = block(data, "public static final class SoulPool");
        String source = "import java.util.*; public final class SoulPoolCheck {\n" + pool + "\n" + """
                private static void check(boolean ok, String name) {
                    if (!ok) throw new AssertionError(name);
                }
                public static void verify() {
                    SoulPool pool = new SoulPool();
                    UUID victim = new UUID(0, 1);
                    pool.reset(2, 6);
                    check(pool.souls() == 2 && pool.maximum() == 6, "开局资源");
                    check(pool.spend() && pool.spend() && !pool.spend() && pool.souls() == 0, "无尸体两次起步与零下限");
                    check(pool.creditKill(victim, 1) && pool.souls() == 1, "成功击杀一魂");
                    check(!pool.creditKill(victim, 1) && pool.souls() == 1, "重复死亡回调不刷魂");
                    check(pool.creditKill(victim, 2) && pool.souls() == 2, "复活后再次成功击杀可产魂");
                    for (long i = 3; i <= 20; i++) pool.creditKill(victim, i);
                    check(pool.souls() == 6, "六魂上限");
                    check(pool.spend() && !pool.creditKill(victim, 20) && pool.souls() == 5,
                            "满魂死亡也去重，不能花魂后补刷旧奖励");
                    check(pool.creditKill(new UUID(0, 2), 20) && pool.souls() == 6, "不同受害者同 tick 独立奖励");
                    pool.clear();
                    check(pool.souls() == 0, "局末/换职清零");
                    pool.reset(2, 6);
                    check(pool.souls() == 2 && pool.creditKill(victim, 20), "新局不残留奖励记录");
                    pool.reset(99, 6);
                    check(pool.souls() == 6, "初始魂数钳制");
                    pool.reset(-1, -2);
                    check(pool.souls() == 0 && pool.maximum() == 0 && !pool.spend()
                            && !pool.creditKill(victim, 1), "负数配置安全边界");
                    pool.reset(Integer.MAX_VALUE - 1, Integer.MAX_VALUE);
                    check(pool.creditKill(victim, 1) && !pool.creditKill(victim, 2)
                            && pool.souls() == Integer.MAX_VALUE, "奖励不溢出");
                }
                }
                """;
        Path generated = output.resolve("SoulPoolCheck.java");
        Files.writeString(generated, source, StandardCharsets.UTF_8);
        int result = ToolProvider.getSystemJavaCompiler().run(null, null, null, "-encoding", "UTF-8",
                "-d", output.toString(), generated.toString());
        require(result == 0, "提取的真实 SoulPool 可独立编译");
        try (var loader = new URLClassLoader(new java.net.URL[]{output.toUri().toURL()})) {
            loader.loadClass("SoulPoolCheck").getMethod("verify").invoke(null);
        }
    }

    private static String block(String source, String marker) {
        int start = source.indexOf(marker);
        if (start < 0) throw new AssertionError("缺少源码段: " + marker);
        int opening = source.indexOf('{', start);
        int depth = 1;
        for (int i = opening + 1; i < source.length(); i++) {
            if (source.charAt(i) == '{') depth++;
            if (source.charAt(i) == '}') depth--;
            if (depth == 0) return source.substring(start, i + 1);
        }
        throw new AssertionError("未闭合源码段: " + marker);
    }

    private static int count(String source, String token) { return source.split(Pattern.quote(token), -1).length - 1; }
    private static boolean before(String source, String first, String second) {
        return source.indexOf(first) >= 0 && source.indexOf(first) < source.indexOf(second);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
