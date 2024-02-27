package io.jenkins.plugins.agent_build_history;

import hudson.Util;
import hudson.model.BallColor;
import hudson.model.Node;
import java.util.ArrayList;
import java.util.List;
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

public class WorkflowJobTrend extends ProgressiveRendering {
    /**
     * Since we cannot predict how many runs there will be, just show an ever-growing progress bar.
     * The first increment will be sized as if this many runs will be in the total,
     * but then like Zeno’s paradox we will never seem to finish until we actually do.
     */
    private static final double MAX_LIKELY_RUNS = 20;
    private final List<JSONObject> results = new ArrayList<>();
    private final Iterable<? extends WorkflowRun> runs;

    public WorkflowJobTrend(Iterable<? extends WorkflowRun> runs) {
        this.runs = runs;
    }
    @Override protected void compute() throws Exception {
        double decay = 1;
        for (WorkflowRun run : runs) {
            if (canceled()) {
                return;
            }
            JSONObject element = new JSONObject();
            calculate(run, element);
            synchronized (this) {
                results.add(element);
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
    private void calculate(WorkflowRun run, JSONObject element) {
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
                            n.put("builtOnStr", hudson.model.Messages.Hudson_Computer_DisplayName());
                        } else {
                            Node node = Jenkins.get().getNode(nodeName);
                            if (node != null) {
                                n.put("builtOn", node.getNodeName());
                                n.put("builtOnStr", node.getDisplayName());
                            } else {
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
