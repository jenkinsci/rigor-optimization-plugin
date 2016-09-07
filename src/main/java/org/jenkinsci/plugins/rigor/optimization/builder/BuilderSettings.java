package org.jenkinsci.plugins.rigor.optimization.builder;

import com.sun.org.apache.xpath.internal.operations.Bool;
import org.jenkinsci.plugins.rigor.optimization.helpers.Utils;

import java.io.PrintStream;
import java.util.ArrayList;

/**
 * Configured settings for a Rigor Optimization builder step
 */
public class BuilderSettings {

    public BuilderSettings() {
        this.InputFailOnResults =false;
        this.DoPolling=false;

        this.PerformanceTestIDs =new ArrayList<Integer>();
        this.PerformanceScore =null;
        this.CriticalNumber =null;
        this.FoundDefectIds =new ArrayList<Integer>();

        // If we time out waiting for snapshot(s) to complete, should we allow the build to pass?
        this.TestTimeoutSecondsParsed=300;    // 5 minutes
        this.FailBuildOnSnapshotError =true;
    }

    // Raw inputs from config form
    public String CredentialsId;
    public String InputPerformanceTestIds;
    public boolean InputFailOnResults;
    public String InputPerformanceScore;
    public String InputCriticalNumber;
    public String InputFoundDefectIds;
    public String InputTestTimeoutSeconds;

    // Failure control
    public Integer TestTimeoutSecondsParsed;
    public Boolean FailBuildOnSnapshotError;
    public Boolean DoPolling;

    // Parsed values
    public ArrayList<Integer> PerformanceTestIDs;
    public Integer PerformanceScore;
    public Integer CriticalNumber;
    public ArrayList<Integer> FoundDefectIds;

    /*
    * Parsing and Validation
    */

    // Parse all configuration settings into usable values, return false on any parse errors
    public boolean ParseSettings(PrintStream logger) {
        Boolean success=true;
        this.DoPolling=false;   // don't bother with polling unless we have at least 1 metric to test

        // Should always have at least 1 performance test id value
        try { this.PerformanceTestIDs =ParsePerformanceTestIDs(this.InputPerformanceTestIds);}
        catch (Exception e) {
            Utils.LogMsg(logger,"Config Error: Performance Test IDs: " + e.getMessage());
            success=false;
        }

        // Stop here if we're not failing the build based on metrics. The rest don't apply.
        if(!this.InputFailOnResults) {
            return success;
        }

        // Otherwise we are failing the build on metrics, so re-verify the values

        // Performance Score
        try { this.PerformanceScore =ParsePerformanceScore(this.InputPerformanceScore);}
        catch (Exception e) {
            Utils.LogMsg(logger,"Config Error: Below Performance Score: " + e.getMessage());
            success=false;
        }
        if(this.PerformanceScore!=null) {
            this.DoPolling=true;
        }

        // Critical # of defects
        try { this.CriticalNumber =ParseCriticalNumber(this.InputCriticalNumber);}
        catch (Exception e) {
            Utils.LogMsg(logger,"Config Error: Exceeds Number of Critical Defects: " + e.getMessage());
            success=false;
        }
        if(this.CriticalNumber!=null) {
            this.DoPolling=true;
        }

        // Defect IDs
        try { this.FoundDefectIds =ParseFoundDefectIDs(this.InputFoundDefectIds);}
        catch (Exception e) {
            Utils.LogMsg(logger,"Config Error: Find Specific Defect IDs: " + e.getMessage());
            success=false;
        }
        if(this.FoundDefectIds.size()>0) {
            this.DoPolling=true;
        }

        // Test timeout (seconds)
        Integer newVal=null;
        try { newVal=ParseTestTimeoutSeconds(this.InputTestTimeoutSeconds);}
        catch (Exception e) {
            Utils.LogMsg(logger,"Config Error: Test timeout: " + e.getMessage());
            success=false;
        }
        if(newVal!=null) {
            this.TestTimeoutSecondsParsed=newVal;
        }

        return success;
    }

    //
    // Field validators
    //

    public static ArrayList<Integer> ParsePerformanceTestIDs(String csvList) throws Exception {
        return Utils.ParseCSVIntegerList(csvList,true);
    }

    public static Integer ParsePerformanceScore(String value) throws Exception {
        Integer score=Utils.ParseOptionalNonNegative(value);
        if(score!=null) {
            if(score<1 || score>100) {
                throw new Exception("Value must be between 1 and 100");
            }
        }
        return score;
    }

    public static Integer ParseCriticalNumber(String value) throws Exception {
        return Utils.ParseOptionalNonNegative(value);
    }

    public static ArrayList<Integer> ParseFoundDefectIDs(String csvList) throws Exception {
        return Utils.ParseCSVIntegerList(csvList,false);
    }

    public static Integer ParseTotalContentSize(String value) throws Exception {
        return Utils.ParseOptionalNonNegative(value);
    }

    public static Integer ParseTotalFoundItems(String value) throws Exception {
        return Utils.ParseOptionalNonNegative(value);
    }

    public static Integer ParseTestTimeoutSeconds(String value) throws Exception {
        return Utils.ParseOptionalNonNegative(value);
    }

}
