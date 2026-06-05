package bg.bas.dcl.LLMs.IfGPTDataset;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Scanner;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import bg.bas.dcl.monolingual.bg.TextProcessor;

/**
 * Processes the Bulgarian National Corpus (BulNC) — general subcorpora.
 *
 * Unlike MARCELL/CURLICAT, BulNC metadata is supplied via an external
 * tab-separated description file (BulNC-description.txt) rather than
 * inline CoNLL-UP comments.  Plain-text source files are read directly.
 *
 * Subcorpora included (controlled by {@link #isIncluded}):
 *   A-Administrative, B-Science, C-MassMedia, D-Fiction
 *   (edit the method to adjust the filter)
 *
 * SETimes articles are excluded regardless of subcorpus.
 *
 * Licence rules:
 *   A-Administrative → CC0
 *   B-Science        → Restricted
 *   C-MassMedia      → Restricted
 *   D-Fiction        → Restricted
 *
 * Description file column indices (0-based):
 *   0  filename stem  |  1  relative path  |  2  collection date
 *   4  author         |  8  title          |  9  publication date
 *   12 url            |  13 translated     |  17 type
 *   19 domain         |  21 subdomain (optional)
 */
public class BulNCProcessor extends BaseSourceProcessor {

    private static final String CC0_LICENCE      = "CC0";
    private static final String CC0_LICENCE_LINK =
            "https://creativecommons.org/public-domain/cc0/";
    private static final String RESTRICTED = "Restricted";

    private final String metaFilePath; // path to BulNC-description.txt
    private final TextProcessor tp = new TextProcessor();

    /**
     * @param metaFilePath absolute path to BulNC-description.txt
     */
    public BulNCProcessor(String metaFilePath) {
        this.metaFilePath = metaFilePath;
    }

    /**
     * @param indir  root directory of the BulNC corpus
     * @param outdir output directory for .txt files and metadata
     */
    @Override
    public void process(String indir, String outdir) {
        try {
            JSONObject json = new JSONObject();
            JSONArray descrArray = new JSONArray();

            Scanner sme = new Scanner(new File(metaFilePath), "UTF-8");
            while (sme.hasNextLine()) {
                String[] dat = sme.nextLine().split("\t");

                String relativePath = dat[1];
                System.out.println("Checking: " + relativePath);

                // --- Subcorpus filter ---
                if (!isIncluded(relativePath)) continue;

                // --- SETimes exclusion ---
                if (dat[12].contains("setimes")) continue;

                String fname = indir + relativePath;
                File f = new File(fname);
                if (!f.exists()) {
                    System.err.println("[MISSING] " + fname);
                    continue;
                }

                String tfname = "bg_bnc_" + dat[0];

                JSONObject fdescr = newBaseDescriptor(tfname);
                applyLicence(fdescr, relativePath);

                fdescr.put("PublicationDate",    dat[9].replaceAll("\\.", "-"));
                fdescr.put("DocumentTitle",      dat[8]);
                fdescr.put("Author",             dat[4]);
                fdescr.put("Style",              "Administrative");
                fdescr.put("Type",               dat[17]);
                fdescr.put("Subdomain",          dat.length > 21 ? dat[21] : "");
                fdescr.put("TranslatedDocument", dat[13]);
                fdescr.put("CollectionDate",     dat[2]);
                fdescr.put("Url",                dat[12]);
                fdescr.put("Domain",             dat[19]);

                Writer out = new OutputStreamWriter(
                        new FileOutputStream(outdir + tfname + ".txt"), "UTF-8");

                Scanner s = new Scanner(f, "UTF-8");
                int nw = 0, ns = 0, np = 0, nt = 0;

                while (s.hasNextLine()) {
                    String text = s.nextLine();
                    np++;

                    out.write(text + "\n");
                    out.flush();

                    for (String sent : tp.splitToSentences(text)) {
                        ns++;
                        String[] words = sent.split(" ");
                        nw += words.length;
                        nt += estimateTokenCount(sent);
                    }
                }

                s.close();
                out.flush();
                out.close();

                fdescr.put("NumberWords",      nw);
                fdescr.put("NumberSentences",  ns);
                fdescr.put("NumberParagraphs", np);
                fdescr.put("NumberTokens",     nt);

                descrArray.add(fdescr);
            }
            sme.close();

            json.put("metadata", descrArray);

            System.out.println("Total documents processed: " + descrArray.size());
            writeMetadata(json, outdir, "metadata_BNC_mm.json");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Returns true for subcorpora that should be processed.
     * Edit this method to change the filter.
     */
    protected boolean isIncluded(String relativePath) {
        return relativePath.contains("C-MassMedia/");
        // Uncomment to add more subcorpora:
        // || relativePath.contains("A-Administrative/")
        // || relativePath.contains("B-Science/")
        // || relativePath.contains("D-Fiction/")
    }

    @SuppressWarnings("unchecked")
    private void applyLicence(JSONObject fdescr, String relativePath) {
        if (relativePath.contains("B-Science/")
                || relativePath.contains("C-MassMedia/")
                || relativePath.contains("D-Fiction/")) {
            fdescr.put("Licence",     RESTRICTED);
            fdescr.put("LicenceLink", "");
        } else {
            fdescr.put("Licence",     CC0_LICENCE);
            fdescr.put("LicenceLink", CC0_LICENCE_LINK);
        }
    }

    @SuppressWarnings("unchecked")
    private void writeMetadata(JSONObject json, String outdir, String filename)
            throws Exception {
        String outMetaPath = outdir + filename;
        Writer outMeta = new OutputStreamWriter(
                new FileOutputStream(outMetaPath), "UTF-8");
        json.writeJSONString(outMeta);
        outMeta.flush();
        outMeta.close();

        convertJsonToCSV(json, outMetaPath + "_CSV.csv");
        System.out.println("Metadata written to: " + outMetaPath);
    }
}
