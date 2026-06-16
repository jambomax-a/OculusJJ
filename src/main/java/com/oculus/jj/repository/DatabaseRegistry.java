package com.oculus.jj.repository;

import java.util.*;

/**
 * Registered databases/data sources for the application.
 */
public class DatabaseRegistry {
    private final Map<String, String> registeredBases = new LinkedHashMap<>(); // Name -> BasePath

    public void registerBase(String name, String basePath) {
        registeredBases.put(name, basePath);
    }

    public List<String> getRegisteredBaseNames() {
        return new ArrayList<>(registeredBases.keySet());
    }

    public String getBasePath(String name) {
        return registeredBases.get(name);
    }
}
