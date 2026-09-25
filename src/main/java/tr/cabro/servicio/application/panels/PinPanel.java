package tr.cabro.servicio.application.panels;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.util.UIScale;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.MainUI;
import tr.cabro.servicio.application.system.Form;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.i18n.AppLocale;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

/**
 * Kilit ve açılış ekranı: "tezgâh saati".
 * <p>
 * Ekran kilitliyken tezgâhta müşteriye dönük durur; bu yüzden yalnızca işletme adı, saat ve tarih
 * görünür, kasa/borç gibi hiçbir iş bilgisi görünmez. PIN için ayrı bir alan yok: ekran açıkken
 * herhangi bir rakam tuşu doğrudan PIN'e yazılır (numpad dahil), Backspace siler, Esc temizler,
 * Ctrl+V yapıştırır. Altı hane dolunca kendiliğinden denetlenir.
 * <p>
 * Yanlış PIN modal hata penceresi açmaz: noktalar sallanır ve alttaki satır kırmızıyla söyler.
 * Beş yanlış denemeden sonra giriş 30 saniye bekletilir. Açık bir oturum kilitlendiyse alt satır
 * kilit açılınca geri gelecek pencereleri hatırlatır.
 */
public class PinPanel extends Form {

    private static final int PIN_LENGTH = 6;
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration COOLDOWN = Duration.ofSeconds(30);
    // Tezgâh saati 24 saat biçiminde; saniye ayrı, küçük ve soluk yazılır.
    private static final DateTimeFormatter HOURS_MINUTES = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter SECONDS = DateTimeFormatter.ofPattern("ss");

    private final StringBuilder pin = new StringBuilder();
    private final PinDots dots = new PinDots(PIN_LENGTH);
    private final JLabel businessName = new JLabel("Servicio");
    private final JLabel logo = new JLabel();
    private final JLabel time = new JLabel();
    private final JLabel seconds = new JLabel();
    private final JLabel date = new JLabel();
    private final JLabel status = new JLabel();
    private final JLabel session = new JLabel();
    private final Timer clock = new Timer(1000, e -> tick());
    private final LightBackground lights = new LightBackground();

    private boolean verifying;
    private int failures;
    private LocalDateTime cooldownUntil;

