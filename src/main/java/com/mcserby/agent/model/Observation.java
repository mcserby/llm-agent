package com.mcserby.agent.model;

import java.util.List;
import java.util.stream.Collectors;

public record Observation(List<Element> siteMap) {

    public String render() {
        return "Observation: site map elements: "
                + siteMap.stream().map(Element::toString).collect(Collectors.joining("\n"))
                + "\n\n";
    }

}
