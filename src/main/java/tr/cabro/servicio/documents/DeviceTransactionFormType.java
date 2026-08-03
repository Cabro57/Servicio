package tr.cabro.servicio.documents;

import tr.cabro.servicio.model.DeviceTransaction;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.model.enums.DeviceTransactionType;

import java.io.File;

/** "Form" popup menüsünde listelenen, ikinci el alım-satım belgelerinin sabit türleri. */
public enum DeviceTransactionFormType {

    DEVICE_EXPERTISE("Cihaz Ekspertizi", new DeviceExpertiseFormGenerator(),
            false, null, null, DeviceTransactionType.PURCHASE),
    PURCHASE_CONTRACT("Satın Alma Sözleşmesi", new PurchaseContractFormGenerator(),
            true, "İşletme", "Satıcı (Müşteri)", DeviceTransactionType.PURCHASE),
    SALE_CONTRACT("Satış Sözleşmesi", new SaleContractFormGenerator(),
            true, "İşletme", "Alıcı (Müşteri)", DeviceTransactionType.SALE),
    WARRANTY_CERTIFICATE("Garanti Belgesi", new WarrantyCertificateFormGenerator(),
            false, null, null, DeviceTransactionType.SALE);

    private final String displayName;
    private final DeviceTransactionFormGenerator generator;
    private final boolean requiresSigners;
    private final String leftSignerLabel;
    private final String rightSignerLabel;
    private final DeviceTransactionType requiredTransactionType;

    DeviceTransactionFormType(String displayName, DeviceTransactionFormGenerator generator,
                              boolean requiresSigners, String leftSignerLabel, String rightSignerLabel,
                              DeviceTransactionType requiredTransactionType) {
        this.displayName = displayName;
        this.generator = generator;
        this.requiresSigners = requiresSigners;
        this.leftSignerLabel = leftSignerLabel;
        this.rightSignerLabel = rightSignerLabel;
        this.requiredTransactionType = requiredTransactionType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean requiresSigners() {
        return requiresSigners;
    }

    public String getLeftSignerLabel() {
        return leftSignerLabel;
    }

    public String getRightSignerLabel() {
        return rightSignerLabel;
    }

    /** DEVICE_EXPERTISE/PURCHASE_CONTRACT yalnızca PURCHASE kaydında, SALE_CONTRACT/WARRANTY_CERTIFICATE yalnızca SALE kaydında anlamlıdır. */
    public boolean isApplicableTo(DeviceTransaction transaction) {
        return transaction != null && transaction.getType() == requiredTransactionType;
    }

    public File generate(DeviceTransaction transaction, User shop, String leftSignerName, String rightSignerName) throws Exception {
        return generator.generate(transaction, shop, leftSignerName, rightSignerName);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
