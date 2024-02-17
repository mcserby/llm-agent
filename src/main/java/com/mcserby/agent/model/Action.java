package com.mcserby.agent.model;

import com.fasterxml.jackson.annotation.JsonAlias;

public record Action(@JsonAlias("action_type") ActionType actionType,
                     @JsonAlias("element_identifier") String elementIdentifier,
                     String value) {

    @Override
    public String toString() {
        return "Action performed: {" +
                "actionType=" + actionType +
                ((elementIdentifier != null)? ", elementIdentifier='" + elementIdentifier + '\'' : "") +
                ", value='" + value + '\'' +
                '}';
    }
}
