package com.agenthub.domain.workspace.service;

import com.agenthub.domain.workspace.model.FileDiffEntry;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Service
public class JGitWorkspaceManager {

    private static final Logger log = LoggerFactory.getLogger(JGitWorkspaceManager.class);

    public void initWorkspace(File workspaceDir) throws Exception {
        if (!workspaceDir.exists()) {
            workspaceDir.mkdirs();
        }
        File gitDir = new File(workspaceDir, ".git");
        if (!gitDir.exists()) {
            try (Git git = Git.init().setDirectory(workspaceDir).call()) {
                File readme = new File(workspaceDir, "README.md");
                if (!readme.exists()) {
                    Files.writeString(readme.toPath(), "# AgentHub Project Workspace\nInitial Baseline.", StandardCharsets.UTF_8);
                }
                git.add().addFilepattern(".").call();
                git.commit().setMessage("Initial project baseline commit").setAuthor("AgentHub", "bot@agenthub.local").call();
                log.info("Initialized Git workspace baseline at: {}", workspaceDir.getAbsolutePath());
            }
        }
    }

    public void commitSnapshot(File workspaceDir, String message) throws Exception {
        try (Git git = Git.open(workspaceDir)) {
            git.add().addFilepattern(".").call();
            git.commit().setMessage(message).setAuthor("AgentHub", "bot@agenthub.local").call();
        }
    }

    public List<FileDiffEntry> computeWorkspaceDiff(File workspaceDir) throws Exception {
        List<FileDiffEntry> diffEntries = new ArrayList<>();
        if (!workspaceDir.exists()) {
            workspaceDir.mkdirs();
        }
        if (!new File(workspaceDir, ".git").exists()) {
            initWorkspace(workspaceDir);
        }

        try (Git git = Git.open(workspaceDir);
             Repository repo = git.getRepository();
             ObjectReader reader = repo.newObjectReader()) {

            ObjectId headId = repo.resolve("HEAD^{tree}");
            if (headId == null) {
                return diffEntries;
            }

            CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
            oldTreeIter.reset(reader, headId);

            FileTreeIterator newTreeIter = new FileTreeIterator(repo);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (DiffFormatter df = new DiffFormatter(out)) {
                df.setRepository(repo);
                df.setDiffComparator(RawTextComparator.DEFAULT);
                df.setDetectRenames(true);

                List<DiffEntry> entries = df.scan(oldTreeIter, newTreeIter);
                for (DiffEntry entry : entries) {
                    out.reset();
                    df.format(entry);
                    String diffText = out.toString(StandardCharsets.UTF_8);

                    int added = 0;
                    int deleted = 0;
                    for (Edit edit : df.toFileHeader(entry).toEditList()) {
                        deleted += edit.getEndA() - edit.getBeginA();
                        added += edit.getEndB() - edit.getBeginB();
                    }

                    FileDiffEntry.ChangeType changeType = switch (entry.getChangeType()) {
                        case ADD -> FileDiffEntry.ChangeType.ADD;
                        case DELETE -> FileDiffEntry.ChangeType.DELETE;
                        default -> FileDiffEntry.ChangeType.MODIFY;
                    };

                    diffEntries.add(new FileDiffEntry(
                            entry.getOldPath(),
                            entry.getNewPath(),
                            changeType,
                            diffText,
                            added,
                            deleted
                    ));
                }
            }
        }
        return diffEntries;
    }
}
