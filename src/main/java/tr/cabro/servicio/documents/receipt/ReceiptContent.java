package tr.cabro.servicio.documents.receipt;

import lombok.Getter;
import lombok.Setter;
import tr.cabro.servicio.model.Payment;
import tr.cabro.servicio.model.Sale;
import tr.cabro.servicio.model.SaleItem;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.model.enums.SaleType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Fiş içeriğinin yazıcıdan bağımsız modeli — bugün {@link ReceiptPdfRenderer} (80mm PDF)
 * tüketiyor, yazıcı bağlandığında aynı model bir ESC/POS renderer'a da verilecek.
 */
@Getter @Setter
public class ReceiptContent {

    private String documentTitle;   // "SATIŞ FİŞİ" | "İADE FİŞİ"
    private String documentNumber;  // "SAT-105" | "İADE-106 (SAT-105)"
    private LocalDateTime date;
    private String customerName;    // null/boş = "Perakende"

    private String shopName;
    private String shopPhone;
    private String shopAddress;

    private final List<Line> lines = new ArrayList<>();
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal discount = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;

    private final List<PaymentLine> payments = new ArrayList<>();
    private BigDecimal changeGiven;  // para üstü; null/0 ise basılmaz

    public record Line(String name, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {}
    public record PaymentLine(String methodLabel, BigDecimal amount) {}

    /** Bir {@link Sale} + o satışa uygulanan ödemelerden fiş içeriği üretir (satış VEYA iade fişi). */
    public static ReceiptContent fromSale(Sale sale, List<Payment> payments, User shop) {
        ReceiptContent content = new ReceiptContent();
        boolean isReturn = sale.getType() == SaleType.RETURN;

        content.setDocumentTitle(isReturn ? "İADE FİŞİ" : "SATIŞ FİŞİ");
        content.setDocumentNumber(isReturn ? "İADE-" + sale.getId() + " (SAT-" + sale.getParentSaleId() + ")" : "SAT-" + sale.getId());
        content.setDate(sale.getSaleDate() != null ? sale.getSaleDate() : LocalDateTime.now());
        content.setCustomerName(sale.getCustomer() != null ? sale.getCustomer().getFullName() : null);

        if (shop != null) {
            content.setShopName(shop.getBusinessName());
            content.setShopPhone(shop.getPhoneNumber());
            content.setShopAddress(shop.getAddress());
        }

        if (sale.getItems() != null) {
            for (SaleItem item : sale.getItems()) {
                content.getLines().add(new Line(item.getItemName(), item.getQuantity(), item.getUnitPrice(), item.getLineTotal()));
            }
        }

        content.setSubtotal(sale.getSubtotal());
        BigDecimal discount = sale.getSubtotal() != null && sale.getTotalAmount() != null
                ? sale.getSubtotal().subtract(sale.getTotalAmount()) : BigDecimal.ZERO;
        content.setDiscount(discount);
        content.setTotal(sale.getTotalAmount());

        if (payments != null) {
            for (Payment p : payments) {
                content.getPayments().add(new PaymentLine(
                        p.getPaymentType() != null ? p.getPaymentType().getDisplayName() : "-", p.getAmount()));
            }
        }

        return content;
    }
}
