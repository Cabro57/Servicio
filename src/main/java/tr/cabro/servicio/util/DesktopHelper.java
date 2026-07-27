package tr.cabro.servicio.util;

import tr.cabro.servicio.Servicio;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/** Dosya/URL açma işlemlerini işletim sisteminin varsayılan uygulamasına devreder. */
public final class DesktopHelper {

    private DesktopHelper() {
    }

    public static boolean openFile(File file) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file);
                return true;
            }
        } catch (IOException e) {
            Servicio.getLogger().error("Dosya açılamadı: " + file, e);
        }
        return false;
    }

    public static boolean browseUrl(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return true;
            }
        } catch (IOException | URISyntaxException e) {
            Servicio.getLogger().error("Bağlantı açılamadı: " + url, e);
        }
        return false;
    }
}
