package io.jenkins.plugins.agent_build_history;

import hudson.Extension;
import hudson.util.ListBoxModel;
import jenkins.model.GlobalConfiguration;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class AgentBuildHistoryConfig extends GlobalConfiguration {
    private static final Logger LOGGER = Logger.getLogger(AgentBuildHistoryConfig.class.getName());

    private String storageDir = getDefaultStorageDir();
    private int entriesPerPage = 20;
    private String defaultSortColumn = "startTime";
    private String defaultSortOrder = "desc";

    public AgentBuildHistoryConfig() {
        load(); // Load the persisted configuration
        ensureStorageDir();
    }

    private String getDefaultStorageDir() {
        String jenkinsHome = System.getenv("JENKINS_HOME");
        if (jenkinsHome != null && !jenkinsHome.isEmpty()) {
            return jenkinsHome + File.separator + "agent_build_history_serialized_data";
        } else {
            LOGGER.warning("JENKINS_HOME environment variable is not set. Falling back to default path.");
            return "/var/jenkins_home/agent_build_history_serialized_data"; // Default fallback if JENKINS_HOME is not set
        }
    }

    private void ensureStorageDir() {
        File storageDirectory = new File(storageDir);
        if (!storageDirectory.exists()) {
            LOGGER.info("Creating storage directory at " + storageDir);
            try {
                boolean created = storageDirectory.mkdirs();
                if (!created) {
                    LOGGER.severe("Failed to create storage directory at " + storageDir);
                }
            } catch (SecurityException e) {
                LOGGER.log(Level.SEVERE, "SecurityException: Insufficient permissions to create directory at " + storageDir, e);
            }
        } else {
            LOGGER.info("Storage directory already exists at " + storageDir);
        }
    }

    public String getStorageDir() {
        return storageDir;
    }

    @DataBoundSetter
    public void setStorageDir(String storageDir) {
        if (!this.storageDir.equals(storageDir)) {
            LOGGER.info("Changing storage directory from " + this.storageDir + " to " + storageDir);
            this.storageDir = storageDir;
            ensureStorageDir();
            AgentBuildHistory.setLoaded(false);
            save(); // Save the configuration
        }
    }

    public int getEntriesPerPage() {
        return entriesPerPage;
    }

    @DataBoundSetter
    public void setEntriesPerPage(int entriesPerPage) {
        this.entriesPerPage = entriesPerPage;
        save(); // Save the configuration
    }

    public String getDefaultSortColumn() {
        return defaultSortColumn;
    }

    @DataBoundSetter
    public void setDefaultSortColumn(String defaultSortColumn) {
        this.defaultSortColumn = defaultSortColumn;
        save(); // Save the configuration
    }

    public String getDefaultSortOrder() {
        return defaultSortOrder;
    }

    @DataBoundSetter
    public void setDefaultSortOrder(String defaultSortOrder) {
        this.defaultSortOrder = defaultSortOrder;
        save(); // Save the configuration
    }

    // Static method to access the configuration instance
    public static AgentBuildHistoryConfig get() {
        return GlobalConfiguration.all().get(AgentBuildHistoryConfig.class);
    }

    public ListBoxModel doFillDefaultSortColumnItems() {
        ListBoxModel items = new ListBoxModel();
        items.add("Start Time", "startTime");
        items.add("Build Name and Build Number", "build");
        return items;
    }

    public ListBoxModel doFillDefaultSortOrderItems() {
        ListBoxModel items = new ListBoxModel();
        items.add("Ascending", "asc");
        items.add("Descending", "desc");
        return items;
    }
}

