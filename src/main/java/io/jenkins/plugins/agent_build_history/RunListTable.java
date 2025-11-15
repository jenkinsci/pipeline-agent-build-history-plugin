package io.jenkins.plugins.agent_build_history;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Cause;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@Restricted(NoExternalUse.class)
@ExportedBean(defaultVisibility = 2)
public class RunListTable {

  String computerName;
  private static final double MAX_LIKELY_RUNS = 20;
  private Iterable<AgentExecution> runs;
  private final List<RunResult> results = new ArrayList<>();

  public RunListTable(String computerName) {
    this.computerName = computerName;
  }

  public void setRuns(Iterable<AgentExecution> runs) {
    this.runs = runs;
  }

  @Exported(name = "runs")
  public List<RunResult> getResults() {
    return results;
  }

  @NonNull
  private RunResult calculate(Run<?, ?> run, AgentExecution execution) {
    RunResult result = new RunResult(run.getParent(), run);
    List<Cause> causeList = run.getCauses();
    if (!causeList.isEmpty()) {
      result.setCause(causeList.get(causeList.size() - 1).getShortDescription());
    } else {
      result.setCause("UnknownCause");
    }

    if (run instanceof WorkflowRun) {
      for (AgentExecution.FlowNodeExecution nodeExec : execution.getFlowNodes()) {
        if (nodeExec.getNodeName().equals(computerName)) {
          result.addExecution(nodeExec);
        }
      }
    }
    return result;
  }

  @ExportedBean(defaultVisibility = 2)
  public class RunResult {
    private final Job<?, ?> job;
    private final Run<?, ?> run;
    private String cause;
    private final List<AgentExecution.FlowNodeExecution> executions = new ArrayList<>();

    public RunResult(Job<?, ?> job, Run<?, ?> run) {
      this.job = job;
      this.run = run;
    }

    public void addExecution(AgentExecution.FlowNodeExecution execution) {
      this.executions.add(execution);
    }

    @Exported
    public List<AgentExecution.FlowNodeExecution> getExecutions() {
      return executions;
    }

    public void setCause(String cause) {
      this.cause = cause;
    }

    @Exported
    public String getCause() {
      return cause;
    }

    @Exported
    public Job<?, ?> getJob() {
      return job;
    }

    @Exported
    public String getFullName() {
      return job.getFullName();
    }

    @Exported(name = "build")
    public Run<?, ?> getRun() {
      return run;
    }

    @Exported
    public Result getResult() {
      return run.getResult();
    }

    @Exported
    public int getNumber() {
      return run.getNumber();
    }

    public String getStartTime() {
      SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
      return formatter.format(new Date(run.getStartTimeInMillis()));
    }
  }

  void compute() {
    for (AgentExecution execution : runs) {
      Run<?, ?> run = execution.getRun();
      RunResult element = calculate(run, execution);
      results.add(element);
    }
  }
}
