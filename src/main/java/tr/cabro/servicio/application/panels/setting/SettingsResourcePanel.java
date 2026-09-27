package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.application.component.chart.ColumnChart;
import tr.cabro.servicio.application.component.chart.LiveGraph;
import tr.cabro.servicio.application.component.detail.DetailKit;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Toasts;
import tr.cabro.servicio.util.ResourceMonitor;
import tr.cabro.servicio.util.ResourceMonitor.Disk;
import tr.cabro.servicio.util.ResourceMonitor.Hardware;
import tr.cabro.servicio.util.ResourceMonitor.Sample;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Ayarlar &gt; Sistem &gt; Kaynak Kullanımı: Servicio hangi donanımda çalışıyor ve o donanımın ne
 * kadarını kullanıyor. "Uygulama yavaşladı" dendiğinde operatörün (ya da destek verenin) ilk
 * bakacağı yer.
 * <p>
 * Her bölüm aynı sırayla okunur: önce donanım (bu bilgisayarda ne var), sonra Servicio'nun payı.
 * İşlemci ve bellek son 60 saniyeyi canlı çizer; zamanlayıcı yalnızca sayfa görünürken çalışır.
 * Disk dökümü dosya ağacını gezdiği için açılışta ve "Yeniden ölç" ile arka planda hesaplanır.
 */
public class SettingsResourcePanel extends JPanel implements SettingsModal.HeaderActions {

    private static final int WINDOW = 60;
    private static final Locale TR = Locale.forLanguageTag("tr");

    private final JButton copyButton = SettingsKit.headerButton("Raporu kopyala", "icons/file-text.svg");
    private final Timer timer = new Timer(1000, e -> tick());

    // İşlemci
    private final JLabel cpuName = bold("Okunuyor…");
    private final JLabel cpuCores = DetailKit.small(" ");
    private final JLabel cpuApp = figureValue();
    private final JLabel cpuSystem = mutedFigureValue();
    private final LiveGraph cpuGraph = new LiveGraph(WINDOW)
            .addSeries(ColumnChart.Ink.neutral(0.72f))
            .addSeries(ColumnChart.Ink.neutral(0.32f));

    // Bellek
    private final JLabel ramTotal = bold("—");
    private final JLabel ramFree = DetailKit.small(" ");
    private final JLabel memApp = figureValue();
    private final JLabel memLimit = mutedFigureValue();
    private final Meter heapMeter = new Meter();
    private final JLabel heapText = DetailKit.small(" ");
    private final LiveGraph memGraph = new LiveGraph(WINDOW).addSeries(ColumnChart.Ink.neutral(0.72f));
    private final JLabel gcText = DetailKit.small(" ");

    // Disk
    private final JLabel volumeName = bold("—");
    private final JLabel volumeFree = DetailKit.small(" ");
    private final JPanel diskFacts = DetailKit.facts();
    private final JLabel factDb, factBackups, factLogs, factOther, factInstall, factTotal;
    private final JLabel dataPath = DetailKit.small(" ");

    // Ekran
    private final JPanel screenRows = new JPanel(new MigLayout("wrap, insets 0, gapy 6, fillx", "[grow, fill]"));
    private final JLabel pipeline = DetailKit.small(" ");

    // Ortam
    private final JPanel envFacts = DetailKit.facts();
    private final JLabel factOs, factJava, factUptime, factThreads, factPool;

    private Hardware hardware;
    private Sample last;
    private Disk disk;

