//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.sun.speech.freetts.en;

import com.sun.speech.freetts.Item;
import com.sun.speech.freetts.PathExtractor;
import com.sun.speech.freetts.PathExtractorImpl;

class F0ModelTerm {
    PathExtractor path;
    float start;
    float mid;
    float end;
    String type;

    F0ModelTerm(String feature, float start, float mid, float end, String type) {
        this.path = new PathExtractorImpl(feature, true);
        this.start = start;
        this.mid = mid;
        this.end = end;
        this.type = type;
    }

    public Object findFeature(Item item) {
        return this.path.findFeature(item);
    }

    public String toString() {
        return this.path.toString();
    }
}
