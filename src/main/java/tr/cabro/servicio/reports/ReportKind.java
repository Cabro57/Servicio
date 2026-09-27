package tr.cabro.servicio.reports;

import tr.cabro.servicio.application.component.chart.ColumnChart.Ink;
import tr.cabro.servicio.application.component.chart.ColumnChart.Series;
import tr.cabro.servicio.application.themes.BadgePalette;
import tr.cabro.servicio.database.repository.AnalyticsRepository;
import tr.cabro.servicio.database.repository.AnalyticsRepository.Breakdown;
import tr.cabro.servicio.database.repository.AnalyticsRepository.CustomerRow;
import tr.cabro.servicio.database.repository.AnalyticsRepository.DebtorRow;
import tr.cabro.servicio.database.repository.AnalyticsRepository.FinancePoint;
import tr.cabro.servicio.database.repository.AnalyticsRepository.FlowPoint;
import tr.cabro.servicio.database.repository.AnalyticsRepository.ItemRow;
import tr.cabro.servicio.database.repository.AnalyticsRepository.PaymentPoint;
import tr.cabro.servicio.database.repository.AnalyticsRepository.PaymentTotal;
import tr.cabro.servicio.database.repository.AnalyticsRepository.StockRow;
import tr.cabro.servicio.i18n.AppLocale;
import tr.cabro.servicio.model.enums.PaymentType;
import tr.cabro.servicio.reports.Report.Col;
import tr.cabro.servicio.reports.Report.Figure;
import tr.cabro.servicio.reports.Report.Kind;
import tr.cabro.servicio.reports.Report.Table;
import tr.cabro.servicio.reports.Report.Tone;
import tr.cabro.servicio.util.PhoneHelper;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Raporlar sayfasındaki dört rapor. Her biri seçilen dönem için rakamları, grafiği ve tabloları
 * {@link AnalyticsRepository} sorgularından kurar (arka planda çağrılır). Dönemden bağımsız
 * "şu an" bilgileri (stok değeri, açık alacak) açıklamasında bunu söyler.
 */
public enum ReportKind {

    FINANCE("Finans ve kâr", "Ciro, maliyet ve net kâr; servis ile satışın payı", "icons/turkish-lira.svg"),
    SERVICE("Servis performansı", "Açılan ve teslim edilen işler, süreler, cihaz ve marka kırılımı", "icons/wrench.svg"),
    SALES("Satış ve stok", "Satılan ürünler, serviste kullanılan parçalar, kritik stok", "icons/shopping-bag.svg"),
    COLLECTIONS("Tahsilat ve müşteriler", "Yönteme göre tahsilat, en çok iş yapan ve borçlu müşteriler", "icons/hand-coins.svg");

    private static final int TOP = 15;

    private final String title;
    private final String description;
    private final String iconPath;

    ReportKind(String title, String description, String iconPath) {
        this.title = title;
        this.description = description;
        this.iconPath = iconPath;
    }

    public String title() { return title; }
    public String description() { return description; }
    public String iconPath() { return iconPath; }

    public Report build(AnalyticsRepository repo, LocalDate from, LocalDate to) {
        Bucket bucket = Bucket.forRange(from, to);
        switch (this) {
            case FINANCE: return finance(repo, from, to, bucket);
            case SERVICE: return service(repo, from, to, bucket);
            case SALES: return sales(repo, from, to, bucket);
            default: return collections(repo, from, to, bucket);
        }
    }

    // ── Finans ──────────────────────────────────────────────────────────────

