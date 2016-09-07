package org.jenkinsci.plugins.rigor.optimization.credentials;

import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.NameWith;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.util.Secret;
import org.kohsuke.stapler.DataBoundConstructor;

@NameWith(value = RigorCredentialsNameProvider.class, priority = 50)
public class RigorCredentialsImpl extends BaseStandardCredentials implements RigorCredentials {
    @NonNull
    private final Secret apiKey;

    @NonNull
    private final String name;

    @DataBoundConstructor
    public RigorCredentialsImpl(@CheckForNull String id,
                                @NonNull @CheckForNull String name,
                                @CheckForNull String description,
                                @CheckForNull String apiKey) {
        super(id, description);
        this.apiKey = Secret.fromString(apiKey);
        this.name = name;
    }

    @NonNull
    public Secret getApiKey() {
        return this.apiKey;
    }

    @NonNull
    public String getName() {
        return this.name;
    }

    @Extension
    public static class Descriptor
            extends CredentialsDescriptor {

        /** {@inheritDoc} */
        @Override
        public String getDisplayName() {
            return "Rigor API Key";
        }
    }
}