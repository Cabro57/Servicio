package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.*;

/**
 * Kayıt ekleme/düzenleme formlarının ortak yapı taşları.
 * <p>
 * Tüm düzenleme modalları aynı dili konuşur: solda dar bir rayda bölüm adı ve kısa açıklama,
 * sağda eşit kolonlu alan ızgarası; her alan "etiket + giriş + (gizli) hata mesajı" hücresidir.
 * Doğrulama hataları toast yerine ilgili alanın altında gösterilir ve alan yazılınca kalkar.
 * <p>
 * Kullanım:
 * <pre>{@code
 * JPanel form = FormKit.railForm(760);
 * form.add(FormKit.rail("Tanım", "Listelerde görünen bilgiler."), "top");
 * JPanel grid = FormKit.grid(2);
 * grid.add(FormKit.cell("Ad *", nameField, nameError));
 * form.add(grid);
 * form.add(FormKit.separator(), "span 2, growx, gaptop 12, gapbottom 12");
 * }</pre>
 */
public final class FormKit {

    public static final int RAIL_WIDTH = 150;

    private FormKit() {}

    /** Sol rayda bölüm adı, sağda alanlar; gizlenen bileşen yer kaplamaz (hidemode 3). */
    public static JPanel railForm(int width) {
        return new JPanel(new MigLayout(
                "wrap 2, insets 16 20 12 20, fillx, hidemode 3, width " + width + ":" + width + ":",
                "[" + RAIL_WIDTH + "!]24[grow, fill]"));
    }

    /** Formu kaydırılabilir yapar; modal küçük ekranda kesilmesin, yatay kaydırma olmasın. */
    public static JScrollPane scroll(JComponent form) {
        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    /** Bölüm başlığı ve altında soluk açıklama; açıklama dar kolonda kelime sınırından kırılır. */
    public static JPanel rail(String title, String hintText) {
        return rail(title, hint(hintText));
    }

    public static JPanel rail(String title, JTextArea hint) {
        JPanel p = new JPanel(new MigLayout("insets 2 0 0 0, gap 0, wrap", "[" + RAIL_WIDTH + "!]", "[]6[]"));
        p.setOpaque(false);
        JLabel t = new JLabel(title);
        t.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        p.add(t);
        p.add(hint, "w " + RAIL_WIDTH + "!");
        return p;
    }

    /** Raydaki çok satırlı açıklama metni. */
    public static JTextArea hint(String text) {
        JTextArea t = new JTextArea(text);
        t.setLineWrap(true);
        t.setWrapStyleWord(true);
        t.setEditable(false);
        t.setFocusable(false);
        t.setOpaque(false);
        t.setBorder(BorderFactory.createEmptyBorder());
        t.putClientProperty(FlatClientProperties.STYLE,
                "foreground: $Label.disabledForeground; font: -1; background: null; margin: 0,0,0,0");
        return t;
    }

    /** Tek satırlık soluk yardım metni (alan altı notlar, durum satırları). */
    public static JLabel note(String text) {
        JLabel l = new JLabel(text);
        l.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground; font: -1");
        return l;
    }

    /** Tıklanınca bir metne hazır ifade ekleyen hap biçimli düğme (şikayet, ekspertiz çipleri). */
    public static JButton chipButton(String text) {
        JButton chip = new JButton(text);
        chip.putClientProperty(FlatClientProperties.STYLE, "arc: 999; margin: 2,10,2,10; font: -1; focusWidth: 0");
        return chip;
    }

    /**
     * Metin alanına ifade ekler: boşsa doğrudan, doluysa virgülle; zaten geçiyorsa eklemez.
     * İmleç sona konur ki operatör devamını (örn. "Batarya sağlığı: %" sonrası sayı) yazabilsin.
     */
    public static void appendPhrase(JTextArea area, String phrase) {
        String current = area.getText().trim();
        if (!current.toLowerCase().contains(phrase.toLowerCase())) {
            area.setText(current.isEmpty() ? phrase : current + (current.endsWith(".") ? " " : ", ") + phrase);
        }
        area.requestFocusInWindow();
        area.setCaretPosition(area.getDocument().getLength());
    }

    public static JSeparator separator() {
        return new JSeparator();
    }

    /** Eşit genişlikte {@code columns} kolonlu alan ızgarası; her hücre bir {@link #cell}. */
    public static JPanel grid(int columns) {
        StringBuilder cols = new StringBuilder();
        for (int i = 0; i < columns; i++) cols.append("[grow, fill, sg col]");
        // "[top]": altında not/hata olan hücre komşusunun etiketini aşağı kaydırmasın.
        // MigLayout son satır kısıtını sonraki tüm satırlara uygular.
        JPanel p = new JPanel(new MigLayout("wrap " + columns + ", insets 0, fillx, hidemode 3, gapx 14, gapy 10",
                cols.toString(), "[top]"));
        p.setOpaque(false);
        return p;
    }

    public static JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.putClientProperty(FlatClientProperties.STYLE, "font: -1");
        return l;
    }

