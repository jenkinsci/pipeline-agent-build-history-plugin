package io.jenkins.plugins.agent_build_history;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Computer;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.listeners.RunListener;
import hudson.util.RunList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.model.NodeListener;
import jenkins.util.Timer;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.support.actions.WorkspaceActionImpl;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStep;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
public class AgentBuildHistory implements Action {

  private static final Logger LOGGER = Logger.getLogger(AgentBuildHistory.class.getName());

  private static final Map<String, Set<AgentExecution>> agentExecutions = new HashMap<>();
  private static final Map<Run<?, ?>, AgentExecution> agentExecutionsMap = new HashMap<>();

  private final Computer computer;

  private static boolean loaded = false;

  @Extension
  public static class HistoryRunListener extends RunListener<Run<?, ?>> {

    @Override
    public void onDeleted(Run run) {
      for (Set<AgentExecution> executions: agentExecutions.values()) {
        executions.removeIf(exec -> run.getFullDisplayName().equals(exec.getRun().getFullDisplayName()));
      }
      agentExecutionsMap.remove(run);
    }
  }

  @Extension
  public static class HistoryNodeListener extends NodeListener {

    @Override
    protected void onDeleted(@NonNull Node node) {
      agentExecutions.remove(node.getNodeName());
    }
  }

  public AgentBuildHistory(Computer computer) {
    this.computer = computer;
  }

  /*
   * used by jelly
   */
  public Computer getComputer() {
    return computer;
  }

  @SuppressFBWarnings(value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD")
  public RunListTable getHandler() {
    if (!loaded) {
      loaded = true;
      Timer.get().schedule(AgentBuildHistory::load, 0, TimeUnit.SECONDS);
    }
    RunListTable runListTable = new RunListTable(computer.getName());
    runListTable.setRuns(getExecutions());
    return  runListTable;
  }

  private static void load() {
    LOGGER.log(Level.FINE, () -> "Starting to load all runs");
    RunList<Run<?, ?>> runList = RunList.fromJobs((Iterable) Jenkins.get().allItems(Job.class));
    runList.forEach(run -> {
      LOGGER.log(Level.FINER, () -> "Loading run " + run.getFullDisplayName());
      AgentExecution execution = getAgentExecution(run);
      if (run instanceof AbstractBuild) {
        Node node = ((AbstractBuild<?, ?>) run).getBuiltOn();
        if (node != null) {
          Set<AgentExecution> executions = loadExecutions(node.getNodeName());
          executions.add(execution);
        }
      } else if (run instanceof WorkflowRun) {
        WorkflowRun wfr = (WorkflowRun) run;
        FlowExecution flowExecution = wfr.getExecution();
        if (flowExecution != null) {
          for (FlowNode flowNode : new DepthFirstScanner().allNodes(flowExecution)) {
            if (! (flowNode instanceof StepStartNode)) {
              continue;
            }
            for (WorkspaceActionImpl action : flowNode.getActions(WorkspaceActionImpl.class)) {
              StepStartNode startNode = (StepStartNode) flowNode;
              StepDescriptor descriptor = startNode.getDescriptor();
              if (descriptor instanceof ExecutorStep.DescriptorImpl) {
                String nodeName = action.getNode();
                execution.addFlowNode(flowNode, nodeName);
                Set<AgentExecution> executions = loadExecutions(nodeName);
                executions.add(execution);
              }
            }
          }
        }
      }
    });
  }

  private static Set<AgentExecution> loadExecutions(String computerName) {
    Set<AgentExecution> executions = agentExecutions.get(computerName);
    if (executions == null) {
      LOGGER.log(Level.FINER, () -> "Creating executions for computer " + computerName);
      executions = Collections.synchronizedSet(new TreeSet<>());
      agentExecutions.put(computerName, executions);
    }
    return executions;
  }

  /* use by jelly */
  public Set<AgentExecution> getExecutions() {
    Set<AgentExecution> executions = agentExecutions.get(computer.getName());
    if (executions == null) {
      return Collections.emptySet();
    }
    return Collections.unmodifiableSet(new TreeSet<>(executions));
  }

  @NonNull
  private static AgentExecution getAgentExecution(Run<?, ?> run) {
    AgentExecution exec = agentExecutionsMap.get(run);
    if (exec == null) {
      LOGGER.log(Level.FINER, () -> "Creating execution for run " + run.getFullDisplayName());
      exec = new AgentExecution(run);
      agentExecutionsMap.put(run, exec);
    }
    return exec;
  }

  public static void startJobExecution(Computer c, Run<?, ?> run) {
    loadExecutions(c.getName()).add(getAgentExecution(run));
  }

  public static void startFlowNodeExecution(Computer c, WorkflowRun run, FlowNode node) {
    AgentExecution exec = getAgentExecution(run);
    exec.addFlowNode(node, c.getName());
    loadExecutions(c.getName()).add(exec);
  }

  @Override
  public String getIconFileName() {
    return "symbol-file-tray-stacked-outline plugin-ionicons-api";
  }

  @Override
  public String getDisplayName() {
    return "Extended Build History";
  }

  @Override
  public String getUrlName() {
    return "extendedBuildHistory";
  }
}
