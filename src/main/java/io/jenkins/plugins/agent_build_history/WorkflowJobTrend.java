package io.jenkins.plugins.agent_build_history;

import hudson.Util;
import hudson.model.BallColor;
import hudson.model.Node;
import hudson.model.Result;
import jenkins.console.ConsoleUrlProvider;
import jenkins.model.Jenkins;
import jenkins.util.ProgressiveRendering;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.actions.TimingAction;
import org.jenkinsci.plugins.workflow.cps.actions.ArgumentsActionImpl;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.support.actions.WorkspaceActionImpl;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStep;
import org.kohsuke.accmod.restrictions.suppressions.SuppressRestrictedWarnings;

import java.util.ArrayList;
import java.util.List;

public class WorkflowJobTrend extends ProgressiveRendering {
    /**
     * Since we cannot predict how many runs there will be, just show an ever-growing progress bar.
     * The first increment will be sized as if this many runs will be in the total,
     * but then like Zeno’s paradox we will never seem to finish until we actually do.
     */
    private static final double MAX_LIKELY_RUNS = 20;
    private final List<JSONObject> results = new ArrayList<>();
    private final Iterable<? extends WorkflowRun> runs;
    private final String statusFilter;
    private final String agentFilter;
    private final boolean filterByAgent;

    public WorkflowJobTrend(Iterable<? extends WorkflowRun> runs, String statusFilter, String agentFilter) {
        this.runs = runs;
        this.statusFilter = statusFilter;
        this.agentFilter = agentFilter;
        this.filterByAgent = Util.fixEmptyAndTrim(agentFilter) != null;
    }
    @Override protected void compute() throws Exception {
        double decay = 1;
        for (WorkflowRun run : runs) {
            if (canceled()) {
                return;
            }
            Result result = run.getResult();
            if (!"all".equals(statusFilter) && result != null) {
              if ("success".equals(statusFilter) && result != Result.SUCCESS) {
                continue;
              }
              if ("unstable".equals(statusFilter) && result != Result.UNSTABLE) {
                continue;
              }
              if ("failure".equals(statusFilter) && result != Result.FAILURE) {
                continue;
              }
              if ("aborted".equals(statusFilter) && result != Result.ABORTED) {
                continue;
              }
            }
            JSONObject element = new JSONObject();
            boolean include = calculate(run, element);
            if (include) {
              synchronized (this) {
                results.add(element);
              }
            }
            decay *= 1 - 1 / MAX_LIKELY_RUNS;
            progress(1 - decay);
        }
    }

    @Override protected synchronized JSON data() {
        JSONArray d = JSONArray.fromObject(results);
        results.clear();
        return d;
    }

    @SuppressRestrictedWarnings({ArgumentsActionImpl.class, hudson.model.Messages.class})
    private boolean calculate(WorkflowRun run, JSONObject element) {
        BallColor iconColor = run.getIconColor();
        element.put("iconName", iconColor.getIconName());
        element.put("iconColorOrdinal", iconColor.ordinal());
        element.put("iconColorDescription", iconColor.getDescription());
        element.put("url", run.getUrl());
        element.put("number", run.getNumber());
        element.put("displayName", run.getDisplayName());
        element.put("duration", run.getDuration());
        element.put("durationString", run.getDurationString());
        element.put("consoleUrl", ConsoleUrlProvider.getRedirectUrl(run));
        element.put("timestampString", run.getTimestampString());
        element.put("timestampString2", run.getTimestampString2());
        JSONArray agents = new JSONArray();
        FlowExecution flowExecution = run.getExecution();
        boolean include = !filterByAgent;
        if (flowExecution != null) {
            for (FlowNode flowNode : new DepthFirstScanner().allNodes(flowExecution)) {
                if (! (flowNode instanceof StepStartNode)) {
                  continue;
                }
                JSONObject n = new JSONObject();
                WorkspaceActionImpl action = flowNode.getAction(WorkspaceActionImpl.class);
                if (action != null) {
                    StepStartNode startNode = (StepStartNode) flowNode;
                    StepDescriptor descriptor = startNode.getDescriptor();
                    if (descriptor instanceof ExecutorStep.DescriptorImpl) {
                        String nodeName = action.getNode();
                        if (nodeName.equals("")) {
                            if (filterByAgent && agentFilter.equals("built-in")) {
                                include = true;
                            }
                            n.put("builtOnStr", hudson.model.Messages.Hudson_Computer_DisplayName());
                        } else {
                            Node node = Jenkins.get().getNode(nodeName);
                            if (node != null) {
                                if (filterByAgent && agentFilter.equals(node.getNodeName())) {
                                    include = true;
                                }
                                n.put("builtOn", node.getNodeName());
                                n.put("builtOnStr", node.getDisplayName());
                            } else {
                                if (filterByAgent && agentFilter.equals(nodeName)) {
                                    include = true;
                                }
                                n.put("builtOnStr", nodeName);
                            }
                        }
                        BlockEndNode endNode = startNode.getEndNode();
                        n.put("duration", getDurationString(startNode, endNode));
                        ArgumentsActionImpl args = flowNode.getAction(ArgumentsActionImpl.class);
                        if (args != null) {
                            String label = (String) args.getArgumentValue("label");
                            if (label != null) {
                                n.put("label", label);
                            }
                        }
                        agents.add(n);
                    }
                }
            }
        }
        element.put("agents", agents);
        return include;
    }

    public String getDurationString(BlockStartNode startNode, BlockEndNode endNode) {
        TimingAction startTime = startNode.getAction(TimingAction.class);
        if (startTime == null) {
            return "n/a";
        }
        long endTimeLong = 0;
        if (endNode != null) {
            TimingAction endTime = endNode.getAction(TimingAction.class);
            if (endTime != null) {
                endTimeLong = endTime.getStartTime();
            }
        }
        if (endTimeLong == 0) {
            return Messages.InProgressDuration(Util.getTimeSpanString(System.currentTimeMillis() - startTime.getStartTime()));
        }
        return Util.getTimeSpanString(endTimeLong - startTime.getStartTime());
    }
}
