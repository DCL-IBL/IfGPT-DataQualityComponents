package bg.bas.dcl.LLMs.IfGPTDataset;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import bg.bas.dcl.LLMs.BiasAnalyser;
import bg.bas.dcl.LLMs.BiasLexicon;
import bg.bas.dcl.LLMs.BulgarianSentenceSplitter;
import bg.bas.dcl.LLMs.PIIDetector;
import bg.bas.dcl.LLMs.SentenceBiasScore;
import bg.bas.dcl.general.FileHandler;
import bg.bas.dcl.general.JSONProcessor;

/**
 * IfGPTPipeline
 *
 * Pipeline for the ifGPT Bulgarian language dataset.
 *
 * -----------------------------------------------------------------------
-----------------------------------------------------------------------
 * PIPELINE STAGES (executed in order by {@link #run()})
 *
 *   1. EXTRACT  
 *   2. SPLIT   
 *   3. CLEAN  
 *   4. DEDUPLICATE  
 *   5. PII      
 *   6. BIAS    
 *   7. COUNTS    —  word / sentence / token counts are recomputed on the cleaned, deduplicated text
 *     FULL_DATA_DIR / FULL_META_DIR
 *
 * -----------------------------------------------------------------------
 
 */
@SuppressWarnings("unchecked")
public class IfGPTPipeline {

    // -----------------------------------------------------------------------
    // Fixed paths
    // -----------------------------------------------------------------------

    public static final String FULL_DATA_DIR =
            "/home/ivelina/WORK-DCL/IfGPT/IFGPT-DATASET-DATA/";
    public static final String FULL_META_DIR =
            "/home/ivelina/WORK-DCL/IfGPT/IFGPT-DATASET-METADATA/";

    // -----------------------------------------------------------------------
    // Configurable paths and options
    // -----------------------------------------------------------------------

    private SourceProcessor sourceProcessor;      // mandatory
    private String newDataDir;                    // mandatory: incoming texts
    private String sampleDir;                     // mandatory: boilerplate sample
    private String newMetaDir;                    // mandatory: output metadata
    private String blocklistFile;                 // boilerplate blocklist file
    private String dedupReport;                   // dedup TSV report path
    private String biasDictPath;                  // bias dictionary TSV
    private String openNlpModelPath = null;       // null = bundled JAR model
    private double boilerplateThreshold = 0.50;   // FileCleanProcessor threshold
    private double dedupThreshold       = 0.90;   // DeduplicationProcessor threshold
    private int    dedupShingleSize     = 5;
    private int    dedupNumHashes       = 200;
    private boolean removeDuplicates    = false;  // whether to strip dup sentences
    private boolean keepBackups         = true;   // keep .bak on file modification
    private boolean skipClean           = false;  // skip boilerplate cleaning
    private boolean skipDedup           = false;  // skip deduplication
    private boolean skipPii             = false;  // skip PII scoring
    private boolean skipBias            = false;  // skip bias scoring

    // -----------------------------------------------------------------------
    //  
    // -----------------------------------------------------------------------

    public IfGPTPipeline setSourceProcessor(SourceProcessor p)  { sourceProcessor = p; return this; }
    public IfGPTPipeline setNewDataDir(String p)                { newDataDir = p; return this; }
    public IfGPTPipeline setSampleDir(String p)                 { sampleDir = p; return this; }
    public IfGPTPipeline setNewMetaDir(String p)                { newMetaDir = p; return this; }
    public IfGPTPipeline setBlocklistFile(String p)             { blocklistFile = p; return this; }
    public IfGPTPipeline setDedupReport(String p)               { dedupReport = p; return this; }
    public IfGPTPipeline setBiasDictPath(String p)              { biasDictPath = p; return this; }
    public IfGPTPipeline setOpenNlpModelPath(String p)          { openNlpModelPath = p; return this; }
    public IfGPTPipeline setBoilerplateThreshold(double t)      { boilerplateThreshold = t; return this; }
    public IfGPTPipeline setDedupThreshold(double t)            { dedupThreshold = t; return this; }
    public IfGPTPipeline setDedupShingleSize(int n)             { dedupShingleSize = n; return this; }
    public IfGPTPipeline setDedupNumHashes(int n)               { dedupNumHashes = n; return this; }
    public IfGPTPipeline setRemoveDuplicates(boolean b)         { removeDuplicates = b; return this; }
    public IfGPTPipeline setKeepBackups(boolean b)              { keepBackups = b; return this; }
    public IfGPTPipeline setSkipClean(boolean b)                { skipClean = b; return this; }
    public IfGPTPipeline setSkipDedup(boolean b)                { skipDedup = b; return this; }
    public IfGPTPipeline setSkipPii(boolean b)                  { skipPii = b; return this; }
    public IfGPTPipeline setSkipBias(boolean b)                 { skipBias = b; return this; }

