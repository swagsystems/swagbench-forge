package com.virgoagario.swagbench.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DesignTodosTest {
    @Test
    void keepsAllSection12OpenQuestionsVisible() {
        assertEquals(6, DesignTodos.SECTION_12.size());
        assertTrue(DesignTodos.SECTION_12.stream().anyMatch(todo -> todo.contains("warmup")));
        assertTrue(DesignTodos.SECTION_12.stream().anyMatch(todo -> todo.contains("loop ownership")));
        assertTrue(DesignTodos.SECTION_12.stream().anyMatch(todo -> todo.contains("mixed-v1")));
        assertTrue(DesignTodos.SECTION_12.stream().anyMatch(todo -> todo.contains("significance")));
        assertTrue(DesignTodos.SECTION_12.stream().anyMatch(todo -> todo.contains("allocation")));
        assertTrue(DesignTodos.SECTION_12.stream().anyMatch(todo -> todo.contains("forced-GC")));
    }

    @Test
    void readmeDocumentsRngPinningLimitsForFutureDrivers() throws IOException {
        String readme = Files.readString(Path.of("README.md"));

        assertTrue(readme.contains("## RNG Pinning Limits"));
        assertTrue(readme.contains("Pinned:"));
        assertTrue(readme.contains("Not fully pinned:"));
        assertTrue(readme.contains("fullyPinned=false"));
    }
}
