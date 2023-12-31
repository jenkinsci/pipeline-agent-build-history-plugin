package io.jenkins.plugins.agent_build_history;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Computer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import jenkins.model.TransientActionFactory;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Extension
@Restricted(NoExternalUse.class)
public class AgentBuildHistoryFactory extends TransientActionFactory<Computer> {
  @Override
  public Class<Computer> type() {
    return Computer.class;
  }

  @NonNull
  @Override
  public Collection<? extends Action> createFor(@NonNull Computer target) {
    List<Action> result = new ArrayList<>();
    AgentBuildHistory abh = new AgentBuildHistory(target);
    result.add(abh);
    return result;
  }
}
