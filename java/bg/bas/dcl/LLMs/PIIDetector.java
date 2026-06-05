package bg.bas.dcl.LLMs;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

import ai.philterd.phileas.model.configuration.PhileasConfiguration;
import ai.philterd.phileas.model.policy.Policy;
import ai.philterd.phileas.model.responses.FilterResponse;
import ai.philterd.phileas.model.responses.Span;
import ai.philterd.phileas.services.PlainTextFilterService;

import bg.bas.dcl.general.FileHandler;

/**
 * PIIDetector
 *
 * Detects Personally Identifiable Information (PII) in Bulgarian text at
 * sentence level using the <b>Phileas</b> library (ai.philterd:phileas).
 *
 * -----------------------------------------------------------------------
 * NOTE ON "PIISA"
 * PIISA (https://piisa.org) is a Python-only PII framework with no Java
 * bindings.  The closest Java-native equivalent with a compatible
 * detection scope is Phileas (Apache 2.0, Maven Central, actively
 * maintained as of 2025).  This component uses Phileas and documents
 * all places where a future PIISA Java binding could be substituted.
 * -----------------------------------------------------------------------
 *
 * MAVEN DEPENDENCY (pom.xml):
 * <pre>
 *   &lt;dependency&gt;
 *     &lt;groupId&gt;ai.philterd&lt;/groupId&gt;
 *     &lt;artifactId&gt;phileas&lt;/artifactId&gt;
 *     &lt;version&gt;3.1.0&lt;/version&gt;
 *   &lt;/dependency&gt;
 * </pre>
 *
 * -----------------------------------------------------------------------
 * PII TYPES DETECTED (Phileas built-in, language-agnostic unless noted):
 *
 *   Person names (NER + census dictionary) | Ages | Email addresses
 *   Phone numbers | IP addresses (v4 + v6) | URLs | Credit card numbers
 *   SSN / TIN | IBAN codes | Bank account numbers | Dates | Zip codes
 *   MAC addresses | Bitcoin addresses | VINs | Passport numbers
 *   Driver licence numbers | Medical conditions
 *
 * Language note: NER-based person-name detection uses English models by
 * default.  For Bulgarian names, supply a custom dictionary filter
 * (see {@link #buildPolicy()}) or integrate a Bulgarian NER model.
 * Regex-based filters (emails, phones, IPs, etc.) are language-independent
 * and work directly on Bulgarian text.
 *
 * -----------------------------------------------------------------------
 * ALGORITHM (per sentence):
 *
 *   1. Phileas scans the sentence and returns a list of PII *spans*, each
 *      carrying a character start/end offset and a PII type label.
 *   2. We map spans back to word tokens by checking which token positions
 *      overlap any detected span.
 *   3. piiCoverage = |tokens overlapping PII spans| / |total word tokens|
 *
 * -----------------------------------------------------------------------
 * USAGE
 *
 *   BulgarianSentenceSplitter splitter = new BulgarianSentenceSplitter();
 *   PIIDetector detector = new PIIDetector(splitter);
 *
 *   List&lt;SentencePIIScore&gt; scores = detector.analyseText("Иван Петров живее на ул. Роза 5.");
 *   for (SentencePIIScore s : scores) {
 *       System.out.printf("%.1f%% PII — %s%n", s.getPiiCoveragePercent(), s.getSentence());
 *   }
 *
 *   // Corpus-level processing with TSV output
 *   detector.analyseDirectory("/path/to/corpus/", "/path/to/pii_report.tsv");
 */
public class PIIDetector {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    /** Context string passed to Phileas (arbitrary; used for logging/caching). */
    private static final String CONTEXT  = "bg-corpus";

    /** Document ID prefix; a counter suffix is appended per sentence. */
    private static final String DOC_ID   = "sent-";

    /** Minimum word count for a sentence to be analysed. */
    private static final int    MIN_WORDS = 3;

    // -----------------------------------------------------------------------
    // Dependencies
    // -----------------------------------------------------------------------

    private final BulgarianSentenceSplitter splitter;
    private final PlainTextFilterService    filterService;
    private final List<Policy>              policies;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    /**
     * Creates a PIIDetector with the default policy (all built-in Phileas
     * filters active, REDACT strategy so spans are easy to count).
     *
     * @param splitter an initialised {@link BulgarianSentenceSplitter}
     */
    public PIIDetector(BulgarianSentenceSplitter splitter) {
        this(splitter, null);
    }

