package org.jenkinsci.plugins.rigor.optimization.api;


import java.util.ArrayList;

// Minimal set of fields we need for the Update Test API call
public class RigorApiTestUpdate {
    public RigorApiTestUpdate() {
        this.tags=new ArrayList<RigorApiTag>();
        this.tag_update="AddTags";
    }

    public String tag_update;
    public ArrayList<RigorApiTag> tags;
}
