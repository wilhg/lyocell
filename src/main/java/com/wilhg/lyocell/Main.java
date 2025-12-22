package com.wilhg.lyocell;

import com.wilhg.lyocell.engine.TestConfig;
import com.wilhg.lyocell.engine.TestEngine;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        int exitCode = run(args);
        System.exit(exitCode);
    }

    public static int run(String[] args) {
        if (args.length < 1) {
            printUsage();
            return 1;
        }

        String scriptArg = null;
        int vus = 1;
        int iterations = 1;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("-u") || arg.equals("--vus")) {
                if (i + 1 < args.length) {
                    vus = Integer.parseInt(args[++i]);
                } else {
                    System.err.println("Missing value for --vus");
                    return 1;
                }
            } else if (arg.equals("-i") || arg.equals("--iterations")) {
                if (i + 1 < args.length) {
                    iterations = Integer.parseInt(args[++i]);
                } else {
                    System.err.println("Missing value for --iterations");
                    return 1;
                }
            } else if (!arg.startsWith("-")) {
                scriptArg = arg;
            }
        }

        if (scriptArg == null) {
            printUsage();
            return 1;
        }

        Path scriptPath = Paths.get(scriptArg);
        if (!scriptPath.toFile().exists()) {
            System.err.println("Script not found: " + scriptArg);
            return 1;
        }

        System.out.println("Starting Lyocell (k6 clone)...");
        
        try {
            TestEngine engine = new TestEngine();
            TestConfig config = new TestConfig(vus, iterations, null);
            engine.run(scriptPath, config);
            return 0;
        } catch (Exception e) {
            System.err.println("Execution failed: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    private static void printUsage() {
        System.err.println("Usage: lyocell <script.js> [options]");
        System.err.println("Options:");
        System.err.println("  -u, --vus <n>          Number of virtual users (default: 1)");
        System.err.println("  -i, --iterations <n>   Total iterations (per VU for now) (default: 1)");
    }
}
