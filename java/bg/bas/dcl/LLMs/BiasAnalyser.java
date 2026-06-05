package bg.bas.dcl.LLMs;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import bg.bas.dcl.general.FileHandler;

/**
 * BiasAnalyser
 *
 * Detects linguistic bias in Bulgarian text using the Bulgarian Bias Dictionary
 * (v4 TSV format).  Works at sentence level: for each sentence it returns a
 * {@link SentenceBiasScore} whose primary metric is the pair-coverage percentage —
 * the fraction of word tokens in the sentence that participate in at least one
 * signal–evaluator pair for each bias category.
 *
 * -----------------------------------------------------------------------
 * ALGORITHM (per sentence)
 *
 *   1. TOKENISE — split on whitespace, strip non-letter characters per token.
 *   2. MATCH    — look each token up in the {@link BiasLexicon} (form index,
 *                 case-insensitive).  Multi-word entries are tried first via a
 *                 forward-scan for bigrams and trigrams.
 *   3. PAIR     — for every signal token, search within ±PAIR_WINDOW tokens for
 *                 an evaluator token of the same bias type (or a general one).
 *                 Each unique (signal position, evaluator position) is a pair.
 *   4. SCORE    — pairCoverage[type] = distinctPairTokens[type] / totalWords
 *                 where distinctPairTokens = set of positions involved in
 *                 at least one confirmed pair for that type.
 *
  
 */
public class BiasAnalyser {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    /**
     * Maximum token distance between a signal and an evaluator for them to
     * be counted as a pair.  10 matches the window used in the original
     * BiasDetector.
     */
    public static final int PAIR_WINDOW = 10;

    /**
     * Sentences with fewer words than this are skipped entirely.
     */
    public static final int MIN_WORDS = 6;

    /**
     * Sentences with more words than this are still processed but a warning
     * is printed (very long sentences may inflate scores).
     */
    public static final int MAX_WORDS = 200;

    // -----------------------------------------------------------------------
    // Dependencies
    // -----------------------------------------------------------------------

