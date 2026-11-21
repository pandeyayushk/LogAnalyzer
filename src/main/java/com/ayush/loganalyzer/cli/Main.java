package com.ayush.loganalyzer.cli;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Interactive command-line entry point. */
public final class Main {
    private Main() {
    }

    public static void main(String[] arguments) {
        LogAnalyzerCli cli = new LogAnalyzerCli();
        PrintWriter output = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
        if (arguments.length > 0) {
            cli.executeArguments(Arrays.asList(arguments), output);
            return;
        }

        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        output.println("Log Analyzer. Type help for commands.");
        try {
            String line;
            while (true) {
                output.print("loganalyzer> ");
                output.flush();
                line = input.readLine();
                if (line == null || !cli.execute(line, output)) {
                    break;
                }
            }
        } catch (IOException exception) {
            output.println("I/O error: " + exception.getMessage());
        }
    }
}