    private Report finance(AnalyticsRepository repo, LocalDate from, LocalDate to, Bucket bucket) {
        List<String> keys = bucket.keys(from, to);
        List<FinancePoint> points = repo.financeTrend(bucket, from, to);
        Map<String, FinancePoint> byKey = new HashMap<>();
        for (FinancePoint p : points) byKey.put(p.key(), p);

        double labor = 0, partRev = 0, partCost = 0, sale = 0, saleCost = 0, ret = 0, retCost = 0;
        for (FinancePoint p : points) {
            labor += p.labor(); partRev += p.partRevenue(); partCost += p.partCost();
            sale += p.sale(); saleCost += p.saleCost(); ret += p.ret(); retCost += p.retCost();
        }
        double service = labor + partRev;
        double salesNet = sale + ret;
        double revenue = service + salesNet;
        double cost = partCost + saleCost + retCost;
        double profit = revenue - cost;
        double margin = revenue != 0 ? profit / revenue : Double.NaN;

        List<Figure> figures = List.of(
                new Figure("Ciro", Report.money(revenue), Tone.PLAIN, "servis + satış − iade"),
                new Figure("Maliyet", Report.money(cost), Tone.PLAIN, "parça ve ürün alışı"),
                new Figure("Net kâr", Report.money(profit), sign(profit), "ciro − maliyet"),
                new Figure("Kâr marjı", Report.percent(margin), Double.isNaN(margin) ? Tone.MUTED : sign(profit), "net kâr / ciro"),
                new Figure("Servis cirosu", Report.money(service), Tone.PLAIN,
                        service > 0 ? Report.percent(labor / service) + " işçilik" : "işçilik ve parça"),
                new Figure("Satış cirosu", Report.money(salesNet), Tone.PLAIN,
                        ret != 0 ? Report.money(ret) + " iade dahil" : "POS satışları"));

        int n = keys.size();
        double[] svc = new double[n], sal = new double[n], prof = new double[n];
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            FinancePoint p = byKey.get(keys.get(i));
            if (p == null) continue;
            svc[i] = p.serviceRevenue();
            sal[i] = p.salesNet();
            prof[i] = p.profit();
            if (p.revenue() == 0 && p.cost() == 0) continue;
            rows.add(new Object[]{label(bucket, keys.get(i)), p.serviceRevenue(), p.sale(), p.ret(), p.cost(), p.profit(),
                    p.revenue() != 0 ? p.profit() / p.revenue() : null});
        }
        Report.Chart chart = new Report.Chart("Ciro ve net kâr", keys,
                List.of(new Series("Servis", svc, Ink.neutral(0.62f)), new Series("Satış", sal, Ink.neutral(0.30f))),
                new Series("Net kâr", prof, Ink.key("Servicio.successColor")), true, true);

        Table periods = new Table(capitalize(bucket.adjective()) + " döküm", "Hareketsiz " + unitName(bucket) + " gösterilmez",
                List.of(new Col("Dönem", Kind.TEXT), new Col("Servis", Kind.MONEY), new Col("Satış", Kind.MONEY),
                        new Col("İade", Kind.MONEY_NEGATIVE), new Col("Maliyet", Kind.MONEY), new Col("Net kâr", Kind.MONEY_SIGN),
                        new Col("Marj", Kind.PERCENT)),
                rows, new Object[]{"Toplam", service, sale, ret, cost, profit, revenue != 0 ? margin : null},
                "Bu dönemde gelir yok");

        List<Object[]> lines = new ArrayList<>();
        lines.add(new Object[]{"İşçilik", labor, 0.0, labor, share(labor, revenue)});
        lines.add(new Object[]{"Serviste kullanılan parça", partRev, partCost, partRev - partCost, share(partRev, revenue)});
        lines.add(new Object[]{"Ürün satışı", sale, saleCost, sale - saleCost, share(sale, revenue)});
        if (ret != 0) lines.add(new Object[]{"Satış iadesi", ret, retCost, ret - retCost, share(ret, revenue)});
        Table sources = new Table("Gelir kalemleri", "Kâr = tutar − alış maliyeti",
                List.of(new Col("Kalem", Kind.TEXT), new Col("Tutar", Kind.MONEY_NEGATIVE), new Col("Maliyet", Kind.MONEY),
                        new Col("Kâr", Kind.MONEY_SIGN), new Col("Pay", Kind.PERCENT)),
                revenue == 0 && cost == 0 ? List.of() : lines,
                new Object[]{"Toplam", revenue, cost, profit, revenue != 0 ? 1.0 : null}, "Bu dönemde gelir yok");

