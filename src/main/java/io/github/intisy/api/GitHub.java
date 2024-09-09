package io.github.intisy.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.intisy.simple.logger.StaticLogger;
import io.github.intisy.utils.custom.HttpDeleteWithBody;
import io.github.intisy.utils.utils.EncryptorUtils;
import io.github.intisy.utils.utils.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class GitHub {

    private final String API_URL = "https://api.github.com";
    private final String repoOwner;
    private final String repoName;
    private final String accessToken;
    private final boolean debug;
    private final List<File> deletedFiles = new ArrayList<>();
    private final List<File> createdFiles = new ArrayList<>();
    private final List<File> modifyFiles = new ArrayList<>();

    public GitHub(String repoOwner, String repoName, String accessToken, boolean debug) {
        this.repoOwner = repoOwner;
        this.repoName = repoName;
        this.accessToken = accessToken;
        this.debug = debug;
    }

    public List<File> getCreatedFiles() {
        return createdFiles;
    }

    public List<File> getDeletedFiles() {
        return deletedFiles;
    }

    public List<File> getModifyFiles() {
        return modifyFiles;
    }

    public boolean doesRepoExist() throws IOException {
        String url = String.format("%s/repos/%s/%s", API_URL, repoOwner, repoName);
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(url);
            httpGet.setHeader("Authorization", "token " + accessToken);
            httpGet.setHeader("Accept", "application/vnd.github.v3+json");
            Boolean value = null;
            String ss = null;
            while (value == null) {
                try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    HttpEntity entity = response.getEntity();
                    String responseString = EntityUtils.toString(entity);
                    JsonParser.parseString(responseString).getAsJsonObject();
                    if (statusCode == 200) {
                        value = true;
                    } else if (statusCode == 404) {
                        value = false;
                    }
                } catch (NullPointerException exception) {
                    StaticLogger.error("Wrong response: " + ss);
                    value = null;
                }
            }
            return value;
        }
    }
    public void downloadGitHubCommit(String sha, Path outputDir) {
        try {
            String repoUrl = String.format("https://github.com/%s/%s/archive/%s.zip", repoOwner, repoName, sha);
            Path zipFilePath = downloadRepoAsZip(repoUrl, outputDir);
            unzip(zipFilePath, outputDir);
            StaticLogger.debug("Repository downloaded successfully from commit SHA " + sha + "!", debug);
        } catch (Exception e) {
            StaticLogger.error("Download failed: " + e.getMessage());
        }
    }

    private Path downloadRepoAsZip(String repoUrl, Path outputDir) throws IOException {
        URL url = new URL(repoUrl);
        String fileName = "repo.zip";
        Path zipFilePath = outputDir.getParent().resolve(fileName);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "token " + accessToken);
        try (InputStream in = connection.getInputStream();
             FileOutputStream fos = new FileOutputStream(zipFilePath.toFile())) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        } finally {
            connection.disconnect();
        }
        return zipFilePath;
    }

    private void unzip(Path zipFilePath, Path destDirectory) throws Exception {
        File destDir = destDirectory.toFile();
        FileUtils.mkdirs(destDir);
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath.toFile()))) {
            ZipEntry entry = zis.getNextEntry();
            assert entry != null;
            String name = entry.getName();
            while ((entry = zis.getNextEntry()) != null) {
                File newFile = newFile(destDir, entry, name);
                if (entry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory " + newFile);
                    }
                } else {
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory " + parent);
                    }
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        byte[] buffer = new byte[4096];
                        int length;
                        while ((length = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, length);
                        }
                    }
                }
            }
        }
    }
    private File newFile(File destinationDir, ZipEntry zipEntry, String name) throws Exception {
        String decrypted = EncryptorUtils.decode(zipEntry.getName().replace(name, ""), "/");
        File destFile = new File(destinationDir, decrypted);
        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();
        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }
        return destFile;
    }

    public JsonObject deleteFolder(String folderPath) throws Exception {
        JsonObject response = null;
        StaticLogger.debug("Deleting folder: " + folderPath.substring(1), debug);
        JsonArray files = getFilesInFolder(EncryptorUtils.encode(folderPath, "/"));
        for (int i = 0; i < files.size(); i++) {
            JsonObject fileObject = files.get(i).getAsJsonObject();
            String filePath = fileObject.get("path").getAsString();
            response = deleteFile("/" + filePath, false);
        }
        return response;
    }

    public void deleteFolder(File path) throws Exception {
        deleteFolder(path, "");
    }
    public void deleteFolder(File path, String folder) throws Exception {
        if (!folder.isEmpty()) {
            StaticLogger.debug("Deleting folder: " + folder.substring(1), debug);
        }
        File[] files = path.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory())
                    deleteFolder(file, folder + "/" + file.getName());
                else {
                    FileUtils.delete(file);
                    deleteFile(folder + "/" + file.getName());
                }
            }
        }
        if (!folder.isEmpty())
            FileUtils.delete(path);
    }

    private JsonArray getFilesInFolder(String folderPath) throws IOException {
        String url = String.format("%s/repos/%s/%s/contents/%s", API_URL, repoOwner, repoName, fixString(folderPath));

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(url);
            httpGet.setHeader("Authorization", "token " + accessToken);
            httpGet.setHeader("Accept", "application/vnd.github.v3+json");

            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                HttpEntity entity = response.getEntity();
                String responseString = EntityUtils.toString(entity);
                JsonElement jsonElement = JsonParser.parseString(responseString);
                if (jsonElement.isJsonArray())
                    return jsonElement.getAsJsonArray();
                else {
                    JsonArray jsonArray = new JsonArray();
                    jsonArray.add(jsonElement);
                    return jsonArray;
                }
            }
        }
    }
    public JsonObject createRepo(String description, boolean isPrivate) throws IOException {
        if (!doesRepoExist()) {
            String url = API_URL + "/user/repos";

            JsonObject json = new JsonObject();
            json.addProperty("name", repoName);
            json.addProperty("description", description);
            json.addProperty("private", isPrivate);
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpPost httpPost = new HttpPost(url);
                httpPost.setHeader("Authorization", "token " + accessToken);
                httpPost.setHeader("Accept", "application/vnd.github.v3+json");
                httpPost.setEntity(new StringEntity(json.toString()));
                JsonObject jsonObject = null;
                while (jsonObject == null) {
                    try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                        HttpEntity entity = response.getEntity();
                        String responseString = EntityUtils.toString(entity);
                        jsonObject = JsonParser.parseString(responseString).getAsJsonObject();
                    } catch (NullPointerException exception) {
                        StaticLogger.error("Wrong response: " + jsonObject);
                        jsonObject = null;
                    }
                }
                return jsonObject;
            }
        } else
            return new JsonObject();
    }

    public JsonObject createFile(String filePath, String fileContent) throws Exception {
        filePath = filePath.substring(1);
        String encryptedFilePath = EncryptorUtils.encode(filePath, "/");
        StaticLogger.debug("Creating file: " + filePath + ", as " + encryptedFilePath, debug);
        String commitMessage = "Create " + filePath;
        String encodedContent = Base64.getEncoder().encodeToString(fileContent.getBytes());
        String url = String.format("%s/repos/%s/%s/contents/%s", API_URL, repoOwner, repoName, fixString(encryptedFilePath));
        JsonObject json = new JsonObject();
        json.addProperty("message", commitMessage);
        json.addProperty("content", encodedContent);
        JsonObject jsonObject = null;
        while (jsonObject == null) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpPut httpPut = new HttpPut(url);
                httpPut.setHeader("Authorization", "token " + accessToken);
                httpPut.setHeader("Accept", "application/vnd.github.v3+json");
                httpPut.setEntity(new StringEntity(json.toString()));
                try (CloseableHttpResponse response = httpClient.execute(httpPut)) {
                    HttpEntity entity = response.getEntity();
                    String responseString = EntityUtils.toString(entity);
                    jsonObject = JsonParser.parseString(responseString).getAsJsonObject();
                }
            } catch (NullPointerException exception) {
                StaticLogger.error("Wrong response: " + jsonObject);
                jsonObject = null;
            }
        }
        return jsonObject;
    }
    public JsonObject updateFile(String filePath, String fileContent) throws Exception {
        filePath = filePath.substring(1);
        StaticLogger.debug("Updating file: " + filePath, debug);
        String commitMessage = "Update " + filePath;
        filePath = EncryptorUtils.encode(filePath, "/");
        String encodedContent = Base64.getEncoder().encodeToString(fileContent.getBytes());
        String url = String.format("%s/repos/%s/%s/contents/%s", API_URL, repoOwner, repoName, fixString(filePath));
        String sha = getFileSha(filePath);
        JsonObject json = new JsonObject();
        json.addProperty("message", commitMessage);
        json.addProperty("content", encodedContent);
        if (sha != null) {
            json.addProperty("sha", sha);
        }
        JsonObject jsonObject = null;
        while (jsonObject == null) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpPut httpPut = new HttpPut(url);
                httpPut.setHeader("Authorization", "token " + accessToken);
                httpPut.setHeader("Accept", "application/vnd.github.v3+json");
                httpPut.setEntity(new StringEntity(json.toString()));
                try (CloseableHttpResponse response = httpClient.execute(httpPut)) {
                    HttpEntity entity = response.getEntity();
                    String responseString = EntityUtils.toString(entity);
                    jsonObject = JsonParser.parseString(responseString).getAsJsonObject();
                }
            } catch (NullPointerException exception) {
                StaticLogger.error("Wrong response: " + jsonObject);
                jsonObject = null;
            }
        }
        return jsonObject;
    }
    public String fixString(String string) {
        return string.replace(" ", "%20");
    }
    public JsonObject deleteFile(String filePath) throws Exception {
        return deleteFile(filePath, true);
    }
    public JsonObject deleteFile(String filePath, boolean encode) throws Exception {
        filePath = filePath.substring(1);
        String encryptedFilePath;
        if (encode)
            encryptedFilePath = EncryptorUtils.encode(filePath, "/");
        else
            encryptedFilePath = filePath;
        StaticLogger.debug("Deleting file: " + filePath + ", as " + encryptedFilePath, debug);
        String commitMessage = "Delete " + filePath;
        String sha = getFileSha(encryptedFilePath);
        String url = String.format("%s/repos/%s/%s/contents/%s", API_URL, repoOwner, repoName, fixString(encryptedFilePath));
        JsonObject jsonObject = null;
        while (jsonObject == null) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpDeleteWithBody httpDelete = getHttpDeleteWithBody(commitMessage, url, sha);
                try (CloseableHttpResponse response = httpClient.execute(httpDelete)) {
                    HttpEntity entity = response.getEntity();
                    String responseString = EntityUtils.toString(entity);
                    jsonObject = JsonParser.parseString(responseString).getAsJsonObject();
                }
            } catch (NullPointerException exception) {
                StaticLogger.error("Wrong response: " + jsonObject);
                jsonObject = null;
            }
        }
        return jsonObject;
    }

    private HttpDeleteWithBody getHttpDeleteWithBody(String commitMessage, String url, String sha) {
        HttpDeleteWithBody httpDelete = new HttpDeleteWithBody(url);
        httpDelete.setHeader("Authorization", "token " + accessToken);
        httpDelete.setHeader("Accept", "application/vnd.github.v3+json");

        // Creating the JSON payload for the commit
        JsonObject json = new JsonObject();
        json.addProperty("message", commitMessage);
        json.addProperty("sha", sha);
        json.addProperty("branch", "main");

        StringEntity entity = new StringEntity(json.toString(), StandardCharsets.UTF_8);
        httpDelete.setEntity(entity);
        return httpDelete;
    }

    private String getFileSha(String filePath) {
        String url = String.format("%s/repos/%s/%s/contents/%s", API_URL, repoOwner, repoName, fixString(filePath));
        JsonObject jsonObject = null;
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(url);
            httpGet.setHeader("Authorization", "token " + accessToken);
            httpGet.setHeader("Accept", "application/vnd.github.v3+json");
            while (jsonObject == null) {
                try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                    HttpEntity entity = response.getEntity();
                    String responseString = EntityUtils.toString(entity);
                    JsonElement responseJson = JsonParser.parseString(responseString);
                    if (responseJson.isJsonArray())
                        jsonObject = responseJson.getAsJsonArray().get(0).getAsJsonObject();
                    else
                        jsonObject = responseJson.getAsJsonObject();
                } catch (NullPointerException exception) {
                    StaticLogger.error("Wrong response: " + jsonObject);
                    jsonObject = null;
                }
            }
            return jsonObject.get("sha").getAsString();
        } catch (Exception e) {
//            StaticLogger.error("Error getting file SHA: " + e.getMessage());
//            StaticLogger.printStackTrace(null);
            return null;
        }
    }
    public void applyChanges(JsonArray commits, Path storagePath) throws Exception {
        List<JsonElement> commitElements = new ArrayList<>();
        for (int i = commits.size()-1; i >= 0; i--) {
            commitElements.add(commits.get(i));
        }
        for (JsonElement commitElement : commitElements) {
            JsonObject commitObject = commitElement.getAsJsonObject();
            String sha = commitObject.get("sha").getAsString();
            String message = commitObject.get("commit").getAsJsonObject().get("message").getAsString();
            StaticLogger.debug("Applying commit " + sha + ": " + message);
            processDiff(fetchCommitDiff(sha), storagePath);
        }
    }

    private void processDiff(String diff, Path storagePath) throws Exception {
        String[] lines = diff.split("\n");
        String currentFile = null;
        List<String> fileContent = new ArrayList<>();
        boolean isNewFile = false;
        boolean isDeletedFile = false;
        boolean inHunk = false;
        int count = 0;
        StaticLogger.debug(" -- from diff: -- ");
        StaticLogger.debug(diff);
        for (String line : lines) {
            if (line.startsWith("diff --git")) {
                if (currentFile != null) {
                    isDeletedFile = false;
                    writeFile(currentFile, storagePath, fileContent, isNewFile);
                }
                String[] parts = line.substring(13).split(" b/");
                if (parts.length == 2) {
                    currentFile = parts[0];
                } else {
                    currentFile = line.substring(13).split(parts[parts.length - 1])[0];
                }
                currentFile = EncryptorUtils.decode(currentFile, "/");
                fileContent = new ArrayList<>();
                isNewFile = false;
                inHunk = false;
            } else if (!isDeletedFile) {
                if (line.startsWith("new file mode")) {
                    isNewFile = true;
                } else if (line.startsWith("deleted file mode")) {
                    assert currentFile != null;
                    File file = storagePath.resolve(currentFile).toFile();
                    deletedFiles.add(file);
                    if (!file.delete())
                        throw new IOException("Failed to delete file: " + currentFile);
                    currentFile = null;
                    File parent = file.getParentFile();
                    if (!parent.equals(storagePath.toFile()) || parent.listFiles() == null || Objects.requireNonNull(parent.listFiles()).length == 0) {
                        if (!parent.delete())
                            throw new IOException("Failed to delete directory: " + parent);
                    }
                    isDeletedFile = true;
                } else if (line.startsWith("---")) {
                    if (!isNewFile && !line.contains("/dev/null")) {
                        fileContent = readFile(currentFile, storagePath);
                    }
                } else if (line.startsWith("@@")) {
                    inHunk = true;
                    count = Integer.parseInt(line.split("\\+")[1].split(",")[0].split(" @@")[0]);
                    if (count == 0) {
                        count = Integer.parseInt(line.split("-")[1].split(",")[0].split(" ")[0]);
                    }
                } else if (inHunk) {
                    if (line.startsWith("+")) {
                        if (line.length() == 1)
                            fileContent.add("");
                        else
                            fileContent.add(count-1, line.substring(1, line.length()-1));
                        count++;
                    } else if (line.startsWith("-")) {
                        fileContent.remove(count-1);
                    } else {
                        count++;
                    }
                }
            }
        }
        if (currentFile != null) {
            writeFile(currentFile, storagePath, fileContent, isNewFile);
        }
    }

    private void writeFile(String filePath, Path storagePath, List<String> content, boolean isNewFile) throws Exception {
        File file = storagePath.resolve(filePath).toFile();
        createdFiles.add(file);
        if (isNewFile) {
            FileUtils.mkdirs(file.getParentFile());
            FileUtils.createNewFile(file);
        }

        try (FileWriter writer = new FileWriter(file)) {
            modifyFiles.add(file);
            for (String line : content) {
                writer.write(line + System.lineSeparator());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> readFile(String filePath, Path storagePath) throws IOException {
        File file = storagePath.resolve(filePath).toFile();
        if (!file.exists()) {
            return new ArrayList<>();
        }
        Charset charset = StandardCharsets.ISO_8859_1;
        return FileUtils.readAllLines(file.toPath(), charset);
    }

    public JsonArray getCommitsSince(String commitSha) throws IOException {
        JsonArray commitsArray = new JsonArray();
        JsonObject commit = getLastCommit();
        if (!commit.get("sha").getAsString().equals(commitSha)) {
            String parent;
            while (!(parent = commit.get("parents").getAsJsonArray().get(0).getAsJsonObject().get("sha").getAsString()).equals(commitSha)) {
                commitsArray.add(commit);
                commit = getCommit(parent);
            }
            commitsArray.add(commit);
            return commitsArray;
        } else
            return new JsonArray();
    }
    public JsonObject getCommit(String commitSha) throws IOException {
        String url = String.format("%s/repos/%s/%s/commits/%s", API_URL, repoOwner, repoName, commitSha);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(url);
            httpGet.setHeader("Authorization", "token " + accessToken);
            httpGet.setHeader("Accept", "application/vnd.github.v3+json");
            JsonObject jsonObject = null;
            while (jsonObject == null) {
                try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                    HttpEntity entity = response.getEntity();
                    String responseString = EntityUtils.toString(entity);
                    jsonObject = JsonParser.parseString(responseString).getAsJsonObject();
                } catch (NullPointerException exception) {
                    StaticLogger.error("Wrong response: " + jsonObject);
                    jsonObject = null;
                }
            }
            return jsonObject;
        }
    }

    public JsonObject getLastCommit() throws IOException {
        String url = String.format("%s/repos/%s/%s/commits?per_page=1", API_URL, repoOwner, repoName);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(url);
            httpGet.setHeader("Authorization", "token " + accessToken);
            httpGet.setHeader("Accept", "application/vnd.github.v3+json");
            JsonObject jsonObject = null;
            while (jsonObject == null) {
                try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                    HttpEntity entity = response.getEntity();
                    String responseString = EntityUtils.toString(entity);
                    jsonObject = JsonParser.parseString(responseString).getAsJsonArray().get(0).getAsJsonObject();
                } catch (NullPointerException exception) {
                    StaticLogger.error("Wrong response: " + jsonObject);
                    jsonObject = null;
                }
            }
            return jsonObject;

        }
    }

    public String fetchCommitDiff(String commitSha) throws IOException {
        String url = String.format("%s/repos/%s/%s/commits/%s", API_URL, repoOwner, repoName, commitSha);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(url);
            httpGet.setHeader("Authorization", "token " + accessToken);
            httpGet.setHeader("Accept", "application/vnd.github.v3.diff");

            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                HttpEntity entity = response.getEntity();
                return EntityUtils.toString(entity);
            }
        }
    }
}
