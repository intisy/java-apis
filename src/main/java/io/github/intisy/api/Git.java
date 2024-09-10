package io.github.intisy.api;

import io.github.intisy.simple.logger.StaticLogger;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Git {
    String apiKey;
    String repoName;
    String repoOwner;
    File path;
    public Git(String apiKey, String repoName, String repoOwner, File path) {
        this.apiKey = apiKey;
        this.repoName = repoName;
        this.repoOwner = repoOwner;
        this.path = path;
    }
    public static Map<String, Set<String>> getAllChanges(String repoPath) {
        Map<String, Set<String>> changes = new HashMap<>();
        changes.put("added", new HashSet<>());
        changes.put("modified", new HashSet<>());
        changes.put("deleted", new HashSet<>());
        changes.put("untracked", new HashSet<>());
        try {
            File repoDir = new File(repoPath);
            org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.open(repoDir);
            Status status = git.status().call();
            changes.get("added").addAll(status.getAdded());
            changes.get("modified").addAll(status.getModified());
            changes.get("deleted").addAll(status.getRemoved());
            changes.get("untracked").addAll(status.getUntracked());

            git.close();
        } catch (IOException | GitAPIException e) {
            e.printStackTrace();
        }
        return changes;
    }
    public String getLastCommitSHA() {
        try {
            org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.open(path);
            Iterable<RevCommit> commits = git.log().setMaxCount(1).call();
            for (RevCommit commit : commits) {
                return commit.getName();
            }
            git.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    public void cloneRepository() throws GitAPIException {
        String repositoryURL = "https://github.com/" + repoOwner + "/" + repoName;
        StaticLogger.note("Cloning repository... (" + repositoryURL + ")");
        CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(repoOwner, apiKey);
        org.eclipse.jgit.api.Git.cloneRepository()
                .setURI(repositoryURL)
                .setCredentialsProvider(credentialsProvider)
                .setDirectory(path)
                .call();
        StaticLogger.success("Repository cloned successfully.");
    }
    public boolean pushRepository(String filePath, String commitMessage) {
        CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(repoOwner, apiKey);
        try {
            org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.open(path);
            File file = new File(path, filePath);
            if (file.exists()) {
                git.add().addFilepattern(filePath).call();
            } else {
                git.rm().addFilepattern(filePath).call();
            }
            git.commit().setMessage(commitMessage).call();
            git.push()
                    .setCredentialsProvider(credentialsProvider)
                    .setRemote(Constants.DEFAULT_REMOTE_NAME)
                    .call();
            git.close();
            return true;

        } catch (IOException | GitAPIException e) {
            e.printStackTrace();
        }
        return false;
    }
    public void pullRepository() throws GitAPIException, IOException {
        CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(repoOwner, apiKey);
        Repository repository = org.eclipse.jgit.api.Git.open(path).getRepository();
        org.eclipse.jgit.api.Git git = new org.eclipse.jgit.api.Git(repository);
        git.fetch().setCredentialsProvider(credentialsProvider).call();
        String currentBranch = repository.getBranch();
        List<Ref> remoteBranches = git.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call();
        boolean updateAvailable = false;
        for (Ref remoteBranch : remoteBranches) {
            if (remoteBranch.getName().endsWith(currentBranch)) {
                updateAvailable = repository.resolve(remoteBranch.getName() + "^{commit}") != null;
                break;
            }
        }
        if (updateAvailable) {
            PullCommand pullCmd = git.pull()
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(repoOwner, apiKey))
                    .setRemoteBranchName("main");
            StaticLogger.note("Pulling Repository...");
            PullResult result = pullCmd.call();
            if (!result.isSuccessful()) {
                StaticLogger.error("Pull failed: " + result);
            } else {
                StaticLogger.success("Successfully pulled repository.");
            }
        }
    }
}
