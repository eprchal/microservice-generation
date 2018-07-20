package org.acumos.microservice.component.docker;

import java.io.File;
import java.io.IOException;

import javax.annotation.PostConstruct;

import org.acumos.cds.client.CommonDataServiceRestClientImpl;
import org.acumos.microservice.component.docker.cmd.CreateImageCommand;
import org.acumos.microservice.component.docker.cmd.PushImageCommand;
import org.acumos.microservice.component.docker.cmd.TagImageCommand;
import org.acumos.microservice.component.docker.preparation.H2ODockerPreparator;
import org.acumos.microservice.component.docker.preparation.JavaGenericDockerPreparator;
import org.acumos.microservice.component.docker.preparation.PythonDockerPreprator;
import org.acumos.microservice.component.docker.preparation.RDockerPreparator;
import org.acumos.onboarding.common.exception.AcumosServiceException;
import org.acumos.onboarding.common.utils.EELFLoggerDelegate;
import org.acumos.onboarding.common.utils.ResourceUtils;
import org.acumos.onboarding.common.utils.UtilityFunction;
import org.acumos.onboarding.component.docker.preparation.Metadata;
import org.acumos.onboarding.component.docker.preparation.MetadataParser;
import org.acumos.onboarding.services.impl.PortalRestClientImpl;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import com.github.dockerjava.api.DockerClient;

public class DockerizeModel {
	
	private static final EELFLoggerDelegate logger = EELFLoggerDelegate.getLogger(DockerizeModel.class);
	
	@Value("${nexus.nexusEndPointURL}")
	protected String nexusEndPointURL;

	@Value("${nexus.nexusUserName}")
	protected String nexusUserName;

	@Value("${nexus.nexusPassword}")
	protected String nexusPassword;

	@Value("${nexus.nexusGroupId}")
	protected String nexusGroupId;

	@Value("${cmndatasvc.cmnDataSvcEndPoinURL}")
	protected String cmnDataSvcEndPoinURL;

	@Value("${cmndatasvc.cmnDataSvcUser}")
	protected String cmnDataSvcUser;

	@Value("${cmndatasvc.cmnDataSvcPwd}")
	protected String cmnDataSvcPwd;

	@Value("${http_proxy}")
	protected String http_proxy;

	@Value("${requirements.extraIndexURL}")
	protected String extraIndexURL;

	@Value("${requirements.trustedHost}")
	protected String trustedHost;

	@Value("${mktPlace.mktPlaceEndPoinURL}")
	protected String portalURL;
	
	protected String modelOriginalName = null;
	
	@Autowired
	protected ResourceLoader resourceLoader;

	//@Autowired
	protected DockerConfiguration dockerConfiguration;
	
	protected MetadataParser metadataParser = null;
	
	protected CommonDataServiceRestClientImpl cdmsClient;

	protected PortalRestClientImpl portalClient;

	protected ResourceUtils resourceUtils;
	
	@PostConstruct
	public void init() {
		logger.debug(EELFLoggerDelegate.debugLogger,"Creating docker service instance");
		this.cdmsClient = new CommonDataServiceRestClientImpl(cmnDataSvcEndPoinURL, cmnDataSvcUser, cmnDataSvcPwd);
		this.portalClient = new PortalRestClientImpl(portalURL);
		this.resourceUtils = new ResourceUtils(resourceLoader);
	}
	
