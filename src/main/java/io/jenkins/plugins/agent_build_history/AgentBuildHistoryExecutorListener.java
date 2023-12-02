package io.jenkins.plugins.agent_build_history;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.ExecutorListener;
import hudson.model.Queue;
import hudson.model.Run;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStepExecution;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Extension
@Restricted(NoExternalUse.class)
public class AgentBuildHistoryExecutorListener implements ExecutorListener {
  private static final Logger LOGGER = Logger.getLogger(AgentBuildHistoryExecutorListener.class.getName());

  @Override
  public void taskStarted(Executor executor, Queue.Task task) {
    Queue.Executable executable = executor.getCurrentExecutable();
    Computer c = executor.getOwner();
    if (executable instanceof AbstractBuild) {
      Run<?, ?> run = (Run<?, ?>) executable;
      LOGGER.log(Level.FINER, () -> "Starting Job: " + run.getFullDisplayName() + " on " + c.getName());
      AgentBuildHistory.startJobExecution(c, run);
    } else if (task instanceof ExecutorStepExecution.PlaceholderTask) {
      ExecutorStepExecution.PlaceholderTask pht = (ExecutorStepExecution.PlaceholderTask) task;
      executable = task.getOwnerExecutable();
      try {
        FlowNode node = pht.getNode();
        if (node != null && executable instanceof WorkflowRun) {
          Run<?, ?> run = (Run<?, ?>) executable;
          AgentBuildHistory.startFlowNodeExecution(c, (WorkflowRun) run, node);
          LOGGER.log(Level.FINER, () -> "Starting part of pipeline: " + run.getFullDisplayName()
                  + " Node id: " + node.getId() + " on " + c.getName());
        }
      } catch (IOException | InterruptedException e) {
        LOGGER.log(Level.FINE, e, () -> "Failed to get FlowNode");
      }
    }
  }
}
