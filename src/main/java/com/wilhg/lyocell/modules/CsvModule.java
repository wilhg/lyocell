package com.wilhg.lyocell.modules;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CsvModule implements LyocellModule {
    @Override
    public String getName() {
        return "lyocell/experimental/csv";
    }

    @Override
    public String getJsSource() {
        return """
            const Csv = globalThis.LyocellCsv;
            export const parse = (data, options) => Csv.parse(data, options);
            export default { parse };
            """;
    }

    @Override
    public void install(Context context, ModuleContext moduleContext) {
        context.getBindings("js").putMember("LyocellCsv", this);
    }

    @HostAccess.Export
    public List<Map<String, String>> parse(String data, Value options) {
        String delimiter = ",";
        if (options != null && options.hasMember("delimiter")) {
            delimiter = options.getMember("delimiter").asString();
        }

        List<Map<String, String>> result = new ArrayList<>();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.StringReader(data))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return result;

            String[] headers = headerLine.split(java.util.regex.Pattern.quote(delimiter));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(java.util.regex.Pattern.quote(delimiter));
                Map<String, String> row = new HashMap<>();
                for (int j = 0; j < headers.length && j < values.length; j++) {
                    row.put(headers[j].trim(), values[j].trim());
                }
                result.add(row);
            }
        } catch (java.io.IOException e) {
            // Ignore
        }

        return result;
    }
}

