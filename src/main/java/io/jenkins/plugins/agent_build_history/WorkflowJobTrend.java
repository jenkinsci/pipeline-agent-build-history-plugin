package io.jenkins.plugins.agent_build_history;

import hudson.Util;
import hudson.model.Cause;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Run;
import java.util.ArrayList;
import java.util.List;
import jenkins.console.ConsoleUrlProvider;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.actions.BodyInvocationAction;
import org.jenkinsci.plugins.workflow.actions.TimingAction;
import org.jenkinsci.plugins.workflow.cps.actions.ArgumentsActionImpl;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.support.actions.WorkspaceActionImpl;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStep;
import org.kohsuke.accmod.restrictions.suppressions.SuppressRestrictedWarnings;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public class WorkflowJobTrend {

  /**
   * Since we cannot predict how many runs there will be, just show an ever-growing progress bar.
   * The first increment will be sized as if this many runs will be in the total,
   * but then like Zeno’s paradox we will never seem to finish until we actually do.
   */
  private final List<WorkflowRunResult> results = new ArrayList<>();
  private final String agentFilter;
  private final boolean filterByAgent;
  private int startNewer;
  private int startOlder;
  private int oldestBuild;
  private int newestBuild;
  private int startBuild;
  private boolean hasMoreRuns = false;
  private final WorkflowJob job;
  private final int limit;
  private final String statusFilter;

  public WorkflowJobTrend(WorkflowJob job, String statusFilter, String agentFilter, int startBuild, int limit) {
    this.agentFilter = agentFilter;
    this.job = job;
    this.filterByAgent = Util.fixEmptyAndTrim(agentFilter) != null;
    this.startBuild = startBuild;
    this.limit = limit;
    this.statusFilter = statusFilter;
  }

  void compute() {
    Run<WorkflowJob, WorkflowRun> lastRun = job.getLastBuild();
    if (lastRun != null) {
      Run<WorkflowJob, WorkflowRun> firstRun = job.getFirstBuild();
      newestBuild = lastRun.getNumber();
      if (startBuild == -1) {
        startBuild = newestBuild;
      }
      if (startBuild < limit) {
        startBuild = limit;
      }
      if (startBuild > newestBuild) {
        startBuild = newestBuild;
      }
      int endBuild = startBuild;
      WorkflowRun n = job.getNearestBuild(startBuild);
      int i = 0;
      while (n != null && i < limit) {
        if (Utils.includeRun(n.getResult(), statusFilter)) {
          WorkflowRunResult result = calculate(n);
          if (result != null) {
            results.add(result);
            i++;
          }
        }

        endBuild = n.getNumber() - 1;
        n = n.getPreviousBuild();
      }
      startOlder = endBuild;
      oldestBuild = 0;
      if (firstRun != null) {
        oldestBuild = firstRun.getNumber();
      }
      oldestBuild += limit - 1;
      startNewer = startBuild + limit;
      if (startNewer > newestBuild) {
        startNewer = newestBuild;
      }
      hasMoreRuns = n != null;
    } else {
      startOlder = -1;
      startNewer = Integer.MAX_VALUE;
      oldestBuild = -1;
      newestBuild = -1;
    }
  }

  @Exported
  public WorkflowJob getJob() {
    return job;
  }

  @Exported
  public String getFullName() {
    return job.getFullName();
  }

  public int getStartNewer() {
    return startNewer;
  }

  public int getStartOlder() {
    return startOlder;
  }

  public int getOldestBuild() {
    return oldestBuild;
  }

  public int getNewestBuild() {
    return newestBuild;
  }

  public int getStartBuild() {
    return startBuild;
  }

  public boolean isHasMoreRuns() {
    return hasMoreRuns;
  }

  @Exported(visibility = 999, name = "runs")
  public List<WorkflowRunResult> getResults() throws Exception {
    return results;
  }

  @ExportedBean(defaultVisibility = 3)
  public static class NodeExecution {
    private String builtOn;
    private String builtOnStr;
    private String label;
    private AgentDuration agentDuration;

    @Exported
    public String getBuiltOn() {
      return builtOn;
    }

    public String getBuiltOnStr() {
      return builtOnStr;
    }

    public String getDurationString() {
      if (agentDuration == null) {
        return "n/a";
      }
      return agentDuration.getDurationString();
    }

    @Exported
    public long getDuration() {
      if (agentDuration == null) {
        return 0;
      }
      return agentDuration.getEndTime() - agentDuration.getStartTime();
    }

    @Exported
    public String getLabel() {
      return label;
    }

    @Exported
    public long getStartTime() {
      if (agentDuration == null) {
        return 0;
      }
      return agentDuration.getStartTime();
    }
  }

  @ExportedBean(defaultVisibility = 2)
  public static class WorkflowRunResult {
    private final WorkflowRun run;
    private List<NodeExecution> agents = new ArrayList<>();

    private WorkflowRunResult(WorkflowRun run) {
      this.run = run;
    }

    public void addNodeExecution(NodeExecution exec) {
      agents.add(exec);
    }

    @Exported(visibility = 999, name = "build")
    public WorkflowRun getRun() {
      return run;
    }

    @Exported(visibility = 999, name = "agents")
    public List<NodeExecution> getAgents() {
      return agents;
    }

    public String getConsoleUrl() {
      return ConsoleUrlProvider.getRedirectUrl(run);
    }

    @Exported(name = "cause")
    public String getShortDescription() {
      List<Cause> causeList = run.getCauses();
      if (!causeList.isEmpty()) {
        return causeList.get(causeList.size() - 1).getShortDescription();
      } else {
        return "Unknown Cause";
      }
    }

    @Exported
    public Result getResult() {
      return run.getResult();
    }
  }

  @SuppressRestrictedWarnings({ArgumentsActionImpl.class, hudson.model.Messages.class})
  private WorkflowRunResult calculate(WorkflowRun run) {
    WorkflowRunResult result = new WorkflowRunResult(run);
    FlowExecution flowExecution = run.getExecution();
    boolean include = !filterByAgent;
    if (flowExecution != null) {
      for (FlowNode flowNode : new DepthFirstScanner().allNodes(flowExecution)) {
        if (!(flowNode instanceof StepStartNode startNode)) {
          continue;
        }
        StepDescriptor descriptor = startNode.getDescriptor();
        if (descriptor instanceof ExecutorStep.DescriptorImpl) {
          if (!flowNode.getActions(BodyInvocationAction.class).isEmpty()) {
            for (FlowNode parent : flowNode.getParents()) {
              if (!(parent instanceof StepStartNode parentNode)) {
                continue;
              }
              StepDescriptor parentDescriptor = parentNode.getDescriptor();
              if (parentDescriptor instanceof ExecutorStep.DescriptorImpl) {
                WorkspaceActionImpl action = parentNode.getAction(WorkspaceActionImpl.class);
                if (action != null) {
                  NodeExecution nodeExecution = new NodeExecution();
                  String nodeName = action.getNode();
                  if (nodeName.isEmpty()) {
                    if (filterByAgent && agentFilter.equals("built-in")) {
                      include = true;
                    }
                    nodeExecution.builtOn = "(built-in)";
                    nodeExecution.builtOnStr = hudson.model.Messages.Hudson_Computer_DisplayName();
                  } else {
                    Node node = Jenkins.get().getNode(nodeName);
                    if (node != null) {
                      if (filterByAgent && agentFilter.equals(node.getNodeName())) {
                        include = true;
                      }
                      nodeExecution.builtOn = node.getNodeName();
                      nodeExecution.builtOnStr = node.getDisplayName();
                    } else {
                      if (filterByAgent && agentFilter.equals(nodeName)) {
                        include = true;
                      }
                      nodeExecution.builtOnStr = nodeName;
                    }
                  }
                  BlockEndNode endNode = startNode.getEndNode();
                  nodeExecution.agentDuration = getAgentDuration(startNode, endNode);
                  ArgumentsActionImpl args = parentNode.getAction(ArgumentsActionImpl.class);
                  if (args != null) {
                    String label = (String) args.getArgumentValue("label");
                    if (label != null) {
                      nodeExecution.label = label;
                    }
                  }
                  result.addNodeExecution(nodeExecution);
                }
              }
            }
          }
        }
      }
    }
    if (include) {
      return result;
    }
    return null;
  }

  public static class AgentDuration {
    private final long startTime;
    private final long endTime;

    public AgentDuration(long startTime, long endTime) {
      this.startTime = startTime;
      this.endTime = endTime;
    }

    public long getStartTime() {
      return startTime;
    }
    public long getEndTime() {
      return endTime;
    }
    public String getDurationString() {
      if (endTime == 0) {
        return Messages.InProgressDuration(Util.getTimeSpanString(System.currentTimeMillis() - startTime));
      }
      return Util.getTimeSpanString(endTime - startTime);
    }
  }

  public AgentDuration getAgentDuration(BlockStartNode startNode, BlockEndNode endNode) {
    TimingAction startTime = startNode.getAction(TimingAction.class);
    if (startTime == null) {
      return null;
    }
    long endTimeLong = 0;
    if (endNode != null) {
      TimingAction endTime = endNode.getAction(TimingAction.class);
      if (endTime != null) {
        endTimeLong = endTime.getStartTime();
      }
    }
    return new AgentDuration(startTime.getStartTime(), endTimeLong);
  }
}
