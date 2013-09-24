
package com.liferay.maven.plugins.tomcat7;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.servlet.ServletException;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.loader.WebappLoader;
import org.apache.catalina.realm.MemoryRealm;
import org.apache.catalina.startup.CatalinaProperties;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.AccessLogValve;
import org.apache.commons.io.IOUtils;
import org.apache.commons.vfs2.FileChangeEvent;
import org.apache.commons.vfs2.FileListener;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;
import org.apache.commons.vfs2.impl.DefaultFileMonitor;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultProjectBuilderConfiguration;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuilderConfiguration;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.shared.filtering.MavenFileFilterRequest;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.apache.tomcat.maven.common.config.AbstractWebapp;
import org.apache.tomcat.maven.common.run.EmbeddedRegistry;
import org.apache.tomcat.maven.plugin.tomcat7.run.RunWarMojo;
import org.apache.tomcat.maven.plugin.tomcat7.run.Webapp;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;

@Mojo( name = "run-liferay", requiresDependencyResolution = ResolutionScope.RUNTIME )
@Execute( phase = LifecyclePhase.PACKAGE )
public class RunLiferayMojo extends RunWarMojo
{

    /**
     * The archive manager.
     *
     * @since 1.0
     */
    @Component
    private ArchiverManager archiverManager;

    /**
     * @see {@link Webapp}
     * @since 1.0
     */
    @Parameter
    private List<Webapp> webapps;

    /**
     * @see {@link Webapp}
     * @since 1.0
     */
    @Parameter
    private List<Webapp> liferayPlugins;

    /**
     * The directory to create the Tomcat server configuration under.
     */
    @Parameter( defaultValue = "${project.build.directory}/tomcat" )
    private File configurationDir;

    /**
     * The path of the Tomcat logging configuration.
     *
     * @since 1.0
     */
    @Parameter( property = "maven.tomcat.tomcatLogging.file" )
    private File tomcatLoggingFile;

    /**
     * overriding the providing web.xml to run tomcat
     * <b>This override the global Tomcat web.xml located in $CATALINA_HOME/conf/</b>
     *
     * @since 1.0
     */
    @Parameter( property = "maven.tomcat.webXml" )
    private File tomcatWebXml;

    /**
     * The directory contains additional configuration Files that copied in the Tomcat conf Directory.
     *
     * @since 1.0
     */
    @Parameter( property = "maven.tomcat.additionalConfigFilesDir", defaultValue = "${basedir}/src/main/tomcatconf" )
    private File additionalConfigFilesDir;

    /**
     * Set this to true to allow Maven to continue to execute after invoking
     * the run goal.
     *
     * @since 1.0
     */
    @Parameter( property = "maven.tomcat.fork", defaultValue = "false" )
    private boolean fork;

    /**
     * List of System properties to pass to the Tomcat Server.
     *
     * @since 1.0
     */
    @Parameter
    private Map<String, String> systemProperties;

    /**
     * <p>
     * Enables or disables naming support for the embedded Tomcat server.
     * </p>
     * <p>
     * <strong>Note:</strong> This setting is ignored if you provide a <code>server.xml</code> for your
     * Tomcat. Instead please configure naming in the <code>server.xml</code>.
     * </p>
     *
     * @see <a href="http://tomcat.apache.org/tomcat-6.0-doc/api/org/apache/catalina/startup/Embedded.html">org.apache.catalina.startup.Embedded</a>
     * @see <a href="http://tomcat.apache.org/tomcat-7.0-doc/api/org/apache/catalina/startup/Tomcat.html">org.apache.catalina.startup.Tomcat</a>
     * @since 2.0
     */
    @Parameter( property = "maven.tomcat.useNaming", defaultValue = "true" )
    private boolean useNaming;

    /**
     * The protocol to run the Tomcat server on.
     * By default it's HTTP/1.1.
     * See possible values <a href="http://tomcat.apache.org/tomcat-7.0-doc/config/http.html">HTTP Connector</a>
     * protocol attribute
     *
     * @since 2.0
     */
    @Parameter( property = "maven.tomcat.protocol", defaultValue = "HTTP/1.1" )
    private String protocol;

