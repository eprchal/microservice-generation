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

package org.acumos.microservice.services.impl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.acumos.cds.client.CommonDataServiceRestClientImpl;
import org.acumos.cds.domain.MLPArtifact;
import org.acumos.nexus.client.NexusArtifactClient;
import org.acumos.nexus.client.RepositoryLocation;
import org.acumos.onboarding.common.utils.EELFLoggerDelegate;

public class DownloadModelArtifacts {

	private static EELFLoggerDelegate logger = EELFLoggerDelegate.getLogger(DownloadModelArtifacts.class);

	String artifactFileName;
	
	CommonDataServiceRestClientImpl cmnDataService;
	
	public List<String> getModelArtifacts(String solutionId, String revisionId, String userName, String password,
			String nexusUrl, String nexusUserName, String nexusPassword, String dataSource) throws Exception {
		logger.debug(EELFLoggerDelegate.debugLogger, "------ Start getBluePrintNexus-----------------");
		logger.debug(EELFLoggerDelegate.debugLogger, "-------solutionId-----------" + solutionId);
		logger.debug(EELFLoggerDelegate.debugLogger, "-------revisionId-----------" + revisionId);
		
		List<MLPArtifact> mlpArtifactList;
		String nexusURI = "";
		
		List<String> artifactNameArray = new ArrayList<String>();
		
		ByteArrayOutputStream byteArrayOutputStream = null;
		this.cmnDataService = new CommonDataServiceRestClientImpl(dataSource, userName, password, null);
		
		File outputFolder = new File("model");
		outputFolder.mkdirs();

		if (revisionId != null) {
			/*Get the list of Artifacts for the SolutionId and revisionId.*/
			mlpArtifactList = cmnDataService.getSolutionRevisionArtifacts(solutionId, revisionId);
			if (mlpArtifactList != null && !mlpArtifactList.isEmpty()) {

				for (int i = 0; i < mlpArtifactList.size(); i++) {

					if (mlpArtifactList.get(i).getArtifactTypeCode().equals("MI")
							|| mlpArtifactList.get(i).getArtifactTypeCode().equals("MD")) {

						nexusURI = mlpArtifactList.get(i).getUri();

						logger.debug(EELFLoggerDelegate.debugLogger, "------ Nexus URI : " + nexusURI + " -------");
						if (nexusURI != null) {
							RepositoryLocation repositoryLocation = new RepositoryLocation();
							repositoryLocation.setId("1");
							repositoryLocation.setUrl(nexusUrl);
							repositoryLocation.setUsername(nexusUserName);
							repositoryLocation.setPassword(nexusPassword);
							NexusArtifactClient artifactClient = new NexusArtifactClient(repositoryLocation);

							byteArrayOutputStream = artifactClient.getArtifact(nexusURI);
							if (!nexusURI.isEmpty()) {
								artifactFileName = nexusURI.substring(nexusURI.lastIndexOf("/") + 1, nexusURI.length());
								artifactNameArray.add(artifactFileName);

							}
						}
						if (byteArrayOutputStream != null) {
							byteArrayOutputStream.close();
						}
						File file = new File(outputFolder,artifactFileName);
						FileOutputStream fout = new FileOutputStream(file);
						fout.write(byteArrayOutputStream.toByteArray());
						fout.flush();
						fout.close();

					}
				}
			}
		}
		return artifactNameArray;
	}
}
