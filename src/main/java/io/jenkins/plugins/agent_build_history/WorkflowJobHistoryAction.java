package io.jenkins.plugins.agent_build_history;

import hudson.model.Action;
import hudson.model.Api;
import hudson.model.Item;
import org.jenkinsci.plugins.workflow.cps.replay.ReplayAction;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;
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

  public WorkflowJobTrend getHandler(String statusFilter, @QueryParameter String agentFilter, @QueryParameter String startBuild) {
    WorkflowJobTrend handler = new WorkflowJobTrend(job, statusFilter, agentFilter, Utils.getDefaultInt(startBuild, -1), 40);
    handler.compute();
    return handler;
  }

  public Api getApi() {
    StaplerRequest2 req = Stapler.getCurrentRequest2();

    WorkflowJobTrend handler = new WorkflowJobTrend(job, req.getParameter("status"),
        req.getParameter("agentFilter"), Utils.getRequestInteger(req, "startBuild", -1),
        Utils.getRequestInteger(req, "limit", 100));
    String uri = req.getRequestURI();
    if (!uri.endsWith("/api/")) {
      handler.compute();
    }
    return new Api(handler);
  }

  @POST
  public void doReplay(@QueryParameter int number) {
    WorkflowRun run = job.getBuildByNumber(number);
    if (!hasReplayPermission(run)) {
      return;
    }
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
