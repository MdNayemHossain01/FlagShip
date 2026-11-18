package io.flagship.starter.aop;

/**
 * Exception thrown when a method protected by @FeatureFlagGuard(throwOnDisabled = true)
 * is called but the feature flag evaluates to false.
 */
public class FeatureDisabledException extends RuntimeException {
    private final String flagKey;
    private final String userKey;

    public FeatureDisabledException(String flagKey, String userKey) {
        super(String.format("Feature flag '%s' is disabled for user '%s'", flagKey, userKey));
        this.flagKey = flagKey;
        this.userKey = userKey;
    }

    public String getFlagKey() {
        return flagKey;
    }

    public String getUserKey() {
        return userKey;
    }
}
