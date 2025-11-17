package ui;

import algorithms.Algorithm;
import algorithms.TKUSP;

import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.Map;

public class AlgorithmFactory {

    /**
     * Return a map: display name -> Algorithm class.
     * Add new algorithms here (or implement classpath discovery / ServiceLoader).
     */
    public static Map<String, Class<? extends Algorithm>> getAvailableAlgorithms() {
        Map<String, Class<? extends Algorithm>> map = new LinkedHashMap<>();
        // Register algorithms here:
        map.put(new TKUSP().getName(), TKUSP.class);
        // map.put(new OtherAlgo().getName(), OtherAlgo.class);
        return map;
    }

    /** Instantiate an algorithm. Prefer constructor(long seed) if present, otherwise no-arg. */
    public static Algorithm createInstance(Class<? extends Algorithm> clazz, long seed) throws Exception {
        try {
            Constructor<? extends Algorithm> c = clazz.getConstructor(long.class);
            return c.newInstance(seed);
        } catch (NoSuchMethodException e) {
            return clazz.getConstructor().newInstance();
        }
    }
}