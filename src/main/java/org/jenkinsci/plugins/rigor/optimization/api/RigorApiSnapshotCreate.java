package org.jenkinsci.plugins.rigor.optimization.api;

import java.util.ArrayList;

// Minimal set of fields we need for the Create Snapshot API call
public class RigorApiSnapshotCreate {
    public RigorApiSnapshotCreate() {
        this.tags=new ArrayList<RigorApiTag>();
    }

    public ArrayList<RigorApiTag> tags;
}
