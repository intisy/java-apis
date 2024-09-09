package io.github.intisy.api;

import io.github.intisy.simple.logger.StaticLogger;
import io.github.intisy.utils.custom.VersionAsset;
import io.github.intisy.utils.utils.ThreadUtils;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.io.file.PathUtils;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.kohsuke.github.GHAsset;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GitHub;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class Git {
    String apiKey;
    public Git(String apiKey) {
        this.apiKey = apiKey;
    }
    public boolean cloneRepository(Path path, String repoName, String repoOwner) throws IOException {
        String repositoryURL = "https://github.com/" + repoOwner + "/" + repoName;
        CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(repoOwner, apiKey);
        if (!path.toFile().exists()) {
            try {
                StaticLogger.note("Cloning repository... (" + repositoryURL + ")");
                try {
                    org.eclipse.jgit.api.Git.cloneRepository()
                            .setURI(repositoryURL)
                            .setCredentialsProvider(credentialsProvider)
                            .setDirectory(path.toFile())
                            .call();
                    StaticLogger.success("Repository cloned successfully.");
                    return true;
                } catch (RuntimeException | TransportException e) {
                    StaticLogger.warning("Github exception while cloning repository: " + e.getMessage() + " (retrying in 5 seconds...)");
                    ThreadUtils.sleep(5000);
                    PathUtils.deleteDirectory(path);
                    return cloneRepository(path, repoName, repoOwner);
                }
            } catch (Exception e) {
                StaticLogger.exception(e);
            }
        } else {
            try {
                Repository repository = org.eclipse.jgit.api.Git.open(path.toFile()).getRepository();
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
                    return true;
                } else {
                    return false;
                }
            } catch (Exception e) {
                StaticLogger.warning("Github exception while pulling repository: " + e.getMessage() + " (retrying in 5 seconds...)");
                ThreadUtils.sleep(5000);
                PathUtils.deleteDirectory(path);
                path.toFile().delete();
                return cloneRepository(path, repoName, repoOwner);
            }
        }
        return false;
    }
    public VersionAsset getAsset(String repoName, String repoOwner, String fileName) {
        StaticLogger.debug("Searching for newest jar file from " + repoName + " assets...");
        try {
            // Connect to GitHub using OAuth token
            GitHub github = GitHub.connectUsingOAuth(apiKey);
            List<GHRelease> releases = github.getRepository(repoOwner + "/" + repoName).listReleases().toList();

            // Find the release by tag name
            GHRelease targetRelease = null;
            double top = 0;
            for (GHRelease release : releases) {
                String tag = release.getTagName();
                int divider = 1;
                double current = 0;
                for (String number : tag.split("\\.")) {
                    current += Double.parseDouble(number) / divider;
                    divider *= 1000;
                }
                if (current > top) {
                    top = current;
                    targetRelease = release;
                }
            }

            // Get assets of the target release
            if (targetRelease != null) {
                List<GHAsset> assets = targetRelease.getAssets();
                if (!assets.isEmpty()) {
                    StaticLogger.debug("Found " + assets.size() + " asset(s) in the release");
                    for (GHAsset asset : assets) {
                        if (asset.getName().equals(fileName))
                            return new VersionAsset(targetRelease.getTagName(), asset);
                    }
                } else {
                    StaticLogger.warning("No assets found for the release");
                }
            } else{
                StaticLogger.warning("Release not found");
            }
        } catch (IOException e) {
            StaticLogger.warning("Github exception while pulling asset: " + e.getMessage() + " (retrying in 5 seconds...)");
            ThreadUtils.sleep(5000);
            return getAsset(repoName, repoOwner, fileName);
        }
        throw new RuntimeException("Could not find an valid asset");
    }
    public void jar(File direction, GHAsset asset, String repoName, String repoOwner) throws IOException {
        String assetName = asset.getName();
        String downloadUrl = "https://api.github.com/repos/" + repoOwner + "/" + repoName + "/releases/assets/" + asset.getId();
        StaticLogger.note("Downloading jar file from Github assets... (" + downloadUrl + ")");
        OkHttpClient client = new OkHttpClient();

        // Build request with authorization header
        Request request = new Request.Builder()
                .url(downloadUrl)
                .addHeader("Accept", "application/octet-stream")
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();

        // Execute the request
        Response response = client.newCall(request).execute();

        // Check for successful response
        if (!response.isSuccessful()) {
            StaticLogger.warning("Failed to download asset: " + response.body() + " (retrying in 5 seconds...)");
            ThreadUtils.sleep(5000);
            response.close();
            jar(direction, asset, repoName, repoOwner);
        } else {
            // Read the response body and write to a file (replace with your logic)
            byte[] bytes = response.body().bytes();
            StaticLogger.success("Downloaded asset: " + assetName);
            try (FileOutputStream fos = new FileOutputStream(direction)) {
                fos.write(bytes); // Write bytes to the file
                StaticLogger.success("Bytes successfully written to the file.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
