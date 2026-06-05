package bg.bas.dcl.LLMs.IfGPTDataset;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Pattern;

import bg.bas.dcl.general.FileHandler;

/**
 * FileCleanProcessor — corpus boilerplate remover.
 *
 * Two-phase cleaning:
 *
 * Phase 1 — LEARN (from a sample directory):
 *   Scans every .txt file in the sample dir and records how many files each
 *   non-empty line appears in.  Lines that appear in ≥ THRESHOLD of the
 *   sample files are added to the "common lines" blocklist.
 *   The blocklist is also saved to disk for inspection / reuse.
 *
 * Phase 2 — CLEAN (over the full data directory):
 *   For every .txt file, removes lines that:
 *     (a) appear in the learned common-lines blocklist, OR
 *     (b) match any of the hardcoded boilerplate regex patterns
 *         (HTML/XML tags, PHP markers, navigation patterns,
 *          URLs, e-mail addresses, cookie/GDPR banners).
 *   Cleaned files overwrite the originals (a .bak backup is kept by default).
 *
 * Usage:
 *   FileCleanProcessor fcp = new FileCleanProcessor(0.50); // 50 % threshold
 *   fcp.learnFromSample("/path/to/sample/dir/");
 *   fcp.saveBlocklist("/path/to/blocklist.txt");           // optional
 *   fcp.cleanDirectory("/path/to/full/data/dir/", true);  // true = keep .bak
 */
public class FileCleanProcessor {

    // -----------------------------------------------------------------------
    // Configuration
    // -----------------------------------------------------------------------

    /** Fraction of sample files a line must appear in to be considered boilerplate. */
    private final double threshold;

    /** Minimum non-whitespace characters a line must have to be evaluated (avoids
     *  treating every blank separator the same way). */
    private static final int MIN_LINE_LENGTH = 3;

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------

    /** Lines found to be common across the sample (Phase 1 output). */
    private final Set<String> commonLines = new HashSet<>();

    /** Diagnostic: line → number of sample files it appeared in. */
    private final Map<String, Integer> lineFrequency = new LinkedHashMap<>();

    // -----------------------------------------------------------------------
    // Hardcoded boilerplate patterns (always applied regardless of frequency)
    // -----------------------------------------------------------------------

