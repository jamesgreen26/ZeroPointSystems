//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.sun.speech.freetts.en.us;

import com.sun.speech.freetts.FeatureSet;
import com.sun.speech.freetts.Item;
import com.sun.speech.freetts.Relation;
import com.sun.speech.freetts.Utterance;

public class WordRelation {
    private Relation relation;
    private TokenToWords tokenToWords;

    private WordRelation(Relation parentRelation, TokenToWords tokenToWords) {
        this.relation = parentRelation;
        this.tokenToWords = tokenToWords;
    }

    public static WordRelation createWordRelation(Utterance utterance, TokenToWords tokenToWords) {
        Relation relation = utterance.createRelation("Word");
        return new WordRelation(relation, tokenToWords);
    }

    public void addBreak() {
        Item wordItem = this.relation.getTail();
        if (wordItem != null) {
            FeatureSet featureSet = wordItem.getFeatures();
            featureSet.setString("break", "1");
        }

    }

    public void addWord(String word) {
        Item tokenItem = this.tokenToWords.getTokenItem();
        Item wordItem = tokenItem.createDaughter();
        FeatureSet featureSet = wordItem.getFeatures();
        featureSet.setString("name", word);
        this.relation.appendItem(wordItem);
    }

    public void setLastWord(String word) {
        Item lastItem = this.relation.getTail();
        FeatureSet featureSet = lastItem.getFeatures();
        featureSet.setString("name", word);
    }

    public Item getTail() {
        return this.relation.getTail();
    }
}
