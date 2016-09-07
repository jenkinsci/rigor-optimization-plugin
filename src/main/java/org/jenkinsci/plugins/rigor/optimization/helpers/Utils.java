package org.jenkinsci.plugins.rigor.optimization.helpers;


import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.ExecutionException;

public class Utils {
    // Convert a CSV string to an array of integers
    public static ArrayList<Integer> SplitCSV(String csv) throws Exception {
        ArrayList<Integer> list=new ArrayList<Integer>();

        // Return empty list if no entries
        csv=csv.trim();
        if(csv.length()==0) {
            return list;
        }

        // Split our our array
        try {
            for (String s : csv.split(",")) {
                s=s.trim();
                list.add(Integer.parseInt(s));
            }
        }
        catch (Exception e) {
            throw new Exception("List must contain numbers.");
        }

        return list;
    }

    // Convert an array of integers to a CSV string
    public static String ToCSV(ArrayList<Integer> vals) {
        String res="";
        for(Integer i=0;i<vals.size();++i) {
            if(i>0) {
                res+=",";
            }
            res+=vals.get(i).toString();
        }
        return res;
    }

    // Parse a non-negative optional input, returning the parsed value.
    // Returns null if no value, or throws exception if bad value
    public static Integer ParseOptionalNonNegative(String value) throws Exception {
        // Empty field = optional ignored
        value=value.trim();
        if(value.length()==0) {
            return null;
        }

        // Validate the number field
        try {
            Integer result=Integer.parseInt(value);
            if(result<0) {
                throw new Exception("Value must be 0 or larger");
            }
            return result;
        }
        catch (Exception e) {
            throw new Exception("Value must be a number");
        }
    }

    // Parse a CSV string into an array of numbers, optionally requiring 1 or more.
    // Returns the parsed list, de-duped.
    public static ArrayList<Integer> ParseCSVIntegerList(String values,
                                                         Boolean requireAtLeastOne)
                                                         throws Exception {
        values=values.trim();
        ArrayList<Integer> result=Utils.SplitCSV(values);

        if(requireAtLeastOne && result.size()==0) {
            throw new Exception("You must supply at least one value");
        }

        // De-dupe
        HashSet<Integer> set=new HashSet<Integer>();
        set.addAll(result);
        result.clear();
        result.addAll(set);

        return result;
    }

    // Add Rigor Optimization prefix to our logging
    public static void LogMsg(PrintStream logger, String msg) {
        if(logger!=null) {
            logger.println("Rigor: " + msg);
        }
    }

    public static String Truncate(String v, int length) {
        return v.substring(0, Math.min(v.length(), length));

    }
}
