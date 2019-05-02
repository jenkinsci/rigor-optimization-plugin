package org.jenkinsci.plugins.rigor.optimization.api;

import com.google.gson.Gson;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.jenkinsci.plugins.rigor.optimization.helpers.Utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.net.URLEncoder;
import java.util.concurrent.ExecutionException;

//
// Wraps direct calls to Rigor Optimization API
//

public class RigorApiClient {
    private static String API_ENDPOINT="https://optimization-api.rigor.com/v2/";

    public RigorApiClient(String apikey) {
        this.apikey=apikey;
    }

    // Validate successful connection to Rigor Optimization API using the supplied key
    public RigorApiResponse TestConnection() {
        // Call Get Tests, should always return 200 ok if our credentials are valid
        String url="tests?p.per_page=1";
        return makeGetRequest(url);
    }

    // Test for existence of a specific performance test ID
    public RigorApiResponse TestForValidTestID(Integer testID) {
        // Call Get Tests, should always return 200 ok if our credentials are valid
        String url="tests/" + testID.toString();
        return makeGetRequest(url);
    }

    // Invoke the Create snapshot API, returning the newly created snapshot info
    public RigorApiSnapshotResult StartSnapshot(Integer testID, String tagName)
            throws Exception {
        Gson gson = new Gson();
        String url="tests/" + testID.toString() + "/snapshots";

        //
        // Create snapshot payload
        //
        RigorApiSnapshotCreate payload=new RigorApiSnapshotCreate();

        // Add Tag?
        if(tagName!=null) {
            RigorApiTag tag=new RigorApiTag();
            tag.name=tagName;
            tag.priority="Low";
            payload.tags.add(tag);
        }

        // Serialize it
        String body;
        try {
            body=gson.toJson(payload);
        }
        catch (Exception e) {
            throw new Exception("Error creating snapshot payload for test " + testID.toString() + ": " + e.getMessage());
        }

        //
        // Make the post
        //
        RigorApiResponse response=makePostRequest(url,body);
        if(!response.Success()) {
            throw new Exception("Error creating snapshot for test " + testID.toString() + ": " + response.FormatError());
        }

        //
        // Pull out the result
        //
        try {
            RigorApiSnapshotResult result=gson.fromJson(response.ResponseBody,RigorApiSnapshotResult.class);
            return result;
        }
        catch (Exception e) {
            throw new Exception("Error creating snapshot for test " + testID.toString() + ": " + e.getMessage());
        }
    }

    // Call Get Snapshot Detail to get current snapshot state
    public RigorApiSnapshotResult GetSnapshot(Integer testID,
                                              Integer snapshotID)
            throws Exception {

        String url="tests/" + testID.toString() + "/snapshots/" + snapshotID.toString();

        // Make the request
        RigorApiResponse response=makeGetRequest(url);
        if(!response.Success()) {
            throw new Exception("Error fetching snapshot " + snapshotID.toString() + " for test " + testID.toString() + ": " + response.FormatError());
        }

        // Pull out the result
        try {
            Gson gson = new Gson();
            RigorApiSnapshotResult result=gson.fromJson(response.ResponseBody,RigorApiSnapshotResult.class);
            return result;
        }
        catch (Exception e) {
            throw new Exception("Error fetching snapshot " + snapshotID.toString() + " for test " + testID.toString() + ": " + response.FormatError());
        }
    }

    // Invoke the Update Snapshot API call, setting new tags
    public RigorApiSnapshotResult UpdateTestWithTags(Integer testID, ArrayList<RigorApiTag> tags)
            throws Exception {
        Gson gson = new Gson();
        String url="tests/" + testID.toString();

        //
        // Update test payload
        //
        RigorApiTestUpdate payload=new RigorApiTestUpdate();
        payload.tags=tags;

        // Serialize it
        String body;
        try {
            body=gson.toJson(payload);
        }
        catch (Exception e) {
            throw new Exception("Error creating update test payload for test " + testID.toString() + ": " + e.getMessage());
        }

        //
        // Make the PUT request
        //
        RigorApiResponse response=makePutRequest(url,body);
        if(!response.Success()) {
            throw new Exception("Error updating test for test " + testID.toString() + ": " + response.FormatError());
        }

        //
        // Pull out the result
        //
        try {
            RigorApiSnapshotResult result=gson.fromJson(response.ResponseBody,RigorApiSnapshotResult.class);
            return result;
        }
        catch (Exception e) {
            throw new Exception("Error updating test for test " + testID.toString() + ": " + e.getMessage());
        }
    }

