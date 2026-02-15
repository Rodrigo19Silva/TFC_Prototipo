package pt.ulusofona.tfc;

public class JSONUtils {

    static String quickJSONFormater(String json) {
        StringBuilder out = new StringBuilder();
        boolean inStr = false, esc = false;
        int indent = 0;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (esc) { out.append(c); esc = false; continue; }
            if (c == '\\') { out.append(c); esc = true; continue; }
            if (c == '\"') { inStr = !inStr; out.append(c); continue; }
            if (inStr) { out.append(c); continue; }

            switch (c) {
                case '{': case '[':
                    out.append(c).append('\n').append("  ".repeat(++indent));
                    break;
                case '}': case ']':
                    out.append('\n').append("  ".repeat(--indent)).append(c);
                    break;
                case ',':
                    out.append(c).append('\n').append("  ".repeat(indent));
                    break;
                case ':':
                    out.append(": ");
                    break;
                default:
                    if (!Character.isWhitespace(c)) out.append(c);
            }
        }
        return out.toString();

    }

    // Very naive JSON string field extractor: "key": "value"
    // Does not work if multiple sub-fields have the same key
    static String getJsonString(String json, String key) {
        String pattern = "\"" + key + "\"";
        int keyPos = json.indexOf(pattern);
        if (keyPos < 0) return null;

        int colonPos = json.indexOf(':', keyPos + pattern.length());
        if (colonPos < 0) return null;

        // find opening quote of the value
        int firstQuote = json.indexOf('"', colonPos + 1);
        if (firstQuote < 0) return null;

        // find closing quote (very naive, minimal escaping handling)
        int secondQuote = json.indexOf('"', firstQuote + 1);
        while (secondQuote > 0 && json.charAt(secondQuote - 1) == '\\') {
            secondQuote = json.indexOf('"', secondQuote + 1);
        }
        if (secondQuote < 0) return null;

        return json.substring(firstQuote + 1, secondQuote);
    }

    static String escapeJsonString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    //função para descapar o texto

    static String unescapeFromLLM(String s) {
        if (s == null) return null;

        // Converte sequências escapadas em caracteres reais
        s = s.replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\r", "\r")
                .replace("\\\"", "\"");

        // No fim, converte \\ em \
        s = s.replace("\\\\", "\\");

        return s;
    }


}