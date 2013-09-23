package com.liferay.maven.plugins.tomcat7;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.tomcat.maven.plugin.tomcat7.run.RunWarMojo;


@Mojo( name = "run-liferay", requiresDependencyResolution = ResolutionScope.RUNTIME )
@Execute( phase = LifecyclePhase.PACKAGE )
public class RunLiferayWarMojo extends RunWarMojo
{

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        getLog().info( "run-liferay executed" );
    }

}