    /**
     * The port to run the Tomcat server on.
     * Will be exposed as System props and session.executionProperties with key tomcat.maven.http.port
     */
    @Parameter( property = "maven.tomcat.port", defaultValue = "8080" )
    private int port;

    /**
     * The AJP port to run the Tomcat server on.
     * By default it's 0 this means won't be started.
     * The ajp connector will be started only for value > 0.
     * Will be exposed as System props and session.executionProperties with key tomcat.maven.ajp.port
     *
     * @since 2.0
     */
    @Parameter( property = "maven.tomcat.ajp.port", defaultValue = "0" )
    private int ajpPort;

    /**
     * The AJP protocol to run the Tomcat server on.
     * By default it's ajp.
     * NOTE The ajp connector will be started only if {@link #ajpPort} > 0.
     * possible values are:
     * <ul>
     * <li>org.apache.coyote.ajp.AjpProtocol - new blocking Java connector that supports an executor</li>
     * <li>org.apache.coyote.ajp.AjpAprProtocol - the APR/native connector.</li>
     * </ul>
     *
     * @since 2.0
     */
    @Parameter( property = "maven.tomcat.ajp.protocol", defaultValue = "org.apache.coyote.ajp.AjpProtocol" )
    private String ajpProtocol;

    /**
     * The https port to run the Tomcat server on.
     * By default it's 0 this means won't be started.
     * The https connector will be started only for value > 0.
     * Will be exposed as System props and session.executionProperties with key tomcat.maven.https.port
     *
     * @since 1.0
     */
    @Parameter( property = "maven.tomcat.httpsPort", defaultValue = "0" )
    private int httpsPort;

    /**
     * The character encoding to use for decoding URIs.
     *
     * @since 1.0
     */
    @Parameter( property = "maven.tomcat.uriEncoding", defaultValue = "ISO-8859-1" )
    private String uriEncoding;

    /**
     * Override the default keystoreFile for the HTTPS connector (if enabled)
     *
     * @since 1.1
     */
    @Parameter
    private String keystoreFile;

    /**
     * Override the default keystorePass for the HTTPS connector (if enabled)
     *
     * @since 1.1
     */
    @Parameter
    private String keystorePass;

    /**
     * Override the type of keystore file to be used for the server certificate. If not specified, the default value is "JKS".
     *
     * @since 2.0
     */
    @Parameter( defaultValue = "JKS" )
    private String keystoreType;

    @Component
    protected MavenProjectBuilder projectBuilder;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        if( skip )
        {
            getLog().info( "Skip execution" );
            return;
        }

        if( !isWar() && getAdditionalWebapps().isEmpty() && getLiferayPlugins().isEmpty() )
        {
            getLog().info( "Skipping non liferay server project" );
            return;
        }

        ClassLoader originalClassLoader = null;

        if ( useSeparateTomcatClassLoader )
        {
            originalClassLoader = Thread.currentThread().getContextClassLoader();
        }

        try
        {
            getLog().info( "Starting liferay" );

            initConfiguration();

            startContainer();

            if( !fork )
            {
                waitIndefinitely();
            }
        }
        catch ( LifecycleException exception )
        {
            throw new MojoExecutionException( messagesProvider.getMessage( "AbstractRunMojo.cannotStart" ), exception );
        }
        catch ( IOException exception )
        {
            throw new MojoExecutionException(
                messagesProvider.getMessage( "AbstractRunMojo.cannotCreateConfiguration" ), exception );
        }
        catch ( ServletException e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }
        catch ( MavenFilteringException e )
        {
            throw new MojoExecutionException( "filtering issue: " + e.getMessage(), e );
        }
        catch( ProjectBuildingException e )
        {
            throw new MojoExecutionException( "project build issue: " + e.getMessage(), e );
        }