    public static JLabel errorLabel() {
        JLabel l = new JLabel();
        l.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Servicio.dangerColor");
        l.setVisible(false);
        return l;
    }

    public static JPanel cell(String label, JComponent input, JComponent below) {
        return cell(fieldLabel(label), input, below);
    }

    /**
     * Etiket, giriş ve altında (varsa) hata/durum satırı. Etiket-giriş arası sıkı, hücreler arası
     * {@link #grid} ile geniş. {@code below} bir hata etiketiyse ve giriş bir metin alanıysa
     * yazılmaya başlanınca hata kendiliğinden kalkar.
     */
    public static JPanel cell(JLabel label, JComponent input, JComponent below) {
        JPanel c = new JPanel(new MigLayout("insets 0, gap 0, wrap, fillx, hidemode 3", "[grow, fill]", "[]4[]3[]"));
        c.setOpaque(false);
        label.setLabelFor(input instanceof JScrollPane sp ? sp.getViewport().getView() : input);
        c.add(label);
        c.add(input);
        if (below != null) {
            c.add(below, "wmin 0");
            Component view = input instanceof JScrollPane sp ? sp.getViewport().getView() : input;
            if (below instanceof JLabel error && view instanceof JTextComponent text) clearOnType(text, error);
        }
        return c;
    }

    /** Alanı hata çerçevesine alır ve mesajı gösterir; ilk hatalı alanı bulmak için alanı döndürür. */
    public static JComponent fail(JComponent field, JLabel error, String message) {
        field.putClientProperty(FlatClientProperties.OUTLINE, FlatClientProperties.OUTLINE_ERROR);
        error.setText(message);
        error.setVisible(true);
        revalidateUp(error);
        return field;
    }

    public static void clear(JComponent field, JLabel error) {
        if (field != null) field.putClientProperty(FlatClientProperties.OUTLINE, null);
        if (error != null && error.isVisible()) {
            error.setVisible(false);
            revalidateUp(error);
        }
    }

    /** Yazılmaya başlanınca hata mesajı ve çerçeve kalkar. */
    public static void clearOnType(JTextComponent field, JLabel error) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { if (error.isVisible()) clear(field, error); }
            @Override public void removeUpdate(DocumentEvent e) { if (error.isVisible()) clear(field, error); }
            @Override public void changedUpdate(DocumentEvent e) { }
        });
    }

    /** Satır kaydırmalı, Tab'ı sonraki alana bırakan çok satırlı metin alanı. */
    public static JTextArea textArea(int rows) {
        JTextArea area = new JTextArea(rows, 20);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
        area.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);
        return area;
    }

    public static JScrollPane areaScroll(JTextArea area) {
        JScrollPane sp = new JScrollPane(area);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return sp;
    }

    /** Gizlenen/gösterilen satır sonrası modal da yeni yüksekliğe uysun diye üst zinciri geçersiz kılar. */
    public static void revalidateUp(Component c) {
        for (Container p = c.getParent(); p != null; p = p.getParent()) {
            p.revalidate();
            if (p instanceof JScrollPane) break;
        }
    }
}
