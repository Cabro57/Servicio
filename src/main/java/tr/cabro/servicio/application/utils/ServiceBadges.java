package tr.cabro.servicio.application.utils;

import tr.cabro.servicio.application.renderer.RowParts;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.model.enums.BadgeColor;
import tr.cabro.servicio.model.enums.ServiceStatus;
import tr.cabro.servicio.util.Format;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;

/** Servis kayıtlarının liste ve ana sayfa satırlarında ortak kullanılan çip ve süre metinleri. */
public final class ServiceBadges {

    private static final Set<ServiceStatus> CLOSED = EnumSet.of(ServiceStatus.DELIVERED, ServiceStatus.RETURN);

    private ServiceBadges() {}

    public static boolean isClosed(WorkOrder s) {
        return CLOSED.contains(s.getServiceStatus());
    }

    /**
     * Ödeme çipi: ücret girilmemiş açık iş "Ücret yok", kapanmış ücretsiz iş "Ücretsiz", tamamı ödenmiş
     * "Ödendi", bir kısmı "Kısmi", hiç ödenmemiş "Ödenmedi" (teslim edilip ödenmediyse kırmızı: borç).
     */
    public static RowParts.Badge payment(WorkOrder s) {
        BigDecimal total = s.getTotalServiceAmount();
        BigDecimal paid = s.getTotalPaid();
        boolean closed = isClosed(s);
        if (total.signum() == 0 && paid.signum() == 0) {
            return new RowParts.Badge(closed ? "Ücretsiz" : "Ücret yok", BadgeColor.GRAY);
        }
        if (paid.compareTo(total) > 0) return new RowParts.Badge("Fazla ödeme", BadgeColor.BLUE);
        if (paid.compareTo(total) == 0) return new RowParts.Badge("Ödendi", BadgeColor.DARK_GREEN);
        if (paid.signum() > 0) return new RowParts.Badge("Kısmi Ödeme", BadgeColor.YELLOW);
        return new RowParts.Badge("Ödenmedi", closed ? BadgeColor.RED : BadgeColor.GRAY);
    }

    /** Tutarın altına yazılan fark: "₺1.250 kalan" ya da "₺100 fazla"; fark yoksa null. */
    public static String paymentDiff(WorkOrder s) {
        BigDecimal total = s.getTotalServiceAmount(), paid = s.getTotalPaid();
        if (paid.signum() > 0 && paid.compareTo(total) < 0) return Format.formatPrice(total.subtract(paid)) + " kalan";
        if (paid.compareTo(total) > 0 && total.signum() > 0) return Format.formatPrice(paid.subtract(total)) + " fazla";
        return null;
    }

    /** Kayıttan bu yana geçen takvim günü. */
    public static long daysSinceCreated(WorkOrder s) {
        if (s.getCreatedAt() == null) return 0;
        return ChronoUnit.DAYS.between(s.getCreatedAt().toLocalDate(), LocalDate.now());
    }

    /**
     * Durumun ne zamandan beri sürdüğü ("hazır 3 gündür"). Durum değişim anı yoksa (eski kayıtlar)
     * geliş tarihinden "serviste" sayılır. updated_at kullanılmaz: her düzenlemede değişir.
     */
    public static String sinceText(WorkOrder wo, String verb) {
        LocalDateTime changed = wo.getStatusChangedAt();
        LocalDateTime time = changed != null ? changed : wo.getCreatedAt();
        if (time == null) return "";
        String v = changed == null ? "serviste" : verb;
        long days = ChronoUnit.DAYS.between(time.toLocalDate(), LocalDate.now());
        if (days <= 0) return "bugün " + (changed != null ? v : "geldi");
        return days + " gündür " + v;
    }

    /** Durumun kaç gündür değişmediği (durum değişim anı yoksa geliş tarihinden). */
    public static long daysInStatus(WorkOrder wo) {
        LocalDateTime time = wo.getStatusChangedAt() != null ? wo.getStatusChangedAt() : wo.getCreatedAt();
        return time == null ? 0 : ChronoUnit.DAYS.between(time.toLocalDate(), LocalDate.now());
    }
}
