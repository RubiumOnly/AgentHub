package com.agenthub.project.dto.onboarding;

import java.util.List;

/**
 * Result of scanning repository environment, framework, and build tools.
 */
public class EnvironmentScanResultView {

    private String language;
    private String framework;
    private String buildTool;
    private String testRunner;
    private String suggestedBuildCommand;
    private String suggestedTestCommand;
    private List<String> detectedFiles;
    private String securityNotice;

    public EnvironmentScanResultView() {
        this.securityNotice = "安全须知：扫描结果仅供参考，系统严禁自动执行未知构建命令，需用户审查确认。";
    }

    public EnvironmentScanResultView(String language, String framework, String buildTool,
                                    String testRunner, String suggestedBuildCommand,
                                    String suggestedTestCommand, List<String> detectedFiles) {
        this.language = language;
        this.framework = framework;
        this.buildTool = buildTool;
        this.testRunner = testRunner;
        this.suggestedBuildCommand = suggestedBuildCommand;
        this.suggestedTestCommand = suggestedTestCommand;
        this.detectedFiles = detectedFiles != null ? detectedFiles : List.of();
        this.securityNotice = "安全须知：扫描结果仅供参考，系统严禁自动执行未知构建命令，需用户审查确认。";
    }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getFramework() { return framework; }
    public void setFramework(String framework) { this.framework = framework; }

    public String getBuildTool() { return buildTool; }
    public void setBuildTool(String buildTool) { this.buildTool = buildTool; }

    public String getTestRunner() { return testRunner; }
    public void setTestRunner(String testRunner) { this.testRunner = testRunner; }

    public String getSuggestedBuildCommand() { return suggestedBuildCommand; }
    public void setSuggestedBuildCommand(String suggestedBuildCommand) { this.suggestedBuildCommand = suggestedBuildCommand; }

    public String getSuggestedTestCommand() { return suggestedTestCommand; }
    public void setSuggestedTestCommand(String suggestedTestCommand) { this.suggestedTestCommand = suggestedTestCommand; }

    public List<String> getDetectedFiles() { return detectedFiles; }
    public void setDetectedFiles(List<String> detectedFiles) { this.detectedFiles = detectedFiles; }

    public String getSecurityNotice() { return securityNotice; }
    public void setSecurityNotice(String securityNotice) { this.securityNotice = securityNotice; }
}
