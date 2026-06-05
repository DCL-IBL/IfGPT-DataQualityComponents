package bg.bas.dcl.LLMs;

import java.util.List;

/**
 * BiasDetectorDemo
 *
  *
 * -----------------------------------------------------------------------
 * MAVEN DEPENDENCIES (add to pom.xml):
 *
 *   <!-- OpenNLP toolkit -->
 *   <dependency>
 *     <groupId>org.apache.opennlp</groupId>
 *     <artifactId>opennlp-tools</artifactId>
 *     <version>2.4.0</version>
 *   </dependency>
 *
 *   <!-- Bulgarian sentence-detection model (UD 2.14, Apache 2.0) -->
 *   <dependency>
 *     <groupId>org.apache.opennlp</groupId>
 *     <artifactId>opennlp-models-sentdetect-bg</artifactId>
 *     <version>1.2</version>
 *   </dependency>
 */
public class BiasDetectorDemo {

    public static void main(String[] args) {

        // ------------------------------------------------------------------
        // 1. Load the Bulgarian sentence splitter
        //    (loads bundled model from the Maven JAR automatically)
        // ------------------------------------------------------------------
        BulgarianSentenceSplitter splitter = new BulgarianSentenceSplitter();

        // Alternatively, supply an explicit model file path:
        // BulgarianSentenceSplitter splitter =
        //     new BulgarianSentenceSplitter("/path/to/bg-sent.bin");


        // ------------------------------------------------------------------
        // 2. Load the bias lexicon
        // ------------------------------------------------------------------
        String dictPath = "/home/ivelina/WORK-DCL/WIKIPEDIA-BIAS/"
                        + "bulgarian_bias_dictionary_v4.tsv";

        BiasLexicon lexicon = new BiasLexicon(dictPath);
        System.out.printf("Lexicon loaded: %d entries%n%n", lexicon.size());


        // ------------------------------------------------------------------
        // 3. Build the analyser
        // ------------------------------------------------------------------
        BiasAnalyser analyser = new BiasAnalyser(lexicon, splitter);


        // ------------------------------------------------------------------
        // 4a. Analyse a block of text in memory
        // ------------------------------------------------------------------
        String sampleText =
            "Слепите хора трудно могат да се справят сами в живота. " +
            "Времето днес е слънчево и приятно.";

        System.out.println("=== Sentence-level bias scores ===");
        System.out.println(SentenceBiasScore.tsvHeader());
        System.out.println();

        List<SentenceBiasScore> scores = analyser.analyseText(sampleText);

        for (SentenceBiasScore score : scores) {
            System.out.println("Sentence : " + score.getSentence());
            System.out.printf ("Words    : %d%n", score.getTotalWords());
            System.out.printf ("Biased   : %b%n", score.isBiased());

            double[] cov = score.coverageArray();
            String[] types = SentenceBiasScore.BIAS_TYPES;
            for (int i = 0; i < types.length; i++) {
                if (cov[i] > 0)
                    System.out.printf("  %-18s %.2f%% pair coverage%n",
                            types[i] + ":", cov[i] * 100);
            }
            System.out.printf ("Total    : %.2f%% overall coverage%n", score.totalCoverage() * 100);
            System.out.println("Lemmas   : " + score.getMatchedLemmas());
            System.out.println();
        }


        // ------------------------------------------------------------------
        // 4b. Analyse a corpus directory — writes a TSV results file
        //     (only biased sentences are written; zero-coverage sentences
        //     are filtered out automatically by analyseDirectory)
        // ------------------------------------------------------------------
        String corpusDir = "/home/ivelina/WORK-DCL/WIKIPEDIA-BIAS/WIKI/";
        String resultTsv = "/home/ivelina/WORK-DCL/WIKIPEDIA-BIAS/bias_results.tsv";

        // analyser.analyseDirectory(corpusDir, resultTsv);


        // ------------------------------------------------------------------
        // 4c. Sentence splitting only — using the splitter standalone
        // ------------------------------------------------------------------
        String text = "Това е първото изречение. Второто е по-дълго и сложно! " +
                      "А третото задава въпрос?";

        String[] sentences = splitter.split(text);
        System.out.println("=== Sentence splitting demo ===");
        for (int i = 0; i < sentences.length; i++) {
            System.out.printf("  [%d] %s%n", i + 1, sentences[i]);
        }
    }
}
