package io.github.intisy.api;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import io.github.intisy.simple.logger.StaticLogger;

import java.util.List;

public class Docker {
    public static void deleteDockerImage(DockerClient dockerClient, String imageId) {
        // Remove the Docker image
        dockerClient.removeImageCmd(imageId).withForce(true).exec();
    }

    public static String getImageIdByTag(DockerClient dockerClient, String imageTag) {
        StaticLogger.note("Getting image by ID: " + imageTag + "...");
        // Get the image ID based on the image tag
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

    public static void deleteImageAndContainersByTag(DockerClient dockerClient, String imageTag) {
        StaticLogger.note("Deleting old image and containers...");
        // Find the image ID based on the image tag
        String imageId = getImageIdByTag(dockerClient, imageTag);
        if (imageId != null) {
            // Find containers using the specified image ID
            List<Container> containers;
            boolean containersFound;
            do {
                containers = dockerClient.listContainersCmd().withShowAll(true).exec();
                containersFound = false;
                // Stop and remove each container
                for (Container container : containers) {
                    if (imageId.equals(container.getImageId())) {
                        containersFound = true;
                        if ("running".equalsIgnoreCase(container.getState())) {
                            dockerClient.stopContainerCmd(container.getId()).exec();
                        }
                        dockerClient.removeContainerCmd(container.getId()).exec();
                    }
                }
                // Wait before checking again
                if (containersFound) {
                    try {
                        StaticLogger.warning("Undeleted Containers found, waiting 1 second...");
                        Thread.sleep(1000); // Wait for 1 second before checking again
                    } catch (InterruptedException e) {
                        StaticLogger.exception(e);
                    }
                }
            } while (containersFound);

            // Remove the Docker image
            Docker.deleteDockerImage(dockerClient, imageId);
        } else {
            StaticLogger.error("Image with tag '" + imageTag + "' not found.");
        }
    }
    public static void deleteImageByTag(DockerClient dockerClient, String imageTag) {
        String imageId = getImageIdByTag(dockerClient, imageTag);
        if (imageId != null) {
            Docker.deleteDockerImage(dockerClient, imageId);
        } else {
            StaticLogger.error("Image with tag '" + imageTag + "' not found.");
        }
    }
}