	/*
	 * @Method Name : dockerizeFile Performs complete dockerization process.
	 */
	public String dockerizeFile(MetadataParser metadataParser, File localmodelFile, String solutionID, int deployment_env) throws AcumosServiceException {
		File outputFolder = localmodelFile.getParentFile();
		Metadata metadata = metadataParser.getMetadata();
		logger.debug(EELFLoggerDelegate.debugLogger,"Preparing app in: {}", outputFolder);
		if (metadata.getRuntimeName().equals("python")) {
			outputFolder = new File(localmodelFile.getParentFile(), "app");
			outputFolder.mkdir();
			
			Resource[] resources = null;
			
			if(deployment_env == 2)
			{
				resources = this.resourceUtils.loadResources("classpath*:templates/dcae_python/*");
			}
			else
			{
				resources = this.resourceUtils.loadResources("classpath*:templates/python/*");
			}

			PythonDockerPreprator dockerPreprator = new PythonDockerPreprator(metadataParser, extraIndexURL,
					trustedHost,http_proxy);
			
			for (Resource resource : resources) {
				UtilityFunction.copyFile(resource, new File(outputFolder, resource.getFilename()));
			}
			try {
				File modelFolder = new File(outputFolder, "model");
				UtilityFunction.unzip(localmodelFile, modelFolder.getAbsolutePath());
			} catch (IOException e) {
				logger.error(EELFLoggerDelegate.errorLogger,"Python templatization failed: {}", e);
			}
			dockerPreprator.prepareDockerAppV2(outputFolder);
		} else if (metadata.getRuntimeName().equals("r")) {
			/*DockerClient dockerClient = DockerClientFactory.getDockerClient(dockerConfiguration);
			logger.debug(EELFLoggerDelegate.debugLogger, "Pull onboarding-base-r image from Nexus call started");
			String repo = "nexus3.acumos.org:10004/onboarding-base-r:1.0";
			PullImageCommand pullImageCommand = new PullImageCommand(repo);
			pullImageCommand.setClient(dockerClient);
			pullImageCommand.execute();
			logger.debug(EELFLoggerDelegate.debugLogger, "Pull onboarding-base-r image from Nexus call ended");	*/	
			
			RDockerPreparator dockerPreprator = new RDockerPreparator(metadataParser, http_proxy);
			Resource[] resources = this.resourceUtils.loadResources("classpath*:templates/r/*");
			for (Resource resource : resources) {
				UtilityFunction.copyFile(resource, new File(outputFolder, resource.getFilename()));
			}
			dockerPreprator.prepareDockerApp(outputFolder);
		} else if (metadata.getRuntimeName().equals("javaargus")) {
			try {
				String outputFile = UtilityFunction.getFileName(localmodelFile, outputFolder.toString());
				File tarFile = new File(outputFile);
				tarFile = UtilityFunction.deCompressGZipFile(localmodelFile, tarFile);
				UtilityFunction.unTarFile(tarFile, outputFolder);
			} catch (IOException e) {
				logger.error(EELFLoggerDelegate.errorLogger,"Java Argus templatization failed: {}", e);
			}
		} else if (metadata.getRuntimeName().equals("h2o")) {
			File plugin_root = new File(outputFolder, "plugin_root");
			plugin_root.mkdirs(); 
			File plugin_src = new File(plugin_root, "src");
			plugin_src.mkdirs();
			File plugin_classes = new File(plugin_root, "classes");
			plugin_classes.mkdirs();

			H2ODockerPreparator dockerPreprator = new H2ODockerPreparator(metadataParser);

			Resource[] resources = this.resourceUtils.loadResources("classpath*:templates/h2o/*");
			for (Resource resource : resources) {
				UtilityFunction.copyFile(resource, new File(outputFolder, resource.getFilename()));
			}
			try {
				UtilityFunction.unzip(localmodelFile, outputFolder.getAbsolutePath());

				String mm[] = modelOriginalName.split("\\.");

				File fd = new File(outputFolder.getAbsolutePath() + "/" + mm[0]);

				File ff[] = fd.listFiles();

				if (ff != null) {
					for (File f : ff) {
						FileUtils.copyFileToDirectory(f, outputFolder);
					}
					UtilityFunction.deleteDirectory(new File(outputFolder.getAbsolutePath() + "/" + modelOriginalName));
					UtilityFunction.deleteDirectory(new File(outputFolder.getAbsolutePath() + "/" + mm[0]));
				}

				// Creat solution id - success
			} catch (IOException e) {
				logger.error(EELFLoggerDelegate.errorLogger,"H2O templatization failed", e);
			}
			dockerPreprator.prepareDockerApp(outputFolder);

		} else if (metadata.getRuntimeName().equals("javageneric")) {
			File plugin_root = new File(outputFolder, "plugin_root");
			plugin_root.mkdirs();
			File plugin_src = new File(plugin_root, "src");
			plugin_src.mkdirs();
			File plugin_classes = new File(plugin_root, "classes");
			plugin_classes.mkdirs();

			JavaGenericDockerPreparator dockerPreprator = new JavaGenericDockerPreparator(metadataParser);
			Resource[] resources = this.resourceUtils.loadResources("classpath*:templates/javaGeneric/*");
			for (Resource resource : resources) {
				UtilityFunction.copyFile(resource, new File(outputFolder, resource.getFilename()));
			}

			try {

				UtilityFunction.unzip(localmodelFile, outputFolder.getAbsolutePath());

				String mm[] = modelOriginalName.split("\\.");

				File fd = new File(outputFolder.getAbsolutePath() + "/" + mm[0]);

				File ff[] = fd.listFiles();

				if (ff != null) {
					for (File f : ff) {
						FileUtils.copyFileToDirectory(f, outputFolder);
					}
					UtilityFunction.deleteDirectory(new File(outputFolder.getAbsolutePath() + "/" + modelOriginalName));
					UtilityFunction.deleteDirectory(new File(outputFolder.getAbsolutePath() + "/" + mm[0]));
				}

			} catch (IOException e) {
				logger.error(EELFLoggerDelegate.errorLogger,"Java-Generic templatization failed", e);
			}

			dockerPreprator.prepareDockerApp(outputFolder);

		} else {
			logger.error(EELFLoggerDelegate.errorLogger,"Unspported runtime {}", metadata.getRuntimeName());
			throw new AcumosServiceException(AcumosServiceException.ErrorCode.INVALID_PARAMETER,
					"Unspported runtime " + metadata.getRuntimeName());
		}
		logger.debug(EELFLoggerDelegate.debugLogger,"Resource List");
		listFilesAndFilesSubDirectories(outputFolder);
		logger.debug(EELFLoggerDelegate.debugLogger,"End of Resource List");
		logger.debug(EELFLoggerDelegate.debugLogger,"Started docker client");
		DockerClient dockerClient = DockerClientFactory.getDockerClient(dockerConfiguration);
		logger.debug(EELFLoggerDelegate.debugLogger,"Docker client created successfully");
		try {			
			logger.debug("Docker image creation started");
			String actualModelName = getActualModelName(metadata, solutionID);  
			CreateImageCommand createCMD = new CreateImageCommand(outputFolder, actualModelName,metadata.getVersion(), null, false, true);
			createCMD.setClient(dockerClient);
			createCMD.execute();
			logger.debug(EELFLoggerDelegate.debugLogger,"Docker image creation done");
			// put catch here
			// /Microservice/Docker image nexus creation -success

			// in catch /Microservice/Docker image nexus creation -failure

			// TODO: remove local image

			logger.debug(EELFLoggerDelegate.debugLogger,"Starting docker image tagging");
			String imageTagName = dockerConfiguration.getImagetagPrefix() + File.separator + actualModelName;
			
			String dockerImageURI = imageTagName + ":" + metadata.getVersion();
			
			TagImageCommand tagImageCommand = new TagImageCommand(actualModelName+ ":" + metadata.getVersion(),
					imageTagName, metadata.getVersion(), true, false);
			tagImageCommand.setClient(dockerClient);
			tagImageCommand.execute();
			logger.debug(EELFLoggerDelegate.debugLogger,"Docker image tagging completed successfully");

			logger.debug(EELFLoggerDelegate.debugLogger,"Starting pushing with Imagename:" + imageTagName + " and version : " + metadata.getVersion()
					+ " in nexus");
			PushImageCommand pushImageCmd = new PushImageCommand(imageTagName, metadata.getVersion(), "");
			pushImageCmd.setClient(dockerClient);
			pushImageCmd.execute();

			logger.debug(EELFLoggerDelegate.debugLogger,"Docker image URI : " + dockerImageURI);

			logger.debug(EELFLoggerDelegate.debugLogger,"Docker image pushed in nexus successfully");

			// Microservice/Docker image pushed to nexus -success

			return dockerImageURI;

		} finally {
			try {
				dockerClient.close();
			} catch (IOException e) {
				logger.error(EELFLoggerDelegate.errorLogger,"Fail to close docker client gracefully", e);
			}
		}
	}
	
	public void listFilesAndFilesSubDirectories(File directory) {

		File[] fList = directory.listFiles();

		for (File file : fList) {
			if (file.isFile()) {
				logger.debug(EELFLoggerDelegate.debugLogger,file.getName());
			} else if (file.isDirectory()) {
				listFilesAndFilesSubDirectories(file);
			}
		}
	}
	
	public String getActualModelName(Metadata metadata, String solutionID) {

		return metadata.getModelName() + "_" + solutionID;
	}
}