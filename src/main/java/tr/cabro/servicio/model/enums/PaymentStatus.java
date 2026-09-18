package tr.cabro.servicio.model.enums;

import lombok.Getter;
import tr.cabro.servicio.model.contract.Visualizable;

/**
 * Bir belgenin (iş emri/satış) tahsis edilmiş ödeme durumu. DB'de saklanmaz;
 * {@code v_document_balances}'daki remaining_amount / total_amount oranından türetilir
 * (bkz. {@code PaymentService}).
 */
@Getter
public enum PaymentStatus implements Visualizable {
    UNPAID("Ödenmedi", "icons/circle-x.svg", BadgeColor.RED),
    PARTIAL("Kısmi Ödendi", "icons/circle-dashed.svg", BadgeColor.YELLOW),
    PAID("Ödendi", "icons/circle-check.svg", BadgeColor.DARK_GREEN);

    private final String displayName;
    private final String iconPath;
    private final BadgeColor badgeColor;

    PaymentStatus(String displayName, String iconPath, BadgeColor badgeColor) {
        this.displayName = displayName;
        this.iconPath = iconPath;
        this.badgeColor = badgeColor;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
