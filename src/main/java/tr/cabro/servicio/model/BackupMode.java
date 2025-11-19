package tr.cabro.servicio.model;

public enum BackupMode {
    NONE("Kapalı"),
    ON_START("Açılışta"),
    ON_EXIT("Kapanışta"),
    ON_START_AND_EXIT("Açılış ve Kapanışta"),
    EVERY_N_MINUTES("Her n dakika da bir"),
    EVERY_N_HOURS("Her n saat de bir"),
    EVERY_N_DAYS("Her n günde bir"),
    EVERY_N_WEEKS("Her n hafta da bir"),
    EVERY_N_MONTHS("Her n ay da bir");

    private final String name;

    BackupMode(String name) {
        this.name = name;
    }


    @Override
    public String toString() {
        return name;
    }
}
