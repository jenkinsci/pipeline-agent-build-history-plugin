package io.jenkins.plugins.agent_build_history;

import hudson.model.Result;
import hudson.model.Run;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

public class BuildHistoryFileManager {

  private static final Logger LOGGER = Logger.getLogger(BuildHistoryFileManager.class.getName());
  public static final String separator = ";";

  // Handle locks for different nodes
  private static final Map<String, Object> nodeLocks = new HashMap<>();

  public static Object getNodeLock(String nodeName) {
    synchronized (nodeLocks) {
      return nodeLocks.computeIfAbsent(nodeName, k -> new Object());
    }
  }

  // Reads the index file for a given node and returns the list of lines
  public static List<String> readIndexFile(String nodeName, String storageDir) {
    Object lock = getNodeLock(nodeName);
    synchronized (lock) {
      File indexFile = new File(storageDir + "/" + nodeName + "_index.txt");
      if (indexFile.exists()) {
        try {
          return Files.readAllLines(indexFile.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
          LOGGER.log(Level.WARNING, "Failed to read index file for node " + nodeName, e);
          return Collections.emptyList();
        }
      }
      return Collections.emptyList();
    }
  }

  public static void updateResult(String nodeName, Run<?, ?> run, String storageDir) {
    Object lock = getNodeLock(nodeName);
    String runIdentifier = run.getParent().getFullName() + separator + run.getNumber() + separator;
    synchronized (lock) {
      List<String> indexLines = readIndexFile(nodeName, storageDir);
      StringWriter buffer = new StringWriter();
      try (BufferedWriter writer = new BufferedWriter(buffer)) {
        boolean found = false;
        for (String line : indexLines) {
          if (line.startsWith(runIdentifier)) {
            if (!line.endsWith(separator + run.getResult())) {
              writeLine(writer, run);
              found = true;
            } else {
              writer.write(line);
            }
          } else {
            writer.write(line);
          }
          writer.newLine();
        }
        if (found) {
          writer.flush();
          File indexFile = new File(storageDir + "/" + nodeName + "_index.txt");
          try (FileWriter fileWriter = new FileWriter(indexFile, StandardCharsets.UTF_8)) {
            fileWriter.write(buffer.toString());
          }
        }
      } catch (IOException e) {
        LOGGER.log(Level.WARNING, "Failed to update result in index-file for node " + nodeName, e);
      }
    }}

  // Appends a new run entry and updates the index
  public static void addRunToNodeIndex(String nodeName, Run<?, ?> run, String storageDir) {
    Object lock = getNodeLock(nodeName);
    if (run instanceof WorkflowRun) {
      HistoryAction action = run.getAction(HistoryAction.class);
      if (action != null) {
        action.addAgent(nodeName);
      }
    }
    synchronized (lock) {
      // Update index for the node
      File indexFile = new File(storageDir + "/" + nodeName + "_index" + ".txt");
      List<String> lines = readIndexFile(nodeName, storageDir);
      String lineMatch = run.getParent().getFullName() + separator + run.getNumber() + separator;
      boolean exists = lines.stream().anyMatch(line -> line.startsWith(lineMatch));
      if (!exists) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(indexFile, StandardCharsets.UTF_8, true))) {
          writeLine(writer, run);
          writer.newLine();
        } catch (IOException e) {
          LOGGER.log(Level.WARNING, "Failed to update index for node " + nodeName, e);
        }
      }
    }
  }

  private static void writeLine(BufferedWriter writer, Run<?, ?> run) throws IOException {
    writer.write(run.getParent().getFullName() + separator + run.getNumber() + separator + run.getStartTimeInMillis());
    if (!run.isLogUpdated()) {
      Result result = run.getResult();
      if (result != null) {
        writer.write(separator + result.toString());
      }
    }
  }

  // Method for getting all saved node names
  public static Set<String> getAllSavedNodeNames(String storageDir) {
    Set<String> nodeNames = new HashSet<>();
    File storageDirectory = new File(storageDir);
    File[] files = storageDirectory.listFiles((dir, name) -> name.endsWith("_index.txt"));

    if (files != null) {
      for (File file : files) {
        String fileName = file.getName();
        String nodeName = fileName.substring(0, fileName.length() - "_index.txt".length());
        nodeNames.add(nodeName);
      }
    }
    return nodeNames;
  }


  public static void deleteExecution(String nodeName, String jobName, int buildNumber, String storageDir) {
    Object lock = getNodeLock(nodeName);
    String runIdentifier = jobName + separator + buildNumber + separator;
    synchronized (lock) {
      List<String> indexLines = readIndexFile(nodeName, storageDir);
      File indexFile = new File(storageDir + "/" + nodeName + "_index.txt");
      try (BufferedWriter writer = new BufferedWriter(new FileWriter(indexFile, StandardCharsets.UTF_8))) {
        for (String line : indexLines) {
          if (!line.startsWith(runIdentifier)) {
            writer.write(line);
            writer.newLine();
          }
        }
      } catch (IOException e) {
        LOGGER.log(Level.WARNING, "Failed to delete Execution for Node: " + nodeName + " job: " + jobName + " build: " + buildNumber, e);
      }
    }
  }

  public static void deleteJobSerialization(String jobName, String storageDir) {
    Set<String> nodeNames = getAllSavedNodeNames(storageDir);
    for (String nodeName : nodeNames) {
      Object lock = getNodeLock(nodeName);
      synchronized (lock) {
        List<String> indexLines = readIndexFile(nodeName, storageDir);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(storageDir + "/" + nodeName + "_index.txt", StandardCharsets.UTF_8))) {
          for (String line : indexLines) {
            if (!line.startsWith(jobName + separator)) {
              writer.write(line);
              writer.newLine();
            }
          }
        } catch (IOException e) {
          LOGGER.log(Level.WARNING, "Failed to delete job from index-file for job: " + jobName + ", node: " + nodeName, e);
        }
      }
    }

  }

  // Delete the index file of a node
  public static void deleteNodeSerializations(String nodeName, String storageDir) {
    Object lock = getNodeLock(nodeName);
    synchronized (lock) {
      File indexFile = new File(storageDir + "/" + nodeName + "_index.txt");
      if (indexFile.exists() && !indexFile.delete()) {
        LOGGER.log(Level.WARNING, "Failed to delete index file for node: " + nodeName);
      }
    }
  }

  public static void renameNodeFiles(String oldNodeName, String newNodeName, String storageDir) {
    Object lock = getNodeLock(oldNodeName);
    synchronized (lock) {
      File oldIndexFile = new File(storageDir + "/" + oldNodeName + "_index.txt");
      File newIndexFile = new File(storageDir + "/" + newNodeName + "_index.txt");
      if (oldIndexFile.exists() && !oldIndexFile.renameTo(newIndexFile)) {
        LOGGER.log(Level.WARNING, "Failed to rename index file from: " + oldNodeName + " to: " + newNodeName);
      }
    }
  }

  public static void renameJob(String oldFullName, String newFullName, String storageDir) {
    Set<String> nodeNames = getAllSavedNodeNames(storageDir);
    for (String nodeName : nodeNames) {
      Object lock = getNodeLock(nodeName);
      synchronized (lock) {
        List<String> indexLines = readIndexFile(nodeName, storageDir);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(storageDir + "/" + nodeName + "_index.txt", StandardCharsets.UTF_8))) {
          for (String line : indexLines) {
            if (line.startsWith(oldFullName + separator)) {
              writer.write(newFullName + line.substring(oldFullName.length()));
              writer.newLine();
            } else {
              writer.write(line);
              writer.newLine();
            }
          }
        } catch (IOException e) {
          LOGGER.log(Level.WARNING, "Failed to rename jobs in index-file in node: " + nodeName, e);
        }
      }
    }
  }
}
