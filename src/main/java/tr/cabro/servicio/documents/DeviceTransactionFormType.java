package tr.cabro.servicio.documents;

import tr.cabro.servicio.model.DeviceTransaction;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.model.enums.DeviceTransactionType;

import java.io.File;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** "Form" popup menüsünde listelenen, ikinci el alım-satım belgelerinin sabit türleri (A4). */
public enum DeviceTransactionFormType {

    DEVICE_EXPERTISE("Cihaz Ekspertizi", "cihaz-ekspertiz", new DeviceExpertiseFormGenerator(),
            null, null, DeviceTransactionType.PURCHASE, List.of()),
    PURCHASE_CONTRACT("Satın Alma Sözleşmesi", "satin-alma-sozlesmesi", new PurchaseContractFormGenerator(),
            "İşletme", "Satıcı (Müşteri)", DeviceTransactionType.PURCHASE, List.of(DocumentText.PURCHASE_CONTRACT_NOTE)),
    SALE_CONTRACT("Satış Sözleşmesi", "satis-sozlesmesi", new SaleContractFormGenerator(),
            "İşletme", "Alıcı (Müşteri)", DeviceTransactionType.SALE, List.of(DocumentText.SALE_CONTRACT_NOTE)),
    WARRANTY_CERTIFICATE("Garanti Belgesi", "garanti-belgesi", new WarrantyCertificateFormGenerator(),
            null, null, DeviceTransactionType.SALE, List.of(DocumentText.WARRANTY_CERTIFICATE_NOTE));

    private final String displayName;
    private final String fileSlug;
    private final DeviceTransactionFormGenerator generator;
    private final String leftSignerLabel;
    private final String rightSignerLabel;
    private final DeviceTransactionType requiredTransactionType;
    private final List<DocumentText> editableTexts;

    DeviceTransactionFormType(String displayName, String fileSlug, DeviceTransactionFormGenerator generator,
                              String leftSignerLabel, String rightSignerLabel,
                              DeviceTransactionType requiredTransactionType, List<DocumentText> editableTexts) {
        this.displayName = displayName;
        this.fileSlug = fileSlug;
        this.generator = generator;
        this.leftSignerLabel = leftSignerLabel;
        this.rightSignerLabel = rightSignerLabel;
        this.requiredTransactionType = requiredTransactionType;
        this.editableTexts = editableTexts;
    }

    public String getDisplayName() {
        return displayName;
    }

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

    public List<DocumentText> getEditableTexts() {
        return editableTexts;
    }

    public Set<DocumentFormat> getSupportedFormats() {
        return EnumSet.allOf(DocumentFormat.class);
    }

    /** DEVICE_EXPERTISE/PURCHASE_CONTRACT yalnızca PURCHASE kaydında, SALE_CONTRACT/WARRANTY_CERTIFICATE yalnızca SALE kaydında anlamlıdır. */
    public boolean isApplicableTo(DeviceTransaction transaction) {
        return transaction != null && transaction.getType() == requiredTransactionType;
    }

    public File generate(DeviceTransaction transaction, User shop, DocumentRequest request,
                         DocumentFormat format, File outFile) throws Exception {
        return generator.generate(transaction, shop, request, format, outFile);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
