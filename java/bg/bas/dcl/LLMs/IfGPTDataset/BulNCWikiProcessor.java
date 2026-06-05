package bg.bas.dcl.LLMs.IfGPTDataset;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Scanner;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import bg.bas.dcl.general.JSONProcessor;
import bg.bas.dcl.monolingual.bg.TextProcessor;

/**
 * Processes the BulNC "F-InformalFiction" (Wiki/Informal) subcorpus.
 *
  
 */
public class BulNCWikiProcessor extends BaseSourceProcessor {

    private static final String CC0_LICENCE      = "CC0";
    private static final String CC0_LICENCE_LINK =
            "https://creativecommons.org/public-domain/cc0/";

    private final String metaFilePath;
    private final String existingMetaJson; // may be null
    private final TextProcessor tp = new TextProcessor();

    public BulNCWikiProcessor(String metaFilePath, String existingMetaJson) {
        this.metaFilePath     = metaFilePath;
        this.existingMetaJson = existingMetaJson;
    }

    /**
      
     */
    @Override
    public void process(String indir, String outdir) {
        try {
            // Load existing metadata if provided, otherwise start fresh
            JSONObject json;
            JSONArray descrArray;

            if (existingMetaJson != null && new File(existingMetaJson).exists()) {
                JSONProcessor jp = new JSONProcessor();
                json = jp.readJSON(new File(existingMetaJson));
                descrArray = (JSONArray) json.get("metadata");
                System.out.println("Loaded existing metadata with "
                        + descrArray.size() + " entries.");
            } else {
                json = new JSONObject();
                descrArray = new JSONArray();
                json.put("metadata", descrArray);
            }

            int newDocs = 0;
            long totalTokens = 0;

            Scanner sme = new Scanner(new File(metaFilePath), "UTF-8");
            while (sme.hasNextLine()) {
                String[] dat = sme.nextLine().split("\t");

                String relativePath = dat[1];
                System.out.println("Checking: " + relativePath);

                if (!relativePath.contains("F-InformalFiction")) continue;

                String fname = indir + relativePath;
                File f = new File(fname);
                if (!f.exists()) {
                    System.err.println("[MISSING] " + fname);
                    continue;
                }

                String tfname = "bg_bnc_" + dat[0];

                JSONObject fdescr = newBaseDescriptor(tfname);
                fdescr.put("Licence",            CC0_LICENCE);
                fdescr.put("LicenceLink",        CC0_LICENCE_LINK);
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
                newDocs++;
                totalTokens += nt;
            }
            sme.close();

            System.out.println("New F-InformalFiction documents added: " + newDocs);
            System.out.println("Total tokens in new documents: " + totalTokens);
            System.out.println("Merged metadata total entries: " + descrArray.size());

            writeMetadata(json, outdir, "metadata.json");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // -----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private void writeMetadata(JSONObject json, String outdir, String filename)
            throws Exception {
        String outMetaPath = outdir + filename;
        Writer outMeta = new OutputStreamWriter(
                new FileOutputStream(outMetaPath), "UTF-8");
        json.writeJSONString(outMeta);
        outMeta.flush();
        outMeta.close();

        System.out.println("Merged metadata written to: " + outMetaPath);
         
    }
}
