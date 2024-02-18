package com.mcserby.agent.model;

public record Element(String href, String type, String content, String xpath) {

    @Override
    public String toString() {
        return "<" + type + ">" +
                "xpath='" + xpath + '\'' +
                ((href != null) ? ",href='" + href + '\'' : "") +
                ", text='" + content + '\'' +
                "</" + type + ">";
    }
}
