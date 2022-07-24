package kamiloply;

import kamiloply.testkit.TestKit;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.internal.PluginUnderTestMetadataReading;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class TestGradlePlugin {
    @TempDir
    private Path copyix;

    @Test
    public void doTest() throws Throwable {
        Files.createDirectories(copyix);

        var rspx = PluginUnderTestMetadataReading.readImplementationClasspath();
        rspx.forEach(System.out::println);
        var testProjectPath = TestKit.resolveProjectRoot("subproj/testproject").toPath();
        Files.walkFileTree(testProjectPath, new FileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.equals(testProjectPath)) {
                    return FileVisitResult.CONTINUE;
                }

                var subitx = testProjectPath.relativize(dir);
                var tostringx = subitx.toString().replace('\\', '/');
                if (tostringx.equals(".gradle") || tostringx.equals("build")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }

                var rpt = copyix.resolve(subitx);
                if (!Files.isDirectory(rpt))
                    Files.createDirectories(rpt);

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                var subitx = testProjectPath.relativize(file);


                if (subitx.toString().equals("build.gradle")) {
                    var rspx = Files.readString(file, StandardCharsets.UTF_8)
                            .replace("// gradle-test", "")
                            .replace("$kamiloply-root", "\"" + TestKit.resolveProjectRoot().getAbsolutePath().replace('\\', '/') + "\"");
                    Files.writeString(copyix.resolve(subitx), rspx, StandardCharsets.UTF_8);
                } else {
                    Files.copy(file, copyix.resolve(subitx), StandardCopyOption.REPLACE_EXISTING);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });

        GradleRunner.create()
                .withPluginClasspath(rspx)
                //.withEnvironment(envs)
                .withTestKitDir(TestKit.resolveProjectRoot("build/gradle-testkit"))
                .withProjectDir(copyix.toAbsolutePath().toFile())
                .withArguments("--info", "--stacktrace", "build")
                .forwardOutput()
                .build();
    }
}
