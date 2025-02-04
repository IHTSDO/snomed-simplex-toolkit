package org.snomed.simplex.weblate;

import com.google.common.base.Strings;
import com.google.common.io.Files;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.sshd.JGitKeyCache;
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

@Service
public class WeblateGitClient {

	public static final String ENGLISH_FILENAME = "en.csv";

	private final String gitIdDirectory;
	private final String repoPath;
	private final String remoteRepo;
	private final String repoBranch;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public WeblateGitClient(
			@Value("${weblate.git.id-directory}") String gitIdDirectory,
			@Value("${weblate.git.repo-path}") String repoPath,
			@Value("${weblate.git.repo-ssh-url}") String remoteRepo,
			@Value("${weblate.git.repo-branch}") String repoBranch) {

		this.gitIdDirectory = gitIdDirectory;
		logger.info("Git id directory {}", new File(gitIdDirectory).getAbsolutePath());
		logger.info("Git repo path {}", new File(repoPath).getAbsolutePath());
		this.repoPath = repoPath;
		if (Strings.isNullOrEmpty(remoteRepo)) {
			throw new IllegalArgumentException("weblate.git.repo-ssh-url cannot be null");
		}
		this.remoteRepo = remoteRepo;
		this.repoBranch = repoBranch;
	}

	public void createBlankComponent(String slug) throws ServiceExceptionWithStatusCode {
		try {
			// Clone repository if it doesn't exist
			File repoDir = new File(repoPath);
			if (!repoDir.exists()) {
				logger.info("Cloning git repository");
				try (Git repo = Git.cloneRepository()
						.setURI(remoteRepo)
						.setDirectory(repoDir)
						.setBranch(repoBranch)
						.setTransportConfigCallback(this::configureSSH)
						.call()) {

					logger.info("Repository cloned");
				}
			}

			// Open local repository
			try (Git git = Git.open(repoDir)) {

				git.fetch();
				git.pull();

				File componentDir = new File(repoDir, slug);
				if (componentDir.exists()) {
					throw new ServiceExceptionWithStatusCode("Component with this slug already exists.", HttpStatus.BAD_REQUEST);
				}

				if (!componentDir.mkdir()) {
					throw new ServiceExceptionWithStatusCode("Failed to create new directory for component.", HttpStatus.INTERNAL_SERVER_ERROR);
				}

				Files.copy(new File(repoDir, ENGLISH_FILENAME), new File(componentDir, ENGLISH_FILENAME));

				// Add all files to staging
				git.add().addFilepattern(".").call();

				// Commit changes
				git.commit().setMessage("Simplex creating '%s' component.".formatted(slug)).call();

				// Push to remote repository using SSH
				git.push()
						.setTransportConfigCallback(this::configureSSH)
						.call();
				logger.info("Pushed '{}' to git repository", slug);
			}
		} catch (IOException | GitAPIException e) {
			throw new ServiceExceptionWithStatusCode("Failed to create component in git repository", HttpStatus.INTERNAL_SERVER_ERROR, e);
		}
	}

	// SSH Configuration using Apache MINA SSHD
	private void configureSSH(Transport transport) {
		SshTransport sshTransport = (SshTransport) transport;
		SshdSessionFactoryBuilder sshdSessionFactoryBuilder = new SshdSessionFactoryBuilder();
		File homeDirectory = new File(System.getProperty("user.home"));
		sshdSessionFactoryBuilder.setHomeDirectory(homeDirectory);
		sshdSessionFactoryBuilder.setSshDirectory(new File(gitIdDirectory));
		sshTransport.setSshSessionFactory(sshdSessionFactoryBuilder.build(new JGitKeyCache()));
	}

	public String getRemoteRepo() {
		return remoteRepo;
	}

	public String getRepoBranch() {
		return repoBranch;
	}
}
