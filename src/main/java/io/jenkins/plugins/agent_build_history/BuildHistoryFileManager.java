package io.jenkins.plugins.agent_build_history;

import hudson.model.Run;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BuildHistoryFileManager {

    private static final Logger LOGGER = Logger.getLogger(BuildHistoryFileManager.class.getName());

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
            List<String> indexLines = new ArrayList<>();
            File indexFile = new File(storageDir + "/" + nodeName + "_index.txt");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(indexFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                        indexLines.add(line);
                }
            }catch (FileNotFoundException e) {
                LOGGER.log(Level.INFO, "Index file not found for node " + nodeName);
                return Collections.emptyList();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to read index file for node " + nodeName, e);
                return Collections.emptyList();
            }
            return indexLines;
        }
    }

    // Appends a new run entry and updates the index
    public static void addRunToNodeIndex(String nodeName, Run<?, ?> run, String storageDir) {
        Object lock = getNodeLock(nodeName);
        synchronized (lock) {
            // Update index for the node
            File indexFile = new File(storageDir + "/" + nodeName + "_index" + ".txt");
            List<String> lines = readIndexFile(nodeName, storageDir);
            boolean exists = lines.contains(run.getParent().getFullName() + "," + run.getNumber() + "," + run.getStartTimeInMillis());
            if (!exists) {
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(indexFile, true), StandardCharsets.UTF_8))) {
                    writer.write(run.getParent().getFullName() + "," + run.getNumber() + "," + run.getStartTimeInMillis());
                    writer.newLine();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to update index for node " + nodeName, e);
                }
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
        synchronized (lock) {
            List<String> indexLines = readIndexFile(nodeName, storageDir);
            File indexFile = new File(storageDir + "/" + nodeName + "_index.txt");
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(indexFile), StandardCharsets.UTF_8))) {
                for (String line : indexLines) {
                    if (!line.startsWith(jobName + "," + buildNumber + ",")) {
                        writer.write(line);
                        writer.newLine();
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to delete Execution for Node: " + nodeName + " job: " + jobName + " build: " + buildNumber, e);
            }
        }
    }

    public static void deleteJobSerialization(String jobName, String storageDir){
        Set<String> nodeNames = getAllSavedNodeNames(storageDir);
        for (String nodeName : nodeNames) {
            Object lock = getNodeLock(nodeName);
            synchronized (lock) {
                List<String> indexLines = readIndexFile(nodeName, storageDir);
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(storageDir + "/" + nodeName + "_index.txt"), StandardCharsets.UTF_8))) {
                    for (String line : indexLines) {
                        if (!line.startsWith(jobName + ",")) {
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
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(storageDir + "/" + nodeName + "_index.txt"), StandardCharsets.UTF_8))) {
                    for (String line : indexLines) {
                        if (line.startsWith(oldFullName + ",")) {
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
