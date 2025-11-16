package io.jenkins.plugins.agent_build_history;

import hudson.model.Result;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest2;

@Restricted(NoExternalUse.class)
public class Utils {

  static boolean includeRun(Result result, String statusFilter) {
    if (!"all".equals(statusFilter) && result != null) {
      if ("success".equalsIgnoreCase(statusFilter) && result != Result.SUCCESS) {
        return false;
      }
      if ("unstable".equalsIgnoreCase(statusFilter) && result != Result.UNSTABLE) {
        return false;
      }
      if ("failure".equalsIgnoreCase(statusFilter) && result != Result.FAILURE) {
        return false;
      }
      if ("not_built".equalsIgnoreCase(statusFilter) && result != Result.NOT_BUILT) {
        return false;
      }
      return !"aborted".equalsIgnoreCase(statusFilter) || result == Result.ABORTED;
    }
    return true;
  }

  public static int getRequestInteger(StaplerRequest2 req, String name, int defaultValue) {
    return getDefaultInt(req.getParameter(name), defaultValue);
  }

  public static int getDefaultInt(String value, int defaultValue) {
    try {
      if (value == null) {
        return defaultValue;
      }
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }
}
