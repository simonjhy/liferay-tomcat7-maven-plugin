
package com.liferay.maven.plugins.tomcat7;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.net.URL;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.Server;
import org.apache.catalina.startup.CatalinaProperties;
import org.apache.catalina.startup.Tomcat;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.shared.filtering.MavenFileFilterRequest;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.apache.tomcat.maven.plugin.tomcat7.run.RunWarMojo;
import org.apache.tomcat.maven.plugin.tomcat7.run.Webapp;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;

@Mojo( name = "shutdown-liferay", requiresDependencyResolution = ResolutionScope.RUNTIME )
@Execute( phase = LifecyclePhase.PACKAGE )
public class ShutdownLiferayMojo extends RunWarMojo
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
    @Parameter( property = "maven.tomcat.ajp.port", defaultValue = "8005" )
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

        ClassLoader originalClassLoader = null;

        if ( useSeparateTomcatClassLoader )
        {
            originalClassLoader = Thread.currentThread().getContextClassLoader();
        }

        try
        {
            getLog().info( "Starting liferay" );

            initConfiguration();

            shutdownContainer();
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

    private void shutdownContainer() throws IOException, LifecycleException, MojoExecutionException, ServletException, ProjectBuildingException
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
            Server s = embeddedTomcat.getServer();

            Socket socket = null;
            OutputStream stream = null;
            try {
                socket = new Socket(s.getAddress(), this.ajpPort);
                stream = socket.getOutputStream();
                String shutdown = s.getShutdown();
                for (int i = 0; i < shutdown.length(); i++) {
                    stream.write(shutdown.charAt(i));
                }
                stream.flush();
            } catch (ConnectException ce) {
                getLog().error( "error connection to catalina " +
                                       s.getAddress() + ":" +
                                       String.valueOf(s.getPort()));
                getLog().error("Catalina.stop: ", ce);
                System.exit(1);
            } catch (IOException e) {
                getLog().error("Catalina.stop: ", e);
                System.exit(1);
            } finally {
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (IOException e) {
                        // Ignore
                    }
                }
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        // Ignore
                    }
                }
            }
        }
        finally
        {
            if ( previousCatalinaBase != null )
            {
                System.setProperty( "catalina.base", previousCatalinaBase );
            }
        }
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
}
