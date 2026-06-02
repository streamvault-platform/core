package io.streamvault.core.api.auth;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class OpenRegistrationTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("streamvault.open-registration", "true");
    }
}
