package com.mcserby.agent.model;

import com.fasterxml.jackson.annotation.JsonAlias;

public record BasicAction(@JsonAlias("action_type") ActionType actionType,
                          @JsonAlias("element_identifier") String elementIdentifier,
                          String value) {

    @Override
    public String toString() {
        return "Action: " +
                actionType.name().toLowerCase() + " " +
                ((elementIdentifier != null)? " " + elementIdentifier : "") +
                ((value != null)? " '" + value + '\'' : "");
    }
}
