package de.taimos.pipeline.aws;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.Folder;
import com.cloudbees.hudson.plugins.folder.properties.FolderCredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.util.ListBoxModel;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.List;

/**
 * Test the behavior of the {@link WithAWSStep}
 *
 * @author Allan Burdajewicz
 */
public class WithAWSStepTest {

    @ClassRule
    public static JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void testStepWithGlobalCredentials() throws Exception {

        String globalCredentialsId = "global-aws-creds";

        List<String> credentialIds = new ArrayList<>();
        credentialIds.add(globalCredentialsId);

        StandardUsernamePasswordCredentials key = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,
                globalCredentialsId, "test-global-creds", "global-aws-access-key-id", "global-aws-secret-access-key");
        SystemCredentialsProvider.getInstance().getCredentials().add(key);
        SystemCredentialsProvider.getInstance().save();

        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "testStepWithGlobalCredentials");
        job.setDefinition(new CpsFlowDefinition(""
                + "node {\n"
                + "  withAWS (credentials: '" + globalCredentialsId + "') {\n"
                + "    echo 'It works!'\n"
                + "  }\n"
                + "}\n", true)
        );
        jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
    }

    @Test
    public void testStepWithFolderCredentials() throws Exception {

        String folderCredentialsId = "folders-aws-creds";

        // Create a folder with credentials in its store
        Folder folder = jenkinsRule.jenkins.createProject(Folder.class, "folder" + jenkinsRule.jenkins.getItems().size());
        CredentialsStore folderStore = getFolderStore(folder);
        StandardUsernamePasswordCredentials inFolderCredentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,
                folderCredentialsId, "test-folder-creds", "folder-aws-access-key-id", "folder-aws-secret-access-key");
        folderStore.addCredentials(Domain.global(), inFolderCredentials);
        SystemCredentialsProvider.getInstance().save();

        List<String> credentialIds = new ArrayList<>();
        credentialIds.add(folderCredentialsId);

        WorkflowJob job = folder.createProject(WorkflowJob.class, "testStepWithFolderCredentials");
        job.setDefinition(new CpsFlowDefinition(""
                + "node {\n"
                + "  withAWS (credentials: '" + folderCredentialsId + "') {\n"
                + "    echo 'It works!'\n"
                + "  }\n"
                + "}\n", true)
        );
        jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

    }


    @Test
    public void testListCredentials() throws Exception {
        Folder folder = jenkinsRule.jenkins.createProject(Folder.class, "folder" + jenkinsRule.jenkins.getItems().size());
        CredentialsStore folderStore = getFolderStore(folder);
        StandardUsernamePasswordCredentials folderCredentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,
                "folder-creds", "test-creds", "aws-access-key-id", "aws-secret-access-key");
        StandardUsernamePasswordCredentials globalCredentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,
                "global-creds", "test-creds", "aws-access-key-id", "aws-secret-access-key");

        folderStore.addCredentials(Domain.global(), folderCredentials);
        SystemCredentialsProvider.getInstance().getCredentials().add(globalCredentials);
        SystemCredentialsProvider.getInstance().save();

        WorkflowJob job = folder.createProject(WorkflowJob.class, "testStepWithFolderCredentials");
        final WithAWSStep.DescriptorImpl descriptor = jenkinsRule.jenkins.getDescriptorByType(WithAWSStep.DescriptorImpl.class);

        // 3 options: Root credentials, folder credentials and "none"
        ListBoxModel list = descriptor.doFillCredentialsItems(job);
        Assert.assertEquals(3, list.size());

        StandardUsernamePasswordCredentials systemCredentials = new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM,
                "system-creds", "test-creds", "aws-access-key-id", "aws-secret-access-key");
        SystemCredentialsProvider.getInstance().getCredentials().add(systemCredentials);

        // Still 3 options: Root credentials, folder credentials and "none"
        list = descriptor.doFillCredentialsItems(job);
        Assert.assertEquals(3, list.size());
    }


    private CredentialsStore getFolderStore(AbstractFolder f) {
        Iterable<CredentialsStore> stores = CredentialsProvider.lookupStores(f);
        CredentialsStore folderStore = null;
        for (CredentialsStore s : stores) {
            if (s.getProvider() instanceof FolderCredentialsProvider && s.getContext() == f) {
                folderStore = s;
                break;
            }
        }
        return folderStore;
    }
}
