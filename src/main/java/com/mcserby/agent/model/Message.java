package com.mcserby.agent.model;

public record Message(MessageType type, String text, boolean canOmitForBrevity){
    public String omittedForBrevity() {
        return type + ": <omitted for brevity, use more recent observations>";
    }

    public String toString(){
        return text;
    }

}