    // Invoke the Update Snapshot API call, setting new tags
    public RigorApiSnapshotResult UpdateSnapshotWithTags(Integer testID, Integer snapshot_id, ArrayList<RigorApiTag> tags)
            throws Exception {
        Gson gson = new Gson();
        String url="tests/" + testID.toString() + "/snapshots";

        //
        // Update snapshot payload
        //
        RigorApiSnapshotUpdate payload=new RigorApiSnapshotUpdate();
        payload.snapshot_ids.add(snapshot_id);
        payload.tags=tags;

        // Serialize it
        String body;
        try {
            body=gson.toJson(payload);
        }
        catch (Exception e) {
            throw new Exception("Error creating update snapshot payload for test " + testID.toString() + ": " + e.getMessage());
        }

        //
        // Make the PUT request
        //
        RigorApiResponse response=makePutRequest(url,body);
        if(!response.Success()) {
            throw new Exception("Error updating snapshot for test " + testID.toString() + ": " + response.FormatError());
        }

        //
        // Pull out the result
        //
        try {
            RigorApiSnapshotResult result=gson.fromJson(response.ResponseBody,RigorApiSnapshotResult.class);
            return result;
        }
        catch (Exception e) {
            throw new Exception("Error updating snapshot for test " + testID.toString() + ": " + e.getMessage());
        }
    }

    // Get details about any critical defects for a snapshot
    public RigorApiDefectResultList GetCriticalDefects(Integer testID,
                                                       Integer snapshotID)
            throws Exception {

        String url="tests/" + testID.toString() + "/snapshots/" + snapshotID.toString() + "/defects";
        url+="?f.show_tpc=No&f.severity=Critical";

        // Make the request
        RigorApiResponse response=makeGetRequest(url);
        if(!response.Success()) {
            throw new Exception("Error fetching critical defects for snapshot " + snapshotID.toString() + ", test " + testID.toString() + ": " + response.FormatError());
        }

        // Pull out the result
        try {
            Gson gson = new Gson();
            RigorApiDefectResultList result=gson.fromJson(response.ResponseBody,RigorApiDefectResultList.class);
            return result;
        }
        catch (Exception e) {
            throw new Exception("Error fetching critical defects for snapshot " + snapshotID.toString() + " for test " + testID.toString() + ": " + response.FormatError());
        }
    }

    // Pull back details for specific Defect ids for a specific snapshot, if found
    public RigorApiDefectResultList GetSpecificDefects(Integer testID,
                                                       Integer snapshotID,
                                                       ArrayList<Integer> defectIDs)
            throws Exception {

        String url="tests/" + testID.toString() + "/snapshots/" + snapshotID.toString() + "/defects";
        url+="?f.show_tpc=No";
        url+="&f.defect_ids=" + URLEncoder.encode(Utils.ToCSV(defectIDs),"UTF-8");
        url+="&p.per_page=" + defectIDs.size(); // (in case there's more than the default page size specified)

        // Make the request
        RigorApiResponse response=makeGetRequest(url);
        if(!response.Success()) {
            throw new Exception("Error fetching found defects for snapshot " + snapshotID.toString() + ", test " + testID.toString() + ": " + response.FormatError());
        }

        // Pull out the result
        try {
            Gson gson = new Gson();
            RigorApiDefectResultList result=gson.fromJson(response.ResponseBody,RigorApiDefectResultList.class);
            return result;
        }
        catch (Exception e) {
            throw new Exception("Error fetching found defects for snapshot " + snapshotID.toString() + " for test " + testID.toString() + ": " + response.FormatError());
        }
    }

    //
    // Utility Functions
    //

