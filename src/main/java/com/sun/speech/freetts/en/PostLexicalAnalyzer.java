//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.sun.speech.freetts.en;

import com.sun.speech.freetts.Item;
import com.sun.speech.freetts.PathExtractor;
import com.sun.speech.freetts.PathExtractorImpl;
import com.sun.speech.freetts.ProcessException;
import com.sun.speech.freetts.Utterance;
import com.sun.speech.freetts.UtteranceProcessor;
import com.sun.speech.freetts.Voice;

public class PostLexicalAnalyzer implements UtteranceProcessor {
    private static final PathExtractor wordPath = new PathExtractorImpl("R:SylStructure.parent.parent.name", true);
    private static final PathExtractor P_PH_VC = new PathExtractorImpl("p.ph_vc", true);
    private static final PathExtractor N_PH_VC = new PathExtractorImpl("n.ph_vc", true);

    public void processUtterance(Utterance utterance) throws ProcessException {
        this.fixApostrophe(utterance);
        this.fixTheIy(utterance);
    }

    private void fixApostrophe(Utterance utterance) {
        Voice voice = utterance.getVoice();

        for(Item item = utterance.getRelation("Segment").getHead(); item != null; item = item.getNext()) {
            String word = wordPath.findFeature(item).toString();
            if (word.equals("'s")) {
                String pname = item.getPrevious().toString();
                if ("fa".indexOf(voice.getPhoneFeature(pname, "ctype")) != -1 && "dbg".indexOf(voice.getPhoneFeature(pname, "cplace")) == -1) {
                    prependSchwa(item);
                } else if (voice.getPhoneFeature(pname, "cvox").equals("-")) {
                    item.getFeatures().setString("name", "s");
                }
            } else if ((word.equals("'ve") || word.equals("'ll") || word.equals("'d")) && "-".equals(P_PH_VC.findFeature(item))) {
                prependSchwa(item);
            }
        }

    }

    private static void prependSchwa(Item item) {
        Item schwa = item.prependItem((Item)null);
        schwa.getFeatures().setString("name", "ax");
        item.getItemAs("SylStructure").prependItem(schwa);
    }

    private void fixTheIy(Utterance utterance) {
        Voice voice = utterance.getVoice();

        for(Item item = utterance.getRelation("Segment").getHead(); item != null; item = item.getNext()) {
            if ("ax".equals(item.toString())) {
                String word = wordPath.findFeature(item).toString();
                if ("the".equals(word) && "+".equals(N_PH_VC.findFeature(item))) {
                    item.getFeatures().setString("name", "iy");
                }
            }
        }

    }

    public String toString() {
        return "PostLexicalAnalyzer";
    }
}
