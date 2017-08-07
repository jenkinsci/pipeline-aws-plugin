package de.taimos.pipeline.aws;

import com.google.common.base.Joiner;

final class RoleSessionNameBuilder {
    private static final int ROLE_SESSION_NAME_MAX_LENGTH = 64;
    private static final String SESSION_NAME_PREFIX = "Jenkins";
    private static final int NUMBER_OF_SEPARATORS = 2;
    private final String jobName;
    private String buildNumber;

    private RoleSessionNameBuilder(String jobName) {
        this.jobName = jobName;
    }

    String build() {
        final String jobNameWithoutWhitespaces = jobName.replace(" ", "");
        final String jobNameWithoutSlashes = jobNameWithoutWhitespaces.replace("/", "-");

        final int maxJobNameLength = ROLE_SESSION_NAME_MAX_LENGTH - (SESSION_NAME_PREFIX.length() + buildNumber.length() + NUMBER_OF_SEPARATORS);

        final int jobNameLength = jobNameWithoutSlashes.length();
        String finalJobName = jobNameWithoutSlashes;
        if (jobNameLength > maxJobNameLength) {
            finalJobName = jobNameWithoutSlashes.substring(0, maxJobNameLength);
        }
        return Joiner.on("-").join(SESSION_NAME_PREFIX, finalJobName, this.buildNumber);
    }

    static RoleSessionNameBuilder withJobName(final String jobName) {
        return new RoleSessionNameBuilder(jobName);
    }

    RoleSessionNameBuilder withBuildNumber(final String buildNumber) {
        this.buildNumber = buildNumber;
        return this;
    }
}

