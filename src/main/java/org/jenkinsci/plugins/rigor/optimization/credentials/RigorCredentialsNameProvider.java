package org.jenkinsci.plugins.rigor.optimization.credentials;

import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import edu.umd.cs.findbugs.annotations.NonNull;

public class RigorCredentialsNameProvider extends CredentialsNameProvider<RigorCredentialsImpl> {
    @NonNull
    @Override
    public String getName(@NonNull RigorCredentialsImpl rigorCredentials) {
        return rigorCredentials.getName();
    }
}
