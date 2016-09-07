package org.jenkinsci.plugins.rigor.optimization.api;

import java.util.ArrayList;

// Partial response from the Get Defects API call
public class RigorApiDefectResult {
    public Integer defect_id;
    public String severity;
    public String name;
    public String defect_url_guest;
}