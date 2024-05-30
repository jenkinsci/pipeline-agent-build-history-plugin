package io.jenkins.plugins.agent_build_history;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Functions;
import hudson.model.BallColor;
import hudson.model.Cause;
import hudson.model.Run;
import jenkins.console.ConsoleUrlProvider;
import jenkins.util.ProgressiveRendering;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.util.ArrayList;
import java.util.List;

@Restricted(NoExternalUse.class)
public class RunListTable extends ProgressiveRendering {

  String computerName;
  private static final double MAX_LIKELY_RUNS = 20;
  private Iterable<AgentExecution> runs;
  private final List<JSONObject> results = new ArrayList<>();

  public RunListTable(String computerName) {
    this.computerName = computerName;
  }

  public void setRuns(Iterable<AgentExecution> runs) {
    this.runs = runs;
  }

  @NonNull
  private JSONObject calculate(Run<?, ?> run, AgentExecution execution) {
    JSONObject element = new JSONObject();
    BallColor iconColor = run.getIconColor();
    element.put("iconColorOrdinal", iconColor.ordinal());
    element.put("iconColorDescription", iconColor.getDescription());
    element.put("url", run.getUrl());
    element.put("number", run.getNumber());
    element.put("consoleUrl", ConsoleUrlProvider.getRedirectUrl(run));
    element.put("iconName", run.getIconColor().getIconName());
    element.put("parentUrl", run.getParent().getUrl());
    element.put("parentFullDisplayName", Functions.breakableString(Functions.escape(run.getParent().getFullDisplayName())));
    element.put("displayName", run.getDisplayName());
    element.put("duration", run.getDuration());
    element.put("durationString", run.getDurationString());
    element.put("timestampString", run.getTimestampString());
    element.put("timestampString2", run.getTimestampString2());
    Run.Summary buildStatusSummary = run.getBuildStatusSummary();
    element.put("buildStatusSummaryWorse", buildStatusSummary.isWorse);
    element.put("buildStatusSummaryMessage", buildStatusSummary.message);
    List<Cause> causeList = run.getCauses();
    if (!causeList.isEmpty()) {
      element.put("shortDescription", causeList.get(causeList.size() - 1).getShortDescription());
    } else {
      element.put("shortDescription", "UnknownCause");
    }

    JSONArray flowNodes = new JSONArray();
    if (run instanceof WorkflowRun) {
      for (AgentExecution.FlowNodeExecution nodeExec: execution.getFlowNodes()) {
        if (nodeExec.getNodeName().equals(computerName)) {
          flowNodes.add(calculateFlowNode(nodeExec));
        }
      }
    }
    element.put("flowNodes", flowNodes);
    return element;
  }

  private JSONObject calculateFlowNode(AgentExecution.FlowNodeExecution node) {
    JSONObject element = new JSONObject();
    element.put("duration", node.getDuration());
    element.put("durationString", node.getDurationString());
    element.put("startTime", node.getStartTimeString());
    element.put("startTimeString", node.getStartTimeSince());
    element.put("flowNodeId", node.getNodeId());
    element.put("flowNodeStatusWorse", node.getFlowNodeStatus().isWorse());
    element.put("flowNodeStatusMessage", node.getFlowNodeStatus().getMessage());
    return element;
  }

  @Override
  protected void compute() throws Exception {
    double decay = 1;
    Functions functions = new Functions();
    for (AgentExecution execution: runs) {
      if (canceled()) {
        return;
      }
      Run<?, ?> run = execution.getRun();
      JSONObject element = calculate(run, execution);
      String runId = functions.generateId();
      if (run instanceof WorkflowRun) {
        element.put("runId", runId);
      } else {
        element.put("runId", "");
      }
      synchronized (this) {
        results.add(element);
      }
      decay *= 1 - 1 / MAX_LIKELY_RUNS;
      progress(1 - decay);
    }
  }

  @NonNull
  @Override
  protected synchronized JSON data() {
    JSONArray d = JSONArray.fromObject(results);
    results.clear();
    return d;
  }
}
