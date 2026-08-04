package com.virgoagario.swagbench.core;

public enum SubsystemBucket {
    ENTITIES("entities"),
    BLOCK_ENTITIES("blockEntities"),
    CHUNK("chunk"),
    SCHEDULED("scheduled"),
    OTHER("other");

    private final String schemaName;

    SubsystemBucket(String schemaName) {
        this.schemaName = schemaName;
    }

    public String schemaName() {
        return schemaName;
    }
}
