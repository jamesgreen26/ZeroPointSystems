//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.sun.speech.freetts.en;

import com.sun.speech.freetts.ProcessException;
import com.sun.speech.freetts.Utterance;
import com.sun.speech.freetts.UtteranceProcessor;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PartOfSpeechTagger implements UtteranceProcessor {
    private static final Logger LOGGER;

    public void processUtterance(Utterance utterance) throws ProcessException {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("PartOfSpeechTagger does nothing!");
        }

    }

    public String toString() {
        return "PartOfSpeechTagger";
    }

    static {
        LOGGER = Logger.getLogger(PartOfSpeechTagger.class.getName());
    }
}
