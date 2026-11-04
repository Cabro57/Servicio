package tr.cabro.servicio.model.enums;

/**
 * Emanetin yönü ({@code device_loans.direction}).
 * <ul>
 *   <li>{@link #OUT}: dükkân müşteriye yedek cihaz verir; depozito alınabilir.</li>
 *   <li>{@link #IN}: müşteri cihazını dükkâna emanet bırakır; depozito yoktur.</li>
 * </ul>
 */
public enum LoanDirection {
    OUT("Emanet verildi", "Emanet ver", "Müşteriye yedek cihaz veriliyor"),
    IN("Emanet alındı", "Emanet al", "Müşteri cihazını dükkâna bırakıyor");

    private final String displayName;
    private final String actionName;
    private final String description;

    LoanDirection(String displayName, String actionName, String description) {
        this.displayName = displayName;
        this.actionName = actionName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Başlatan eylemin adı ("Emanet ver" / "Emanet al"). */
    public String getActionName() {
        return actionName;
    }

    /** Kapatan eylemin adı: verilen geri alınır, alınan teslim edilir. */
    public String getCloseActionName() {
        return this == OUT ? "Emanet iade al" : "Emaneti teslim et";
    }

    public String getDescription() {
        return description;
    }
}
