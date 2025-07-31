package tr.cabro.servicio.model;

public enum BackupMode {
    ON_START,
    ON_EXIT,
    ON_START_AND_EXIT,
    EVERY_N_MINUTES,
    EVERY_N_HOURS,
    EVERY_N_DAYS,
    EVERY_N_WEEKS,
    EVERY_N_MONTHS
}
