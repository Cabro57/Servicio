package tr.cabro.servicio;

import lombok.Getter;

public class LauncherAccessContext {
    @Getter
    private static boolean allowed = false;

    public static void allow() {
        allowed = true;
    }

}

