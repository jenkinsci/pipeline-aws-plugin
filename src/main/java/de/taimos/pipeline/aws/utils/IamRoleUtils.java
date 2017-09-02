package de.taimos.pipeline.aws.utils;

import com.amazonaws.regions.Regions;

import java.util.regex.Pattern;

public final class IamRoleUtils {

    private static final String AWS_DEFAULT_PARTITION_NAME = "aws";
    private static final String AWS_CN_PARTITION_NAME = "aws-cn";
    private static final Pattern IAM_ROLE_PATTERN = Pattern.compile("arn:(aws|aws-cn)::iam::[0-9]{12}:role/[\\w+=,.@-]{1,64}");


    public static String selectPartitionName(String region) {
        if (Regions.CN_NORTH_1.getName().equals(region)) {
            return AWS_CN_PARTITION_NAME;
        }
        return AWS_DEFAULT_PARTITION_NAME;
    }

    public static boolean validRoleArn(String role) {
        return (IAM_ROLE_PATTERN.matcher(role).matches());
    }

}
