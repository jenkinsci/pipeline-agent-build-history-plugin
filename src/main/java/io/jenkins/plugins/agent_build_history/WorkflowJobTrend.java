package io.jenkins.plugins.agent_build_history;

import hudson.Util;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Run;
import jenkins.console.ConsoleUrlProvider;
import jenkins.model.Jenkins;
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
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.support.actions.WorkspaceActionImpl;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStep;
import org.kohsuke.accmod.restrictions.suppressions.SuppressRestrictedWarnings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WorkflowJobTrend {

    /**
     * Since we cannot predict how many runs there will be, just show an ever-growing progress bar.
     * The first increment will be sized as if this many runs will be in the total,
     * but then like Zeno’s paradox we will never seem to finish until we actually do.
     */
    private static final double MAX_LIKELY_RUNS = 20;
    private static final int MAX_PER_PAGE = 40;
    private final List<WorkflowRunResult> results = new ArrayList<>();
    private final List<WorkflowRun> runs;
    private final String statusFilter;
    private final String agentFilter;
    private final boolean filterByAgent;
    private int startNewer;
    private int startOlder;
    private int oldestBuild;
    private int newestBuild;
    private int startBuild;

    public WorkflowJobTrend(WorkflowJob job, String statusFilter, String agentFilter, String startBuildString) {
        this.statusFilter = statusFilter;
        this.agentFilter = agentFilter;
        this.filterByAgent = Util.fixEmptyAndTrim(agentFilter) != null;
        startBuild = Integer.parseInt(startBuildString);
        Run<WorkflowJob, WorkflowRun> lastRun = job.getLastBuild();
        if (lastRun != null) {
            Run<WorkflowJob, WorkflowRun> firstRun = job.getFirstBuild();
            this.runs = new ArrayList<>();
            newestBuild = lastRun.getNumber();
            if (startBuild == -1) {
                startBuild = newestBuild;
            }
            if (startBuild < MAX_PER_PAGE) {
                startBuild = MAX_PER_PAGE;
            }
            if (startBuild > newestBuild) {
                startBuild = newestBuild;
            }
            int endBuild = startBuild;
            WorkflowRun n = job.getNearestBuild(startBuild);
            int i = 0;
            while (n != null && i < MAX_PER_PAGE) {
                if (includeRun(n)) {
                    runs.add(n);
                    i++;
                }
                endBuild = n.getNumber() - 1;
                n = n.getPreviousBuild();
            }
            startOlder = endBuild;
            oldestBuild = 0;
            if (firstRun != null) {
                oldestBuild = firstRun.getNumber();
            }
            oldestBuild += MAX_PER_PAGE - 1;
            startNewer = startBuild + MAX_PER_PAGE;
            if (startNewer > newestBuild) {
                startNewer = newestBuild;
            }
        } else {
            this.runs = Collections.emptyList();
            startOlder = -1;
            startNewer = Integer.MAX_VALUE;
            oldestBuild = -1;
            newestBuild = -1;
        }
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

    public WorkflowJobTrend run() {
        return this;
    }

    private boolean includeRun(WorkflowRun run) {
        Result result = run.getResult();
        if (!"all".equals(statusFilter) && result != null) {
            if ("success".equals(statusFilter) && result != Result.SUCCESS) {
                return false;
            }
            if ("unstable".equals(statusFilter) && result != Result.UNSTABLE) {
                return false;
            }
            if ("failure".equals(statusFilter) && result != Result.FAILURE) {
                return false;
            }
            if ("aborted".equals(statusFilter) && result != Result.ABORTED) {
                return false;
            }
        }
        return true;
    }

    public List<WorkflowRunResult> getResults() throws Exception {
        for (WorkflowRun run : runs) {
            WorkflowRunResult result = calculate(run);
            if (result != null) {
                results.add(result);
            }
        }
        return results;
    }

    public static class NodeExecution {
        private String builtOn;
        private String builtOnStr;
        private String duration;
        private String label;

        public String getBuiltOn() {
            return builtOn;
        }

        public String getBuiltOnStr() {
            return builtOnStr;
        }

        public String getDuration() {
            return duration;
        }

        public String getLabel() {
            return label;
        }
    }

    public static class WorkflowRunResult {
        private final WorkflowRun run;
        private List<NodeExecution> agents = new ArrayList<>();

        private WorkflowRunResult(WorkflowRun run) {
            this.run = run;
        }

        public void addNodeExecution(NodeExecution exec) {
            agents.add(exec);
        }

        public WorkflowRun getRun() {
            return run;
        }

        public List<NodeExecution> getAgents() {
            return agents;
        }

        public String getConsoleUrl() {
            return ConsoleUrlProvider.getRedirectUrl(run);
        }
    }

    @SuppressRestrictedWarnings({ArgumentsActionImpl.class, hudson.model.Messages.class})
    private WorkflowRunResult calculate(WorkflowRun run) {
        WorkflowRunResult result = new WorkflowRunResult(run);
        FlowExecution flowExecution = run.getExecution();
        boolean include = !filterByAgent;
        if (flowExecution != null) {
            for (FlowNode flowNode : new DepthFirstScanner().allNodes(flowExecution)) {
                if (! (flowNode instanceof StepStartNode)) {
                  continue;
                }
                WorkspaceActionImpl action = flowNode.getAction(WorkspaceActionImpl.class);
                if (action != null) {
                    NodeExecution nodeExecution = new NodeExecution();
                    StepStartNode startNode = (StepStartNode) flowNode;
                    StepDescriptor descriptor = startNode.getDescriptor();
                    if (descriptor instanceof ExecutorStep.DescriptorImpl) {
                        String nodeName = action.getNode();
                        if (nodeName.equals("")) {
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
                        nodeExecution.duration = getDurationString(startNode, endNode);
                        ArgumentsActionImpl args = flowNode.getAction(ArgumentsActionImpl.class);
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
        if (include) {
            return result;
        }
        return null;
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
