//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.sun.speech.freetts.en;

import com.sun.speech.freetts.Token;
import com.sun.speech.freetts.Tokenizer;
import java.io.IOException;
import java.io.Reader;

public class TokenizerImpl implements Tokenizer {
    public static final int EOF = -1;
    public static final String DEFAULT_WHITESPACE_SYMBOLS = " \t\n\r";
    public static final String DEFAULT_SINGLE_CHAR_SYMBOLS = "(){}[]";
    public static final String DEFAULT_PREPUNCTUATION_SYMBOLS = "\"'`({[";
    public static final String DEFAULT_POSTPUNCTUATION_SYMBOLS = "\"'`.,:;!?(){}[]";
    private int lineNumber = 0;
    private String inputText = null;
    private Reader reader = null;
    private int currentChar = 0;
    private int currentPosition = 0;
    private String whitespaceSymbols = " \t\n\r";
    private String singleCharSymbols = "(){}[]";
    private String prepunctuationSymbols = "\"'`({[";
    private String postpunctuationSymbols = "\"'`.,:;!?(){}[]";
    private String errorDescription = null;
    private Token token;
    private Token lastToken = null;
    private long duration = 0L;

    public TokenizerImpl() {
    }

    public TokenizerImpl(String string) {
        this.setInputText(string);
    }

    public TokenizerImpl(Reader file) {
        this.setInputReader(file);
    }

    public void setWhitespaceSymbols(String symbols) {
        this.whitespaceSymbols = symbols;
    }

    public void setSingleCharSymbols(String symbols) {
        this.singleCharSymbols = symbols;
    }

    public void setPrepunctuationSymbols(String symbols) {
        this.prepunctuationSymbols = symbols;
    }

    public void setPostpunctuationSymbols(String symbols) {
        this.postpunctuationSymbols = symbols;
    }

    public void setInputText(String inputString) {
        this.inputText = inputString;
        this.currentPosition = 0;
        if (this.inputText != null) {
            this.getNextChar();
        }

    }

    public void setInputReader(Reader reader) {
        this.reader = reader;
        this.getNextChar();
    }

    public Token getNextToken() {
        this.lastToken = this.token;
        this.token = new Token();
        this.token.setWhitespace(this.getTokenOfCharClass(this.whitespaceSymbols));
        this.token.setPrepunctuation(this.getTokenOfCharClass(this.prepunctuationSymbols));
        if (this.singleCharSymbols.indexOf(this.currentChar) != -1) {
            this.token.setWord(String.valueOf((char)this.currentChar));
            this.getNextChar();
        } else {
            this.token.setWord(this.getTokenNotOfCharClass(this.whitespaceSymbols));
        }

        this.token.setPosition(this.currentPosition);
        this.token.setLineNumber(this.lineNumber);
        this.removeTokenPostpunctuation();
        return this.token;
    }

    public boolean hasMoreTokens() {
        int nextChar = this.currentChar;
        return nextChar != -1;
    }

    private int getNextChar() {
        if (this.reader != null) {
            try {
                int readVal = this.reader.read();
                if (readVal == -1) {
                    this.currentChar = -1;
                } else {
                    this.currentChar = (char)readVal;
                }
            } catch (IOException ioe) {
                this.currentChar = -1;
                this.errorDescription = ioe.getMessage();
            }
        } else if (this.inputText != null) {
            if (this.currentPosition < this.inputText.length()) {
                this.currentChar = this.inputText.charAt(this.currentPosition);
            } else {
                this.currentChar = -1;
            }
        }

        if (this.currentChar != -1) {
            ++this.currentPosition;
        }

        if (this.currentChar == 10) {
            ++this.lineNumber;
        }

        return this.currentChar;
    }

    private String getTokenOfCharClass(String charClass) {
        return this.getTokenByCharClass(charClass, true);
    }

    private String getTokenNotOfCharClass(String endingCharClass) {
        return this.getTokenByCharClass(endingCharClass, false);
    }

    private String getTokenByCharClass(String charClass, boolean containThisCharClass) {
        StringBuffer buffer = new StringBuffer();

        while(charClass.indexOf(this.currentChar) != -1 == containThisCharClass && this.singleCharSymbols.indexOf(this.currentChar) == -1 && this.currentChar != -1) {
            buffer.append((char)this.currentChar);
            this.getNextChar();
        }

        return buffer.toString();
    }

    private void removeTokenPostpunctuation() {
        if (this.token != null) {
            String tokenWord = this.token.getWord();
            int tokenLength = tokenWord.length();

            int position;
            for(position = tokenLength - 1; position > 0 && this.postpunctuationSymbols.indexOf(tokenWord.charAt(position)) != -1; --position) {
            }

            if (tokenLength - 1 != position) {
                this.token.setPostpunctuation(tokenWord.substring(position + 1));
                this.token.setWord(tokenWord.substring(0, position + 1));
            } else {
                this.token.setPostpunctuation("");
            }
        }

    }

    public boolean hasErrors() {
        return this.errorDescription != null;
    }

    public String getErrorDescription() {
        return this.errorDescription;
    }

    public boolean isBreak() {
        String tokenWhiteSpace = this.token.getWhitespace();
        String lastTokenPostpunctuation = null;
        if (this.lastToken != null) {
            lastTokenPostpunctuation = this.lastToken.getPostpunctuation();
        }

        if (this.lastToken != null && this.token != null) {
            if (tokenWhiteSpace.indexOf(10) != tokenWhiteSpace.lastIndexOf(10)) {
                return true;
            } else if (lastTokenPostpunctuation.indexOf(58) == -1 && lastTokenPostpunctuation.indexOf(63) == -1 && lastTokenPostpunctuation.indexOf(33) == -1) {
                if (lastTokenPostpunctuation.indexOf(46) != -1 && tokenWhiteSpace.length() > 1 && Character.isUpperCase(this.token.getWord().charAt(0))) {
                    return true;
                } else {
                    String lastWord = this.lastToken.getWord();
                    int lastWordLength = lastWord.length();
                    return lastTokenPostpunctuation.indexOf(46) != -1 && Character.isUpperCase(this.token.getWord().charAt(0)) && !Character.isUpperCase(lastWord.charAt(lastWordLength - 1)) && (lastWordLength >= 4 || !Character.isUpperCase(lastWord.charAt(0)));
                }
            } else {
                return true;
            }
        } else {
            return false;
        }
    }
}
