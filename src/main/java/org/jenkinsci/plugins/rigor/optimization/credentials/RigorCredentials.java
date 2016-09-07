package org.jenkinsci.plugins.rigor.optimization.credentials;

import com.cloudbees.plugins.credentials.Credentials;
import hudson.util.Secret;

public interface RigorCredentials extends Credentials {
    String getName();

    String getDescription();

    Secret getApiKey();
}