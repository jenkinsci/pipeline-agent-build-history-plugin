package io.jenkins.plugins.agent_build_history;

import hudson.model.Result;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
public class Utils {

  static boolean includeRun(Result result, String statusFilter) {
    if (!"all".equals(statusFilter) && result != null) {
      if ("success".equals(statusFilter) && result != Result.SUCCESS) {
        return false;
      }
      if ("unstable".equals(statusFilter) && result != Result.UNSTABLE) {
        return false;
      }
      if ("failure".equals(statusFilter) && result != Result.FAILURE) {
        return false;
      }
      if ("aborted".equals(statusFilter) && result != Result.ABORTED) {
        return false;
      }
    }
    return true;
  }

}