    private final BiasLexicon              lexicon;
    private final BulgarianSentenceSplitter splitter;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * @param lexicon  the loaded bias dictionary
     * @param splitter an initialised Bulgarian sentence splitter
     */
    public BiasAnalyser(BiasLexicon lexicon, BulgarianSentenceSplitter splitter) {
        if (lexicon  == null) throw new IllegalArgumentException("lexicon must not be null");
        if (splitter == null) throw new IllegalArgumentException("splitter must not be null");
        this.lexicon  = lexicon;
        this.splitter = splitter;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Splits {@code text} into sentences and returns a bias score for each. 
     */
    public List<SentenceBiasScore> analyseText(String text) {
        List<SentenceBiasScore> results = new ArrayList<>();
        if (text == null || text.isBlank()) return results;

        for (String sentence : splitter.split(text)) {
            results.add(analyseSentence(sentence));
        }
        return results;
    }

    /**
     * Analyses a single pre-split sentence.
     * 
     */
    public SentenceBiasScore analyseSentence(String sentence) {
        // --- Tokenise --------------------------------------------------
        String lower        = sentence.toLowerCase();
        String[] rawTokens  = lower.split("\\s+");

        // Build clean token list and a parallel lookup list
        // We attempt multi-word matches (bigrams, trigrams) first
        List<String>    cleanTokens = new ArrayList<>();   // word-only tokens
        List<BiasEntry> matched     = new ArrayList<>();   // parallel match (null=no match)

        int i = 0;
        while (i < rawTokens.length) {
            // Try trigram (3-word multi-word entry)
            if (i + 2 < rawTokens.length) {
                String tri = clean(rawTokens[i]) + " "
                           + clean(rawTokens[i + 1]) + " "
                           + clean(rawTokens[i + 2]);
                BiasEntry e = lexicon.lookup(tri);
                if (e != null) {
                    // Represent as 3 tokens (positions), all pointing to same entry
                    for (int k = 0; k < 3; k++) {
                        cleanTokens.add(clean(rawTokens[i + k]));
                        matched.add(e);
                    }
                    i += 3;
                    continue;
                }
            }
            // Try bigram
            if (i + 1 < rawTokens.length) {
                String bi = clean(rawTokens[i]) + " " + clean(rawTokens[i + 1]);
                BiasEntry e = lexicon.lookup(bi);
                if (e != null) {
                    for (int k = 0; k < 2; k++) {
                        cleanTokens.add(clean(rawTokens[i + k]));
                        matched.add(e);
                    }
                    i += 2;
                    continue;
                }
            }
            // Unigram
            String tok = clean(rawTokens[i]);
            if (!tok.isEmpty()) {
                cleanTokens.add(tok);
                matched.add(lexicon.lookup(tok));
            }
            i++;
        }

        int totalWords = cleanTokens.size();
 
        String[] biasTypes = SentenceBiasScore.BIAS_TYPES;

        Map<String, Integer> signalCount    = new HashMap<>();
        Map<String, Integer> evaluatorCount = new HashMap<>();
        Map<String, Double>  pairCoverage   = new HashMap<>();

        for (String type : biasTypes) {
            signalCount.put(type, 0);
            evaluatorCount.put(type, 0);
            pairCoverage.put(type, 0.0);
        }

        List<String> matchedLemmas = new ArrayList<>();
        int totalBiasWords  = 0;
        int totalDerogatory = 0;
        int totalColloquial = 0;

        if (totalWords < MIN_WORDS) {
            // Return zero-score result for very short sentences
            return new SentenceBiasScore(sentence, totalWords,
                    pairCoverage, signalCount, evaluatorCount,
                    matchedLemmas, 0, 0, 0, false);
        }

        // --- Collect matched positions ---------------------------------
        Set<String> seenLemmas = new HashSet<>();

        // signalPositions[type] = list of token indices that are signals for that type
        Map<String, List<Integer>> signalPos  = new HashMap<>();
        // evalPositions[type]   = list of token indices that are evaluators for that type
        Map<String, List<Integer>> evalPos    = new HashMap<>();

        for (String type : biasTypes) {
            signalPos.put(type, new ArrayList<>());
            evalPos.put(type,   new ArrayList<>());
        }

        for (int ti = 0; ti < totalWords; ti++) {
            BiasEntry entry = matched.get(ti);
            if (entry == null) continue;

            String lemma = entry.getWord();

            // Count each unique lemma only once (avoid double-counting
            // inflected-form repetitions of the same word in one sentence)
            if (seenLemmas.add(lemma)) {
                matchedLemmas.add(lemma);
            }

            if (entry.isEvaluative()) totalBiasWords++;
            if (entry.isDerogatory()) totalDerogatory++;
            if (entry.isColloquial()) totalColloquial++;

            // Determine which types this entry applies to
            List<String> applicableTypes = entry.isTyped()
                    ? List.of(entry.getBiasType())
                    : Arrays.asList(biasTypes);    // general entry → all types

            for (String type : applicableTypes) {
                if (entry.isSignal()) {
                    signalPos.get(type).add(ti);
                }
                if (entry.isEvaluativeModifier()) {
                    evalPos.get(type).add(ti);
                }
            }
        }

        // --- Pair detection & score computation ----------------------- 
        Map<String, Set<Integer>> pairTokens = new HashMap<>();
        for (String type : biasTypes) pairTokens.put(type, new HashSet<>());

        for (String type : biasTypes) {
            List<Integer> signals    = signalPos.get(type);
            List<Integer> evaluators = evalPos.get(type);

            for (int sIdx : signals) {
                boolean paired = false;

                // Self-pair: signal is itself evaluative
                BiasEntry sEntry = matched.get(sIdx);
                if (sEntry != null && sEntry.isEvaluativeModifier()) {
                    pairTokens.get(type).add(sIdx);
                    paired = true;
                }

                // Pair with a distinct evaluator within window
                for (int eIdx : evaluators) {
                    if (eIdx == sIdx) continue;
                    if (Math.abs(sIdx - eIdx) <= PAIR_WINDOW) {
                        pairTokens.get(type).add(sIdx);
                        pairTokens.get(type).add(eIdx);
                        paired = true;
                    }
                }
            }

            int sigCount  = signals.size();
            int evalCount = (int) evaluators.stream()
                    .filter(eIdx -> pairTokens.get(type).contains(eIdx))
                    .count();

            signalCount.put(type,    sigCount);
            evaluatorCount.put(type, evalCount);
 
            double coverage = totalWords > 0
                    ? (double) pairTokens.get(type).size() / totalWords
                    : 0.0;
            pairCoverage.put(type, coverage);
        }

        // --- Multi-type flag ------------------------------------------
        int typesWithPairs = 0;
        for (String type : biasTypes)
            if (!pairTokens.get(type).isEmpty()) typesWithPairs++;
        boolean multiType = typesWithPairs >= 2;

        return new SentenceBiasScore(
                sentence, totalWords,
                pairCoverage, signalCount, evaluatorCount,
                matchedLemmas, totalBiasWords, totalDerogatory, totalColloquial,
                multiType);
    }

    

    /**
     * Analyses all .txt files  
     */
    public void analyseDirectory(String corpusDir, String resultPath) {
        try {
            FileHandler fh = new FileHandler();

            try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(resultPath, false), StandardCharsets.UTF_8))) {

                bw.write(SentenceBiasScore.tsvHeader());
                bw.newLine();

                int filesProcessed = 0;
                int sentencesWritten = 0;

                for (File f : fh.getFileListing(new File(corpusDir))) {
                    if (!f.isFile() || !f.getName().endsWith(".txt")) continue;

                    System.out.println("[BiasAnalyser] Processing: " + f.getName());

                    StringBuilder text = new StringBuilder();
                    try (Scanner sc = new Scanner(f, StandardCharsets.UTF_8)) {
                        while (sc.hasNextLine()) {
                            text.append(sc.nextLine()).append(' ');
                        }
                    }

                    for (SentenceBiasScore score : analyseText(text.toString())) {
                        if (score.isBiased()) {
                            bw.write(f.getName() + "\t" + score.toTsv());
                            bw.newLine();
                            sentencesWritten++;
                        }
                    }
                    filesProcessed++;
                }

                System.out.printf("[BiasAnalyser] Done. Files: %d  Biased sentences written: %d%n",
                        filesProcessed, sentencesWritten);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

   
    private String clean(String token) {
        return token.replaceAll("[^\\p{L}\\s]", "").trim();
    }
}
