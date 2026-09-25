package com.movie.parser;

import android.util.Log;

import com.movie.data.MovieRecord;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Minimal, dependency-free .xlsx writer.
 *
 * <p>A full Apache POI port is not usable on Android (POI needs {@code java.awt}, {@code javax.xml.*}
 * and a desktop JVM), and heavyweight forks such as poi-android add several megabytes and still
 * break on newer Excel tags. The app already parses .xlsx by hand in {@link FileImporter}, so this
 * class writes the mirror image of that parser: a real OOXML package with
 * {@code t="inlineStr"} cells (no sharedStrings part needed).</p>
 *
 * <p>Column contract is identical to the reader:
 * A = ID, B = name, C = sublink, D = category, E = link, F = pageUrl.</p>
 */
public final class ExcelWorkbookWriter {

    private static final String TAG = "ExcelWorkbookWriter";

    private static final String[] HEADERS =
            {"ID", "name", "sublink", "category", "link", "pageUrl"};

    private ExcelWorkbookWriter() {
    }

    /** Serialises the whole record list into a byte[] ready to be uploaded back to WorkDrive. */
    public static byte[] writeMovies(List<MovieRecord> records) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 16);

        StringBuilder sheet = new StringBuilder(1 << 18);
        sheet.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sheet.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        sheet.append("<sheetData>");

        // Header row
        sheet.append("<row r=\"1\">");
        for (int c = 0; c < HEADERS.length; c++) {
            appendCell(sheet, columnLetter(c) + 1, HEADERS[c]);
        }
        sheet.append("</row>");

        int rowIdx = 1;
        if (records != null) {
            for (MovieRecord r : records) {
                if (r == null) continue;
                rowIdx++;
                sheet.append("<row r=\"").append(rowIdx).append("\">");
                appendCell(sheet, "A" + rowIdx, String.valueOf(rowIdx - 1));
                appendCell(sheet, "B" + rowIdx, r.getName());
                appendCell(sheet, "C" + rowIdx, r.getSublink());
                appendCell(sheet, "D" + rowIdx, r.getCategory());
                appendCell(sheet, "E" + rowIdx, r.getLink());
                appendCell(sheet, "F" + rowIdx, r.getPageUrl());
                sheet.append("</row>");
            }
        }

        sheet.append("</sheetData></worksheet>");

        ZipOutputStream zip = new ZipOutputStream(out);
        try {
            put(zip, "[Content_Types].xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                            + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                            + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                            + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                            + "</Types>");

            put(zip, "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                            + "</Relationships>");

            put(zip, "xl/workbook.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                            + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                            + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                            + "<sheets><sheet name=\"movies\" sheetId=\"1\" r:id=\"rId1\"/></sheets>"
                            + "</workbook>");

            put(zip, "xl/_rels/workbook.xml.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                            + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                            + "</Relationships>");

            put(zip, "xl/styles.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                            + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                            + "<fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
                            + "<fills count=\"1\"><fill><patternFill patternType=\"none\"/></fill></fills>"
                            + "<borders count=\"1\"><border/></borders>"
                            + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                            + "<cellXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/></cellXfs>"
                            + "</styleSheet>");

            put(zip, "xl/worksheets/sheet1.xml", sheet.toString());
        } finally {
            zip.close();
        }

        Log.i(TAG, "Built workbook with " + Math.max(0, rowIdx - 1) + " data rows ("
                + out.size() + " bytes)");
        return out.toByteArray();
    }

    private static void appendCell(StringBuilder sb, String ref, String value) {
        String v = value == null ? "" : value;
        sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                .append(escape(v))
                .append("</t></is></c>");
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes("UTF-8"));
        zip.closeEntry();
    }

    private static String columnLetter(int zeroBasedIndex) {
        int i = zeroBasedIndex;
        StringBuilder sb = new StringBuilder();
        while (i >= 0) {
            sb.insert(0, (char) ('A' + (i % 26)));
            i = i / 26 - 1;
        }
        return sb.toString();
    }

    private static String escape(String raw) {
        StringBuilder sb = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&apos;");
                    break;
                default:
                    // Strip control characters Excel refuses to open.
                    if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                        continue;
                    }
                    sb.append(c);
                    break;
            }
        }
        return sb.toString();
    }
}
