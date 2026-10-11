package tr.cabro.servicio.application.component;

import tr.cabro.servicio.util.DesktopHelper;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sürüm notunu (GitHub release açıklaması, Markdown) HTML olarak gösterir: "##"/"###" başlıklar,
 * "-"/"*" maddeler, **kalın**, `kod` ve bağlantılar. Önceden notlar düz metne indirgeniyordu;
 * alt başlıklar madde gibi görünüyor, kalın yazı ve bağlantılar kayboluyordu.
 * <p>
 * Renk ve yazı tipi her oluşturmada temadan okunur; metin HTML'e kaçışlanarak çevrilir, notun
 * içindeki ham HTML çalıştırılmaz. Yalnızca http(s) bağlantıları tıklanabilir, tarayıcıda açılır.
 */
public final class ReleaseNotesView extends JEditorPane {

    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\((https?://[^)\\s]+)\\)");
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*|__(.+?)__");
    private static final Pattern CODE = Pattern.compile("`([^`]+)`");

    public ReleaseNotesView(String markdown) {
        setEditable(false);
        setOpaque(false);
        setFocusable(false);
        setBorder(BorderFactory.createEmptyBorder());
        putClientProperty(HONOR_DISPLAY_PROPERTIES, true);
        setFont(UIManager.getFont("Label.font"));
        setForeground(UIManager.getColor("Label.foreground"));

        HTMLEditorKit kit = new HTMLEditorKit();
        setEditorKit(kit);
        // Kurallar belgenin kendi stil sayfasına yazılır; kit'in paylaşılan varsayılanı değişmez.
        HTMLDocument doc = (HTMLDocument) kit.createDefaultDocument();
        doc.getStyleSheet().addRule(css());
        try {
            kit.read(new StringReader("<html><body>" + toHtml(markdown) + "</body></html>"), doc, 0);
        } catch (Exception e) {
            // Okunamayan not boş görünür; modal yine çalışır.
        }
        setDocument(doc);
        addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && e.getURL() != null) {
                DesktopHelper.browseUrl(e.getURL().toString());
            }
        });
    }

    /**
     * HTML görünümü yüksekliği genişliğe göre hesaplar; genişlik bilinmeden en uzun satır kadar
     * yayılır. Kapsayıcının genişliği verilip yükseklik o genişlikte kırılan metinden alınır.
     */
    @Override
    public Dimension getPreferredSize() {
        // Yerleşim bir kez genişlik verdiyse o kullanılır (kapsayıcının yerleşim boşlukları dahil doğru değer);
        // ilk geçişte kapsayıcının genişliğinden tahmin edilir, setBounds sonra düzeltir.
        Container parent = getParent();
        int width = getWidth() > 0 ? getWidth()
                : parent != null ? parent.getWidth() - parent.getInsets().left - parent.getInsets().right : 0;
        // Genişlik henüz bilinmiyorsa en uzun satır kadar yayılıp modalı genişletmesin.
        if (width <= 0) return new Dimension(1, super.getPreferredSize().height);
        setSize(width, Short.MAX_VALUE);
        return new Dimension(width, super.getPreferredSize().height);
    }

    /** Genişlik değişince yükseklik yeni genişlikte yeniden hesaplansın (ilk yerleşim, pencere boyu). */
    @Override
    public void setBounds(int x, int y, int width, int height) {
        int old = getWidth();
        super.setBounds(x, y, width, height);
        if (width != old) SwingUtilities.invokeLater(this::revalidate);
    }

    // ------------------------------------------------------------------ Markdown -> HTML

    static String toHtml(String markdown) {
        StringBuilder out = new StringBuilder();
        if (markdown == null) return "";
        boolean inList = false;
        boolean first = true;
        for (String raw : markdown.split("\\r?\\n")) {
            String line = raw.strip();
            boolean item = line.startsWith("- ") || line.startsWith("* ");
            if (!item && inList) {
                out.append("</ul>");
                inList = false;
            }
            if (line.isEmpty()) continue;
            if (item) {
                if (!inList) {
                    out.append("<ul>");
                    inList = true;
                }
                out.append("<li>").append(inline(line.substring(2).strip())).append("</li>");
            } else if (line.startsWith("#")) {
                int level = 0;
                while (level < line.length() && line.charAt(level) == '#') level++;
                String tag = level <= 2 ? "h2" : "h3";
                out.append('<').append(tag).append(first ? " class=\"first\"" : "").append('>')
                        .append(inline(line.substring(level).strip()))
                        .append("</").append(tag).append('>');
            } else {
                out.append("<p>").append(inline(line)).append("</p>");
            }
            first = false;
        }
        if (inList) out.append("</ul>");
        return out.toString();
    }

    private static String inline(String text) {
        String s = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        s = replace(CODE.matcher(s), m -> "<code>" + m.group(1) + "</code>");
        s = replace(LINK.matcher(s), m -> "<a href=\"" + m.group(2) + "\">" + m.group(1) + "</a>");
        s = replace(BOLD.matcher(s), m -> "<b>" + (m.group(1) != null ? m.group(1) : m.group(2)) + "</b>");
        return s;
    }

    private static String replace(Matcher m, java.util.function.Function<Matcher, String> f) {
        StringBuilder sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(f.apply(m)));
        m.appendTail(sb);
        return sb.toString();
    }

    /** Tema renkleriyle stil: başlıklar kalın, maddeler hafif içeride, kod soluk zeminde. */
    private static String css() {
        Font base = UIManager.getFont("Label.font");
        int size = base != null ? base.getSize() : 13;
        String fg = hex(UIManager.getColor("Label.foreground"));
        String muted = hex(UIManager.getColor("Label.disabledForeground"));
        String link = hex(UIManager.getColor("Component.linkColor"));
        String codeBg = hex(UIManager.getColor("Servicio.rowHoverBackground"));
        // li sağ boşluğu: Swing'in liste görünümü madde girintisini satır genişliğinden tam düşmüyor,
        // uzun maddenin ilk satırı ~10px taşıp kesiliyordu.
        return "body { margin: 0; color: " + fg + "; }"
                + " h2 { font-size: " + (size + 1) + "pt; font-weight: bold; margin: 14px 0 6px 0; }"
                + " h3 { font-size: " + size + "pt; font-weight: bold; margin: 12px 0 4px 0; }"
                + " h2.first, h3.first { margin-top: 0; }"
                + " p { margin: 0 0 6px 0; color: " + muted + "; }"
                + " ul { margin: 0 0 2px 18px; }"
                + " li { margin: 0 12px 4px 0; }"
                + " b { font-weight: bold; }"
                + " code { font-family: monospace; background: " + codeBg + "; }"
                + " a { color: " + link + "; }";
    }

    private static String hex(Color c) {
        if (c == null) return "#808080";
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}
