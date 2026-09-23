package tr.cabro.servicio.settings;

/**
 * Termal fiş yazıcısının kağıt genişliği. Yazıcı makineye bağlı olduğu için {@code config.json}'da
 * tutulur (bkz. {@link AppConfig.Printing}).
 * <p>
 * Basılabilir alan kağıttan dardır: 80mm rulo yazıcılarda ~72mm (satırda 48 karakter),
 * 58mm rulo yazıcılarda ~48mm (satırda 32 karakter).
 */
public enum ReceiptPaperWidth {

    MM_80("80 mm", 80f, 72f, 8.5f),
    MM_58("58 mm", 58f, 48f, 7.5f);

    private final String displayName;
    private final float paperMm;
    private final float printableMm;
    private final float baseFontSize;

    ReceiptPaperWidth(String displayName, float paperMm, float printableMm, float baseFontSize) {
        this.displayName = displayName;
        this.paperMm = paperMm;
        this.printableMm = printableMm;
        this.baseFontSize = baseFontSize;
    }

    public float getPaperMm() {
        return paperMm;
    }

    public float getPrintableMm() {
        return printableMm;
    }

    /** Gövde metninin punto değeri; diğer boyutlar buna göre ölçeklenir. */
    public float getBaseFontSize() {
        return baseFontSize;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
