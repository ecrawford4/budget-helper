package budgethelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class CsvExportSupport {
    private CsvExportSupport() {
    }

    public static void exportRows(List<List<String>> rows, Path outputPath) throws IOException {
        StringBuilder builder = new StringBuilder();
        for (List<String> row : rows) {
            for (int index = 0; index < row.size(); index++) {
                if (index > 0) {
                    builder.append(',');
                }
                builder.append(escape(row.get(index)));
            }
            builder.append(System.lineSeparator());
        }
        Files.writeString(outputPath, builder.toString(), StandardCharsets.UTF_8);
    }

    private static String escape(String value) {
        String normalized = value == null ? "" : value;
        return '"' + normalized.replace("\r", " ").replace("\n", " ").replace("\"", "\"\"") + '"';
    }
}
