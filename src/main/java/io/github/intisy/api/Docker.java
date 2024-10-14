package io.github.intisy.api;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import io.github.intisy.simple.logger.StaticLogger;

import java.util.List;

@SuppressWarnings("unused")
public class Docker {
    private final DockerClient dockerClient;
    public Docker(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }
    public void deleteDockerImage(String imageId) {
        dockerClient.removeImageCmd(imageId).withForce(true).exec();
    }

    public String getImageIdByTag(String imageTag) {
        StaticLogger.note("Getting image by ID: " + imageTag + "...");
        List<Image> images = dockerClient.listImagesCmd()
                .exec();
        if (!images.isEmpty()) {
            for (Image image : images) {
                if (image.getRepoTags().length > 0) {
                    if (image.getRepoTags()[0].equals(imageTag)) {
                        StaticLogger.success("Successfully got Image from ID: " + imageTag);
                        return image.getId();
                    }
                }
            }
        }
        return null;
    }

    public void deleteImageAndContainersByTag(String imageTag) {
        StaticLogger.note("Deleting old image and containers...");
        String imageId = getImageIdByTag(imageTag);
        if (imageId != null) {
            List<Container> containers;
            boolean containersFound;
            do {
                containers = dockerClient.listContainersCmd().withShowAll(true).exec();
                containersFound = false;
                for (Container container : containers) {
                    if (imageId.equals(container.getImageId())) {
                        containersFound = true;
                        if ("running".equalsIgnoreCase(container.getState())) {
                            dockerClient.stopContainerCmd(container.getId()).exec();
                        }
                        dockerClient.removeContainerCmd(container.getId()).exec();
                    }
                }
                if (containersFound) {
                    try {
                        StaticLogger.warning("Undeleted Containers found, waiting 1 second...");
                        Thread.sleep(1000); // Wait for 1 second before checking again
                    } catch (InterruptedException e) {
                        StaticLogger.exception(e);
                    }
                }
            } while (containersFound);

            deleteDockerImage(imageId);
        } else {
            StaticLogger.error("Image with tag '" + imageTag + "' not found.");
        }
    }
    public void deleteImageByTag(String imageTag) {
        String imageId = getImageIdByTag(imageTag);
        if (imageId != null) {
            deleteDockerImage(imageId);
        } else {
            StaticLogger.error("Image with tag '" + imageTag + "' not found.");
        }
    }
}