    public SettingsResourcePanel() {
        factDb = DetailKit.fact(diskFacts, "Veritabanı");
        factBackups = DetailKit.fact(diskFacts, "Yedekler");
        factLogs = DetailKit.fact(diskFacts, "Günlük dosyaları");
        factOther = DetailKit.fact(diskFacts, "Ayarlar ve diğer");
        factInstall = DetailKit.fact(diskFacts, "Uygulama dosyaları");
        factTotal = DetailKit.fact(diskFacts, "Servicio toplamı");

        factOs = DetailKit.fact(envFacts, "İşletim sistemi");
        factJava = DetailKit.fact(envFacts, "Java");
        factUptime = DetailKit.fact(envFacts, "Açık kalma süresi");
        factThreads = DetailKit.fact(envFacts, "İş parçacığı");
        factPool = DetailKit.fact(envFacts, "Veritabanı bağlantısı");

        setLayout(new MigLayout("fill, insets 0", "[grow, fill]", "[top]"));
        setOpaque(false);
        add(createPage());

        copyButton.addActionListener(e -> copyReport());
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) == 0) return;
            if (isShowing()) {
                // Sayfa saniyede bir kendini güncellediği için kaydırmada piksel kopyalama (blit)
                // eski karelerin harflerini kaydırılmayan alana taşıyıp iz bırakıyordu; basit mod
                // her kaydırmada görünen alanı baştan çizer.
                JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, this);
                if (viewport != null) viewport.setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
                tick();
                timer.start();
            } else {
                timer.stop();
            }
        });

        CompletableFuture.supplyAsync(ResourceMonitor::hardware)
                .thenAccept(h -> SwingUtilities.invokeLater(() -> showHardware(h)))
                .exceptionally(ex -> ErrorHandler.handle(this, "Donanım bilgisi okunamadı", ex));
        measureDisk();
    }

    @Override
    public List<JComponent> headerActions() {
        return List.of(copyButton);
    }

    // ------------------------------------------------------------------ yerleşim

    private JPanel createPage() {
        JPanel page = SettingsKit.page();

        JPanel cpu = SettingsKit.stack();
        cpu.add(hardwareLine(cpuName, cpuCores));
        cpu.add(figures(figure("Servicio", cpuApp), figure("Tüm sistem", cpuSystem)), "gaptop 6");
        cpu.add(cpuGraph, "growx, h 76!, gaptop 4");
        cpu.add(legend("Son 60 saniye  ·  koyu çizgi Servicio, açık çizgi tüm sistem"));
        SettingsKit.section(page, "İşlemci", "Servicio'nun işlemciden aldığı pay; boşta %1'in altında kalır.", cpu);

        JPanel mem = SettingsKit.stack();
        mem.add(hardwareLine(ramTotal, ramFree));
        mem.add(figures(figure("Servicio kullanıyor", memApp), figure("Ayrılabilecek en çok", memLimit)), "gaptop 6");
        JPanel meterRow = new JPanel(new MigLayout("insets 0, fillx, gap 10", "[grow, fill][pref!]", "[center]"));
        meterRow.setOpaque(false);
        meterRow.add(heapMeter, "h 8!");
        meterRow.add(heapText);
        mem.add(meterRow, "gaptop 2");
        mem.add(memGraph, "growx, h 64!, gaptop 4");
        JPanel gcRow = new JPanel(new MigLayout("insets 0, fillx, gap 8", "[grow][]", "[center]"));
        gcRow.setOpaque(false);
        gcRow.add(gcText, "wmin 0");
        gcRow.add(DetailKit.link("Belleği toparla", this::collect));
        mem.add(gcRow);
        SettingsKit.section(page, "Bellek", "Kullanılan bellek iniş çıkış yapar: Java kullanılmayanı ara ara kendisi toplar.", mem);

        JPanel diskBox = SettingsKit.stack();
        diskBox.add(hardwareLine(volumeName, volumeFree));
        diskBox.add(diskFacts, "gaptop 6");
        JPanel pathRow = new JPanel(new MigLayout("insets 0, fillx, gap 8", "[grow][][]", "[center]"));
        pathRow.setOpaque(false);
        pathRow.add(dataPath, "w 0:0:, growx");
        pathRow.add(DetailKit.link("Klasörü aç", this::openDataFolder));
        pathRow.add(DetailKit.link("Yeniden ölç", this::measureDisk));
        diskBox.add(pathRow, "gaptop 2, wmin 0");
        SettingsKit.section(page, "Disk", "Veriler, yedekler ve günlükler bu bilgisayarda tutulur.", diskBox);

        screenRows.setOpaque(false);
        JPanel screenBox = SettingsKit.stack();
        screenBox.add(screenRows);
        screenBox.add(pipeline);
        SettingsKit.section(page, "Ekran ve grafik", "Arayüz bu ekranlarda, bu çizim yoluyla çiziliyor.", screenBox);

        SettingsKit.section(page, "Çalışma ortamı", "Destek isterken \"Raporu kopyala\" ile bu sayfanın tamamını gönderebilirsiniz.", envFacts);
        return page;
    }

    private static JLabel bold(String text) {
        JLabel l = new JLabel(text);
        l.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        return l;
    }

    /** Stili yalnızca değiştiğinde uygular: her saniye yeniden stil vermek gereksiz yerleşim ve çizim tetikliyordu. */
    private static void style(JComponent c, String style) {
        if (!style.equals(c.getClientProperty(FlatClientProperties.STYLE))) c.putClientProperty(FlatClientProperties.STYLE, style);
    }

    private static JLabel figureValue() {
        JLabel l = new JLabel("—");
        l.putClientProperty(FlatClientProperties.STYLE, "font: bold +5");
        return l;
    }

    private static JLabel mutedFigureValue() {
        JLabel l = new JLabel("—");
        l.putClientProperty(FlatClientProperties.STYLE, "font: bold +5; foreground: $Label.disabledForeground");
        return l;
    }

    private static JPanel hardwareLine(JLabel name, JLabel detail) {
        JPanel p = new JPanel(new MigLayout("insets 0, gap 8", "[shrink 100][]", "[baseline]"));
        p.setOpaque(false);
        p.add(name, "w 0:pref:, wmin 0");
        p.add(detail);
        return p;
    }

    private static JPanel figure(String caption, JLabel value) {
        JPanel p = new JPanel(new MigLayout("wrap, insets 0, gap 0", "[]", "[]1[]"));
        p.setOpaque(false);
        p.add(DetailKit.small(caption));
        p.add(value);
        return p;
    }

    private static JPanel figures(JComponent... figures) {
        JPanel p = new JPanel(new MigLayout("insets 0, gap 36", "", "[top]"));
        p.setOpaque(false);
        for (JComponent f : figures) p.add(f);
        return p;
    }

    private static JLabel legend(String text) {
        return DetailKit.small(text);
    }

    // ------------------------------------------------------------------ veri

    private void showHardware(Hardware h) {
        hardware = h;
        cpuName.setText(h.cpuName());
        cpuName.setToolTipText(h.cpuName());
        cpuCores.setText(h.cores() + " mantıksal çekirdek");
        ramTotal.setText(bytes(h.physicalTotal()) + " fiziksel bellek");

        screenRows.removeAll();
        if (h.screens().isEmpty()) screenRows.add(DetailKit.small("Ekran bilgisi okunamadı"));
        int i = 1;
        for (ResourceMonitor.Screen s : h.screens()) {
            String text = s.width() + " × " + s.height()
                    + "   ·   %" + Math.round(s.scale() * 100) + " ölçek"
                    + (s.refreshRate() > 0 ? "   ·   " + s.refreshRate() + " Hz" : "");
            JPanel row = new JPanel(new MigLayout("insets 0, gap 8", "[][]", "[baseline]"));
            row.setOpaque(false);
            row.add(bold(h.screens().size() > 1 ? i + ". ekran" : "Ekran"));
            row.add(new JLabel(text + (s.primary() && h.screens().size() > 1 ? "   ·   ana ekran" : "")));
            screenRows.add(row);
            i++;
        }
        pipeline.setText("Çizim yolu: " + h.pipeline());

        DetailKit.setFact(factOs, h.os());
        DetailKit.setFact(factJava, h.javaVersion() + " · " + h.javaVendor());
        factJava.setToolTipText(h.javaHome());
        revalidate();
        repaint();
    }

    private void tick() {
        Sample s = ResourceMonitor.sample();
        last = s;

        cpuGraph.push(s.processCpu() >= 0 ? s.processCpu() : Double.NaN, s.systemCpu() >= 0 ? s.systemCpu() : Double.NaN);
        cpuApp.setText(percent(s.processCpu()));
        cpuSystem.setText(percent(s.systemCpu()));
        boolean hot = s.processCpu() > 0.5;
        cpuGraph.setAlert(hot);
        style(cpuApp, "font: bold +5" + (hot ? "; foreground: $Servicio.warningColor" : ""));

        long app = s.heapUsed() + s.nonHeapUsed();
        memApp.setText(bytes(app));
        memLimit.setText(bytes(s.heapMax()));
        if (s.physicalFree() >= 0) ramFree.setText(bytes(s.physicalFree()) + " boş  ·  Servicio'nun payı %"
                + oneDecimal(s.physicalTotal() > 0 ? 100.0 * app / s.physicalTotal() : 0));
        double ratio = s.heapMax() > 0 ? (double) s.heapUsed() / s.heapMax() : 0;
        heapMeter.setValue(ratio, ratio > 0.85);
        heapText.setText("Yığın " + bytes(s.heapUsed()) + " / " + bytes(s.heapMax()));
        memGraph.setMax(Math.max(1, s.heapCommitted()));
        memGraph.setAlert(ratio > 0.85);
        memGraph.push(s.heapUsed());
        gcText.setText("Çöp toplama " + s.gcCount() + " kez, toplam " + oneDecimal(s.gcTimeMs() / 1000.0) + " sn");

        DetailKit.setFact(factUptime, duration(s.uptimeMs()));
        DetailKit.setFact(factThreads, s.threads() + " (en çok " + s.peakThreads() + ")");
        DetailKit.setFact(factPool, s.poolTotal() + " bağlantı  ·  " + s.poolActive() + " çalışıyor");
    }

    private void measureDisk() {
        factTotal.setText("ölçülüyor…");
        CompletableFuture.supplyAsync(ResourceMonitor::disk)
                .thenAccept(d -> SwingUtilities.invokeLater(() -> showDisk(d)))
                .exceptionally(ex -> ErrorHandler.handle(this, "Disk kullanımı ölçülemedi", ex));
    }

    private void showDisk(Disk d) {
        disk = d;
        volumeName.setText(d.volume() + " sürücüsü  ·  " + bytes(d.volumeTotal()));
        double freeRatio = d.volumeTotal() > 0 ? (double) d.volumeFree() / d.volumeTotal() : 1;
        volumeFree.setText(bytes(d.volumeFree()) + " boş");
        // Sürücü dolmak üzereyse yedek alınamaz; anlamı olan tek renk bu.
        volumeFree.putClientProperty(FlatClientProperties.STYLE, freeRatio < 0.1
                ? "font: -1 bold; foreground: $Servicio.warningColor" : "font: -1; foreground: $Label.disabledForeground");
        DetailKit.setFact(factDb, bytes(d.database()));
        DetailKit.setFact(factBackups, bytes(d.backups()));
        DetailKit.setFact(factLogs, bytes(d.logs()));
        DetailKit.setFact(factOther, bytes(d.otherData()));
        DetailKit.setFact(factInstall, d.install() >= 0 ? bytes(d.install()) : null);
        factInstall.setToolTipText(d.installPath());
        DetailKit.setFact(factTotal, bytes(d.dataTotal() + Math.max(0, d.install())));
        dataPath.setText("Veri klasörü: " + d.dataPath());
        dataPath.setToolTipText(d.dataPath());
    }

    private void collect() {
        CompletableFuture.supplyAsync(ResourceMonitor::collectGarbage)
                .thenAccept(freed -> SwingUtilities.invokeLater(() -> {
                    Toasts.show(this, Toast.Type.INFO, freed > 1024 * 1024
                            ? bytes(freed) + " bellek boşaldı" : "Toparlanacak bellek yoktu");
                    tick();
                }));
    }

    private void openDataFolder() {
        try {
            Desktop.getDesktop().open(ResourceMonitor.dataFolder());
        } catch (Exception ex) {
            ErrorHandler.handle(this, "Veri klasörü açılamadı", ex);
        }
    }

    /** Sayfanın tamamı düz metin; destek için WhatsApp/e-postaya yapıştırılır. */
    private void copyReport() {
        StringBuilder sb = new StringBuilder("Servicio kaynak raporu\n");
        if (hardware != null) {
            sb.append("İşlemci: ").append(hardware.cpuName()).append(" (").append(hardware.cores()).append(" çekirdek)\n");
            sb.append("Bellek: ").append(bytes(hardware.physicalTotal())).append('\n');
            sb.append("İşletim sistemi: ").append(hardware.os()).append('\n');
            sb.append("Java: ").append(hardware.javaVersion()).append(" · ").append(hardware.javaVendor()).append('\n');
            for (ResourceMonitor.Screen s : hardware.screens()) {
                sb.append("Ekran: ").append(s.width()).append('×').append(s.height())
                        .append(" %").append(Math.round(s.scale() * 100)).append('\n');
            }
            sb.append("Çizim yolu: ").append(hardware.pipeline()).append('\n');
        }
        if (last != null) {
            sb.append("\nServicio işlemci: ").append(percent(last.processCpu()))
                    .append(" · sistem: ").append(percent(last.systemCpu())).append('\n');
            sb.append("Servicio bellek: ").append(bytes(last.heapUsed() + last.nonHeapUsed()))
                    .append(" (yığın ").append(bytes(last.heapUsed())).append(" / ").append(bytes(last.heapMax())).append(")\n");
            sb.append("Boş fiziksel bellek: ").append(bytes(last.physicalFree())).append('\n');
            sb.append("İş parçacığı: ").append(last.threads()).append(" · açık kalma: ").append(duration(last.uptimeMs())).append('\n');
        }
        if (disk != null) {
            sb.append("\nDisk: ").append(disk.volume()).append(' ').append(bytes(disk.volumeFree())).append(" boş / ")
                    .append(bytes(disk.volumeTotal())).append('\n');
            sb.append("Veritabanı ").append(bytes(disk.database())).append(" · yedekler ").append(bytes(disk.backups()))
                    .append(" · günlükler ").append(bytes(disk.logs())).append('\n');
            sb.append("Veri klasörü: ").append(disk.dataPath()).append('\n');
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString().trim()), null);
        Toasts.show(this, Toast.Type.SUCCESS, "Kaynak raporu panoya kopyalandı");
    }

    // ------------------------------------------------------------------ biçim

    private static String percent(double v) {
        if (v < 0) return "—";
        double p = v * 100;
        return "%" + (p < 10 ? oneDecimal(p) : String.valueOf(Math.round(p)));
    }

    private static String oneDecimal(double v) {
        return String.format(TR, "%.1f", v);
    }

    static String bytes(long b) {
        if (b < 0) return "—";
        if (b < 1024) return b + " B";
        double kb = b / 1024.0;
        if (kb < 1024) return Math.round(kb) + " KB";
        double mb = kb / 1024.0;
        if (mb < 1024) return (mb < 10 ? oneDecimal(mb) : String.valueOf(Math.round(mb))) + " MB";
        return oneDecimal(mb / 1024.0) + " GB";
    }

    private static String duration(long ms) {
        long minutes = ms / 60000;
        if (minutes < 1) return "1 dakikadan az";
        long hours = minutes / 60;
        long days = hours / 24;
        if (days > 0) return days + " gün " + (hours % 24) + " sa";
        if (hours > 0) return hours + " sa " + (minutes % 60) + " dk";
        return minutes + " dk";
    }

    /** İnce doluluk çubuğu: nötr mürekkep, eşik aşılınca uyarı rengi. */
    private static final class Meter extends JComponent {
        private double value;
        private boolean alert;

        Meter() {
            setOpaque(false);
            setPreferredSize(new Dimension(200, 8));
        }

        void setValue(double value, boolean alert) {
            this.value = Math.max(0, Math.min(1, value));
            this.alert = alert;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int h = getHeight();
            int w = getWidth();
            g2.setColor(ColumnChart.Ink.neutral(0.10f).get());
            g2.fillRoundRect(0, 0, w, h, h, h);
            int fill = (int) Math.round(w * value);
            if (fill > 0) {
                g2.setColor(alert ? UIManager.getColor("Servicio.warningColor") : ColumnChart.Ink.neutral(0.62f).get());
                g2.fillRoundRect(0, 0, Math.max(fill, h), h, h, h);
            }
            g2.dispose();
        }
    }
}