    // -----------------------------------------------------------------------
    //   -----------------------------------------------------------------------

    /**
     * Executes all  stages in order.
     * Throws {@link IllegalStateException} if mandatory configuration is missing.
     */
    public void run() {
        validateConfig();
        ensureDirs(newMetaDir, FULL_DATA_DIR, FULL_META_DIR);

        banner("STAGE 1 — SOURCE EXTRACTION");
        runExtraction();

        // Shared NLP components (initialised once, reused across stages)
        BulgarianSentenceSplitter splitter = new BulgarianSentenceSplitter(openNlpModelPath);

        banner("STAGE 2 — SENTENCE SPLITTING & INITIAL METADATA");
        runSentenceSplitting(splitter);

        if (!skipClean) {
            banner("STAGE 3 — BOILERPLATE CLEANING");
            runCleaning();
        } else {
            log("STAGE 3 skipped (skipClean=true)");
        }

        if (!skipDedup) {
            banner("STAGE 4 — DEDUPLICATION");
            runDeduplication();
        } else {
            log("STAGE 4 skipped (skipDedup=true)");
        }

        PIIDetector  piiDetector  = skipPii  ? null : new PIIDetector(splitter);
        BiasAnalyser biasAnalyser = skipBias ? null : buildBiasAnalyser(splitter);

        banner("STAGES 5-7 — PII, BIAS & FINAL COUNTS");
        runAnnotationAndCounts(splitter, piiDetector, biasAnalyser);

        banner("STAGE 8 — PERSIST TO FULL CORPUS");
        runPersist();

        banner("PIPELINE COMPLETE");
    }

    // -----------------------------------------------------------------------
    // Stage 1 — Extraction
    // -----------------------------------------------------------------------

    private void runExtraction() {
        // The source processor writes plain-text files to newDataDir and
        // seed metadata JSON to newMetaDir.
        sourceProcessor.process(newDataDir, newMetaDir);
        log("Extraction complete → " + newDataDir);
    }

    // -----------------------------------------------------------------------
    // Stage 2 — Sentence splitting
    // -----------------------------------------------------------------------

