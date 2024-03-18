package com.mcserby.agent.model;

import java.util.List;
import java.util.stream.Collectors;

public record Observation(String header, List<SimpleElement> siteMap) {

    public String render() {
        return "Observation: " + header + "\n"
                + siteMap.stream().map(SimpleElement::toString).collect(Collectors.joining("\n"))
                + "\n\n";
    }

}
