package tr.cabro.servicio.application.component;

import tr.cabro.servicio.model.Customer;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

public class AsyncCustomerComboBox extends EmbeddedComboBox<Customer> {

    private final DefaultComboBoxModel<Customer> listModel;
    private final JTextComponent editorComponent;
    private Timer debounceTimer;
    private boolean isFetching = false;

    private Consumer<String> searchAction;

    public AsyncCustomerComboBox() {
        super();
        this.listModel = new DefaultComboBoxModel<>();
        setModel(listModel);
        setEditable(true);
        setRenderer(new CustomerCellRenderer()); // Özel hücre tasarımı (İsim + Telefon)

        this.editorComponent = (JTextComponent) getEditor().getEditorComponent();
        initEvents();
    }

    private void initEvents() {
        // Debounce: Kullanıcı yazmayı bıraktıktan 300ms sonra arama tetiklenir
        debounceTimer = new Timer(300, e -> {
            String query = editorComponent.getText().trim();
            // Arama metni girilmişse ve listModel'deki bir nesne seçilmemişse arama yap
            if (query.length() >= 2 && searchAction != null) {
                searchAction.accept(query);
            }
        });
        debounceTimer.setRepeats(false);

        editorComponent.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { triggerSearch(); }
            @Override public void removeUpdate(DocumentEvent e) { triggerSearch(); }
            @Override public void changedUpdate(DocumentEvent e) { triggerSearch(); }

            private void triggerSearch() {
                if (!isFetching) {
                    if (debounceTimer.isRunning()) debounceTimer.restart();
                    else debounceTimer.start();
                }
            }
        });
    }

    public void setOnSearch(Consumer<String> searchAction) {
        this.searchAction = searchAction;
    }

    // Veritabanından veri geldiğinde modeli günceller
    public void updateResults(List<Customer> results) {
        SwingUtilities.invokeLater(() -> {
            isFetching = true; // Dinleyicileri geçici durdur
            String currentText = editorComponent.getText();

            listModel.removeAllElements();
            if (results != null) {
                for (Customer c : results) {
                    listModel.addElement(c);
                }
            }

            editorComponent.setText(currentText);

            if (listModel.getSize() > 0) {
                setPopupVisible(true);
            } else {
                setPopupVisible(false);
            }

            isFetching = false;
        });
    }

    public Customer getSelectedCustomer() {
        Object selected = getSelectedItem();
        if (selected instanceof Customer) {
            return (Customer) selected;
        }
        return null; // Sadece text girilmişse null döner
    }
}