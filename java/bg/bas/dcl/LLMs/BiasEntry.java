package bg.bas.dcl.LLMs;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * BiasEntry
 * 
 * TSV column order (0-based, tab-separated):
 *   0  word          — canonical lemma
 *   1  POS           — part of speech  (N, A, V, …)
 *   2  signal        — "true" / "false" : marks identity-group signals
 *   3  biasType      — gender | race_ethnicity | religion | disability | appearance | "" (general)
 *   4  biasValue     — positive | negative | neutral | ""
 *   5  derogatory    — "true" / "false"
 *   6  colloquial    — "true" / "false"
 *   7  forms         — "true" / "false" (unused flag; inflected forms are in col 10)
 *   8  positivity    — double in [0,1]
 *   9  negativity    — double in [0,1]
 *  10  inflectedForms — pipe-separated list of surface forms, or empty
 */
public class BiasEntry {

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final String      word;
    private final String      pos;
    private final boolean     signal;
    private final String      biasType;      // "" means general / not type-specific
    private final String      biasValue;     // "" means unscored
    private final boolean     derogatory;
    private final boolean     colloquial;
    private final double      positivity;
    private final double      negativity;

    /** All known surface forms (lemma + inflected), lowercased for fast lookup. */
    private final Set<String> forms;

    // -----------------------------------------------------------------------
    // Constructor — called by BiasLexicon during TSV loading
    // -----------------------------------------------------------------------

    public BiasEntry(String word, String pos,
                     boolean signal, String biasType, String biasValue,
                     boolean derogatory, boolean colloquial,
                     double positivity, double negativity,
                     Set<String> forms) {
        this.word        = word == null   ? "" : word.trim();
        this.pos         = pos  == null   ? "" : pos.trim();
        this.signal      = signal;
        this.biasType    = biasType   == null ? "" : biasType.trim();
        this.biasValue   = biasValue  == null ? "" : biasValue.trim();
        this.derogatory  = derogatory;
        this.colloquial  = colloquial;
        this.positivity  = positivity;
        this.negativity  = negativity;
        this.forms       = Collections.unmodifiableSet(
                           forms == null ? new HashSet<>() : forms);
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    /** Canonical lemma as it appears in the dictionary. */
    public String getWord()        { return word; }

    /** Part-of-speech tag (N, A, V, …). */
    public String getPos()         { return pos; }

    /**
     * True if this entry marks an identity-group signal word —
     * i.e. a term that identifies a person by a protected attribute
     * (e.g. "жена", "мюсюлманин").
     */
    public boolean isSignal()      { return signal; }

    /**
     * Bias category, or empty string if applicable to all categories.
     * Values: "gender", "race_ethnicity", "religion", "disability", "appearance".
     */
    public String getBiasType()    { return biasType; }

    /**
     * Evaluative polarity of the word in a bias context.
     * Values: "positive", "negative", "neutral", or "" (unscored).
     */
    public String getBiasValue()   { return biasValue; }

    /** True if the word is explicitly marked as derogatory / pejorative. */
    public boolean isDerogatory()  { return derogatory; }

    /** True if the word is marked as colloquial / informal. */
    public boolean isColloquial()  { return colloquial; }

    /**
     * Positivity score in [0, 1] derived from BulNet synset sentiment.
     * Higher = more positive connotation.
     */
    public double getPositivity()  { return positivity; }

    /**
     * Negativity score in [0, 1] derived from BulNet synset sentiment.
     * Higher = more negative connotation.
     */
    public double getNegativity()  { return negativity; }

    /**
     * Unmodifiable set of all surface forms (lemma + inflected variants),
     * stored in lowercase.
     */
    public Set<String> getForms()  { return forms; }

    // -----------------------------------------------------------------------
    // Convenience predicates
    // -----------------------------------------------------------------------

    /** True if this entry carries any evaluative information (non-empty biasValue). */
    public boolean isEvaluative() {
        return !biasValue.isEmpty() && !biasValue.equals("neutral");
    }

    /** True if biasType is non-empty (i.e. assigned to a specific category). */
    public boolean isTyped() {
        return !biasType.isEmpty();
    }

    /**
     * True if this entry can act as an evaluative modifier in a bias pair —
     * i.e. it has a non-neutral polarity, or it is derogatory or colloquial.
     */
    public boolean isEvaluativeModifier() {
        return isEvaluative() || derogatory || colloquial
                || positivity > 0.5 || negativity > 0.5;
    }

    // -----------------------------------------------------------------------
    // Object overrides
    // -----------------------------------------------------------------------

    @Override
    public String toString() {
        return String.format("BiasEntry{word='%s', signal=%b, type='%s', value='%s', "
                + "pos+neg=[%.2f,%.2f], derog=%b, coll=%b, forms=%d}",
                word, signal, biasType, biasValue,
                positivity, negativity, derogatory, colloquial, forms.size());
    }
}
