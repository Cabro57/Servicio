package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import net.miginfocom.swing.MigLayout;
import raven.modal.ModalDialog;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.detail.DetailKit;
import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.themes.BadgePalette;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.model.enums.BadgeColor;
import tr.cabro.servicio.updater.UpdateManifest;
import tr.cabro.servicio.updater.UpdateService;
import tr.cabro.servicio.updater.UpdateService.State;
import tr.cabro.servicio.util.DesktopHelper;
import tr.cabro.servicio.util.DialogHelper;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;

/**
 * Güncelleme penceresi: bulunan sürümün notları ve tek birincil eylem. Alt çubuktaki göstergeden ya da
 * Ayarlar &gt; Güncelleme'den açılır; kendiliğinden hiç açılmaz.
 * <p>
 * Pencere durumu {@link UpdateService}'ten okur ve dinler: açıkken indirme başlarsa ilerlemeyi, biterse
 * "Yeniden başlat"ı gösterir. "İndir ve kur" indirmeyi başlatıp pencereyi kapatır; indirme alt çubukta
 * sürer. Yeniden başlatma sorusu bu pencerenin içinde ayrı bir modal olarak sorulmaz (önceki sürümde
 * iç içe modal, dıştakiyle birlikte kapanıyordu).
 */
public class UpdateModal extends JPanel {

    public static final String MODAL_ID = "app-update";

    private static final String RELEASES_URL = "https://github.com/Cabro57/Servicio/releases";

    private final UpdateService service = Servicio.getInstance().getUpdateService();
    private final Runnable listener = this::render;

    private final JPanel notes = new JPanel(new MigLayout("wrap, insets 0, fillx, gap 0, hidemode 3", "[grow, fill]"));
    private final JPanel stateBlock = new JPanel(new MigLayout("wrap, insets 0, fillx, gap 0, hidemode 3", "[grow, fill]"));
    private final JLabel progressText = DetailKit.small("");
    private final JLabel progressPercent = DetailKit.small("");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel stateLine = new JLabel();
    private final JTextArea stateHint = FormKit.hint("");

    private final JButton skip = DetailKit.link("Bu sürümü atla", this::skip);
    private final JButton secondary = DetailKit.secondaryButton("", null, null);
    private final JButton primary = DetailKit.primaryButton("", null, null);

    private String notesVersion;

    /** Pencereyi açar; bulunmuş bir sürüm yoksa Ayarlar &gt; Güncelleme'ye gider. */
    public static void open() {
        if (ModalDialog.isIdExist(MODAL_ID)) return;
        UpdateService service = Servicio.getInstance().getUpdateService();
        if (service.getManifest() == null) {
            FormManager.showSettings(SETTINGS_PAGE);
            return;
        }
        AppModal.showModal(Servicio.getInstance().getFrame(),
                new SimpleModalBorder(new UpdateModal(), "Güncelleme"),
                ModalDialog.createOption(), MODAL_ID);
    }

    /** Ayarlar'daki güncelleme sayfasının kimliği (grup/başlık). */
    public static final String SETTINGS_PAGE = "Sistem/Güncelleme";

    /**
     * İndirilmiş güncellemeyi kurup uygulamayı yeniden açar. Ekranda açık bir pencere (yarım kalmış
     * bir kayıt olabilir) varsa önce sorar.
     */
    public static void restart(Component parent) {
        Runnable go = () -> Servicio.getInstance().getUpdateService().restartNow(
                e -> DialogHelper.error(parent, "updater.apply.failed", String.valueOf(e.getMessage())));
        if (AppModal.hasOpenModal()) {
            DialogHelper.confirm(parent, "updater.restart.confirm.title", "updater.restart.confirm.message", go);
        } else {
            go.run();
        }
    }

