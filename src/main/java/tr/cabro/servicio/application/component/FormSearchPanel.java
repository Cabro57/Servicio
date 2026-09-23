package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.icons.FlatMenuArrowIcon;
import net.miginfocom.swing.MigLayout;
import raven.modal.component.ModalContainer;
import tr.cabro.servicio.application.menu.MyMenuValidation;
import tr.cabro.servicio.application.system.Form;
import tr.cabro.servicio.application.utils.RecentSearchStore;
import tr.cabro.servicio.application.utils.SystemForm;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Part;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.model.dto.PageResult;
import tr.cabro.servicio.application.system.QuickAction;
import tr.cabro.servicio.service.CustomerService;
import tr.cabro.servicio.service.WorkOrderService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.searchableresult.ActionResult;
import tr.cabro.servicio.util.searchableresult.CustomerSearchResult;
import tr.cabro.servicio.util.searchableresult.PartSearchResult;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class FormSearchPanel extends JPanel {

    private LookAndFeel oldTheme = UIManager.getLookAndFeel();
    private final int SEARCH_MAX_LENGTH = 50;
    private final Map<SystemForm, Class<? extends Form>> formsMap;
    private final List<Item> listItems = new ArrayList<>();

    private javax.swing.Timer searchDebounceTimer;
    private CompletableFuture<Void> activeSearchFuture;

    public FormSearchPanel(Map<SystemForm, Class<? extends Form>> formsMap) {
        this.formsMap = formsMap;
        init();
    }

    private void init() {
        setLayout(new MigLayout("fillx,insets 0,wrap", "[fill,500]"));
        textSearch = new JTextField();
        panelResult = new PanelResult();
        textSearch.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Müşteri, servis, parça ara ya da bir işlem yaz…");
        textSearch.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new Ikon("icons/search.svg", 0.7f));
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
        add(new JSeparator(), "height 2!");
        add(createKeyHints(), "gap 17 17 4 6");
        installSearchField();
    }

    /** Paletin altındaki klavye ipuçları. */
    private JPanel createKeyHints() {
        JPanel panel = new JPanel(new MigLayout("insets 0, gapx 4", "", "[center]"));
        panel.setOpaque(false);
        String[][] hints = {{"↑ ↓", "gez"}, {"Enter", "aç / çalıştır"}, {"Esc", "kapat"}};
        for (int i = 0; i < hints.length; i++) {
            JLabel key = new JLabel(hints[i][0]);
            key.putClientProperty(FlatClientProperties.STYLE,
                    "font: -2; foreground: $Label.disabledForeground; border: 0,4,0,4,$Component.borderColor,1,6");
            JLabel text = new JLabel(hints[i][1]);
            text.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
            panel.add(key, i > 0 ? "gapleft 12" : "");
            panel.add(text);
        }
        return panel;
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
        // 1. Önceki aramayı iptal et; kuyrukta bekleyen eski geri çağrılar da seq ile elenir.
        if (activeSearchFuture != null && !activeSearchFuture.isDone()) {
            activeSearchFuture.cancel(true);
        }
        final int seq = ++searchSeq;

        panelResult.removeAll();
        listItems.clear();

        if (st.isEmpty()) {
            showRecentResult();
            return;
        }

        // 2. İşlemler ve sayfalar yerel ve anlık: DB beklemeden hemen listelenir.
        List<ISearchableResult> actionResults = new ArrayList<>();
        for (QuickAction action : QuickAction.values()) {
            if (action.matches(st)) actionResults.add(new ActionResult(action));
        }
        List<ISearchableResult> staticResults = new ArrayList<>();
        for (Map.Entry<SystemForm, Class<? extends Form>> entry : formsMap.entrySet()) {
            SystemForm s = entry.getKey();
            if (s.name().toLowerCase().contains(st) || s.description().toLowerCase().contains(st) || checkTags(s.tags(), st)) {
                if (MyMenuValidation.validation(entry.getValue())) {
                    staticResults.add(new StaticFormResult(s, entry.getValue()));
                }
            }
        }
        addGroup("İşlemler", actionResults);
        addGroup("Sayfalar", staticResults);
        if (!listItems.isEmpty()) setSelected(0);
        updateLayout();

        // 3. Paralel asenkron kayıt aramaları (her grupta ilk birkaç sonuç; palet taranabilir kalsın)
        WorkOrderService workOrderService = ServiceManager.getWorkOrderService();
        CustomerService customerService = ServiceManager.getCustomerService();
        CompletableFuture<PageResult<WorkOrder>> servicesFuture = workOrderService.searchPaged(st, 1, GROUP_LIMIT);
        CompletableFuture<List<Customer>> customersFuture = customerService.search(st);
        CompletableFuture<PageResult<Part>> partsFuture = ServiceManager.getPartService().searchPaged(st, 1, GROUP_LIMIT);

        // 4. Kayıt grupları yerel grupların altına eklenir
        activeSearchFuture = CompletableFuture.allOf(servicesFuture, customersFuture, partsFuture)
                .thenAccept(v -> {
                    List<ISearchableResult> services = new ArrayList<>();
                    servicesFuture.join().getItems().forEach(w -> services.add(new ServiceSearchResult(w)));
                    List<ISearchableResult> customers = new ArrayList<>();
                    customersFuture.join().stream().limit(GROUP_LIMIT).forEach(c -> customers.add(new CustomerSearchResult(c)));
                    List<ISearchableResult> parts = new ArrayList<>();
                    partsFuture.join().getItems().forEach(p -> parts.add(new PartSearchResult(p)));

                    SwingUtilities.invokeLater(() -> {
                        if (seq != searchSeq) return;
                        addGroup("Müşteriler", customers);
                        addGroup("Servisler", services);
                        addGroup("Parçalar", parts);
                        if (listItems.isEmpty()) {
                            panelResult.add(createNoResult(st));
                        } else if (getSelectedIndex() == -1) {
                            setSelected(0);
                        }
                        updateLayout();
                    });
                })
                .exceptionally(ex -> {
                    if (ex.getCause() instanceof java.util.concurrent.CancellationException || ex instanceof java.util.concurrent.CancellationException) {
                        return null;
                    }
                    // Gerçek bir hata: yerel gruplar kalır, altına bilgi satırı eklenir
                    Servicio.getLogger().error("Arama sırasında asenkron hata: ", ex);
                    SwingUtilities.invokeLater(() -> {
                        if (seq != searchSeq) return;
                        JLabel error = new JLabel("Kayıtlar aranamadı. Tekrar deneyin.");
                        error.putClientProperty(FlatClientProperties.STYLE,
                                "foreground:$Servicio.dangerColor;border:10,12,10,12;");
                        panelResult.add(error);
                        updateLayout();
                    });
                    return null;
                });
    }

    /** Her aramada artar; eski aramanın geç gelen sonuçlarını ayıklamak için. */
    private int searchSeq;

    private static final int GROUP_LIMIT = 6;

    /** Başlıklı bir sonuç grubu ekler; boş grup hiç görünmez. */
    private void addGroup(String title, List<ISearchableResult> results) {
        if (results.isEmpty()) return;
        panelResult.add(createLabel(title));
        for (ISearchableResult result : results) {
            Item item = new Item(result, false, false);
            checkComponentOrientation(item);
            panelResult.add(item);
            listItems.add(item);
        }
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
        final int seq = ++searchSeq;
        panelResult.removeAll();
        listItems.clear();
        List<ISearchableResult> actions = new ArrayList<>();
        for (QuickAction action : QuickAction.values()) actions.add(new ActionResult(action));
        addGroup("İşlemler", actions);
        setSelected(0);
        updateLayout();

        // Son ve favori kayıtlar DB'den çözülür; hepsi gelince tek seferde EDT'de eklenir.
        CompletableFuture<List<ISearchableResult>> recentF = resolveRecent(false);
        CompletableFuture<List<ISearchableResult>> favoriteF = resolveRecent(true);
        CompletableFuture.allOf(recentF, favoriteF).thenRun(() -> SwingUtilities.invokeLater(() -> {
            if (seq != searchSeq) return;
            addRecentGroup("Son", recentF.join(), false);
            addRecentGroup("Favori", favoriteF.join(), true);
            updateLayout();
        })).exceptionally(ex -> {
            Servicio.getLogger().error("Son aramalar yüklenemedi", ex);
            return null;
        });
    }

    private void addRecentGroup(String title, List<ISearchableResult> results, boolean favorite) {
        if (results.isEmpty()) return;
        panelResult.add(createLabel(title));
        for (ISearchableResult result : results) {
            Item item = new Item(result, true, favorite);
            checkComponentOrientation(item);
            panelResult.add(item);
            listItems.add(item);
        }
    }

    private JLabel createLabel(String title) {
        JLabel label = new JLabel(title);
        label.putClientProperty(FlatClientProperties.STYLE, "" +
                "font:bold -1;" +
                "foreground:$Label.disabledForeground;" +
                "border:10,12,4,12;");
        checkComponentOrientation(label);
        return label;
    }

    /** Kayıtlı son/favori kimlikleri, sırası korunarak sonuç nesnelerine çözer (silinmiş kayıtlar atlanır). */
    private CompletableFuture<List<ISearchableResult>> resolveRecent(boolean favorite) {
        List<CompletableFuture<Optional<ISearchableResult>>> futures = new ArrayList<>();
        for (String s : RecentSearchStore.get(favorite)) {
            String[] sp = s.split(":");
            if (sp.length < 2) continue;
            try {
                if (sp[0].equals("STATIC")) {
                    Class<? extends Form> classForm = getClassForm(sp[1]);
                    ISearchableResult result = null;
                    if (classForm != null && MyMenuValidation.validation(classForm)) {
                        for (Map.Entry<SystemForm, Class<? extends Form>> entry : formsMap.entrySet()) {
                            if (entry.getKey().name().equals(sp[1])) result = new StaticFormResult(entry.getKey(), entry.getValue());
                        }
                    }
                    futures.add(CompletableFuture.completedFuture(Optional.ofNullable(result)));
                } else if (sp[0].equals("SERVICE")) {
                    futures.add(ServiceManager.getWorkOrderService().get(Long.parseLong(sp[1]))
                            .thenApply(o -> o.map(ServiceSearchResult::new)));
                } else if (sp[0].equals("CUSTOMER")) {
                    futures.add(ServiceManager.getCustomerService().get(Long.parseLong(sp[1]))
                            .thenApply(o -> o.map(CustomerSearchResult::new)));
                }
            } catch (NumberFormatException ignored) {
                // Bozuk kayıt: atla
            }
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply(v -> {
            List<ISearchableResult> list = new ArrayList<>();
            for (CompletableFuture<Optional<ISearchableResult>> f : futures) f.join().ifPresent(list::add);
            return list;
        });
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
            setLayout(new MigLayout("insets 3 6 3 0,filly,gapy 2", "[]10[]push[]"));
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
            if (data.getIconPath() != null) {
                add(new JLabel(new Ikon(data.getIconPath(), 18, "Label.disabledForeground")), "cell 0 0,span 1 2,aligny center");
            }
            JLabel labelName = new JLabel(data.getDisplayName());
            labelName.putClientProperty(FlatClientProperties.STYLE, "font:bold;");
            add(labelName, "cell 1 0");
            add(labelDescription, "cell 1 1");
            if (isRecent) {
                add(createRecentOption(), "cell 2 0,span 1 2");
            } else if (data.getShortcutText() != null) {
                JLabel key = new JLabel(data.getShortcutText());
                key.putClientProperty(FlatClientProperties.STYLE,
                        "font: -2; foreground: $Label.disabledForeground; border: 1,5,1,5,$Component.borderColor,1,6");
                add(key, "cell 2 0,span 1 2,aligny center,gapright 8");
            } else {
                add(new JLabel(new FlatMenuArrowIcon()), "cell 2 0,span 1 2");
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
            // Modal kapatma, menü seçimi ve "son aramalar"a ekleme artık sonucun kendi
            // executeAction() gerçeklemesinde yapılıyor (bkz. util/searchableresult/).
            data.executeAction();
        }

        protected Component createRecentOption() {
            JPanel panel = new JPanel(new MigLayout("insets n 0 n 0,fill,gapx 2", "", "[fill]"));
            panel.setOpaque(false);
            JButton cmdRemove = createButton("remove", "x.svg", 0.35f, "Label.foreground", 0.9f);
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

        // Son/favori listesi değişince grup başlıkları (İşlemler/Son/Favori) tutarlı kalsın diye
        // liste yerinde yamanmaz, kayıt güncellenip baştan kurulur.
        protected void removeRecent() {
            RecentSearchStore.remove(data.getUniqueId(), isFavorite);
            showRecentResult();
        }

        protected void addFavorite() {
            RecentSearchStore.add(data.getUniqueId(), true);
            showRecentResult();
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
