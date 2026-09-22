package tr.cabro.servicio.application.panels.customer;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Genel Bakış'taki "Son hareketler" listesinin bir satırı: servis, satış, tahsilat ve 2.el
 * işlemleri tek bir tarih sırasında gösterilebilsin diye ortak biçime çevrilir.
 * {@link #source} satır açıldığında hangi kayda gidileceğini taşır.
 */
@Getter
public class CustomerActivity {

    public enum Kind {
        SERVICE("Servis", "icons/wrench.svg"),
        SALE("Satış", "icons/shopping-bag.svg"),
        RETURN("İade", "icons/undo-2.svg"),
        PAYMENT("Tahsilat", "icons/hand-coins.svg"),
        DEVICE_PURCHASE("2.El Alım", "icons/banknote-arrow-down.svg"),
        DEVICE_SALE("2.El Satış", "icons/banknote-arrow-up.svg");

        @Getter private final String label;
        @Getter private final String iconPath;

        Kind(String label, String iconPath) {
            this.label = label;
            this.iconPath = iconPath;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final Kind kind;
    private final LocalDateTime date;
    private final String description;
    private final BigDecimal amount;
    private final Object source;

    public CustomerActivity(Kind kind, LocalDateTime date, String description, BigDecimal amount, Object source) {
        this.kind = kind;
        this.date = date;
        this.description = description;
        this.amount = amount;
        this.source = source;
    }
}
