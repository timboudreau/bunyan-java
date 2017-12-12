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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Tim Boudreau
 */
class RecoveryFilesReader {

    private final RecoveryFiles files;

    public RecoveryFilesReader(RecoveryFiles files) {
        this.files = files;
    }

    public Processor newProcessor() {
        Set<CursorAndLogFile> knownDone = new HashSet<>();
        return new Processor(files.iterator(knownDone), knownDone);
    }

    static class Processor implements AutoCloseable {

        private final Iterator<CursorAndLogFile> it;
        private OneRecoveryFileReader curr;
        private final Set<CursorAndLogFile> knownDone;
        private final Map<CursorAndLogFile, OneRecoveryFileReader> readerForFiles = new HashMap<>();

        public Processor(Iterator<CursorAndLogFile> it, Set<CursorAndLogFile> knownDone) {
            this.it = it;
            this.knownDone = knownDone;
        }

        private OneRecoveryFileReader reader() throws Exception {
            if (curr != null) {
                if (curr.hasNext()) {
                    return curr;
                } else {
                    curr.close(true);
                    curr = null;
                }
            }
            CursorAndLogFile last = null;
            while (curr == null) {
                if (!it.hasNext()) {
                    return null;
                }
                CursorAndLogFile lf = it.next();
                System.out.println(" -- Loop on " + lf);
                OneRecoveryFileReader r = readerForFiles.get(lf);
                if (r == null) {
                    r = OneRecoveryFileReader.from(lf);
                    System.out.println("Create a new reader for " + lf);
                    if (r != null) {
                        readerForFiles.put(lf, r);
                    }
                }
                if (r == null) {
                    continue;
                }
                if (!r.isOpen()) {
                    System.out.println("  --not open");
                    if (last != null && last.equals(lf)) {
                        System.out.println("  -- same as last, break");
                        break;
                    }
                    System.out.println("  -- open keep going");
                    continue;
                }
                if (!r.hasNext()) {
                    r.close(true);
                    if (last != null && last.equals(lf)) {
                        System.out.println(" -- no next, break");
                        break;
                    }
                    System.out.println(" -- r keep going");
                    continue;
                }
                last = lf;
                curr = r;
                break;
            }
            return curr;
        }

        public File currentLogFile() {
            return curr == null ? null : curr.files().logFile;
        }

        Object lastState;

        public ProcessableLine next(OnBeforeRead collisionCheck) throws Exception {
            try {
                OneRecoveryFileReader reader = reader();
                if (reader == null) {
                    System.out.println(" --reader is null, abort");
                    return null;
                }
//                System.out.println("NEXT on " + reader.files() + " at " + reader.position());
                System.out.println("NEXT on " + reader.files() );
                if (lastState != null && lastState.equals(reader.state())) {
                    System.out.println("  add to known empty");
                    knownDone.add(reader.files());
                }
                lastState = reader.state();
                System.out.println("STATE: " + lastState);
                ProcessableLine line = reader.nextLine(collisionCheck);
                System.out.println("GOT LINE " + line);
                if (!reader.hasNext()) {
                    knownDone.add(reader.files());
                    reader.close(true);
                    reader = null;
                }
                return line;
            } catch (Exception ee) {
                ee.printStackTrace();
                throw ee;
            }
        }

        public boolean hasNext() throws IOException {
            return it.hasNext() || (curr != null && curr.hasNext());
        }

        @Override
        public void close() throws Exception {
            if (curr != null) {
                curr.close(true);
            }
            for (OneRecoveryFileReader r : readerForFiles.values()) {
                System.out.println("  CLOSE " + r.files());
                r.close(true);
            }
//            while (it.hasNext()) {
//                it.next();
//            }
            System.out.println("Close processor.");
        }
    }

    /*

    static class Processor implements AutoCloseable {

        private final Iterator<CursorAndLogFile> it;
        private OneRecoveryFileReader curr;
        private final Set<CursorAndLogFile> knownDone;

        public Processor(Iterator<CursorAndLogFile> it, Set<CursorAndLogFile> knownDone) {
            this.it = it;
            this.knownDone = knownDone;
        }

        private OneRecoveryFileReader reader() throws Exception {
            if (curr != null && !curr.hasNext()) {
                knownDone.add(curr.files());
                curr.close(true);
                curr = null;
            }
            if (curr != null && curr.hasNext()) {
                return curr;
            } else {
                while (it.hasNext()) {
                    CursorAndLogFile fi = it.next();
                    System.out.println(" --next file " + fi);
                    if (knownDone.contains(fi)) {
                        curr = null;
                        continue;
//                        break;
                    }
                    OneRecoveryFileReader reader = OneRecoveryFileReader.from(fi);
                    if (reader != null) {
                        reader.onClose(() -> {
                            System.out.println("onClose " + fi);
                            knownDone.add(fi);
                        });
                        if (reader.hasNext()) {
                            curr = reader;
                            break;
                        } else {
                            reader.close(true);
                            knownDone.add(reader.files());
                        }
                    } else {
                        break;
                    }
                }
            }
            return curr;
        }

        public File currentLogFile() {
            return curr == null ? null : curr.files().logFile;
        }

        Object lastState;

        public ProcessableLine next(OnBeforeRead collisionCheck) throws Exception {
            try {
                OneRecoveryFileReader reader = reader();
                if (reader == null) {
                    System.out.println(" --reader is null, abort");
                    return null;
                }
                System.out.println("NEXT on " + reader.files());
                if (lastState != null && lastState.equals(reader.state())) {
                    System.out.println("  add to known empty");
                    knownDone.add(reader.files());
                }
                lastState = reader.state();
                System.out.println("STATE: " + lastState);
                ProcessableLine line = reader.nextLine(collisionCheck);
                System.out.println("GOT LINE " + line);
                if (!reader.hasNext()) {
                    knownDone.add(reader.files());
                    reader.close(true);
                    reader = null;
                }
                return line;
            } catch (Exception ee) {
                ee.printStackTrace();
                throw ee;
            }
        }

        public boolean hasNext() throws IOException {
            return it.hasNext() || (curr != null && curr.hasNext());
        }

        @Override
        public void close() throws Exception {
            if (curr != null) {
                curr.close(true);
            }
            while (it.hasNext()) {
                it.next();
            }
            System.out.println("Close processor.");
        }
    }
     */
}
