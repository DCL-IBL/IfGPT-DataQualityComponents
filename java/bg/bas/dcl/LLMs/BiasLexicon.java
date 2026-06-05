package bg.bas.dcl.LLMs;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BiasLexicon
 *
 * Loads the Bulgarian bias dictionary (bulgarian_bias_dictionary_v4.tsv) and
 * provides fast O(1) form-level lookup for use by the bias detector.
 *
 * -----------------------------------------------------------------------
 * TSV FORMAT (tab-separated, first row is header):
 *
 *   Col  0  word           canonical lemma
 *   Col  1  POS            N | A | V | …
 *   Col  2  signal         true | false
 *   Col  3  biasType       gender | race_ethnicity | religion | disability | appearance | ""
 *   Col  4  biasValue      positive | negative | neutral | ""
 *   Col  5  derogatory     true | false
 *   Col  6  colloquial     true | false
 *   Col  7  forms          (boolean flag — ignored; inflected forms in col 10)
 *   Col  8  positivity     double [0,1]
 *   Col  9  negativity     double [0,1]
 *   Col 10  inflectedForms pipe-separated surface forms, or empty
 *
 *  
 */
public class BiasLexicon {

    // -----------------------------------------------------------------------
    // Indexes
    // -----------------------------------------------------------------------

    /**
     * Primary form index: lowercased surface form → BiasEntry.
     * A single form can only map to one entry (first one wins if there are
     * duplicates — extremely rare in the dictionary).
     */
    private final Map<String, BiasEntry> formIndex = new HashMap<>();

    /**
     * Canonical word index: lowercased lemma → BiasEntry.
     * Useful when you already have the base form.
     */
    private final Map<String, BiasEntry> wordIndex = new HashMap<>();

    /** All entries in load order. */
    private final List<BiasEntry> entries = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Loading statistics
    // -----------------------------------------------------------------------

    private int loadedEntries  = 0;
    private int skippedLines   = 0;
    private int formConflicts  = 0;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * Loads the bias dictionary from a TSV file.
     *
     * @param tsvPath absolute path to the TSV file
     * @throws RuntimeException if the file cannot be read
     */
    public BiasLexicon(String tsvPath) {
        load(tsvPath);
        System.out.printf("[BiasLexicon] Loaded %d entries, %d form keys, "
                + "%d skipped lines, %d form conflicts.%n",
                loadedEntries, formIndex.size(), skippedLines, formConflicts);
    }

    // -----------------------------------------------------------------------
    // Lookup API
    // -----------------------------------------------------------------------

    /**
     * Looks up a surface token (case-insensitive) and returns the
     * matching {@link BiasEntry}, or {@code null} if not found.
     *
     * @param token any surface form (inflected or base)
     */
    public BiasEntry lookup(String token) {
        if (token == null || token.isBlank()) return null;
        return formIndex.get(token.toLowerCase().trim());
    }

    /**
     * Returns true if the token (any form) is present in the lexicon.
     *
     * @param token surface form to check
     */
    public boolean contains(String token) {
        return lookup(token) != null;
    }

    /**
     * Looks up a canonical lemma directly.
     *
     * @param lemma the base/dictionary form
     */
    public BiasEntry lookupLemma(String lemma) {
        if (lemma == null || lemma.isBlank()) return null;
        return wordIndex.get(lemma.toLowerCase().trim());
    }

    // -----------------------------------------------------------------------
    // Filtered views
    // -----------------------------------------------------------------------

    /**
     * Returns all entries whose {@code biasType} matches the given category
     * (case-insensitive), plus all general entries (empty biasType).
     *
     * @param biasType e.g. "gender", "disability"
     */
    public List<BiasEntry> getByType(String biasType) {
        List<BiasEntry> result = new ArrayList<>();
        String target = biasType == null ? "" : biasType.toLowerCase().trim();
        for (BiasEntry e : entries)
            if (e.getBiasType().equalsIgnoreCase(target) || e.getBiasType().isEmpty())
                result.add(e);
        return result;
    }

    /**
     * Returns all entries that are marked as signals (signal=true) for
     * the given bias category, or all signal entries if biasType is null/empty.
     */
    public List<BiasEntry> getSignals(String biasType) {
        List<BiasEntry> result = new ArrayList<>();
        for (BiasEntry e : entries) {
            if (!e.isSignal()) continue;
            if (biasType == null || biasType.isBlank()
                    || e.getBiasType().isEmpty()
                    || e.getBiasType().equalsIgnoreCase(biasType))
                result.add(e);
        }
        return result;
    }

    /** Returns an unmodifiable view of all loaded entries. */
    public Collection<BiasEntry> getAll() {
        return Collections.unmodifiableList(entries);
    }

    /** Number of loaded dictionary entries. */
    public int size() { return entries.size(); }

    // -----------------------------------------------------------------------
    // Internal loading
    // -----------------------------------------------------------------------

    private void load(String tsvPath) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(tsvPath),
                        StandardCharsets.UTF_8))) {

            String headerLine = br.readLine(); // skip header
            if (headerLine == null) {
                System.err.println("[BiasLexicon] Empty file: " + tsvPath);
                return;
            }

            String line;
            int lineNum = 1; // already read header as line 1

            while ((line = br.readLine()) != null) {
                lineNum++;
                if (line.isBlank()) { skippedLines++; continue; }

                String[] cols = line.split("\t", -1);

                // Minimum viable: need at least 10 columns
                if (cols.length < 10) {
                    System.err.printf("[BiasLexicon] Line %d: only %d columns, skipping.%n",
                            lineNum, cols.length);
                    skippedLines++;
                    continue;
                }

                try {
                    String word       = cols[0].trim();
                    String pos        = cols[1].trim();
                    boolean signal    = "true".equalsIgnoreCase(cols[2].trim());
                    String biasType   = cols[3].trim();
                    String biasValue  = cols[4].trim();
                    boolean derog     = "true".equalsIgnoreCase(cols[5].trim());
                    boolean coll      = "true".equalsIgnoreCase(cols[6].trim());
                    // cols[7] is a boolean forms-flag (ignored)
                    double positivity = parseDouble(cols[8], lineNum);
                    double negativity = parseDouble(cols[9], lineNum);

                    // Inflected forms: pipe-separated in col 10 (if present)
                    Set<String> formsSet = new HashSet<>();
                    formsSet.add(word.toLowerCase()); // always include the lemma

                    if (cols.length > 10 && !cols[10].isBlank()) {
                        for (String f : cols[10].split("\\|")) {
                            String fc = f.trim().toLowerCase();
                            if (!fc.isEmpty()) formsSet.add(fc);
                        }
                    }

                    BiasEntry entry = new BiasEntry(word, pos, signal,
                            biasType, biasValue, derog, coll,
                            positivity, negativity, formsSet);

                    entries.add(entry);
                    wordIndex.put(word.toLowerCase(), entry);

                    for (String form : formsSet) {
                        if (formIndex.containsKey(form)) {
                            formConflicts++;
                            // Keep first entry — do not overwrite
                        } else {
                            formIndex.put(form, entry);
                        }
                    }

                    loadedEntries++;

                } catch (Exception e) {
                    System.err.printf("[BiasLexicon] Line %d: parse error — %s%n",
                            lineNum, e.getMessage());
                    skippedLines++;
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to load bias lexicon from: " + tsvPath, e);
        }
    }

    private double parseDouble(String s, int lineNum) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            System.err.printf("[BiasLexicon] Line %d: cannot parse double '%s', using 0.0%n",
                    lineNum, s);
            return 0.0;
        }
    }
}
