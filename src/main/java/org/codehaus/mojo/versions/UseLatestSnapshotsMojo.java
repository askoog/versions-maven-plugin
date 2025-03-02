package org.codehaus.mojo.versions;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import javax.inject.Inject;
import javax.xml.stream.XMLStreamException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.artifact.metadata.ArtifactMetadataRetrievalException;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.Restriction;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.mojo.versions.api.ArtifactVersions;
import org.codehaus.mojo.versions.api.PomHelper;
import org.codehaus.mojo.versions.api.Segment;
import org.codehaus.mojo.versions.ordering.InvalidSegmentException;
import org.codehaus.mojo.versions.ordering.VersionComparator;
import org.codehaus.mojo.versions.rewriting.ModifiedPomXMLEventReader;
import org.codehaus.mojo.versions.utils.DependencyBuilder;

import static java.util.Collections.singletonList;
import static org.codehaus.mojo.versions.api.Segment.MAJOR;

/**
 * Replaces any release versions with the latest snapshot version (if it has been deployed).
 *
 * @author Stephen Connolly
 * @since 1.0-beta-1
 */
@Mojo( name = "use-latest-snapshots", threadSafe = true )
public class UseLatestSnapshotsMojo
    extends AbstractVersionsDependencyUpdaterMojo
{

    /**
     * Whether to allow the major version number to be changed.
     *
     * @since 1.0-beta-1
     */
    @Parameter( property = "allowMajorUpdates", defaultValue = "false" )
    protected boolean allowMajorUpdates;

    /**
     * Whether to allow the minor version number to be changed.
     *
     * @since 1.0-beta-1
     */
    @Parameter( property = "allowMinorUpdates", defaultValue = "false" )
    protected boolean allowMinorUpdates;

    /**
     * Whether to allow the incremental version number to be changed.
     *
     * @since 1.0-beta-1
     */
    @Parameter( property = "allowIncrementalUpdates", defaultValue = "true" )
    protected boolean allowIncrementalUpdates;

    // ------------------------------ FIELDS ------------------------------

    /**
     * Pattern to match a snapshot version.
     */
    private final Pattern matchSnapshotRegex = Pattern.compile( "^(.+)-((SNAPSHOT)|(\\d{8}\\.\\d{6}-\\d+))$" );

    // ------------------------------ METHODS --------------------------

    @Inject
    public UseLatestSnapshotsMojo( RepositorySystem repositorySystem,
                                MavenProjectBuilder projectBuilder,
                                ArtifactMetadataSource artifactMetadataSource,
                                WagonManager wagonManager,
                                ArtifactResolver artifactResolver )
    {
        super( repositorySystem, projectBuilder, artifactMetadataSource, wagonManager, artifactResolver );
    }

    /**
     * @param pom the pom to update.
     * @throws org.apache.maven.plugin.MojoExecutionException when things go wrong
     * @throws org.apache.maven.plugin.MojoFailureException   when things go wrong in a very bad way
     * @throws javax.xml.stream.XMLStreamException            when things go wrong with XML streaming
     * @see AbstractVersionsUpdaterMojo#update(org.codehaus.mojo.versions.rewriting.ModifiedPomXMLEventReader)
     */
    protected void update( ModifiedPomXMLEventReader pom )
        throws MojoExecutionException, MojoFailureException, XMLStreamException
    {
        try
        {
            if ( getProject().getDependencyManagement() != null && isProcessingDependencyManagement() )
            {
                useLatestSnapshots( pom, getProject().getDependencyManagement().getDependencies() );
            }
            if ( getProject().getDependencies() != null && isProcessingDependencies() )
            {
                useLatestSnapshots( pom, getProject().getDependencies() );
            }
            if ( getProject().getParent() != null && isProcessingParent() )
            {
                useLatestSnapshots( pom, singletonList( DependencyBuilder.newBuilder()
                        .withGroupId( getProject().getParent().getGroupId() )
                        .withArtifactId( getProject().getParent().getArtifactId() )
                        .withVersion( getProject().getParent().getVersion() )
                        .withType( "pom" )
                        .build() ) );
            }
        }
        catch ( ArtifactMetadataRetrievalException e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }
    }

    private void useLatestSnapshots( ModifiedPomXMLEventReader pom, Collection<Dependency> dependencies )
        throws XMLStreamException, MojoExecutionException, ArtifactMetadataRetrievalException
    {
        Optional<Segment> unchangedSegment = determineUnchangedSegment( allowMajorUpdates, allowMinorUpdates,
                allowIncrementalUpdates );

        for ( Dependency dep : dependencies )
        {
            if ( isExcludeReactor() && isProducedByReactor( dep ) )
            {
                getLog().info( "Ignoring reactor dependency: " + toString( dep ) );
                continue;
            }

            if ( isHandledByProperty( dep ) )
            {
                getLog().debug( "Ignoring dependency with property as version: " + toString( dep ) );
                continue;
            }

            String version = dep.getVersion();
            Matcher versionMatcher = matchSnapshotRegex.matcher( version );
            if ( !versionMatcher.matches() )
            {
                getLog().debug( "Looking for latest snapshot of " + toString( dep ) );
                Artifact artifact = this.toArtifact( dep );
                if ( !isIncluded( artifact ) )
                {
                    continue;
                }

                ArtifactVersion selectedVersion = new DefaultArtifactVersion( version );

                ArtifactVersions versions = getHelper().lookupArtifactVersions( artifact, false );
                final VersionComparator versionComparator = versions.getVersionComparator();
                final DefaultArtifactVersion lowerBound = new DefaultArtifactVersion( version );
                if ( unchangedSegment.isPresent()
                        && unchangedSegment.get().value() >= versionComparator.getSegmentCount( lowerBound ) )
                {
                    getLog().info( "Ignoring " + toString( dep ) + " as the version number is too short" );
                    continue;
                }
                try
                {
                    ArtifactVersion upperBound = unchangedSegment.isPresent()
                            && unchangedSegment.get().value() >= MAJOR.value()
                            ? versionComparator.incrementSegment( lowerBound, unchangedSegment.get() )
                            : null;
                    getLog().info( "Upper bound: " + ( upperBound == null ? "none" : upperBound.toString() ) );
                    Restriction restriction = new Restriction( lowerBound, false, upperBound, false );
                    ArtifactVersion[] newer = versions.getVersions( restriction, true );
                    getLog().debug( "Candidate versions " + Arrays.asList( newer ) );

                    // TODO consider creating a search + filter in the Details services to get latest snapshot.
                    String latestVersion;
                    ArrayList<ArtifactVersion> snapshotsOnly = new ArrayList<>();

                    for ( ArtifactVersion artifactVersion : newer )
                    {
                        String newVersion = artifactVersion.toString();
                        if ( matchSnapshotRegex.matcher( newVersion ).matches() )
                        {
                            snapshotsOnly.add( artifactVersion );
                        }
                    }
                    ArtifactVersion[] filteredVersions = snapshotsOnly.toArray(
                            new ArtifactVersion[snapshotsOnly.size()] );
                    if ( filteredVersions.length > 0 )
                    {
                        latestVersion = filteredVersions[filteredVersions.length - 1].toString();
                        if ( getProject().getParent() != null )
                        {
                            final Artifact parentArtifact = getProject().getParentArtifact();
                            if ( artifact.getId().equals( parentArtifact.getId() ) && isProcessingParent() )
                            {
                                if ( PomHelper.setProjectParentVersion( pom, latestVersion ) )
                                {
                                    getLog().debug( "Made parent update from " + version + " to " + latestVersion );

                                    this.getChangeRecorder()
                                            .recordUpdate( "useLatestSnapshots", parentArtifact.getGroupId(),
                                                    parentArtifact.getArtifactId(), version, latestVersion );
                                }
                            }
                        }

                        if ( PomHelper.setDependencyVersion( pom, dep.getGroupId(), dep.getArtifactId(), version,
                                latestVersion, getProject().getModel() ) )
                        {
                            getLog().info( "Updated " + toString( dep ) + " to version " + latestVersion );

                            this.getChangeRecorder().recordUpdate( "useLatestSnapshots", dep.getGroupId(),
                                    dep.getArtifactId(), version, latestVersion );
                        }
                    }
                }
                catch ( InvalidSegmentException e )
                {
                    getLog().warn( String.format( "Skipping the processing of %s:%s:%s due to: %s", dep.getGroupId(),
                            dep.getArtifactId(), dep.getVersion(), e.getMessage() ) );
                }
            }
        }
    }

}
