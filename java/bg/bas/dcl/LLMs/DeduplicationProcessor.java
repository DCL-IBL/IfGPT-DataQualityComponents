package bg.bas.dcl.LLMs.IfGPTDataset;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;

import info.debatty.java.lsh.MinHash;

import bg.bas.dcl.general.FileHandler;

/**
 * DeduplicationProcessor — sentence-level near-duplicate detection
 * using MinHash + LSH (Jaccard similarity).
 *
 * -----------------------------------------------------------------------
 * MAVEN DEPENDENCY (add to pom.xml):
 *
 *   <dependency>
 *     <groupId>info.debatty</groupId>
 *     <artifactId>java-lsh</artifactId>
 *     <version>0.12</version>
 *   </dependency>
 *
 * -----------------------------------------------------------------------
 * HOW IT WORKS
 *
 *   1. INDEX phase  — reads all .txt files in the "full corpus" directory.
 *      Each sentence is shingled into character n-grams, converted to a
 *      boolean vector over a shared vocabulary, and a MinHash signature
 *      is computed.  All signatures are stored in an in-memory index keyed
 *      by (file, lineNumber).
 *
 *   2. QUERY phase  — reads every sentence in the "new folder".
 *      For each sentence its MinHash signature is compared against every
 *      indexed corpus signature (approximate Jaccard via signature similarity).
 *      Pairs whose estimated Jaccard similarity ≥ threshold are reported.
 *
 *   3. REPORT       — a TSV report is written listing every duplicate pair:
 *      new-file | new-line | corpus-file | corpus-line | similarity | sentence
 *
 *   4. OPTIONAL REMOVE — sentences in the new folder that are duplicates of
 *      corpus sentences are stripped from their file (originals backed up).
 *      Files that become empty after removal are deleted.
 *
 * -----------------------------------------------------------------------
 * PARAMETERS
 *
 *   threshold   — Jaccard similarity to call a near-duplicate  (default 0.90)
 *   shingleSize — character n-gram size for shingling           (default 5)
 *   numHashes   — number of hash functions for MinHash          (default 200)
 *                 More hashes → better accuracy, slower index.
 *
 * -----------------------------------------------------------------------
 * USAGE
 *
 *   DeduplicationProcessor dp = new DeduplicationProcessor(0.90);
 *   dp.indexCorpus("/path/to/full/corpus/");
 *   dp.detectDuplicates("/path/to/new/folder/", "/path/to/report.tsv");
 *   dp.removeDuplicatesFromNewFolder("/path/to/new/folder/", true); // true=keep .bak
 */
public class DeduplicationProcessor {

    // -----------------------------------------------------------------------
    // Configuration
    // -----------------------------------------------------------------------

    private final double threshold;     // Jaccard similarity cut-off
    private final int    shingleSize;   // character n-gram size
    private final int    numHashes;     // MinHash signature length

    // -----------------------------------------------------------------------
    // Index state (built during indexCorpus)
    // -----------------------------------------------------------------------

    /** Shared vocabulary: every distinct shingle seen across all corpus sentences. */
    private final Set<String> vocabulary = new HashSet<>();

    /**
     * Corpus index: maps SentenceKey → raw sentence text + MinHash signature.
     * Built in two passes to allow vocabulary to be finalised before signing.
     */
    private final Map<SentenceKey, IndexedSentence> corpusIndex = new LinkedHashMap<>();

    /** MinHash object — initialised once vocabulary size is known. */
    private MinHash minHash;

    // -----------------------------------------------------------------------
    // Duplicate results (populated by detectDuplicates)
    // -----------------------------------------------------------------------

    /** All duplicate pairs found in the last detectDuplicates run. */
    private final List<DuplicatePair> duplicatePairs = new ArrayList<>();

    /**
     * Set of SentenceKeys in the NEW folder that are duplicates.
     * Used by removeDuplicatesFromNewFolder.
     */
    private final Set<SentenceKey> duplicateNewSentences = new HashSet<>();

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public DeduplicationProcessor(double threshold) {
        this(threshold, 5, 200);
    }

    public DeduplicationProcessor(double threshold, int shingleSize, int numHashes) {
        if (threshold < 0 || threshold > 1)
            throw new IllegalArgumentException("Threshold must be in [0, 1].");
        this.threshold   = threshold;
        this.shingleSize = shingleSize;
        this.numHashes   = numHashes;
    }

    // -----------------------------------------------------------------------
    // Phase 1 — Index the full corpus
    // -----------------------------------------------------------------------

