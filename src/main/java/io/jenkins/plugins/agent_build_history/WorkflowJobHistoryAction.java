package io.jenkins.plugins.agent_build_history;

import hudson.model.Action;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;

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

    public WorkflowJobTrend getHandler(String statusFilter, String agentFilter) {
        return new WorkflowJobTrend(job.getBuilds(), statusFilter, agentFilter);
    }
}
