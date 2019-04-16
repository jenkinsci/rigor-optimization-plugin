package org.jenkinsci.plugins.rigor.optimization.api;

//
// Helper functions that make use of the RigorApiClient
//

import com.sun.org.apache.xpath.internal.operations.Bool;
import org.jenkinsci.plugins.rigor.optimization.builder.BuilderSettings;
import org.jenkinsci.plugins.rigor.optimization.helpers.Utils;

import java.io.PrintStream;
import java.util.ArrayList;

public class RigorApiHelper {
    public RigorApiHelper(String apiKey, BuilderSettings settings, PrintStream logger, Integer buildNumber, String projectName) {
        this.apiClient=new RigorApiClient(apiKey);
        this.settings=settings;
        this.logger=logger;
        this.buildNumber=buildNumber;
        this.projectName=projectName;

        // Display friendly tag name
        if(this.buildNumber!=null && this.projectName!=null) {
            this.projectAndBuild="Jenkins " + this.projectName + " #" + buildNumber;
        }
        else {
            this.projectAndBuild="Jenkins Build";
        }
        this.projectAndBuild=Utils.Truncate(this.projectAndBuild,RigorApiTag.MaxTagLength);
    }

    //
    // API Connectivity Test
    //

    // Make sure we have a valid API key, and valid test IDs for that API key
    public void TestApiConnection(String testIDs) throws Exception {
        // Validate the API connection
        RigorApiResponse resp=this.apiClient.TestConnection();
        if(!resp.Success()) {
            throw new Exception(resp.FormatError());
        }

        // Parse the test ids
        ArrayList<Integer> testIDValues=BuilderSettings.ParsePerformanceTestIDs(testIDs);

        // Test the IDs
        checkForBadTestIDs(testIDValues);
    }


    // Verify the existance of the list of test ids passed in.
    protected void checkForBadTestIDs(ArrayList<Integer> testIDs) throws Exception {
        // Look for bad ids
        ArrayList<Integer> badIDs=new ArrayList<>();
        for (Integer id : testIDs) {
            RigorApiResponse resp= this.apiClient.TestForValidTestID(id);
            if(!resp.Success()) {
                badIDs.add(id);
            }
        }

        // And alert if we found any
        if(badIDs.size()==1) {
            throw new Exception("Test ID " + badIDs.get(0) + " does not exist.");
        }
        else if(badIDs.size()>1) {
            String csv= Utils.ToCSV(badIDs);
            throw new Exception("The following Test IDs do not exist: " + csv);
        }
    }

    //
    // Run the build tests
    //


    // Run the configured build tests, optionally waiting until completion
    // and passing/failing build based on results.
    // Returns true if build step succeeds, false to fail the build.
    // Assumes input settings are already validated by caller
    public Boolean RunBuildTests() {
        logger.println("");
        logger.println("####################################################");
        logger.println("Testing website performance using Rigor Optimization");
        logger.println("");

        // Kick off the snapshots
        ArrayList<RigorApiSnapshotResult> snapshotsStarted;
        try { snapshotsStarted=startSnapshots(); }
        catch (Exception e) {
            return passOrFailForSnapshotError();
        }

        // Should we wait around?
        if(!this.settings.InputFailOnResults) {
            Utils.LogMsg(logger, "Fail based on results disabled, continuing build without waiting for snapshots to complete.");
            return true;
        }
        if(!this.settings.DoPolling) {
            Utils.LogMsg(logger, "No metrics were configured for build failure, continuing build without waiting for snapshots to complete.");
            return true;
        }

        // Wait for completion
        ArrayList<RigorApiSnapshotResult> snapshotsCompleted;
        try {
            snapshotsCompleted=waitForSnapshotsComplete(snapshotsStarted);
        }
        catch (Exception e) {
            Utils.LogMsg(logger,"An error occurred waiting for snapshot(s) to complete: " + e.getMessage());
            return passOrFailForSnapshotError();
        }
        if(snapshotsCompleted==null) {
            Utils.LogMsg(logger,"Timeout occured waiting for snapshots to complete.");
            return passOrFailForSnapshotError();
        }

        // Pass/fail based on settings
        try {
            Boolean result=analyzeResults(snapshotsCompleted);
            logger.println("");
            return result;
        }
        catch (Exception e) {
            Utils.LogMsg(logger,"An error occurred processing snapshot results: " + e.getMessage());
            logger.println("");
            return passOrFailForSnapshotError();
        }
    }

