package budgethelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PdfReportExporter {
    private static final int MAX_CHARS_PER_LINE = 90;
    private static final int MAX_LINES_PER_PAGE = 48;

    private PdfReportExporter() {
    }

    public static void export(List<String> sourceLines, Path outputPath) throws IOException {
        List<String> preparedLines = new ArrayList<>();
        for (String line : sourceLines) {
            preparedLines.addAll(UiSupport.wrapText(sanitize(line), MAX_CHARS_PER_LINE));
        }
        if (preparedLines.isEmpty()) {
            preparedLines.add("");
        }

        List<List<String>> pages = paginate(preparedLines, MAX_LINES_PER_PAGE);
        int pageCount = pages.size();
        int fontObjectNumber = 3 + (pageCount * 2);
        int objectCount = fontObjectNumber;
        int[] offsets = new int[objectCount + 1];

        StringBuilder pdf = new StringBuilder();
        pdf.append("%PDF-1.4\n");

        offsets[1] = pdf.length();
        pdf.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

        offsets[2] = pdf.length();
        pdf.append("2 0 obj\n<< /Type /Pages /Count ").append(pageCount).append(" /Kids [");
        for (int index = 0; index < pageCount; index++) {
            pdf.append(3 + (index * 2)).append(" 0 R ");
        }
        pdf.append("] >>\nendobj\n");

        for (int index = 0; index < pageCount; index++) {
            int pageObjectNumber = 3 + (index * 2);
            int contentObjectNumber = pageObjectNumber + 1;
            offsets[pageObjectNumber] = pdf.length();
            pdf.append(pageObjectNumber).append(" 0 obj\n")
                    .append("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] ")
                    .append("/Resources << /Font << /F1 ").append(fontObjectNumber).append(" 0 R >> >> ")
                    .append("/Contents ").append(contentObjectNumber).append(" 0 R >>\nendobj\n");
            String stream = buildPageStream(pages.get(index));
            offsets[contentObjectNumber] = pdf.length();
            pdf.append(contentObjectNumber).append(" 0 obj\n<< /Length ").append(stream.length())
                    .append(" >>\nstream\n").append(stream).append("endstream\nendobj\n");
        }

        offsets[fontObjectNumber] = pdf.length();
        pdf.append(fontObjectNumber).append(" 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n");

        int xrefOffset = pdf.length();
        pdf.append("xref\n0 ").append(objectCount + 1).append("\n0000000000 65535 f \n");
        for (int objectNumber = 1; objectNumber <= objectCount; objectNumber++) {
            pdf.append(String.format("%010d 00000 n \n", offsets[objectNumber]));
        }
        pdf.append("trailer\n<< /Size ").append(objectCount + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xrefOffset).append("\n%%EOF\n");
        Files.writeString(outputPath, pdf.toString(), StandardCharsets.ISO_8859_1);
    }

    private static List<List<String>> paginate(List<String> lines, int pageSize) {
        List<List<String>> pages = new ArrayList<>();
        for (int start = 0; start < lines.size(); start += pageSize) {
            int end = Math.min(lines.size(), start + pageSize);
            pages.add(new ArrayList<>(lines.subList(start, end)));
        }
        return pages;
    }

    private static String buildPageStream(List<String> lines) {
        StringBuilder stream = new StringBuilder();
        stream.append("BT\n/F1 11 Tf\n50 760 Td\n14 TL\n");
        for (String line : lines) {
            stream.append('(').append(escapePdfText(line)).append(") Tj\nT*\n");
        }
        stream.append("ET\n");
        return stream.toString();
    }

    private static String escapePdfText(String line) {
        return line.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static String sanitize(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (char character : value.toCharArray()) {
            builder.append(character <= 127 ? character : '?');
        }
        return builder.toString();
    }
}
