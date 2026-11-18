package io.flagship.starter.aop;

import io.flagship.starter.client.FlagShipClient;

/**
 * Backward compatibility alias for FeatureFlagGuardAspect.
 */
public class FeatureGateAspect extends FeatureFlagGuardAspect {
    public FeatureGateAspect(FlagShipClient flagClient) {
        super(flagClient);
    }
}
