package org.dreeam.leaf.config;

public enum EnumConfigCategory {

    ASYNC("async"),
    PERFORMANCE("performance"),
    NETWORK("network"),
    FIXES("fixes"),
    MISC("misc"),
    GAMEPLAY("gameplay"),
    WIP("wip");

    private final String baseKeyName;

    EnumConfigCategory(String baseKeyName) {
        this.baseKeyName = baseKeyName;
    }

    public String getBaseKeyName() {
        return this.baseKeyName;
    }
}