    private static final List<Pattern> BOILERPLATE_PATTERNS = Arrays.asList(

        // ---- HTML / XML tags ------------------------------------------------
        Pattern.compile("(?i)^\\s*<[^>]+>\\s*$"),                        // whole-line tag
        Pattern.compile("(?i).*<(script|style|head|meta|link|iframe)[^>]*>.*"),
        Pattern.compile("(?i).*</(script|style|head|body|html)>.*"),
        Pattern.compile("(?i).*<!--.*-->.*"),                             // HTML comment
        Pattern.compile("(?i).*&(nbsp|amp|lt|gt|quot|apos);.*"),         // HTML entities

        // ---- PHP / server-side markers --------------------------------------
        Pattern.compile("(?i).*<\\?php.*"),
        Pattern.compile("(?i).*\\?>\\s*"),
        Pattern.compile("(?i).*<%.*%>.*"),                                // ASP-style tags

        // ---- Navigation / menu patterns ------------------------------------
        Pattern.compile("(?i)^\\s*(home|начало|меню|menu|навигация|navigation"
                + "|търсене|search|вход|login|изход|logout"
                + "|регистрация|register|контакти|contacts"
                + "|за нас|about us|sitemap|карта на сайта)\\s*$"),
        Pattern.compile("(?i)^\\s*(next|prev|previous|следващ|предишен"
                + "|напред|назад|нагоре|back|forward|top|горе)\\s*$"),
        Pattern.compile("(?i)^\\s*\\|\\s*(.*\\|\\s*)+$"),                // pipe-separated nav bars
        Pattern.compile("(?i)^\\s*(>\\s*){2,}"),                         // breadcrumb: A > B > C
        Pattern.compile("(?i)^\\s*(\\d+\\.?\\s+){3,}$"),                 // numbered nav lists

        // ---- URLs ----------------------------------------------------------
        Pattern.compile("(?i)\\bhttps?://\\S+"),
        Pattern.compile("(?i)\\bwww\\.\\S+\\.\\S+"),
        Pattern.compile("(?i)\\bftp://\\S+"),

        // ---- E-mail addresses ----------------------------------------------
        Pattern.compile("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}"),

        // ---- Cookie / GDPR banners -----------------------------------------
        Pattern.compile("(?i).*(бисквитки|cookies|gdpr|privacy policy|поверителност"
                + "|приемам|accept all|отхвърлям|decline|consent"
                + "|лични данни|personal data|условия за ползване"
                + "|terms of (use|service)|политика за).*"),

        // ---- Social / sharing buttons --------------------------------------
        Pattern.compile("(?i)^\\s*(share|сподели|like|харесай|tweet|retweet"
                + "|pinterest|linkedin|facebook|twitter|instagram"
                + "|google\\+?|youtube|tiktok|viber|whatsapp)\\s*$"),

        // ---- Counters / analytics snippets ---------------------------------
        Pattern.compile("(?i).*google.analytics.*"),
        Pattern.compile("(?i).*ga\\s*\\(\\s*['\"].*"),
        Pattern.compile("(?i).*gtag\\s*\\(.*"),
        Pattern.compile("(?i).*_gaq\\.push.*"),

        // ---- Print / date / page artefacts ---------------------------------
        Pattern.compile("(?i)^\\s*страница\\s+\\d+\\s*(от\\s+\\d+)?\\s*$"),   // "страница 1 от 5"
        Pattern.compile("(?i)^\\s*page\\s+\\d+\\s*(of\\s+\\d+)?\\s*$"),
        Pattern.compile("(?i)^\\s*©.*$"),                                 // copyright line
        Pattern.compile("(?i)^\\s*all rights reserved.*$"),
        Pattern.compile("(?i)^\\s*права запазени.*$"),

        // ---- Lines that are purely punctuation / symbols -------------------
        Pattern.compile("^[\\s\\p{Punct}\\|\\-_=*~`^]+$")
    );

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * @param threshold fraction [0,1] of sample files a line must appear in
     *                  to be added to the blocklist (e.g. 0.50 for 50 %).
     */
    public FileCleanProcessor(double threshold) {
        if (threshold < 0 || threshold > 1)
            throw new IllegalArgumentException("Threshold must be in [0, 1].");
        this.threshold = threshold;
    }

    // -----------------------------------------------------------------------
    // Phase 1 — Learn from sample
    // -----------------------------------------------------------------------

