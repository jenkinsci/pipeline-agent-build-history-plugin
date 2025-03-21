package io.jenkins.plugins.agent_build_history;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.File;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class BuildHistoryFileManagerTest {

  private JenkinsRule jenkinsRule;

  @TempDir
  private File storageDir;

  @BeforeEach
  void setUp(JenkinsRule rule) {
    jenkinsRule = rule;
  }

  @Test
  void testAddRunToNodeIndex() throws Exception {
    String nodeName = "test-node";
    String jobName = "test-job";
    int buildNumber = 1;

    // Simulate adding a run to the node index
    BuildHistoryFileManager.addRunToNodeIndex(nodeName, createDummyRun(jobName, buildNumber), storageDir.getAbsolutePath());

    // Verify that the index file has been created
    File indexFile = new File(storageDir, nodeName + "_index.txt");
    assertTrue(indexFile.exists(), "Index file should be created");

    // Verify that the index file contains the correct entry
    List<String> indexEntries = BuildHistoryFileManager.readIndexFile(nodeName, storageDir.getAbsolutePath());
    assertEquals(1, indexEntries.size());
    assertTrue(indexEntries.get(0).contains(jobName + BuildHistoryFileManager.separator + buildNumber), "Index should contain the job and build number");
  }

  @Test
  void testDeleteExecution() throws Exception {
    String nodeName = "test-node";
    String jobName = "test-job";
    int buildNumber = 1;

    // Add a run to the node index
    BuildHistoryFileManager.addRunToNodeIndex(nodeName, createDummyRun(jobName, buildNumber), storageDir.getAbsolutePath());

    // Verify that the entry exists
    List<String> indexEntriesBeforeDeletion = BuildHistoryFileManager.readIndexFile(nodeName, storageDir.getAbsolutePath());
    assertEquals(1, indexEntriesBeforeDeletion.size());

    // Delete the execution
    BuildHistoryFileManager.deleteExecution(nodeName, jobName, buildNumber, storageDir.getAbsolutePath());

    // Verify that the entry has been removed
    List<String> indexEntriesAfterDeletion = BuildHistoryFileManager.readIndexFile(nodeName, storageDir.getAbsolutePath());
    assertTrue(indexEntriesAfterDeletion.isEmpty(), "Index should be empty after deletion");
  }

  @Test
  void testRenameNodeFiles() throws Exception {
    String oldNodeName = "old-node";
    String newNodeName = "new-node";
    String jobName = "test-job";
    int buildNumber = 1;

    // Add a run to the old node
    BuildHistoryFileManager.addRunToNodeIndex(oldNodeName, createDummyRun(jobName, buildNumber), storageDir.getAbsolutePath());

    // Rename the node files
    BuildHistoryFileManager.renameNodeFiles(oldNodeName, newNodeName, storageDir.getAbsolutePath());

    // Verify that the old node's index file has been renamed
    File oldIndexFile = new File(storageDir, oldNodeName + "_index.txt");
    File newIndexFile = new File(storageDir, newNodeName + "_index.txt");
    assertFalse(oldIndexFile.exists(), "Old index file should not exist");
    assertTrue(newIndexFile.exists(), "New index file should exist");

    // Verify that the renamed index file contains the correct entry
    List<String> indexEntries = BuildHistoryFileManager.readIndexFile(newNodeName, storageDir.getAbsolutePath());
    assertEquals(1, indexEntries.size());
    assertTrue(indexEntries.get(0).contains(jobName + BuildHistoryFileManager.separator + buildNumber), "Renamed index should contain the job and build number");
  }

  @Test
  void testGetAllSavedNodeNames() throws Exception {
    String nodeName1 = "node-1";
    String nodeName2 = "node-2";

    // Add runs to multiple nodes
    BuildHistoryFileManager.addRunToNodeIndex(nodeName1, createDummyRun("job1", 1), storageDir.getAbsolutePath());
    BuildHistoryFileManager.addRunToNodeIndex(nodeName2, createDummyRun("job2", 2), storageDir.getAbsolutePath());

    // Get all saved node names
    Set<String> savedNodeNames = BuildHistoryFileManager.getAllSavedNodeNames(storageDir.getAbsolutePath());

    // Verify that both node names are present
    assertTrue(savedNodeNames.contains(nodeName1), "Saved node names should contain node-1");
    assertTrue(savedNodeNames.contains(nodeName2), "Saved node names should contain node-2");
  }

  @Test
  void testRenameJobWithTwoNodes() throws Exception {
    String nodeName1 = "node-1";
    String nodeName2 = "node-2";
    String oldJobName = "old-job";
    String newJobName = "new-job";
    int buildNumber = 1;

    Run<?, ?> run = createDummyRun(oldJobName, buildNumber);

    // Add a run with the old job name to both nodes
    BuildHistoryFileManager.addRunToNodeIndex(nodeName1, run, storageDir.getAbsolutePath());
    BuildHistoryFileManager.addRunToNodeIndex(nodeName2, run, storageDir.getAbsolutePath());

    // Verify that the entries exist with the old job name in both nodes
    List<String> indexEntriesBeforeRenameNode1 = BuildHistoryFileManager.readIndexFile(nodeName1, storageDir.getAbsolutePath());
    List<String> indexEntriesBeforeRenameNode2 = BuildHistoryFileManager.readIndexFile(nodeName2, storageDir.getAbsolutePath());
    assertEquals(1, indexEntriesBeforeRenameNode1.size());
    assertEquals(1, indexEntriesBeforeRenameNode2.size());
    assertTrue(indexEntriesBeforeRenameNode1.get(0).contains(oldJobName), "Node 1 index should contain the old job name");
    assertTrue(indexEntriesBeforeRenameNode2.get(0).contains(oldJobName), "Node 2 index should contain the old job name");

    // Rename the job in both nodes' index files
    BuildHistoryFileManager.renameJob(oldJobName, newJobName, storageDir.getAbsolutePath());

    // Verify that the entries are now renamed with the new job name in both nodes
    List<String> indexEntriesAfterRenameNode1 = BuildHistoryFileManager.readIndexFile(nodeName1, storageDir.getAbsolutePath());
    List<String> indexEntriesAfterRenameNode2 = BuildHistoryFileManager.readIndexFile(nodeName2, storageDir.getAbsolutePath());
    assertEquals(1, indexEntriesAfterRenameNode1.size());
    assertEquals(1, indexEntriesAfterRenameNode2.size());
    assertTrue(indexEntriesAfterRenameNode1.get(0).contains(newJobName), "Node 1 index should contain the new job name");
    assertTrue(indexEntriesAfterRenameNode2.get(0).contains(newJobName), "Node 2 index should contain the new job name");
    assertFalse(indexEntriesAfterRenameNode1.get(0).contains(oldJobName), "Node 1 index should not contain the old job name");
    assertFalse(indexEntriesAfterRenameNode2.get(0).contains(oldJobName), "Node 2 index should not contain the old job name");
  }

  // Helper method to create a dummy Run object
  private Run<?, ?> createDummyRun(String jobName, int buildNumber) throws Exception {
    // Use JenkinsRule to create a FreeStyle project (or any other type of job)
    FreeStyleProject project = jenkinsRule.createFreeStyleProject(jobName);

    // Simulate a build (Run) by triggering a build and waiting for completion
    FreeStyleBuild build = project.scheduleBuild2(0).get();

    // Return the created build (Run)
    return build;
  }
}