        finally
        {
            if ( useSeparateTomcatClassLoader )
            {
                Thread.currentThread().setContextClassLoader( originalClassLoader );
            }
        }
    }

    @SuppressWarnings( "deprecation" )
    private void startContainer() throws IOException, LifecycleException, MojoExecutionException, ServletException, ProjectBuildingException
    {
        String previousCatalinaBase = System.getProperty( "catalina.base" );

        try
        {
         // Set the system properties
            setupSystemProperties();

            System.setProperty( "catalina.base", configurationDir.getAbsolutePath() );

            System.setProperty( "java.util.logging.manager", "org.apache.juli.ClassLoaderLogManager" );
            System.setProperty( "java.util.logging.config.file",
                                new File( configurationDir, "conf/logging.properties" ).toString() );

            // Trigger loading of catalina.properties
            CatalinaProperties.getProperty( "foo" );

            Tomcat embeddedTomcat = new LiferayExtendedTomcat( configurationDir );

            embeddedTomcat.setBaseDir( configurationDir.getAbsolutePath() );
            MemoryRealm memoryRealm = new MemoryRealm();

            embeddedTomcat.setDefaultRealm( memoryRealm );

            if( useNaming )
            {
                embeddedTomcat.enableNaming();
            }

            final File webappsDir = new File( configurationDir, "webapps" );

            embeddedTomcat.getHost().setAppBase( webappsDir.getAbsolutePath() );

            if( hostName != null )
            {
                embeddedTomcat.getHost().setName( hostName );
            }

            if( aliases != null )
            {
                for( String alias : aliases )
                {
                    embeddedTomcat.getHost().addAlias( alias );
                }
            }

            Connector connector = new Connector( protocol );
            connector.setPort( port );

            if ( httpsPort > 0 )
            {
                connector.setRedirectPort( httpsPort );
            }

            connector.setURIEncoding( uriEncoding );

            embeddedTomcat.getService().addConnector( connector );

            embeddedTomcat.setConnector( connector );

            AccessLogValve alv = new AccessLogValve();
            alv.setDirectory( new File( configurationDir, "logs" ).getAbsolutePath() );
            alv.setPattern( "%h %l %u %t \"%r\" %s %b %I %D" );
            embeddedTomcat.getHost().getPipeline().addValve( alv );

            // create https connector
            Connector httpsConnector = null;
            if ( httpsPort > 0 )
            {
                httpsConnector = new Connector( protocol );
                httpsConnector.setPort( httpsPort );
                httpsConnector.setSecure( true );
                httpsConnector.setProperty( "SSLEnabled", "true" );
                // should be default but configure it anyway
                httpsConnector.setProperty( "sslProtocol", "TLS" );

                if ( keystoreFile != null )
                {
                    httpsConnector.setAttribute( "keystoreFile", keystoreFile );
                }

                if ( keystorePass != null )
                {
                    httpsConnector.setAttribute( "keystorePass", keystorePass );
                }

                if ( keystoreType != null )
                {
                    httpsConnector.setAttribute( "keystoreType", keystoreType );
                }

                httpsConnector.setAttribute( "clientAuth", clientAuth );

                embeddedTomcat.getEngine().getService().addConnector( httpsConnector );
            }

            // create ajp connector
            Connector ajpConnector = null;
            if ( ajpPort > 0 )
            {
                ajpConnector = new Connector( ajpProtocol );
                ajpConnector.setPort( ajpPort );
                ajpConnector.setURIEncoding( uriEncoding );
                embeddedTomcat.getEngine().getService().addConnector( ajpConnector );
            }

            if ( ! getAdditionalWebapps().isEmpty() )
            {
                createDependencyContexts( embeddedTomcat );
            }

            if( useSeparateTomcatClassLoader )
            {
                Thread.currentThread().setContextClassLoader( getTomcatClassLoader() );
                embeddedTomcat.getEngine().setParentClassLoader( getTomcatClassLoader() );
            }

            embeddedTomcat.start();

            Properties portProperties = new Properties();
            portProperties.put( "tomcat.maven.http.port", Integer.toString( connector.getLocalPort() ) );

            session.getExecutionProperties().put( "tomcat.maven.http.port", Integer.toString( connector.getLocalPort() ) );
            System.setProperty( "tomcat.maven.http.port", Integer.toString( connector.getLocalPort() ) );

            if( httpsConnector != null )
            {
                session.getExecutionProperties().put(
                    "tomcat.maven.https.port", Integer.toString( httpsConnector.getLocalPort() ) );
                portProperties.put( "tomcat.maven.https.port", Integer.toString( httpsConnector.getLocalPort() ) );
                System.setProperty( "tomcat.maven.https.port", Integer.toString( httpsConnector.getLocalPort() ) );
            }

            if( ajpConnector != null )
            {
                session.getExecutionProperties().put(
                    "tomcat.maven.ajp.port", Integer.toString( ajpConnector.getLocalPort() ) );
                portProperties.put( "tomcat.maven.ajp.port", Integer.toString( ajpConnector.getLocalPort() ) );
                System.setProperty( "tomcat.maven.ajp.port", Integer.toString( ajpConnector.getLocalPort() ) );
            }

            if( propertiesPortFilePath != null )
            {
                File propertiesPortsFile = new File( propertiesPortFilePath );

                if( propertiesPortsFile.exists() )
                {
                    propertiesPortsFile.delete();
                }
                FileOutputStream fileOutputStream = new FileOutputStream( propertiesPortsFile );

                try
                {
                    portProperties.store( fileOutputStream, "Apache Tomcat Maven plugin port used" );
                }
                finally
                {
                    IOUtils.closeQuietly( fileOutputStream );
                }
            }

            EmbeddedRegistry.getInstance().register( embeddedTomcat );

            watchWebappsDirectory( embeddedTomcat, webappsDir );
        }
        finally
        {
            if ( previousCatalinaBase != null )
            {
                System.setProperty( "catalina.base", previousCatalinaBase );
            }
        }
    }

    private void watchWebappsDirectory( final Tomcat container, File webappsDir) throws FileSystemException
    {
        FileSystemManager fsManager = VFS.getManager();
        FileObject listendir = fsManager.resolveFile( webappsDir.getAbsolutePath() );

        DefaultFileMonitor fm = new DefaultFileMonitor(new FileListener()
        {

            public void fileDeleted( FileChangeEvent event ) throws Exception
            {

            }

            public void fileCreated( FileChangeEvent event ) throws Exception
            {
                File dirToDeploy = new File( event.getFile().getURL().getFile() );
                hotDeployDirectory( container, dirToDeploy );
            }

            public void fileChanged( FileChangeEvent event ) throws Exception
            {
                // TODO Auto-generated method stub

            }
        });
        fm.setRecursive(false);
        fm.addFile(listendir);
        fm.start();
    }

    protected void hotDeployDirectory( final Tomcat container, File dirToDeploy ) throws MojoExecutionException, IOException, ServletException
    {
        LiferayExtendedTomcat xTomcat = (LiferayExtendedTomcat) container;

        xTomcat.hotDeployWebapp( "/" + dirToDeploy.getName(), dirToDeploy.getAbsolutePath(), createWebappLoader() );
    }

    private List<Webapp> getAdditionalWebapps()
    {
        if ( webapps == null )
        {
            return Collections.emptyList();
        }
        return webapps;
    }

    private Collection<Context> createDependencyContexts( Tomcat container ) throws MojoExecutionException,
        MalformedURLException, ServletException, IOException
    {
        getLog().info( "Deploying dependency wars" );
        // Let's add other modules
        List<Context> contexts = new ArrayList<Context>();

        ScopeArtifactFilter filter = new ScopeArtifactFilter( "tomcat" );
        @SuppressWarnings( "unchecked" )
        Set<Artifact> artifacts = project.getArtifacts();
        for( Artifact artifact : artifacts )
        {

            // Artifact is not yet registered and it has neither test, nor a
            // provided scope, not is it optional
            if( "war".equals( artifact.getType() ) && !artifact.isOptional() && filter.include( artifact ) )
            {
                addContextFromArtifact( container, contexts, artifact, "/" + artifact.getArtifactId(), null, false );
            }
        }

        for( AbstractWebapp additionalWebapp : getAdditionalWebapps() )
        {
            String contextPath = additionalWebapp.getContextPath();
            if( !contextPath.startsWith( "/" ) )
            {
                contextPath = "/" + contextPath;
            }
            addContextFromArtifact(
                container, contexts, getArtifact( additionalWebapp ), contextPath, additionalWebapp.getContextFile(),
                additionalWebapp.isAsWebapp() );
        }
        return contexts;
    }

    /**
     * Allows the startup of additional webapps in the tomcat container by declaration with scope
     * "tomcat".
     *
     * @param container tomcat
     * @return dependency tomcat contexts of warfiles in scope "tomcat"
     * @throws ProjectBuildingException
     */
    private Collection<Context> createPluginContexts( Tomcat container ) throws MojoExecutionException,
        MalformedURLException, ServletException, IOException, ProjectBuildingException
    {
        getLog().info( "Deploying plugins" );

        // Let's add other modules
        List<Context> contexts = new ArrayList<Context>();

        for( AbstractWebapp additionalWebapp : getLiferayPlugins() )
        {
            String contextPath = additionalWebapp.getContextPath();

            if( !contextPath.startsWith( "/" ) )
            {
                contextPath = "/" + contextPath;
            }

            // check to see if we can deploy context from source
            final MavenProject parent = this.project.getParent();
            final List parentModules = parent.getModules();

            if( parentModules != null && ! parentModules.isEmpty() )
            {
                for( Object module : parentModules )
                {
                    File pom = new File( this.project.getBasedir(), module.toString() );
                    ProjectBuilderConfiguration config = new DefaultProjectBuilderConfiguration();
                    MavenProject moduleProject = projectBuilder.build( pom, config );

                    System.out.println(module);
                }
            }

            addContextFromArtifact(
                container, contexts, getArtifact( additionalWebapp ), contextPath, additionalWebapp.getContextFile(),
                additionalWebapp.isAsWebapp() );
        }

        return contexts;
    }

    private void addContextFromArtifact(
        Tomcat container, List<Context> contexts, Artifact artifact, String contextPath, File contextXml,
        boolean asWebApp ) throws MojoExecutionException, MalformedURLException, ServletException, IOException
    {
        getLog().info( "Deploy warfile: " + String.valueOf( artifact.getFile() ) + " to contextPath: " + contextPath );

        File webapps = new File( configurationDir, "webapps" );
        File artifactWarDir = new File( webapps, artifact.getArtifactId() );

        if( !artifactWarDir.exists() )
        {
            // dont extract if exists
            artifactWarDir.mkdir();

            try
            {
                UnArchiver unArchiver = archiverManager.getUnArchiver( "zip" );
                unArchiver.setSourceFile( artifact.getFile() );
                unArchiver.setDestDirectory( artifactWarDir );

                // Extract the module
                unArchiver.extract();
            }
            catch( NoSuchArchiverException e )
            {
                getLog().error( e );
                return;
            }
            catch( ArchiverException e )
            {
                getLog().error( e );
                return;
            }
        }

        // TODO make that configurable ?
        // WebappLoader webappLoader = new WebappLoader( Thread.currentThread().getContextClassLoader() );
        WebappLoader webappLoader = createWebappLoader();
        Context context = null;
        if( asWebApp )
        {
            context = container.addWebapp( contextPath, artifactWarDir.getAbsolutePath() );
        }
        else
        {
            context = container.addContext( contextPath, artifactWarDir.getAbsolutePath() );
        }
        context.setLoader( webappLoader );

        File contextFile = contextXml != null ? contextXml : getContextFile();
        if( contextFile != null )
        {
            context.setConfigFile( contextFile.toURI().toURL() );
        }

        contexts.add( context );
        // container.getHost().addChild(context);
    }

    /**
     * Set the SystemProperties from the configuration.
     */
    private void setupSystemProperties()
    {
        if( systemProperties != null && !systemProperties.isEmpty() )
        {
            getLog().info( "setting SystemProperties:" );

            for( String key : systemProperties.keySet() )
            {
                String value = systemProperties.get( key );

                if( value != null )
                {
                    getLog().info( " " + key + "=" + value );
                    System.setProperty( key, value );
                }
                else
                {
                    getLog().info( "skip sysProps " + key + " with empty value" );
                }
            }
        }
    }

    /**
     * Causes the current thread to wait indefinitely. This method does not return.
     */

    boolean keepWaiting = true;

    private void waitIndefinitely()
    {
        while(keepWaiting){
            try
            {
                Thread.sleep( 1000 );
            }
            catch( InterruptedException e )
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
//        Object lock = new Object();
//
//        synchronized( lock )
//        {
//            try
//            {
//                lock.wait();
//            }
//            catch( InterruptedException exception )
//            {
//                getLog().warn( messagesProvider.getMessage( "AbstractRunMojo.interrupted" ), exception );
//            }
//        }
    }

    /**
     * Copies the specified class resource to the specified file.
     *
     * @param fromPath the path of the class resource to copy
     * @param toFile   the file to copy to
     * @throws IOException if the file could not be copied
     */
    private void copyFile( String fromPath, File toFile ) throws IOException
    {
        URL fromURL = getClass().getResource( fromPath );

        if( fromURL == null )
        {
            throw new FileNotFoundException( fromPath );
        }

        FileUtils.copyURLToFile( fromURL, toFile );
    }

    private void initConfiguration() throws IOException, MojoExecutionException, MavenFilteringException
    {
        if( configurationDir.exists() )
        {
            getLog().info( messagesProvider.getMessage( "AbstractRunMojo.usingConfiguration", configurationDir ) );
        }
        else
        {
            getLog().info( messagesProvider.getMessage( "AbstractRunMojo.creatingConfiguration", configurationDir ) );

            configurationDir.mkdirs();

            File confDir = new File( configurationDir, "conf" );
            confDir.mkdir();

            if( tomcatLoggingFile != null )
            {
                FileUtils.copyFile( tomcatLoggingFile, new File( confDir, "logging.properties" ) );
            }
            else
            {
                copyFile( "/conf/logging.properties", new File( confDir, "logging.properties" ) );
            }

            copyFile( "/conf/tomcat-users.xml", new File( confDir, "tomcat-users.xml" ) );

            if( tomcatWebXml != null )
            {
                if( !tomcatWebXml.exists() )
                {
                    throw new MojoExecutionException( " tomcatWebXml " + tomcatWebXml.getPath() + " not exists" );
                }
                // MTOMCAT-42 here it's a real file resources not a one coming with the mojo
                // MTOMCAT-128 apply filtering
                MavenFileFilterRequest mavenFileFilterRequest = new MavenFileFilterRequest();
                mavenFileFilterRequest.setFrom( tomcatWebXml );
                mavenFileFilterRequest.setTo( new File( confDir, "web.xml" ) );
                mavenFileFilterRequest.setMavenProject( project );
                mavenFileFilterRequest.setMavenSession( session );
                mavenFileFilterRequest.setFiltering( true );

                mavenFileFilter.copyFile( mavenFileFilterRequest );

            }
            else
            {
                copyFile( "/conf/web.xml", new File( confDir, "web.xml" ) );
            }

            File logDir = new File( configurationDir, "logs" );
            logDir.mkdir();

            File webappsDir = new File( configurationDir, "webapps" );
            webappsDir.mkdir();

            if( additionalConfigFilesDir != null && additionalConfigFilesDir.exists() )
            {
                DirectoryScanner scanner = new DirectoryScanner();
                scanner.addDefaultExcludes();
                scanner.setBasedir( additionalConfigFilesDir.getPath() );
                scanner.scan();

                String[] files = scanner.getIncludedFiles();

                if( files != null && files.length > 0 )
                {
                    getLog().info( "Coping additional tomcat config files" );

                    for( int i = 0; i < files.length; i++ )
                    {
                        File file = new File( additionalConfigFilesDir, files[i] );

                        getLog().info( " copy " + file.getName() );

                        FileUtils.copyFileToDirectory( file, confDir );
                    }
                }
            }
        }
    }

    private List<Webapp> getLiferayPlugins()
    {
        if( liferayPlugins == null )
        {
            return Collections.emptyList();
        }

        return liferayPlugins;
    }
}