    private UpdateModal() {
        setLayout(new MigLayout("wrap, insets 2 24 18 24, fillx, gap 0, hidemode 3", "[grow, fill]"));
        UpdateManifest manifest = service.getManifest();

        // --- Kimlik: hangi sürüm, ne zaman, şu an ne kullanılıyor ---
        JLabel title = new JLabel("Servicio " + manifest.getVersion());
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +6");
        JPanel titleRow = new JPanel(new MigLayout("insets 0, gap 10", "[][]", "[center]"));
        titleRow.setOpaque(false);
        titleRow.add(title);
        if (service.isHotfix()) {
            JLabel badge = new JLabel("Kritik yama");
            badge.setOpaque(true);
            badge.putClientProperty(FlatClientProperties.STYLE, BadgePalette.style(BadgeColor.YELLOW, null));
            badge.setToolTipText("Aynı sürümün düzeltilmiş dosyaları; atlanamaz");
            titleRow.add(badge);
        }
        add(titleRow);
        add(DetailKit.muted(identityLine(manifest)), "gaptop 2");

        // --- Sürüm notları ---
        notes.setOpaque(false);
        JPanel card = new JPanel(new MigLayout("wrap, insets 14 16 14 16, fillx, gap 0", "[grow, fill]", "[]8[grow, fill]"));
        card.putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");
        card.add(DetailKit.title("Bu sürümde"));
        JScrollPane scroll = DetailKit.scroll(notes);
        card.add(scroll, "hmin 60, hmax " + UIScale.scale(300));
        add(card, "gaptop 16");

        // --- Durum: indirme ilerlemesi, hata ya da hazır bilgisi ---
        stateBlock.setOpaque(false);
        progressBar.putClientProperty(FlatClientProperties.STYLE, "arc: 6");
        progressBar.putClientProperty("JProgressBar.largeHeight", Boolean.FALSE);
        JPanel progressHead = new JPanel(new MigLayout("insets 0, fillx, gap 8", "[grow][]", "[]"));
        progressHead.setOpaque(false);
        progressHead.add(progressText, "wmin 0");
        progressHead.add(progressPercent);
        stateBlock.add(progressHead);
        stateBlock.add(progressBar, "gaptop 6, h " + UIScale.scale(6) + "!");
        stateLine.setIconTextGap(8);
        stateBlock.add(stateLine);
        stateBlock.add(stateHint, "gaptop 4, gapleft " + UIScale.scale(24) + ", wmin 0");
        add(stateBlock, "gaptop 14");

        // --- Eylemler ---
        JPanel footer = new JPanel(new MigLayout("insets 0, fillx, gap 8", "[]push[][]", "[center]"));
        footer.setOpaque(false);
        skip.setToolTipText("Bu sürüm bir daha kendiliğinden bildirilmez; Ayarlar > Güncelleme'den geri alınabilir");
        footer.add(skip);
        footer.add(secondary);
        footer.add(primary);
        secondary.addActionListener(e -> onSecondary());
        primary.addActionListener(e -> onPrimary());
        add(footer, "gaptop 18");

        render();
    }

    private String identityLine(UpdateManifest manifest) {
        String line = "Kullandığınız sürüm " + service.getCurrentVersion();
        try {
            if (manifest.getReleaseDate() != null && !manifest.getReleaseDate().isBlank()) {
                LocalDate date = LocalDate.parse(manifest.getReleaseDate().substring(0, 10));
                line += "   ·   " + DateFormats.shortDate().format(date) + " tarihinde yayınlandı";
            }
        } catch (Exception ignored) {
            // Tarih biçimi beklenmedikse yalnızca sürüm gösterilir.
        }
        return line;
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        size.width = UIScale.scale(560);
        return size;
    }

    // ------------------------------------------------------------------ durum

    private void render() {
        State state = service.getState();
        UpdateManifest manifest = service.getManifest();
        if (manifest != null && !manifest.getVersion().equals(notesVersion)) {
            notesVersion = manifest.getVersion();
            loadNotes();
        }

        boolean downloading = state == State.DOWNLOADING;
        progressText.getParent().setVisible(downloading);
        progressBar.setVisible(downloading);
        stateLine.setVisible(false);
        stateHint.setVisible(false);
        skip.setVisible(state == State.AVAILABLE && !service.isHotfix());
        secondary.setVisible(true);
        primary.setVisible(true);
        primary.setEnabled(true);
        secondary.setEnabled(true);

        switch (state) {
            case DOWNLOADING -> {
                int value = (int) Math.round(service.getProgress() * 100);
                progressBar.setValue(value);
                progressPercent.setText("%" + value);
                progressText.setText(service.getCurrentFile() != null
                        ? "İndiriliyor: " + service.getCurrentFile() : "İndirme hazırlanıyor…");
                buttons("İndirmeyi iptal et", "Arka planda sürdür", null);
            }
            case READY -> {
                line("İndirme tamamlandı", "icons/package-check.svg", "Servicio.successColor",
                        "Yeniden başlatınca kurulur. Şimdi yapmazsanız uygulamayı kapatırken kurulur ve bir sonraki açılışta hazır olur.");
                buttons("Sonra", "Yeniden başlat", "icons/refresh-cw.svg");
            }
            case RESTARTING -> {
                line("Güncelleme kuruluyor…", "icons/loader.svg", "Label.disabledForeground", null);
                buttons("Sonra", "Yeniden başlat", "icons/refresh-cw.svg");
                primary.setEnabled(false);
                secondary.setEnabled(false);
            }
            case DOWNLOAD_FAILED -> {
                line("Güncelleme indirilemedi", "icons/circle-x.svg", "Servicio.dangerColor",
                        failureHint(service.getError()));
                buttons("Kapat", "Tekrar dene", "icons/refresh-cw.svg");
            }
            default -> {
                // Sürüm atlandı ya da artık güncel: pencerenin göstereceği bir şey kalmadı.
                if (manifest == null) {
                    close();
                    return;
                }
                // AVAILABLE; ya da bulunmuş sürüm dururken elle yeniden denetleniyor.
                buttons("Sonra", "İndir ve kur", "icons/download.svg");
                primary.setEnabled(state == State.AVAILABLE);
            }
        }
        revalidate();
        repaint();
    }

