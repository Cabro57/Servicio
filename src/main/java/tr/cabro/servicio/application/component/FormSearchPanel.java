package raven.modal.component;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.icons.FlatMenuArrowIcon;
import net.miginfocom.swing.MigLayout;
import raven.modal.menu.MyMenuValidation;
import raven.modal.system.Form;
import raven.modal.utils.DemoPreferences;
import raven.modal.utils.SystemForm;
import tr.cabro.servicio.application.util.Ikon;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.service.CustomerService;
import tr.cabro.servicio.service.RepairService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.searchableresult.CustomerSearchResult;
import tr.cabro.servicio.util.searchableresult.ISearchableResult;
import tr.cabro.servicio.util.searchableresult.ServiceSearchResult;
import tr.cabro.servicio.util.searchableresult.StaticFormResult;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public class FormSearchPanel extends JPanel {

    private LookAndFeel oldTheme = UIManager.getLookAndFeel();
    private final int SEARCH_MAX_LENGTH = 50;
    private final Map<SystemForm, Class<? extends Form>> formsMap;
    private final List<Item> listItems = new ArrayList<>();

    private javax.swing.Timer searchDebounceTimer;
    private SwingWorker<Void, ISearchableResult> activeSearchWorker;

    public FormSearchPanel(Map<SystemForm, Class<? extends Form>> formsMap) {
        this.formsMap = formsMap;
        init();
    }

    private void init() {
        setLayout(new MigLayout("fillx,insets 0,wrap", "[fill,500]"));
        textSearch = new JTextField();
        panelResult = new PanelResult();
        textSearch.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Ara...");
        textSearch.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new FlatSVGIcon("icons/search.svg", 0.4f));
        textSearch.putClientProperty(FlatClientProperties.STYLE, "" +
                "border:3,3,3,3;" +
                "background:null;" +
                "showClearButton:true;");
        add(textSearch, "gap 17 17 0 0");
        add(new JSeparator(), "height 2!");
        JScrollPane scrollPane = new JScrollPane(panelResult);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        scrollPane.getVerticalScrollBar().putClientProperty(FlatClientProperties.STYLE, "" +
                "trackArc:$ScrollBar.thumbArc;" +
                "thumbInsets:0,3,0,3;" +
                "trackInsets:0,3,0,3;" +
                "width:12;");
        add(scrollPane);
        installSearchField();
    }

    public final void formCheck() {
        if (oldTheme != UIManager.getLookAndFeel()) {
            oldTheme = UIManager.getLookAndFeel();
            SwingUtilities.updateComponentTreeUI(this);
        }
    }

    private void installSearchField() {
        textSearch.setDocument(new PlainDocument() {
            @Override
            public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
                if (getLength() + str.length() <= SEARCH_MAX_LENGTH) {
                    super.insertString(offs, str, a);
                }
            }
        });

        searchDebounceTimer = new javax.swing.Timer(300, e -> {
            // Kullanıcı yazmayı bıraktığında asıl aramayı başlat
            performSearch(textSearch.getText().trim().toLowerCase());
        });
        searchDebounceTimer.setRepeats(false);

        textSearch.getDocument().addDocumentListener(new DocumentListener() {
            private String text;

            @Override
            public void insertUpdate(DocumentEvent e) {
                search();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                search();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                search();
            }

            private void search() {
                searchDebounceTimer.restart();
            }
        });
        textSearch.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_UP:
                        move(true);
                        break;
                    case KeyEvent.VK_DOWN:
                        move(false);
                        break;
                    case KeyEvent.VK_ENTER:
                        showForm();
                        break;
                }
            }
        });
    }

    private void performSearch(String st) {
        // Önceki aramayı iptal et
        if (activeSearchWorker != null && !activeSearchWorker.isDone()) {
            activeSearchWorker.cancel(true);
        }

        panelResult.removeAll();
        listItems.clear(); // Artık 'Item' değil, 'ISearchableResult' tutan Item'ları tutmalı

        if (st.isEmpty()) {
            showRecentResult(); // Sonuçları göster
            updateLayout();
            return;
        }

        // 4. Asenkron arama için SwingWorker
        activeSearchWorker = new SwingWorker<Void, ISearchableResult>() {

            @Override
            protected Void doInBackground() throws Exception {
                // 1. STATİK FORMLARI ARA (Lokal, Hızlı)
                for (Map.Entry<SystemForm, Class<? extends Form>> entry : formsMap.entrySet()) {
                    if (isCancelled()) return null;
                    SystemForm s = entry.getKey();
                    if (s.name().toLowerCase().contains(st)
                        || s.description().toLowerCase().contains(st)
                        || checkTags(s.tags(), st)) {
                        if (MyMenuValidation.validation(entry.getValue())) {
                            publish(new StaticFormResult(s, entry.getValue()));
                        }
                    }
                }

                RepairService repairService = ServiceManager.getRepairService();
                List<Service> services = repairService.search(st);
                for (Service service : services) {
                    if (isCancelled()) return null;
                    publish(new ServiceSearchResult(service));
                }

                CustomerService customerService = ServiceManager.getCustomerService();
                List<Customer> customers = customerService.search(st);
                for (Customer customer : customers) {
                    if (isCancelled()) return null;
                    publish(new CustomerSearchResult(customer));
                }

                // 2. VERİTABANINI ARA (Yavaş, Asenkron)
                // ÖNEMLİ: Kendi veritabanı servislerinizi burada çağırın
                // MusteriRepository repo = new MusteriRepository();
                // List<Musteri> musteriler = repo.searchByName(st);
                // for (Musteri musteri : musteriler) {
                //     if (isCancelled()) return null;
                //     publish(new MusteriSearchResult(musteri));
                // }

                // ...Aynı şeyi Ürünler, Servisler vb. için yapabilirsiniz...

                return null;
            }

            @Override
            protected void process(List<ISearchableResult> chunks) {
                // publish() ile gönderilen sonuçlar anlık olarak buraya düşer
                for (ISearchableResult result : chunks) {
                    // Item sınıfınızın 'ISearchableResult' alacak şekilde
                    // güncellenmesi gerekiyor (Bakınız Adım 5)
                    Item item = new Item(result, false, false);
                    checkComponentOrientation(item);
                    panelResult.add(item);
                    listItems.add(item);
                }
                if (!listItems.isEmpty() && getSelectedIndex() == -1) {
                    setSelected(0);
                }
                updateLayout();
            }

            @Override
            protected void done() {
                // Arama bittiğinde
                if (listItems.isEmpty()) {
                    panelResult.add(createNoResult(st));
                }
                updateLayout();
            }
        };
        activeSearchWorker.execute(); // Aramayı başlat
    }

    private boolean checkTags(String[] tags, String st) {
        if (tags.length == 0) return false;
        return Arrays.stream(tags).anyMatch(s -> s.contains(st));
    }


    private void updateLayout() {
        Container container = SwingUtilities.getAncestorOfClass(ModalContainer.class, FormSearchPanel.this);
        if (container != null) {
            container.revalidate();
        }
    }

    private void showForm() {
        int index = getSelectedIndex();
        if (index != -1) {
            listItems.get(index).showForm();
        }
    }

    private void setSelected(int index) {
        for (int i = 0; i < listItems.size(); i++) {
            listItems.get(i).setSelected(index == i);
        }
    }

    private int getSelectedIndex() {
        for (int i = 0; i < listItems.size(); i++) {
            if (listItems.get(i).isSelected()) {
                return i;
            }
        }
        return -1;
    }

    private void move(boolean up) {
        if (listItems.isEmpty()) return;
        int index = getSelectedIndex();
        int size = listItems.size();
        if (index == -1) {
            if (up) {
                index = listItems.size() - 1;
            } else {
                index = 0;
            }
        } else {
            if (up) {
                index = (index == 0) ? size - 1 : index - 1;
            } else {
                index = (index == size - 1) ? 0 : index + 1;
            }
        }
        setSelected(index);
    }

    private void showRecentResult() {
        List<Item> recentSearch = getRecentSearch(false);
        List<Item> favoriteSearch = getRecentSearch(true);
        panelResult.removeAll();
        listItems.clear();
        if (recentSearch != null && !recentSearch.isEmpty()) {
            panelResult.add(createLabel("Son"));
            for (Item item : recentSearch) {
                checkComponentOrientation(item);
                panelResult.add(item);
                listItems.add(item);
            }
        }

        if (favoriteSearch != null && !favoriteSearch.isEmpty()) {
            panelResult.add(createLabel("Favori"));
            for (Item item : favoriteSearch) {
                checkComponentOrientation(item);
                panelResult.add(item);
                listItems.add(item);
            }
        }
        if (listItems.isEmpty()) {
            panelResult.add(new NoRecentResult());
        } else {
            setSelected(0);
        }
        updateLayout();
    }

    private JLabel createLabel(String title) {
        JLabel label = new JLabel(title);
        label.putClientProperty(FlatClientProperties.STYLE, "" +
                "font:bold +1;" +
                "border:5,15,5,15;");
        checkComponentOrientation(label);
        return label;
    }

    private List<Item> getRecentSearch(boolean favorite) {
        String[] recentSearch = DemoPreferences.getRecentSearch(favorite);
        if (recentSearch == null) {
            return null;
        }

        List<Item> list = new ArrayList<>();
        for (String s : recentSearch) {

            String[] sp = s.split(":");

            if (sp[0].equals("STATIC")) {
                Class<? extends Form> classForm = getClassForm(sp[1]);
                if (MyMenuValidation.validation(classForm)) {
                    Item item = createRecentItem(sp[1], favorite);
                    if (item != null) {
                        list.add(item);
                    }
                }
            } else if (sp[0].equals("SERVICE")) {
                Service service = ServiceManager.getRepairService().get(Integer.parseInt(sp[1])).get();
                ServiceSearchResult result = new ServiceSearchResult(service);
                Item item = new Item(result, true, favorite);

                list.add(item);
            } else if (sp[0].equals("CUSTOMER")) {
                Customer customer = ServiceManager.getCustomerService().get(Integer.parseInt(sp[1])).get();
                CustomerSearchResult result = new CustomerSearchResult(customer);
                Item item = new Item(result, true, favorite);

                list.add(item);
            }


        }
        return list;
    }

    private Class<? extends Form> getClassForm(String name) {
        for (Map.Entry<SystemForm, Class<? extends Form>> entry : formsMap.entrySet()) {
            if (entry.getKey().name().equals(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Item createRecentItem(String name, boolean favorite) {
        for (Map.Entry<SystemForm, Class<? extends Form>> entry : formsMap.entrySet()) {
            if (entry.getKey().name().equals(name)) {
                return new Item(new StaticFormResult(entry.getKey(), entry.getValue()), true, favorite);
            }
        }
        return null;
    }

    private Component createNoResult(String text) {
        JPanel panel = new JPanel(new MigLayout("insets 15 5 15 5,al center,gapx 1"));
        JLabel label = new JLabel("\"");
        JLabel labelEnd = new JLabel("\" için sonuç bulunamadı ");
        label.putClientProperty(FlatClientProperties.STYLE, "" +
                "foreground:$Label.disabledForeground;");
        labelEnd.putClientProperty(FlatClientProperties.STYLE, "" +
                "foreground:$Label.disabledForeground;");
        JLabel labelText = new JLabel(text);

        panel.add(label);
        panel.add(labelText);
        panel.add(labelEnd);
        return panel;
    }

    public void clearSearch() {
        if (!textSearch.getText().isEmpty()) {
            textSearch.setText("");
        } else {
            showRecentResult();
        }
    }

    public void searchGrabFocus() {
        textSearch.grabFocus();
    }

    private void checkComponentOrientation(Component com) {
        if (getComponentOrientation().isLeftToRight() != com.getComponentOrientation().isLeftToRight()) {
            com.applyComponentOrientation(getComponentOrientation());
        }
    }

    private JTextField textSearch;
    private JPanel panelResult;

    private static class NoRecentResult extends JPanel {

        public NoRecentResult() {
            init();
        }

        private void init() {
            setLayout(new MigLayout("insets 15 5 15 5,al center"));
            JLabel label = new JLabel("Son arama yok");
            label.putClientProperty(FlatClientProperties.STYLE, "" +
                    "foreground:$Label.disabledForeground;" +
                    "font:bold;");
            add(label);
        }
    }

    private class Item extends JButton {

        private final ISearchableResult data;
        private final boolean isRecent;
        private final boolean isFavorite;
        private Component itemSource;

        public Item(ISearchableResult resultData, boolean isRecent, boolean isFavorite) {
            this.data = resultData;
            this.isRecent = isRecent;
            this.isFavorite = isFavorite;
            init();
        }


        private void init() {
            setFocusable(false);
            setHorizontalAlignment(JButton.LEADING);
            setLayout(new MigLayout("insets 3 3 3 0,filly,gapy 2", "[]push[]"));
            putClientProperty(FlatClientProperties.STYLE, "" +
                    "background:null;" +
                    "arc:10;" +
                    "borderWidth:0;" +
                    "focusWidth:0;" +
                    "innerFocusWidth:0;" +
                    "[light]selectedBackground:lighten($Button.selectedBackground,9%)");
            JLabel labelDescription = new JLabel(data.getDescription());
            labelDescription.putClientProperty(FlatClientProperties.STYLE, "" +
                    "foreground:$Label.disabledForeground;");
            add(new JLabel(data.getDisplayName()), "cell 0 0");
            add(labelDescription, "cell 0 1");
            if (!isRecent) {
                add(new JLabel(new FlatMenuArrowIcon()), "cell 1 0,span 1 2");
            } else {
                add(createRecentOption(), "cell 1 0,span 1 2");
            }
            addActionListener(e -> {
                if (itemSource == null) {
                    clearSelected();
                    setSelected(true);
                    showForm();
                } else if (itemSource.getName().equals("remove")) {
                    removeRecent();
                } else if (itemSource.getName().equals("favorite")) {
                    addFavorite();
                }
            });
        }

        private void clearSelected() {
            for (Component com : getParent().getComponents()) {
                if (com instanceof JButton) {
                    ((JButton) com).setSelected(false);
                }
            }
        }

        protected void showForm() {
//            ModalDialog.closeModal(FormSearch.ID);
//            Drawer.setSelectedItemClass(form);
//            if (!isFavorite) {
//                DemoPreferences.addRecentSearch(data.name(), false);
//            }

            data.executeAction();
        }

        protected Component createRecentOption() {
            JPanel panel = new JPanel(new MigLayout("insets n 0 n 0,fill,gapx 2", "", "[fill]"));
            panel.setOpaque(false);
            JButton cmdRemove = createButton("remove", "clear.svg", 0.35f, "Label.foreground", 0.9f);
            if (!isFavorite) {
                JButton cmdFavorite = createButton("favorite", "favorite.svg", 0.4f, "Component.accentColor", 0.9f);
                panel.add(cmdFavorite);
            } else {
                JLabel label = new JLabel(new Ikon("icons/favorite_filled.svg", 0.4f, "Component.accentColor", 0.8f));
                label.putClientProperty(FlatClientProperties.STYLE, "" +
                        "border:3,3,3,3;");
                panel.add(label);
            }
            panel.add(new JSeparator(JSeparator.VERTICAL), "gapy 5 5");
            panel.add(cmdRemove);
            return panel;
        }

        private JButton createButton(String name, String icon, float scale, String hoverKey, float alpha) {
            Ikon svgIcon = new Ikon("icons/" + icon, scale, "Label.disabledForeground", alpha);
            JButton button = new JButton(svgIcon);
            button.setName(name);
            button.setFocusable(false);
            button.setContentAreaFilled(false);
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            button.setModel(getModel());
            button.putClientProperty(FlatClientProperties.STYLE, "" +
                    "margin:3,3,3,3;");

            button.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    svgIcon.setColorKey(hoverKey);
                    itemSource = (Component) e.getSource();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    svgIcon.setColorKey("Label.disabledForeground");
                    itemSource = null;
                }
            });
            return button;
        }

        protected void removeRecent() {
            DemoPreferences.removeRecentSearch(data.getUniqueId(), isFavorite);
            panelResult.remove(this);
            listItems.remove(this);
            if (listItems.isEmpty()) {
                panelResult.removeAll();
                panelResult.add(new NoRecentResult());
            } else {
                if (getCount(isFavorite) == 0) {
                    if (isFavorite) {
                        panelResult.remove(panelResult.getComponentCount() - 1);
                    } else {
                        panelResult.remove(0);
                    }
                }
            }
            updateLayout();
        }

        protected void addFavorite() {
            DemoPreferences.addRecentSearch(data.getUniqueId(), true);
            int[] index = getFirstFavoriteIndex();
            panelResult.remove(this);
            listItems.remove(this);
            Item item = new Item(data, isRecent, true);
            checkComponentOrientation(item);
            if (index == null) {
                panelResult.add(createLabel("Favori"));
                panelResult.add(item);
                listItems.add(item);
            } else {
                panelResult.remove(this);
                listItems.remove(this);
                panelResult.add(item, index[1] - 1);
                listItems.add(index[0] - 1, item);
            }
            if (getCount(false) == 0) {
                panelResult.remove(0);
            }
            updateLayout();
        }

        private int getCount(boolean favorite) {
            int count = 0;
            for (Item item : listItems) {
                if (item.isFavorite == favorite) {
                    count++;
                }
            }
            return count;
        }

        private int[] getFirstFavoriteIndex() {
            for (int i = 0; i < listItems.size(); i++) {
                if (listItems.get(i).isFavorite) {
                    return new int[]{i, panelResult.getComponentZOrder(listItems.get(i))};
                }
            }
            return null;
        }
    }

    private static class PanelResult extends JPanel implements Scrollable {

        public PanelResult() {
            super(new MigLayout("insets 3 10 3 10,fillx,wrap", "[fill]"));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle rectangle, int i, int i1) {
            return 50;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle rectangle, int i, int i1) {
            return 50;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
