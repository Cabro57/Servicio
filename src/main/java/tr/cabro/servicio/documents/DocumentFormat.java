package tr.cabro.servicio.documents;

/** Belgenin kaydedilebildiği dosya biçimleri. Termal fişler yalnızca PDF'tir. */
public enum DocumentFormat {

    PDF("PDF Belgesi", "pdf"),
    DOCX("Word Belgesi", "docx"),
    RTF("Zengin Metin (RTF)", "rtf");

    private final String displayName;
    private final String extension;

    DocumentFormat(String displayName, String extension) {
        this.displayName = displayName;
        this.extension = extension;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getExtension() {
        return extension;
    }
}
