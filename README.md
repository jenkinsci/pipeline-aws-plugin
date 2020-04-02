# Status

[![Build Status](https://ci.jenkins.io/buildStatus/icon?job=Plugins/pipeline-aws-plugin/master)](https://ci.jenkins.io/job/Plugins/job/pipeline-aws-plugin/job/master/)

# Features

This plugins adds Jenkins pipeline steps to interact with the AWS API.

* [withAWS](#withaws)
* [awsIdentity](#awsidentity)
* [cfInvalidate](#cfinvalidate)
* [s3Upload](#s3upload)
* [s3Download](#s3download)
* [s3Copy](#s3copy)
* [s3Delete](#s3delete)
* [s3DoesObjectExist](#s3doesobjectexist)
* [s3FindFiles](#s3findfiles)
* [s3PresignURL](#s3presignurl)
* [cfnValidate](#cfnvalidate)
* [cfnUpdate](#cfnupdate)
* [cfnDelete](#cfndelete)
* [cfnDescribe](#cfndescribe)
* [cfnExports](#cfnexports)
* [cfnCreateChangeSet](#cfncreatechangeset)
* [cfnExecuteChangeSet](#cfnexecutechangeset)
* [cfnUpdateStackSet](#cfnupdatestackset)
* [cfnDeleteStackSet](#cfndeletestackset)
* [snsPublish](#snspublish)
* [deployAPI](#deployapi)
* [awaitDeploymentCompletion](#awaitdeploymentcompletion)
* [listAWSAccounts](#listawsaccounts)
* [updateIdP](#updateidp)
* [setAccountAlias](#setaccountalias)
* [ecrDeleteImages](#ecrdeleteimages)
* [ecrListImages](#ecrlistimages)
* [ecrLogin](#ecrlogin)
* [ecrSetRepositoryPolicy](#ecrsetrepositorypolicy)
* [invokeLambda](#invokelambda)
* [lambdaCleanupVersions](#lambdacleanupversions)
* [ec2ShareAmi](#ec2ShareAmi)
* [elbRegisterInstance](#elbRegisterInstance)
* [elbDeregisterInstance](#elbDeregisterInstance)
* [elbIsInstanceRegistered](#elbIsInstanceRegistered)
* [elbIsInstanceDeregistered](#elbIsInstanceDeregistered)

[**see the changelog for release information**](#changelog)

# Primary/Agent setups

This plugin is not optimized to setups with a primary and multiple agents. 
Only steps that touch the workspace are executed on the agents while the rest is executed on the master.

For the best experience make sure that primary and agents have the same IAM permission and networking capabilities.

# Usage / Steps

## withAWS

the `withAWS` step provides authorization for the nested steps.
You can provide region and profile information or let Jenkins
assume a role in another or the same AWS account.
You can mix all parameters in one `withAWS` block.

Set region information (note that region and endpointUrl are mutually exclusive):

```groovy
withAWS(region:'eu-west-1') {
    // do something
}
```

Use provided endpointUrl (endpointUrl is optional, however, region and endpointUrl are mutually exclusive):

```groovy
withAWS(endpointUrl:'https://minio.mycompany.com',credentials:'nameOfSystemCredentials',federatedUserId:"${submitter}@${releaseVersion}") {
    // do something
}
```


Use Jenkins UsernamePassword credentials information (Username: AccessKeyId, Password: SecretAccessKey):

```groovy
withAWS(credentials:'IDofSystemCredentials') {
    // do something
}
```

Use Jenkins AWS credentials information (AWS Access Key: AccessKeyId, AWS Secret Key: SecretAccessKey):

```groovy
withAWS(credentials:'IDofAwsCredentials') {
    // do something
}
```

Use profile information from `~/.aws/config`:

```groovy
withAWS(profile:'myProfile') {
    // do something
}
```

Assume role information (account is optional - uses current account as default. externalId, roleSessionName and policy are optional. duration is optional - if specified it represents the maximum amount of time in seconds the session may persist for, defaults to 3600.):

```groovy
withAWS(role:'admin', roleAccount:'123456789012', externalId: 'my-external-id', policy: '{"Version":"2012-10-17","Statement":[{"Sid":"Stmt1","Effect":"Deny","Action":"s3:DeleteObject","Resource":"*"}]}', duration: '3600', roleSessionName: 'my-custom-session-name') {
    // do something
}
```

Assume federated user id information (federatedUserId is optional - if specified it generates a set of temporary credentials and allows you to push a federated user id into cloud trail for auditing. duration is optional - if specified it represents the maximum amount of time in seconds the session may persist for, defaults to 3600.):

```groovy
withAWS(region:'eu-central-1',credentials:'nameOfSystemCredentials',federatedUserId:"${submitter}@${releaseVersion}", duration: '3600') {
    // do something
}
```

Authentication with a SAML assertion (fetched from your company IdP) by assuming a role

```groovy
withAWS(role: 'myRole', roleAccount: '123456789', principalArn: 'arn:aws:iam::123456789:saml-provider/test', samlAssertion: 'base64SAML', region:'eu-west-1') {
  // do something
}
```

When you use Jenkins Declarative Pipelines you can also use `withAWS` in an options block:

```groovy
options {
	withAWS(profile:'myProfile')
}
stages {
	...
}
```

## awsIdentity

Print current AWS identity information to the log.

The step returns an objects with the following fields:

* account - The AWS account ID number of the account that owns or contains the calling entity
* user - The unique identifier of the calling entity
* arn - The AWS ARN associated with the calling entity

```groovy
def identity = awsIdentity()
```

## cfInvalidate

Invalidate given paths in CloudFront distribution.

```groovy
cfInvalidate(distribution:'someDistributionId', paths:['/*'])
cfInvalidate(distribution:'someDistributionId', paths:['/*'], waitForCompletion: true)
```

## S3 Steps

All s3* steps take an optional pathStyleAccessEnabled and payloadSigningEnabled boolean parameter.

```groovy
s3Upload(pathStyleAccessEnabled: true, payloadSigningEnabled: true, file:'file.txt', bucket:'my-bucket', path:'path/to/target/file.txt')
s3Copy(pathStyleAccessEnabled: true, fromBucket:'my-bucket', fromPath:'path/to/source/file.txt', toBucket:'other-bucket', toPath:'path/to/destination/file.txt')
s3Delete(pathStyleAccessEnabled: true, bucket:'my-bucket', path:'path/to/source/file.txt')
s3Download(pathStyleAccessEnabled: true, file:'file.txt', bucket:'my-bucket', path:'path/to/source/file.txt', force:true)
exists = s3DoesObjectExist(pathStyleAccessEnabled: true, bucket:'my-bucket', path:'path/to/source/file.txt')
files = s3FindFiles(pathStyleAccessEnabled: true, bucket:'my-bucket')

```

### s3Upload

Upload a file/folder from the workspace (or a String) to an S3 bucket.
If the `file` parameter denotes a directory, the complete directory including all subfolders will be uploaded.

```groovy
s3Upload(file:'file.txt', bucket:'my-bucket', path:'path/to/target/file.txt')
s3Upload(file:'someFolder', bucket:'my-bucket', path:'path/to/targetFolder/')
```

Another way to use it is with include/exclude patterns which are applied in the specified subdirectory (`workingDir`).
The option accepts a comma-separated list of patterns.

```groovy
s3Upload(bucket:"my-bucket", path:'path/to/targetFolder/', includePathPattern:'**/*', workingDir:'dist', excludePathPattern:'**/*.svg,**/*.jpg')
```

Specific user metadatas can be added to uploaded files

```groovy
s3Upload(bucket:"my-bucket", path:'path/to/targetFolder/', includePathPattern:'**/*.svg', workingDir:'dist', metadatas:['Key:SomeValue','Another:Value'])
```

Specific cachecontrol can be added to uploaded files

```groovy
s3Upload(bucket:"my-bucket", path:'path/to/targetFolder/', includePathPattern:'**/*.svg', workingDir:'dist', cacheControl:'public,max-age=31536000')
```

Specific content encoding can be added to uploaded files
```groovy
s3Upload(file:'file.txt', bucket:'my-bucket', contentEncoding: 'gzip')
```

Specific content type can be added to uploaded files

```groovy
s3Upload(bucket:"my-bucket", path:'path/to/targetFolder/', includePathPattern:'**/*.ttf', workingDir:'dist', contentType:'application/x-font-ttf')
```

Canned ACLs can be added to upload requests.

```groovy
s3Upload(file:'file.txt', bucket:'my-bucket', path:'path/to/target/file.txt', acl:'PublicRead')
s3Upload(file:'someFolder', bucket:'my-bucket', path:'path/to/targetFolder/', acl:'BucketOwnerFullControl')
```

A Server Side Encryption Algorithm can be added to upload requests.

```groovy
s3Upload(file:'file.txt', bucket:'my-bucket', path:'path/to/target/file.txt', sseAlgorithm:'AES256')
```

A KMS alias or KMS id can be used to encrypt the uploaded file or directory at rest.

```groovy
s3Upload(file: 'foo.txt', bucket: 'my-bucket', path: 'path/to/target/file.txt', kmsId: 'alias/foo')
s3Upload(file: 'foo.txt', bucket: 'my-bucket', path: 'path/to/target/file.txt', kmsId: '8e1d420d-bf94-4a15-a07a-8ad965abb30f')
s3upload(file: 'bar-dir', bucket: 'my-bucket', path: 'path/to/target', kmsId: 'alias/bar')
```

A redirect location can be added to uploaded files.

```groovy
s3Upload(file: 'file.txt', bucket: 'my-bucket', redirectLocation: '/redirect')
```

Creating an S3 object by creating the file whose contents is the provided text argument.

```groovy
s3Upload(path: 'file.txt', bucket: 'my-bucket', text: 'Some Text Content')
s3Upload(path: 'path/to/targetFolder/file.txt', bucket: 'my-bucket', text: 'Some Text Content')
```

Log messages can be less verbose. Disable it when you feel the logs are excessive but you will lose the visibility of what files having been uploaded to S3.

```groovy
s3Upload(path: 'source/path/', bucket: 'my-bucket', verbose: false)
```

### s3Download

Download a file/folder from S3 to the local workspace.
Set optional parameter `force` to `true` to overwrite existing file in workspace.
If the `path` ends with a `/` the complete virtual directory will be downloaded.

```groovy
s3Download(file:'file.txt', bucket:'my-bucket', path:'path/to/source/file.txt', force:true)
s3Download(file:'targetFolder/', bucket:'my-bucket', path:'path/to/sourceFolder/', force:true)
```

### s3Copy

Copy file between S3 buckets.

```groovy
s3Copy(fromBucket:'my-bucket', fromPath:'path/to/source/file.txt', toBucket:'other-bucket', toPath:'path/to/destination/file.txt')
```


### s3Delete

Delete a file/folder from S3.
If the path ends in a "/", then the path will be interpreted to be a folder, and all of its contents will be removed.

```groovy
s3Delete(bucket:'my-bucket', path:'path/to/source/file.txt')
s3Delete(bucket:'my-bucket', path:'path/to/sourceFolder/')
```

### s3DoesObjectExist

Check if object exists in S3 bucket.

```groovy
exists = s3DoesObjectExist(bucket:'my-bucket', path:'path/to/source/file.txt')
```

### s3FindFiles

This provides a way to query the files/folders in the S3 bucket, analogous to the `findFiles` step provided by "pipeline-utility-steps-plugin".
If specified, the `path` limits the scope of the operation to that folder only.
The `glob` parameter tells `s3FindFiles` what to look for.  This can be a file name, a full path to a file, or a standard glob ("\*", "\*.ext", "path/\*\*/file.ext", etc.).

If you do not specify `path`, then it will default to the root of the bucket.
The path is assumed to be a folder; you do not need to end it with a "/", but it is okay if you do.
The `path` property of the results will be _relative_ to this value.

This works by enumerating _every_ file/folder in the S3 bucket under `path` and then performing glob matching.
When possible, you should use `path` to limit the search space for efficiency purposes.

If you do not specify `glob`, then it will default to "\*".

By default, this will return both files and folders.
To only return files, set the `onlyFiles` parameter to `true`.

```groovy
files = s3FindFiles(bucket:'my-bucket')
files = s3FindFiles(bucket:'my-bucket', glob:'path/to/targetFolder/file.ext')
files = s3FindFiles(bucket:'my-bucket', path:'path/to/targetFolder/', glob:'file.ext')
files = s3FindFiles(bucket:'my-bucket', path:'path/to/targetFolder/', glob:'*.ext')
files = s3FindFiles(bucket:'my-bucket', path:'path/', glob:'**/file.ext')
```

`s3FindFiles` returns an array of `FileWrapper` objects exactly identical to those returned by `findFiles`.

Each `FileWrapper` object has the following properties:

* `name`: the filename portion of the path (for "path/to/my/file.ext", this would be "file.ext")
* `path`: the full path of the file, _relative_ to the `path` specified (for `path`="path/to/", this property of the file "path/to/my/file.ext" would be "my/file.ext")
* `directory`: true if this is a directory; false otherwise
* `length`: the length of the file (this is always "0" for directories)
* `lastModified`: the last modification timestamp, in milliseconds since the Unix epoch (this is always "0" for directories)

When used in a string context, a `FileWrapper` object returns the value of its `path` property.

## s3PresignURL

Will presign the bucket/key and return a url. Defaults to 1 minute duration, using GET.
```groovy
def url = s3PresignURL(bucket: 'mybucket', key: 'mykey')
```

The duration can be overridden:
```groovy
def url = s3PresignURL(bucket: 'mybucket', key: 'mykey', durationInSeconds: 300) //5 minutes
```

The method can also be overridden:
```groovy
def url = s3PresignURL(bucket: 'mybucket', key: 'mykey', httpMethod: 'POST')
```

## cfnValidate

Validates the given CloudFormation template.

```groovy
def response = cfnValidate(file:'template.yaml')
echo "template description: ${response.description}"
```

## cfnUpdate

Create or update the given CloudFormation stack using the given template from the workspace.
You can specify an optional list of parameters, either as a key/value pair or a map.
You can also specify a list of `keepParams` of parameters which will use the previous value on stack updates.

Using `timeoutInMinutes` you can specify the amount of time that can pass before the stack status becomes CREATE_FAILED and the stack gets rolled back.
Due to limitations in the AWS API, this only applies to stack creation.

If you have many parameters you can specify a `paramsFile` containing the parameters. The format is either a standard
JSON file like with the cli or a YAML file for the [cfn-params](https://www.npmjs.com/package/cfn-params) command line utility.

Additionally you can specify a list of tags that are set on the stack and all resources created by CloudFormation.

The step returns the outputs of the stack as a map. It also contains special values prefixed with `jenkins`:

* `jenkinsStackUpdateStatus` - "true"/"false" whether the stack was modified or not

When cfnUpdate creates a stack and the creation fails, the stack is deleted instead of being left in a broken state.

To prevent running into rate limiting on the AWS API you can change the default polling interval of 1000 ms using the parameter `pollIntervall`. Using the value `0` disables event printing.

```groovy
def outputs = cfnUpdate(stack:'my-stack', file:'template.yaml', params:['InstanceType=t2.nano'], keepParams:['Version'], timeoutInMinutes:10, tags:['TagName=Value'], notificationARNs:['arn:aws:sns:us-east-1:993852309656:topic'], pollInterval:1000)
```

or the parameters can be specified as a map:
```groovy
def outputs = cfnUpdate(stack:'my-stack', file:'template.yaml', params:['InstanceType': 't2.nano'], keepParams:['Version'], timeoutInMinutes:10, tags:['TagName=Value'], notificationARNs:['arn:aws:sns:us-east-1:993852309656:topic'], pollInterval:1000)
```

Alternatively, you can specify a URL to a template on S3 (you'll need this if you hit the 51200 byte limit on template):

```groovy
def outputs = cfnUpdate(stack:'my-stack', url:'https://s3.amazonaws.com/my-templates-bucket/template.yaml')
```

By default the `cfnUpdate` step creates a new stack if the specified stack does not exist, this behaviour can be overridden by passing `create: 'false'` as parameter :
```groovy
def outputs = cfnUpdate(stack:'my-stack', url:'https://s3.amazonaws.com/my-templates-bucket/template.yaml', create: 'false')
```
In above example if `my-stack` already exists it would be updated and if it doesnt exist no actions would be performed.


In a case where CloudFormation needs to use a different IAM Role for creating the stack than the one currently in effect, you can pass the complete Role ARN to be used as `roleArn` parameter. i.e:
```groovy
def outputs = cfnUpdate(stack:'my-stack', url:'https://s3.amazonaws.com/my-templates-bucket/template.yaml', roleArn: 'arn:aws:iam::123456789012:role/S3Access')
```

It's possible to override the behaviour of a stack when the creation fails by using "onFailure". Allowed values are DO_NOTHING, ROLLBACK, or DELETE
Because the normal default value of ROLLBACK behaves strangely in a CI/CD environment. cfnUpdate uses DELETE as default.
```groovy
def outputs = cfnUpdate(stack:'my-stack', url:'https://s3.amazonaws.com/my-templates-bucket/template.yaml', onFailure:'DELETE')
```

You can specify rollback triggers for the stack update:
```groovy
def outputs = cfnUpdate(stack:'my-stack', url:'https://s3.amazonaws.com/my-templates-bucket/template.yaml', rollbackTimeoutInMinutes: 10, rollbackTriggers: ['AWS::CloudWatch::Alarm=arn:of:cloudwatch:alarm'])
```

When creating a stack, you can activate termination protection by using the `enableTerminationProtection` field:

```groovy
def outputs = cfnUpdate(stack:'my-stack', url:'https://s3.amazonaws.com/my-templates-bucket/template.yaml', enableTerminationProtection: true)
```

Note: When creating a stack, either `file` or `url` are required. When updating it, omitting both parameters will keep the stack's current template.

## cfnDelete

Remove the given stack from CloudFormation.

To prevent running into rate limiting on the AWS API you can change the default polling interval of 1000 ms using the parameter `pollIntervall`. Using the value `0` disables event printing.

```groovy
cfnDelete(stack:'my-stack', pollInterval:1000)
```

## cfnDescribe

The step returns the outputs of the stack as map.

```groovy
def outputs = cfnDescribe(stack:'my-stack')
```

## cfnExports

The step returns the global CloudFormation exports as map.

```groovy
def globalExports = cfnExports()
```

## cfnCreateChangeSet

Create a change set to update the given CloudFormation stack using the given template from the workspace.
You can specify an optional list of parameters, either as a key/value pair or a map.
You can also specify a list of `keepParams` of parameters which will use the previous value on stack updates.

If you have many parameters you can specify a `paramsFile` containing the parameters. The format is either a standard
JSON file like with the cli or a YAML file for the [cfn-params](https://www.npmjs.com/package/cfn-params) command line utility.

Additionally you can specify a list of tags that are set on the stack and all resources created by CloudFormation.

The step returns the outputs of the stack as a map. It also contains special values prefixed with `jenkins`:

* `jenkinsStackUpdateStatus` - "true"/"false" whether the stack was modified or not


To prevent running into rate limiting on the AWS API you can change the default polling interval of 1000 ms using the parameter `pollIntervall`. Using the value `0` disables event printing.

```groovy
cfnCreateChangeSet(stack:'my-stack', changeSet:'my-change-set', file:'template.yaml', params:['InstanceType=t2.nano'], keepParams:['Version'], tags:['TagName=Value'], notificationARNs:['arn:aws:sns:us-east-1:993852309656:topic'], pollInterval:1000)
```
or the parameters can be specified as a map:
```groovy
cfnCreateChangeSet(stack:'my-stack', changeSet:'my-change-set', file:'template.yaml', params:['InstanceType': 't2.nano'], keepParams:['Version'], tags:['TagName=Value'], notificationARNs:['arn:aws:sns:us-east-1:993852309656:topic'], pollInterval:1000)
```

Alternatively, you can specify a URL to a template on S3 (you'll need this if you hit the 51200 byte limit on template):

```groovy
cfnCreateChangeSet(stack:'my-stack', changeSet:'my-change-set', url:'https://s3.amazonaws.com/my-templates-bucket/template.yaml')
```

or specify a raw template:
```groovy
cfnCreateChangeSet(stack:'my-stack', changeSet:'my-change-set', template: 'my template body')
```

By default the `cfnCreateChangeSet` step creates a change set for creating a new stack if the specified stack does not exist, this behaviour can be overridden by passing `create: 'false'` as parameter :
```groovy
cfnCreateChangeSet(stack:'my-stack', changeSet:'my-change-set', url:'https://s3.amazonaws.com/my-templates-bucket/template.yaml', create: 'false')
```
In above example if `my-stack` already exists, a change set stack with change set will be created, and if it doesnt exist no actions would be performed.


In a case where CloudFormation needs to use a different IAM Role for creating or updating the stack than the one currently in effect, you can pass the complete Role ARN to be used as `roleArn` parameter. i.e:
```groovy
cfnCreateChangeSet(stack:'my-stack', changeSet:'my-change-set', url:'https://s3.amazonaws.com/my-templates-bucket/template.yaml', roleArn: 'arn:aws:iam::123456789012:role/S3Access')
```

You can specify rollback triggers for the stack update:
```groovy
cfnCreateChangeSet(stack:'my-stack', changeSet:'my-change-set', url:'https://s3.amazonaws.com/my-templates-bucket/template.yaml', rollbackTimeoutInMinutes: 10, rollbackTriggers: ['AWS::CloudWatch::Alarm=arn:of:cloudwatch:alarm'])
```

Note: When creating a change set for a non-existing stack, either `file` or `url` are required. When updating it, omitting both parameters will keep the stack's current template.

## cfnExecuteChangeSet

Execute a previously created change set to create or update a CloudFormation stack. All the necessary information, like parameters and tags, were provided earlier when the change set was created.

To prevent running into rate limiting on the AWS API you can change the default polling interval of 1000 ms using the parameter `pollIntervall`. Using the value `0` disables event printing.

```groovy
def outputs = cfnExecuteChangeSet(stack:'my-stack', changeSet:'my-change-set', pollInterval:1000)
```

## cfnUpdateStackSet

Create a stack set. Similar options to cfnUpdate. Will monitor the resulting StackSet operation and will fail the build step if the operation does not complete successfully.

To prevent running into rate limiting on the AWS API you can change the default polling interval of 1000 ms using the parameter `pollIntervall`. Using the value `0` disables event printing.

```groovy
  cfnUpdateStackSet(stackSet:'myStackSet', url:'https://s3.amazonaws.com/my-templates-bucket/template.yaml')
```

To set a custom administrator role ARN:
```groovy
  cfnUpdateStackSet(stackSet:'myStackSet', url:'https://s3.amazonaws.com/my-templates-bucket/template.yaml', administratorRoleArn: 'mycustomarn')
```

To set a operation preferences:
```groovy
  cfnUpdateStackSet(stackSet:'myStackSet', url:'https://s3.amazonaws.com/my-templates-bucket/template.yaml', operationPreferences: [failureToleranceCount: 5])
```

When the stack set gets really big, the recommendation from AWS is to batch the update requests. This option is *not* part of the AWS API, but is an implementation to facilitate updating a large stack set.
To automatically batch via region (find all stack instances, group them by region, and submit each region separately): (
```groovy
  cfnUpdateStackSet(stackSet:'myStackSet', url:'https://s3.amazonaws.com/my-templates-bucket/template.yaml', batchingOptions: [regions: true])
```

## cfnDeleteStackSet

Deletes a stack set.

To prevent running into rate limiting on the AWS API you can change the default polling interval of 1000 ms using the parameter `pollIntervall`. Using the value `0` disables event printing.

```groovy
  cfnDeleteStackSet(stackSet:'myStackSet')
```

## snsPublish

Publishes a message to SNS.
Note that the optional parameter `messageAttributes` is assuming string only values.

```groovy
snsPublish(topicArn:'arn:aws:sns:us-east-1:123456789012:MyNewTopic', subject:'my subject', message:'this is your message', messageAttributes: ['k1': 'v1', 'k2': 'v2'])
```

## deployAPI

Deploys an API Gateway definition to a stage.

```groovy
deployAPI(api:'myApiId', stage:'Prod')
```

Additionally you can specify a description and stage variables.

```groovy
deployAPI(api:'myApiId', stage:'Prod', description:"Build: ${env.BUILD_ID}", variables:['key=value'])
```

## awaitDeploymentCompletion

Awaits for a CodeDeploy deployment to complete.

The step runs within the `withAWS` block and requires only one parameter:

* deploymentId (the AWS CodeDeploy deployment id: e.g. 'd-3GR0HQLDN')

Simple await:
```groovy
awaitDeploymentCompletion('d-3GR0HQLDN')
```

Timed await:
```groovy
timeout(time: 15, unit: 'MINUTES'){
    awaitDeploymentCompletion('d-3GR0HQLDN')
}
```

## listAWSAccounts

Retrieves the list of all AWS accounts of the organization. This step can only be run in the master account.

The step returns an array of Account objects with the following fields:

* id - the account id
* arn - the organizations ARN
* name - the account name
* safeName - the name converted to only contain lower-case, numbers and hyphens
* status - the account status

```groovy
def accounts = listAWSAccounts()
```

You can specify a parent id (Root, Orga unit) with the optional parameter `parent`

```groovy
def accounts = listAWSAccounts('ou-1234-12345678')
```

## updateIdP

Create or update a SAML identity provider with the given metadata document.

The step returns the ARN of the created identity provider.

```groovy
def idp = updateIdP(name: 'nameToCreateOrUpdate', metadata: 'pathToMetadataFile')
```

## updateTrustPolicy

Update the assume role trust policy of the given role using the provided file.

```groovy
updateTrustPolicy(roleName: 'SomeRole', policyFile: 'path/to/somefile.json')
```

## setAccountAlias

Create or update the AWS account alias.

```groovy
setAccountAlias(name: 'awsAlias')
```

## ecrDeleteImages

Delete images in a repository.

```groovy
ecrDeleteImages(repositoryName: 'foo', imageIds: ['imageDigest': 'digest', 'imageTag': 'tag'])
```

## ecrListImages

List images in a repository.

```groovy
def images = ecrListImages(repositoryName: 'foo')
```

## ecrLogin

Create login string to authenticate docker with the ECR.

The step returns the shell command to perform the login.

```groovy
def login = ecrLogin()
```

For older versions of docker that need the email parameter use:

```groovy
def login = ecrLogin(email:true)
```

It's also possible to specify AWS accounts to perform ECR login into:

```groovy
def login = ecrLogin(registryIds: ['123456789', '987654321'])
```

## ecrSetRepositoryPolicy

Sets the json policy document containing ECR permissions.

* registryId - The AWS account ID associated with the registry that contains the repository.
* repositoryName - The name of the repository to receive the policy.
* policyText - The JSON repository policy text to apply to the repository. For more information, see [Amazon ECR Repository Policy Examples](https://docs.aws.amazon.com/AmazonECR/latest/userguide/RepositoryPolicyExamples.html) in the _Amazon Elastic Container Registry User Guide_. 

The step returns the object returned by the command.
* Note - make sure you set the correct region in the credentials in order to find the repository

```groovy
def result = ecrSetRepositoryPolicy(registryId: 'my-registryId',
                                     repositoryName: 'my-repositoryName',
                                     policyText: 'json-policyText'
)
```

```groovy
def policyFile ="${env.WORKSPACE}/policyText.json"
def policyText = readFile file: policyFile
def result = ecrSetRepositoryPolicy(registryId: 'my-registryId',
                                     repositoryName: 'my-repositoryName',
                                     policyText: policyText
)
```

## invokeLambda

Invoke a Lambda function.

The step returns the object returned by the Lambda.

```groovy
def result = invokeLambda(
	functionName: 'myLambdaFunction',
	payload: [ "key": "value", "anotherkey" : [ "another", "value"] ]
)
```

Alternatively payload and return value can be Strings instead of Objects:

```groovy
String result = invokeLambda(
	functionName: 'myLambdaFunction',
	payloadAsString: '{"key": "value"}',
	returnValueAsString: true
)
```

## lambdaCleanupVersions

Cleans up lambda function versions older than the daysAgo flag.
The main use case around this is for tooling like AWS Serverless Application Model.
It creates lambda functions, but marks them as `DeletionPolicy: Retain` so the versions are never deleted.
Overtime, these unused versions will accumulate and the account/region might hit the limit for maximum storage of lambda functions.

```groovy
lambdaCleanupVersions(
	functionName: 'myLambdaFunction',
	daysAgo: 14
)
```

To discover and delete all old versions of functions created by a AWS CloudFormation stack:

```groovy
lambdaCleanupVersions(
	stackName: 'myStack',
	daysAgo: 14
)
```

## ec2ShareAmi

Share an AMI image to one or more accounts

```groovy
ec2ShareAmi(
    amiId: 'ami-23842',
    accountIds: [ "0123456789", "1234567890" ]
)
```

## elbRegisterInstance

Registers a target to a Target Group.

```groovy
elbRegisterInstance(
    targetGroupARN: 'arn:aws:elasticloadbalancing:us-west-2:123456789:targetgroup/my-load-balancer/123456789',
    instanceID: 'i-myid',
    port: 8080
)
```

## elbDeregisterInstance

Deregisters a target from a Target Group.

```groovy
elbDeregisterInstance(
    targetGroupARN: 'arn:aws:elasticloadbalancing:us-west-2:123456789:targetgroup/my-load-balancer/123456789',
    instanceID: 'i-myid',
    port: 8080
)
```

## elbIsInstanceRegistered

Check if target has registered and healthy.

The step returns true or false.


```groovy
elbIsInstanceRegistered(
    targetGroupARN: 'arn:aws:elasticloadbalancing:us-west-2:123456789:targetgroup/my-load-balancer/123456789',
    instanceID: 'i-myid',
    port: 8080
)
```

## elbIsInstanceDeregistered

Check if target has completed removed from the Target Group.

The step returns true or false.

```groovy
elbIsInstanceDeregistered(
    targetGroupARN: 'arn:aws:elasticloadbalancing:us-west-2:123456789:targetgroup/my-load-balancer/123456789',
    instanceID: 'i-myid',
    port: 8080
)
```

# Changelog

## current master
* Add batching support for cfnUpdateStackSet
* Retry stack set deployments on LimitExceededException when there are too many StackSet operations occuring.
* Add ELB methods to mangage instances during deployemnts ( [elbRegisterInstance](#elbRegisterInstance), [elbDeregisterInstance](#elbDeregisterInstance), [elbIsInstanceRegistered](#elbIsInstanceRegistered), [elbIsInstanceDeregistered](#elbIsInstanceDeregistered) )

## 1.40
* add `registryIds` argument to `ecrLogin`
* fix CloudFormation CreateChangeSet for a stack with IN_REVIEW state
* Add lambdaCleanupVersions
* Add ecrSetRepositoryPolicy

## 1.39
* add `notificationARNs` argument to `cfnUpdate` and `cfnUpdateStackSet`
* Handle `Stopped` status for CodeDeployment deployments

## 1.38
* Add ecrListImages
* Add ecrDeleteImages
* Fix instances of TransferManger from aws-sdk were never closed properly
* add `s3DoesObjectExist` step

## 1.37
* add `parent` argument to `listAWSAccounts`
* Add Xerces dependency to fix #117
* Add ability to upload a String to an S3 object by adding `text` option to `s3Upload`
* Add redirect location option to `s3Upload`
* Add support for SessionToken when using iamMfaToken #170

## 1.36
* add `jenkinsStackUpdateStatus` to stack outputs. Specifies if stack was modified
* Increase AWS SDK retry count from default (3 retries) to 10 retries
* Add CAPABILITY_AUTO_EXPAND to Cloudformation Stacks and Stacksets

## 1.35
* fixing regression that `region` was now mandatory on `withAWS`
* fix s3Upload step doesn't allow to set object metadata with values containing colon character (#141)

## 1.34 (*use >= 1.35!!*)
* add support for assume role with SAML assertion authentication (#140)

## 1.33
* fix timeout settings for `cfnExecuteChangeSet` (#132)
* fixed tagsFile parameter being ignored when the tags parameter equals null.
* fixed error string matching for empty change set in cloudformation
* automatically derive partition from region (#137)

## 1.32
* add paging for listAWSAccounts (#128)
* retry stackset update on StaleRequestException
* add support for OperationPreferences in cfnUpdateStackSet

## 1.31
* handle throttles from cloudformation stackset operations
* fixed regression in cfnUpdate

## 1.30 (*use >= 1.31!!*)
* allow the customization of setting a roleSessionName
* content encoding can be specified in `s3Upload` step
* allow configuration of cloudformation stack timeout

## 1.29
* fix issues with stack timeouts

## 1.28
* use SynchronousNonBlockingStepExecution for long running AWS steps to allow the pipeline step to be aborted
* use custom polling strategy for cloudformation waiters to speed up pipeline feedback from cloudformation changes
* add support for tagsFile in cfnUpdate, cfnCreateChangeSet, cfnUpdateStackSet
* add `administratorRoleArn` to cfnUpdateStackSet

## 1.27
* add rollback configuration to `cfnUpdate`
* add `enableTerminationProtection` to `cfnUpdate`
* add retries around cfnUpdateStackSet when stack set is currently busy
* add `s3Copy` step
* allow upload of single files to bucket root

## 1.26
* add duration to withAWS
* add sseAlgorithm to s3Upload
* add messageAttributes in snsPublish
* add ability to utilize AWS Credentials Plugin
* add iamMfaToken to withAWS step

## 1.25
* Return ValidateTemplate response on cfnValidate
* Add s3PresignURL
* use `SynchronousNonBlockingStepExecution` for some steps for better error handling
* allow s3Delete to empty bucket (#63)
* set minimal Jenkins version to 2.60.3 and switch to Java 8
* fix `cfnExecuteChange` step (#67)

## 1.24
* Do not fail job on empty change set creation
* Add support for maps with cloudformation parameters.
* Allow cfnCreateStackSet, cfnUpdate, cfnCreateChangeSet to take a raw (string) template
* add `ec2ShareAmi` step

## 1.23
* add updateTrustPolicy step (#48)
* fix NPE in ProxyConfiguration (#51)
* fix strange upload behavior when uploading file to path (#53)
* add support for Stacksets
* return change set from step

## 1.22
* Add `kmsId` parameter to `s3Upload`.
* Fix more characters in RoleSessionName
* Allow upload of multiple files to bucket root (#41)
* Use DELETE method for failed stack creation. (*Changed behavior*)
* Use Jenkins proxy config when available
* retrieve all CloudFormation exports (#42)
* s3Upload returns the S3 URL of the target

## 1.21
* Fix: `s3Upload` did not work in Jenkins 2.102+ (#JENKINS-49025)
* Fix: RoleSessionName (slashes in buildNumber) in `withAWS` step for assume role. (#JENKINS-45807)
* Doc: Clarify usage of metadata

## 1.20
* Fix: `setAccountAlias` broken during code cleanup

## 1.19 (*use >= 1.20!!*)
* Fix: RoleSessionName (decoding job name HTML url encoding) in `withAWS` step for assume role.
* Add `onFailure` option when creating a stack to allow changed behaviour.
* Add the possibility to define specific content-type for s3Upload step.
* Support roleArns with paths
* add `setAccountAlias` step

## 1.18
* Fixed regression added by #27 (#JENKINS-47912)

## 1.17 (*use >= 1.18!!*)
* Add policy for withAWS support - allows an additional policy to be combined with the policy associated with the assumed role.
* Add `cfnCreateChangeSet` step
* Add `cfnExecuteChangeSet` step
* Add endpoint-url for withAWS support - allows configuring a non-AWS endpoint for internally-hosted clouds.
* Add support for String payload and return value in `invokeLambda` step
* Support additional S3 options: pathStyleAccessEnabled and payloadSigningEnabled
* Update AWS SDK to 1.11.221
* Fix: return value of `invokeLambda` is now serializable

## 1.16
* Add federatedUserId for withAWS support - generates temporary aws credentials for federated user which gets logged in CloudTrail
* Add return value to `awsIdentity` step
* Add `ecrLogin` step
* Add `invokeLambda` step
* Add `cacheControl` to `s3Upload`step

## 1.15
* Add the following options to `S3Upload` : `workingDir`, `includePathPattern`, `excludePathPattern`, `metadatas` and `acl`

## 1.14
* fixes JENKINS-45964: Assuming Role does not work in AWS-China
* Allow opt out for by-default stack creation with `cfnUpdate`
* roleArn parameter support for `cfnUpdate`
* Fix: Rendering the paths for S3* steps manually (Windows)
* fixes JENKINS-46247: Fix credentials scope in withAWS step and add a credentials dropdown
* add `safeName` to `listAWSAccounts` step

## 1.13
* Add `s3FindFiles` step
* add `updateIdP` step
* Fix creation of RoleSessionName
* Fix bug when missing DescribeStacks permission

## 1.12
* Make polling interval for CFN events configurable #JENKINS-45348
* Add `awaitDeploymentCompletion` step
* Add `s3Delete` step
* Add `listAWSAccounts` step

## 1.11
* Replace slash in RoleSessionName coming from Job folders

## 1.10
* improve S3 download logging #JENKINS-44903
* change RoleSessionName to include job name and build number
* add the ability to use a URL in cfnValidate

## 1.9
* add support for create stack timeout
* add the ability to use a URL in cfnUpdate
* add deployAPI step

## 1.8
* add support for externalId for role changes
* allow path to be null or empty in S3 steps

## 1.7
* fix environment for withAWS step
* add support for recursive S3 upload/download

## 1.6
* fix #JENKINS-42415 causing S3 errors on slaves
* add paramsFile support for cfnUpdate
* allow the use of Jenkins credentials for AWS access #JENKINS-41261

## 1.5
* add cfnExports step
* add cfnValidate step
* change how s3Upload works to use the aws client to guess the correct content type for the file.

## 1.4
* add empty checks for mandatory strings
* use latest AWS SDK
* add support for CloudFormation stack tags

## 1.3
* add support for publishing messages to SNS
* fail step on errors during CloudFormation actions

## 1.2
* add proxy support using standard environment variables
* add cfnDescribe step to fetch stack outputs

## 1.1
* fixing invalidation of CloudFront distributions
* add output of stack creation, updates and deletes
* Only fetch AWS environment once
* make long-running steps async

## 1.0
* first release containing multiple pipeline steps
