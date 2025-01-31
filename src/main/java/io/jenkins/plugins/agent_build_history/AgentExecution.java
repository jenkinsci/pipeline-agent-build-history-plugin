package io.jenkins.plugins.agent_build_history;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.model.Run;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.TimingAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
public class AgentExecution implements Comparable<AgentExecution> {

  private final Run<?, ?> run;
  private final Set<FlowNodeExecution> flowNodes = Collections.synchronizedSet(new TreeSet<>());

  public AgentExecution(Run<?, ?> run) {
    this.run = run;
  }

  @NonNull
  public Run<?, ?> getRun() {
    return run;
  }

  public void addFlowNode(FlowNode node, String nodeName) {
    FlowNodeExecution exec = new FlowNodeExecution(node.getId(), nodeName);
    flowNodes.add(exec);
  }

  public Set<FlowNodeExecution> getFlowNodes() {
    return Collections.unmodifiableSet(flowNodes);
  }

  private static long getNodeTime(FlowNode node) {
    if (node == null) {
      return 0;
    }
    TimingAction timingAction = node.getAction(TimingAction.class);
    if (timingAction != null) {
      return timingAction.getStartTime();
    }
    return 0;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AgentExecution execution = (AgentExecution) o;
    return Objects.equals(run, execution.run);
  }

  @Override
  public int hashCode() {
    return Objects.hash(run);
  }

  /*
   * Ordering is based on the start time stamp. In the unlikely case two runs
   * start in the same millisecond, sort by full display name
   */
  @Override
  public int compareTo(AgentExecution o) {
    int compare = Long.compare(o.run.getStartTimeInMillis(), run.getStartTimeInMillis());
    if (compare == 0) {
      return o.run.getFullDisplayName().compareToIgnoreCase(run.getFullDisplayName());
    }
    return compare;
  }

  public class FlowNodeExecution implements Comparable<FlowNodeExecution> {
    private final String nodeId;
    private Status status;

    private String nodeName;
    private final long startTime;

    public FlowNodeExecution(String nodeId, String nodeName) {
      this.nodeId = nodeId;
      this.nodeName = nodeName;
      startTime = AgentExecution.getNodeTime(getStartNode());
    }

    @CheckForNull
    private FlowNode getStartNode() {
      Run<?, ?> run = AgentExecution.this.getRun();
      FlowExecution execution = ((WorkflowRun) run).getExecution();
      if (execution != null) {
        try {
          return execution.getNode(nodeId);
        } catch (IOException e) {
          return null;
        }
      }
      return null;
    }

    private FlowNode getEndNode() {
      Run<?, ?> run = AgentExecution.this.getRun();
      FlowExecution execution = ((WorkflowRun) run).getExecution();
      if (execution != null) {
        try {
          FlowNode node = execution.getNode(nodeId);
          if (node instanceof BlockStartNode) {
            return ((BlockStartNode) node).getEndNode();
          }
        } catch (IOException e) {
          return null;
        }
      }
      return null;
    }

    public String getDurationString() {
      long endTime = getNodeTime(getEndNode());
      if (endTime == 0) {
        return Messages.InProgressDuration(Util.getTimeSpanString(System.currentTimeMillis() - this.startTime));
      } else {
        return Util.getTimeSpanString(endTime - this.startTime);
      }
    }

    public long getDuration() {
      long endTime = getNodeTime(getEndNode());
      if (endTime == 0) {
        return Math.max(System.currentTimeMillis() - startTime, 0L);
      } else {
        return Math.max(endTime - startTime, 0L);
      }
    }

    @Override
    public int compareTo(FlowNodeExecution o) {
      int compare = Long.compare(o.startTime, startTime);
      if (compare == 0) {
        return nodeId.compareTo(o.nodeId);
      }
      return compare;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      FlowNodeExecution that = (FlowNodeExecution) o;
      return Objects.equals(startTime, that.startTime);
    }

    @Override
    public int hashCode() {
      return Objects.hash(startTime);
    }

    public String getNodeId() {
      return nodeId;
    }

    public String getNodeName() {
      return nodeName;
    }

    public Status getFlowNodeStatus() {
      long endTime = getNodeTime(getEndNode());
      if (endTime == 0) {
        return Status.RUNNING;
      }
      if (status == null) {
        Run<?, ?> run = AgentExecution.this.getRun();
        if (!(run instanceof WorkflowRun)) {
          return Status.UNKNOWN;
        }
        WorkflowRun wfr = (WorkflowRun) run;
        FlowExecution flowExecution = wfr.getExecution();
        if (flowExecution == null) {
          return Status.UNKNOWN;
        }
        try {
          FlowNode node = flowExecution.getNode(nodeId);
          if (node instanceof BlockStartNode) {
            BlockEndNode<?> endNode = ((BlockStartNode) node).getEndNode();
            if (endNode != null) {
              ErrorAction errorAction = endNode.getError();
              status = errorAction != null ? Status.FAILURE : Status.SUCCESS;
              return status;
            }
          }
        } catch (IOException e) {
          return Status.UNKNOWN;
        }
        return Status.UNKNOWN;
      }
      return status;
    }

    public String getStartTimeString() {
      return Util.XS_DATETIME_FORMATTER2.format(Instant.ofEpochMilli(startTime));
    }

    public String getStartTimeSince() {
      long duration = (new GregorianCalendar()).getTimeInMillis() - startTime;
      return Util.getTimeSpanString(duration);
    }
  }

  public enum Status {
    UNKNOWN(false, Messages.Unknown()),
    SUCCESS(false, Messages.Success()),
    RUNNING(false, Messages.StillRunning()),
    FAILURE(true, Messages.Failure());

    private final boolean worse;
    private final String message;

    Status(boolean worse, String message) {
      this.worse = worse;
      this.message = message;
    }

    public boolean isWorse() {
      return worse;
    }

    public String getMessage() {
      return message;
    }
  }
}
