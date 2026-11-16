package io.wifi.srepackcompat.startup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ObsoleteModRemovalTest {
    @TempDir Path temp;

    private Path file(String name) throws Exception {
        Path path = temp.resolve(name);
        Files.createDirectories(path.getParent());
        return Files.createFile(path);
    }

    @Test void usesActualJavaWhenJavaHomeIsWrong() throws Exception {
        Path actual = file("实际 Java runtime/bin/java.exe");
        file("wrong-home/bin/javaw.exe");
        assertEquals(actual, ObsoleteModRemoval.resolveJavaExecutable(
                temp.resolve("wrong-home"), actual.toString()));
    }

    @Test void acceptsActualJavawAndUnixJava() throws Exception {
        for (String name : new String[]{"javaw.exe", "java"}) {
            Path actual = file("runtime/bin/" + name);
            assertEquals(actual, ObsoleteModRemoval.resolveJavaExecutable(
                    temp.resolve("missing-home"), actual.toString()));
        }
    }

    @Test void fallsBackToJavaExeWithoutJavaw() throws Exception {
        Path executable = file("runtime/bin/java.exe");
        assertEquals(executable, ObsoleteModRemoval.resolveJavaExecutable(
                temp.resolve("runtime"), null));
    }

    @Test void prefersJavawInJavaHomeAndFallsBackToUnixJava() throws Exception {
        Path javaw = file("runtime/bin/javaw.exe");
        Path java = file("runtime/bin/java");
        assertEquals(javaw, ObsoleteModRemoval.resolveJavaExecutable(temp.resolve("runtime"), ""));
        Files.delete(javaw);
        assertEquals(java, ObsoleteModRemoval.resolveJavaExecutable(temp.resolve("runtime"), " "));
    }

    @Test void ignoresMissingProcessExecutableAndNonJavaLauncher() throws Exception {
        Path executable = file("runtime/bin/java.exe");
        Path launcher = file("launcher.exe");
        assertEquals(executable, ObsoleteModRemoval.resolveJavaExecutable(
                temp.resolve("runtime"), launcher.toString()));
        assertEquals(executable, ObsoleteModRemoval.resolveJavaExecutable(
                temp.resolve("runtime"), temp.resolve("missing/java.exe").toString()));
    }

    @Test void rejectsDirectoriesAndReportsMissingJavaClearly() throws Exception {
        Files.createDirectories(temp.resolve("runtime/bin/javaw.exe"));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> ObsoleteModRemoval.resolveJavaExecutable(temp.resolve("runtime"), null));
        assertTrue(error.getMessage().contains("找不到独立清理器所需的 Java"));
        assertTrue(error.getMessage().contains(temp.resolve("runtime").toString()));
    }

    @Test void resolvedCurrentRuntimeCanStartChildProcess() throws Exception {
        Path executable = ObsoleteModRemoval.resolveJavaExecutable(temp.resolve("wrong-home"),
                ProcessHandle.current().info().command().orElseThrow());
        Process process = new ProcessBuilder(executable.toString(), "-version")
                .redirectErrorStream(true).redirectOutput(temp.resolve("java-version.log").toFile()).start();
        try {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue());
            assertTrue(Files.readString(temp.resolve("java-version.log")).contains("version"));
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
