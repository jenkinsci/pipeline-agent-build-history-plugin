import hudson.XmlFile;
import hudson.model.Computer;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.slaves.DumbSlave;
import io.jenkins.plugins.agent_build_history.*;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class AgentBuildHistoryTest {

    @Before
    public void setUp() throws Exception {
        // Set up a temporary directory for each test
        File tempDir = jenkinsRule.createTmpDir();
        AgentBuildHistoryConfig.get().setStorageDir(tempDir.getAbsolutePath()); // Dynamically set the storage directory
    }

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void testWithConnectedAgent() throws Exception {
        // Create a new agent
        DumbSlave agent = jenkinsRule.createSlave("test-agent-1", "agent1", null); // This creates and starts an agent

        // Fetch the agent's computer (agent's node in Jenkins)
        Computer agentComputer = agent.toComputer();

        int retryCount = 10;
        while (retryCount-- > 0 && (agentComputer == null || !agentComputer.isOnline())) {
            Thread.sleep(500); // Wait for 500ms before checking again
            agentComputer = agent.toComputer(); // Re-fetch the computer to check the latest status
        }

        // Ensure the agent is connected
        assertTrue("Agent should be connected", agentComputer.isOnline());

        // Create a FreeStyle project and configure it to run on the agent
        FreeStyleProject project = jenkinsRule.createFreeStyleProject();
        project.setAssignedNode(agent); // Assign the project to the agent

        // Schedule a build
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Assert that the build was successful
        jenkinsRule.assertBuildStatusSuccess(build);

        // Now, let's test the build history with the agent
        AgentBuildHistory history = new AgentBuildHistory(agentComputer);
        List<AgentExecution> executions = history.getExecutionsForNode(agentComputer.getName(), 0, 10, "startTime", "desc");

        // Ensure the build history includes the executed build
        assertFalse("Build history should not be empty", executions.isEmpty());
    }

    @Test
    public void testFreeStyleBuildHistory() throws Exception {
        // Create a FreeStyle project
        FreeStyleProject project = jenkinsRule.createFreeStyleProject("test-project");

        // Build the project
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Assert that the build completed successfully
        jenkinsRule.assertBuildStatusSuccess(build);

        // Test that the build history is being tracked correctly
        Computer computer = jenkinsRule.jenkins.getComputer("");
        int retryCount = 10;
        while (retryCount-- > 0 && (computer == null || !computer.isOnline())) {
            Thread.sleep(500); // Wait for 500ms before checking again
            computer = jenkinsRule.jenkins.getComputer(""); // Re-fetch the computer to check the latest status
        }
        assertNotNull("Computer should not be null", computer);
        AgentBuildHistory history = new AgentBuildHistory(computer);
        List<AgentExecution> executions = history.getExecutionsForNode("", 0, 10, "startTime", "desc");

        // Ensure build history includes the test project
        assertFalse("Build history should not be empty", executions.isEmpty());
    }

    @Test
    public void testWorkflowBuildHistory() throws Exception {
        // Create a Workflow (Pipeline) job
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "pipeline-project");
        job.setDefinition(new CpsFlowDefinition(
                "node { echo 'Hello, World!' }", true));

        // Build the project
        WorkflowRun build = job.scheduleBuild2(0).waitForStart();
        jenkinsRule.waitForCompletion(build);

        // Assert that the build completed successfully
        jenkinsRule.assertBuildStatusSuccess(build);

        // Test that the workflow build history is being tracked correctly
        AgentBuildHistory history = new AgentBuildHistory(jenkinsRule.jenkins.getComputer(""));
        List<AgentExecution> executions = history.getExecutionsForNode("", 0, 10, "startTime", "desc");

        assertFalse("Build history should not be empty", executions.isEmpty());
    }

    @Test
    public void testBuildPagination() throws Exception {
        // Create a FreeStyle project
        FreeStyleProject project = jenkinsRule.createFreeStyleProject("paginated-project");

        // Trigger multiple builds to test pagination
        for (int i = 0; i < 30; i++) {
            project.scheduleBuild2(0).get();
        }

        // Check the pagination of the build history
        AgentBuildHistory history = new AgentBuildHistory(jenkinsRule.jenkins.getComputer(""));
        List<AgentExecution> executionsPage1 = history.getExecutionsForNode("", 0, 10, "startTime", "desc");
        List<AgentExecution> executionsPage2 = history.getExecutionsForNode("", 10, 10, "startTime", "desc");

        assertEquals(10, executionsPage1.size());
        assertEquals(10, executionsPage2.size());
    }

    @Test
    public void testSortByBuildNumber() throws Exception {
        // Create a FreeStyle project
        FreeStyleProject project = jenkinsRule.createFreeStyleProject("sorted-project");

        // Trigger multiple builds
        for (int i = 0; i < 5; i++) {
            project.scheduleBuild2(0).get();
        }

        // Test sorting by build number
        AgentBuildHistory history = new AgentBuildHistory(jenkinsRule.jenkins.getComputer(""));
        List<AgentExecution> executions = history.getExecutionsForNode("", 0, 10, "build", "asc");

        // Check that the executions are sorted by build number
        for (int i = 0; i < executions.size() - 1; i++) {
            assertTrue(executions.get(i).getRun().getNumber() < executions.get(i + 1).getRun().getNumber());
        }
    }

    @Test
    public void testFlowNodeExecutionTracking() throws Exception {
        // Create a Workflow (Pipeline) job
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "flow-node-project");
        job.setDefinition(new CpsFlowDefinition(
                "node { echo 'Hello, World!' }", true));

        // Build the project
        WorkflowRun build = job.scheduleBuild2(0).waitForStart();
        jenkinsRule.waitForCompletion(build);

        // Assert that the build completed successfully
        jenkinsRule.assertBuildStatusSuccess(build);

        // Test that the flow node execution is tracked
        AgentBuildHistory history = new AgentBuildHistory(jenkinsRule.jenkins.getComputer(""));
        List<AgentExecution> executions = history.getExecutionsForNode("", 0, 10, "startTime", "desc");

        AgentExecution execution = executions.get(0);
        assertFalse("Flow nodes should be tracked", execution.getFlowNodes().isEmpty());
    }

    @Test
    public void testBuildDeletion() throws Exception {
        // Create a FreeStyle project
        FreeStyleProject project = jenkinsRule.createFreeStyleProject("deletion-project");

        // Schedule and complete a few builds
        FreeStyleBuild build1 = project.scheduleBuild2(0).get();
        FreeStyleBuild build2 = project.scheduleBuild2(0).get();

        // Verify the builds are in the history
        AgentBuildHistory history = new AgentBuildHistory(jenkinsRule.jenkins.getComputer(""));
        List<AgentExecution> executionsBeforeDeletion = history.getExecutionsForNode("", 0, 10, "startTime", "desc");
        assertEquals(2, executionsBeforeDeletion.size());

        // Delete the second build
        build2.delete();

        // Verify the build is removed from history
        List<AgentExecution> executionsAfterDeletion = history.getExecutionsForNode("", 0, 10, "startTime", "desc");
        assertEquals(1, executionsAfterDeletion.size());

        assertEquals("The first build should still be in the history", build1.getNumber(), executionsAfterDeletion.get(0).getRun().getNumber());
    }

    @Test
    public void testJobDeletion() throws Exception {
        // Create a FreeStyle project
        FreeStyleProject project = jenkinsRule.createFreeStyleProject("job-deletion-project");

        // Schedule and complete a build
        project.scheduleBuild2(0).get();

        // Verify the job is in the history
        AgentBuildHistory history = new AgentBuildHistory(jenkinsRule.jenkins.getComputer(""));
        List<AgentExecution> executionsBeforeDeletion = history.getExecutionsForNode("", 0, 10, "startTime", "desc");
        assertFalse("Build history should not be empty", executionsBeforeDeletion.isEmpty());

        // Delete the job
        project.delete();

        // Verify the job's history is removed
        List<AgentExecution> executionsAfterDeletion = history.getExecutionsForNode("", 0, 10, "startTime", "desc");
        assertTrue("Build history should be empty after job deletion", executionsAfterDeletion.isEmpty());
    }
    @Test
    public void testJobRename() throws Exception {
        // Create a FreeStyle project
        FreeStyleProject project = jenkinsRule.createFreeStyleProject("old-job-name");

        // Schedule and complete a build
        project.scheduleBuild2(0).get();
        project.scheduleBuild2(0).get();

        // Verify the job is in the history
        AgentBuildHistory history = new AgentBuildHistory(jenkinsRule.jenkins.getComputer(""));
        List<AgentExecution> executionsBeforeRename = history.getExecutionsForNode("", 0, 10, "startTime", "desc");
        assertFalse("Build history should not be empty", executionsBeforeRename.isEmpty());

        // Rename the job
        project.renameTo("new-job-name");

        // Verify the history is updated with the new job name
        List<AgentExecution> executionsAfterRename = history.getExecutionsForNode("", 0, 10, "startTime", "desc");
        assertFalse("Build history should still exist after job rename", executionsAfterRename.isEmpty());
        assertTrue("The build should now be associated with the new job name",
                executionsAfterRename.get(0).getRun().getParent().getName().equals("new-job-name"));

        List<String> indexLines = BuildHistoryFileManager.readIndexFile("", AgentBuildHistoryConfig.get().getStorageDir());
        for (String line : indexLines) {
            assertFalse("The old job name should not be in the index file", line.contains("old-job-name"));
            assertTrue("The new job name should be in the index file", line.contains("new-job-name"));
        }
    }
    @Test
    public void testNodeRenameWithManualOnUpdated() throws Exception {
        // Create a subclass of HistoryNodeListener to expose the protected onUpdated method
        class TestableHistoryNodeListener extends AgentBuildHistoryListeners.HistoryNodeListener {
            public void callOnUpdated(Node oldOne, Node newOne) {
                onUpdated(oldOne, newOne); // Call the protected method from the subclass
            }
        }

        // Create the initial agent (old node)
        DumbSlave oldAgent = jenkinsRule.createSlave("test-agent-1", "agent1", null);

        // Create a FreeStyle project and configure it to run on the old agent
        FreeStyleProject project = jenkinsRule.createFreeStyleProject();
        project.setAssignedNode(oldAgent);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Verify the build is in the history
        AgentBuildHistory history = new AgentBuildHistory(oldAgent.toComputer());
        List<AgentExecution> executionsBeforeRename = history.getExecutionsForNode("test-agent-1", 0, 10, "startTime", "desc");
        assertFalse("Build history should not be empty", executionsBeforeRename.isEmpty());

        // Simulate the renaming of the node
        // Create a new DumbSlave with the new name (renamed node)
        DumbSlave newAgent = jenkinsRule.createSlave("renamed-agent", "agent1", null);

        // Manually trigger the onUpdated method using the TestableHistoryNodeListener because renaming agents is not supported in JenkinsRule
        TestableHistoryNodeListener listener = new TestableHistoryNodeListener();
        listener.callOnUpdated(oldAgent, newAgent);


        // Verify the history is updated with the new node name
        List<AgentExecution> executionsAfterRename = history.getExecutionsForNode("renamed-agent", 0, 10, "startTime", "desc");
        assertFalse("Build history should still exist after node rename", executionsAfterRename.isEmpty());
    }

    @Test
    public void testWorkflowBuildWithTwoAgentsAndStages() throws Exception {
        // Create two agents
        DumbSlave agent1 = jenkinsRule.createSlave("agent-1", "agent1", null);
        DumbSlave agent2 = jenkinsRule.createSlave("agent-2", "agent2", null);

        // Fetch the agent's computer (agent's node in Jenkins)
        Computer agent1Computer = agent1.toComputer();
        Computer agent2Computer = agent2.toComputer();

        int retryCount = 10;
        while (retryCount-- > 0 && (agent1Computer == null || !agent1Computer.isOnline() || agent2Computer == null || !agent2Computer.isOnline())) {
            Thread.sleep(500); // Wait for 500ms before checking again
            agent1Computer = agent1.toComputer(); // Re-fetch the computer to check the latest status
            agent2Computer = agent2.toComputer();
        }

        // Ensure both agents are connected
        assertNotNull("Agent 1 should not be null", agent1Computer);
        assertNotNull("Agent 2 should not be null", agent2Computer);
        assertTrue("Agent 1 should be online", agent1Computer.isOnline());
        assertTrue("Agent 2 should be online", agent2Computer.isOnline());

        // Create a Workflow (Pipeline) job with multiple stages on different agents
        WorkflowJob job = jenkinsRule.createProject(WorkflowJob.class, "multi-agent-pipeline");
        job.setDefinition(new CpsFlowDefinition(
                "node('agent1') {\n" +
                        "    echo 'Hello, World!'\n" +
                        "}\n" +
                        "node('agent2') {\n" +
                        "    echo 'Hello, World!'\n" +
                        "}\n" +
                        "node('agent1') {\n" +
                        "    echo 'Hello, World!'\n" +
                        "}\n", true));

        // Build the project
        WorkflowRun build = job.scheduleBuild2(0).waitForStart();
        jenkinsRule.waitForCompletion(build);
        // Assert that the build completed successfully
        jenkinsRule.assertBuildStatusSuccess(build);

        // Fetch the build history for both agents
        AgentBuildHistory historyAgent1 = new AgentBuildHistory(agent1.toComputer());
        AgentBuildHistory historyAgent2 = new AgentBuildHistory(agent2.toComputer());
        // Get executions for each agent
        List<AgentExecution> executionsAgent1 = historyAgent1.getExecutionsForNode("agent-1", 0, 10, "startTime", "desc");
        List<AgentExecution> executionsAgent2 = historyAgent2.getExecutionsForNode("agent-2", 0, 10, "startTime", "desc");

        // Ensure there are executions for both agents
        assertFalse("Build history for agent 1 should not be empty", executionsAgent1.isEmpty());
        assertFalse("Build history for agent 2 should not be empty", executionsAgent2.isEmpty());

        // Verify that the flow nodes for Agent 1 are correct (should have two stages: Stage 1 and Stage 3)
        AgentExecution executionAgent1 = executionsAgent1.get(0);
        List<AgentExecution.FlowNodeExecution> flowNodesAgent1 = executionAgent1.getFlowNodes().stream()
                .filter(node -> node.getNodeName().equals("agent-1"))
                .collect(Collectors.toList());

        assertEquals("Agent 1 should have two flow nodes", 2, flowNodesAgent1.size());

        // Verify that the flow nodes for Agent 2 are correct (should have only Stage 2)
        AgentExecution executionAgent2 = executionsAgent2.get(0);
        List<AgentExecution.FlowNodeExecution> flowNodesAgent2 = executionAgent2.getFlowNodes().stream()
                .filter(node -> node.getNodeName().equals("agent-2"))
                .collect(Collectors.toList());

        assertEquals("Agent 2 should have one flow node", 1, flowNodesAgent2.size());
    }


}
