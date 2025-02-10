package io.jenkins.plugins.agent_build_history;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Computer;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Run;
import hudson.util.RunList;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.util.Timer;
import org.jenkinsci.plugins.workflow.actions.BodyInvocationAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.support.actions.WorkspaceActionImpl;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStep;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;

@Restricted(NoExternalUse.class)
public class AgentBuildHistory implements Action {

  private static final Logger LOGGER = Logger.getLogger(AgentBuildHistory.class.getName());
  private final Computer computer;
  private int totalPages = 1;
  private static boolean loaded = false;
  private static boolean loadingComplete = false;

  public AgentBuildHistory(Computer computer) {
    this.computer = computer;
    LOGGER.log(Level.CONFIG, () -> "Creating AgentBuildHistory for " + computer.getName());
  }

  public static String getCookieValue(StaplerRequest2 req, String name, String defaultValue) {
    Cookie[] cookies = req.getCookies();
    if (cookies != null) {
      for (Cookie cookie : cookies) {
        if (cookie.getName().equals(name)) {
          return cookie.getValue();
        }
      }
    }
    return defaultValue; // Fallback to default if cookie not found
  }

  /*
   * used by jelly
   */
  public Computer getComputer() {
    return computer;
  }

  /*
   * used by jelly
   */
  public boolean isLoadingComplete() {
    return loadingComplete;
  }

  public static void setLoaded(boolean loaded) {
    AgentBuildHistory.loaded = loaded;
    AgentBuildHistory.loadingComplete = loaded;
  }

  public int getTotalPages() {
    return totalPages;
  }