    // Start new snapshots for all configured tests.
    protected ArrayList<RigorApiSnapshotResult> startSnapshots() throws Exception {
        Utils.LogMsg(logger,"Creating " + this.settings.PerformanceTestIDs.size() + " new performance snapshot(s)");
        boolean launchedAll=true;

        // If we're waiting for a snapshot to complete, don't tag it in Rigor Optimization
        // until it finishes so we can communicate build pass/fail. If we're not waiting, then
        // just tag it when the snapshot starts
        String snapshotStartTag=null;
        if(!this.settings.InputFailOnResults || !this.settings.DoPolling) {
            snapshotStartTag=this.projectAndBuild;
        }

        // Create new snapshots for each configured test
        ArrayList<RigorApiSnapshotResult> results = new ArrayList<RigorApiSnapshotResult>();
        ArrayList<Integer> ptiBuffer = new ArrayList<Integer>(this.settings.PerformanceTestIDs);
        for(Integer testID : ptiBuffer) {
            Utils.LogMsg(logger,"Creating new snapshot for test " + testID + "...");
            try {
                RigorApiSnapshotResult result=this.apiClient.StartSnapshot(testID, snapshotStartTag);
                results.add(result);
                Utils.LogMsg(logger,"New snapshot " + result.snapshot_id + " created: " + result.snapshot_url_guest);
            }
            catch (Exception e) {
                launchedAll=false;
                Utils.LogMsg(logger,"Failed to create snapshot: " + e.getMessage());
            }

            // Tag this test as a jenkins build test
            try {
                ArrayList<RigorApiTag> tags=new ArrayList<RigorApiTag>();
                RigorApiTag tag=new RigorApiTag();

                if(this.projectName!=null) {
                    tag.name="Jenkins " + this.projectName;
                    tag.name=Utils.Truncate(tag.name,RigorApiTag.MaxTagLength);
                }
                else {
                    tag.name="Jenkins Build";
                }
                tag.priority="Low";
                tags.add(tag);
                this.apiClient.UpdateTestWithTags(testID,tags);
            }
            catch(Exception e) {
                // Don't fail the build if tagging failed, just march on with a warning
                Utils.LogMsg(logger,"Failed to tag test " + testID.toString() + ": " + e.getMessage());
            }
        }

        if(!launchedAll) {
            throw new Exception("Failed to launch 1 or more performance tests.");
        }

        Utils.LogMsg(logger,"Done creating snapshots");
        Utils.LogMsg(logger,"");

        return results;
    }

    // Polling loop until all started snapshots complete
    protected ArrayList<RigorApiSnapshotResult> waitForSnapshotsComplete(ArrayList<RigorApiSnapshotResult> snapshotsStarted) throws Exception {
        ArrayList<RigorApiSnapshotResult> completedSnapshots=new ArrayList<>();
        int numberRemain=snapshotsStarted.size();
        long currentTime=System.currentTimeMillis();
        long timeoutTimeMS = currentTime + (this.settings.TestTimeoutSecondsParsed * 1000);

        // For backing off polling frequency
        long pollBackoff1=currentTime+120000;    // after 2 minutes
        long pollBackoff2=currentTime+300000;    // after 5 minutes
        int sleepTime=10000; // start with 10 seconds between polling loop

        // Poll for completion of all snapshots, up to the timeout
        Utils.LogMsg(logger,"Waiting for completion of " + numberRemain + " snapshot(s), timeout " + this.settings.TestTimeoutSecondsParsed + " seconds");
        while( (numberRemain>0) && (currentTime< timeoutTimeMS) )
        {
            // Back off polling frequency the longer we wait
            if(currentTime>pollBackoff2) {
                sleepTime=30000;    // wait 30 seconds
            }
            else if(currentTime>pollBackoff1) {
                sleepTime=20000;    // wait 20 seconds
            }
            try { Thread.sleep(sleepTime); }
            catch (InterruptedException e) {
                // User ininitiated a build stop
                throw new Exception("Abort signal received, exiting.");
            }

            // Test each remaining snapshot, removing any completed
            Utils.LogMsg(logger,"Polling status of " + numberRemain + " remaining snapshot(s)...");
            for(int i=snapshotsStarted.size()-1;i>=0;--i) {
                RigorApiSnapshotResult snapshot=snapshotsStarted.get(i);
                RigorApiSnapshotResult result=this.apiClient.GetSnapshot(snapshot.test_id,snapshot.snapshot_id);

                // Did the scan finish?
                if(result.IsFailedScan()) {
                    // Yes, but it failed
                    throw new Exception("Test " + snapshot.test_id + ", snapshot " + snapshot.snapshot_id + " failed scanning.");
                }
                else if(result.IsScanComplete()) {
                    // Yes, and it succeeded. Remove from polling list
                    completedSnapshots.add(result);
                    snapshotsStarted.remove(i);
                    --numberRemain;

                    Utils.LogMsg(logger,"Snapshot " + snapshot.snapshot_id + " for test " + snapshot.test_id + " complete. " + numberRemain + " remaining");
                }
                // else, still waiting...
            }

            currentTime=System.currentTimeMillis();
        }

        // Abort if we hit timeout
        if(numberRemain>0) {
            throw new Exception("Timeout exceeded, aborting.");
        }

        Utils.LogMsg(logger,"All snapshots complete");

        return completedSnapshots;
    }

