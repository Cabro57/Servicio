package tr.cabro.servicio.documents;

import tr.cabro.servicio.model.User;
import tr.cabro.servicio.model.WorkOrder;

import java.io.File;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * "Form" popup menüsünde listelenen, kod tarafında hazır tasarlanmış sabit form türleri.
 * {@link #isThermal()} olanlar termal rulo fiş (yalnızca PDF), diğerleri A4 belgedir
 * (PDF, Word, RTF); menü ikisini ayrı gruplar.
 * <p>
 * {@link #getEditableTexts()} belge oluşturma penceresinde düzenlenebilen metinleri,
 * {@link #hasWarrantyDays()} garanti günü alanını belirler.
 */
public enum ServiceFormType {

    DEVICE_INTAKE("Cihaz Kabul Formu", "cihaz-kabul", new DeviceIntakeFormGenerator(),
            "Teslim Eden Adı (Müşteri)", "Teslim Alan Adı (İşletme)", false, false,
            List.of(DocumentText.INTAKE_TERMS)),
    SERVICE_DELIVERY("Servis Teslim Formu", "servis-teslim", new ServiceDeliveryFormGenerator(),
            "Teslim Eden Adı (İşletme)", "Teslim Alan Adı (Müşteri)", false, true,
            List.of()),
    FAULT_DIAGNOSIS("Arıza Tespit Formu", "ariza-tespit", new FaultDiagnosisFormGenerator(),
            null, null, false, false,
            List.of()),
    REPAIR_QUOTE_APPROVAL("Teklif / Onarım Onayı Formu", "teklif-onay", new RepairQuoteApprovalFormGenerator(),
            "Teklifi Sunan Adı (İşletme)", "Onaylayan Adı (Müşteri)", false, false,
            List.of(DocumentText.QUOTE_APPROVAL_NOTE)),

    // Termal fişler: müşteri adı imza çizgisine kendiliğinden basılır, isim sorulmaz.
    DEVICE_INTAKE_SLIP("Cihaz Kabul Fişi", "cihaz-kabul-fisi", new DeviceIntakeSlipGenerator(),
            null, null, true, false,
            List.of(DocumentText.INTAKE_TERMS, DocumentText.INTAKE_SLIP_NOTE)),
    SERVICE_DELIVERY_SLIP("Servis Teslim Fişi", "servis-teslim-fisi", new ServiceDeliverySlipGenerator(),
            null, null, true, true,
            List.of(DocumentText.SLIP_FOOTER));

    private final String displayName;
    private final String fileSlug;
    private final ServiceFormGenerator generator;
    private final String leftSignerLabel;
    private final String rightSignerLabel;
    private final boolean thermal;
    private final boolean warrantyDays;
    private final List<DocumentText> editableTexts;

    ServiceFormType(String displayName, String fileSlug, ServiceFormGenerator generator,
                    String leftSignerLabel, String rightSignerLabel, boolean thermal, boolean warrantyDays,
                    List<DocumentText> editableTexts) {
        this.displayName = displayName;
        this.fileSlug = fileSlug;
        this.generator = generator;
        this.leftSignerLabel = leftSignerLabel;
        this.rightSignerLabel = rightSignerLabel;
        this.thermal = thermal;
        this.warrantyDays = warrantyDays;
        this.editableTexts = editableTexts;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Kaydedilen dosyanın önerilen adı için kısa ad ("servis-teslim"). */
    public String getFileSlug() {
        return fileSlug;
    }

    public boolean requiresSigners() {
        return leftSignerLabel != null;
    }

    public String getLeftSignerLabel() {
        return leftSignerLabel;
    }

    public String getRightSignerLabel() {
        return rightSignerLabel;
    }

    /** Termal rulo fiş mi (kağıt genişliği Ayarlar &gt; Yazdırma'dan)? */
    public boolean isThermal() {
        return thermal;
    }

    /** Belgede garanti günü alanı var mı (teslim belgeleri)? */
    public boolean hasWarrantyDays() {
        return warrantyDays;
    }

    public List<DocumentText> getEditableTexts() {
        return editableTexts;
    }

    public Set<DocumentFormat> getSupportedFormats() {
        return thermal ? EnumSet.of(DocumentFormat.PDF) : EnumSet.allOf(DocumentFormat.class);
    }

    public File generate(WorkOrder workOrder, User shop, DocumentRequest request,
                         DocumentFormat format, File outFile) throws Exception {
        return generator.generate(workOrder, shop, request, format, outFile);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
