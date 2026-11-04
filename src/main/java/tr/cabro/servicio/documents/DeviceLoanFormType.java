package tr.cabro.servicio.documents;

import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.model.Business;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.DeviceLoan;
import tr.cabro.servicio.model.enums.LoanDirection;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Emanet cihaz belgeleri (A4, iki imzalı). Her yönün bir başlangıç ve bir kapanış belgesi var;
 * kapanış belgesi yalnızca kapatılmış emanette listelenir. Düzen kodda sabittir, yalnızca
 * {@link DocumentText} metinleri düzenlenir.
 */
public enum DeviceLoanFormType {

    OUT_DELIVERY("Emanet Teslim Formu", "emanet-teslim", LoanDirection.OUT, false,
            "İşletme", "Teslim Alan (Müşteri)", DocumentText.LOAN_OUT_TERMS),
    OUT_RETURN("Emanet İade Formu", "emanet-iade", LoanDirection.OUT, true,
            "İşletme", "İade Eden (Müşteri)", DocumentText.LOAN_OUT_RETURN_NOTE),
    IN_RECEIPT("Emanet Alındı Belgesi", "emanet-alindi", LoanDirection.IN, false,
            "Teslim Alan (İşletme)", "Teslim Eden (Müşteri)", DocumentText.LOAN_IN_TERMS),
    IN_HANDOVER("Emanet Teslim Belgesi", "emanet-teslim-edildi", LoanDirection.IN, true,
            "Teslim Eden (İşletme)", "Teslim Alan (Müşteri)", DocumentText.LOAN_IN_HANDOVER_NOTE);

    private final String displayName;
    private final String fileSlug;
    private final LoanDirection direction;
    private final boolean closing;
    private final String leftSignerLabel;
    private final String rightSignerLabel;
    private final DocumentText text;

    DeviceLoanFormType(String displayName, String fileSlug, LoanDirection direction, boolean closing,
                       String leftSignerLabel, String rightSignerLabel, DocumentText text) {
        this.displayName = displayName;
        this.fileSlug = fileSlug;
        this.direction = direction;
        this.closing = closing;
        this.leftSignerLabel = leftSignerLabel;
        this.rightSignerLabel = rightSignerLabel;
        this.text = text;
    }

    public String getDisplayName() { return displayName; }
    public String getFileSlug() { return fileSlug; }
    public String getLeftSignerLabel() { return leftSignerLabel; }
    public String getRightSignerLabel() { return rightSignerLabel; }
    public List<DocumentText> getEditableTexts() { return List.of(text); }
    public Set<DocumentFormat> getSupportedFormats() { return EnumSet.allOf(DocumentFormat.class); }

    /** Yön tutmalı; kapanış belgesi yalnızca kapatılmış emanette. */
    public boolean isApplicableTo(DeviceLoan loan) {
        return loan != null && loan.getDirection() == direction && (!closing || !loan.isOpen());
    }

    /** Emanet başlatılınca/kapatılınca önerilecek belge. */
    public static DeviceLoanFormType defaultFor(DeviceLoan loan) {
        if (loan.getDirection() == LoanDirection.OUT) return loan.isOpen() ? OUT_DELIVERY : OUT_RETURN;
        return loan.isOpen() ? IN_RECEIPT : IN_HANDOVER;
    }

    public File generate(DeviceLoan loan, Business shop, DocumentRequest request, DocumentFormat format, File outFile) throws Exception {
        Customer customer = loan.getCustomer();
        Device device = loan.getDevice();
        LocalDateTime docDate = closing ? loan.getReturnedAt() : loan.getGivenAt();

        DocumentWriter doc = DocumentWriter.open(format, outFile, shop, displayName, loan.getCode(), date(docDate));
        try {
            doc.section(direction == LoanDirection.OUT ? "Müşteri ve Emanet Cihaz" : "Müşteri ve Cihaz");
            doc.fields(new String[][]{
                    {"Müşteri", customer != null ? customer.getFullName() : null},
                    {"Telefon", DeviceExpertiseFormGenerator.phone(customer)},
                    {"Cihaz", device != null ? device.getDisplayName() : null},
                    {"Seri No / IMEI", device != null ? device.getSerialNo() : null}
            });

            doc.section("Emanet Bilgisi");
            List<String[]> rows = new ArrayList<>();
            rows.add(new String[]{direction == LoanDirection.OUT ? "Veriliş Tarihi" : "Bırakılış Tarihi", dateTime(loan.getGivenAt())});
            rows.add(new String[]{direction == LoanDirection.OUT ? "Son İade Tarihi" : "Planlanan Teslim", date(loan.getDueAt())});
            if (direction == LoanDirection.OUT) {
                rows.add(new String[]{"Depozito", loan.hasDeposit()
                        ? PdfDocumentBuilder.money(loan.getDeposit()) + (loan.getDepositPaymentType() != null ? " (" + loan.getDepositPaymentType().getDisplayName() + ")" : "")
                        : "Alınmadı"});
            }
            if (closing) rows.add(new String[]{direction == LoanDirection.OUT ? "İade Tarihi" : "Teslim Tarihi", dateTime(loan.getReturnedAt())});
            doc.fields(rows.toArray(new String[0][]));
            doc.wideField("Teslim Anındaki Durum", blank(loan.getConditionOut()));
            doc.wideField("Aksesuarlar", blank(loan.getAccessories()));
            if (loan.getNote() != null && !loan.getNote().isBlank()) doc.wideField("Not", loan.getNote());

            if (closing) {
                doc.section(direction == LoanDirection.OUT ? "İade" : "Teslim");
                doc.wideField(direction == LoanDirection.OUT ? "İadedeki Durum / Hasar" : "Teslimdeki Durum", blank(loan.getConditionIn()));
                if (direction == LoanDirection.OUT && loan.hasDeposit()) {
                    BigDecimal damage = loan.getDamageCharge() != null ? loan.getDamageCharge() : BigDecimal.ZERO;
                    doc.fields(new String[][]{
                            {"Alınan Depozito", PdfDocumentBuilder.money(loan.getDeposit())},
                            {"Hasar Kesintisi", PdfDocumentBuilder.money(damage)},
                            {"İade Edilen", PdfDocumentBuilder.money(loan.getDepositRefund())}
                    });
                }
            }

            doc.section(closing ? "Beyan" : "Koşullar");
            doc.terms(request.text(text));
            doc.signatures(leftSignerLabel, request.getLeftSignerName(), rightSignerLabel, request.getRightSignerName());
            doc.close();
        } catch (Exception e) {
            doc.abort();
            throw e;
        }
        return outFile;
    }

    private static String date(LocalDateTime value) {
        return value != null ? value.format(DateFormats.shortDate()) : "—";
    }

    private static String dateTime(LocalDateTime value) {
        return value != null ? value.format(DateFormats.dateTime()) : "—";
    }

    private static String blank(String s) {
        return s != null && !s.isBlank() ? s : "Belirtilmemiş";
    }

    @Override
    public String toString() {
        return displayName;
    }
}
