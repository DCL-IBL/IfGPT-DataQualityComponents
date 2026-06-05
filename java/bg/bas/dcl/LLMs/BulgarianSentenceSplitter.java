package bg.bas.dcl.LLMs;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;

/**
 * BulgarianSentenceSplitter
 *
 * Wraps the Apache OpenNLP sentence detection model for Bulgarian, providing
 * a clean, reusable API for all other pipeline components.
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
 *   <!-- Bulgarian sentence-detection model (UD-based, Apache 2.0) -->
 *   <dependency>
 *     <groupId>org.apache.opennlp</groupId>
 *     <artifactId>opennlp-models-sentdetect-bg</artifactId>
 *     <version>1.2</version>
 *   </dependency>
 *
 * The model JAR bundles the binary model at:
 *   opennlp/models/sentdetect/bg-ud-ewt-sentence-detector.bin
 * You can also supply an external model file via the two-argument constructor.
 *
 * ------------------------------------------------- 
 */
public class BulgarianSentenceSplitter {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    /**
     * Classpath location of the bundled Bulgarian sentence-detection model.
     * Matches the path inside the opennlp-models-sentdetect-bg JAR.
     */
    private static final String BUNDLED_MODEL_PATH =
            "opennlp/models/sentdetect/bg-ud-ewt-sentence-detector.bin";

    /**
     * Minimum character length for a string to be considered a valid sentence.
     * Shorter strings are returned as-is without splitting.
     */
    private static final int MIN_TEXT_LENGTH = 5;

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------

    private final SentenceDetectorME detector;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    /**
     * Loads the Bulgarian sentence-detection model from the bundled Maven JAR.
     * Requires the opennlp-models-sentdetect-bg artifact on the classpath.
     *
     * @throws RuntimeException if the model cannot be loaded
     */
    public BulgarianSentenceSplitter() {
        this(null);
    }

    /**
     * Loads the Bulgarian sentence-detection model.
     *
     * @param modelPath absolute path to a .bin OpenNLP sentence-detection model,
     *                  or {@code null} / empty string to load from the classpath JAR
     * @throws RuntimeException if the model cannot be loaded
     */
    public BulgarianSentenceSplitter(String modelPath) {
        try {
            InputStream stream;

            if (modelPath == null || modelPath.isBlank()) {
                // Load from the bundled JAR on the classpath
                stream = getClass().getClassLoader()
                        .getResourceAsStream(BUNDLED_MODEL_PATH);
                if (stream == null) {
                    throw new IllegalStateException(
                            "Bulgarian sentence model not found .");
                }
                System.out.println("[SentenceSplitter] Loaded bundled model: " + BUNDLED_MODEL_PATH);
            } else {
                File f = new File(modelPath);
                if (!f.exists())
                    throw new IllegalArgumentException(
                            "Sentence model file not found: " + modelPath);
                stream = new FileInputStream(f);
                System.out.println("[SentenceSplitter] Loaded external model: " + modelPath);
            }

            SentenceModel model = new SentenceModel(stream);
            stream.close();
            detector = new SentenceDetectorME(model);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load Bulgarian sentence model", e);
        }
    }

    // -----------------------------------------------------------------------
    // Core API
    // -----------------------------------------------------------------------

     
    public String[] split(String text) {
        if (text == null) return new String[0];
        String trimmed = text.trim();
        if (trimmed.length() < MIN_TEXT_LENGTH) {
            return trimmed.isEmpty() ? new String[0] : new String[]{trimmed};
        }
        return detector.sentDetect(trimmed);
    }

    
    public List<String> splitToList(String text) {
        return new ArrayList<>(Arrays.asList(split(text)));
    }

     
    public List<String> splitParagraphs(String[] paragraphs) {
        List<String> all = new ArrayList<>();
        if (paragraphs == null) return all;
        for (String para : paragraphs) {
            if (para != null && !para.isBlank())
                all.addAll(splitToList(para));
        }
        return all;
    }

     
    public double[] getSentenceProbabilities() {
        return detector.getSentenceProbabilities();
    }
 
     
    public List<String> splitAndFilter(String text, int minWords) {
        List<String> result = new ArrayList<>();
        for (String sent : split(text)) {
            if (sent.split("\\s+").length >= minWords)
                result.add(sent);
        }
        return result;
    }
}
