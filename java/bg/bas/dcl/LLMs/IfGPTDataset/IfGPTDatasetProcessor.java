package bg.bas.dcl.LLMs.IfGPTDataset;

/**
 * IfGPTDatasetProcessor
 *
  
 */
public class IfGPTDatasetProcessor {

    // -----------------------------------------------------------------------
    // Shared paths
    // -----------------------------------------------------------------------

    // New batch being ingested
    static final String NEW_DATA_DIR    = "/home/ivelina/WORK-DCL/IfGPT/NEW_BATCH/data/";
    static final String NEW_META_DIR    = "/home/ivelina/WORK-DCL/IfGPT/NEW_BATCH/metadata/";
    static final String SAMPLE_DIR      = "/home/ivelina/WORK-DCL/IfGPT/NEW_BATCH/sample/";
    static final String BLOCKLIST_FILE  = "/home/ivelina/WORK-DCL/IfGPT/NEW_BATCH/blocklist.txt";
    static final String DEDUP_REPORT    = "/home/ivelina/WORK-DCL/IfGPT/NEW_BATCH/dedup_report.tsv";

    // Shared resources
    static final String BULNC_META_FILE = "/home/ivelina/SVN_CORPUS/BulNC/BulNC-description.txt";
    static final String BIAS_DICT       = "/home/ivelina/WORK-DCL/WIKIPEDIA-BIAS/"
                                        + "bulgarian_bias_dictionary_v4.tsv";

    // -----------------------------------------------------------------------
    // Main
    // -----------------------------------------------------------------------

    public static void main(String[] args) {

        // ==================================================================
        // MODE A — FULL PIPELINE  (one call runs all 8 stages)
        // ==================================================================
        // Choose the source processor that matches the new batch format,
        // then call pipeline.run().

        // --- BulNC Mass Media batch ---
        runBulNCPipeline();

        // --- MARCELL batch ---
        // runMarcellPipeline();

        // --- CURLICAT batch ---
        // runCurlicatPipeline();

        // --- BulNC Wiki/InformalFiction batch ---
        // runBulNCWikiPipeline();


        // ==================================================================
        // MODE B — INDIVIDUAL STAGES
        // ==================================================================

        // --- 1. Extract only ---
        // new BulNCProcessor(BULNC_META_FILE).process(NEW_DATA_DIR, NEW_META_DIR);

        // --- 3. Clean only (learn + apply) ---
        // FileCleanProcessor fcp = new FileCleanProcessor(0.50);
        // fcp.learnFromSample(SAMPLE_DIR);
        // fcp.printTopCommonLines(30);
        // fcp.saveBlocklist(BLOCKLIST_FILE);
        // fcp.cleanDirectory(NEW_DATA_DIR, true);

        // --- 4. Deduplication only ---
        // DeduplicationProcessor dp = new DeduplicationProcessor(0.90, 5, 200);
        // dp.indexCorpus(IfGPTPipeline.FULL_DATA_DIR);
        // dp.detectDuplicates(NEW_DATA_DIR, DEDUP_REPORT);
        // dp.removeDuplicatesFromNewFolder(NEW_DATA_DIR, true); // optional

        // --- 5/6. PII + Bias annotation only (on already-split sentences) ---
        // bg.bas.dcl.LLMs.BulgarianSentenceSplitter splitter =
        //         new bg.bas.dcl.LLMs.BulgarianSentenceSplitter();
        // bg.bas.dcl.LLMs.PIIDetector pii = new bg.bas.dcl.LLMs.PIIDetector(splitter);
        // pii.analyseDirectory(NEW_DATA_DIR, NEW_META_DIR + "pii_report.tsv");
        //
        // bg.bas.dcl.LLMs.BiasLexicon lex =
        //         new bg.bas.dcl.LLMs.BiasLexicon(BIAS_DICT);
        // bg.bas.dcl.LLMs.BiasAnalyser bias =
        //         new bg.bas.dcl.LLMs.BiasAnalyser(lex, splitter);
        // bias.analyseDirectory(NEW_DATA_DIR, NEW_META_DIR + "bias_report.tsv");


        // ==================================================================
        // MODE C — UTILITIES
        // ==================================================================

        // Convert an existing metadata JSON to CSV
        // new MarcellProcessor().convertJsonToCSV(
        //         IfGPTPipeline.FULL_META_DIR + "metadata_BNC_mm.json");
    }

    // -----------------------------------------------------------------------
    // Pipeline factory methods (one per source type)
    // -----------------------------------------------------------------------

    private static void runBulNCPipeline() {
        new IfGPTPipeline()
            .setSourceProcessor(new BulNCProcessor(BULNC_META_FILE))
            .setNewDataDir(NEW_DATA_DIR)
            .setSampleDir(SAMPLE_DIR)
            .setNewMetaDir(NEW_META_DIR)
            .setBlocklistFile(BLOCKLIST_FILE)
            .setDedupReport(DEDUP_REPORT)
            .setBiasDictPath(BIAS_DICT)
            .setBoilerplateThreshold(0.50)
            .setDedupThreshold(0.90)
            .setRemoveDuplicates(false)   // set true to delete dup sentences
            .setKeepBackups(true)
            .run();
    }

    private static void runMarcellPipeline() {
        String indirMarcell = "/home/ivelina/WORK-DCL/ifGPT/CORPORA/MARCELL/bg-annotated/";
        String outdirMarcell= "/home/ivelina/WORK-DCL/ifGPT/CORPORA/MARCELL/texts/";

        new IfGPTPipeline()
            .setSourceProcessor(new MarcellProcessor())
            .setNewDataDir(outdirMarcell)
            .setSampleDir(SAMPLE_DIR)
            .setNewMetaDir(NEW_META_DIR)
            .setBlocklistFile(BLOCKLIST_FILE)
            .setDedupReport(DEDUP_REPORT)
            .setBiasDictPath(BIAS_DICT)
            .setSkipClean(false)
            .setSkipDedup(false)
            .run();
    }

    private static void runCurlicatPipeline() {
        String indirCurlicat = "/home/ivelina/WORK-DCL/ifGPT/CORPORA/CURLICAT/archive/"
                             + "Bulgarian_Curlicat_corpus/";
        String outdirCurlicat= "/home/ivelina/WORK-DCL/ifGPT/CORPORA/CURLICAT/texts/";

        new IfGPTPipeline()
            .setSourceProcessor(new CurlicatProcessor())
            .setNewDataDir(outdirCurlicat)
            .setSampleDir(SAMPLE_DIR)
            .setNewMetaDir(NEW_META_DIR)
            .setBlocklistFile(BLOCKLIST_FILE)
            .setDedupReport(DEDUP_REPORT)
            .setBiasDictPath(BIAS_DICT)
            .run();
    }

    private static void runBulNCWikiPipeline() {
        String existingMeta = IfGPTPipeline.FULL_META_DIR + "metadata_BNC_mm.json";
        String outdirWiki   = "/home/ivelina/WORK-DCL/ifGPT/CORPORA/BulNC/wiki-texts/";

        new IfGPTPipeline()
            .setSourceProcessor(new BulNCWikiProcessor(BULNC_META_FILE, existingMeta))
            .setNewDataDir(outdirWiki)
            .setSampleDir(SAMPLE_DIR)
            .setNewMetaDir(NEW_META_DIR)
            .setBlocklistFile(BLOCKLIST_FILE)
            .setDedupReport(DEDUP_REPORT)
            .setBiasDictPath(BIAS_DICT)
            .run();
    }
}
