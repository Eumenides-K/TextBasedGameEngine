package edu.uob;

import java.util.HashSet;

public class GameAction
{
    private HashSet<String> triggers;
    private HashSet<String> subjects;
    private HashSet<String> consumed;
    private HashSet<String> produced;
    private String narration;

    public GameAction() {
        triggers=new HashSet<>();
        subjects = new HashSet<>();
        consumed = new HashSet<>();
        produced = new HashSet<>();
        narration = "";
    }

    public HashSet<String> getTriggers() {
        return triggers;
    }

    public void setTriggers(HashSet<String> triggers) {
        this.triggers = triggers;
    }

    public HashSet<String> getSubjects() {
        return subjects;
    }

    public void setSubjects(HashSet<String> subjects) {
        this.subjects = subjects;
    }

    public HashSet<String> getConsumed() {
        return consumed;
    }

    public void setConsumed(HashSet<String> consumed) {
        this.consumed = consumed;
    }

    public HashSet<String> getProduced() {
        return produced;
    }

    public void setProduced(HashSet<String> produced) {
        this.produced = produced;
    }

    public String getNarration() {
        return narration;
    }

    public void setNarration(String narration) {
        this.narration = narration;
    }
}

