package io.jenkins.plugins.agent_build_history;

import hudson.Extension;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class AgentBuildHistoryConfig extends GlobalConfiguration {
  private static final Logger LOGGER = Logger.getLogger(AgentBuildHistoryConfig.class.getName());

  private String storageDir = getDefaultStorageDir();

  public AgentBuildHistoryConfig() {
    load(); // Load the persisted configuration
    ensureStorageDir();
  }

  private String getDefaultStorageDir() {
    String jenkinsRootDir = Jenkins.get().getRootDir().getAbsolutePath();
    return jenkinsRootDir + File.separator + "io.jenkins.plugins.agent_build_history.serialized_data";
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

  // Static method to access the configuration instance
  public static AgentBuildHistoryConfig get() {
    return GlobalConfiguration.all().get(AgentBuildHistoryConfig.class);
  }
}

