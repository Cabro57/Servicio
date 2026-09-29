package tr.cabro.servicio.model.enums;

import lombok.Getter;
import org.jdbi.v3.core.enums.EnumByName;
import tr.cabro.servicio.model.contract.Visualizable;

import java.util.Arrays;

@Getter
@EnumByName
public enum ServiceStatus implements Visualizable {
    // Renkler durumun ANLAMINI taşır: yedi durumun yedisi de ayrı renk alır.
    // Eskiden READY, DELIVERED ve WAITING_FOR_PART yeşilin üç tonuydu — bloke bir cihaz
    // ile teslim edilmiş bir cihaz listede aynı görünüyordu ve renk kanalı bilgi taşımıyordu.
    /** Başlangıç durumu: cihaz teslim alındı, işe henüz başlanmadı. */
    ACCEPTED("Kabul Edildi", "icons/clipboard-list.svg", BadgeColor.TEAL),
    /** Üzerinde çalışılıyor — süregelen iş. */
    UNDER_REPAIR("Tamirde", "icons/wrench.svg", BadgeColor.BLUE),
    /** Bitti, müşteri aranacak — kullanıcıdan hamle bekleyen tek durum. */
    READY("Hazır", "icons/thumbs-up.svg", BadgeColor.PURPLE),
    /** Bizde değil — nötr. */
    ANOTHER_SERVICE("Başka Serviste", "icons/users.svg", BadgeColor.GRAY),
    /** Kapanmış iş. */
    DELIVERED("Teslim Edildi", "icons/package-check.svg", BadgeColor.GREEN),
    RETURN("İade", "icons/undo-2.svg", BadgeColor.RED),
    /** Bloke: elimizde ama ilerleyemiyoruz. */
    WAITING_FOR_PART("Parça Bekliyor", "icons/hourglass.svg", BadgeColor.YELLOW),;

    private final String displayName;
    private final String iconPath;
    private final BadgeColor badgeColor;

    ServiceStatus(String displayName, String iconPath, BadgeColor badgeColor) {
        this.displayName = displayName;
        this.iconPath = iconPath;
        this.badgeColor = badgeColor;
    }

    /** Teslim edilmiş ya da iade edilmiş kayıt kapalıdır: durum, kalemler ve notlar değiştirilemez. */
    public boolean isClosed() {
        return this == DELIVERED || this == RETURN;
    }

    public static ServiceStatus of(String name) {
        if (name == null) return UNDER_REPAIR;
        return Arrays.stream(values())
                .filter(status -> status.name().equalsIgnoreCase(name) || status.displayName.equalsIgnoreCase(name))
                .findFirst()
                .orElse(UNDER_REPAIR);
    }
}