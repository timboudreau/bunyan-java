/*
 * The MIT License
 *
 * Copyright 2017 tim.
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

package com.mastfrog.bunyan.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Uses a single loop over characters to test for multiple strings.
 * Thread-safe.
 *
 * @author Tim Boudreau
 */
final class MatchWords implements Predicate<String> {

    private final List<MatchState> matchers = new ArrayList<>();
    private ThreadLocal<MatchState[]> local = new ThreadLocal<>();
    private final boolean or;

    MatchWords(boolean or) {
        this.or = or;
    }
    
    MatchWords(boolean or, String[] strings) {
        this.or = or;
        for (String s : strings) {
            add(s);
        }
    }

    void add(String s) {
        matchers.add(new MatchState(s));
    }

    private MatchState[] matchers() {
        MatchState[] result = local.get();
        if (result == null) {
            result = new MatchState[matchers.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = matchers.get(i).copy();
            }
        }
        return result;
    }

    @Override
    public boolean test(String t) {
        char[] c = t.toCharArray();
        MatchState[] mtchrs = matchers();
        boolean[] matched = new boolean[mtchrs.length];
        for (MatchState mtchr : mtchrs) {
            mtchr.reset();
        }
        int matchedCount = 0;
        for (int i = 0; i < c.length; i++) {
            for (int j = 0; j < mtchrs.length; j++) {
                if (!matched[j]) {
                    mtchrs[j].check(c[i]);
                    if (mtchrs[j].isMatched()) {
                        matched[j] = true;
                        matchedCount++;
                    }
                }
            }
        }
        return or ? matchedCount > 0 : matchedCount == mtchrs.length;
    }

    private static final class MatchState {

        private final char[] what;
        private int matched = 0;

        MatchState(String what) {
            this.what = what.toCharArray();
        }

        MatchState(char[] what) {
            this.what = what;
        }

        public MatchState copy() {
            return new MatchState(what);
        }

        private void reset() {
            matched = 0;
        }

        boolean isMatched() {
            return matched >= what.length - 1;
        }

        void check(char c) {
            if (isMatched()) {
                return;
            }
            if (what[matched] == c) {
                matched++;
            }
        }
    }

}
