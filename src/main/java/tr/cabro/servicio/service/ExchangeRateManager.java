package tr.cabro.servicio.service;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.database.repository.ExchangeRateRepository;
import tr.cabro.servicio.model.dictionary.ExchangeRate;
import tr.cabro.servicio.service.exception.ValidationException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * USD/EUR gibi dövizlerin TL karşılığını yönetir. Kur geçmişi tutulmuyor —
 * her güncelleme (manuel ya da TCMB) mevcut satırın üzerine yazıyor.
 */
public class ExchangeRateManager {

    private static final String TCMB_URL = "https://www.tcmb.gov.tr/kurlar/today.xml";
    private static final Map<String, String> TCMB_CURRENCY_NAMES = Map.of(
            "USD", "Amerikan Doları",
            "EUR", "Euro"
    );

    private final ExchangeRateRepository repository;

    public ExchangeRateManager(ExchangeRateRepository repository) {
        this.repository = repository;
    }

    public CompletableFuture<List<ExchangeRate>> getAll() {
        return CompletableFuture.supplyAsync(repository::findAll);
    }

    public CompletableFuture<Void> setManualRate(String currencyCode, BigDecimal rate) {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new ValidationException("Döviz kodu boş olamaz.");
        }
        if (rate == null || rate.signum() <= 0) {
            throw new ValidationException("Kur sıfırdan büyük olmalı.");
        }
        String code = currencyCode.toUpperCase(Locale.ROOT);
        return CompletableFuture.runAsync(() -> {
            String name = repository.findByCode(code)
                    .map(ExchangeRate::getCurrencyName)
                    .orElse(TCMB_CURRENCY_NAMES.getOrDefault(code, code));
            repository.upsert(code, name, rate, "Manuel", LocalDateTime.now());
        });
    }

    /** TCMB'nin günlük kur servisinden USD/EUR satış kurlarını çeker ve tabloya yazar. */
    public CompletableFuture<List<ExchangeRate>> refreshFromTcmb() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, BigDecimal> rates = fetchTcmbRates();
            if (rates.isEmpty()) {
                throw new ValidationException("TCMB'den kur alınamadı. Bugün için kur yayınlanmamış olabilir (hafta sonu/tatil).");
            }
            LocalDateTime now = LocalDateTime.now();
            rates.forEach((code, rate) ->
                    repository.upsert(code, TCMB_CURRENCY_NAMES.getOrDefault(code, code), rate, "TCMB", now));
            return repository.findAll();
        });
    }

    private Map<String, BigDecimal> fetchTcmbRates() {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(TCMB_URL).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Servicio/" + Servicio.getInstance().getAppVersion());
            conn.setInstanceFollowRedirects(true);

            try (InputStream in = conn.getInputStream()) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(in);

                NodeList currencyNodes = doc.getElementsByTagName("Currency");
                for (int i = 0; i < currencyNodes.getLength(); i++) {
                    Element currencyEl = (Element) currencyNodes.item(i);
                    String code = currencyEl.getAttribute("CurrencyCode");
                    if (!TCMB_CURRENCY_NAMES.containsKey(code)) continue;

                    String forexSelling = elementText(currencyEl, "ForexSelling");
                    if (forexSelling == null || forexSelling.isBlank()) continue;

                    BigDecimal rate = new BigDecimal(forexSelling.replace(',', '.'));
                    result.put(code, rate);
                }
            }
        } catch (IOException | ParserConfigurationException | org.xml.sax.SAXException e) {
            Servicio.getLogger().error("TCMB kur servisi okunamadı: " + e.getMessage(), e);
        } finally {
            if (conn != null) conn.disconnect();
        }
        return result;
    }

    private String elementText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) return null;
        return nodes.item(0).getTextContent();
    }
}
