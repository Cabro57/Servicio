package tr.cabro.servicio.documents.receipt;

import com.lowagie.text.DocumentException;
import tr.cabro.servicio.documents.DocumentText;
import tr.cabro.servicio.documents.DocumentTexts;

import java.io.File;
import java.io.IOException;

/**
 * {@link ReceiptContent}'i (satış veya iade) termal fiş olarak basar. Kağıt genişliği
 * Ayarlar &gt; Yazdırma'dan gelir; düzen kuralları için bkz. {@link ThermalSlip}.
 * <p>
 * {@link ReceiptContent} yazıcıdan bağımsız kaldı: ileride doğrudan ESC/POS basımı eklenirse
 * aynı model ayrı bir renderer'a verilir.
 */
public class ReceiptPdfRenderer {

    public File render(ReceiptContent content, File outFile) throws IOException, DocumentException {
        ThermalSlip slip = ThermalSlip.create()
                .shopHeader(content.getShopName(), content.getShopPhone(), content.getShopAddress())
                .titleBand(content.getDocumentTitle())
                .documentLine(content.getDocumentNumber(), content.getDate())
                .field("Müşteri", notBlank(content.getCustomerName()) ? content.getCustomerName() : "Perakende")
                .dashed();

        for (ReceiptContent.Line line : content.getLines()) {
            slip.item(line.name(), line.quantity(), line.unitPrice(), line.lineTotal());
        }
        slip.dashed();

        boolean hasDiscount = content.getDiscount() != null && content.getDiscount().signum() > 0;
        if (hasDiscount) {
            slip.amount("Ara Toplam", content.getSubtotal());
            slip.amount("İndirim", content.getDiscount().negate());
        }
        slip.grandTotal("Toplam", content.getTotal());

        if (!content.getPayments().isEmpty()) {
            slip.heading("Ödeme");
            for (ReceiptContent.PaymentLine payment : content.getPayments()) {
                slip.amount(payment.methodLabel(), payment.amount());
            }
        }
        if (content.getChangeGiven() != null && content.getChangeGiven().signum() > 0) {
            slip.strongAmount("Para Üstü", content.getChangeGiven());
        }

        slip.dashed().note(DocumentTexts.get(DocumentText.SLIP_FOOTER));
        return slip.write(outFile);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