    // Snapshots have completed, now analyze their results to see if we should fail the build
    // Returns true if all passed, false if at least one failure
    protected boolean analyzeResults(ArrayList<RigorApiSnapshotResult> snapshotsCompleted) throws Exception {
        boolean allPassed=true;
        String msg;

        Utils.LogMsg(logger,"");
        Utils.LogMsg(logger,"----------------------");
        Utils.LogMsg(logger,"Analyzing Test Results");
        Utils.LogMsg(logger,"----------------------");
        Utils.LogMsg(logger,"");

        // Look through each result
        boolean firstLoop=true;
        for(RigorApiSnapshotResult snapshot: snapshotsCompleted) {
            ArrayList<RigorApiTag> buildTags=new ArrayList<RigorApiTag>();

            // Pretty format it
            if(!firstLoop) {
                Utils.LogMsg(logger,"");
                Utils.LogMsg(logger,"----------------------");
                Utils.LogMsg(logger,"");
            }
            firstLoop=false;
            Utils.LogMsg(logger,"Analyzing Test " + snapshot.test_id + ", Snapshot " + snapshot.snapshot_id + ": " + snapshot.snapshot_url_guest);
            Utils.LogMsg(logger,"");

            // Test Performance Score
            if(this.settings.PerformanceScore!=null) {
                msg="Performance Score: " + snapshot.zoompf_score + " (limit " + this.settings.PerformanceScore + ")";
                if(snapshot.zoompf_score<this.settings.PerformanceScore) {
                    msg="** FAILED **: " + msg;
                    allPassed=false;

                    // Tag the failure
                    addBuildFailTag(buildTags,"Score less than " + this.settings.PerformanceScore.toString());
                }
                else {
                    msg="Passed: " + msg;
                }
                Utils.LogMsg(logger,msg);;
            }

            // Test Critical defects
            if(this.settings.CriticalNumber!=null) {
                msg="Critical Defects: " + snapshot.defect_count_critical_1pc + " (limit " + this.settings.CriticalNumber + ")";
                if(snapshot.defect_count_critical_1pc>this.settings.CriticalNumber) {
                    msg="** FAILED **: " + msg;
                    Utils.LogMsg(logger,msg);
                    allPassed=false;

                    // Tag the failure
                    addBuildFailTag(buildTags,"Critical defects more than " + this.settings.CriticalNumber.toString());

                    // Add extra detail about what failed
                    logCriticalDefects(snapshot);
                    Utils.LogMsg(logger,"...Reminder: you can mute or change severity of these defects for future builds using the defect links above (must be logged in)");
                }
                else {
                    msg="Passed: " + msg;
                    Utils.LogMsg(logger,msg);;
                }
            }

            // Test specific defects
            if(this.settings.FoundDefectIds.size()>0) {
                if(!analyzeFoundDefects(snapshot, buildTags)) {
                    allPassed=false;
                }
            }

            // Tag the snapshot with more details
            if(buildTags.size()>0) {
                // Build failure
                Utils.LogMsg(logger, "Tagging defect failures");

                // Add an overall failure tag too
                RigorApiTag failTag=new RigorApiTag();
                failTag.name=this.projectAndBuild + " build failed";
                failTag.priority="High";
                buildTags.add(0,failTag);   // set first
                this.apiClient.UpdateSnapshotWithTags(snapshot.test_id,snapshot.snapshot_id,buildTags);
            }
            else {
                // Build success
                RigorApiTag passTag=new RigorApiTag();
                passTag.name=this.projectAndBuild + " build passed";
                passTag.priority="Low";
                buildTags.add(passTag);
                this.apiClient.UpdateSnapshotWithTags(snapshot.test_id,snapshot.snapshot_id,buildTags);
            }
        }

        // Peace out
        Utils.LogMsg(logger,"");
        if(allPassed) {
            Utils.LogMsg(logger, "All tests passed!");
            return true;
        }
        else {
            Utils.LogMsg(logger, "One or more tests failed.");
            return false;
        }
    }

