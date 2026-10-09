package com.agenthub.sandbox.domain.security;

import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Security policy and command firewall for sandbox process executions.
 * Defensively inspects commands for destructive filesystem operations, remote code injection,
 * malicious shell chaining, and restricts executable binaries to an approved whitelist.
 */
public class CommandSecurityGuard {

    private static final Logger log = LoggerFactory.getLogger(CommandSecurityGuard.class);

    // High-risk destructive and injection patterns
    private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
            // Destructive file deletion (supports -rf, -fr, separated -r -f, --recursive --force, and quoted targets)
            Pattern.compile("(?i)\\brm\\s+.*(-[a-z]*r[a-z]*f[a-z]*|-[a-z]*f[a-z]*r[a-z]*|(-r|-R|--recursive)\\b.*(-f|--force)\\b|(-f|--force)\\b.*(-r|-R|--recursive)\\b).*[\"']?([/~]|\\*)"),
            Pattern.compile("(?i)\\brm\\s+(-[a-z]*r[a-z]*|--recursive)\\s+[\"']?([/~]|\\*)"),
            Pattern.compile("(?i)\\brm\\s+.*--no-preserve-root"),
            Pattern.compile("(?i)\\bdel\\s+.*[\"']?[a-zA-Z]:\\\\"),
            Pattern.compile("(?i)\\bformat\\s+[\"']?[a-zA-Z]:"),
            Pattern.compile("(?i)\\brmdir\\s+/[sq]\\s+.*[\"']?[a-zA-Z]:\\\\"),
            Pattern.compile("(?i)\\b(Remove-Item|ri)\\b.*(-Recurse|-r)\\b.*(-Force|-f)\\b.*[\"']?([a-zA-Z]:\\\\|[/~])"),
            // Low-level disk formatting and overwrite
            Pattern.compile("(?i)\\bmkfs(\\.[a-z0-9]+)?\\b"),
            Pattern.compile("(?i)\\bdd\\s+if="),
            Pattern.compile("(?i)\\bfdisk\\b"),
            Pattern.compile("(?i)\\bchmod\\s+(-[a-z]*R[a-z]*\\s+)?777\\s+[/~]"),
            // Remote script pipe injection (e.g. curl http://... | sh)
            Pattern.compile("(?i)\\b(curl|wget)\\s+.*\\|\\s*(ba)?sh\\b"),
            Pattern.compile("(?i)\\b(curl|wget)\\s+.*\\|\\s*python[0-9]*\\b"),
            Pattern.compile("(?i)\\bpowershell.*(-enc|-encodedcommand)\\b"),
            // Malicious shell chaining
            Pattern.compile("(?i)(;|\\&\\&|\\|\\||\\|)\\s*rm\\s+.*(-[a-z]*r[a-z]*f[a-z]*|-[a-z]*f[a-z]*r[a-z]*|(-r|-R|--recursive)\\b.*(-f|--force)\\b|(-f|--force)\\b.*(-r|-R|--recursive)\\b).*[\"']?([/~]|\\*)"),
            Pattern.compile("(?i)(;|\\&\\&|\\|\\||\\|)\\s*rm\\s+(-[a-z]*r[a-z]*|--recursive)\\s+[\"']?([/~]|\\*)"),
            Pattern.compile("(?i)(;|\\&\\&|\\|\\||\\|)\\s*del\\s+.*[\"']?[a-zA-Z]:\\\\"),
            Pattern.compile("(?i)(;|\\&\\&|\\|\\||\\|)\\s*mkfs\\b"),
            Pattern.compile("(?i)(;|\\&\\&|\\|\\||\\|)\\s*dd\\s+if="),
            // Fork bomb & system halt
            Pattern.compile(":\\(\\)\\{\\s*:\\|:\\&\\s*\\};:"),
            Pattern.compile("(?i)\\b(shutdown|reboot|init\\s+0|halt)\\b"),
            // Host overwrite & sensitive password reading
            Pattern.compile("(?i)>\\s*/dev/sd[a-z]"),
            Pattern.compile("(?i)>\\s*/etc/"),
            Pattern.compile("(?i)\\bcat\\s+/etc/(shadow|passwd)\\b")
    );

    // Whitelist of allowed executable base names
    private static final Set<String> ALLOWED_EXECUTABLES = Set.of(
            "node", "npm", "npx", "yarn", "pnpm",
            "java", "javac", "jar", "mvn", "mvnw", "gradle", "gradlew",
            "python", "python3", "pip", "pip3",
            "git",
            "echo", "cat", "ls", "dir", "pwd", "mkdir", "cp", "mv", "find", "grep",
            "head", "tail", "touch", "sleep", "whoami",
            "cmd", "cmd.exe", "powershell", "powershell.exe", "pwsh", "sh", "bash",
            "docker"
    );

    // Explicitly blacklisted dangerous executables
    private static final Set<String> BLACKLISTED_EXECUTABLES = Set.of(
            "sudo", "su", "useradd", "usermod", "userdel", "passwd", "chroot",
            "nc", "netcat", "nmap", "iptables", "ufw",
            "mkfs", "fdisk", "dd", "systemctl", "service", "eval"
    );

    /**
     * Inspects a command line string and arguments against the security policy.
     * Throws BusinessException with ErrorCode.SANDBOX_COMMAND_BLOCKED if a threat is detected.
     */
    public void validateCommand(String command, List<String> args) {
        if (command == null || command.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Command must not be empty");
        }

        String fullCommandLine = buildCommandLineString(command, args);

        // 1. Check dangerous regex patterns
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(fullCommandLine).find()) {
                log.warn("Blocked dangerous command execution matching pattern [{}]: {}", pattern.pattern(), fullCommandLine);
                throw new BusinessException(ErrorCode.SANDBOX_COMMAND_BLOCKED,
                        "High-risk command pattern blocked by security policy: " + pattern.pattern());
            }
        }

        // 2. Validate executable against whitelist
        String baseExecutable = extractBaseExecutable(command);
        if (BLACKLISTED_EXECUTABLES.contains(baseExecutable.toLowerCase())) {
            log.warn("Blocked blacklisted executable [{}] in command: {}", baseExecutable, fullCommandLine);
            throw new BusinessException(ErrorCode.SANDBOX_COMMAND_BLOCKED,
                    "Executable [" + baseExecutable + "] is explicitly forbidden in sandbox");
        }

        if (!ALLOWED_EXECUTABLES.contains(baseExecutable.toLowerCase())) {
            log.warn("Blocked unapproved executable [{}] in command: {}", baseExecutable, fullCommandLine);
            throw new BusinessException(ErrorCode.SANDBOX_COMMAND_BLOCKED,
                    "Executable [" + baseExecutable + "] is not permitted in sandbox whitelist");
        }

        // 3. For shell wrappers (sh, bash, cmd, powershell), deeply validate inner arguments
        if (isShellExecutable(baseExecutable)) {
            validateShellArguments(args);
        }
    }

    private void validateShellArguments(List<String> args) {
        if (args == null || args.isEmpty()) {
            return;
        }
        for (String arg : args) {
            for (Pattern pattern : DANGEROUS_PATTERNS) {
                if (pattern.matcher(arg).find()) {
                    log.warn("Blocked dangerous shell argument matching pattern [{}]: {}", pattern.pattern(), arg);
                    throw new BusinessException(ErrorCode.SANDBOX_COMMAND_BLOCKED,
                            "Dangerous shell script argument blocked: " + pattern.pattern());
                }
            }
        }
    }

    private boolean isShellExecutable(String baseExecutable) {
        String lower = baseExecutable.toLowerCase();
        return lower.equals("sh") || lower.equals("bash") || lower.equals("cmd") ||
                lower.equals("cmd.exe") || lower.equals("powershell") ||
                lower.equals("powershell.exe") || lower.equals("pwsh");
    }

    public String extractBaseExecutable(String command) {
        String trimmed = command.trim();
        // Remove surrounding quotes if present (e.g. "node" or 'git')
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
                (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            if (trimmed.length() >= 2) {
                trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
            }
        }
        // Strip path separators if command was given as /usr/bin/node or C:\Program Files\nodejs\node.exe
        int lastSlash = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
        if (lastSlash >= 0 && lastSlash < trimmed.length() - 1) {
            trimmed = trimmed.substring(lastSlash + 1);
        }
        // Remove .exe / .cmd / .bat suffixes for normalization (case-insensitive)
        String lower = trimmed.toLowerCase();
        if (lower.endsWith(".cmd") || lower.endsWith(".bat") || lower.endsWith(".exe")) {
            trimmed = trimmed.substring(0, trimmed.length() - 4);
        }
        return trimmed;
    }

    private String buildCommandLineString(String command, List<String> args) {
        StringBuilder sb = new StringBuilder(command);
        if (args != null) {
            for (String arg : args) {
                sb.append(" ").append(arg);
            }
        }
        return sb.toString();
    }
}