        return new Report(this, from, to, bucket, figures, chart, List.of(periods, sources));
    }

    // ── Servis ──────────────────────────────────────────────────────────────

    private Report service(AnalyticsRepository repo, LocalDate from, LocalDate to, Bucket bucket) {
        List<String> keys = bucket.keys(from, to);
        AnalyticsRepository.ServiceStats s = repo.serviceStats(from, to);
        Map<String, FlowPoint> byKey = new HashMap<>();
        for (FlowPoint p : repo.serviceFlow(bucket, from, to)) byKey.put(p.key(), p);

        List<Figure> figures = List.of(
                new Figure("Açılan servis", count(s.opened()), s.opened() == 0 ? Tone.MUTED : Tone.PLAIN, "kabul edilen cihaz"),
                new Figure("Teslim edilen", count(s.delivered()), s.delivered() == 0 ? Tone.MUTED : Tone.SUCCESS, "müşteriye verilen"),
                new Figure("İade", count(s.returned()), s.returned() == 0 ? Tone.MUTED : Tone.DANGER, "onarılmadan verilen"),
                new Figure("Ort. teslim süresi", Report.days(s.avgDays()), Double.isNaN(s.avgDays()) ? Tone.MUTED : Tone.PLAIN, "kabulden teslime"),
                new Figure("Ort. servis tutarı", s.avgTicket() > 0 ? Report.money(s.avgTicket()) : "—",
                        s.avgTicket() > 0 ? Tone.PLAIN : Tone.MUTED, "ücretli servisler"),
                new Figure("Şu an atölyede", count(s.openNow()), s.openNow() == 0 ? Tone.MUTED : Tone.PLAIN, "dönemden bağımsız"));

        int n = keys.size();
        double[] opened = new double[n], delivered = new double[n];
        for (int i = 0; i < n; i++) {
            FlowPoint p = byKey.get(keys.get(i));
            if (p == null) continue;
            opened[i] = p.opened();
            delivered[i] = p.delivered();
        }
        Report.Chart chart = new Report.Chart("Açılan ve teslim edilen", keys,
                List.of(new Series("Açılan", opened, Ink.neutral(0.45f)),
                        new Series("Teslim edilen", delivered, Ink.key("Servicio.successColor"))), null, false, false);

        Table types = breakdownTable("Cihaz türleri", "Tür", repo.byDeviceType(from, to));
        Table brands = breakdownTable("Markalar", "Marka", repo.byBrand(from, to));

        List<Object[]> laborRows = new ArrayList<>();
        for (ItemRow r : repo.serviceItems("LABOR", from, to, TOP)) {
            laborRows.add(new Object[]{r.name(), r.quantity(), r.revenue()});
        }
        Table labors = new Table("En çok yapılan işlemler", "İşçilik kalemleri, ilk " + TOP,
                List.of(new Col("İşçilik", Kind.TEXT), new Col("Adet", Kind.COUNT), new Col("Ciro", Kind.MONEY)),
                laborRows, null, "Bu dönemde işçilik girilmedi");

        return new Report(this, from, to, bucket, figures, chart, List.of(types, brands, labors));
    }

    private static Table breakdownTable(String title, String nameCol, List<Breakdown> data) {
        List<Object[]> rows = new ArrayList<>();
        long count = 0;
        double revenue = 0;
        for (Breakdown b : data) {
            rows.add(new Object[]{b.name(), b.count(), b.revenue(), Double.isNaN(b.avgDays()) ? null : b.avgDays()});
            count += b.count();
            revenue += b.revenue();
        }
        return new Table(title, "Süre yalnızca teslim edilenlerden",
                List.of(new Col(nameCol, Kind.TEXT), new Col("Servis", Kind.COUNT), new Col("Ciro", Kind.MONEY), new Col("Ort. süre", Kind.DAYS)),
                rows, rows.isEmpty() ? null : new Object[]{"Toplam", count, revenue, null}, "Bu dönemde servis açılmadı");
    }

    // ── Satış ve stok ───────────────────────────────────────────────────────

    private Report sales(AnalyticsRepository repo, LocalDate from, LocalDate to, Bucket bucket) {
        List<String> keys = bucket.keys(from, to);
        AnalyticsRepository.SalesStats s = repo.salesStats(from, to);
        AnalyticsRepository.StockValue stock = repo.stockValue();
        List<StockRow> low = repo.lowStock();
        Map<String, FinancePoint> byKey = new HashMap<>();
        for (FinancePoint p : repo.financeTrend(bucket, from, to)) byKey.put(p.key(), p);

        double basket = s.saleCount() > 0 ? s.saleRevenue() / s.saleCount() : 0;
        List<Figure> figures = List.of(
                new Figure("Satış fişi", count(s.saleCount()), s.saleCount() == 0 ? Tone.MUTED : Tone.PLAIN, "POS satışı"),
                new Figure("Satış cirosu", Report.money(s.saleRevenue()), Tone.PLAIN, "iadeler hariç"),
                new Figure("İade", s.returnAmount() != 0 ? Report.money(s.returnAmount()) : "—",
                        s.returnAmount() != 0 ? Tone.DANGER : Tone.MUTED, s.returnCount() + " iade fişi"),
                new Figure("Ortalama sepet", basket > 0 ? Report.money(basket) : "—", basket > 0 ? Tone.PLAIN : Tone.MUTED, "fiş başına"),
                new Figure("Stok değeri", Report.money(stock.parts() + stock.products()), Tone.PLAIN,
                        "alış fiyatıyla · şu an"),
                new Figure("Kritik stok", count(low.size()), low.isEmpty() ? Tone.MUTED : Tone.WARNING, "asgari seviyede · şu an"));

        int n = keys.size();
        double[] sale = new double[n], ret = new double[n];
        for (int i = 0; i < n; i++) {
            FinancePoint p = byKey.get(keys.get(i));
            if (p == null) continue;
            sale[i] = p.sale();
            ret[i] = p.ret();
        }
        Report.Chart chart = new Report.Chart("Satış ve iade", keys,
                List.of(new Series("Satış", sale, Ink.neutral(0.50f)), new Series("İade", ret, Ink.key("Servicio.dangerColor"))),
                null, true, true);

        List<Object[]> productRows = new ArrayList<>();
        for (ItemRow r : repo.topProducts(from, to, TOP)) {
            productRows.add(new Object[]{r.name(), r.quantity(), r.revenue(), r.revenue() - r.cost()});
        }
        Table products = new Table("En çok satan ürünler", "İadeler düşülmüş, ilk " + TOP,
                List.of(new Col("Ürün", Kind.TEXT), new Col("Adet", Kind.COUNT), new Col("Ciro", Kind.MONEY), new Col("Kâr", Kind.MONEY_SIGN)),
                productRows, null, "Bu dönemde satış yok");

        List<Object[]> partRows = new ArrayList<>();
        for (ItemRow r : repo.serviceItems("PART", from, to, TOP)) {
            partRows.add(new Object[]{r.name(), r.quantity(), r.cost(), r.revenue(), r.revenue() - r.cost()});
        }
        Table parts = new Table("Serviste kullanılan parçalar", "İlk " + TOP,
                List.of(new Col("Parça", Kind.TEXT), new Col("Adet", Kind.COUNT), new Col("Maliyet", Kind.MONEY),
                        new Col("Ciro", Kind.MONEY), new Col("Kâr", Kind.MONEY_SIGN)),
                partRows, null, "Bu dönemde parça kullanılmadı");

        List<Object[]> lowRows = new ArrayList<>();
        for (StockRow r : low) lowRows.add(new Object[]{r.name(), r.kind(), r.stock(), r.minStock()});
        Table lowTable = new Table("Kritik stok", "Stoğu asgari seviyede ya da altında · şu an",
                List.of(new Col("Ad", Kind.TEXT), new Col("Tür", Kind.TEXT), new Col("Stok", Kind.COUNT), new Col("Asgari", Kind.COUNT)),
                lowRows, null, "Asgari seviyenin altında kalem yok");

        return new Report(this, from, to, bucket, figures, chart, List.of(products, parts, lowTable));
    }

    // ── Tahsilat ve müşteriler ──────────────────────────────────────────────

    private Report collections(AnalyticsRepository repo, LocalDate from, LocalDate to, Bucket bucket) {
        List<String> keys = bucket.keys(from, to);
        List<PaymentTotal> totals = repo.paymentTotals(from, to);
        AnalyticsRepository.Receivables receivables = repo.receivables();

        Map<PaymentType, PaymentTotal> byType = new EnumMap<>(PaymentType.class);
        double collected = 0, refunds = 0;
        long refundCount = 0;
        for (PaymentTotal t : totals) {
            if (t.type() == null) {
                refunds = t.amount();
                refundCount = t.count();
            } else {
                byType.put(t.type(), t);
                collected += t.amount();
            }
        }

        List<Figure> figures = new ArrayList<>();
        figures.add(new Figure("Tahsilat", Report.money(collected), collected > 0 ? Tone.SUCCESS : Tone.MUTED, "alınan ödemeler"));
        for (PaymentType type : new PaymentType[]{PaymentType.CASH, PaymentType.CREDIT_CARD, PaymentType.TRANSFER}) {
            PaymentTotal t = byType.get(type);
            double amount = t != null ? t.amount() : 0;
            figures.add(new Figure(type.getDisplayName(), amount > 0 ? Report.money(amount) : "—",
                    amount > 0 ? Tone.PLAIN : Tone.MUTED,
                    amount > 0 ? Report.percent(amount / collected) + " · " + t.count() + " işlem" : "işlem yok"));
        }
        figures.add(new Figure("İade ödemesi", refunds != 0 ? Report.money(refunds) : "—",
                refunds != 0 ? Tone.DANGER : Tone.MUTED, refundCount + " iade"));
        figures.add(new Figure("Açık alacak", Report.money(receivables.total()),
                receivables.total() > 0 ? Tone.WARNING : Tone.MUTED, receivables.debtors() + " borçlu · şu an"));

        int n = keys.size();
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < n; i++) index.put(keys.get(i), i);
        Map<PaymentType, double[]> series = new EnumMap<>(PaymentType.class);
        for (PaymentPoint p : repo.paymentTrend(bucket, from, to)) {
            Integer i = index.get(p.key());
            if (i == null) continue;
            series.computeIfAbsent(p.type(), t -> new double[n])[i] += p.amount();
        }
        List<Series> bars = new ArrayList<>();
        for (PaymentType type : PaymentType.values()) {
            double[] values = series.get(type);
            if (values == null) continue;
            bars.add(new Series(type.getDisplayName(), values, () -> BadgePalette.foreground(type.getBadgeColor())));
        }
        if (bars.isEmpty()) bars.add(new Series("Tahsilat", new double[n], Ink.neutral(0.5f)));
        Report.Chart chart = new Report.Chart("Yönteme göre tahsilat", keys, bars, null, true, true);

        List<Object[]> methodRows = new ArrayList<>();
        for (PaymentType type : PaymentType.values()) {
            PaymentTotal t = byType.get(type);
            if (t == null) continue;
            methodRows.add(new Object[]{type.getDisplayName(), t.count(), t.amount(), share(t.amount(), collected)});
        }
        if (refunds != 0) methodRows.add(new Object[]{"İade ödemesi", refundCount, refunds, null});
        Table methods = new Table("Yönteme göre", "Pay, girişler içinde",
                List.of(new Col("Yöntem", Kind.TEXT), new Col("İşlem", Kind.COUNT), new Col("Tutar", Kind.MONEY_NEGATIVE), new Col("Pay", Kind.PERCENT)),
                methodRows, methodRows.isEmpty() ? null : new Object[]{"Net", null, collected + refunds, null}, "Bu dönemde tahsilat yok");

        List<CustomerRow> top = repo.topCustomers(from, to, TOP);
        List<Object[]> topRows = new ArrayList<>();
        long[] topIds = new long[top.size()];
        for (int i = 0; i < top.size(); i++) {
            CustomerRow c = top.get(i);
            topRows.add(new Object[]{c.name(), c.service(), c.sale(), c.total(), c.paid()});
            topIds[i] = c.id();
        }
        Table customers = new Table("En çok iş yapan müşteriler", "Perakende satışlar hariç, ilk " + TOP,
                List.of(new Col("Müşteri", Kind.TEXT), new Col("Servis", Kind.MONEY), new Col("Satış", Kind.MONEY),
                        new Col("Toplam", Kind.MONEY), new Col("Ödenen", Kind.MONEY)),
                topRows, null, "Bu dönemde kayıtlı müşteriye iş yapılmadı", topIds);

        List<DebtorRow> debtors = repo.debtors(50);
        List<Object[]> debtRows = new ArrayList<>();
        long[] debtIds = new long[debtors.size()];
        LocalDate today = LocalDate.now();
        double debtTotal = 0;
        for (int i = 0; i < debtors.size(); i++) {
            DebtorRow d = debtors.get(i);
            Long age = d.oldestOpen() != null ? ChronoUnit.DAYS.between(LocalDate.parse(d.oldestOpen()), today) : null;
            debtRows.add(new Object[]{d.name(), d.phone() != null ? PhoneHelper.formatForDisplay(d.phone()) : null,
                    d.balance(), d.oldestOpen(), age});
            debtIds[i] = d.id();
            debtTotal += d.balance();
        }
        Table debt = new Table("Borçlu müşteriler", "Bakiyeye göre · şu an",
                List.of(new Col("Müşteri", Kind.TEXT), new Col("Telefon", Kind.TEXT), new Col("Bakiye", Kind.MONEY_OWED),
                        new Col("En eski açık belge", Kind.DATE), new Col("Gün", Kind.COUNT)),
                debtRows, debtRows.isEmpty() ? null : new Object[]{"Toplam", null, debtTotal, null, null},
                "Borçlu müşteri yok", debtIds);

        return new Report(this, from, to, bucket, figures, chart, List.of(methods, customers, debt));
    }

    // ── Yardımcılar ─────────────────────────────────────────────────────────

    private static Tone sign(double v) {
        return v > 0 ? Tone.SUCCESS : v < 0 ? Tone.DANGER : Tone.PLAIN;
    }

    private static Double share(double part, double whole) {
        return whole != 0 ? part / whole : null;
    }

    private static String count(long n) {
        return Kind.COUNT.format(n);
    }

    private static String label(Bucket bucket, String key) {
        return bucket.longLabel(key, AppLocale.uiLocale());
    }

    private static String unitName(Bucket bucket) {
        switch (bucket) {
            case HOUR: return "saatler";
            case DAY: return "günler";
            case WEEK: return "haftalar";
            default: return "aylar";
        }
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(AppLocale.uiLocale()) + s.substring(1);
    }
}