    /**
     * Reads all .txt files in {@code corpusDir}, shingles every sentence,
     * builds a shared vocabulary, and computes MinHash signatures.
     *
     * This must be called before {@link #detectDuplicates}.
     *
     * @param corpusDir directory of clean .txt files representing the full corpus
     */
    public void indexCorpus(String corpusDir) {
        System.out.println("[Index] Scanning corpus: " + corpusDir);
        try {
            FileHandler fh = new FileHandler();

            // --- Pass 1: collect sentences and build vocabulary ---
            // Temporary store: key → raw text + shingle set (signatures computed later)
            Map<SentenceKey, Set<String>> rawShingles = new LinkedHashMap<>();

            for (File f : fh.getFileListing(new File(corpusDir))) {
                if (!f.isFile() || !f.getName().endsWith(".txt")) continue;

                Scanner sc = new Scanner(f, "UTF-8");
                int lineNum = 0;
                while (sc.hasNextLine()) {
                    String line = sc.nextLine().trim();
                    lineNum++;
                    if (line.length() < shingleSize) continue;

                    Set<String> shingles = shingle(line);
                    vocabulary.addAll(shingles);
                    rawShingles.put(new SentenceKey(f.getName(), lineNum), shingles);
                }
                sc.close();
            }

            System.out.println("[Index] Vocabulary size: " + vocabulary.size()
                    + "  Sentences: " + rawShingles.size());

            if (vocabulary.isEmpty()) {
                System.err.println("[Index] No sentences found — aborting.");
                return;
            }

            // --- Initialise MinHash with finalised vocabulary size ---
            // Error parameter 0.05 → ~400 hashes needed; we use numHashes directly.
            // The debatty MinHash constructor accepts (error, dictSize).
            // We use the lower-level approach: fix numHashes via the signature size.
            // info.debatty MinHash(double error, int dictSize) chooses hash count itself.
            // For explicit control we pass a small error so it aligns with numHashes.
            minHash = new MinHash(numHashes, vocabulary.size());

            // --- Pass 2: compute and store signatures ---
            List<String> vocabList = new ArrayList<>(vocabulary);
            corpusIndex.clear();

            // Also keep a raw-text map for the report
            Map<SentenceKey, String> rawTexts = new HashMap<>();
            // re-scan to get raw text (we only stored shingles above)
            for (File f : fh.getFileListing(new File(corpusDir))) {
                if (!f.isFile() || !f.getName().endsWith(".txt")) continue;
                Scanner sc = new Scanner(f, "UTF-8");
                int lineNum = 0;
                while (sc.hasNextLine()) {
                    String line = sc.nextLine().trim();
                    lineNum++;
                    if (line.length() < shingleSize) continue;
                    rawTexts.put(new SentenceKey(f.getName(), lineNum), line);
                }
                sc.close();
            }

            for (Map.Entry<SentenceKey, Set<String>> entry : rawShingles.entrySet()) {
                SentenceKey key     = entry.getKey();
                boolean[]   vector  = toVector(entry.getValue(), vocabList);
                int[]       sig     = minHash.signature(vector);
                String      rawText = rawTexts.getOrDefault(key, "");
                corpusIndex.put(key, new IndexedSentence(rawText, sig));
            }

            System.out.println("[Index] Corpus index built: "
                    + corpusIndex.size() + " sentences.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // -----------------------------------------------------------------------
    // Phase 2 — Detect duplicates in new folder
    // -----------------------------------------------------------------------

    /**
     * Compares every sentence in {@code newDir} against the corpus index.
     * Pairs with estimated Jaccard ≥ threshold are recorded as duplicates
     * and written to {@code reportPath}.
     *
     * Call {@link #indexCorpus} first.
     *
     * @param newDir     directory of new .txt files to check
     * @param reportPath destination TSV report file
     */
    public void detectDuplicates(String newDir, String reportPath) {
        if (corpusIndex.isEmpty()) {
            System.err.println("[Detect] Corpus index is empty. Call indexCorpus() first.");
            return;
        }

        System.out.println("[Detect] Comparing new folder against corpus index...");
        duplicatePairs.clear();
        duplicateNewSentences.clear();

        List<String> vocabList = new ArrayList<>(vocabulary);

        try {
            FileHandler fh = new FileHandler();

            for (File f : fh.getFileListing(new File(newDir))) {
                if (!f.isFile() || !f.getName().endsWith(".txt")) continue;

                System.out.println("[Detect] Checking: " + f.getName());

                Scanner sc = new Scanner(f, "UTF-8");
                int lineNum = 0;

                while (sc.hasNextLine()) {
                    String line = sc.nextLine().trim();
                    lineNum++;
                    if (line.length() < shingleSize) continue;

                    Set<String> shingles = shingle(line);

                    // Only shingles already in vocabulary are meaningful
                    Set<String> filtered = new HashSet<>(shingles);
                    filtered.retainAll(vocabulary);

                    // If almost none of the shingles are in vocab → skip
                    // (the sentence is likely from a very different domain)
                    if (filtered.isEmpty()) continue;

                    boolean[]   newVec = toVector(filtered, vocabList);
                    int[]       newSig = minHash.signature(newVec);

                    SentenceKey newKey = new SentenceKey(f.getName(), lineNum);

                    // Compare against all corpus sentences
                    // For large corpora, replace this loop with an LSH band index
                    for (Map.Entry<SentenceKey, IndexedSentence> entry : corpusIndex.entrySet()) {
                        double sim = minHash.similarity(newSig, entry.getValue().signature);
                        if (sim >= threshold) {
                            DuplicatePair pair = new DuplicatePair(
                                    newKey,   line,
                                    entry.getKey(), entry.getValue().text,
                                    sim);
                            duplicatePairs.add(pair);
                            duplicateNewSentences.add(newKey);
                            // Don't break — report ALL corpus matches for transparency
                        }
                    }
                }
                sc.close();
            }

            System.out.println("[Detect] Duplicate sentence pairs found: "
                    + duplicatePairs.size());
            System.out.println("[Detect] Unique new sentences flagged: "
                    + duplicateNewSentences.size());

            writeReport(reportPath);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // -----------------------------------------------------------------------
    // Phase 3 — Optionally remove duplicates from new folder
    // -----------------------------------------------------------------------

    /**
     * Removes from every file in {@code newDir} any sentence whose
     * (file, lineNumber) is in the duplicate set detected by
     * {@link #detectDuplicates}.
     *
     * Files that become empty after removal are deleted.
     * Must be called after {@link #detectDuplicates}.
     *
     * @param newDir     directory of new .txt files to clean
     * @param keepBackup if true, originals are renamed to *.bak first
     */
    public void removeDuplicatesFromNewFolder(String newDir, boolean keepBackup) {
        if (duplicateNewSentences.isEmpty()) {
            System.out.println("[Remove] No duplicates to remove.");
            return;
        }

        System.out.println("[Remove] Removing "
                + duplicateNewSentences.size() + " duplicate sentences...");

        try {
            FileHandler fh = new FileHandler();
            int filesModified = 0;
            int totalRemoved  = 0;

            for (File f : fh.getFileListing(new File(newDir))) {
                if (!f.isFile() || !f.getName().endsWith(".txt")) continue;

                List<String> inputLines  = new ArrayList<>();
                Scanner sc = new Scanner(f, "UTF-8");
                int lineNum = 0;
                while (sc.hasNextLine()) {
                    inputLines.add(sc.nextLine());
                    lineNum++;
                }
                sc.close();

                List<String> outputLines = new ArrayList<>();
                int removed = 0;

                for (int i = 0; i < inputLines.size(); i++) {
                    String trimmed = inputLines.get(i).trim();
                    // +1 because lineNum was 1-based during indexing
                    SentenceKey key = new SentenceKey(f.getName(), i + 1);

                    if (trimmed.length() >= shingleSize
                            && duplicateNewSentences.contains(key)) {
                        removed++;
                    } else {
                        outputLines.add(inputLines.get(i));
                    }
                }

                if (removed > 0) {
                    if (keepBackup) {
                        Files.copy(f.toPath(),
                                new File(f.getAbsolutePath() + ".bak").toPath(),
                                StandardCopyOption.REPLACE_EXISTING);
                    }

                    // Check if file would become empty (only blank lines)
                    boolean allBlank = outputLines.stream()
                            .allMatch(String::isBlank);

                    if (allBlank) {
                        f.delete();
                        System.out.println("[Remove] Deleted (empty after dedup): "
                                + f.getName());
                    } else {
                        Writer w = new OutputStreamWriter(
                                new FileOutputStream(f), "UTF-8");
                        for (String l : outputLines) {
                            w.write(l + "\n");
                        }
                        w.flush();
                        w.close();
                        System.out.println("[Remove] " + f.getName()
                                + " — removed " + removed + " sentences.");
                    }

                    filesModified++;
                    totalRemoved += removed;
                }
            }

            System.out.println("[Remove] Done. Files modified: " + filesModified
                    + "  Sentences removed: " + totalRemoved);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // -----------------------------------------------------------------------
    // Report writer
    // -----------------------------------------------------------------------

    private void writeReport(String reportPath) throws Exception {
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(reportPath), "UTF-8"))) {

            // Header
            pw.println("# DeduplicationProcessor report");
            pw.println("# Threshold: " + threshold
                    + "  ShingleSize: " + shingleSize
                    + "  NumHashes: " + numHashes);
            pw.println("# Duplicate pairs: " + duplicatePairs.size());
            pw.println("# Unique new sentences flagged: " + duplicateNewSentences.size());
            pw.println();
            pw.println("NEW_FILE\tNEW_LINE\tCORPUS_FILE\tCORPUS_LINE\tSIMILARITY\tNEW_SENTENCE\tCORPUS_SENTENCE");

            // Sort by similarity descending, then new file, then line
            List<DuplicatePair> sorted = new ArrayList<>(duplicatePairs);
            sorted.sort((a, b) -> {
                int cmp = Double.compare(b.similarity, a.similarity);
                if (cmp != 0) return cmp;
                cmp = a.newKey.fileName.compareTo(b.newKey.fileName);
                if (cmp != 0) return cmp;
                return Integer.compare(a.newKey.lineNumber, b.newKey.lineNumber);
            });

            for (DuplicatePair p : sorted) {
                pw.printf("%s\t%d\t%s\t%d\t%.4f\t%s\t%s%n",
                        p.newKey.fileName,
                        p.newKey.lineNumber,
                        p.corpusKey.fileName,
                        p.corpusKey.lineNumber,
                        p.similarity,
                        sanitiseTsv(p.newText),
                        sanitiseTsv(p.corpusText));
            }
        }
        System.out.println("[Report] Written to: " + reportPath);
    }

    // -----------------------------------------------------------------------
    // Shingling and vectorisation helpers
    // -----------------------------------------------------------------------

    /**
     * Produces the set of character n-grams (shingles) for a sentence.
     * Lowercased so matching is case-insensitive.
     */
    private Set<String> shingle(String text) {
        Set<String> shingles = new TreeSet<>();
        String lower = text.toLowerCase();
        for (int i = 0; i <= lower.length() - shingleSize; i++) {
            shingles.add(lower.substring(i, i + shingleSize));
        }
        return shingles;
    }

    /**
     * Converts a shingle set to a boolean presence vector over the shared vocabulary.
     *
     * @param shingles  shingle set for this sentence
     * @param vocabList ordered list of all vocabulary shingles
     * @return boolean[] where true = shingle present
     */
    private boolean[] toVector(Set<String> shingles, List<String> vocabList) {
        boolean[] vector = new boolean[vocabList.size()];
        for (int i = 0; i < vocabList.size(); i++) {
            vector[i] = shingles.contains(vocabList.get(i));
        }
        return vector;
    }

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------

    private String sanitiseTsv(String s) {
        if (s == null) return "";
        return s.replace("\t", " ").replace("\n", " ").replace("\r", "");
    }

    /** Returns an unmodifiable view of all detected duplicate pairs. */
    public List<DuplicatePair> getDuplicatePairs() {
        return Collections.unmodifiableList(duplicatePairs);
    }

    /** Returns the number of corpus sentences indexed. */
    public int getCorpusSize() {
        return corpusIndex.size();
    }

    // -----------------------------------------------------------------------
    // Inner data classes
    // -----------------------------------------------------------------------

    /**
     * Uniquely identifies a sentence by its source file name and line number.
     */
    public static class SentenceKey {
        public final String fileName;
        public final int    lineNumber;

        public SentenceKey(String fileName, int lineNumber) {
            this.fileName   = fileName;
            this.lineNumber = lineNumber;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SentenceKey)) return false;
            SentenceKey other = (SentenceKey) o;
            return lineNumber == other.lineNumber
                    && fileName.equals(other.fileName);
        }

        @Override
        public int hashCode() {
            return 31 * fileName.hashCode() + lineNumber;
        }

        @Override
        public String toString() {
            return fileName + ":" + lineNumber;
        }
    }

    /**
     * Holds the raw text and MinHash signature for an indexed corpus sentence.
     */
    private static class IndexedSentence {
        final String text;
        final int[]  signature;

        IndexedSentence(String text, int[] signature) {
            this.text      = text;
            this.signature = signature;
        }
    }

    /**
     * Represents a detected near-duplicate pair between a new sentence
     * and a corpus sentence.
     */
    public static class DuplicatePair {
        public final SentenceKey newKey;
        public final String      newText;
        public final SentenceKey corpusKey;
        public final String      corpusText;
        public final double      similarity;

        public DuplicatePair(SentenceKey newKey,    String newText,
                             SentenceKey corpusKey, String corpusText,
                             double similarity) {
            this.newKey     = newKey;
            this.newText    = newText;
            this.corpusKey  = corpusKey;
            this.corpusText = corpusText;
            this.similarity = similarity;
        }

        @Override
        public String toString() {
            return String.format("[%.2f] %s ↔ %s", similarity, newKey, corpusKey);
        }
    }
}
