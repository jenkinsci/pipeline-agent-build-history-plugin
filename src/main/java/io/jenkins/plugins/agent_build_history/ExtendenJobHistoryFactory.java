package io.jenkins.plugins.agent_build_history;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Action;
import java.util.Collection;
import java.util.Collections;
import jenkins.model.TransientActionFactory;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;

@Extension
public class ExtendenJobHistoryFactory extends TransientActionFactory<WorkflowJob> {
  @Override
  public Class<WorkflowJob> type() {
    return WorkflowJob.class;
  }

  @NonNull
  @Override
  public Collection<? extends Action> createFor(@NonNull WorkflowJob target) {
    return Collections.singletonList(new WorkflowJobHistoryAction(target));
  }
}