    /**
     * Creates a PIIDetector with a custom Phileas {@link Policy}.
     * Pass {@code null} to use the built-in all-PII policy.
     *
     * @param splitter       an initialised {@link BulgarianSentenceSplitter}
     * @param customPolicy   a pre-built Phileas Policy, or null for default
     */
    public PIIDetector(BulgarianSentenceSplitter splitter, Policy customPolicy) {
        if (splitter == null)
            throw new IllegalArgumentException("splitter must not be null");

        this.splitter = splitter;

        try {
            Properties props = new Properties();
            PhileasConfiguration config = new PhileasConfiguration(props);
            this.filterService = new PlainTextFilterService(config);
            this.policies = List.of(customPolicy != null ? customPolicy : buildPolicy());
            System.out.println("[PIIDetector] Phileas filter service initialised.");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialise Phileas filter service", e);
        }
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Splits {@code text} into sentences and returns a {@link SentencePIIScore}
     * for each sentence.
     *
     * Sentences shorter than {@link #MIN_WORDS} words receive a zero score
     * without calling Phileas (to avoid spurious detections on fragments).
     *
     * @param text any Bulgarian plain text (may span multiple paragraphs)
     * @return one score per detected sentence, in order; never null
     */
    public List<SentencePIIScore> analyseText(String text) {
        List<SentencePIIScore> results = new ArrayList<>();
        if (text == null || text.isBlank()) return results;

        int docCounter = 0;
        for (String sentence : splitter.split(text)) {
            results.add(analyseSentence(sentence, DOC_ID + (docCounter++)));
        }
        return results;
    }

    /**
     * Analyses a single pre-split sentence.
     *
     * @param sentence the sentence string (not null)
     * @param docId    a document/sentence identifier string for Phileas context
     * @return a fully populated {@link SentencePIIScore}
     */
    public SentencePIIScore analyseSentence(String sentence, String docId) {

        // --- Tokenise ---
        String[] rawTokens = sentence.trim().split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String t : rawTokens) {
            String clean = t.replaceAll("[^\\p{L}\\p{N}@._+\\-]", "");
            if (!clean.isEmpty()) tokens.add(clean);
        }
        int totalWords = tokens.size();

        if (totalWords < MIN_WORDS) {
            return SentencePIIScore.empty(sentence, totalWords);
        }

        // --- Run Phileas ---
        List<Span> spans;
        try {
            FilterResponse response = filterService.filter(
                    policies, CONTEXT, docId, sentence, null);
            spans = response.getSpans() != null ? response.getSpans() : List.of();
        } catch (Exception e) {
            System.err.println("[PIIDetector] Phileas error on sentence: " + e.getMessage());
            return SentencePIIScore.error(sentence, totalWords, e.getMessage());
        }

        // --- Map character-level spans back to token positions ---
        // Build token character offsets from the original sentence string
        int[] tokenStart = new int[tokens.size()];
        int[] tokenEnd   = new int[tokens.size()];
        int cursor = 0;
        for (int ti = 0; ti < tokens.size(); ti++) {
            String tok = tokens.get(ti);
            int idx = sentence.indexOf(tok, cursor);
            if (idx < 0) {
                // Fallback: token not found at expected position (normalisation artefact)
                tokenStart[ti] = cursor;
                tokenEnd[ti]   = cursor + tok.length();
            } else {
                tokenStart[ti] = idx;
                tokenEnd[ti]   = idx + tok.length();
                cursor = idx + tok.length();
            }
        }

        // Count distinct PII tokens and collect type labels per token
        Map<Integer, String> piiTokenType = new LinkedHashMap<>(); // tokenIndex → PII type
        for (Span span : spans) {
            int spanStart = span.getStart();
            int spanEnd   = span.getEnd();
            String type   = span.getFilterType() != null
                            ? span.getFilterType().name()
                            : "UNKNOWN";

            for (int ti = 0; ti < tokens.size(); ti++) {
                // Overlap: token and span share at least one character
                if (tokenStart[ti] < spanEnd && tokenEnd[ti] > spanStart) {
                    piiTokenType.put(ti, type);
                }
            }
        }

        // --- Build type frequency map ---
        Map<String, Integer> typeCounts = new LinkedHashMap<>();
        for (String type : piiTokenType.values()) {
            typeCounts.merge(type, 1, Integer::sum);
        }

        int piiTokenCount = piiTokenType.size();
        double coverage   = totalWords > 0
                ? (double) piiTokenCount / totalWords
                : 0.0;

        return new SentencePIIScore(
                sentence, totalWords, piiTokenCount, coverage,
                new ArrayList<>(piiTokenType.values()),
                typeCounts, spans, null);
    }

    // -----------------------------------------------------------------------
    // Corpus-level processing
    // -----------------------------------------------------------------------

    /**
     * Analyses all .txt files in {@code corpusDir} sentence by sentence and
     * writes results to a TSV file at {@code reportPath}.
     *
     * Only sentences with at least one PII token are written to the report.
     *
     * @param corpusDir  directory of plain-text .txt files
     * @param reportPath destination TSV report file path
     */
    public void analyseDirectory(String corpusDir, String reportPath) {
        try {
            FileHandler fh = new FileHandler();
            int filesProcessed = 0, sentencesWritten = 0;

            try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(reportPath, false), StandardCharsets.UTF_8))) {

                bw.write("file\t" + SentencePIIScore.tsvHeader());
                bw.newLine();

                for (File f : fh.getFileListing(new File(corpusDir))) {
                    if (!f.isFile() || !f.getName().endsWith(".txt")) continue;

                    System.out.println("[PIIDetector] Processing: " + f.getName());

                    StringBuilder text = new StringBuilder();
                    try (Scanner sc = new Scanner(f, StandardCharsets.UTF_8)) {
                        while (sc.hasNextLine()) text.append(sc.nextLine()).append(' ');
                    }

                    int docCounter = 0;
                    for (SentencePIIScore score : analyseText(text.toString())) {
                        if (score.hasPII()) {
                            bw.write(f.getName() + "\t" + score.toTsv());
                            bw.newLine();
                            sentencesWritten++;
                        }
                        docCounter++;
                    }
                    filesProcessed++;
                }
            }

