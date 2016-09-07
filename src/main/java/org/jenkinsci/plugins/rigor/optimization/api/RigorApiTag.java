package org.jenkinsci.plugins.rigor.optimization.api;

public class RigorApiTag {

    public RigorApiTag() {
        this.priority="Medium";
    }

    public String name;     // Must be 80 characters or less
    public String priority; // possible values: High, Medium, Low

    public static final int MaxTagLength = 80;
}
