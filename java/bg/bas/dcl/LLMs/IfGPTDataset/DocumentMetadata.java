package bg.bas.dcl.LLMs.IfGPTDataset;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * DocumentMetadata
 *
 * Canonical in-memory representation of the ifGPT dataset metadata schema.
  
 */
@SuppressWarnings("unchecked")
public class DocumentMetadata {

    // -----------------------------------------------------------------------
    // ── MANDATORY (15) ──────────────────────────────────────────────────────
    // -----------------------------------------------------------------------

    /** Unique document identifier with the language prefix "bg". */
    private String       identifier        = "";

    /** Licence name (open, restricted, …). */
    private String       licence           = "";

    /** Publication date yyyy-mm-dd. */
    private String       publicationDate   = "";

    /** Title of the document. */
    private String       documentTitle     = "";

    /** Publishing organisation / media outlet / institutional originator. */
    private String       source            = "";

    /** Modality: "textual" | "multimodal". */
    private String       medium            = "textual";

    /** Original web address. */
    private String       url               = "";

    /** Up to six subject-area labels from a controlled vocabulary. */
    private List<String> domain            = new ArrayList<>();

    /** Up to six free-text keywords. */
    private List<String> keywords          = new ArrayList<>();

    /** Total word count (non-punctuation tokens). */
    private int          numberWords       = 0;

    /** Total sentence count. */
    private int          numberSentences   = 0;

    /** Total paragraph count. */
    private int          numberParagraphs  = 0;

    /** Total token count (words + punctuation). */
    private int          numberTokens      = 0;

    /**
     * Per-sentence PII coverage vector.
     * Entry i = proportion of tokens in sentence i flagged as PII ∈ [0,1].
     * Length == numberSentences after pipeline completion.
     */
    private List<Double> piiVector         = new ArrayList<>();

    /**
     * Per-sentence bias coverage vector.
     * Entry i = proportion of tokens in sentence i flagged as biased ∈ [0,1].
     * Length == numberSentences after pipeline completion.
     */
    private List<Double> biasVector        = new ArrayList<>();

    // -----------------------------------------------------------------------
    // ── OPTIONAL (8) ────────────────────────────────────────────────────────
    // -----------------------------------------------------------------------

    /** Name(s) of the author(s). */
    private List<String> author            = new ArrayList<>();

    /** Stylistic register: legal | journalistic | administrative | … */
    private String       style             = "";

    /** Document genre: book | document | article | … */
    private String       type              = "";

    /** Narrower thematic classification, hierarchically linked to Domain. */
    private List<String> subdomain         = new ArrayList<>();

    /** true = translation, false = original Bulgarian text. */
    private Boolean      translatedDocument = null;  // null = unknown

    /** Date of acquisition yyyy-mm-dd. */
    private String       collectionDate    = "";

    /** URL of the licence text. */
    private String       licenceLink       = "";

    /** Anticipated NLP applications from a predefined list. */
    private List<String> taskCategories    = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public DocumentMetadata() {}

    public DocumentMetadata(String identifier) {
        this.identifier = identifier;
    }

    // -----------------------------------------------------------------------
    // Fluent setters — mandatory
    // -----------------------------------------------------------------------

    public DocumentMetadata setIdentifier(String v)       { identifier       = v; return this; }
    public DocumentMetadata setLicence(String v)          { licence          = v; return this; }
    public DocumentMetadata setPublicationDate(String v)  { publicationDate  = v; return this; }
    public DocumentMetadata setDocumentTitle(String v)    { documentTitle    = v; return this; }
    public DocumentMetadata setSource(String v)           { source           = v; return this; }
    public DocumentMetadata setMedium(String v)           { medium           = v; return this; }
    public DocumentMetadata setUrl(String v)              { url              = v; return this; }
    public DocumentMetadata setDomain(List<String> v)     { domain           = v != null ? v : new ArrayList<>(); return this; }
    public DocumentMetadata addDomain(String v)           { domain.add(v); return this; }
    public DocumentMetadata setKeywords(List<String> v)   { keywords         = v != null ? v : new ArrayList<>(); return this; }
    public DocumentMetadata addKeyword(String v)          { keywords.add(v); return this; }
    public DocumentMetadata setNumberWords(int v)         { numberWords      = v; return this; }
    public DocumentMetadata setNumberSentences(int v)     { numberSentences  = v; return this; }
    public DocumentMetadata setNumberParagraphs(int v)    { numberParagraphs = v; return this; }
    public DocumentMetadata setNumberTokens(int v)        { numberTokens     = v; return this; }
    public DocumentMetadata setPiiVector(List<Double> v)  { piiVector        = v != null ? v : new ArrayList<>(); return this; }
    public DocumentMetadata setBiasVector(List<Double> v) { biasVector       = v != null ? v : new ArrayList<>(); return this; }

