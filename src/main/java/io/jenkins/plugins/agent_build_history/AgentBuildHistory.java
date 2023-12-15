package io.jenkins.plugins.agent_build_history;

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
import jenkins.model.Jenkins;
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

  private static final Map<Computer, Set<AgentExecution>> agentExecutions = new HashMap<>();
  private static final Map<Computer, Map<Run<?, ?>, AgentExecution>> agentExecutionsMap = new HashMap<>();

  private final Computer computer;

  @Extension
  public static class HistoryRunListener extends RunListener<Run<?, ?>> {

    @Override
    public void onDeleted(Run run) {
      for (Set<AgentExecution> executions: agentExecutions.values()) {
        executions.removeIf(exec -> run.getFullDisplayName().equals(exec.getRun().getFullDisplayName()));
      }
      for (Map<Run<?, ?>, AgentExecution> executions: agentExecutionsMap.values()) {
        executions.remove(run);
      }
    }
  }

  public AgentBuildHistory(Computer computer) {
    this.computer = computer;
    loadExecutions(computer);
  }

  /*
   * used by jelly
   */
  public Computer getComputer() {
    return computer;
  }

  public RunListTable getHandler() {
    RunListTable runListTable = new RunListTable();
    runListTable.setRuns(getExecutions());
    return  runListTable;
  }

  private static void load(Computer computer) {
    Set<AgentExecution> executions = agentExecutions.get(computer);
    Node node = computer.getNode();
    if (node == null) {
      return;
    }
    RunList<Run<?, ?>> runList = RunList.fromJobs((Iterable) Jenkins.get().allItems(Job.class));
    runList.forEach(run -> {
      if (run instanceof AbstractBuild && ((AbstractBuild<?, ?>) run).getBuiltOn() == node) {
        AgentExecution execution = new AgentExecution(run);
        executions.add(execution);
        agentExecutionsMap.get(computer).put(run, execution);
      } else if (run instanceof WorkflowRun) {
        WorkflowRun wfr = (WorkflowRun) run;
        FlowExecution flowExecution = wfr.getExecution();
        if (flowExecution != null) {
          AgentExecution execution = new AgentExecution(wfr);
          boolean matchesNode = false;
          for (FlowNode flowNode : new DepthFirstScanner().allNodes(flowExecution)) {
            for (WorkspaceActionImpl action : flowNode.getActions(WorkspaceActionImpl.class)) {
              StepStartNode startNode = (StepStartNode) flowNode;
              StepDescriptor descriptor = startNode.getDescriptor();
              if (descriptor instanceof ExecutorStep.DescriptorImpl) {
                String nodeName = action.getNode();
                if (node.getNodeName().equals(nodeName)) {
                  matchesNode = true;
                  execution.addFlowNode(flowNode);
                }
              }
            }
          }
          if (matchesNode) {
            executions.add(execution);
            agentExecutionsMap.get(computer).put(run, execution);
          }
        }
      }
    });
  }

  private static synchronized void loadExecutions(Computer computer) {
    if (agentExecutions.get(computer) == null) {
      Set<AgentExecution> executions = Collections.synchronizedSet(new TreeSet<>());
      agentExecutions.put(computer, executions);
      agentExecutionsMap.put(computer, Collections.synchronizedMap(new HashMap<>()));
      Timer.get().schedule(() -> load(computer), 0, TimeUnit.SECONDS);
    }
  }

  /* use by jelly */
  public Set<AgentExecution> getExecutions() {
    return Collections.unmodifiableSet(
            agentExecutions.get(computer));
  }

  private static AgentExecution getAgentExecution(Computer c, Run<?, ?> run) {
    loadExecutions(c);
    Map<Run<?, ?>, AgentExecution> agentExecsMap = agentExecutionsMap.get(c);
    Set<AgentExecution> agentExecs = agentExecutions.get(c);
    AgentExecution exec = agentExecsMap.get(run);
    if (exec == null) {
      exec = new AgentExecution(run);
      agentExecs.add(exec);
      agentExecsMap.put(run, exec);
    }
    return exec;
  }

  public static void startJobExecution(Computer c, Run<?, ?> run) {
    getAgentExecution(c, run);
  }

  public static void startFlowNodeExecution(Computer c, WorkflowRun run, FlowNode node) {
    AgentExecution exec = getAgentExecution(c, run);
    exec.addFlowNode(node);
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
