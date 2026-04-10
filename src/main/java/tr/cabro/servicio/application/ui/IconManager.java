package tr.cabro.servicio.application.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import tr.cabro.servicio.application.util.Ikon;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IconManager {
    private static final Map<String, Ikon> ICON_CACHE = new ConcurrentHashMap<>();

    public static Ikon getIcon(String path, int size) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }

        String key = path + "_" + size;

        // Java 8 uyumlu cache mekanizması
        return ICON_CACHE.computeIfAbsent(key, k -> new Ikon(path, size));
    }
}