    // Fluent setters — optional
    public DocumentMetadata setAuthor(List<String> v)          { author            = v != null ? v : new ArrayList<>(); return this; }
    public DocumentMetadata addAuthor(String v)                { author.add(v); return this; }
    public DocumentMetadata setStyle(String v)                 { style             = v; return this; }
    public DocumentMetadata setType(String v)                  { type              = v; return this; }
    public DocumentMetadata setSubdomain(List<String> v)       { subdomain         = v != null ? v : new ArrayList<>(); return this; }
    public DocumentMetadata addSubdomain(String v)             { subdomain.add(v); return this; }
    public DocumentMetadata setTranslatedDocument(Boolean v)   { translatedDocument= v; return this; }
    public DocumentMetadata setCollectionDate(String v)        { collectionDate    = v; return this; }
    public DocumentMetadata setLicenceLink(String v)           { licenceLink       = v; return this; }
    public DocumentMetadata setTaskCategories(List<String> v)  { taskCategories    = v != null ? v : new ArrayList<>(); return this; }
    public DocumentMetadata addTaskCategory(String v)          { taskCategories.add(v); return this; }

    // -----------------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------------

    public String       getIdentifier()        { return identifier; }
    public String       getLicence()           { return licence; }
    public String       getPublicationDate()   { return publicationDate; }
    public String       getDocumentTitle()     { return documentTitle; }
    public String       getSource()            { return source; }
    public String       getMedium()            { return medium; }
    public String       getUrl()               { return url; }
    public List<String> getDomain()            { return Collections.unmodifiableList(domain); }
    public List<String> getKeywords()          { return Collections.unmodifiableList(keywords); }
    public int          getNumberWords()       { return numberWords; }
    public int          getNumberSentences()   { return numberSentences; }
    public int          getNumberParagraphs()  { return numberParagraphs; }
    public int          getNumberTokens()      { return numberTokens; }
    public List<Double> getPiiVector()         { return Collections.unmodifiableList(piiVector); }
    public List<Double> getBiasVector()        { return Collections.unmodifiableList(biasVector); }

    public List<String> getAuthor()            { return Collections.unmodifiableList(author); }
    public String       getStyle()             { return style; }
    public String       getType()              { return type; }
    public List<String> getSubdomain()         { return Collections.unmodifiableList(subdomain); }
    public Boolean      getTranslatedDocument(){ return translatedDocument; }
    public String       getCollectionDate()    { return collectionDate; }
    public String       getLicenceLink()       { return licenceLink; }
    public List<String> getTaskCategories()    { return Collections.unmodifiableList(taskCategories); }

    // -----------------------------------------------------------------------
    // Validation
    // -----------------------------------------------------------------------

    /**
     * Returns a list of missing mandatory fields.
     * An empty list means the record is complete.
     */
    public List<String> missingMandatoryFields() {
        List<String> missing = new ArrayList<>();
        if (identifier.isBlank())       missing.add("Identifier");
        if (licence.isBlank())          missing.add("Licence");
        if (medium.isBlank())           missing.add("Medium");
        if (numberWords == 0)           missing.add("NumberWords");
        if (numberSentences == 0)       missing.add("NumberSentences");
        if (numberParagraphs == 0)      missing.add("NumberParagraphs");
        if (numberTokens == 0)          missing.add("NumberTokens");
        // piiVector and biasVector may legitimately be empty for clean docs
        return missing;
    }

    // -----------------------------------------------------------------------
    // JSON serialisation  (json-simple)
    // -----------------------------------------------------------------------

    /** Serialises this record to a json-simple JSONObject. */
    public JSONObject toJson() {
        JSONObject o = new JSONObject();

        // Mandatory
        o.put("Identifier",                       identifier);
        o.put("Licence",                          licence);
        o.put("PublicationDate",                  publicationDate);
        o.put("DocumentTitle",                    documentTitle);
        o.put("Source",                           source);
        o.put("Medium",                           medium);
        o.put("Url",                              url);
        o.put("Domain",                           toJsonArray(domain));
        o.put("Keywords",                         toJsonArray(keywords));
        o.put("NumberWords",                      numberWords);
        o.put("NumberSentences",                  numberSentences);
        o.put("NumberParagraphs",                 numberParagraphs);
        o.put("NumberTokens",                     numberTokens);
        o.put("PersonallyIdentifiableInformation",toJsonDoubleArray(piiVector));
        o.put("BiasedInformation",                toJsonDoubleArray(biasVector));

        // Optional
        o.put("Author",            toJsonArray(author));
        o.put("Style",             style);
        o.put("Type",              type);
        o.put("Subdomain",         toJsonArray(subdomain));
        o.put("TranslatedDocument",
              translatedDocument == null ? "" : translatedDocument.toString());
        o.put("CollectionDate",    collectionDate);
        o.put("LicenceLink",       licenceLink);
        o.put("TaskCategories",    toJsonArray(taskCategories));

        return o;
    }