            System.out.printf("[PIIDetector] Done.  Files: %d  Sentences with PII written: %d%n",
                    filesProcessed, sentencesWritten);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // -----------------------------------------------------------------------
    // Policy builder
    // -----------------------------------------------------------------------

    /**
     * Builds the default Phileas {@link Policy} that activates all
     * language-agnostic PII filters with a REDACT strategy (so that
     * span positions remain stable for overlap calculation).
     *
     * To customise, edit the JSON string below or deserialise your own
     * policy from a .json file with:
     *   Policy policy = Policy.fromJson(new String(Files.readAllBytes(path)));
     *
     * To add a Bulgarian names dictionary, add an "identifiers.dictionary"
     * block pointing to a file of Bulgarian given names and surnames.
     */
    private Policy buildPolicy() throws Exception {
        String policyJson = "{"
            + "\"name\": \"pii-all\","
            + "\"identifiers\": {"
            +   "\"emailAddress\":    {\"emailAddressFilterStrategies\":    [{\"strategy\":\"REDACT\",\"redactionFormat\":\"{{{REDACTED-%t}}}\"}]},"
            +   "\"phoneNumber\":     {\"phoneNumberFilterStrategies\":     [{\"strategy\":\"REDACT\",\"redactionFormat\":\"{{{REDACTED-%t}}}\"}]},"
            +   "\"ipAddress\":       {\"ipAddressFilterStrategies\":       [{\"strategy\":\"REDACT\",\"redactionFormat\":\"{{{REDACTED-%t}}}\"}]},"
            +   "\"url\":             {\"urlFilterStrategies\":             [{\"strategy\":\"REDACT\",\"redactionFormat\":\"{{{REDACTED-%t}}}\"}]},"
            +   "\"creditCard\":      {\"creditCardFilterStrategies\":      [{\"strategy\":\"REDACT\",\"redactionFormat\":\"{{{REDACTED-%t}}}\"}]},"
            +   "\"ssn\":             {\"ssnFilterStrategies\":             [{\"strategy\":\"REDACT\",\"redactionFormat\":\"{{{REDACTED-%t}}}\"}]},"
            +   "\"ibanCode\":        {\"ibanCodeFilterStrategies\":        [{\"strategy\":\"REDACT\",\"redactionFormat\":\"{{{REDACTED-%t}}}\"}]},"
            +   "\"bankAccountNumber\":{\"bankAccountNumberFilterStrategies\":[{\"strategy\":\"REDACT\",\"redactionFormat\":\"{{{REDACTED-%t}}}\"}]},"
            +   "\"date\":            {\"dateFilterStrategies\":            [{\"strategy\":\"REDACT\",\"redactionFormat\":\"{{{REDACTED-%t}}}\"}]},"
            +   "\"age\":             {\"ageFilterStrategies\":             [{\"strategy\":\"REDACT\",\"redactionFormat\":\"{{{REDACTED-%t}}}\"}]},"
            +   "\"macAddress\":      {\"macAddressFilterStrategies\":      [{\"strategy\":\"REDACT\",\"redactionFormat\":\"{{{REDACTED-%t}}}\"}]},"
            +   "\"bitcoinAddress\":  {\"bitcoinAddressFilterStrategies\":  [{\"strategy\":\"REDACT\",\"redactionFormat\":\"{{{REDACTED-%t}}}\"}]},"
            +   "\"vin\":             {\"vinFilterStrategies\":             [{\"strategy\":\"REDACT\",\"redactionFormat\":\"{{{REDACTED-%t}}}\"}]},"
            +   "\"zipCode\":         {\"zipCodeFilterStrategies\":         [{\"strategy\":\"REDACT\",\"redactionFormat\":\"{{{REDACTED-%t}}}\"}]},"
            +   "\"person\":          {\"personFilterStrategies\":          [{\"strategy\":\"REDACT\",\"redactionFormat\":\"{{{REDACTED-%t}}}\"}]}"
            + "}"
            + "}";
        return Policy.fromJson(policyJson);
    }

