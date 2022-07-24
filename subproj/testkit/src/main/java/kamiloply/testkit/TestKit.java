package kamiloply.testkit;

import java.io.File;

public class TestKit {
    private static File resolvedProjectRoot;

    public static File resolveProjectRoot() {
        if (resolvedProjectRoot == null) return resolvedProjectRoot = resolveProjectRoot0();
        return resolvedProjectRoot;
    }

    public static File resolveProjectRoot(String subp) {
        return new File(resolveProjectRoot(), subp);
    }

    private static File resolveProjectRoot0() {
        File start = new File("aeafwe").getAbsoluteFile();

        File resolved = start;
        while (resolved != null) {
            crt:
            {
                if (!new File(resolved, "gradle/wrapper/gradle-wrapper.jar").exists()) break crt;
                if (!new File(resolved, "gradle/wrapper/gradle-wrapper.properties").exists()) break crt;
                if (!new File(resolved, "subproj/api").exists()) break crt;
                if (!new File(resolved, "subproj/core").exists()) break crt;
                if (!new File(resolved, "subproj/testkit").exists()) break crt;

                return resolved;
            }

            resolved = resolved.getParentFile();
        }
        throw new IllegalStateException("Failed to resolve project root.");

    }
}
