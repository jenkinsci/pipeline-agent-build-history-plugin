package io.jenkins.plugins.agent_build_history;

import hudson.model.InvisibleAction;
import java.util.HashSet;
import java.util.Set;

public class HistoryAction extends InvisibleAction {
  private final Set<String> agents = new HashSet<>();

  public HistoryAction() {
  }

  public void addAgent(String agentName) {
    agents.add(agentName);
  }

  public Set<String> getAgents() {
    return agents;
  }
}
