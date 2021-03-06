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

package org.acumos.microservice.component.docker.preparation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.List;
import java.util.Properties;

import org.acumos.onboarding.common.exception.AcumosServiceException;
import org.acumos.onboarding.common.utils.EELFLoggerDelegate;
import org.acumos.onboarding.common.utils.UtilityFunction;
import org.acumos.onboarding.component.docker.preparation.Metadata;
import org.acumos.onboarding.component.docker.preparation.MetadataParser;
import org.acumos.onboarding.component.docker.preparation.Requirement;

public class H2ODockerPreparator { 
	private Metadata metadata;

	private String rVersion;
	private String serverPort;
	private static final EELFLoggerDelegate logger = EELFLoggerDelegate.getLogger(H2ODockerPreparator.class);

	public H2ODockerPreparator(MetadataParser metadataParser) throws AcumosServiceException {
		this.metadata = metadataParser.getMetadata();

		int[] runtimeVersion = versionAsArray(metadata.getRuntimeVersion());
		if (runtimeVersion[0] == 0) { 
			int[] baseVersion = new int[] { 0, 0, 1 };
			if (compareVersion(baseVersion, runtimeVersion) >= 0) {
				this.rVersion = "3.3.2";
			} else {
				throw new AcumosServiceException(AcumosServiceException.ErrorCode.INVALID_PARAMETER,
						"Unspported r version " + metadata.getRuntimeVersion());
			}
		} else {
			throw new AcumosServiceException(AcumosServiceException.ErrorCode.INVALID_PARAMETER,
					"Unspported r version " + metadata.getRuntimeVersion());
		}
	}

	public void prepareDockerApp(File outputFolder) throws AcumosServiceException {

		Properties prop = new Properties();
		InputStream input = null;
		try {
			input = new FileInputStream(new File(outputFolder, "application.properties"));
			prop.load(input);
			serverPort = prop.getProperty("server.port");
			if(serverPort.equals(null) || serverPort.equals(""))
			{
				serverPort = "3330";
			}			
		} catch (IOException e) {
			logger.error(EELFLoggerDelegate.errorLogger,e.getMessage());
		}
		this.createDockerFile(new File(outputFolder, "Dockerfile"), new File(outputFolder, "Dockerfile"));
		this.createRequirements(new File(outputFolder, "requirements.txt"), new File(outputFolder, "requirements.txt"));
	}

	public void createRequirements(File inPackageRFile, File outPackageRFile) throws AcumosServiceException {
		try {
			List<Requirement> requirements = this.metadata.getRequirements();
			StringBuilder reqBuilder = new StringBuilder();
			for (Requirement requirement : requirements) {
				reqBuilder.append("\"" + requirement.name + "\",");
			}
			String reqAsString = reqBuilder.toString();
			reqAsString = reqAsString.substring(0, reqAsString.length() - 1);
			String packageRFileAsString = new String(UtilityFunction.toBytes(inPackageRFile));
			packageRFileAsString = MessageFormat.format(packageRFileAsString, new Object[] { reqAsString });
			FileWriter writer = new FileWriter(outPackageRFile);
			try {
				writer.write(packageRFileAsString.trim());
			} finally {
				writer.close();
			}
		} catch (IOException e) {
			throw new AcumosServiceException(AcumosServiceException.ErrorCode.INTERNAL_SERVER_ERROR,
					"Fail to create dockerFile for input model", e);
		}
	}

	public void createDockerFile(File inDockerFile, File outDockerFile) throws AcumosServiceException {
		try {

			String dockerFileAsString = new String(UtilityFunction.toBytes(inDockerFile));

			String modelname = this.metadata.getSolutionName();

			dockerFileAsString = MessageFormat.format(dockerFileAsString,
					new Object[] { serverPort, "H2OModelService.jar", modelname + ".zip" });

			FileWriter writer = new FileWriter(outDockerFile);
			try {
				writer.write(dockerFileAsString.trim());
			} finally {
				writer.close();
			}
		} catch (IOException e) {
			throw new AcumosServiceException(AcumosServiceException.ErrorCode.INTERNAL_SERVER_ERROR,
					"Fail to create dockerFile for input model", e);
		}
	}

	public static int compareVersion(int[] baseVersion, int[] currentVersion) {
		int result = 0;
		for (int i = 0; i < baseVersion.length; i++) {
			if (currentVersion.length < i + 1 || baseVersion[i] > currentVersion[i]) {
				result = 1;
				break;
			} else if (baseVersion[i] < currentVersion[i]) {
				result = -1;
				break;
			}
		}
		return result;
	}

	public static int[] versionAsArray(String version) {
		String[] versionArr = version.split("\\.");
		int[] versionIntArr = new int[versionArr.length];
		for (int i = 0; i < versionArr.length; i++) {
			versionIntArr[i] = Integer.parseInt(versionArr[i]);
		}
		return versionIntArr;
	}

}
