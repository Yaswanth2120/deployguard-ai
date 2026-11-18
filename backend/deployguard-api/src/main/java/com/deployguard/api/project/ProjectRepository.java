package com.deployguard.api.project;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    @Query("select p from Project p where lower(p.githubRepoUrl) in :urls")
    List<Project> findByGithubRepoUrlCandidates(@Param("urls") Collection<String> lowercaseUrls);
}
