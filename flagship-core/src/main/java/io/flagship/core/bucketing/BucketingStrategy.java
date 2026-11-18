package io.flagship.core.bucketing;

public interface BucketingStrategy {
    int computeBucket(String flagKey, String userKey, String salt);

    default int computeBucket(String flagKey, String userKey) {
        return computeBucket(flagKey, userKey, null);
    }
}
