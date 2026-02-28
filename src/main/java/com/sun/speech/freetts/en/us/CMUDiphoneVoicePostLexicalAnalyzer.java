//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.sun.speech.freetts.en.us;

import com.sun.speech.freetts.Item;
import com.sun.speech.freetts.ProcessException;
import com.sun.speech.freetts.Utterance;
import com.sun.speech.freetts.UtteranceProcessor;
import com.sun.speech.freetts.en.PostLexicalAnalyzer;

class CMUDiphoneVoicePostLexicalAnalyzer implements UtteranceProcessor {
    UtteranceProcessor englishPostLex = new PostLexicalAnalyzer();

    public void processUtterance(Utterance utterance) throws ProcessException {
        this.fixPhoneme_AH(utterance);
        this.englishPostLex.processUtterance(utterance);
    }

    private void fixPhoneme_AH(Utterance utterance) {
        for(Item item = utterance.getRelation("Segment").getHead(); item != null; item = item.getNext()) {
            if (item.getFeatures().getString("name").equals("ah")) {
                item.getFeatures().setString("name", "aa");
            }
        }

    }

    public String toString() {
        return "PostLexicalAnalyzer";
    }
}
