package com.liferay.maven.plugins.tomcat7;

import java.io.File;

import org.apache.catalina.Context;
import org.apache.catalina.Loader;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.ContextConfig;
import org.apache.tomcat.maven.plugin.tomcat7.run.ExtendedTomcat;


public class LiferayExtendedTomcat extends ExtendedTomcat
{

    private File configurationDir;


    public LiferayExtendedTomcat( File configurationDir )
    {
        super( configurationDir );
        this.configurationDir = configurationDir;
    }


    public Context hotDeployWebapp( String name, String path, Loader loader )
    {
        Context ctx = new StandardContext();
        ctx.setLoader( loader );
        ctx.setName( name );
        ctx.setPath( name );
        ctx.setDocBase( path );

        ContextConfig ctxCfg = new ContextConfig();
        ctx.addLifecycleListener( ctxCfg );

        ctxCfg.setDefaultWebXml( new File( configurationDir, "conf/web.xml" ).getAbsolutePath() );

        getHost().addChild( ctx );

        return ctx;
    }
}
