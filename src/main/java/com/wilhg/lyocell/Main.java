package com.wilhg.lyocell;

import com.wilhg.lyocell.engine.JsEngine;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: lyocell <script.js>");
            System.exit(1);
        }

        String scriptArg = args[0];
        Path scriptPath = Paths.get(scriptArg);

        if (!scriptPath.toFile().exists()) {
            System.err.println("Script not found: " + scriptArg);
            System.exit(1);
        }

        System.out.println("Starting Lyocell (k6 clone)...");
        
        try (JsEngine engine = new JsEngine()) {
            engine.runScript(scriptPath);
        } catch (Exception e) {
            System.err.println("Execution failed:");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
