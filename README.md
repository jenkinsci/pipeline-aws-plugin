[![Gitpod ready-to-code](https://img.shields.io/badge/Gitpod-ready--to--code-blue?logo=gitpod)](https://gitpod.io/#https://github.com/jenkinsci/pipeline-aws-plugin)
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
* [createDeployment](#createDeployment)
* [awaitDeploymentCompletion](#awaitdeploymentcompletion)
* [listAWSAccounts](#listawsaccounts)
* [updateIdP](#updateidp)
* [setAccountAlias](#setaccountalias)
* [ecrDeleteImage](#ecrdeleteimage)
* [ecrListImages](#ecrlistimages)
* [ecrLogin](#ecrlogin)
* [ecrSetRepositoryPolicy](#ecrsetrepositorypolicy)
* [invokeLambda](#invokelambda)
* [lambdaVersionCleanup](#lambdacleanupversions)
* [ec2ShareAmi](#ec2ShareAmi)
* [elbRegisterInstance](#elbRegisterInstance)
* [elbDeregisterInstance](#elbDeregisterInstance)
* [elbIsInstanceRegistered](#elbIsInstanceRegistered)
* [elbIsInstanceDeregistered](#elbIsInstanceDeregistered)
* [ebCreateApplication](#ebCreateApplication)
* [ebCreateApplicationVersion](#ebCreateApplicationVersion)
* [ebCreateConfigurationTemplate](#ebCreateConfigurationTemplate)
* [ebCreateEnvironment](#ebCreateEnvironment)
* [ebSwapEnvironmentCNAMEs](#ebSwapEnvironmentCNAMEs)
* [ebWaitOnEnvironmentStatus](#ebWaitOnEnvironmentStatus)
* [ebWaitOnEnvironmentHealth](#ebWaitOnEnvironmentHealth)

[**see the changelog for release information**](#changelog)

# Upgrading from 1.45

The next release replaces the AWS SDK for Java 1.x - which reached end of life on 31 December 2025 -
with the AWS SDK for Java 2.x throughout the plugin
([#341](https://github.com/jenkinsci/pipeline-aws-plugin/issues/341),
[JENKINS-73650](https://issues.jenkins.io/browse/JENKINS-73650)). Step names and parameters are
unchanged, and the legacy v1 spellings of parameter values (`acl: 'PublicRead'`, CloudFormation
capabilities, Elastic Beanstalk health and status strings, ECR filters) are still accepted, so most
pipelines need no edit at all.

What you do need to check before upgrading:

* **Your controller must run Jenkins 2.541.1 or newer**, and `aws-credentials` 265 or newer (the
  version the plugin BOM supplies; the credentials API this plugin needs first shipped in
  238.v8fb_588a_2b_e67). 1.45 is the last release usable on older controllers.
* **`s3PresignURL`** now presigns a specific S3 operation rather than signing an arbitrary URL, so
  `httpMethod` must be one of `GET`, `PUT`, `DELETE`, `HEAD` - `POST` and `PATCH` are rejected - and
  `durationInSeconds` must be within 1..604800 (7 days).
* **`acl: 'LogDeliveryWrite'`** is rejected by `s3Upload` and `s3Copy`. It is a bucket ACL with no
  object-level counterpart; S3 rejected it on an object before, too.
* **Steps that returned raw AWS response objects now return maps**: `cfnUpdateStackSet`,
  `ecrDeleteImage` and `ecrSetRepositoryPolicy`. Read `result.stackSet.stackSetId` rather than
  `result.getStackSet().getStackSetId()`. Sandboxed pipelines could never read those fields at all,
  so only scripts running outside the sandbox are affected.
* **`cfnValidate`** no longer returns the undocumented `sdkResponseMetadata` and `sdkHttpMetadata`
  keys. The documented fields are unchanged.
* **Catching AWS exceptions by type no longer works.** Errors from every migrated step now surface
  as `software.amazon.awssdk...` exceptions rather than `com.amazonaws...AmazonServiceException`, and
  around an agent-side `s3Upload`/`s3Download` they arrive as `hudson.remoting.ProxyException`. The
  v1 classes are still on the classpath, so a `catch (com.amazonaws...)` still compiles and simply
  stops matching - the build fails where it used to recover. Catch `Exception` and inspect the
  message.
* **`withAWS(credentials: ...)` and `withAWS(samlAssertion: ...)` now clear any inherited
  `AWS_SESSION_TOKEN`**, so that a credential carrying no token cannot be paired with a stale one. An
  `AWS_SESSION_TOKEN` you set deliberately - from `withEnv`, a global environment variable, or a
  credentials binding - is dropped too, because the step cannot tell the two apart; set it inside the
  block rather than around it. The drop is logged, unless `role` or `federatedUserId` is also set:
  those mint a fresh session from STS and export its token, so the block has a valid one and nothing
  is missing.

The `current master` section of the changelog below - this release, until it is tagged - lists these
and the smaller behaviour changes, including several long-standing bugs fixed along the way, in full.

Note that the AWS SDK v1 jar is still on the runtime classpath, because `aws-credentials` continues
to declare it ([aws-credentials-plugin#284](https://github.com/jenkinsci/aws-credentials-plugin/pull/284)).
No code in this plugin uses it.

# Primary/Agent setups

This plugin is not optimized to setups with a primary and multiple agents.
Only steps that touch the workspace are executed on the agents while the rest is executed on the master.

For the best experience make sure that primary and agents have the same IAM permission and networking capabilities.

## Tuning

These environment variables adjust how the plugin's AWS clients behave. Set them the way you would
any other environment variable - `environment { }`, `withEnv`, or the controller's global
configuration - and they apply to the steps that run inside that scope.

| Variable | Default | Effect |
| --- | --- | --- |
| `AWS_SDK_RETRIES` | `10` | Retries per request, on top of the initial attempt. |
| `AWS_SDK_SOCKET_TIMEOUT` | `50000` | Socket timeout in milliseconds. |
| `AWS_SDK_MAX_CONNECTIONS` | `500` | Maximum concurrent connections across all synchronous AWS requests in the controller. |

`AWS_SDK_MAX_CONNECTIONS` is worth raising only if a very wide `parallel` block fails with
`ConnectionPoolTimeoutException`; the limit is process-wide because the underlying connection pool is
shared, and it is a ceiling rather than a preallocation, so raising it costs nothing while idle.

## Retrieve credentials from node

By default, credentials lookup is done on the master node for all steps.
To enable credentials lookup on the current node, enable `Retrieve credentials from node` in Jenkins global configuration. This is globally applicable and restricts all access to the master's credentials.

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
withAWS(role:'admin', roleAccount:'123456789012', externalId: 'my-external-id', policy: '{"Version":"2012-10-17","Statement":[{"Sid":"Stmt1","Effect":"Deny","Action":"s3:DeleteObject","Resource":"*"}]}', duration: 3600, roleSessionName: 'my-custom-session-name') {
    // do something
}
```

Assume federated user id information (federatedUserId is optional - if specified it generates a set of temporary credentials and allows you to push a federated user id into cloud trail for auditing. duration is optional - if specified it represents the maximum amount of time in seconds the session may persist for, defaults to 3600.):

```groovy
withAWS(region:'eu-central-1',credentials:'nameOfSystemCredentials',federatedUserId:"${submitter}@${releaseVersion}", duration: 3600) {
    // do something
}
```

Authentication with a SAML assertion (fetched from your company IdP) by assuming a role

```groovy
withAWS(role: 'myRole', roleAccount: '123456789', principalArn: 'arn:aws:iam::123456789:saml-provider/test', samlAssertion: 'base64SAML', region:'eu-west-1') {
  // do something
}
```

Authentication by retrieving credentials from the node in scope

```groovy
node('myNode') { // Credentials will be fetched from this node
  withAWS(role: 'myRole', roleAccount: '123456789', region:'eu-west-1', useNode: true) {
    // do something
  }
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
s3Upload(bucket:"my-bucket", path:'path/to/targetFolder/', includePathPattern:'**/*.ttf', workingDir:'dist', contentType:'application/x-font-ttf', contentDisposition:'attachment')
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

Tags can be added to uploaded files.

```groovy
s3Upload(file: 'file.txt', bucket: 'my-bucket', tags: '[tag1:value1, tag2:value2]')

def tags=[:]
tags["tag1"]="value1"
tags["tag2"]="value2"

s3Upload(file: 'file.txt', bucket: 'my-bucket', tags: tags.toString())
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

Each object is written under the target folder at its full key path, not flattened: the second
example above puts `path/to/sourceFolder/a/b.txt` at `targetFolder/path/to/sourceFolder/a/b.txt`.
Zero-byte "folder marker" objects - the ones the S3 console creates for an empty folder - are
skipped. The build fails if any object under the prefix could not be downloaded.

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

The duration can be overridden, from 1 second up to 604800 (7 days, the maximum a SigV4
query-parameter signature can express). Anything outside that range fails the step:
```groovy
def url = s3PresignURL(bucket: 'mybucket', key: 'mykey', durationInSeconds: 300) //5 minutes
```

The method can also be overridden. `GET` (the default), `PUT`, `DELETE` and `HEAD` are supported:
```groovy
def url = s3PresignURL(bucket: 'mybucket', key: 'mykey', httpMethod: 'PUT')
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

To prevent running into rate limiting on the AWS API you can change the default polling interval of 1000 ms using the parameter `pollInterval`. Using the value `0` disables event printing.

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

To prevent running into rate limiting on the AWS API you can change the default polling interval of 1000 ms using the parameter `pollInterval`. Using the value `0` disables event printing.  

Note: When deleting a stack only 'stack' parameter is required.

```groovy
cfnDelete(stack:'my-stack', pollInterval:1000, retainResources :['mylogicalid'], roleArn: 'my-arn', clientRequestToken: 'my-request-token')
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


To prevent running into rate limiting on the AWS API you can change the default polling interval of 1000 ms using the parameter `pollInterval`. Using the value `0` disables event printing.

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

To prevent running into rate limiting on the AWS API you can change the default polling interval of 1000 ms using the parameter `pollInterval`. Using the value `0` disables event printing.

```groovy
def outputs = cfnExecuteChangeSet(stack:'my-stack', changeSet:'my-change-set', pollInterval:1000)
```

## cfnUpdateStackSet

Create a stack set. Similar options to cfnUpdate. Will monitor the resulting StackSet operation and will fail the build step if the operation does not complete successfully.

`pollInterval` (default 1000 ms) is the delay between the `DescribeStackSetOperation` calls this step
makes while waiting for an update to finish; raise it to stay clear of AWS API rate limits. This step
prints no stack events, so unlike `cfnUpdate` the value `0` does not disable anything; it is treated
as one second, so that it cannot become a tight loop against the AWS API. (On the `create` path the
step checks the new stack set's status instead, and since the API reports a stack set as only
`ACTIVE` or `DELETED` that check settles on the first call rather than polling.)

Two things to know about the wait:

* It has no timeout - it waits for as long as the operation takes. `timeoutInMinutes` and
  `timeoutInSeconds` are accepted for consistency with `cfnUpdate` but do not apply here.
* It fails, reporting the status it saw, if the stack set reaches a state other than the one being
  waited for - `DELETED` while waiting for `ACTIVE`, for instance - rather than waiting for a state
  that can no longer arrive.

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

The step returns the stack set's description as a map, so its fields are readable from a sandboxed
pipeline:

```groovy
  def result = cfnUpdateStackSet(stackSet:'myStackSet', url:'https://s3.amazonaws.com/my-templates-bucket/template.yaml')
  echo "stack set id: ${result.stackSet.stackSetId}"
```

`create` defaults to `true`, so a missing stack set is created. Passing `create: false` skips
creation instead, and in that case the step returns `null` rather than a map - guard the return
value if you use it:

```groovy
  def result = cfnUpdateStackSet(stackSet:'myStackSet', url:'...', create: false)
  if (result != null) {
    echo "stack set id: ${result.stackSet.stackSetId}"
  }
```

## cfnDeleteStackSet

Deletes a stack set. The step submits the deletion and returns; it does not wait for AWS to finish,
so the `pollInterval` parameter it accepts has no effect.

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

## createDeployment

Deploys an application revision through the specified deployment group (AWS CodeDeploy)

From S3 bucket:
```groovy
createDeployment(
        s3Bucket: 'jenkins.bucket',
        s3Key: 'artifacts/SimpleWebApp.zip',
        s3BundleType: 'zip', // [Valid values: tar | tgz | zip | YAML | JSON]
        applicationName: 'SampleWebApp',
        deploymentGroupName: 'SampleDeploymentGroup',
        deploymentConfigName: 'CodeDeployDefault.AllAtOnce',
        description: 'Test deploy',
        waitForCompletion: 'true',
        //Optional values 
        ignoreApplicationStopFailures: 'false',
        fileExistsBehavior: 'OVERWRITE'// [Valid values: DISALLOW, OVERWRITE, RETAIN]
)
```

From GitHub:
```groovy
createDeployment(
        gitHubRepository: 'MykhayloGnylorybov/AwsCodeDeployArtifact',
        gitHubCommitId: 'e9ee742f44c9a0f97ee3aa94593e7b6aad6e2d14',
        applicationName: 'SampleWebApp',
        deploymentGroupName: 'SampleDeploymentGroup',
        deploymentConfigName: 'CodeDeployDefault.AllAtOnce',
        description: 'Test deploy',
        waitForCompletion: 'true'
)
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
* state - the account state: `PENDING_ACTIVATION`, `ACTIVE`, `SUSPENDED`, `PENDING_CLOSURE` or `CLOSED`
* status - the account status, kept for compatibility; prefer `state`

AWS retires the Organizations `Status` field on **9 September 2026** in favour of `State`; both are
returned at present. Both keys are provided and each falls back to the other, so neither is `null`
for a response that carries only one - whichever one that is. A response can carry only `Status`
today, since `endpointUrl` can point at a service that does not return `State`.

`state` is the one to use going forward. Note it has two values `status` never had -
`PENDING_ACTIVATION` and `CLOSED` - so a pipeline that enumerates the possible values should handle
them. Wherever a response still carries `Status`, `status` reports exactly what it always did.

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

## ecrDeleteImage

Delete images in a repository.

```groovy
ecrDeleteImage(repositoryName: 'foo', imageIds: [['imageDigest': 'digest', 'imageTag': 'tag']])
```

The step returns a list of maps, each with `imageTag` and `imageDigest`, for the images that were
deleted.

## ecrListImages

List images in a repository. The step returns a list of maps, each with `imageTag` and
`imageDigest`.

```groovy
def images = ecrListImages(repositoryName: 'foo')
```

Pass `filter` to restrict the result by tag status. `tagStatus` accepts `TAGGED`, `UNTAGGED` or
`ANY` (the default):

```groovy
def untagged = ecrListImages(repositoryName: 'foo', filter: [tagStatus: 'UNTAGGED'])
ecrDeleteImage(repositoryName: 'foo', imageIds: untagged)
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

The step returns a map with `registryId`, `repositoryName` and `policyText`.
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

## lambdaVersionCleanup

Cleans up lambda function versions older than the daysAgo flag.
The main use case around this is for tooling like AWS Serverless Application Model.
It creates lambda functions, but marks them as `DeletionPolicy: Retain` so the versions are never deleted.
Overtime, these unused versions will accumulate and the account/region might hit the limit for maximum storage of lambda functions.

```groovy
lambdaVersionCleanup(
	functionName: 'myLambdaFunction',
	daysAgo: 14
)
```

To discover and delete all old versions of functions created by a AWS CloudFormation stack:

```groovy
lambdaVersionCleanup(
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

## ebCreateApplication
Creates a new Elastic Beanstalk application.

Arguments:
 * applicationName _(Required)_ - Name of the application to be created
 * description - Descriptive text to add to the application

[AWS reference](https://docs.aws.amazon.com/elasticbeanstalk/latest/api/API_CreateApplication.html)

```groovy
ebCreateApplication(
    applicatName: "my-application",
    description: "My first application"
)
```

## ebCreateApplicationVersion
Creates a new deployable version for an existing Elastic Beanstalk application.
This version created is based on files uploaded to an S3 bucket, that are used to create a deployable version of the application.
This version label can be used to deploy a new environment.

Arguments:
 * applicationName _(Required)_ - Name of the application where the new version should be created
 * versionLabel _(Required)_ - Name of the version to be created
 * s3Bucket _(Required)_ - Name of the S3 Bucket where the source code / executable of this version exists
 * s3Key: _(Required)_ - Path in the S3 Bucket where the source code / executable of this version exists
 * description - Descriptive text of the application version

[AWS reference](https://docs.aws.amazon.com/elasticbeanstalk/latest/api/API_CreateApplicationVersion.html)

```groovy
ebCreateApplicationVersion(
    applicationName: "my-application",
    versionLabel: "my-application-1.0.0",
    s3Bucket: "my-bucket",
    s3Key: "my-application.jar",
    description: "My first application version"
)
```

## ebCreateConfigurationTemplate
Creates a new deployable version for an existing Elastic Beanstalk application.
This version created is based on files uploaded to an S3 bucket, that are used to create a deployable version of the application.
This version label can be used to deploy a new environment.

Arguments:
 * applicationName _(Required)_ - Name of the application where the new configuration template should be created
 * templateName _(Required)_ - Name of the configuration template to be created
 * environmentId - Id of the environment to use as a source for the new configuration template. _Required if no solutionStackName or sourceConfiguration are provided_
 * solutionStackName - Solution stack string for the new configuration template. List of supported platforms can be seen in 
[AWS](https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/concepts.platforms.html). _Required if no environmentId or sourceConfiguration are provided_
 * sourceConfigurationApplication - Name of the application that has the source configuration to copy over. Should be used in conjunction with sourceConfigurationTemplate. _Required if no environmentId or solutionStackName are provided_ 
 * sourceConfigurationTemplate - Name of the configuration to be used as a source for the new configuration template. Should be used in conjunction with sourceConfigurationApplication. _Required if no environmentId or solutionStackName are provided_
 * description - Descriptive text of the application configuration template

[AWS reference](https://docs.aws.amazon.com/elasticbeanstalk/latest/api/API_CreateConfigurationTemplate.html)

```groovy
// Create configuration template based on existing environment
ebCreateConfigurationTemplate(
    applicationName: "my-application",
    templateName: "my-application-production-template",
    environmentId: "my-application-production",
    description: "Configuration template for the production environment of my application"
)

// Create configuration template based on a solution stack
ebCreateConfigurationTemplate(
    applicationName: "my-application",
    templateName: "my-application-production-template",
    solutionStackName: "64bit Amazon Linux 2018.03 v3.3.9 running Tomcat 8.5 Java 8",
    description: "Configuration template for the production environment of my application"
)

// Create configuration template based on an existing configuration template
ebCreateConfigurationTemplate(
    applicationName: "my-application",
    templateName: "my-application-production-template",
    sourceConfigurationApplication: "my-other-application",
    sourceConfigurationTemplate: "my-other-application-production-template",
    description: "Configuration template for the production environment of my application"
)
```

## ebCreateEnvironment
Creates a new environment for an existing Elastic Beanstalk application.
This environment can be created based on existing configuration templates and application versions for that application.

Arguments:
 * applicationName _(Required)_ - Name of the application where the new environment should be created
 * environmentName _(Required)_ - Name of the environment to be created
 * templateName - Name of the configuration template to use with the environment to be created. _Mutually exclusive with solutionStackName_
 * solutionStackName - Solution stack string for the new environment. List of supported platforms can be seen in 
[AWS](https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/concepts.platforms.html). _Mutually exclusive with templateName_
 * versionLabel - Name of the application version to be deployed in the new environment
 * updateOnExisting - If set to false the command will throw an exception if the environment already exists. Otherwise, in case the environment already exists, it will be updated. _Defaults to true_
 * description - Descriptive text of the environment

[AWS reference](https://docs.aws.amazon.com/elasticbeanstalk/latest/api/API_CreateEnvironment.html)

```groovy
// Create environment from existing configuration template
ebCreateEnvironment(
    applicationName: "my-application",
    environmentName: "production",
    templateName: "my-application-production-template",
    versionLabel: "my-application-1.0.0",
    description: "Production environment of my application"
)

// Create environment with no configuration template, using a Supported Platform string
ebCreateEnvironment(
    applicationName: "my-application",
    environmentName: "production",
    solutionStackName: "64bit Amazon Linux 2018.03 v3.3.9 running Tomcat 8.5 Java 8",
    versionLabel: "my-application-1.0.0",
    description: "Production environment of my application"
)
```

## ebSwapEnvironmentCNAMEs
Swaps the CNAMEs of the environments. This is useful for [Blue-Green deployments](https://en.wikipedia.org/wiki/Blue-green_deployment).

Arguments:
 * sourceEnvironmentId - Id of the source environment. _Should be used with destinationEnvironmentId_
 * sourceEnvironmentName - Name of the source environment. _Should be used with destinationEnvironmentName_
 * sourceEnvironmentCNAME - CNAME of the source environment. If provided, it will be used to lookup the id and name of the source environment.
 * destinationEnvironmentId - Id of the destination environment. _Should be used with sourceEnvironmentId_
 * destinationEnvironmentName - Name of the destination environment. _Should be used with sourceEnvironmentName_
 * destinationEnvironmentCNAME - CNAME of the destunatuin environment. If provided, it will be used to lookup the id and name of the destination environment. 

[AWS reference](https://docs.aws.amazon.com/elasticbeanstalk/latest/api/API_SwapEnvironmentCNAMEs.html  )

```groovy
// Swap CNAMEs using Ids
ebSwapEnvironmentCNAMEs(
    sourceEnvironmentId: "e-65abcdefgh",
    destinationEnvironmentId: "e-66zxcvbdg"
)

// Swap CNAMEs using the environment names
ebCreateEnvironment(
    sourceEnvironmentName: "production",
    destinationEnvironmentName: "production-2"
)

// Swap CNAMEs using the source environment name and destination environment CNAME
ebCreateEnvironment(
        sourceEnvironmentName: "green",
        destinationEnvironmentCNAME: "production.eu-west-1.elasticbeanstalk.com"
)
```

## ebWaitOnEnvironmentStatus
Waits for environment to be in the specified status. 

This can be used to ensure that the environment is ready to accept commands, like an update, or a termination command.
Be aware this does not guarantee that the application has finished starting up. 
If an application has a long startup time, the environment will be ready for new commands before the application has finished the boot.

Arguments:
 * applicationName - Name of the application of that environment
 * environmentName - Name of the environment
 * status - Status to wait for. Valid values: `Launching | Updating | Ready | Terminating | Terminated`. _Defaults to Ready_

```groovy
// Wait for environment to be ready for new commands
ebWaitOnEnvironmentStatus(
    applicationName: "my-application",
    environmentName: "production"
)

// Wait for environment to be terminated
ebWaitOnEnvironmentStatus(
    applicationName: "my-application",
    environmentName: "temporary",
    status: "Terminated"
)
```

## ebWaitOnEnvironmentHealth
Waits for environment to reach the desired health status, and remain there for a minimum amount of time.

This can be used to ensure that the environment has finished the startup process, and that the web application is ready and available.

Arguments:
 * applicationName _(Required)_ - Name of the application of that environment
 * environmentName _(Required)_ - Name of the environment
 * health - Health status to wait for. Valid values: `Green | Yellow | Red | Grey`. _Defaults to Green_
 * stabilityThreshold - Amount of time (in seconds) to wait before considering the status stable. Can be disabled by setting it to 0. _Defaults to 60_

```groovy
// Wait for environment health to be green for at least 1 minute
ebWaitOnEnvironmentHealth(
    applicationName: "my-application",
    environmentName: "production"
)

// Detect immediately if environment becomes red
ebWaitOnEnvironmentHealth(
    applicationName: "my-application",
    environmentName: "temporary",
    health: "Red",
    stabilityThreshold: 0
)
```

# Changelog

## current master

* **Breaking**: requires Jenkins 2.541.1 or newer. The plugin's parent POM and plugin BOM were
  updated to the 2.541 baseline in preparation for the migration to the AWS SDK for Java 2.x
  ([#341](https://github.com/jenkinsci/pipeline-aws-plugin/issues/341),
  [JENKINS-73650](https://issues.jenkins.io/browse/JENKINS-73650)). 1.45 is the last release usable
  on controllers older than 2.541.1.
* Migrated to the AWS SDK for Java 2.x: `snsPublish`, `ec2ShareAmi`, `setAccountAlias`, `updateIdP`,
  `updateTrustPolicy`, `listAWSAccounts`, `elbRegisterInstance`, `elbDeregisterInstance`,
  `elbIsInstanceRegistered`, `elbIsInstanceDeregistered`, `createDeployment`,
  `awaitDeploymentCompletion`, `deployAPI`, `ebCreateApplication`,
  `ebCreateApplicationVersion`, `ebCreateConfigurationTemplate`, `ebCreateEnvironment`,
  `ebSwapEnvironmentCNAMEs`, `ebWaitOnEnvironmentStatus`, `ebWaitOnEnvironmentHealth`,
  `invokeLambda`, `cfInvalidate`, `ecrDeleteImage`, `ecrListImages`, `ecrSetRepositoryPolicy`,
  `ecrLogin`, `lambdaVersionCleanup`, `cfnValidate`, `cfnUpdate`, `cfnDelete`, `cfnDescribe`,
  `cfnExports`, `cfnCreateChangeSet`, `cfnExecuteChangeSet`, `cfnUpdateStackSet`,
  `cfnDeleteStackSet`, `s3Delete`, `s3FindFiles`, `s3DoesObjectExist`, `s3PresignURL`, `s3Copy`,
  `s3Upload`, `s3Download`, `withAWS` and `awsIdentity`. Behaviour and step parameters are unchanged, but AWS
  errors from these steps now surface as SDK v2 exceptions
  (`software.amazon.awssdk.services...Exception`) rather than `com.amazonaws...AmazonServiceException`,
  so build logs and anything scraping them will show different exception names and wording. The ELB
  steps also log the raw AWS response, whose rendering differs between the two SDKs. A
  `cfInvalidate(waitForCompletion: true)` that times out now fails with
  `software.amazon.awssdk.core.exception.SdkClientException` - a core-package exception, not a
  service one, so it does not match the prefix above - rather than
  `com.amazonaws.waiters.WaiterTimedOutException`.
* **Breaking**: `cfnValidate` no longer returns the undocumented `sdkResponseMetadata` and
  `sdkHttpMetadata` keys. They were an artifact of serialising the AWS response object, which
  exposed inherited HTTP plumbing; the documented fields (`description`, `parameters`,
  `capabilities`, `capabilitiesReason`, `declaredTransforms`) are unchanged.
* **Breaking**: `ecrDeleteImage` and `ecrSetRepositoryPolicy` now return maps rather than AWS SDK
  objects - `[[imageTag: ..., imageDigest: ...]]` and
  `[registryId: ..., repositoryName: ..., policyText: ...]` respectively. Reading fields off the
  previous return value was not possible from a *sandboxed* pipeline - Jenkins script security
  rejects field and getter access on SDK model classes - so for sandboxed scripts only `echo`
  output changes, and those values are now readable. Scripts running outside the sandbox (approved
  scripts, shared libraries) read those objects at different levels and must switch to the map keys:
  `result.getRegistryId()` for `ecrSetRepositoryPolicy`'s single object becomes
  `result.registryId`, and `result[0].getImageTag()` for an element of `ecrDeleteImage`'s list
  becomes `result[0].imageTag`. `ecrListImages` already returned maps and is unchanged.
* **Breaking**: `s3PresignURL` no longer accepts `httpMethod: 'POST'` or `'PATCH'`, and fails with
  `httpMethod must be one of [GET, PUT, DELETE, HEAD]` instead. SDK v1 signed a URL for whatever HTTP
  method it was handed; v2 presigns a specific S3 operation, and there is no presigner for either of
  those. Note that a presigned POST URL was never how browser form uploads to S3 work - those use a
  separate policy-document mechanism this step has never implemented - so a `POST` URL from this step
  was unlikely to have been usable in the first place. The README previously used `POST` in its own
  example; it now uses `PUT`.
* **Breaking**: `s3PresignURL` now rejects a `durationInSeconds` outside 1..604800 (7 days) instead of
  accepting it. SigV4 query-parameter presigning cannot express a longer expiry: v1 signed the URL
  anyway and left S3 to reject it on use, while v2's signer throws, so the value is now checked up
  front with a message naming the parameter.
* `s3PresignURL` now honours `pathStyleAccessEnabled`, which reaches the presigner alongside the
  other S3 options.
* `s3Upload` now uses the AWS SDK v2 transfer manager, and fails the build if any file in a
  directory or pattern upload could not be uploaded - SDK v1 threw in that case, while v2 reports
  per-file failures on an otherwise successful transfer.
* Fixed: `s3Upload` with `includePathPattern` and `kmsId` sent the KMS key id as the server-side
  encryption *algorithm*. The single-file path set it correctly, so the two disagreed; both now go
  through one translation.
* `listAWSAccounts` now also returns a `state` field for each account. AWS retires the Organizations
  `Status` field on 9 September 2026 in favour of `State`; each key falls back to the other, so
  neither is `null` for a response that carries only one - whichever one that is. `state` carries
  two values `status` never did, `PENDING_ACTIVATION` and `CLOSED`; `status` reports those only
  where a response omits `Status`.
* Fixed: an `sseAlgorithm` the SDK does not model - a typo such as `'AES-256'`, or an algorithm this
  SDK version predates - was sent to S3 as the literal string `null` by `s3Upload` and `s3Copy`, so
  the resulting error never named what was typed. SDK v2 maps an unrecognised value to a sentinel
  whose string form is `"null"`; the value is now passed through verbatim as SDK v1 did, so a
  genuinely new algorithm keeps working and a typo is reported by S3 against the value itself.
* Synchronous AWS clients now share one HTTP connection pool per distinct socket-timeout, proxy and
  connection-limit configuration, instead of creating one per step invocation. The SDK v2 Apache
  client registers its connection manager with a process-static reaper and only deregisters it on
  close, which nothing in this plugin does, so a long-running controller accumulated a pool per
  `snsPublish`, `cfnUpdate`, `s3Delete`, `ecrLogin` or `withAWS(role: ...)`. SDK v1 leaked in the same
  way; this is the point at which it stops.
  Because the pool is now shared, its size is a process-wide limit on concurrent synchronous AWS
  requests rather than a per-invocation one, so it is set to 500 rather than left at the SDK's
  default of 50. Set `AWS_SDK_MAX_CONNECTIONS` to override it if a very wide `parallel` block still
  exhausts it; the value forms part of the sharing key, so raising it takes effect rather than
  reusing a pool built at the old size. See [Tuning](#tuning).
* **Breaking**: a pipeline that wraps an agent-side `s3Upload` or `s3Download` in
  `try`/`catch (com.amazonaws...AmazonS3Exception)` can no longer catch the AWS exception by type.
  The v1 exception crossed the remoting channel intact; the v2 one does not, and arrives as
  `hudson.remoting.ProxyException`. The AWS error message is preserved, so catching `Exception` and
  inspecting the message still works.
* `withAWS(credentials: ...)` now exports `AWS_SESSION_TOKEN` whenever the resolved credential
  carries one, not only when `iamMfaToken` is supplied. SDK v1 read the token on the MFA path alone,
  so a credential that resolved to a session credential without MFA was exported as a key and secret
  with no token - a pair that cannot sign. Pipelines relying on that partial credential set will now
  authenticate as the session was intended to.
* Fixed: `withAWS(credentials: ...)` with a credential that carries no session token now clears any
  inherited `AWS_SESSION_TOKEN` for the block, whether the credential is a Jenkins username/password
  pair or an AWS credential. Nesting it inside `withAWS(role: ...)` previously signed the inner block
  with the new key and secret and the outer block's token, which AWS rejects.
  `withAWS(samlAssertion: ...)` clears it too.
  Note the flip side: an `AWS_SESSION_TOKEN` set deliberately out of band - from `withEnv`, a global
  environment variable, or a credentials binding - is no longer visible inside the block either,
  since the step cannot distinguish it from a stale one. The drop is logged, unless `role` or
  `federatedUserId` is also set, in which case those supply a token of their own.
* `s3Download` of a directory keeps putting files where SDK v1 put them. SDK v2 resolves object keys
  relative to the listing prefix, so `s3Download(bucket: 'b', path: 'a/b/', file: 'out')` would have
  written `out/x.txt` where v1 wrote `out/a/b/x.txt` - a silent change, since the download itself
  succeeds and only a later step reading the file by its full key path fails. The prefix is folded
  into the destination so the v1 layout is preserved.
* `s3Download` of a directory (a `path` ending in `/`, or no `path`) now fails the build if any
  individual object could not be downloaded, listing each failure. SDK v1 threw in that case; SDK v2
  reports per-file failures on an otherwise successful transfer, so without an explicit check a
  partly-downloaded directory would have looked like a clean download.
* `s3Copy` now uses the AWS SDK v2 transfer manager. The `acl` parameter keeps its v1 spellings
  (`acl: 'PublicRead'`, not `'PUBLIC_READ'`) so existing Jenkinsfiles are unaffected.
* Fixed: `s3Upload` of a directory sent every file with no bucket and no key, so the upload failed
  for all of them. Fixed before release; it never shipped.
* Fixed: `s3Upload` of a directory with a `path` ending in `/` named every object `prefix//name`.
  Fixed before release; it never shipped.
* Fixed: the first `s3Upload`, `s3Copy`, `s3Download` or `s3PresignURL` in a build no longer breaks
  AWS credential resolution for everything after it. Those steps close their client when done, and an
  SDK v2 client closes the credentials provider it was given - which, without explicit credentials or
  a profile, is a process-wide shared instance. It is now wrapped so that closing a client leaves it
  intact. Fixed before release; it never shipped - both the client-closing and the shared provider
  arrived with the SDK v2 migration.
* `s3Copy` and `s3Upload` again use a multipart transfer for large objects, at v1's settings: an
  upload becomes multipart above 16 MiB with 5 MiB parts, a copy above 5 GiB with 100 MiB parts. SDK
  v2's asynchronous client does no multipart transfer at all unless asked, and its own defaults are
  8 MiB for everything.
  Both halves are observable. The threshold decides whether an object gets a multipart ETag - a
  digest of the part digests with a `-N` suffix, rather than the content MD5 - and the part size
  decides that ETag's value and its `N`. A pipeline comparing an ETag against a locally computed
  digest, or against one recorded from a previous run, depends on both.
  `s3Download` does not use a multipart transfer. SDK v2 can fetch an object part by part - one
  request per part it was uploaded with - but v1 had no such thing, a download was one request
  however large the object, and unlike an upload or a copy there is no size at which a
  single-request download is rejected. Leaving it off keeps v1's request count.
* **Breaking**: `acl: 'LogDeliveryWrite'` is no longer accepted by `s3Copy` or `s3Upload`. It is a
  bucket ACL, and SDK v2 models object and bucket canned ACLs separately, so it has no object-level
  counterpart. S3 rejected it on an object under v1 too - what changes is that the build now fails
  when the value is bound rather than when the request is sent.
* This release adds the netty asynchronous HTTP client, which the v2 transfer manager requires. It is
  the one AWS SDK module that ships to agents, since `s3Upload` and `s3Download` build their own
  clients there.
* `s3Delete`, `s3FindFiles` and `s3DoesObjectExist` now use the AWS SDK v2 S3 client. The
  `pathStyleAccessEnabled` and `payloadSigningEnabled` parameters still work; v2 has no direct
  equivalent of the latter, so it is expressed as disabling `aws-chunked` encoding, which is the
  closest thing to v1's "sign the body in one piece".
* When `withAWS(endpointUrl: ...)` points at a host outside `amazonaws.com`, S3 requests no longer add
  the CRC32 checksum trailer that SDK 2.30 turned on by default. Several S3-compatible stores (MinIO
  among them) reject it. An `endpointUrl` naming a real AWS endpoint - a documented way to pin a
  regional endpoint - keeps the trailer, as does the no-override case.
* A long `cfnUpdateStackSet` wait no longer fails with `StackOverflowError`. Both of its waits - the
  operation wait on update, and the wait for a newly created stack set to become `ACTIVE` - were
  implemented as one level of recursion per poll, so an operation that polled more times than the JVM
  stack could hold died instead of returning. They are loops now. (`cfnDeleteStackSet` does not wait
  at all, so it was never affected.)
* `cfnUpdateStackSet` now fails with the observed status if the stack set reaches a state it is not
  waiting for - `DELETED` while waiting for `ACTIVE`, or a status this SDK does not model - instead of
  polling for a state that can no longer arrive.
* `pollInterval: 0` on `cfnUpdateStackSet` now polls once a second rather than continuously, matching
  the treatment the `cfnUpdate`/`cfnDelete` waiters already had. Positive values, including sub-second
  ones, are unchanged.
* The `cfnUpdateStackSet` documentation now states that its waits have no timeout and that
  `timeoutInMinutes`/`timeoutInSeconds`, while accepted, do not apply to them. This has always been
  the behaviour - nothing under the stack-set steps reads the timeout - it was simply undocumented.
* With `pollInterval: 0`, the waiters behind `cfnUpdate`, `cfnDelete` and the change-set steps now
  poll once a second rather than continuously. That value is documented as disabling event printing,
  which it still does; previously it also reached the waiter's backoff, and with no attempt cap that
  meant calling `DescribeStacks` in a tight loop for the whole timeout. Every positive interval,
  including sub-second ones, is still passed through exactly. `cfnUpdateStackSet` polls through its
  own loop rather than an SDK waiter, and gets the same one-second floor (see above);
  `cfnDeleteStackSet` accepts `pollInterval` but never waits, so the parameter has no effect at all.
* **Breaking**: `cfnUpdateStackSet` (including its `create: true` path) now returns a map instead of
  the AWS `DescribeStackSet` response object - for example `result.stackSet.stackSetId`. The v2 response
  types are not `Serializable`, so holding the old value across a step boundary
  (`def r = cfnUpdateStackSet(...); echo "${r}"`) would have failed the build with
  `NotSerializableException`. As with the ECR steps, sandboxed pipelines could not read fields off
  the previous value at all; non-sandboxed scripts calling `getStackSet()` must switch to the map
  keys.
* The `ecrDeleteImage` documentation previously named the step `ecrDeleteImages`, which does not
  exist; copying that example failed with `No such DSL method`. The docs now match the step.
* `createDeployment` no longer fails with a `NullPointerException` when `waitForCompletion` is
  omitted; the parameter is optional and now defaults to not waiting.
* `createDeployment` again rejects an unrecognised `fileExistsBehavior` before calling AWS, with
  the same `Cannot create enum from ... value!` message as before. The AWS SDK v2 does not fail on
  unknown enum values, so without this the typo would have reached AWS as an empty value.
* `createDeployment` (with `waitForCompletion`) and `awaitDeploymentCompletion` can now be
  interrupted: aborting a build while either was waiting left the step polling CodeDeploy until
  the deployment itself reached a terminal state, so the build did not stop when it was aborted.
  This is a long-standing bug, present in 1.45 and earlier, not a regression introduced by the
  SDK v2 migration.
* `ebCreateConfigurationTemplate` now logs the application name in its completion message, which
  previously repeated the template name.

## 1.45
### Enhanced ECS and Lambda Deployment Support

We've improved the `CreateDeployStep` class to better handle ECS and Lambda deployments:

1. Added a new method `isEcsOrLambdaDeployment()` to detect if the deployment is for ECS or Lambda.
2. Modified the `getFileExistsBehavior()` method to skip setting `FileExistsBehavior` for ECS and Lambda deployments.
3. These changes ensure that the `fileExistsBehavior` parameter is only set for EC2/On-premises deployments, avoiding potential issues with ECS and Lambda deployments.

### New Dependencies

The following new imports have been added to support these changes:

- `com.amazonaws.services.codedeploy.model.GetDeploymentGroupRequest`
- `com.amazonaws.services.codedeploy.model.GetDeploymentGroupResult`
- `com.amazonaws.services.codedeploy.model.DeploymentGroupInfo`

### Improved Error Handling

The `isEcsOrLambdaDeployment()` method includes basic error handling to gracefully handle any exceptions that may occur when querying the deployment group information.

These updates enhance the plugin's compatibility with different AWS CodeDeploy compute platforms while maintaining existing functionality for EC2/On-premises deployments.


## 1.44
* Fix global configuration naming for JCasC. Please note that this is a breaking change if JCasC is defined. This can be fixed by renaming pluginImpl --> pipelineStepsAWS.
* Fix Elastic Beanstalk client creation bug that ignored provided configurations in the withAWSStep
* Fix upload tags if file is a directory 
* Add CNAME parameters to Elastic Beanstalk `ebSwapEnvironmentCNAMEs` command that lookup the required id and name params

## 1.43
* Add Elastic Beanstalk steps (`ebCreateApplication, ebCreateApplicationVersion, ebCreateConfigurationTemplate, ebCreateEnvironment, ebSwapEnvironmentCNAMEs, ebWaitOnEnvironmentStatus, ebWaitOnEnvironmentHealth`) 
* Fix documentation for lambdaVersionCleanup
* Fix wrong partition detection when assuming role
* Fix resource listing for lambdaVersionCleanup when using a cloudformation stack with lots of resources
* Fix issues around S3UploadFile with text string argument
* Fix cfnExecuteChangeSet to correctly handle no resource change, but updates to outputs (#210)

## 1.42
* Adds new parameters to cfnDelete for roleArn, clientRequestToken, and retainResources.
* Add ELB methods to mangage instances during deployemnts ( [elbRegisterInstance](#elbRegisterInstance), [elbDeregisterInstance](#elbDeregisterInstance), [elbIsInstanceRegistered](#elbIsInstanceRegistered), [elbIsInstanceDeregistered](#elbIsInstanceDeregistered) )
* Add tags to files uploaded with S3Upload
* Add `createDeployment` step
* fix `cfnExecuteChangeSet` when no resource change (#210)

## 1.41
* Add batching support for cfnUpdateStackSet
* Retry stack set deployments on LimitExceededException when there are too many StackSet operations occuring.

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
