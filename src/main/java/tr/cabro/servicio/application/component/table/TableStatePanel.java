package tr.cabro.servicio.application.component.table;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.utils.Ikon;

import javax.swing.*;

/**
 * Bir tablonun veri dışındaki üç halini gösterir: yükleniyor, arama sonucu boş ve
 * hiç kayıt yok. Üçü de eskiden aynı resmi veriyordu — boşluğun üstünde kolon
 * başlıkları — ve kullanıcı "arama bir şey bulamadı mı, yoksa sorgu mu takıldı?"
 * sorusunu ekrandan cevaplayamıyordu.
 * <p>
 * Tablo yerine geçtiği için başlık satırı da gizlenir; boş bir ızgara iskeleti
 * göstermek yerine ne olduğunu ve sıradaki adımı söyler.
 */
public class TableStatePanel extends JPanel {

    private final JLabel iconLabel = new JLabel();
    private final JLabel titleLabel = new JLabel();
    private final JLabel descriptionLabel = new JLabel();
    private final JButton actionButton = new JButton();
    private final JProgressBar progressBar = new JProgressBar();

    public TableStatePanel() {
        this(false);
    }

    /**
     * @param compact dar alanlar (ör. POS sepeti) için kenar boşlukları küçük tutulur —
     *                varsayılan 40px boşluk 140px'lik bir alanda mesajı keser.
     */
    public TableStatePanel(boolean compact) {
        setOpaque(false);
        setLayout(new MigLayout(compact ? "insets 12, fillx, wrap" : "insets 40, fillx, wrap",
                "[center, grow]", compact ? "[]6[]4[]10[]" : "[]10[]6[]14[]"));

        titleLabel.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        descriptionLabel.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
        actionButton.putClientProperty(FlatClientProperties.STYLE,
                "background: $Component.accentColor; foreground: $Servicio.onAccentForeground; arc: 10; margin: 6,14,6,14");
        progressBar.setIndeterminate(true);

        add(iconLabel, "align center");
        add(titleLabel, "align center");
        add(descriptionLabel, "align center");
        add(actionButton, "align center");
        add(progressBar, "align center, w 180!, h 4!");
    }

    /** Veri beklenirken. Belirsiz ilerleme çubuğu, "takıldı mı?" sorusunu ekrandan cevaplar. */
    public void showLoading() {
        apply(null, "Yükleniyor…", "Kayıtlar getiriliyor.", null, null);
        progressBar.setVisible(true);
    }

    /** Filtre/arama sonucu boş — kayıt var ama bu ölçütle eşleşen yok. */
    public void showNoResults(String searchText, Runnable onClearFilter) {
        String description = searchText == null || searchText.isBlank()
                ? "Seçtiğiniz filtrelerle eşleşen kayıt yok."
                : "\"" + searchText + "\" için sonuç bulunamadı.";
        apply("icons/search.svg", "Eşleşen kayıt yok", description, "Filtreyi temizle", onClearFilter);
    }

    /** Hiç kayıt yok — ilk kaydı oluşturmaya davet. */
    public void showEmpty(String title, String description, String actionText, Runnable onAction) {
        apply("icons/plus.svg", title, description, actionText, onAction);
    }

    /** Eylemsiz bilgi durumu — kaydı oluşturma yeri başka ekranda olduğunda (ör. POS sepeti). */
    public void showMessage(String iconPath, String title, String description) {
        apply(iconPath, title, description, null, null);
    }

    private void apply(String iconPath, String title, String description, String actionText, Runnable onAction) {
        progressBar.setVisible(false);

        if (iconPath != null) {
            iconLabel.setIcon(new Ikon(iconPath, 1.8f));
            iconLabel.setVisible(true);
        } else {
            iconLabel.setVisible(false);
        }

        titleLabel.setText(title);
        descriptionLabel.setText(description);

        for (java.awt.event.ActionListener listener : actionButton.getActionListeners()) {
            actionButton.removeActionListener(listener);
        }
        if (actionText != null && onAction != null) {
            actionButton.setText(actionText);
            actionButton.addActionListener(e -> onAction.run());
            actionButton.setVisible(true);
        } else {
            actionButton.setVisible(false);
        }

        revalidate();
        repaint();
    }
}
