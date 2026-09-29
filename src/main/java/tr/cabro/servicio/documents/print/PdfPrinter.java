package tr.cabro.servicio.documents.print;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.Orientation;
import org.apache.pdfbox.printing.PDFPageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tr.cabro.servicio.settings.AppConfig;
import tr.cabro.servicio.settings.AppSettings;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Üretilmiş PDF'leri yazdırma diyaloğu açmadan, seçili yazıcıya gerçek boyutta basar.
 * <p>
 * PDF'ler OpenPDF ile zaten kağıdın boyunda üretiliyor (termal fiş içerik boyunda, A4 belge A4);
 * PDFBox sayfayı {@code ACTUAL_SIZE} ile ölçeklemeden gönderir. Yazıcı seçimi, kopya sayısı
 * makineye özeldir ({@link AppConfig.Printing}); boş yazıcı adı sistem varsayılanı demektir.
 * <p>
 * İşler tek bir arka plan iş parçacığında sırayla yürür: yazıcı sürücüsü çağrıları yavaş olabilir
 * ve arayüzü ya da veritabanı kuyruğunu bekletmemeli; ardışık iki fiş de karışmadan sırayla basılır.
 */
public final class PdfPrinter {

    private static final Logger log = LoggerFactory.getLogger(PdfPrinter.class);

    /** Belgenin hangi yazıcıya gideceği. */
    public enum Role {
        /** Termal rulo fişler: satış, iade, tahsilat, kabul/teslim fişi. */
        RECEIPT,
        /** A4 belgeler: formlar, raporlar. */
        DOCUMENT
    }

    private static final ExecutorService QUEUE = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "servicio-print");
        t.setDaemon(true);
        return t;
    });

    private PdfPrinter() {
    }

    /** Kurulu yazıcıların adları. Ağ yazıcılarında yavaş olabilir; arayüz iş parçacığında çağrılmamalı. */
    public static List<String> installedPrinters() {
        List<String> names = new ArrayList<>();
        for (PrintService service : PrintServiceLookup.lookupPrintServices(null, null)) {
            names.add(service.getName());
        }
        return names;
    }

    /** Sistem varsayılan yazıcısının adı; tanımlı değilse {@code null}. */
    public static String defaultPrinterName() {
        PrintService service = PrintServiceLookup.lookupDefaultPrintService();
        return service != null ? service.getName() : null;
    }

    /** Role göre ayarlardaki yazıcı adı; boşsa sistem varsayılanı kullanılır. */
    public static String configuredPrinter(Role role) {
        AppConfig.Printing printing = AppSettings.get().getPrinting();
        return role == Role.RECEIPT ? printing.getReceiptPrinter() : printing.getDocumentPrinter();
    }

    private static int configuredCopies(Role role) {
        AppConfig.Printing printing = AppSettings.get().getPrinting();
        return role == Role.RECEIPT ? printing.getReceiptCopies() : printing.getDocumentCopies();
    }

    /**
     * PDF'i rolün yazıcısına, ayarlardaki kopya sayısıyla sıraya koyar.
     *
     * @return işin gönderildiği yazıcının adı
     */
    public static CompletableFuture<String> printAsync(File pdf, Role role, String jobName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return print(pdf, role, jobName);
            } catch (Exception ex) {
                throw new PrintFailedException(ex.getMessage(), ex);
            }
        }, QUEUE);
    }

    /** Engelleyerek yazdırır; {@link #printAsync} dışında yalnızca zaten arka plandaysa kullanılmalı. */
    public static String print(File pdf, Role role, String jobName) throws IOException, PrinterException {
        PrintService service = resolve(configuredPrinter(role));
        int copies = configuredCopies(role);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            // dpi 0: vektörel gönderim (rasterleştirme yok); center false: sayfa kağıdın başına oturur,
            // kenar boşlukları PDF'in içinde zaten var.
            PDFPageable pageable = new PDFPageable(document, Orientation.AUTO, false, 0f, false);
            // Kopya sayısı sürücüye bırakılmaz (termal sürücülerin çoğu yok sayıyor): her kopya ayrı iş,
            // böylece her fiş ayrı kesilir.
            for (int i = 0; i < copies; i++) {
                PrinterJob job = PrinterJob.getPrinterJob();
                job.setPrintService(service);
                job.setJobName(jobName);
                job.setPageable(pageable);
                job.print();
            }
        }
        log.info("Yazdırıldı: {} → {} ({} kopya)", jobName, service.getName(), copies);
        return service.getName();
    }

    private static PrintService resolve(String name) throws PrinterException {
        if (name == null || name.isBlank()) {
            PrintService service = PrintServiceLookup.lookupDefaultPrintService();
            if (service == null) throw new PrinterException("Sistemde varsayılan yazıcı tanımlı değil.");
            return service;
        }
        for (PrintService service : PrintServiceLookup.lookupPrintServices(null, null)) {
            if (service.getName().equals(name)) return service;
        }
        throw new PrinterException("Seçili yazıcı bulunamadı: " + name);
    }

    /** {@link #printAsync} hatası; mesajı kullanıcıya gösterilecek kadar açıktır. */
    public static final class PrintFailedException extends RuntimeException {
        PrintFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