    /**
     * Populates a DocumentMetadata from a json-simple JSONObject previously
     * produced by {@link #toJson()}.
     */
    public static DocumentMetadata fromJson(JSONObject o) {
        DocumentMetadata m = new DocumentMetadata();

        m.identifier        = str(o, "Identifier");
        m.licence           = str(o, "Licence");
        m.publicationDate   = str(o, "PublicationDate");
        m.documentTitle     = str(o, "DocumentTitle");
        m.source            = str(o, "Source");
        m.medium            = str(o, "Medium");
        m.url               = str(o, "Url");
        m.domain            = strList(o, "Domain");
        m.keywords          = strList(o, "Keywords");
        m.numberWords       = intVal(o, "NumberWords");
        m.numberSentences   = intVal(o, "NumberSentences");
        m.numberParagraphs  = intVal(o, "NumberParagraphs");
        m.numberTokens      = intVal(o, "NumberTokens");
        m.piiVector         = doubleList(o, "PersonallyIdentifiableInformation");
        m.biasVector        = doubleList(o, "BiasedInformation");

        m.author            = strList(o, "Author");
        m.style             = str(o, "Style");
        m.type              = str(o, "Type");
        m.subdomain         = strList(o, "Subdomain");
        String td           = str(o, "TranslatedDocument");
        m.translatedDocument= td.isBlank() ? null : Boolean.parseBoolean(td);
        m.collectionDate    = str(o, "CollectionDate");
        m.licenceLink       = str(o, "LicenceLink");
        m.taskCategories    = strList(o, "TaskCategories");

        return m;
    }

    // -----------------------------------------------------------------------
    // Interop with legacy JSONObject format (used by source processors)
    // -----------------------------------------------------------------------

    /**
     * Merges fields from a legacy source-processor JSONObject (the format
     * produced by MarcellProcessor, BulNCProcessor, etc.) into this record.
     * Fields already set on {@code this} are NOT overwritten.
     */
    public void mergeLegacy(JSONObject legacy) {
        if (identifier.isBlank())      setIdentifier(str(legacy, "Identifier"));
        if (licence.isBlank())         setLicence(str(legacy, "Licence"));
        if (licenceLink.isBlank())     setLicenceLink(str(legacy, "LicenceLink"));
        if (publicationDate.isBlank()) setPublicationDate(str(legacy, "PublicationDate"));
        if (documentTitle.isBlank())   setDocumentTitle(str(legacy, "DocumentTitle"));
        if (source.isBlank())          setSource(str(legacy, "Source"));
        if (url.isBlank())             setUrl(str(legacy, "Url"));
        if (style.isBlank())           setStyle(str(legacy, "Style"));
        if (type.isBlank())            setType(str(legacy, "Type"));
        if (collectionDate.isBlank())  setCollectionDate(str(legacy, "CollectionDate"));

        if (author.isEmpty()) {
            String a = str(legacy, "Author");
            if (!a.isBlank()) author.add(a);
        }
        if (domain.isEmpty()) {
            String d = str(legacy, "Domain");
            if (!d.isBlank()) domain.add(d);
        }
        if (subdomain.isEmpty()) {
            String s = str(legacy, "Subdomain");
            if (!s.isBlank()) subdomain.add(s);
        }
        if (numberWords      == 0) numberWords      = intVal(legacy, "NumberWords");
        if (numberSentences  == 0) numberSentences  = intVal(legacy, "NumberSentences");
        if (numberParagraphs == 0) numberParagraphs = intVal(legacy, "NumberParagraphs");
        if (numberTokens     == 0) numberTokens     = intVal(legacy, "NumberTokens");

        String translated = str(legacy, "TranslatedDocument");
        if (translatedDocument == null && !translated.isBlank())
            translatedDocument = Boolean.parseBoolean(translated);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static String str(JSONObject o, String key) {
        Object v = o.get(key);
        return v == null ? "" : v.toString().trim();
    }

    private static int intVal(JSONObject o, String key) {
        Object v = o.get(key);
        if (v == null) return 0;
        try { return Integer.parseInt(v.toString().trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    private static List<String> strList(JSONObject o, String key) {
        Object v = o.get(key);
        List<String> list = new ArrayList<>();
        if (v instanceof JSONArray) {
            for (Object item : (JSONArray) v)
                if (item != null) list.add(item.toString());
        } else if (v != null && !v.toString().isBlank()) {
            list.add(v.toString().trim());
        }
        return list;
    }

    private static List<Double> doubleList(JSONObject o, String key) {
        Object v = o.get(key);
        List<Double> list = new ArrayList<>();
        if (v instanceof JSONArray) {
            for (Object item : (JSONArray) v) {
                try { list.add(Double.parseDouble(item.toString())); }
                catch (NumberFormatException ignored) {}
            }
        }
        return list;
    }

    private JSONArray toJsonArray(List<String> list) {
        JSONArray a = new JSONArray();
        if (list != null) a.addAll(list);
        return a;
    }

    private JSONArray toJsonDoubleArray(List<Double> list) {
        JSONArray a = new JSONArray();
        if (list != null) a.addAll(list);
        return a;
    }

    @Override
    public String toString() {
        return String.format(
            "DocumentMetadata{id='%s', sentences=%d, words=%d, piiEntries=%d, biasEntries=%d}",
            identifier, numberSentences, numberWords, piiVector.size(), biasVector.size());
    }
}
