//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.sun.speech.freetts.en.us;

import com.sun.speech.freetts.*;

import java.io.IOException;
import java.net.URL;
import java.util.Locale;

public class CMUTimeVoice extends CMUClusterUnitVoice {
    public CMUTimeVoice(String name, Gender gender, Age age, String description, Locale locale, String domain, String organization, CMULexicon lexicon, URL database) {
        super(name, gender, age, description, locale, domain, organization, lexicon, database);
    }

    protected UtteranceProcessor getPostLexicalAnalyzer() throws IOException {
        return new UtteranceProcessor() {
            public void processUtterance(Utterance utterance) throws ProcessException {
            }
        };
    }

    public String toString() {
        return "CMUTimeVoice";
    }
}
