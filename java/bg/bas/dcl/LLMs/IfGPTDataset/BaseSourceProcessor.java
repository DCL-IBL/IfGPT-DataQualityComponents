package bg.bas.dcl.LLMs.IfGPTDataset;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import bg.bas.dcl.general.JSONProcessor;

import java.io.File;

/**
 * Abstract base for all source processors.
 *
 * Provides shared utilities:
 *  - convertJsonToCSV: write a metadata JSONObject to a CSV file
 *  - estimateTokenCount: simple punctuation-aware token estimator
 *
 * Each concrete subclass implements {@link SourceProcessor#process(String, String)}
 * with source-specific parsing logic.
 */
public abstract class BaseSourceProcessor implements SourceProcessor {

    // -----------------------------------------------------------------------
    // CSV export
    // -----------------------------------------------------------------------

    /**
     * Reads a metadata.json file from disk and writes a CSV alongside it.
     *
     * @param metadataJsonPath path to the metadata JSON file
     */
    public void convertJsonToCSV(String metadataJsonPath) {
        try {
            JSONProcessor pr = new JSONProcessor();
            JSONObject json = pr.readJSON(new File(metadataJsonPath));
            convertJsonToCSV(json, metadataJsonPath + "_CSV.csv");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Writes the "metadata" array inside {@code json} to a CSV at {@code outCsvPath}.
     * Reports structural inconsistencies (missing/extra fields) to stderr.
     *
     * @param json       JSONObject that contains a "metadata" JSONArray
     * @param outCsvPath destination CSV file path
     */
    public void convertJsonToCSV(JSONObject json, String outCsvPath) {
        try {
            JSONArray array = (JSONArray) json.get("metadata");

            if (array == null || array.isEmpty()) {
                System.err.println("[INCONSISTENCY] 'metadata' array is null or empty in: " + outCsvPath);
                return;
            }

            // Collect all unique field names, preserving insertion order
            LinkedHashSet<String> headersSet = new LinkedHashSet<>();
            for (Object obj : array) {
                if (obj instanceof JSONObject) {
                    headersSet.addAll(((JSONObject) obj).keySet());
                } else {
                    System.err.println("[INCONSISTENCY] Non-JSONObject entry found in metadata array.");
                }
            }

            ArrayList<String> headers = new ArrayList<>(headersSet);

            try (PrintWriter writer = new PrintWriter(new FileWriter(outCsvPath))) {

                // Header row
                writer.println(String.join(",", headers));

                // Data rows
                for (int i = 0; i < array.size(); i++) {
                    Object obj = array.get(i);

                    if (!(obj instanceof JSONObject)) {
                        System.err.println("[INCONSISTENCY] Row " + i + " is not a JSONObject, skipping.");
                        continue;
                    }

                    JSONObject row = (JSONObject) obj;

                    // Structural checks
                    for (String header : headers) {
                        if (!row.containsKey(header)) {
                            System.err.println("[INCONSISTENCY] Row " + i + " missing field: '" + header + "'");
                        }
                    }
                    for (Object key : row.keySet()) {
                        if (!headersSet.contains(key.toString())) {
                            System.err.println("[INCONSISTENCY] Row " + i + " has unexpected field: '" + key + "'");
                        }
                    }

                    // Build CSV line with RFC-4180 escaping
                    ArrayList<String> values = new ArrayList<>();
                    for (String header : headers) {
                        Object value = row.get(header);
                        if (value == null) {
                            values.add("");
                        } else {
                            String strVal = value.toString();
                            if (strVal.contains(",") || strVal.contains("\"") || strVal.contains("\n")) {
                                strVal = "\"" + strVal.replace("\"", "\"\"") + "\"";
                            }
                            values.add(strVal);
                        }
                    }
                    writer.println(String.join(",", values));
                }
            }

            System.out.println("CSV written to: " + outCsvPath);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // -----------------------------------------------------------------------
    // Shared helpers
    // -----------------------------------------------------------------------

    /**
     * Estimates the number of tokens in a sentence by counting words plus
     * standalone punctuation characters (.,;:?!()-).
     *
     * @param sentence whitespace-tokenised sentence string
     * @return estimated token count
     */
    protected int estimateTokenCount(String sentence) {
        String[] words = sentence.split(" ");
        int punctCount = sentence.length()
                - sentence.replaceAll("[.,;:()?!\\-]", "").length();
        return words.length + punctCount;
    }

    /**
     * Creates a JSONObject pre-populated with the metadata fields that are
     * common to every source (counts start at 0).
     *
     * @param identifier unique document identifier
     * @return partially initialised JSONObject
     */
    @SuppressWarnings("unchecked")
    protected JSONObject newBaseDescriptor(String identifier) {
        JSONObject fdescr = new JSONObject();
        fdescr.put("Identifier",                  identifier);
        fdescr.put("Licence",                     "");
        fdescr.put("LicenceLink",                 "");
        fdescr.put("PublicationDate",             "");
        fdescr.put("DocumentTitle",               "");
        fdescr.put("Source",                      "");
        fdescr.put("Author",                      "");
        fdescr.put("Style",                       "");
        fdescr.put("Type",                        "");
        fdescr.put("Subdomain",                   "");
        fdescr.put("TranslatedDocument",          "");
        fdescr.put("CollectionDate",              "");
        fdescr.put("Medium",                      "text");
        fdescr.put("Url",                         "");
        fdescr.put("Domain",                      "");
        fdescr.put("Keywords",                    "");
        fdescr.put("PersonallyIdentifiableInformation", "");
        fdescr.put("BiasedInformation",           "");
        fdescr.put("TaskCategories",              "");
        fdescr.put("NumberWords",                 0);
        fdescr.put("NumberSentences",             0);
        fdescr.put("NumberParagraphs",            0);
        fdescr.put("NumberTokens",                0);
        return fdescr;
    }
}
