package com.mcserby.agent.model;

import java.util.List;
import java.util.stream.Collectors;

public record SimpleElement(
        String tagName,
        String xpath,
        String href,
        String id,
        String placeholder,
        String textOrValue,
        String type,
        String areaLabelOrLabel,
        List<SimpleElement> children) {

    @Override
    public String toString() {
        return toPseudoHtml();
    }

    private String toPseudoHtml() {
        return "<" + tagName + xpathProperty() +
                idProperty() + typeProperty() + hrefProperty() + placeholderProperty() + ">" +
                (children.isEmpty() ? textOrValue :
                        "\n\t" + children.stream().map(SimpleElement::toPseudoHtml)
                        .map(s -> "\t" + s + "\n").collect(Collectors.joining())) +
                "</" + tagName + ">";
    }

    private String xpathProperty() {
        return prop("xpath", xpath);
    }
    private String idProperty() {
        return prop("id", id);
    }

    private String hrefProperty() {
        return prop("href", href);
    }

    public String placeholderProperty() {
        return prop("placeholder", placeholder);
    }

    public String typeProperty() {
        return prop("type", type);
    }

    public String labelProperty() {
        return prop("label", areaLabelOrLabel);
    }

    private String prop(String name, String value) {
        return (value != null && !value.isEmpty()) ? " " + name + "='" + value + '\'' : "";
    }

    public SimpleElement withChildren(List<SimpleElement> children) {
        return new SimpleElement(
                tagName,
                xpath,
                href,
                id,
                placeholder,
                removeTextOrValue(),
                type,
                areaLabelOrLabel,
                children
        );
    }

    private String removeTextOrValue() {
        return null;
    }
}