    // -----------------------------------------------------------------------
    // Inner result class
    // -----------------------------------------------------------------------

    /**
     * Immutable result object for one sentence's PII analysis.
     */
    public static class SentencePIIScore {

        private final String            sentence;
        private final int               totalWords;
        private final int               piiTokenCount;
        /** PII coverage: piiTokenCount / totalWords in [0, 1]. */
        private final double            piiCoverage;
        /** Ordered list of PII type labels for each PII token found. */
        private final List<String>      piiTypes;
        /** Frequency of each PII type in this sentence. */
        private final Map<String, Integer> typeFrequency;
        /** Raw Phileas spans (character-level). */
        private final List<Span>        spans;
        /** Non-null if Phileas threw an exception for this sentence. */
        private final String            errorMessage;

        SentencePIIScore(String sentence, int totalWords, int piiTokenCount,
                         double piiCoverage, List<String> piiTypes,
                         Map<String, Integer> typeFrequency,
                         List<Span> spans, String errorMessage) {
            this.sentence      = sentence;
            this.totalWords    = totalWords;
            this.piiTokenCount = piiTokenCount;
            this.piiCoverage   = piiCoverage;
            this.piiTypes      = Collections.unmodifiableList(piiTypes);
            this.typeFrequency = Collections.unmodifiableMap(typeFrequency);
            this.spans         = spans != null
                                 ? Collections.unmodifiableList(spans)
                                 : List.of();
            this.errorMessage  = errorMessage;
        }

        static SentencePIIScore empty(String sentence, int totalWords) {
            return new SentencePIIScore(sentence, totalWords, 0, 0.0,
                    List.of(), Map.of(), List.of(), null);
        }

        static SentencePIIScore error(String sentence, int totalWords, String msg) {
            return new SentencePIIScore(sentence, totalWords, 0, 0.0,
                    List.of(), Map.of(), List.of(), msg);
        }

        // --- Accessors ---

        public String            getSentence()           { return sentence; }
        public int               getTotalWords()         { return totalWords; }
        public int               getPiiTokenCount()      { return piiTokenCount; }
        /** PII coverage ratio in [0, 1]. */
        public double            getPiiCoverage()        { return piiCoverage; }
        /** PII coverage expressed as a percentage [0, 100]. */
        public double            getPiiCoveragePercent() { return piiCoverage * 100.0; }
        public List<String>      getPiiTypes()           { return piiTypes; }
        public Map<String, Integer> getTypeFrequency()   { return typeFrequency; }
        public List<Span>        getSpans()              { return spans; }
        public boolean           hasPII()                { return piiTokenCount > 0; }
        public boolean           hasError()              { return errorMessage != null; }
        public String            getErrorMessage()       { return errorMessage; }

        /** Number of distinct PII categories detected in this sentence. */
        public int distinctPiiTypes() { return typeFrequency.size(); }

        // --- TSV export ---

        /**
         * TSV row: sentence | totalWords | piiTokens | coverage% | distinctTypes | typeFrequency
         */
        public String toTsv() {
            return String.format("%s\t%d\t%d\t%.4f\t%.2f\t%d\t%s",
                    sentence.replace('\t', ' '),
                    totalWords,
                    piiTokenCount,
                    piiCoverage,
                    getPiiCoveragePercent(),
                    distinctPiiTypes(),
                    typeFrequency.toString());
        }

        public static String tsvHeader() {
            return "sentence\ttotalWords\tpiiTokens\tpiiCoverage\tpiiCoverage%\tdistinctPiiTypes\ttypeFrequency";
        }

        @Override
        public String toString() {
            return String.format("SentencePIIScore{words=%d, piiTokens=%d, coverage=%.1f%%, types=%s}",
                    totalWords, piiTokenCount, getPiiCoveragePercent(), typeFrequency.keySet());
        }
    }
}
