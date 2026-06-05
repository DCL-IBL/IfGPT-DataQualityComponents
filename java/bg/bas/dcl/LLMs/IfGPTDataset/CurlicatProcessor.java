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
 * Processes the CURLICAT Bulgarian corpus.
 *
 * Input:  CoNLL-UP files (.conllup) with richer inline metadata than MARCELL.
 * Output: One plain-text .txt per document + metadata.json + metadata CSV.
 *
 * Metadata comment prefixes recognised:
 *   # PublicationDate =  → PublicationDate
 *   # DocumentTitle =    → DocumentTitle
 *   # Author =           → Author
 *   # DocumentType =     → Type
 *   # Url =              → Url
 *   # Style =            → Style
 *   # Domain =           → Domain
 *   # Subdomain =        → Subdomain
 *   # CollectionDate =   → CollectionDate
 *   # License =          → Licence  (overrides default if present)
 *
 * Default licence: CC-BY-SA-4.0.
 */
public class CurlicatProcessor extends BaseSourceProcessor {

    private static final String DEFAULT_LICENCE      = "CC-BY-SA-4.0";
    private static final String DEFAULT_LICENCE_LINK =
            "https://elrc-share.eu/static/metashare/licences/CC-BY-SA-4.0.pdf";
    private static final String PREFIX = "bg_CURLICAT_";
    private static final String EXT    = ".conllup";

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
                fdescr.put("Licence",     DEFAULT_LICENCE);
                fdescr.put("LicenceLink", DEFAULT_LICENCE_LINK);

                Writer out = new OutputStreamWriter(
                        new FileOutputStream(outdir + tfname + ".txt"), "UTF-8");

                Scanner s = new Scanner(f, "UTF-8");
                int nw = 0, ns = 0, np = 0, nt = 0;

                while (s.hasNextLine()) {
                    String line = s.nextLine();

                    // --- Metadata extraction ---
                    if (line.startsWith("# PublicationDate =")) {
                        fdescr.put("PublicationDate",
                                line.replace("# PublicationDate =", "").trim());
                    } else if (line.startsWith("# DocumentTitle =")) {
                        fdescr.put("DocumentTitle",
                                line.replace("# DocumentTitle =", "").trim());
                    } else if (line.startsWith("# Author =")) {
                        fdescr.put("Author",
                                line.replace("# Author =", "").trim());
                    } else if (line.startsWith("# DocumentType =")) {
                        fdescr.put("Type",
                                line.replace("# DocumentType =", "").trim());
                    } else if (line.startsWith("# Url =")) {
                        fdescr.put("Url",
                                line.replace("# Url =", "").trim());
                    } else if (line.startsWith("# Style =")) {
                        fdescr.put("Style",
                                line.replace("# Style =", "").trim());
                    } else if (line.startsWith("# Domain =")) {
                        fdescr.put("Domain",
                                line.replace("# Domain =", "").trim());
                    } else if (line.startsWith("# Subdomain =")) {
                        fdescr.put("Subdomain",
                                line.replace("# Subdomain =", "").trim());
                    } else if (line.startsWith("# CollectionDate =")) {
                        fdescr.put("CollectionDate",
                                line.replace("# CollectionDate =", "").trim());
                    } else if (line.startsWith("# License =")) {
                        // Override default licence if the file declares one
                        fdescr.put("Licence",
                                line.replace("# License =", "").trim());
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
                        // CoNLL-UP token line
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
            writeMetadata(json, outdir, "metadata_CC.json");

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