    /**
     * Scans all .txt files in {@code sampleDir}, counts how many files each
     * trimmed non-empty line appears in, and populates {@link #commonLines}
     * with those meeting the threshold.
     *
     * @param sampleDir directory containing representative sample .txt files
     */
    public void learnFromSample(String sampleDir) {
        try {
            FileHandler fh = new FileHandler();
            List<File> sampleFiles = new ArrayList<>();

            for (File f : fh.getFileListing(new File(sampleDir))) {
                if (f.isFile() && f.getName().endsWith(".txt"))
                    sampleFiles.add(f);
            }

            int total = sampleFiles.size();
            if (total == 0) {
                System.err.println("[LearnPhase] No .txt files found in: " + sampleDir);
                return;
            }
            System.out.println("[LearnPhase] Scanning " + total + " sample files...");

            // For each file, collect the *distinct* lines it contains so a
            // repeated line inside one document only counts once.
            Map<String, Integer> fileCount = new HashMap<>();

            for (File f : sampleFiles) {
                Set<String> seenInFile = new HashSet<>();
                Scanner s = new Scanner(f, "UTF-8");
                while (s.hasNextLine()) {
                    String line = s.nextLine().trim();
                    if (line.length() < MIN_LINE_LENGTH) continue;
                    if (seenInFile.add(line)) {                    // first occurrence in this file
                        fileCount.merge(line, 1, Integer::sum);
                    }
                }
                s.close();
            }

            // Apply threshold
            commonLines.clear();
            lineFrequency.clear();

            double cutoff = threshold * total;
            for (Map.Entry<String, Integer> entry : fileCount.entrySet()) {
                lineFrequency.put(entry.getKey(), entry.getValue());
                if (entry.getValue() >= cutoff) {
                    commonLines.add(entry.getKey());
                }
            }

            System.out.println("[LearnPhase] Common lines identified: " + commonLines.size()
                    + "  (threshold=" + (int)(threshold * 100) + "%, files=" + total + ")");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Replaces the learned common-lines set with a pre-built one.
     * Useful when loading a previously saved blocklist.
     *
     * @param lines set of exact line strings to treat as boilerplate
     */
    public void setCommonLines(Set<String> lines) {
        commonLines.clear();
        commonLines.addAll(lines);
    }

    // -----------------------------------------------------------------------
    // Blocklist persistence
    // -----------------------------------------------------------------------

    /**
     * Saves the learned blocklist to a plain-text file (one line per entry),
     * preceded by a frequency comment for human review.
     *
     * @param outPath destination file path
     */
    public void saveBlocklist(String outPath) {
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(outPath), "UTF-8"))) {

            pw.println("# FileCleanProcessor blocklist");
            pw.println("# threshold=" + threshold
                    + "  entries=" + commonLines.size());
            pw.println("# Format: <frequency TAB line>");
            pw.println();

            // Sort by descending frequency for readability
            lineFrequency.entrySet().stream()
                .filter(e -> commonLines.contains(e.getKey()))
                .sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(e -> pw.println(e.getValue() + "\t" + e.getKey()));

            System.out.println("[Blocklist] Saved " + commonLines.size()
                    + " entries to: " + outPath);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Loads a blocklist previously saved by {@link #saveBlocklist}.
     * Comment lines (starting with #) and blank lines are skipped.
     *
     * @param blocklistPath path to the blocklist file
     */
    public void loadBlocklist(String blocklistPath) {
        try {
            commonLines.clear();
            Scanner sc = new Scanner(new File(blocklistPath), "UTF-8");
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                if (line.startsWith("#") || line.isBlank()) continue;
                // Format: "<freq>\t<content>"  or bare "<content>"
                int tab = line.indexOf('\t');
                String content = (tab >= 0) ? line.substring(tab + 1) : line;
                if (!content.isBlank()) commonLines.add(content.trim());
            }
            sc.close();
            System.out.println("[Blocklist] Loaded " + commonLines.size()
                    + " entries from: " + blocklistPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // -----------------------------------------------------------------------
    // Phase 2 — Clean full directory
    // -----------------------------------------------------------------------

    /**
     * Cleans every .txt file in {@code dataDir} by removing lines that are
     * in the learned blocklist or match a hardcoded boilerplate pattern.
     *
     * @param dataDir    directory containing corpus .txt files to clean
     * @param keepBackup if true, originals are renamed to *.bak before overwriting
     */
    public void cleanDirectory(String dataDir, boolean keepBackup) {
        try {
            if (commonLines.isEmpty()) {
                System.out.println("[CleanPhase] Warning: no common lines loaded. "
                        + "Only regex patterns will be applied.");
            }

            FileHandler fh = new FileHandler();
            int processed = 0, linesRemoved = 0;

            for (File f : fh.getFileListing(new File(dataDir))) {
                if (!f.isFile() || !f.getName().endsWith(".txt")) continue;

                CleanResult result = cleanFile(f, keepBackup);
                processed++;
                linesRemoved += result.linesRemoved;

                if (result.linesRemoved > 0) {
                    System.out.println("[CleanPhase] " + f.getName()
                            + " — removed " + result.linesRemoved + " lines.");
                }
            }

            System.out.println("[CleanPhase] Done. Files processed: " + processed
                    + "  Total lines removed: " + linesRemoved);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Cleans a single file in place.
     *
     * @param file       the .txt file to clean
     * @param keepBackup if true, a .bak copy of the original is kept
     * @return CleanResult with statistics
     */
    public CleanResult cleanFile(File file, boolean keepBackup) {
        int removed = 0;
        try {
            // Read all lines
            List<String> inputLines  = new ArrayList<>();
            Scanner sc = new Scanner(file, "UTF-8");
            while (sc.hasNextLine()) inputLines.add(sc.nextLine());
            sc.close();

            // Filter
            List<String> outputLines = new ArrayList<>();
            for (String line : inputLines) {
                if (shouldRemove(line)) {
                    removed++;
                } else {
                    outputLines.add(line);
                }
            }

            if (removed > 0) {
                // Backup
                if (keepBackup) {
                    File bak = new File(file.getAbsolutePath() + ".bak");
                    Files.copy(file.toPath(), bak.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                }

                // Overwrite
                Writer w = new OutputStreamWriter(
                        new FileOutputStream(file), "UTF-8");
                for (String l : outputLines) {
                    w.write(l + "\n");
                }
                w.flush();
                w.close();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return new CleanResult(file, removed);
    }

    // -----------------------------------------------------------------------
    // Core line decision
    // -----------------------------------------------------------------------

    /**
     * Returns true if the line should be removed.
     *
     * A line is removed if:
     *   1. Its trimmed form is in the learned common-lines blocklist, OR
     *   2. It matches any hardcoded boilerplate regex pattern.
     *
     * Blank lines shorter than MIN_LINE_LENGTH are always kept so that
     * paragraph structure is preserved.
     *
     * @param rawLine the original line from the file (not yet trimmed)
     */
    public boolean shouldRemove(String rawLine) {
        String trimmed = rawLine.trim();

        // Always keep blank/very-short lines (paragraph separators)
        if (trimmed.length() < MIN_LINE_LENGTH) return false;

        // 1. Exact-match blocklist
        if (commonLines.contains(trimmed)) return true;

        // 2. Regex boilerplate patterns
        for (Pattern p : BOILERPLATE_PATTERNS) {
            if (p.matcher(trimmed).matches() || p.matcher(trimmed).find()) {
                return true;
            }
        }

        return false;
    }

    // -----------------------------------------------------------------------
    // Diagnostic helpers
    // -----------------------------------------------------------------------

    /** Returns an unmodifiable view of the learned common-lines set. */
    public Set<String> getCommonLines() {
        return java.util.Collections.unmodifiableSet(commonLines);
    }

    /** Returns a copy of the frequency map (line → number of sample files). */
    public Map<String, Integer> getLineFrequency() {
        return java.util.Collections.unmodifiableMap(lineFrequency);
    }

    /**
     * Prints a summary of the top {@code n} most-frequent common lines to stdout.
     */
    public void printTopCommonLines(int n) {
        System.out.println("--- Top " + n + " common lines (by sample frequency) ---");
        lineFrequency.entrySet().stream()
            .filter(e -> commonLines.contains(e.getKey()))
            .sorted((a, b) -> b.getValue() - a.getValue())
            .limit(n)
            .forEach(e -> System.out.printf("  [%4d]  %s%n", e.getValue(), e.getKey()));
    }

    // -----------------------------------------------------------------------
    // Inner result class
    // -----------------------------------------------------------------------

    /** Simple value object returned by {@link #cleanFile}. */
    public static class CleanResult {
        public final File file;
        public final int  linesRemoved;

        public CleanResult(File file, int linesRemoved) {
            this.file         = file;
            this.linesRemoved = linesRemoved;
        }
    }
}
