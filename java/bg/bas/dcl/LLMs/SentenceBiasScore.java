package bg.bas.dcl.LLMs;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * SentenceBiasScore
 *
 */
public class SentenceBiasScore {

       public static final String[] BIAS_TYPES = {
        "gender", "race_ethnicity", "religion", "disability", "appearance"
    };

  
     private final String sentence;

      private final int totalWords;

   
    private final Map<String, Double> pairCoverage;

     
    private final Map<String, Integer> signalCount;

     
    private final Map<String, Integer> evaluatorCount;

    
    /** All dictionary entries matched in this sentence (lemma strings). */
    private final List<String> matchedLemmas;

    /** Total matched bias words (evaluative, non-neutral). */
    private final int totalBiasWords;

    /** Count of matched derogatory terms. */
    private final int totalDerogatory;

    /** Count of matched colloquial terms. */
    private final int totalColloquial;

     
    private final boolean multiType;

     
    SentenceBiasScore(String sentence,
                      int totalWords,
                      Map<String, Double>  pairCoverage,
                      Map<String, Integer> signalCount,
                      Map<String, Integer> evaluatorCount,
                      List<String>         matchedLemmas,
                      int totalBiasWords,
                      int totalDerogatory,
                      int totalColloquial,
                      boolean multiType) {
        this.sentence       = sentence;
        this.totalWords     = totalWords;
        this.pairCoverage   = Collections.unmodifiableMap(pairCoverage);
        this.signalCount    = Collections.unmodifiableMap(signalCount);
        this.evaluatorCount = Collections.unmodifiableMap(evaluatorCount);
        this.matchedLemmas  = Collections.unmodifiableList(matchedLemmas);
        this.totalBiasWords = totalBiasWords;
        this.totalDerogatory= totalDerogatory;
        this.totalColloquial= totalColloquial;
        this.multiType      = multiType;
    }

  
    public double getPairCoverage(String biasType) {
        if (biasType == null || biasType.isBlank()) return totalCoverage();
        return pairCoverage.getOrDefault(biasType.toLowerCase(), 0.0);
    }

     
    public double totalCoverage() {
        double sum = 0;
        for (double v : pairCoverage.values()) sum += v;
        return sum;
    }

    
    public double[] coverageArray() {
        double[] arr = new double[BIAS_TYPES.length];
        for (int i = 0; i < BIAS_TYPES.length; i++)
            arr[i] = getPairCoverage(BIAS_TYPES[i]);
        return arr;
    }

    /** True if any bias type has a non-zero pair-coverage score. */
    public boolean isBiased() {
        for (double v : pairCoverage.values())
            if (v > 0) return true;
        return false;
    }

     
    public String      getSentence()                        { return sentence; }
    public int         getTotalWords()                      { return totalWords; }
    public int         getSignalCount(String type)          { return signalCount.getOrDefault(type, 0); }
    public int         getEvaluatorCount(String type)       { return evaluatorCount.getOrDefault(type, 0); }
    public List<String>getMatchedLemmas()                   { return matchedLemmas; }
    public int         getTotalBiasWords()                  { return totalBiasWords; }
    public int         getTotalDerogatory()                 { return totalDerogatory; }
    public int         getTotalColloquial()                 { return totalColloquial; }
    public boolean     isMultiType()                        { return multiType; }

  
    public String toTsv() {
        StringBuilder sb = new StringBuilder();
        sb.append(sentence).append('\t');
        sb.append(totalWords).append('\t');
        sb.append(matchedLemmas).append('\t');

        for (String type : BIAS_TYPES) {
            sb.append(signalCount.getOrDefault(type, 0)).append('\t');
            sb.append(evaluatorCount.getOrDefault(type, 0)).append('\t');
            sb.append(String.format("%.4f", getPairCoverage(type))).append('\t');
        }

        sb.append(totalBiasWords).append('\t');
        sb.append(totalDerogatory).append('\t');
        sb.append(totalColloquial).append('\t');
        sb.append(multiType ? 1 : 0).append('\t');
        sb.append(String.format("%.4f", totalCoverage()));

        return sb.toString();
    }

 
    public static String tsvHeader() {
        StringBuilder sb = new StringBuilder();
        sb.append("sentence\ttotalWords\tmatchedLemmas\t");
        for (String type : BIAS_TYPES)
            sb.append(type).append("_signals\t")
              .append(type).append("_evaluators\t")
              .append(type).append("_coverage\t");
        sb.append("totalBiasWords\ttotalDerogatory\ttotalColloquial\t")
          .append("multiType\ttotalCoverage");
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("SentenceBiasScore{words=%d, coverage=%.3f, biased=%b, sentence='%s'}",
                totalWords, totalCoverage(), isBiased(),
                sentence.length() > 80 ? sentence.substring(0, 80) + "…" : sentence);
    }
}
