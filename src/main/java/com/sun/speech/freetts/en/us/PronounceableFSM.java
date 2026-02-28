//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.sun.speech.freetts.en.us;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.StringTokenizer;

public class PronounceableFSM {
    private static final String VOCAB_SIZE = "VOCAB_SIZE";
    private static final String NUM_OF_TRANSITIONS = "NUM_OF_TRANSITIONS";
    private static final String TRANSITIONS = "TRANSITIONS";
    protected int vocabularySize;
    protected int[] transitions;
    protected boolean scanFromFront;

    public PronounceableFSM(URL url, boolean scanFromFront) throws IOException {
        this.scanFromFront = scanFromFront;
        InputStream is = url.openStream();
        this.loadText(is);
        is.close();
    }

    public PronounceableFSM(int vocabularySize, int[] transitions, boolean scanFromFront) {
        this.vocabularySize = vocabularySize;
        this.transitions = transitions;
        this.scanFromFront = scanFromFront;
    }

    private void loadText(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        String line = null;

        while((line = reader.readLine()) != null) {
            if (!line.startsWith("***")) {
                if (line.startsWith("VOCAB_SIZE")) {
                    this.vocabularySize = this.parseLastInt(line);
                } else if (line.startsWith("NUM_OF_TRANSITIONS")) {
                    int transitionsSize = this.parseLastInt(line);
                    this.transitions = new int[transitionsSize];
                } else if (line.startsWith("TRANSITIONS")) {
                    StringTokenizer st = new StringTokenizer(line);
                    String transition = st.nextToken();

                    for(int i = 0; st.hasMoreTokens() && i < this.transitions.length; this.transitions[i++] = Integer.parseInt(transition)) {
                        transition = st.nextToken().trim();
                    }
                }
            }
        }

        reader.close();
    }

    private int parseLastInt(String line) {
        String lastInt = line.trim().substring(line.lastIndexOf(" "));
        return Integer.parseInt(lastInt.trim());
    }

    private int transition(int state, int symbol) {
        for(int i = state; i < this.transitions.length; ++i) {
            if (this.transitions[i] % this.vocabularySize == symbol) {
                return this.transitions[i] / this.vocabularySize;
            }
        }

        return -1;
    }

    public boolean accept(String inputString) {
        int state = this.transition(0, 35);
        int leftEnd = inputString.length() - 1;
        int start = this.scanFromFront ? 0 : leftEnd;
        int i = start;

        while(0 <= i && i <= leftEnd) {
            char c = inputString.charAt(i);
            int symbol;
            if (c != 'n' && c != 'm') {
                if ("aeiouy".indexOf(c) != -1) {
                    symbol = 86;
                } else {
                    symbol = c;
                }
            } else {
                symbol = 78;
            }

            state = this.transition(state, symbol);
            if (state == -1) {
                return false;
            }

            if (symbol == 86) {
                return true;
            }

            if (this.scanFromFront) {
                ++i;
            } else {
                --i;
            }
        }

        return false;
    }
}
