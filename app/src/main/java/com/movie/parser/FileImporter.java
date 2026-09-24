package com.movie.parser;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import com.movie.data.MovieRecord;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Java port of FileImporter.kt
 *
 * Kotlin `object FileImporter` -> Java final class with static methods + private ctor.
 * Uses org.xmlpull.v1 (bundled in the Android runtime) exactly like the Kotlin original.
 */
public final class FileImporter {

    private static final String TAG = "FileImporter";

    private FileImporter() {
    }

    public static List<MovieRecord> importUri(Context context, Uri uri) {
        String fileName = getFileName(context, uri).toLowerCase(Locale.ROOT);

        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                return new ArrayList<>();
            }
            try {
                if (fileName.endsWith(".csv") || fileName.endsWith(".txt")) {
                    return parseCsv(inputStream);
                } else if (fileName.endsWith(".xlsx")) {
                    return parseXlsx(inputStream);
                } else {
                    // Try parsing as CSV as fallback
                    return parseCsv(inputStream);
                }
            } finally {
                try {
                    inputStream.close();
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error importing file: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    private static String getFileName(Context context, Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        result = cursor.getString(index);
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            }
        }
        return result == null ? "file.xlsx" : result;
    }

    public static List<MovieRecord> parseCsv(InputStream inputStream) {
        List<MovieRecord> records = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
            String line = reader.readLine();
            if (line == null) return new ArrayList<>();

            List<String> headers = parseCsvLine(line);

            int nameIdx = 1;
            int sublinkIdx = 2;
            int categoryIdx = 3;
            int linkIdx = 4;
            int pageUrlIdx = 5;

            boolean matchedAny = false;
            for (int i = 0; i < headers.size(); i++) {
                String h = headers.get(i).trim().toLowerCase(Locale.ROOT);
                if (h.contains("name")) {
                    nameIdx = i;
                    matchedAny = true;
                } else if (h.contains("sublink")) {
                    sublinkIdx = i;
                    matchedAny = true;
                } else if (h.contains("category")) {
                    categoryIdx = i;
                    matchedAny = true;
                } else if ((h.contains("link") || h.contains("url")) && !h.contains("page")) {
                    linkIdx = i;
                    matchedAny = true;
                } else if (h.contains("page") || h.equals("pageurl")) {
                    pageUrlIdx = i;
                    matchedAny = true;
                }
            }

            // If the first row was a header row, we skip it.
            // Otherwise, if no header matches, we treat the first row as data.
            if (matchedAny) {
                String nextLine = reader.readLine();
                if (nextLine != null) {
                    line = nextLine;
                } else {
                    return new ArrayList<>();
                }
            }

            String current = line;
            while (true) {
                List<String> row = parseCsvLine(current);
                boolean allEmpty = true;
                for (String cell : row) {
                    if (!cell.isEmpty()) {
                        allEmpty = false;
                        break;
                    }
                }
                if (!(row.isEmpty() || allEmpty)) {
                    String name = getOrNull(row, nameIdx);
                    if (!name.isEmpty()) {
                        String sublink = getOrNull(row, sublinkIdx);
                        String category = getOrNull(row, categoryIdx);
                        String link = getOrNull(row, linkIdx);
                        String pageUrl = getOrNull(row, pageUrlIdx);

                        records.add(new MovieRecord(
                                0,
                                name,
                                sublink,
                                category,
                                link,
                                pageUrl
                        ));
                    }
                }

                String next = reader.readLine();
                if (next == null) break;
                current = next;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing CSV: " + e.getMessage(), e);
        }
        return records;
    }

    private static String getOrNull(List<String> row, int index) {
        if (index < 0 || index >= row.size()) return "";
        String v = row.get(index);
        return v == null ? "" : v.trim();
    }