    protected void addBuildFailTag(ArrayList<RigorApiTag> buildFailTags, String msg) {
        RigorApiTag failTag=new RigorApiTag();
        failTag.name="Jenkins Failure: " + msg;

        // Truncate tag names to max length
        failTag.name=Utils.Truncate(failTag.name,RigorApiTag.MaxTagLength);

        failTag.priority="High";
        buildFailTags.add(failTag);
    }

    // Provide links and detail about the found critical defects
    protected void logCriticalDefects(RigorApiSnapshotResult snapshot) {
        try {
            RigorApiDefectResultList criticalDefects=this.apiClient.GetCriticalDefects(snapshot.test_id,snapshot.snapshot_id);
            int count=0;
            for(RigorApiDefectResult defect: criticalDefects.defects) {
                ++count;
                logDefect(count, defect);
            }
        }
        catch (Exception e) {
            // Don't fail things if we had problems getting details, this part is informational only
            Utils.LogMsg(logger, "Failed to load critical defect details: " + e.getMessage());
        }
    }

    // Look for specific defects in the results, returning true if none are found (e.g. all passed).
    protected boolean analyzeFoundDefects(RigorApiSnapshotResult snapshot, ArrayList<RigorApiTag> buildFailTags)
            throws Exception {

        // Look for these specific defects in the results
        RigorApiDefectResultList defectList=this.apiClient.GetSpecificDefects(snapshot.test_id,snapshot.snapshot_id,this.settings.FoundDefectIds);
        if(defectList.defects.size()==0) {
            Utils.LogMsg(logger,"Passed: No defects in found defect fail list were discovered");
            return true;
        }

        // Uh-oh, we found something. Log what we found
        Utils.LogMsg(logger,"** FAILED **: " + defectList.defects.size() + " defect(s) in your defect fail list were found:");
        int count=0;
        for(RigorApiDefectResult defect: defectList.defects) {
            ++count;
            logDefect(count, defect);
        }

        String tagmsg;
        if(count==1) {
            tagmsg="1 failed defect found";
        }
        else {
            tagmsg=count + " failed defects found";
        }

        // Tag the failure
        addBuildFailTag(buildFailTags,tagmsg);

        return false;
    }

    protected void logDefect(int defectNumber, RigorApiDefectResult defect) {
        String msg="--> " + defectNumber + ". " + defect.severity + " severity defect '" + defect.name + "' (" + defect.defect_id.toString() + "): ";
        msg+=defect.defect_url_guest;
        Utils.LogMsg(logger, msg);
    }

    // Pass or fail build for snapshot error based on settings
    protected boolean passOrFailForSnapshotError() {
        if(this.settings.FailBuildOnSnapshotError) {
            Utils.LogMsg(logger,"Failing build per configured setting (fail on test error).");
            return false;
        }
        else {
            Utils.LogMsg(logger,"Allowing build to continue per configured setting (don't fail on test error).");
            return true;
        }
    }


    protected RigorApiClient apiClient;
    protected BuilderSettings settings;
    protected PrintStream logger;
    protected Integer buildNumber;
    protected String projectName;
    protected String projectAndBuild;
}
