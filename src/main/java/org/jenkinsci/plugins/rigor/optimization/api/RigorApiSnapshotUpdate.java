package org.jenkinsci.plugins.rigor.optimization.api;

import java.util.ArrayList;

// Minimal set of fields we need for the Update Snapsot API call
public class RigorApiSnapshotUpdate {
    public RigorApiSnapshotUpdate() {
        this.tags=new ArrayList<RigorApiTag>();
        this.tag_update="AddTags";
        this.snapshot_ids=new ArrayList<Integer>();
    }

    public String tag_update;
    public ArrayList<Integer> snapshot_ids;
    public ArrayList<RigorApiTag> tags;
}