    private static List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
            i++;
        }
        result.add(current.toString());

        List<String> cleaned = new ArrayList<>(result.size());
        for (String s : result) {
            String t = s.trim();
            if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
                t = t.substring(1, t.length() - 1);
            }
            cleaned.add(t);
        }
        return cleaned;
    }

    public static List<MovieRecord> parseXlsx(InputStream inputStream) {
        List<String> sharedStrings = new ArrayList<>();
        byte[] sheetBytes = null;
        byte[] sharedStringsBytes = null;

        try {
            ZipInputStream zipInputStream = new ZipInputStream(inputStream);
            ZipEntry zipEntry = zipInputStream.getNextEntry();
            while (zipEntry != null) {
                String name = zipEntry.getName();
                if ("xl/sharedStrings.xml".equals(name)) {
                    sharedStringsBytes = readAllBytes(zipInputStream);
                } else if ("xl/worksheets/sheet1.xml".equals(name)) {
                    sheetBytes = readAllBytes(zipInputStream);
                }
                zipInputStream.closeEntry();
                zipEntry = zipInputStream.getNextEntry();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading xlsx zip container: " + e.getMessage(), e);
            return new ArrayList<>();
        }

        if (sharedStringsBytes != null) {
            sharedStrings = parseSharedStrings(sharedStringsBytes);
        }

        if (sheetBytes != null) {
            return parseSheet(sheetBytes, sharedStrings);
        }

        return new ArrayList<>();
    }

    private static byte[] readAllBytes(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int nRead;
        while ((nRead = in.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }

    private static List<String> parseSharedStrings(byte[] bytes) {
        List<String> strings = new ArrayList<>();
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new java.io.ByteArrayInputStream(bytes), "UTF-8");
            int eventType = parser.getEventType();
            StringBuilder currentText = new StringBuilder();
            boolean insideT = false;

            while (eventType != XmlPullParser.END_DOCUMENT) {
                String name = parser.getName();
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        if ("t".equals(name)) {
                            insideT = true;
                            currentText.setLength(0);
                        }
                        break;
                    case XmlPullParser.TEXT:
                        if (insideT) {
                            currentText.append(parser.getText());
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        if ("t".equals(name)) {
                            strings.add(currentText.toString());
                            insideT = false;
                        }
                        break;
                    default:
                        break;
                }
                eventType = parser.next();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing shared strings: " + e.getMessage(), e);
        }
        return strings;
    }

    private static List<MovieRecord> parseSheet(byte[] bytes, List<String> sharedStrings) {
        List<MovieRecord> records = new ArrayList<>();
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new java.io.ByteArrayInputStream(bytes), "UTF-8");
            int eventType = parser.getEventType();

            String currentCellRef = null;
            String currentCellType = null;
            boolean insideValOrStr = false;
            StringBuilder currentText = new StringBuilder();

            final Map<String, String> rowData = new HashMap<>();

            // Default mappings based on letters
            Map<String, String> colMap = new HashMap<>();
            colMap.put("name", "B");
            colMap.put("sublink", "C");
            colMap.put("category", "D");
            colMap.put("link", "E");
            colMap.put("page", "F");
            boolean isFirstRow = true;

            while (eventType != XmlPullParser.END_DOCUMENT) {
                String name = parser.getName();
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        if ("row".equals(name)) {
                            rowData.clear();
                        } else if ("c".equals(name)) {
                            currentCellRef = parser.getAttributeValue(null, "r");
                            currentCellType = parser.getAttributeValue(null, "t");
                        } else if ("v".equals(name) || "t".equals(name)) {
                            insideValOrStr = true;
                            currentText.setLength(0);
                        }
                        break;
                    case XmlPullParser.TEXT:
                        if (insideValOrStr) {
                            currentText.append(parser.getText());
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        if ("v".equals(name) || "t".equals(name)) {
                            insideValOrStr = false;
                        } else if ("c".equals(name)) {
                            String ref = currentCellRef;
                            if (ref != null) {
                                String colLetter = takeWhileLetters(ref).toUpperCase(Locale.ROOT);
                                String rawText = currentText.toString();

                                String finalValue;
                                if ("s".equals(currentCellType)) {
                                    Integer idx = toIntOrNull(rawText);
                                    if (idx != null && idx >= 0 && idx < sharedStrings.size()) {
                                        finalValue = sharedStrings.get(idx);
                                    } else {
                                        finalValue = rawText;
                                    }
                                } else {
                                    finalValue = rawText;
                                }
                                rowData.put(colLetter, finalValue);
                            }
                            currentCellRef = null;
                            currentCellType = null;
                        } else if ("row".equals(name)) {
                            if (isFirstRow) {
                                boolean headerMatched = false;
                                for (Map.Entry<String, String> e : rowData.entrySet()) {
                                    String col = e.getKey();
                                    String value = e.getValue() == null ? "" : e.getValue();
                                    String v = value.trim().toLowerCase(Locale.ROOT);
                                    if (v.contains("name")) {
                                        colMap.put("name", col);
                                        headerMatched = true;
                                    } else if (v.contains("sublink")) {
                                        colMap.put("sublink", col);
                                        headerMatched = true;
                                    } else if (v.contains("category")) {
                                        colMap.put("category", col);
                                        headerMatched = true;
                                    } else if ((v.contains("link") || v.contains("url")) && !v.contains("page")) {
                                        colMap.put("link", col);
                                        headerMatched = true;
                                    } else if (v.contains("page") || v.equals("pageurl")) {
                                        colMap.put("page", col);
                                        headerMatched = true;
                                    }
                                }
                                isFirstRow = false;
                                // If headers were matched, skip this row for records.
                                if (headerMatched) {
                                    eventType = parser.next();
                                    continue;
                                }
                            }

                            String movieName = trimOrEmpty(rowData.get(valueOr(colMap.get("name"), "B")));
                            if (!movieName.isEmpty()) {
                                String sublink = trimOrEmpty(rowData.get(valueOr(colMap.get("sublink"), "C")));
                                String category = trimOrEmpty(rowData.get(valueOr(colMap.get("category"), "D")));
                                String link = trimOrEmpty(rowData.get(valueOr(colMap.get("link"), "E")));
                                String pageUrl = trimOrEmpty(rowData.get(valueOr(colMap.get("page"), "F")));

                                records.add(new MovieRecord(
                                        0,
                                        movieName,
                                        sublink,
                                        category,
                                        link,
                                        pageUrl
                                ));
                            }
                        }
                        break;
                    default:
                        break;
                }
                eventType = parser.next();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing sheet XML: " + e.getMessage(), e);
        }
        return records;
    }

    private static String valueOr(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private static String trimOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String takeWhileLetters(String ref) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ref.length(); i++) {
            char c = ref.charAt(i);
            if (Character.isLetter(c)) {
                sb.append(c);
            } else {
                break;
            }
        }
        return sb.toString();
    }

    private static Integer toIntOrNull(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** Unused helper kept for completeness / parity with Kotlin helpers. */
    static List<String> emptyList() {
        return Collections.emptyList();
    }
}
