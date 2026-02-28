//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.sun.speech.freetts.en;

import com.sun.speech.freetts.Item;
import com.sun.speech.freetts.PathExtractor;
import com.sun.speech.freetts.PathExtractorImpl;
import com.sun.speech.freetts.ProcessException;
import com.sun.speech.freetts.Relation;
import com.sun.speech.freetts.Utterance;
import com.sun.speech.freetts.UtteranceProcessor;

public class PauseGenerator implements UtteranceProcessor {
    private static final PathExtractor segmentPath = new PathExtractorImpl("R:SylStructure.daughtern.daughtern.R:Segment", false);
    private static final PathExtractor puncPath = new PathExtractorImpl("R:Token.parent.punc", true);

    public void processUtterance(Utterance utterance) throws ProcessException {
        String silence = utterance.getVoice().getFeatures().getString("silence");
        Item phraseHead = utterance.getRelation("Phrase").getHead();
        if (phraseHead != null) {
            Relation segment = utterance.getRelation("Segment");
            Item s = segment.getHead();
            if (s == null) {
                s = segment.appendItem((Item)null);
            } else {
                s = s.prependItem((Item)null);
            }

            s.getFeatures().setString("name", silence);

            for(Item phrase = phraseHead; phrase != null; phrase = phrase.getNext()) {
                for(Item word = phrase.getLastDaughter(); word != null; word = word.getPrevious()) {
                    Item seg = segmentPath.findItem(word);
                    if (seg != null) {
                        Item pause = seg.appendItem((Item)null);
                        pause.getFeatures().setString("name", silence);
                        break;
                    }
                }
            }

        }
    }

    public String toString() {
        return "PauseGenerator";
    }
}