    /**
     * Reads each metadata JSON produced by the source processor, then for
     * each document text file counts sentences properly using the OpenNLP
     * splitter and writes the sentence list to a parallel .sentences file
     * (one sentence per line) used by later stages.
     */
    private void runSentenceSplitting(BulgarianSentenceSplitter splitter) {
        try {
            FileHandler fh = new FileHandler();
            int docs = 0;

            for (File txtFile : fh.getFileListing(new File(newDataDir))) {
                if (!txtFile.isFile() || !txtFile.getName().endsWith(".txt")) continue;

                // Read document text
                StringBuilder sb = new StringBuilder();
                try (Scanner sc = new Scanner(txtFile, StandardCharsets.UTF_8)) {
                    while (sc.hasNextLine()) sb.append(sc.nextLine()).append('\n');
                }
                String text = sb.toString().trim();

                // Split into sentences and persist to .sentences sidecar file
                String[] sentences = splitter.split(text);
                File sentFile = new File(newDataDir, txtFile.getName()
                        .replace(".txt", ".sentences"));

                try (Writer w = new OutputStreamWriter(
                        new FileOutputStream(sentFile), StandardCharsets.UTF_8)) {
                    for (String sent : sentences) {
                        if (!sent.isBlank()) {
                            w.write(sent.trim());
                            w.write('\n');
                        }
                    }
                }
                docs++;
            }
            log("Sentence splitting complete.  Documents: " + docs);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // -----------------------------------------------------------------------
    // Stage 3 — Boilerplate cleaning
    // -----------------------------------------------------------------------

    private void runCleaning() {
        FileCleanProcessor fcp = new FileCleanProcessor(boilerplateThreshold);

        // Learn from sample
        fcp.learnFromSample(sampleDir);
        fcp.printTopCommonLines(20);

        // Save blocklist for audit / reproducibility
        if (blocklistFile != null && !blocklistFile.isBlank()) {
            fcp.saveBlocklist(blocklistFile);
        }

        // Clean the new data directory
        fcp.cleanDirectory(newDataDir, keepBackups);
        log("Boilerplate cleaning complete → " + newDataDir);
    }

    // -----------------------------------------------------------------------
    // Stage 4 — Deduplication
    // -----------------------------------------------------------------------

    private void runDeduplication() {
        DeduplicationProcessor dp = new DeduplicationProcessor(
                dedupThreshold, dedupShingleSize, dedupNumHashes);

        // Index the full existing corpus
        log("Indexing full corpus for deduplication…");
        dp.indexCorpus(FULL_DATA_DIR);
        log("Corpus indexed.  Sentences: " + dp.getCorpusSize());

        // Detect near-duplicates in new data
        String report = dedupReport != null
                ? dedupReport
                : newMetaDir + "dedup_report.tsv";
        dp.detectDuplicates(newDataDir, report);

        if (removeDuplicates) {
            dp.removeDuplicatesFromNewFolder(newDataDir, keepBackups);
        }
    }

    // -----------------------------------------------------------------------
    // Stages 5-7 — PII, Bias annotation + final counts
    // -----------------------------------------------------------------------

    /**
     * For each document:
     *   a) reads the (cleaned, deduplicated) .sentences sidecar file,
     *   b) runs PII and/or Bias scoring per sentence,
     *   c) recomputes word/sentence/token counts on the surviving text,
     *   d) merges all computed values into a DocumentMetadata and writes
     *      the final metadata JSON to newMetaDir.
     */
    private void runAnnotationAndCounts(BulgarianSentenceSplitter splitter,
                                        PIIDetector  piiDetector,
                                        BiasAnalyser biasAnalyser) {
        try {
            FileHandler   fh = new FileHandler();
            JSONProcessor jp = new JSONProcessor();
            int docs = 0, errors = 0;

            for (File sentFile : fh.getFileListing(new File(newDataDir))) {
                if (!sentFile.isFile()
                        || !sentFile.getName().endsWith(".sentences")) continue;

                String stem = sentFile.getName().replace(".sentences", "");

                // --- Load sentences ---
                List<String> sentences = new ArrayList<>();
                try (Scanner sc = new Scanner(sentFile, StandardCharsets.UTF_8)) {
                    while (sc.hasNextLine()) {
                        String s = sc.nextLine().trim();
                        if (!s.isBlank()) sentences.add(s);
                    }
                }

                if (sentences.isEmpty()) {
                    log("[WARN] No sentences for: " + stem);
                    errors++;
                    continue;
                }

                // --- Load or create DocumentMetadata ---
                DocumentMetadata meta = loadOrCreateMetadata(jp, stem);

                // --- PII per sentence ---
                List<Double> piiVec = new ArrayList<>();
                if (piiDetector != null) {
                    int sentIdx = 0;
                    for (String sent : sentences) {
                        PIIDetector.SentencePIIScore score =
                                piiDetector.analyseSentence(sent, stem + "-" + sentIdx++);
                        piiVec.add(score.getPiiCoverage());
                    }
                }
                meta.setPiiVector(piiVec);

                // --- Bias per sentence ---
                List<Double> biasVec = new ArrayList<>();
                if (biasAnalyser != null) {
                    for (String sent : sentences) {
                        SentenceBiasScore score = biasAnalyser.analyseSentence(sent);
                        biasVec.add(score.totalCoverage());
                    }
                }
                meta.setBiasVector(biasVec);

                // --- Recompute counts from surviving sentences ---
                int nSentences  = sentences.size();
                int nWords      = 0;
                int nTokens     = 0;

                for (String sent : sentences) {
                    String[] toks = sent.split("\\s+");
                    nWords  += toks.length;
                    // estimate tokens: words + punctuation characters
                    nTokens += toks.length + sent.length()
                             - sent.replaceAll("[.,;:!?()\\-]", "").length();
                }

                // Paragraphs: count blank-line groups in the original text file
                int nParagraphs = countParagraphs(new File(newDataDir, stem + ".txt"));

                meta.setNumberSentences(nSentences)
                    .setNumberWords(nWords)
                    .setNumberTokens(nTokens)
                    .setNumberParagraphs(nParagraphs);

                // --- Persist metadata JSON ---
                writeMetadata(meta, newMetaDir + stem + "_meta.json");
                docs++;
            }

            log("Annotation & counts complete.  Documents: " + docs
                    + "  Errors: " + errors);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // -----------------------------------------------------------------------
    // Stage 8 
    // -----------------------------------------------------------------------

    /** 
     */
    private void runPersist() {
        try {
            FileHandler fh = new FileHandler();
            int dataCopied = 0, metaCopied = 0;

            // Copy text files
            for (File f : fh.getFileListing(new File(newDataDir))) {
                if (!f.isFile() || !f.getName().endsWith(".txt")) continue;
                File dest = new File(FULL_DATA_DIR, f.getName());
                if (!dest.exists()) {
                    Files.copy(f.toPath(), dest.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                    dataCopied++;
                }
            }

            // Copy metadata JSON files
            for (File f : fh.getFileListing(new File(newMetaDir))) {
                if (!f.isFile() || !f.getName().endsWith("_meta.json")) continue;
                File dest = new File(FULL_META_DIR, f.getName());
                if (!dest.exists()) {
                    Files.copy(f.toPath(), dest.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                    metaCopied++;
                }
            }

            log("Persist complete.  Text files copied: " + dataCopied
                    + "  Metadata files copied: " + metaCopied);
            log("FULL_DATA_DIR : " + FULL_DATA_DIR);
            log("FULL_META_DIR : " + FULL_META_DIR);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private DocumentMetadata loadOrCreateMetadata(JSONProcessor jp, String stem) {
        // Try to find a seed metadata JSON written by the source processor
        // Filename conventions: stem + ".json" or stem + "_meta.json"
        String[] candidates = {
            newMetaDir + stem + "_meta.json",
            newMetaDir + stem + ".json"
        };
        for (String path : candidates) {
            File f = new File(path);
            if (f.exists()) {
                try {
                    JSONObject raw = jp.readJSON(f);
                    // First try full schema, then legacy format
                    if (raw.containsKey("Identifier")) {
                        return DocumentMetadata.fromJson(raw);
                    } else {
                        DocumentMetadata m = new DocumentMetadata(stem);
                        m.mergeLegacy(raw);
                        return m;
                    }
                } catch (Exception e) {
                    log("[WARN] Could not parse metadata JSON for " + stem + ": " + e.getMessage());
                }
            }
        }
        // Fall back to empty skeleton
        return new DocumentMetadata(stem);
    }

    private void writeMetadata(DocumentMetadata meta, String outPath) throws Exception {
        JSONObject json = meta.toJson();
        try (Writer w = new OutputStreamWriter(
                new FileOutputStream(outPath), StandardCharsets.UTF_8)) {
            json.writeJSONString(w);
        }
    }

    private int countParagraphs(File txtFile) {
        if (!txtFile.exists()) return 0;
        int count = 0;
        boolean inPara = false;
        try (Scanner sc = new Scanner(txtFile, StandardCharsets.UTF_8)) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                if (line.isBlank()) {
                    inPara = false;
                } else {
                    if (!inPara) { count++; inPara = true; }
                }
            }
        } catch (Exception e) { /* ignored */ }
        return Math.max(count, 1);
    }

    private BiasAnalyser buildBiasAnalyser(BulgarianSentenceSplitter splitter) {
        if (biasDictPath == null || biasDictPath.isBlank()) {
            log("[WARN] No bias dictionary path set — bias scoring disabled.");
            return null;
        }
        BiasLexicon lexicon = new BiasLexicon(biasDictPath);
        return new BiasAnalyser(lexicon, splitter);
    }

    private void validateConfig() {
        List<String> missing = new ArrayList<>();
        if (sourceProcessor == null) missing.add("sourceProcessor");
        if (newDataDir  == null || newDataDir.isBlank())  missing.add("newDataDir");
        if (sampleDir   == null || sampleDir.isBlank())   missing.add("sampleDir");
        if (newMetaDir  == null || newMetaDir.isBlank())  missing.add("newMetaDir");
        if (!missing.isEmpty())
            throw new IllegalStateException(
                "Pipeline configuration missing: " + missing);
    }

    private void ensureDirs(String... paths) {
        for (String p : paths) {
            if (p != null) new File(p).mkdirs();
        }
    }

    private void banner(String msg) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  " + msg);
        System.out.println("=".repeat(60));
    }

    private void log(String msg) {
        System.out.println("[Pipeline] " + msg);
    }
}
