package tr.cabro.servicio.util;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Şablon metinlerindeki {alan_adi} yer tutucularını verilen değerlerle değiştirir. */
public final class TemplateEngine {

    private static final Pattern TOKEN = Pattern.compile("\\{([a-z0-9_]+)\\}");

    private TemplateEngine() {
    }

    public static String render(String template, Map<String, String> tokens) {
        if (template == null) return "";
        Matcher matcher = TOKEN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String value = tokens.getOrDefault(matcher.group(1), matcher.group(0));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
