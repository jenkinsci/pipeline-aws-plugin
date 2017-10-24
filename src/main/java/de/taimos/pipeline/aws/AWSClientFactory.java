/*
 * -
 * #%L
 * Pipeline: AWS Steps
 * %%
 * Copyright (C) 2016 Taimos GmbH
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package de.taimos.pipeline.aws;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.client.builder.AwsSyncClientBuilder;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.apigateway.AmazonApiGateway;
import com.amazonaws.services.apigateway.AmazonApiGatewayClientBuilder;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import com.amazonaws.services.cloudfront.AmazonCloudFront;
import com.amazonaws.services.cloudfront.AmazonCloudFrontClientBuilder;
import com.amazonaws.services.codedeploy.AmazonCodeDeploy;
import com.amazonaws.services.codedeploy.AmazonCodeDeployClientBuilder;
import com.amazonaws.services.ecr.AmazonECR;
import com.amazonaws.services.ecr.AmazonECRClientBuilder;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.organizations.AWSOrganizations;
import com.amazonaws.services.organizations.AWSOrganizationsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import hudson.EnvVars;
import org.apache.commons.lang.StringUtils;

public class AWSClientFactory {

    static final String AWS_PROFILE = "AWS_PROFILE";
    static final String AWS_DEFAULT_PROFILE = "AWS_DEFAULT_PROFILE";
    static final String AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
    static final String AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";
    static final String AWS_SESSION_TOKEN = "AWS_SESSION_TOKEN";
    static final String AWS_DEFAULT_REGION = "AWS_DEFAULT_REGION";
    static final String AWS_REGION = "AWS_REGION";
    static final String AWS_ENDPOINT_URL = "AWS_ENDPOINT_URL";

    private static <B extends AwsSyncClientBuilder<?, ?>> B configureAwsSyncClientBuilder(B clientBuilder, EnvVars vars) {
        if (StringUtils.isNotBlank(vars.get(AWS_ENDPOINT_URL))) {
            clientBuilder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(vars.get(AWS_ENDPOINT_URL), vars.get(AWS_REGION)));
        }
        clientBuilder.setRegion(AWSClientFactory.getRegion(vars).getName());
        clientBuilder.setCredentials(AWSClientFactory.getCredentials(vars));
        clientBuilder.setClientConfiguration(AWSClientFactory.getClientConfiguration(vars));
        return clientBuilder;
    }

    public static AmazonS3 createAmazonS3Client(EnvVars vars) {
        return configureAwsSyncClientBuilder(AmazonS3ClientBuilder.standard(), vars).build();
    }

    public static TransferManager createTransferManager(EnvVars vars) {
        AmazonS3 s3Client = configureAwsSyncClientBuilder(AmazonS3ClientBuilder.standard(), vars).build();
        return TransferManagerBuilder.standard().withS3Client(s3Client).build();
    }

    public static AmazonCloudFormation createAmazonCloudFormationClient(EnvVars vars) {
        return configureAwsSyncClientBuilder(AmazonCloudFormationClientBuilder.standard(), vars).build();
    }

    public static AWSSecurityTokenService createAWSSecurityTokenServiceClient(EnvVars vars) {
        return configureAwsSyncClientBuilder(AWSSecurityTokenServiceClientBuilder.standard(), vars).build();
    }

    public static AmazonCloudFront createAmazonCloudFrontClient(EnvVars vars) {
        return configureAwsSyncClientBuilder(AmazonCloudFrontClientBuilder.standard(), vars).build();
    }

    public static AmazonApiGateway createAmazonApiGatewayClient(EnvVars vars) {
        return configureAwsSyncClientBuilder(AmazonApiGatewayClientBuilder.standard(), vars).build();
    }

    public static AmazonECR createAmazonECRClient(EnvVars vars) {
        return configureAwsSyncClientBuilder(AmazonECRClientBuilder.standard(), vars).build();
    }

    public static AWSLambda createAWSLambdaClient(EnvVars vars) {
        return configureAwsSyncClientBuilder(AWSLambdaClientBuilder.standard(), vars).build();
    }

    public static AWSOrganizations createAWSOrganizationsClient(EnvVars vars) {
        return configureAwsSyncClientBuilder(AWSOrganizationsClientBuilder.standard(), vars).build();
    }

    public static AmazonSNS createAmazonSNSClient(EnvVars vars) {
        return configureAwsSyncClientBuilder(AmazonSNSClientBuilder.standard(), vars).build();
    }

    public static AmazonIdentityManagement createAmazonIdentityManagementClient(EnvVars vars) {
        return configureAwsSyncClientBuilder(AmazonIdentityManagementClientBuilder.standard(), vars).build();
    }

    public static AmazonCodeDeploy createAmazonCodeDeployClient(EnvVars vars) {
        return configureAwsSyncClientBuilder(AmazonCodeDeployClientBuilder.standard(), vars).build();
    }

    private static ClientConfiguration getClientConfiguration(EnvVars vars) {
        ClientConfiguration clientConfiguration = new ClientConfiguration();
        ProxyConfiguration.configure(vars, clientConfiguration);
        return clientConfiguration;
    }

    private static AWSCredentialsProvider getCredentials(EnvVars vars) {
        AWSCredentialsProvider provider = handleStaticCredentials(vars);
        if (provider != null) {
            return provider;
        }

        provider = handleProfile(vars);
        if (provider != null) {
            return provider;
        }

        return new DefaultAWSCredentialsProviderChain();
    }

    private static AWSCredentialsProvider handleProfile(EnvVars vars) {
        String profile = vars.get(AWS_PROFILE, vars.get(AWS_DEFAULT_PROFILE));
        if (profile != null) {
            return new ProfileCredentialsProvider(profile);
        }
        return null;
    }

    private static AWSCredentialsProvider handleStaticCredentials(EnvVars vars) {
        String accessKey = vars.get(AWS_ACCESS_KEY_ID);
        String secretAccessKey = vars.get(AWS_SECRET_ACCESS_KEY);
        if (accessKey != null && secretAccessKey != null) {
            String sessionToken = vars.get(AWS_SESSION_TOKEN);
            if (sessionToken != null) {
                return new AWSStaticCredentialsProvider(new BasicSessionCredentials(accessKey, secretAccessKey, sessionToken));
            }
            return new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretAccessKey));
        }
        return null;
    }

    private static Region getRegion(EnvVars vars) {
        if (vars.get(AWS_DEFAULT_REGION) != null) {
            return Region.getRegion(Regions.fromName(vars.get(AWS_DEFAULT_REGION)));
        }
        if (vars.get(AWS_REGION) != null) {
            return Region.getRegion(Regions.fromName(vars.get(AWS_REGION)));
        }
        if (System.getenv(AWS_DEFAULT_REGION) != null) {
            return Region.getRegion(Regions.fromName(System.getenv(AWS_DEFAULT_REGION)));
        }
        if (System.getenv(AWS_REGION) != null) {
            return Region.getRegion(Regions.fromName(System.getenv(AWS_REGION)));
        }
        Region currentRegion = Regions.getCurrentRegion();
        if (currentRegion != null) {
            return currentRegion;
        }
        return Region.getRegion(Regions.DEFAULT_REGION);
    }
}
