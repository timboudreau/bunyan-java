/*
 * The MIT License
 *
 * Copyright 2017 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.bunyan.recovery;

import com.mastfrog.bunyan.recovery.CursorAndLogFile;
import com.mastfrog.util.time.TimeUtil;
import java.io.File;
import java.text.DecimalFormat;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Supplies log and cursor files matching a timestamp pattern; iterator provides
 * all such files in a directory, and checks for new ones before returning an
 * empty status from hasNext(). Assumes a pair of files, using a sortable
 * timestamp as the file name, followed by a sequence number, followed by .log
 * and .cursor. The reader updates the cursor file as it goes, so that on
 * failure it can pick back up where it left off. The reader is expected to
 * delete fully read and processed files, but failure to do so is harmless (just
 * adds some overhead).
 *
 * @author Tim Boudreau
 */
public class RecoveryFiles {

    static final Pattern RP = Pattern.compile("^([\\d]{4}-[\\-\\d\\.]*\\.)(\\d+)(\\.log)$");
    public final File dir;
    private static final DecimalFormat FMT = new DecimalFormat("0000");

    public RecoveryFiles(File dir) {
        this.dir = dir;
    }

    public CursorAndLogFile newLogFiles() {
        ZonedDateTime now = ZonedDateTime.now();
        String base = TimeUtil.toSortableStringFormat(now) + "." + FMT.format(0);
        File logFile = new File(dir, base + ".log");
        File cursorFile = new File(dir, base + ".cursor");
        return new CursorAndLogFile(cursorFile, logFile);
    }

    public static CursorAndLogFile nextLogFiles(File logFile) {
        Matcher m = RP.matcher(logFile.getName());
        if (!m.find()) {
            throw new IllegalArgumentException("File name does not match " + RP.pattern());
        }
        int index = Integer.parseInt(m.group(2)) + 1;
        File newLogFile = new File(logFile.getParentFile(), m.group(1) + index + ".log");
        File newCursorFile = new File(logFile.getParentFile(), m.group(1) + ".cursor");
        return new CursorAndLogFile(newCursorFile, newLogFile);
    }

    public String nextLogNameBase(File logFile, int index) {
        System.out.println("logfileName " + logFile.getName());
        Matcher m = RP.matcher(logFile.getName());
        m.find();
        int nextIndex = index + 1;
        String result = m.group(1) + FMT.format(nextIndex);
        return result;
    }

    public File cursorFileForLogFile(File f) {
        Matcher m = RP.matcher(f.getName());
        if (m.matches()) {
            String cursorFilespec = m.group(1) + m.group(2) + ".cursor";
            File cursorFile = new File(dir, cursorFilespec);
            return cursorFile;
        }
        return null;
    }

    private List<CursorAndLogFile> findAllRecoveryFiles() {
        List<CursorAndLogFile> result = new LinkedList<>();
        for (File f : dir.listFiles()) {
            File cursorFile = cursorFileForLogFile(f);
            if (cursorFile != null) {
                result.add(new CursorAndLogFile(cursorFile, f));
            }
        }
        Collections.sort(result);
        return result;
    }

    public Iterator<CursorAndLogFile> iterator(Set<CursorAndLogFile> done) {
        return new AllFilesIterator(done);
    }

    final class AllFilesIterator implements Iterator<CursorAndLogFile> {

        // Iterator which, when it reaches the tail, double checks that
        // new files have not been created since it started iterating
        List<CursorAndLogFile> l;
        Iterator<CursorAndLogFile> iter;
        private final Set<CursorAndLogFile> knownDone;

        AllFilesIterator(Set<CursorAndLogFile> knownDone) {
            l = findAllRecoveryFiles();
            l.removeAll(knownDone);
            iter = l.iterator();
            this.knownDone = knownDone;
        }

        private void checkReinit() {
            if (!iter.hasNext()) {
                List<CursorAndLogFile> nue = findAllRecoveryFiles();
                nue.removeAll(l);
                nue.removeAll(knownDone);
                l = nue;
                iter = nue.iterator();
                System.out.println("Now list of " + nue.size() + " files " + nue);
            }
        }

        @Override
        public boolean hasNext() {
            checkReinit();
            return iter.hasNext();
        }

        @Override
        public CursorAndLogFile next() {
            CursorAndLogFile f = iter.next();
            return f;
        }
    }

}
