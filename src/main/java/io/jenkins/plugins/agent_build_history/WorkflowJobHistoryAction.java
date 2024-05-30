package io.jenkins.plugins.agent_build_history;

import hudson.model.Action;
import hudson.model.Item;
import org.jenkinsci.plugins.workflow.cps.replay.ReplayAction;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

public class WorkflowJobHistoryAction implements Action {

    private final WorkflowJob job;

    public WorkflowJobHistoryAction(WorkflowJob job) {
        this.job = job;
    }

    public WorkflowJob getJob() {
        return job;
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

    public WorkflowJobTrend getHandler(String statusFilter, String agentFilter, String startBuild) {
        return new WorkflowJobTrend(job, statusFilter, agentFilter, startBuild);
    }

    @POST
    public void doReplay(@QueryParameter int number) {
        WorkflowRun run = job.getBuildByNumber(number);
        ReplayAction replayAction = run.getAction(ReplayAction.class);
        if (!isReplayable(replayAction)) {
            return;
        }
        replayAction.run2(replayAction.getOriginalScript(), replayAction.getOriginalLoadedScripts());
    }

    public boolean isReplayable(ReplayAction replayAction) {
        return replayAction != null && replayAction.isRebuildEnabled();
    }

    public boolean hasReplayPermission(WorkflowRun run) {
        return job.hasPermission(Item.BUILD) || run.hasPermission(ReplayAction.REPLAY);
    }
}
