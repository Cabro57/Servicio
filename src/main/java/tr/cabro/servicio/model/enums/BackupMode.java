package tr.cabro.servicio.model.enums;

import lombok.Getter;

@Getter
public enum BackupMode {
    NONE("Kapalı — yalnızca elle"),
    ON_START("Uygulama açılınca"),
    ON_EXIT("Uygulama kapanınca"),
    ON_START_AND_EXIT("Açılınca ve kapanınca"),
    EVERY_N_MINUTES("Belirli dakikada bir"),
    EVERY_N_HOURS("Belirli saatte bir"),
    EVERY_N_DAYS("Belirli günde bir"),
    EVERY_N_WEEKS("Belirli haftada bir"),
    EVERY_N_MONTHS("Belirli ayda bir");

    private final String displayName;

    BackupMode(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}