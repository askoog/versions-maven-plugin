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

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.doxia.sink.SinkEventAttributes;
import org.apache.maven.doxia.sink.impl.SinkEventAttributeSet;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.codehaus.mojo.versions.api.ArtifactVersions;
import org.codehaus.mojo.versions.utils.PluginComparator;
import org.codehaus.plexus.i18n.I18N;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.codehaus.mojo.versions.api.Segment.INCREMENTAL;
import static org.codehaus.mojo.versions.api.Segment.MAJOR;
import static org.codehaus.mojo.versions.api.Segment.MINOR;
import static org.codehaus.mojo.versions.api.Segment.SUBINCREMENTAL;

/**
 * @since 1.0-beta-1
 */
public class PluginUpdatesRenderer
    extends AbstractVersionsReportRenderer
{
    private final Map<Plugin, PluginUpdatesDetails> pluginUpdates;

    private final Map<Plugin, PluginUpdatesDetails> pluginManagementUpdates;

    public PluginUpdatesRenderer( Sink sink, I18N i18n, String bundleName, Locale locale,
                                  Map<Plugin, PluginUpdatesDetails> pluginUpdates,
                                  Map<Plugin, PluginUpdatesDetails> pluginManagementUpdates )
    {
        super( sink, bundleName, i18n, locale );
        this.pluginUpdates = pluginUpdates;
        this.pluginManagementUpdates = pluginManagementUpdates;
    }

    protected void renderBody()
    {
        Map<Plugin, PluginUpdatesDetails> allUpdates = new TreeMap<>( new PluginComparator() );
        allUpdates.putAll( pluginManagementUpdates );
        allUpdates.putAll( pluginUpdates );

        sink.section1();
        sink.sectionTitle1();
        sink.text( getText( "report.overview.title" ) );
        sink.sectionTitle1_();
        sink.paragraph();
        sink.text( getText( "report.overview.text" ) );
        sink.paragraph_();

        renderSummaryTotalsTable( allUpdates );

        renderSummaryTable( "report.overview.pluginManagement", pluginManagementUpdates,
                            "report.overview.noPluginManagement" );

        renderSummaryTable( "report.overview.plugin", pluginUpdates, "report.overview.noPlugin" );

        sink.section1_();

        sink.section1();
        sink.sectionTitle1();
        sink.text( getText( "report.detail.title" ) );
        sink.sectionTitle1_();
        sink.paragraph();
        sink.text( getText( "report.detail.text" ) );
        sink.paragraph_();

        for ( final Map.Entry<Plugin, PluginUpdatesDetails> entry : allUpdates.entrySet() )
        {
            renderPluginDetail( entry.getKey(), entry.getValue() );
        }
        sink.section1_();

    }

    private void renderSummaryTotalsTable( Map<Plugin, PluginUpdatesDetails> allUpdates )
    {
        int numInc = 0;
        int numMin = 0;
        int numMaj = 0;
        int numAny = 0;
        int numCur = 0;
        int numDep = 0;
        for ( PluginUpdatesDetails pluginDetails : allUpdates.values() )
        {
            ArtifactVersions details = pluginDetails.getArtifactVersions();
            if ( details.getOldestUpdate( of( SUBINCREMENTAL ) ) != null )
            {
                numAny++;
            }
            else if ( details.getOldestUpdate( of( INCREMENTAL ) ) != null )
            {
                numInc++;
            }
            else if ( details.getOldestUpdate( of( MINOR ) ) != null )
            {
                numMin++;
            }
            else if ( details.getOldestUpdate( of( MAJOR ) ) != null )
            {
                numMaj++;
            }
            else
            {
                numCur++;
            }
            if ( pluginDetails.isDependencyUpdateAvailable() )
            {
                numDep++;
            }
        }
        sink.table();
        sink.tableRow();
        sink.tableCell();
        renderSuccessIcon();
        sink.tableCell_();
        sink.tableCell();
        sink.text( getText( "report.overview.numUpToDate" ) );
        sink.tableCell_();
        sink.tableCell();
        sink.text( Integer.toString( numCur ) );
        sink.tableCell_();
        sink.tableRow_();
        sink.tableRow();
        sink.tableCell();
        renderWarningIcon();
        sink.tableCell_();
        sink.tableCell();
        sink.text( getText( "report.overview.numNewerVersionAvailable" ) );
        sink.tableCell_();
        sink.tableCell();
        sink.text( Integer.toString( numAny ) );
        sink.tableCell_();
        sink.tableRow_();
        sink.tableRow();
        sink.tableCell();
        renderWarningIcon();
        sink.tableCell_();
        sink.tableCell();
        sink.text( getText( "report.overview.numNewerIncrementalAvailable" ) );
        sink.tableCell_();
        sink.tableCell();
        sink.text( Integer.toString( numInc ) );
        sink.tableCell_();
        sink.tableRow_();
        sink.tableRow();
        sink.tableCell();
        renderWarningIcon();
        sink.tableCell_();
        sink.tableCell();
        sink.text( getText( "report.overview.numNewerMinorAvailable" ) );
        sink.tableCell_();
        sink.tableCell();
        sink.text( Integer.toString( numMin ) );
        sink.tableCell_();
        sink.tableRow_();
        sink.tableRow();
        sink.tableCell();
        renderWarningIcon();
        sink.tableCell_();
        sink.tableCell();
        sink.text( getText( "report.overview.numNewerMajorAvailable" ) );
        sink.tableCell_();
        sink.tableCell();
        sink.text( Integer.toString( numMaj ) );
        sink.tableCell_();
        sink.tableRow_();
        sink.tableRow();
        sink.tableCell();
        renderWarningIcon();
        sink.tableCell_();
        sink.tableCell();
        sink.text( getText( "report.overview.numNewerDependenciesAvailable" ) );
        sink.tableCell_();
        sink.tableCell();
        sink.text( Integer.toString( numDep ) );
        sink.tableCell_();
        sink.tableRow_();
        sink.table_();
    }

    private void renderSummaryTable( String titleKey, Map<Plugin, PluginUpdatesDetails> contents, String emptyKey )
    {
        sink.section2();
        sink.sectionTitle2();
        sink.text( getText( titleKey ) );
        sink.sectionTitle2_();

        if ( contents.isEmpty() )
        {
            sink.paragraph();
            sink.text( getText( emptyKey ) );
            sink.paragraph_();
        }
        else
        {
            sink.table();
            renderSummaryTableHeader();
            for ( final Map.Entry<Plugin, PluginUpdatesDetails> entry : contents.entrySet() )
            {
                renderPluginSummary( entry.getKey(), entry.getValue() );
            }
            renderSummaryTableHeader();
            sink.table_();
        }
        sink.section2_();
    }

    private void renderSummaryTableHeader()
    {
        sink.tableRow();
        sink.tableHeaderCell();
        sink.text( getText( "report.status" ) );
        sink.tableHeaderCell_();
        sink.tableHeaderCell();
        sink.text( getText( "report.groupId" ) );
        sink.tableHeaderCell_();
        sink.tableHeaderCell();
        sink.text( getText( "report.artifactId" ) );
        sink.tableHeaderCell_();
        sink.tableHeaderCell();
        sink.text( getText( "report.currentVersion" ) );
        sink.tableHeaderCell_();
        sink.tableHeaderCell();
        sink.text( getText( "report.latestSubIncremental" ) );
        sink.tableHeaderCell_();
        sink.tableHeaderCell();
        sink.text( getText( "report.latestIncremental" ) );
        sink.tableHeaderCell_();
        sink.tableHeaderCell();
        sink.text( getText( "report.latestMinor" ) );
        sink.tableHeaderCell_();
        sink.tableHeaderCell();
        sink.text( getText( "report.latestMajor" ) );
        sink.tableHeaderCell_();
        sink.tableHeaderCell();
        sink.text( getText( "report.dependencyStatus" ) );
        sink.tableHeaderCell_();
        sink.tableRow_();
    }

    private void renderPluginSummary( Plugin plugin, PluginUpdatesDetails details )
    {
        sink.tableRow();
        sink.tableCell();
        if ( !details.isUpdateAvailable() )
        {
            renderSuccessIcon();
        }
        else
        {
            renderWarningIcon();
        }
        sink.tableCell_();
        sink.tableCell();
        sink.text( plugin.getGroupId() );
        sink.tableCell_();
        sink.tableCell();
        sink.text( plugin.getArtifactId() );
        sink.tableCell_();
        sink.tableCell();
        if ( !details.isArtifactUpdateAvailable() )
        {
            safeBold();
        }
        sink.text( plugin.getVersion() );
        if ( !details.isArtifactUpdateAvailable() )
        {
            safeBold_();
        }
        sink.tableCell_();

        sink.tableCell();
        if ( details.getArtifactVersions().getNewestUpdate( of( SUBINCREMENTAL ) ) != null )
        {
            safeBold();
            sink.text( details.getArtifactVersions().getNewestUpdate( of( SUBINCREMENTAL ) ).toString() );
            safeBold_();
        }
        sink.tableCell_();

        sink.tableCell();
        if ( details.getArtifactVersions().getNewestUpdate( of( INCREMENTAL ) ) != null )
        {
            safeBold();
            sink.text( details.getArtifactVersions().getNewestUpdate( of( INCREMENTAL ) ).toString() );
            safeBold_();
        }
        sink.tableCell_();

        sink.tableCell();
        if ( details.getArtifactVersions().getNewestUpdate( of( MINOR ) ) != null )
        {
            safeBold();
            sink.text( details.getArtifactVersions().getNewestUpdate( of( MINOR ) ).toString() );
            safeBold_();
        }
        sink.tableCell_();

        sink.tableCell();
        if ( details.getArtifactVersions().getNewestUpdate( of( MAJOR ) ) != null )
        {
            safeBold();
            sink.text( details.getArtifactVersions().getNewestUpdate( of( MAJOR ) ).toString() );
            safeBold_();
        }
        sink.tableCell_();

        sink.tableCell();
        if ( details.isDependencyUpdateAvailable() )
        {
            renderWarningIcon();
        }
        else
        {
            renderSuccessIcon();
        }
        sink.tableCell_();

        sink.tableRow_();
    }

    @SuppressWarnings( "checkstyle:MethodLength" )
    private void renderPluginDetail( Plugin plugin, PluginUpdatesDetails details )
    {
        final SinkEventAttributes headerAttributes = new SinkEventAttributeSet();
        headerAttributes.addAttribute( SinkEventAttributes.WIDTH, "20%" );
        final SinkEventAttributes cellAttributes = new SinkEventAttributeSet();
        headerAttributes.addAttribute( SinkEventAttributes.WIDTH, "80%" );
        sink.section2();
        sink.sectionTitle2();
        sink.text( MessageFormat.format( getText( "report.plugin" ),
                ArtifactUtils.versionlessKey( plugin.getGroupId(), plugin.getArtifactId() ) ) );
        sink.sectionTitle2_();
        sink.table();
        sink.tableRows( new int[] {Sink.JUSTIFY_RIGHT, Sink.JUSTIFY_LEFT}, false );
        sink.tableRow();
        sink.tableHeaderCell( headerAttributes );
        sink.text( getText( "report.status" ) );
        sink.tableHeaderCell_();
        sink.tableCell( cellAttributes );
        ArtifactVersion[] versions = details.getArtifactVersions().getAllUpdates( empty() );
        if ( details.getArtifactVersions().getOldestUpdate( of( SUBINCREMENTAL ) ) != null )
        {
            renderWarningIcon();
            sink.nonBreakingSpace();
            sink.text( getText( "report.otherUpdatesAvailable" ) );
        }
        else if ( details.getArtifactVersions().getOldestUpdate( of( INCREMENTAL ) ) != null )
        {
            renderWarningIcon();
            sink.nonBreakingSpace();
            sink.text( getText( "report.incrementalUpdatesAvailable" ) );
        }
        else if ( details.getArtifactVersions().getOldestUpdate( of( MINOR ) ) != null )
        {
            renderWarningIcon();
            sink.nonBreakingSpace();
            sink.text( getText( "report.minorUpdatesAvailable" ) );
        }
        else if ( details.getArtifactVersions().getOldestUpdate( of( MAJOR ) ) != null )
        {
            renderWarningIcon();
            sink.nonBreakingSpace();
            sink.text( getText( "report.majorUpdatesAvailable" ) );
        }
        else
        {
            renderSuccessIcon();
            sink.nonBreakingSpace();
            sink.text( getText( "report.noUpdatesAvailable" ) );
        }
        sink.tableCell_();
        sink.tableRow_();
        sink.tableRow();
        sink.tableHeaderCell( headerAttributes );
        sink.text( getText( "report.groupId" ) );
        sink.tableHeaderCell_();
        sink.tableCell( cellAttributes );
        sink.text( plugin.getGroupId() );
        sink.tableCell_();
        sink.tableRow_();
        sink.tableRow();
        sink.tableHeaderCell( headerAttributes );
        sink.text( getText( "report.artifactId" ) );
        sink.tableHeaderCell_();
        sink.tableCell( cellAttributes );
        sink.text( plugin.getArtifactId() );
        sink.tableCell_();
        sink.tableRow_();
        sink.tableRow();
        sink.tableHeaderCell( headerAttributes );
        sink.text( getText( "report.currentVersion" ) );
        sink.tableHeaderCell_();
        sink.tableCell( cellAttributes );
        sink.text( plugin.getVersion() );
        sink.tableCell_();
        sink.tableRow_();
        if ( versions.length > 0 )
        {
            sink.tableRow();
            sink.tableHeaderCell( headerAttributes );
            sink.text( getText( "report.updateVersions" ) );
            sink.tableHeaderCell_();
            sink.tableCell( cellAttributes );
            for ( int i = 0; i < versions.length; i++ )
            {
                if ( i > 0 )
                {
                    sink.lineBreak();
                }
                String label = getLabel( versions[i], details.getArtifactVersions() );
                if ( label != null )
                {
                    safeBold();
                }
                sink.text( versions[i].toString() );
                if ( label != null )
                {
                    safeBold_();
                    sink.nonBreakingSpace();
                    safeItalic();
                    sink.text( label );
                    safeItalic_();
                }
            }
            sink.tableCell_();
            sink.tableRow_();
        }
        sink.tableRows_();
        sink.table_();

        if ( !details.getDependencyVersions().isEmpty() )
        {
            sink.section3();
            sink.sectionTitle3();
            sink.text( MessageFormat.format( getText( "report.pluginDependencies" ),
                    ArtifactUtils.versionlessKey( plugin.getGroupId(), plugin.getArtifactId() ) ) );
            sink.sectionTitle3_();

            renderDependencySummaryTable( details.getDependencyVersions(), false, true, true );

            sink.section3_();

            details.getDependencyVersions()
                    .forEach( this::renderDependencyDetail );
        }
        sink.section2_();
    }

    private void renderDependencyDetail( Dependency dependency, ArtifactVersions details )
    {
        sink.section3();
        sink.sectionTitle3();
        sink.text( MessageFormat.format( getText( "report.pluginDependency" ),
                ArtifactUtils.versionlessKey( dependency.getGroupId(), dependency.getArtifactId() ) ) );
        sink.sectionTitle3_();
        renderDependencyDetailTable( dependency, details, false, true, true );
        sink.section3_();
    }

}
