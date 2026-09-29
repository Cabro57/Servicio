package tr.cabro.servicio.application.panels.customer;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.application.utils.Toasts;
import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.enums.CustomerType;
import tr.cabro.servicio.util.PhoneHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Müşteri detayının sol kolonundaki bilgi kartı. Kimlik şeridi iletişimi tek satıra sığdırır ve
 * dar ekranda keser; ikinci telefon, TC/vergi numarası ve vergi dairesi orada hiç yer almaz.
 * Kart bunların hepsini alt alta gösterir: soluk başlık üstte, değer altta. Boş alanlar yazılmaz.
 * Telefon, e-posta ve numaralara tıklanınca değer panoya kopyalanır (tezgahta telefonla
 * konuşurken ya da fatura keserken yazmak yerine yapıştırmak için).
 */
public class CustomerInfoPanel extends JPanel {

    private final JPanel rows = new JPanel(new MigLayout("insets 0, wrap, fillx, gap 0, hidemode 3", "[grow, fill]", ""));
    private final JLabel lblEmpty = new JLabel("İletişim bilgisi girilmemiş.");
    private final JButton btnEdit;

    public CustomerInfoPanel(Runnable onEdit) {
        setLayout(new MigLayout("insets 14 16 14 12, fillx, wrap, hidemode 3", "[grow, fill]", "[]8[]"));
        putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");

        JLabel title = new JLabel("Müşteri bilgileri", new Ikon("icons/user.svg", 0.85f, "Label.disabledForeground"), SwingConstants.LEADING);
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold; iconTextGap: 8");

        btnEdit = new JButton(new Ikon("icons/pencil.svg", 0.75f));
        btnEdit.setToolTipText("Müşteri bilgilerini düzenle");
        btnEdit.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        btnEdit.putClientProperty(FlatClientProperties.STYLE, "arc: 8; margin: 3,3,3,3");
        btnEdit.addActionListener(e -> onEdit.run());

        JPanel header = new JPanel(new MigLayout("insets 0, fillx", "[grow][]", "[center]"));
        header.setOpaque(false);
        header.add(title);
        header.add(btnEdit);
        add(header, "growx");

        rows.setOpaque(false);
        add(rows, "wmin 0");

        lblEmpty.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground; font: -1");
        add(lblEmpty);
    }

    public void setCustomer(Customer c) {
        rows.removeAll();
        if (c != null) {
            addRow("Telefon", phone(c.getPhoneNumber1()), true);
            addRow("2. telefon", phone(c.getPhoneNumber2()), true);
            addRow("E-posta", c.getEmail(), true);
            addRow("Adres", c.getAddress(), false);
            if (c.getType() == CustomerType.KURUMSAL) {
                addRow("Vergi no", c.getTaxNumber(), true);
                addRow("Vergi dairesi", c.getTaxOffice(), false);
            } else {
                addRow("TC kimlik no", c.getIdentityNo(), true);
            }
        }
        boolean any = rows.getComponentCount() > 0;
        rows.setVisible(any);
        lblEmpty.setVisible(!any);
        if (c != null && c.getCreatedAt() != null) {
            JLabel since = new JLabel("Kayıt " + c.getCreatedAt().format(DateFormats.shortDate()));
            since.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
            rows.add(since, any ? "gaptop 10" : "");
            rows.setVisible(true);
        }
        revalidate();
        repaint();
    }

    private static String phone(String raw) {
        return raw == null || raw.isBlank() ? null : PhoneHelper.formatForDisplay(raw);
    }

    /** Soluk başlık üstte, değer altta; uzun değer (adres) satıra sarılır. */
    private void addRow(String caption, String value, boolean copyable) {
        if (value == null || value.isBlank()) return;
        JLabel cap = new JLabel(caption);
        cap.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
        rows.add(cap, rows.getComponentCount() == 0 ? "" : "gaptop 8");
        rows.add(copyable ? copyValue(value.trim()) : wrappedValue(value.trim()), "wmin 0, gaptop 2");
    }

    /** Tek satırlık değer: tıklanınca panoya kopyalanır, dar kolonda kesilir, tamamı ipucunda. */
    private JLabel copyValue(String value) {
        JLabel l = new JLabel(value);
        l.setToolTipText(value + "  ·  kopyalamak için tıklayın");
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value), null);
                Toasts.show(CustomerInfoPanel.this, Toast.Type.SUCCESS, "Kopyalandı: " + value);
            }
        });
        return l;
    }

    /** Çok satırlı değer (adres): kolonun genişliğinde sarılır. */
    private JTextArea wrappedValue(String value) {
        JTextArea area = new JTextArea(value);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setBorder(BorderFactory.createEmptyBorder());
        area.putClientProperty(FlatClientProperties.STYLE, "background: null; margin: 0,0,0,0");
        // İlk yerleşimde genişlik 0; sarılmış yükseklik genişlik belli olunca yeniden hesaplansın.
        area.addComponentListener(new ComponentAdapter() {
            private int lastWidth = -1;

            @Override
            public void componentResized(ComponentEvent e) {
                if (area.getWidth() != lastWidth) {
                    lastWidth = area.getWidth();
                    CustomerInfoPanel.this.revalidate();
                }
            }
        });
        return area;
    }
}
