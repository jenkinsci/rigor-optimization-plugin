package org.jenkinsci.plugins.rigor.optimization.builder;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;


import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.rigor.optimization.api.RigorApiHelper;
import org.jenkinsci.plugins.rigor.optimization.credentials.RigorCredentials;
import org.jenkinsci.plugins.rigor.optimization.helpers.Utils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;

public class RigorBuilder extends Builder  {
    private BuilderSettings settings;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public RigorBuilder(String credentialsId,
                        String performanceTestIds,
                        Boolean failOnSnapshotError,
                        Boolean failOnResults,
                        String performanceScore,
                        String criticalNumber,
                        String foundDefectIds,
                        String totalContentSize,
                        String totalFoundItems,
                        String testTimeoutSeconds) {

        this.settings=new BuilderSettings();

        settings.CredentialsId =credentialsId;
        settings.InputPerformanceTestIds =performanceTestIds;
        settings.FailBuildOnSnapshotError = failOnSnapshotError;
        settings.InputFailOnResults =failOnResults;
        settings.InputPerformanceScore =performanceScore;
        settings.InputCriticalNumber =criticalNumber;
        settings.InputFoundDefectIds =foundDefectIds;
        settings.InputTestTimeoutSeconds = testTimeoutSeconds;
    }

    // Accessors to allow persistance of values set from config.jelly
    public String getCredentialsId() {
        return settings.CredentialsId;
    }
    public String getperformanceTestIds() {
        return settings.InputPerformanceTestIds;
    }
    public boolean getFailOnSnapshotError() {
        return settings.FailBuildOnSnapshotError;
    }
    public boolean getFailOnResults() {
        return settings.InputFailOnResults;
    }
    public String getPerformanceScore() {
        return settings.InputPerformanceScore;
    }
    public String getCriticalNumber() { return settings.InputCriticalNumber; }
    public String getFoundDefectIds() { return settings.InputFoundDefectIds; }
    public String getTestTimeoutSeconds() { return settings.InputTestTimeoutSeconds; }

    // Called when a build is run
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        PrintStream logger=listener.getLogger();
        try {
            // Fail the build if any configuration parse errors
            if(!this.settings.ParseSettings(logger)) {
                return false;
            }

            // Info about the build, if available
            Integer buildNumber=null;
            String projectName=null;
            try {
                buildNumber=build.number;
                projectName=build.getProject().getName();
            }
            catch (Exception e) {
                Utils.LogMsg(logger, "Failed to locate build version information, omitting.");
            }

            // Init our API connector
            String apiKey=getDescriptor().getRigorCredentials(settings.CredentialsId);
            RigorApiHelper helper=new RigorApiHelper(apiKey, this.settings, logger, buildNumber, projectName);

            // Start the pending tests, optionally waiting for completion.
            // Returns true when build can continue succesfully, or false to fail thebuild
            return helper.RunBuildTests();
        }
        catch (Exception e) {
            // Fail the build if we hit a problem
            Utils.LogMsg(logger, "An error occurred: " + e.getMessage());
            return false;
        }
    }

    // Overridden for better type safety.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }


    /**
     * Descriptor for {@link RigorBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        /**
         * In order to load the persisted global configuration, you have to
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * Validation of config fields in response to OnChange events
         * See https://wiki.jenkins-ci.org/display/JENKINS/Form+Validation
         */

        // Validate Performance Test IDs field
        public FormValidation doCheckPerformanceTestIds(@QueryParameter String value) throws IOException {
            try {
                BuilderSettings.ParsePerformanceTestIDs(value);
                return FormValidation.ok();
            }
            catch (Exception e) {
                return FormValidation.error(e.getMessage());
            }
        }

        // Validate Performance Score field
        public FormValidation doCheckPerformanceScore(@QueryParameter String value) throws IOException, ServletException {
            try {
                BuilderSettings.ParsePerformanceScore(value);
                return FormValidation.ok();
            }
            catch (Exception e) {
                return FormValidation.error(e.getMessage());
            }
        }

        // Validate Critical Number field
        public FormValidation doCheckCriticalNumber(@QueryParameter String value) throws IOException {
            try {
                BuilderSettings.ParseCriticalNumber(value);
                return FormValidation.ok();
            }
            catch (Exception e) {
                return FormValidation.error(e.getMessage());
            }
        }

        // Validate Specific Defects field
        public FormValidation doCheckFoundDefectIds(@QueryParameter String value) throws IOException {
            try {
                BuilderSettings.ParseFoundDefectIDs(value);
                return FormValidation.ok();
            }
            catch (Exception e) {
                return FormValidation.error(e.getMessage());
            }
        }

        // Validate test timeout
        public FormValidation doCheckTestTimeoutSeconds(@QueryParameter String value) throws IOException {
            try {
                BuilderSettings.ParseTestTimeoutSeconds(value);
                return FormValidation.ok();
            }
            catch (Exception e) {
                return FormValidation.error(e.getMessage());
            }
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types
            return true;
        }

        // This human readable name is used in the configuration screen for this plugin's build step
        public String getDisplayName() {
            return "Test website performance using Rigor Optimization";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            save();
            return super.configure(req,formData);
        }

        // Get API token from config
        private String getRigorCredentials(String credentialsId) {
            List<RigorCredentials> rigorCredentialsList = CredentialsProvider.lookupCredentials(RigorCredentials.class, Jenkins.getInstance(), ACL.SYSTEM);
            RigorCredentials rigorCredentials = CredentialsMatchers.firstOrNull(rigorCredentialsList, CredentialsMatchers.allOf(CredentialsMatchers.withId(credentialsId)));

            return rigorCredentials == null ? null : rigorCredentials.getApiKey().getPlainText();
        }

        // Only show Rigor Credentials in the credentials dropdown
        @SuppressWarnings("unused") // used by stapler
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Jenkins context,
                                                     @QueryParameter String remoteBase) {
            if (context == null || !context.hasPermission(Item.CONFIGURE)) {
                return new StandardListBoxModel();
            }

            List<DomainRequirement> domainRequirements = newArrayList();
            return new StandardListBoxModel()
                    .withEmptySelection()
                    .withMatching(
                            CredentialsMatchers.anyOf(
                                    CredentialsMatchers.instanceOf(RigorCredentials.class)),
                            CredentialsProvider.lookupCredentials(
                                    StandardCredentials.class,
                                    context,
                                    ACL.SYSTEM,
                                    domainRequirements));
        }

        @SuppressWarnings("unused") // used by stapler
        public FormValidation doVerifyCredentials(
                @QueryParameter String credentialsId, @QueryParameter String performanceTestIds) throws IOException {

            // Make sure we have credentials
            int length=credentialsId.length();
            if(length==0) {
                return FormValidation.error("No credentials supplied!!!");
            }
            String apiKey=getRigorCredentials(credentialsId);
            if(apiKey==null || apiKey.equals("")) {
                return FormValidation.error("API Key Not Found");
            }

            // Test the connection + test IDs
            try {
                RigorApiHelper helper=new RigorApiHelper(apiKey, null, null, null, null);
                helper.TestApiConnection(performanceTestIds);
                return FormValidation.ok("Connection confirmed!");
            }
            catch (Exception e) {
                return FormValidation.error(e.getMessage());
            }
        }
    }

}