    // Make a GET request to the relative URL off the root API url, returning JSON response
    protected RigorApiResponse makeGetRequest(String relativeURL)  {
        String url=API_ENDPOINT + relativeURL;
        RigorApiResponse response=new RigorApiResponse();

        try {

            // Make GET request
            HttpClient client = new DefaultHttpClient();
            HttpGet request = new HttpGet(url);
            request.addHeader("Content-Type", "application/json");
            request.addHeader("Accept", "application/json");
            request.addHeader("API-KEY", this.apikey);
            HttpResponse httpResponse = client.execute(request);

            // Get response
            response.HttpStatusCode=httpResponse.getStatusLine().getStatusCode();
            response.HttpStatusMessage =httpResponse.getStatusLine().getReasonPhrase();

            BufferedReader rd = new BufferedReader(
                    new InputStreamReader(httpResponse.getEntity().getContent(), Charset.defaultCharset()));
            StringBuffer result = new StringBuffer();
            String line = "";
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
            response.ResponseBody =result.toString();

            if(!response.Success() && response.ResponseBody!=null) {
                Gson gson = new Gson();
                response.RigorError=gson.fromJson(response.ResponseBody,RigorApiError.class);
            }
        }
        catch (Exception e) {
            response.HttpStatusCode=500;    // internal server error
            response.HttpStatusMessage=e.getMessage();
        }

        return response;
    }

    // Make a POST request to the relative URL off the root API url, returning JSON response
    protected RigorApiResponse makePostRequest(String relativeURL, String bodyToPost)  {
        String url=API_ENDPOINT + relativeURL;
        RigorApiResponse response=new RigorApiResponse();

        try {

            // Make PUT request
            HttpClient client = new DefaultHttpClient();
            HttpPost post = new HttpPost(url);
            post.addHeader("Content-Type", "application/json");
            post.addHeader("Accept", "application/json");
            post.addHeader("API-KEY", this.apikey);
            StringEntity entity=new StringEntity(bodyToPost);
            post.setEntity(entity);
            HttpResponse httpResponse = client.execute(post);

            // Get response
            response.HttpStatusCode=httpResponse.getStatusLine().getStatusCode();
            response.HttpStatusMessage =httpResponse.getStatusLine().getReasonPhrase();

            BufferedReader rd = new BufferedReader(
                    new InputStreamReader(httpResponse.getEntity().getContent(),Charset.defaultCharset()));
            StringBuffer result = new StringBuffer();
            String line = "";
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
            response.ResponseBody =result.toString();

            if(!response.Success() && response.ResponseBody!=null) {
                Gson gson = new Gson();
                response.RigorError=gson.fromJson(response.ResponseBody,RigorApiError.class);
            }
        }
        catch (Exception e) {
            response.HttpStatusCode=500;    // internal server error
            response.HttpStatusMessage=e.getMessage();
        }

        return response;
    }

    // Make a POST request to the relative URL off the root API url, returning JSON response
    protected RigorApiResponse makePutRequest(String relativeURL, String bodyToPut)  {
        String url=API_ENDPOINT + relativeURL;
        RigorApiResponse response=new RigorApiResponse();

        try {

            // Make PUT request
            HttpClient client = new DefaultHttpClient();
            HttpPut put=new HttpPut(url);
            put.addHeader("Content-Type", "application/json");
            put.addHeader("Accept", "application/json");
            put.addHeader("API-KEY", this.apikey);
            StringEntity entity=new StringEntity(bodyToPut);
            put.setEntity(entity);
            HttpResponse httpResponse = client.execute(put);

            // Get response
            response.HttpStatusCode=httpResponse.getStatusLine().getStatusCode();
            response.HttpStatusMessage =httpResponse.getStatusLine().getReasonPhrase();

            BufferedReader rd = new BufferedReader(
                    new InputStreamReader(httpResponse.getEntity().getContent(),Charset.defaultCharset()));
            StringBuffer result = new StringBuffer();
            String line = "";
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
            response.ResponseBody =result.toString();

            if(!response.Success() && response.ResponseBody!=null) {
                Gson gson = new Gson();
                response.RigorError=gson.fromJson(response.ResponseBody,RigorApiError.class);
            }
        }
        catch (Exception e) {
            response.HttpStatusCode=500;    // internal server error
            response.HttpStatusMessage=e.getMessage();
        }

        return response;
    }

    private String apikey;
}