    private static String failureHint(String error) {
        String base = "İnternet bağlantınızı kontrol edip tekrar deneyin. Sorun sürerse güncellemeyi proje sayfasından indirebilirsiniz.";
        return error == null || error.isBlank() ? base : base + "\nAyrıntı: " + error;
    }

    private void line(String text, String icon, String colorKey, String hint) {
        stateLine.setText(text);
        stateLine.setIcon(new Ikon(icon, 16, colorKey));
        stateLine.putClientProperty(FlatClientProperties.STYLE, "font: bold; foreground: $" + colorKey);
        stateLine.setVisible(true);
        if (hint != null) {
            stateHint.setText(hint);
            stateHint.setVisible(true);
        }
    }

    private void buttons(String secondaryText, String primaryText, String primaryIcon) {
        secondary.setText(secondaryText);
        primary.setText(primaryText);
        primary.setIcon(primaryIcon != null ? new Ikon(primaryIcon, 16, "Servicio.onAccentForeground") : null);
    }

    private void onPrimary() {
        switch (service.getState()) {
            case AVAILABLE, DOWNLOAD_FAILED -> {
                service.download();
                close();
            }
            case DOWNLOADING -> close();
            case READY -> {
                close();
                restart(Servicio.getInstance().getFrame());
            }
            default -> { }
        }
    }

    private void onSecondary() {
        if (service.getState() == State.DOWNLOADING) {
            service.cancelDownload();
        } else {
            close();
        }
    }

    private void skip() {
        service.skipVersion();
        close();
    }

    private void close() {
        if (ModalDialog.isIdExist(MODAL_ID)) AppModal.closeModal(MODAL_ID);
    }

    // ------------------------------------------------------------------ sürüm notları

    private void loadNotes() {
        notes.removeAll();
        notes.add(DetailKit.small("Sürüm notları yükleniyor…"));
        service.fetchReleaseInfo(this::showNotes, e -> showNotesError());
    }

    private void showNotes(UpdateManifest.GitHubReleaseInfo info) {
        notes.removeAll();
        List<String> lines = info.changeLines;
        if (lines == null || lines.isEmpty()) {
            notes.add(DetailKit.small("Bu sürüm için not yazılmamış."));
        } else {
            boolean first = true;
            for (String raw : lines) {
                boolean heading = raw.startsWith("**") && raw.endsWith("**") && raw.length() > 4;
                String text = plain(heading ? raw.substring(2, raw.length() - 2) : raw);
                if (text.isEmpty()) continue;
                if (heading) {
                    JLabel h = new JLabel(text);
                    h.putClientProperty(FlatClientProperties.STYLE, "font: bold");
                    notes.add(h, first ? "" : "gaptop 12");
                } else {
                    notes.add(bullet(text), "gaptop 4");
                }
                first = false;
            }
        }
        notes.revalidate();
        notes.repaint();
    }

    private void showNotesError() {
        notes.removeAll();
        notes.add(DetailKit.small("Sürüm notları alınamadı."));
        JButton open = DetailKit.link("Proje sayfasında oku", () -> DesktopHelper.browseUrl(RELEASES_URL));
        notes.add(open, "gaptop 4, gapleft -6, growx 0");
        notes.revalidate();
        notes.repaint();
    }

    /** Madde: soluk nokta + kelimeden kırılan metin. */
    private static JPanel bullet(String text) {
        JPanel row = new JPanel(new MigLayout("insets 0, gap 8, fillx", "[][grow, fill]", "[top]"));
        row.setOpaque(false);
        JLabel dot = new JLabel("•");
        dot.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
        JTextArea body = FormKit.hint(text);
        body.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.foreground; background: null; margin: 0,0,0,0");
        row.add(dot);
        row.add(body, "wmin 0");
        return row;
    }

    /** GitHub notlarındaki satır içi Markdown işaretlerini temizler. */
    private static String plain(String text) {
        return text.replaceAll("\\[([^]]+)]\\([^)]*\\)", "$1")
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .trim();
    }

    // ------------------------------------------------------------------ yaşam döngüsü

    @Override
    public void addNotify() {
        super.addNotify();
        service.addListener(listener);
    }

    @Override
    public void removeNotify() {
        service.removeListener(listener);
        super.removeNotify();
    }
}
