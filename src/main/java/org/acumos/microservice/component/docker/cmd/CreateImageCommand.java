/*-
 * ===============LICENSE_START=======================================================
 * Acumos
 * ===================================================================================
 * Copyright (C) 2017 AT&T Intellectual Property & Tech Mahindra. All rights reserved.
 * ===================================================================================
 * This Acumos software file is distributed by AT&T and Tech Mahindra
 * under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *      http://www.apache.org/licenses/LICENSE-2.0
 *  
 * This file is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ===============LICENSE_END=========================================================
 */

package org.acumos.microservice.component.docker.cmd;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.acumos.onboarding.common.utils.EELFLoggerDelegate;
import org.acumos.onboarding.common.utils.LogBean;
import org.acumos.onboarding.common.utils.LogThreadLocal;
import org.acumos.onboarding.common.utils.OnboardingConstants;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageCmd;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.core.command.BuildImageResultCallback;

/**
 * This command creates a new image from specified Dockerfile.
 *
 * @see <A HREF=
 *      "http://docs.docker.com/reference/api/docker_remote_api_v1.13/#build-an-image-from-dockerfile-via-stdin">Docker
 *      build</A>
 */
public class CreateImageCommand extends DockerCommand {

	private static final EELFLoggerDelegate logger = EELFLoggerDelegate.getLogger(CreateImageCommand.class);

	private final File dockerFolder;

	private final String imageName;

	private final String imageTag;

	private final String dockerFile;

	private final boolean noCache;

	private final boolean rm;

	private String buildArgs;

	private String imageId;

	public CreateImageCommand(File dockerFolder, String imageName, String imageTag, String dockerFile, boolean noCache,
			boolean rm) {
		this.dockerFolder = dockerFolder;
		this.imageName = imageName;
		this.imageTag = imageTag;
		this.dockerFile = dockerFile;
		this.noCache = noCache;
		this.rm = rm;
	}

	public String getBuildArgs() {
		return buildArgs;
	}

	public void setBuildArgs(String buildArgs) {
		this.buildArgs = buildArgs;
	}

	public String getImageId() {
		return imageId;
	}

	@Override
	public void execute() throws DockerException {
		if (dockerFolder == null) {
			logger.error(EELFLoggerDelegate.errorLogger, "dockerFolder is not configured");
			throw new IllegalArgumentException("dockerFolder is not configured");
		}
		if (imageName == null) {
			logger.error(EELFLoggerDelegate.errorLogger, "imageName is not configured");
			throw new IllegalArgumentException("imageName is not configured");
		}
		if (imageTag == null) {
			logger.error(EELFLoggerDelegate.errorLogger, "imageName is not configured");
			throw new IllegalArgumentException("imageTag is not configured");
		}
		if (!dockerFolder.exists()) {
			logger.error(EELFLoggerDelegate.errorLogger, "configured dockerFolder '" + dockerFolder + "' does not exist.");
			throw new IllegalArgumentException("configured dockerFolder '" + dockerFolder + "' does not exist.");
		}
		final Map<String, String> buildArgsMap = new HashMap<String, String>();
		if ((buildArgs != null) && (!buildArgs.trim().isEmpty())) {
			logger.debug(EELFLoggerDelegate.debugLogger,"Parsing buildArgs: " + buildArgs);
			String[] split = buildArgs.split(",|;");
			for (String arg : split) {
				String[] pair = arg.split("=");
				if (pair.length == 2) {
					buildArgsMap.put(pair[0].trim(), pair[1].trim());
				} else {
					logger.error(EELFLoggerDelegate.debugLogger,"Invalid format for " + arg + ". Buildargs should be formatted as key=value");
				}
			}
		}
		String dockerFile = this.dockerFile == null ? "Dockerfile" : this.dockerFile;
		File docker = new File(dockerFolder, dockerFile);
		if (!docker.exists()) {
			logger.error(EELFLoggerDelegate.errorLogger, "Configured Docker file '%s' does not exist. {}", dockerFile);
			throw new IllegalArgumentException(String.format("Configured Docker file '%s' does not exist.", dockerFile));
		}
		DockerClient client = getClient();
		try {
			LogBean logBean = LogThreadLocal.get();
			String fileName = logBean.getFileName();
			String logPath = logBean.getLogPath();
			logger.debug(EELFLoggerDelegate.debugLogger,"Log FileName in createImgCmd : "+fileName);
			BuildImageResultCallback callback = new BuildImageResultCallback() {
				@Override
				public void onNext(BuildResponseItem item) {
					if (item.getStream() != null) {
						String strStep = new String(item.getStream());
						logger.info("\t" + strStep);
						logger.debug(EELFLoggerDelegate.debugLogger,strStep);
						addLogs(strStep);
					} else {
						logger.info("\t" + item);
					}
					super.onNext(item);
				}
				
                /**
                * Add logs into file specific to model
                * @param strStep
                */
				private void addLogs(String strStep) {
					if (fileName != null) {
						File file = new java.io.File(logPath);
						try {
							FileWriter fout = new FileWriter(file.getPath() + File.separator + fileName, true);
							fout.write(new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date()) + "  "
									+ OnboardingConstants.lOG_TYPE_DEBUG + "  " + strStep + "\n");
							fout.close();
						} catch (IOException e) {
							//info to avoid infinite loop.logger.debug call again calls addlog method
							logger.info("Exception occured while adding logs in log file: CreateImageCmd "
									+ e.getMessage());
						}
					} else {
						//info to avoid infinite loop.logger.debug call again calls addlog method
						logger.info("FileName is null for adding logs : CreateImgCmd");
					}
				}


				@Override
				public void onError(Throwable throwable) {
					//logger.error(EELFLoggerDelegate.errorLogger,"Failed to creating docker image", throwable);
					logger.error("Failed to creating docker image", throwable);
					super.onError(throwable);
				}
			};
			
			BuildImageCmd buildImageCmd = client.buildImageCmd(docker)
					.withTags(new HashSet<>(Arrays.asList(imageName + ":" + imageTag))).withNoCache(noCache)
					.withRemove(rm);// .withTag(imageName + ":" + imageTag)
			if (!buildArgsMap.isEmpty()) {
				for (final Map.Entry<String, String> entry : buildArgsMap.entrySet()) {
					buildImageCmd = buildImageCmd.withBuildArg(entry.getKey(), entry.getValue());
				}
			}
			BuildImageResultCallback result = buildImageCmd.exec(callback);
			this.imageId = result.awaitImageId();

		} catch (Exception e) {
			logger.error(EELFLoggerDelegate.errorLogger, "Error {}", e);
			throw new RuntimeException(e);
		}
	}

	@Override
	public String getDisplayName() {
		return "Create/build image";
	}
}