  @SuppressFBWarnings(value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD")
  public RunListTable getHandler() {
    if (!loaded) {
      loaded = true;
      Timer.get().schedule(AgentBuildHistory::load, 0, TimeUnit.SECONDS);
    }
    RunListTable runListTable = new RunListTable(computer.getName());
    //Get Parameters from URL
    StaplerRequest2 req = Stapler.getCurrentRequest2();
    int page = req.getParameter("page") != null ? Integer.parseInt(req.getParameter("page")) : 1;
    int pageSize = req.getParameter("pageSize") != null ? Integer.parseInt(req.getParameter("pageSize")) : Integer.parseInt(getCookieValue(req, "pageSize", "20"));
    String sortColumn = req.getParameter("sortColumn") != null ? req.getParameter("sortColumn") : getCookieValue(req, "sortColumn", "startTime");
    String sortOrder = req.getParameter("sortOrder") != null ? req.getParameter("sortOrder") : getCookieValue(req, "sortOrder", "desc");
    //Update totalPages depending on pageSize
    int totalEntries = BuildHistoryFileManager.readIndexFile(computer.getName(), AgentBuildHistoryConfig.get().getStorageDir()).size();
    totalPages = (int) Math.ceil((double) totalEntries / pageSize);

    LOGGER.finer("Getting runs for node: " + computer.getName() + " page: " + page + " pageSize: " + pageSize + " sortColumn: " + sortColumn + " sortOrder: " + sortOrder);

    int start = (page - 1) * pageSize;
    runListTable.setRuns(getExecutionsForNode(computer.getName(), start, pageSize, sortColumn, sortOrder));
    return runListTable;
  }

  public List<AgentExecution> getExecutionsForNode(String nodeName, int start, int limit, String sortColumn, String sortOrder) {
    String storageDir = AgentBuildHistoryConfig.get().getStorageDir();
    List<String> indexLines = BuildHistoryFileManager.readIndexFile(nodeName, storageDir);
    LOGGER.finer("Found " + indexLines.size() + " entries for node " + nodeName);
    if (indexLines.isEmpty()) {
      return List.of();
    }
    // Sort index lines based on start time or build
    indexLines.sort((a, b) -> {
      int comparison = 0;
      switch (sortColumn) {
        case "startTime":
          long timeA = Long.parseLong(a.split(BuildHistoryFileManager.separator)[2]);
          long timeB = Long.parseLong(b.split(BuildHistoryFileManager.separator)[2]);
          comparison = Long.compare(timeA, timeB);
          break;
        case "build":
          comparison = a.split(BuildHistoryFileManager.separator)[0].compareTo(b.split(BuildHistoryFileManager.separator)[0]);
          if (comparison == 0) {
            // Only compare build numbers if the job names are the same
            int buildNumberA = Integer.parseInt(a.split(BuildHistoryFileManager.separator)[1]);
            int buildNumberB = Integer.parseInt(b.split(BuildHistoryFileManager.separator)[1]);
            comparison = Integer.compare(buildNumberA, buildNumberB);
          }
          break;
        default:
          comparison = 0;
      }
      return sortOrder.equals("asc") ? comparison : -comparison;
    });
    // Apply pagination
    int end = Math.min(start + limit, indexLines.size());
    List<String> page = indexLines.subList(start, end);
    List<AgentExecution> result = new ArrayList<>();

    for (String line : page) {
      String[] parts = line.split(BuildHistoryFileManager.separator);
      String jobName = parts[0];
      int buildNumber = Integer.parseInt(parts[1]);
      // Load execution using deserialization
      AgentExecution execution = loadSingleExecution(jobName, buildNumber);
      if (execution != null) {
        result.add(execution);
      }
    }
    LOGGER.finer("Returning " + result.size() + " entries for node " + nodeName);
    return result;
  }

  public static AgentExecution loadSingleExecution(String jobName, int buildNumber) {
    Job<?, ?> job = Jenkins.get().getItemByFullName(jobName, Job.class);
    Run<?, ?> run = null;
    if (job != null) {
      run = job.getBuildByNumber(buildNumber);
    }
    if (run == null) {
      LOGGER.info("Run not found for " + jobName + " #" + buildNumber);
      return null;
    }
    LOGGER.finer("Loading run " + run.getFullDisplayName());
    AgentExecution execution = new AgentExecution(run);

    if (run instanceof AbstractBuild<?, ?> build) {
      Node node = build.getBuiltOn();
      if (node != null) {
        LOGGER.finer("Loading AbstractBuild on node: " + node.getNodeName());
        return execution;
      }
    } else if (run instanceof WorkflowRun wfr) {
      LOGGER.finer("Loading WorkflowRun: " + wfr.getFullDisplayName());
      FlowExecution flowExecution = wfr.getExecution();
      if (flowExecution != null) {
        for (FlowNode flowNode : new DepthFirstScanner().allNodes(flowExecution)) {
          if (!(flowNode instanceof StepStartNode startNode)) {
            continue;
          }
          StepDescriptor descriptor = startNode.getDescriptor();
          if (descriptor instanceof ExecutorStep.DescriptorImpl) {
            if (!flowNode.getActions(BodyInvocationAction.class).isEmpty()) {
              for (FlowNode parent : flowNode.getParents()) {
                if (!(parent instanceof StepStartNode parentNode)) {
                  continue;
                }

                StepDescriptor parentDescriptor = parentNode.getDescriptor();
                if (parentDescriptor instanceof ExecutorStep.DescriptorImpl) {
                  WorkspaceActionImpl action = parentNode.getAction(WorkspaceActionImpl.class);
                  if (action != null) {
                    String nodeName = action.getNode();
                    execution.addFlowNode(flowNode, nodeName);
                    LOGGER.finer("Loading WorkflowRun FlowNode on node: " + nodeName);
                  }
                }
                break;
              }
            }
          }
        }
      }
    }
    return execution;
  }

  private static void load() {
    LOGGER.log(Level.INFO, () -> "Starting to synchronize all runs");
    RunList<Run<?, ?>> runList = RunList.fromJobs((Iterable) Jenkins.get().allItems(Job.class));
    runList.forEach(run -> {
      LOGGER.finer("Loading run " + run.getFullDisplayName());

      if (run instanceof AbstractBuild<?, ?> build) {
        Node node = build.getBuiltOn();
        if (node != null) {
          BuildHistoryFileManager.addRunToNodeIndex(node.getNodeName(), run, AgentBuildHistoryConfig.get().getStorageDir());
        }
      } else if (run instanceof WorkflowRun wfr) {
        FlowExecution flowExecution = wfr.getExecution();
        if (flowExecution != null) {
          for (FlowNode flowNode : new DepthFirstScanner().allNodes(flowExecution)) {
            if (!(flowNode instanceof StepStartNode startNode)) {
              continue;
            }
            for (WorkspaceActionImpl action : flowNode.getActions(WorkspaceActionImpl.class)) {
              StepDescriptor descriptor = startNode.getDescriptor();
              if (descriptor instanceof ExecutorStep.DescriptorImpl) {
                String nodeName = action.getNode();
                BuildHistoryFileManager.addRunToNodeIndex(nodeName, run, AgentBuildHistoryConfig.get().getStorageDir());
              }
            }
          }
        }
      }
    });
    loadingComplete = true;
    LOGGER.log(Level.INFO, () -> "Synchronizing all runs complete");
  }

  public static void startJobExecution(Computer c, Run<?, ?> run) {
    BuildHistoryFileManager.addRunToNodeIndex(c.getName(), run, AgentBuildHistoryConfig.get().getStorageDir());
  }

  public static void startFlowNodeExecution(Computer c, WorkflowRun run, FlowNode node) {
    BuildHistoryFileManager.addRunToNodeIndex(c.getName(), run, AgentBuildHistoryConfig.get().getStorageDir());
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
}
