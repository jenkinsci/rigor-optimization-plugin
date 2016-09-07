package org.jenkinsci.plugins.rigor.optimization.api;

/**
 * Created by mtish on 8/24/2016.
 */
public class RigorApiResponse {
    public int HttpStatusCode =0;
    public String HttpStatusMessage="";
    public String ResponseBody="";
    public RigorApiError RigorError=null;

    public boolean Success() {
        return (this.HttpStatusCode ==200);
    }

    public String FormatError() {
        String msg="Server returned " + this.HttpStatusCode;
        if(this.HttpStatusMessage.length()>0) {
            msg+=" (" + this.HttpStatusMessage + ")";
        }
        if(this.RigorError!=null) {
            msg+=": " + this.RigorError.message;
        }
        return msg;
    }
}
