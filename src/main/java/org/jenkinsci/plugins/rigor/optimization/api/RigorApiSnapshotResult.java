package org.jenkinsci.plugins.rigor.optimization.api;


// Partial response from the Create Snapshot API call
public class RigorApiSnapshotResult {
    public Integer test_id;
    public Integer snapshot_id;
    public String status;
    public String snapshot_url_guest;
    public Integer zoompf_score;
    public Integer defect_count_critical_1pc;

    // Are we still scanning?
    public boolean IsScanComplete() {
        if(status.equals("InQueue") || status.equals("ScanRunning") ) {
            return false;
        }
        else {
            return true;
        }
    }

    public boolean IsFailedScan() {
        if(status.equals("BadScan")) {
            return true;
        }
        else {
            return false;
        }
    }
}
