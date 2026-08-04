package com.virgoagario.swagbench.control;

import com.virgoagario.swagbench.core.Report;
import java.nio.file.Path;

public record RunResult(int exitCode, Report report, Path reportPath, String message) {
    public static RunResult failure(int exitCode, String message) {
        return new RunResult(exitCode, null, null, message);
    }
}