    public PinPanel() {
        setLayout(new MigLayout("fill, insets 22 28 16 28, hidemode 3", "[grow, fill]", "[grow]"));
        setFocusable(true);
        add(footer(), "dock south");
        // Saat paneli başlık ve alt satırdan bağımsız olarak ekranın tam ortasında durur.
        add(center(), "pos 0.5al 0.5al");
        bindKeys();
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { requestFocusInWindow(); }
        });
        getAccessibleContext().setAccessibleName("Kilit ekranı. PIN kodunuzu yazın.");
    }

    // ------------------------------------------------------------------ yerleşim

    /** Kartın başlığı: işletme logosu ve adı, ortada; altında ince çizgi saati ayırır. */
    private JComponent header() {
        JPanel p = new JPanel(new MigLayout("insets 0, gap 10, hidemode 3", "[][]", "[center]"));
        p.setOpaque(false);
        businessName.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        logo.setVisible(false);
        p.add(logo);
        p.add(businessName);
        return p;
    }

    /**
     * Saat kartı: uygulamanın diğer kartlarıyla aynı (listCard: tablo zemini, ince çizgi, 15px köşe).
     * İçinde saat (saniye küçük ve soluk), tarih, PIN noktaları ve durum satırı.
     */
    private JComponent center() {
        JPanel p = new JPanel(new MigLayout("insets 24 56 30 56, wrap, gap 0", "[center]", ""));
        p.putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");
        time.putClientProperty(FlatClientProperties.STYLE, "font: +64 $light.font");
        seconds.putClientProperty(FlatClientProperties.STYLE, "font: +22 $light.font; foreground: $Label.disabledForeground");
        date.putClientProperty(FlatClientProperties.STYLE, "font: +3; foreground: $Label.disabledForeground");
        status.setIconTextGap(6);
        JPanel clockRow = new JPanel(new MigLayout("insets 0, gap 6", "[][]", "[baseline]"));
        clockRow.setOpaque(false);
        clockRow.add(time);
        clockRow.add(seconds);
        p.add(header());
        p.add(new JSeparator(), "growx, gaptop 16, gapbottom 22, wmin 240");
        p.add(clockRow);
        p.add(date, "gaptop 2");
        p.add(dots, "gaptop 48");
        p.add(status, "gaptop 18");
        return p;
    }

    private JComponent footer() {
        JPanel p = new JPanel(new MigLayout("insets 0, fillx, gap 8, hidemode 3", "[grow][][]", "[center]"));
        p.setOpaque(false);
        session.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
        session.setIconTextGap(6);
        p.add(session, "wmin 0");

        JLabel version = new JLabel("v" + Servicio.getInstance().getAppVersion());
        version.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
        p.add(version);

        JButton exit = new JButton("Çıkış", new Ikon("icons/x.svg", 14, "Label.disabledForeground"));
        exit.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        exit.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground; arc: 10; margin: 4,8,4,8");
        exit.setToolTipText("Uygulamayı kapat");
        exit.setFocusable(false);
        exit.addActionListener(e -> {
            if (SwingUtilities.getWindowAncestor(this) instanceof MainUI ui) ui.attemptExit();
        });
        p.add(exit);
        return p;
    }

    // ------------------------------------------------------------------ gösterim

    /** Ekran her gösterildiğinde: PIN boş, saat işliyor, işletme ve kilit bilgisi güncel, odak ekranda. */
    @Override
    public void addNotify() {
        super.addNotify();
        pin.setLength(0);
        verifying = false;
        dots.setFilled(0, false);
        showHint();
        tick();
        clock.start();
        refreshSession();
        loadBusiness();
        SwingUtilities.invokeLater(this::requestFocusInWindow);
    }

    @Override
    public void removeNotify() {
        clock.stop();
        super.removeNotify();
    }

    private void tick() {
        LocalDateTime now = LocalDateTime.now();
        time.setText(now.format(HOURS_MINUTES));
        seconds.setText(now.format(SECONDS));
        date.setText(now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(AppLocale.uiLocale())));
        if (cooldownUntil != null) {
            long left = Duration.between(now, cooldownUntil).toSeconds();
            if (left > 0) {
                setStatus("Çok fazla yanlış deneme. " + left + " sn sonra tekrar deneyin.", "icons/hourglass.svg", "Servicio.warningColor");
            } else {
                cooldownUntil = null;
                failures = 0;
                showHint();
            }
        }
    }

    private void refreshSession() {
        if (!FormManager.hasOpenSession()) {
            session.setVisible(false);
            return;
        }
        int modals = FormManager.pendingModalCount();
        session.setIcon(new Ikon("icons/lock.svg", 13, "Label.disabledForeground"));
        session.setText(modals > 0
                ? "Ekran kilitli  ·  açık " + (modals == 1 ? "pencereniz" : modals + " pencereniz") + " ve yazdıklarınız kilit açılınca geri gelecek"
                : "Ekran kilitli  ·  kilit açılınca kaldığınız sayfaya dönersiniz");
        session.setVisible(true);
    }

    private void loadBusiness() {
        ServiceManager.getUserService().get(1L).thenAccept(user -> SwingUtilities.invokeLater(() ->
                applyBusiness(user.orElse(null)))).exceptionally(ex -> {
            Servicio.getLogger().warn("Kilit ekranı için işletme bilgisi okunamadı", ex);
            return null;
        });
    }

    private void applyBusiness(User user) {
        String name = user != null && user.getBusinessName() != null && !user.getBusinessName().isBlank()
                ? user.getBusinessName() : "Servicio";
        businessName.setText(name);
        Icon icon = null;
        if (user != null && user.getLogoPath() != null && !user.getLogoPath().isBlank()) {
            File file = new File(new File(Servicio.getInstance().getDataFolder(), "logos"), user.getLogoPath());
            if (file.isFile()) {
                ImageIcon raw = new ImageIcon(file.getAbsolutePath());
                int h = UIScale.scale(28);
                if (raw.getIconHeight() > 0) {
                    int w = Math.max(1, raw.getIconWidth() * h / raw.getIconHeight());
                    icon = new ImageIcon(raw.getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH));
                }
            }
        }
        logo.setIcon(icon);
        logo.setVisible(icon != null);
    }

    // ------------------------------------------------------------------ giriş

    private void bindKeys() {
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                char c = e.getKeyChar();
                if (Character.isDigit(c) && !e.isControlDown() && !e.isAltDown()) type(String.valueOf(c));
            }

            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_BACK_SPACE -> backspace();
                    case KeyEvent.VK_ESCAPE -> clear();
                    case KeyEvent.VK_ENTER -> submit();
                    case KeyEvent.VK_V -> { if (e.isControlDown()) paste(); }
                    default -> { }
                }
            }
        });
    }

    private boolean blocked() {
        return verifying || cooldownUntil != null;
    }

    private void type(String digits) {
        if (blocked()) return;
        for (char c : digits.toCharArray()) {
            if (pin.length() >= PIN_LENGTH) break;
            if (Character.isDigit(c)) pin.append(c);
        }
        dots.setFilled(pin.length(), false);
        showHint();
        if (pin.length() == PIN_LENGTH) verify();
    }

    private void backspace() {
        if (blocked() || pin.length() == 0) return;
        pin.setLength(pin.length() - 1);
        dots.setFilled(pin.length(), false);
        showHint();
    }

    private void clear() {
        if (blocked()) return;
        pin.setLength(0);
        dots.setFilled(0, false);
        showHint();
    }

    private void submit() {
        if (blocked()) return;
        if (pin.length() < PIN_LENGTH) {
            dots.shake();
            setStatus("PIN " + PIN_LENGTH + " haneli; " + (PIN_LENGTH - pin.length()) + " hane eksik.",
                    "icons/circle-alert.svg", "Servicio.warningColor");
        }
    }

    private void paste() {
        try {
            Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            if (data instanceof String text) type(text.replaceAll("\\D", ""));
        } catch (Exception ignored) {
            // Panoda metin yoksa yapıştırılacak bir şey de yok.
        }
    }

    private void verify() {
        verifying = true;
        setStatus("Denetleniyor…", null, null);
        String entered = pin.toString();
        ServiceManager.getUserService().authenticate(entered).whenComplete((ok, ex) -> SwingUtilities.invokeLater(() -> {
            verifying = false;
            pin.setLength(0);
            if (ex != null) {
                Servicio.getLogger().error("PIN doğrulanamadı", ex);
                dots.setFilled(0, false);
                setStatus("PIN denetlenemedi. Tekrar deneyin; sürerse uygulamayı yeniden başlatın.",
                        "icons/circle-alert.svg", "Servicio.dangerColor");
                return;
            }
            if (Boolean.TRUE.equals(ok)) {
                failures = 0;
                dots.setFilled(0, false);
                FormManager.unlock();
                return;
            }
            failures++;
            dots.setFilled(PIN_LENGTH, true);
            dots.shake();
            // Sallanma bitince noktalar boşalır; operatör hemen yeniden yazabilir.
            Timer reset = new Timer(420, e -> dots.setFilled(pin.length(), false));
            reset.setRepeats(false);
            reset.start();
            if (failures >= MAX_ATTEMPTS) {
                cooldownUntil = LocalDateTime.now().plus(COOLDOWN);
                tick();
            } else {
                int left = MAX_ATTEMPTS - failures;
                setStatus("PIN yanlış. " + (left <= 2 ? left + " deneme hakkınız kaldı." : "Tekrar deneyin."),
                        "icons/circle-alert.svg", "Servicio.dangerColor");
            }
        }));
    }

    private void showHint() {
        if (cooldownUntil != null || verifying) return;
        setStatus(pin.length() == 0 ? "Kilidi açmak için PIN kodunuzu yazın" : " ", null, null);
    }

    private void setStatus(String text, String icon, String colorKey) {
        String color = colorKey != null ? colorKey : "Label.disabledForeground";
        status.setText(text);
        status.setIcon(icon != null ? new Ikon(icon, 14, color) : null);
        status.putClientProperty(FlatClientProperties.STYLE, "foreground: $" + color);
    }

    // ------------------------------------------------------------------ zemin

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        lights.paint((Graphics2D) g, getWidth(), getHeight());
    }

    /**
     * Sabit, temadan türeyen ışıklar: üç yumuşak ışık lekesi, renkleri vurgu renginden türetilir
     * (vurgunun kendisi ve tonu iki yana biraz kaydırılmış iki kardeşi). Hareket yok. Zemin boyut,
     * tema ya da vurgu değişince bir kez yeniden çizilir; diğer boyamalarda hazır resim kullanılır.
     */
    private static final class LightBackground {
        // Her ışık: merkez x/y (ekran oranı), çap (uzun kenar oranı), ton kayması (derece).
        private static final double[][] LIGHTS = {
                {0.18, 0.22, 0.70, 0},
                {0.86, 0.80, 0.66, 28},
                {0.80, 0.12, 0.44, -26},
        };

        private BufferedImage cache;
        private Color cachedAccent;
        private boolean cachedDark;

        void paint(Graphics2D g, int w, int h) {
            if (w <= 0 || h <= 0) return;
            Color accent = UIManager.getColor("Component.accentColor");
            boolean dark = FlatLaf.isLafDark();
            if (cache == null || cache.getWidth() != w || cache.getHeight() != h
                    || !accent.equals(cachedAccent) || dark != cachedDark) {
                cache = render(w, h, accent, dark);
                cachedAccent = accent;
                cachedDark = dark;
            }
            g.drawImage(cache, 0, 0, null);
        }

        private static BufferedImage render(int w, int h, Color accent, boolean dark) {
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Koyu temada ışık daha belirgin olabilir; açık temada yazıyı bastırmasın diye daha hafif.
            float alpha = dark ? 0.32f : 0.20f;
            int longSide = Math.max(w, h);
            for (double[] l : LIGHTS) {
                Color c = shiftHue(accent, (float) l[3]);
                float r = (float) (longSide * l[2] / 2);
                float cx = (float) (l[0] * w), cy = (float) (l[1] * h);
                g2.setPaint(new RadialGradientPaint(cx, cy, r, new float[]{0f, 0.45f, 1f},
                        new Color[]{withAlpha(c, alpha), withAlpha(c, alpha * 0.4f), withAlpha(c, 0f)}));
                g2.fillRect((int) (cx - r), (int) (cy - r), (int) (2 * r), (int) (2 * r));
            }
            g2.dispose();
            return img;
        }

        private static Color shiftHue(Color c, float degrees) {
            float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
            float hue = (hsb[0] + degrees / 360f + 1f) % 1f;
            return Color.getHSBColor(hue, hsb[1], hsb[2]);
        }

        private static Color withAlpha(Color c, float a) {
            return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(a * 255));
        }
    }

    // ------------------------------------------------------------------ noktalar

    /**
     * PIN haneleri: boş hane ince halka, dolu hane dolu daire. Yanlış PIN'de noktalar tehlike rengine
     * döner ve yatayda sönümlenerek sallanır (ekran okuyucu için durum satırı ayrıca söyler).
     */
    private static final class PinDots extends JComponent {
        private static final int SIZE = 14, GAP = 18, SHAKE_MS = 380;

        private final int length;
        private int filled;
        private boolean error;
        private long shakeStart;
        private final Timer anim = new Timer(15, e -> {
            if (System.currentTimeMillis() - shakeStart > SHAKE_MS) ((Timer) e.getSource()).stop();
            repaint();
        });

        PinDots(int length) {
            this.length = length;
        }

        void setFilled(int filled, boolean error) {
            this.filled = filled;
            this.error = error;
            repaint();
        }

        void shake() {
            shakeStart = System.currentTimeMillis();
            anim.restart();
        }

        @Override
        public Dimension getPreferredSize() {
            int size = UIScale.scale(SIZE), gap = UIScale.scale(GAP);
            // Sallanma payı: noktalar kendi alanının dışına taşıp kesilmesin.
            return new Dimension(length * size + (length - 1) * gap + UIScale.scale(24), size + UIScale.scale(4));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int size = UIScale.scale(SIZE), gap = UIScale.scale(GAP);
            int total = length * size + (length - 1) * gap;
            double offset = 0;
            long elapsed = System.currentTimeMillis() - shakeStart;
            if (anim.isRunning() && elapsed < SHAKE_MS) {
                double t = elapsed / (double) SHAKE_MS;
                offset = Math.sin(t * Math.PI * 6) * UIScale.scale(10) * (1 - t);
            }
            int x0 = (int) Math.round((getWidth() - total) / 2.0 + offset);
            int y = (getHeight() - size) / 2;
            Color on = UIManager.getColor(error ? "Servicio.dangerColor" : "Label.foreground");
            Color ring = UIManager.getColor("Label.disabledForeground");
            float stroke = UIScale.scale(1.5f);
            g2.setStroke(new BasicStroke(stroke));
            for (int i = 0; i < length; i++) {
                int x = x0 + i * (size + gap);
                if (i < filled) {
                    g2.setColor(on);
                    g2.fillOval(x, y, size, size);
                } else {
                    g2.setColor(ring);
                    g2.drawOval(x + 1, y + 1, size - 2, size - 2);
                }
            }
            g2.dispose();
        }
    }
}
