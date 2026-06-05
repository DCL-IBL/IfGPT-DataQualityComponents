package bg.bas.dcl.LLMs.IfGPTDataset;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Scanner;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import bg.bas.dcl.general.FileHandler;

/**
 * Processes the MARCELL Bulgarian annotated corpus.
 * 
 * Licence: CC0-1.0 (fixed for all MARCELL documents).
 * Domain:  "Държавно управление" (State governance).
 * Style:   "Административен".
 */
public class MarcellProcessor extends BaseSourceProcessor {

    private static final String LICENCE      = "CC0-1.0";
    private static final String LICENCE_LINK =
            "https://elrc-share.eu/static/metashare/licences/CC0-1.0.pdf";
    private static final String DOMAIN  = "Държавно управление";
    private static final String STYLE   = "Административен";
    private static final String PREFIX  = "bg_MARCELL_";
    private static final String EXT     = ".conllup";

    @Override
    public void process(String indir, String outdir) {
        try {
            FileHandler fh = new FileHandler();
            JSONObject json = new JSONObject();
            JSONArray descrArray = new JSONArray();

            for (File f : fh.getFileListing(new File(indir))) {
                if (!f.isFile()) continue;

                System.out.println("Processing: " + f.getAbsolutePath());

                String tfname = PREFIX + f.getName().replace(EXT, "");

                JSONObject fdescr = newBaseDescriptor(tfname);
                fdescr.put("Licence",     LICENCE);
                fdescr.put("LicenceLink", LICENCE_LINK);
                fdescr.put("Domain",      DOMAIN);
                fdescr.put("Style",       STYLE);

                Writer out = new OutputStreamWriter(
                        new FileOutputStream(outdir + tfname + ".txt"), "UTF-8");

                Scanner s = new Scanner(f, "UTF-8");
                int nw = 0, ns = 0, np = 0, nt = 0;

                while (s.hasNextLine()) {
                    String line = s.nextLine();

                    // --- Metadata extraction ---
                    if (line.startsWith("# date =")) {
                        fdescr.put("PublicationDate", line.replace("# date =", "").trim());
                    } else if (line.startsWith("# title =")) {
                        fdescr.put("DocumentTitle", line.replace("# title =", "").trim());
                    } else if (line.startsWith("# issuer =")) {
                        fdescr.put("Author", line.replace("# issuer =", "").trim());
                    } else if (line.startsWith("# type =")) {
                        fdescr.put("Type", line.replace("# type =", "").trim());
                    } else if (line.startsWith("# url =")) {
                        fdescr.put("Url", line.replace("# url =", "").trim());
                    }

                    // --- Structure counting ---
                    else if (line.startsWith("# sent_id =")) {
                        ns++;
                    } else if (line.startsWith("# newpar id =")) {
                        np++;
                        out.write("\n");
                    }

                    // --- Text output ---
                    else if (line.startsWith("# text =")) {
                        out.write(line.replace("# text =", "").trim() + "\n");
                        out.flush();
                    } else {
                        // CoNLL-UP token line: count words and tokens
                        String[] cols = line.split("\t");
                        if (cols.length > 5) {
                            nt++;
                            if (!cols[3].equals("PUNCT")) nw++;
                        }
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

            json.put("metadata", descrArray);
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

        convertJsonToCSV(json, outMetaPath + "_CSV.csv");
        System.out.println("Metadata written to: " + outMetaPath);
    }
}
