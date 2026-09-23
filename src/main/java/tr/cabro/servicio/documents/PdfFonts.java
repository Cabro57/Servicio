package tr.cabro.servicio.documents;

import com.lowagie.text.DocumentException;
import com.lowagie.text.pdf.BaseFont;

import java.io.IOException;
import java.io.InputStream;

/**
 * Belgelerde kullanılan gömülü fontlar. OpenPDF'in varsayılan Type1 fontları (Helvetica)
 * Türkçe çğıöşü karakterlerini basamadığı için DejaVu Sans, Unicode (Identity-H) olarak gömülür.
 * A4 formlar ve termal fişler aynı kaynağı kullanır.
 */
public final class PdfFonts {

    private static final String REGULAR_PATH = "/fonts/DejaVuSans-Regular.ttf";
    private static final String BOLD_PATH = "/fonts/DejaVuSans-Bold.ttf";

    private static byte[] regularBytes;
    private static byte[] boldBytes;

    private PdfFonts() {
    }

    public static BaseFont regular() throws IOException, DocumentException {
        if (regularBytes == null) regularBytes = read(REGULAR_PATH);
        return create(REGULAR_PATH, regularBytes);
    }

    public static BaseFont bold() throws IOException, DocumentException {
        if (boldBytes == null) boldBytes = read(BOLD_PATH);
        return create(BOLD_PATH, boldBytes);
    }

    // Her belge kendi BaseFont örneğini alır: gömülen alt küme (subset) belgeye özeldir.
    private static BaseFont create(String name, byte[] bytes) throws IOException, DocumentException {
        return BaseFont.createFont(name, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bytes, null);
    }

    private static byte[] read(String classpathPath) throws IOException {
        try (InputStream is = PdfFonts.class.getResourceAsStream(classpathPath)) {
            if (is == null) {
                throw new IOException("Font kaynağı bulunamadı: " + classpathPath);
            }
            return is.readAllBytes();
        }
    }
}
