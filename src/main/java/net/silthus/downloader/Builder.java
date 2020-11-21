package net.silthus.downloader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.snakeyaml.error.YAMLException;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.ObjectArrays;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.output.TeeOutputStream;
import org.apache.commons.lang3.Validate;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.swing.*;
import java.awt.*;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Builder
{

    public static final String LOG_FILE = "ToF-BuildTools.log.txt";
    public static final boolean IS_WINDOWS = System.getProperty( "os.name" ).startsWith( "Windows" );
    public static final File CWD = new File( "." );
    public static final File CONFIG = new File("plugins.yml");
    private static final boolean autocrlf = !"\n".equals( System.getProperty( "line.separator" ) );

    private static File msysDir;
    private static CredentialsProvider credentialsProvider = null;
    private static Map<String, String> headers = new HashMap<>();
    public static String API_KEY = null;

    public static void main(String[] args) throws Exception
    {
        if ( CWD.getAbsolutePath().contains( "'" ) || CWD.getAbsolutePath().contains( "#" ) )
        {
            System.err.println( "Please do not run in a path with special characters!" );
            return;
        }

        if ( false && System.console() == null )
        {
            JFrame jFrame = new JFrame();
            jFrame.setTitle( "Tales of Faldoria - BuildTools" );
            jFrame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
            jFrame.getContentPane().add( new JLabel( "You have to run BuildTools through bash (msysgit). Please read our wiki." ) );
            jFrame.pack();
            jFrame.setVisible( true );

            Desktop.getDesktop().browse( new URI( "https://git.faldoria.de/server/buildtools" ) );
            return;
        }

        // May be null
        String buildVersion = Builder.class.getPackage().getImplementationVersion();
        int buildNumber = -1;
        if ( buildVersion != null )
        {
            String[] split = buildVersion.split( "-" );
            if ( split.length == 4 )
            {
                try
                {
                    buildNumber = Integer.parseInt( split[3] );
                } catch ( NumberFormatException ex )
                {
                }
            }
        }
        System.out.println( "Loading BuildTools version: " + buildVersion + " (#" + buildNumber + ")" );

        OptionParser parser = new OptionParser();
        OptionSpec<Void> disableCertFlag = parser.accepts( "disable-certificate-check" );
        OptionSpec<File> config = parser.acceptsAll(Arrays.asList("c", "config")).withRequiredArg().ofType(File.class).defaultsTo(CONFIG);
        OptionSpec<String> configs = parser.accepts("configs").withOptionalArg().ofType(String.class);
        OptionSpec<File> sourceDir = parser.acceptsAll( Arrays.asList( "d", "dir" ) ).withRequiredArg().ofType( File.class ).defaultsTo( CWD );
        OptionSpec<File> outputDir = parser.acceptsAll( Arrays.asList( "o", "output-dir" ) ).withRequiredArg().ofType( File.class ).defaultsTo( CWD );
        OptionSpec<String> gitUsername = parser.accepts("git-username").withOptionalArg().ofType(String.class);
        OptionSpec<String> gitPassword = parser.accepts("git-password").withOptionalArg().ofType(String.class);
        OptionSpec<String> fileUsername = parser.accepts("file-username").withOptionalArg().ofType(String.class);
        OptionSpec<String> filePassword = parser.accepts("file-password").withOptionalArg().ofType(String.class);
        OptionSpec<String> headers = parser.acceptsAll(Arrays.asList("header", "h")).withOptionalArg().ofType(String.class);

        OptionSet options = parser.parse( args );

        if ( options.has( disableCertFlag ) )
        {
            disableHttpsCertificateCheck();
        }

        if (options.has(gitPassword) && options.has(gitUsername) && options.valueOf(gitUsername) != null && options.valueOf(gitPassword) != null) {
            credentialsProvider = new UsernamePasswordCredentialsProvider(options.valueOf(gitUsername), options.valueOf(gitPassword));
        }

        if (options.has(fileUsername) && options.has(filePassword)) {
            Authenticator.setDefault(new PasswordAuthenticator(options.valueOf(fileUsername), options.valueOf(filePassword)));
        }

        if (options.has(headers)) {
            for (String header : options.valuesOf(headers)) {
                String[] split = header.split(":");
                Builder.headers.put(split[0].trim(), split.length > 1 ? split[1].trim() : "");
            }
        }

        logOutput();

        try
        {
            runProcess( CWD, "sh", "-c", "exit" );
        } catch ( Exception ex )
        {
            if ( IS_WINDOWS )
            {
                String gitVersion = "PortableGit-2.15.0-" + ( System.getProperty( "os.arch" ).endsWith( "64" ) ? "64" : "32" ) + "-bit";
                msysDir = new File( gitVersion, "PortableGit" );

                if ( !msysDir.isDirectory() )
                {
                    System.out.println( "*** Could not find PortableGit installation, downloading. ***" );

                    String gitName = gitVersion + ".7z.exe";
                    File gitInstall = new File( gitVersion, gitName );
                    gitInstall.getParentFile().mkdirs();

                    if ( !gitInstall.exists() )
                    {
                        download( "https://static.spigotmc.org/git/" + gitName, gitInstall, false);
                    }

                    System.out.println( "Extracting downloaded git install" );
                    // yes to all, silent, don't run. Only -y seems to work
                    runProcess( gitInstall.getParentFile(), gitInstall.getAbsolutePath(), "-y", "-gm2", "-nr" );

                    gitInstall.delete();
                }

                System.out.println( "*** Using downloaded git " + msysDir + " ***" );
                System.out.println( "*** Please note that this is a beta feature, so if it does not work please also try a manual install of git from https://git-for-windows.github.io/ ***" );
            } else
            {
                System.err.println(ex.getMessage());
                ex.printStackTrace();
                System.out.println( "You must run this jar through bash (msysgit)" );
                System.exit( 1 );
            }
        }

        runProcess( CWD, "git", "--version" );

        try
        {
            runProcess( CWD, "git", "config", "--global", "--includes", "user.name" );
        } catch ( Exception ex )
        {
            System.out.println( "Git name not set, setting it to default value." );
            runProcess( CWD, "git", "config", "--global", "user.name", "BuildTools" );
        }
        try
        {
            runProcess( CWD, "git", "config", "--global", "--includes", "user.email" );
        } catch ( Exception ex )
        {
            System.out.println( "Git email not set, setting it to default value." );
            runProcess( CWD, "git", "config", "--global", "user.email", "unconfigured@null.spigotmc.org" );
        }

        if (options.has(configs)) {
            String suffix = options.valueOf(configs);
            try (DirectoryStream<Path> paths = Files.newDirectoryStream(options.valueOf(sourceDir).toPath(), "*" + suffix)) {
                paths.forEach(path -> updatePluginConfigs(path.toFile(), options.valueOf(outputDir)));
            }
        } else {
            updatePluginConfigs(options.valueOf(config), options.valueOf(outputDir));
        }
    }

    private static void updatePluginConfigs(File configFile, File dir) {

        try {
            if (configFile.createNewFile()) {
                System.out.println("*** Created new empty " + configFile.getName() + "! Please configure your plugins and configs in this file. ***");
                return;
            }
            if (dir.mkdirs()) {
                System.out.println("Created empty " + dir.getName() + " directory to house our plugins.");
            }

            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

            PluginsConfig pluginsConfig = mapper.readValue(configFile, PluginsConfig.class);
            API_KEY = pluginsConfig.getCurseApiKey();
            pluginsConfig
                    .getPlugins()
                    .forEach((name, plugin) -> {
                        plugin.setName(name);
                        plugin.load();
                        downloadPlugin(plugin, dir);
                    });
        } catch (IOException e) {
            System.err.println("*** Failed to parse plugins.yml: " + e.getMessage() + " ***");
            System.exit(1);
        }
    }

    private static void downloadPlugin(Plugin plugin, File dir) {

        try {
            File file = download(plugin.getUrl(), new File(dir, plugin.getFile()), plugin.isUseToken());
            if (!file.exists()) {
                System.err.println("Failed to download " + plugin.getUrl() + " as " + plugin.getFile());
                return;
            }
            PluginDescriptionFile pluginDescription = getPluginDescription(file);

            File dataFolder = new File(dir, pluginDescription.getName());

            if (!dataFolder.exists()) {
                switch (plugin.getConfigType()) {
                    case GIT:
                        clone(plugin.getConfigUrl(), dataFolder);
                        break;
                    case ZIP:
                        downloadPluginConfigs(plugin, dataFolder);
                        break;
                }
            } else {
                switch (plugin.getConfigType()) {
                    case GIT:
                        try {
                            Git git = Git.open(dataFolder);
                            pull(git, plugin.getBranch());
                        } catch (Exception e) {
                            System.err.println("Failed to update git repo of " + plugin.getName() + ": " + e.getMessage());
                        }
                        break;
                    case ZIP:
                        dataFolder.delete();
                        downloadPluginConfigs(plugin, dataFolder);
                        break;
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to download " + plugin.getFile() + " from " + plugin.getUrl() + ": " + e.getMessage());
        } catch (InvalidDescriptionException e) {
            System.err.println("Invalid plugin: " + e.getMessage());
        } catch (GitAPIException e) {
            e.printStackTrace();
        }
    }

    private static void downloadPluginConfigs(Plugin plugin, File dataFolder) {
        try {
            File configZip = new File("tmp", plugin.getFile() + ".zip");
            File download = download(plugin.getConfigUrl(), configZip, plugin.isUseToken());
            unzip(download, dataFolder);
        } catch (IOException e) {
            System.err.println("Failed to download configs for " + plugin.getName() + " from: " + plugin.getConfigUrl() + ": " + e.getMessage());
        }
    }

    public static final String get(String url) throws IOException
    {
        URLConnection con = new URL( url ).openConnection();
        con.setConnectTimeout( 5000 );
        con.setReadTimeout( 5000 );

        InputStreamReader r = null;
        try
        {
            r = new InputStreamReader( con.getInputStream() );

            return CharStreams.toString( r );
        } finally
        {
            if ( r != null )
            {
                r.close();
            }
        }
    }

    public static void copyJar(String path, final String jarPrefix, File outJar) throws Exception
    {
        File[] files = new File( path ).listFiles( new FilenameFilter()
        {
            @Override
            public boolean accept(File dir, String name)
            {
                return name.startsWith( jarPrefix ) && name.endsWith( ".jar" );
            }
        } );

        if ( !outJar.getParentFile().isDirectory() )
        {
            outJar.getParentFile().mkdirs();
        }

        for ( File file : files )
        {
            System.out.println( "Copying " + file.getName() + " to " + outJar.getAbsolutePath() );
            com.google.common.io.Files.copy( file, outJar );
            System.out.println( "  - Saved as " + outJar );
        }
    }

    public static void pull(Git repo, String ref) throws Exception
    {
        System.out.println( "Pulling updates for " + repo.getRepository().getDirectory() );

        repo.reset().setRef( "origin/master" ).setMode( ResetCommand.ResetType.HARD ).call();
        FetchCommand fetch = repo.fetch();
        if (credentialsProvider != null) fetch.setCredentialsProvider(credentialsProvider);
        fetch.call();

        System.out.println( "Successfully fetched updates!" );

        repo.reset().setRef( ref ).setMode( ResetCommand.ResetType.HARD ).call();
        if ( ref.equals( "master" ) )
        {
            repo.reset().setRef( "origin/master" ).setMode( ResetCommand.ResetType.HARD ).call();
        }
        System.out.println( "Checked out: " + ref );
    }

    public static int runProcess(File workDir, String... command) throws Exception
    {
        if ( msysDir != null )
        {
            if ( "bash".equals( command[0] ) )
            {
                command[0] = "git-bash";
            }
            String[] shim = new String[]
                    {
                            "cmd.exe", "/C"
                    };
            command = ObjectArrays.concat( shim, command, String.class );
        }
        if (!IS_WINDOWS && "sh".equals(command[0])) {
            command[0] = "/bin/sh";
        }
        if (!IS_WINDOWS && "git".equals(command[0])) {
            command[0] = "/usr/bin/git";
        }
        return runProcess0( workDir, command );
    }

    private static int runProcess0(File workDir, String... command) throws Exception
    {
        ProcessBuilder pb = new ProcessBuilder( command );
        pb.directory( workDir );
        pb.environment().put( "JAVA_HOME", System.getProperty( "java.home" ) );
        if ( !pb.environment().containsKey( "MAVEN_OPTS" ) )
        {
            pb.environment().put( "MAVEN_OPTS", "-Xmx1024M" );
        }
        if ( msysDir != null )
        {
            String pathEnv = null;
            for ( String key : pb.environment().keySet() )
            {
                if ( key.equalsIgnoreCase( "path" ) )
                {
                    pathEnv = key;
                }
            }
            if ( pathEnv == null )
            {
                throw new IllegalStateException( "Could not find path variable!" );
            }

            String path = pb.environment().get( pathEnv );
            path += ";" + msysDir.getAbsolutePath();
            path += ";" + new File( msysDir, "bin" ).getAbsolutePath();
            pb.environment().put( pathEnv, path );
        }

        final Process ps = pb.start();

        new Thread( new StreamRedirector( ps.getInputStream(), System.out ) ).start();
        new Thread( new StreamRedirector( ps.getErrorStream(), System.err ) ).start();

        int status = ps.waitFor();

        if ( status != 0 )
        {
            throw new RuntimeException( "Error running command, return status !=0: " + Arrays.toString( command ) );
        }

        return status;
    }

    @RequiredArgsConstructor
    private static class StreamRedirector implements Runnable
    {

        private final InputStream in;
        private final PrintStream out;

        @Override
        public void run()
        {
            BufferedReader br = new BufferedReader( new InputStreamReader( in ) );
            try
            {
                String line;
                while ( ( line = br.readLine() ) != null )
                {
                    out.println( line );
                }
            } catch ( IOException ex )
            {
                throw Throwables.propagate( ex );
            }
        }
    }

    public static void unzip(File zipFile, File targetFolder) throws IOException
    {
        unzip( zipFile, targetFolder, null );
    }

    public static void unzip(File zipFile, File targetFolder, Predicate<String> filter) throws IOException
    {
        targetFolder.mkdir();
        ZipFile zip = new ZipFile( zipFile );

        try
        {
            for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); )
            {
                ZipEntry entry = entries.nextElement();

                if ( filter != null )
                {
                    if ( !filter.apply( entry.getName() ) )
                    {
                        continue;
                    }
                }

                File outFile = new File( targetFolder, entry.getName() );

                if ( entry.isDirectory() )
                {
                    outFile.mkdirs();
                    continue;
                }
                if ( outFile.getParentFile() != null )
                {
                    outFile.getParentFile().mkdirs();
                }

                InputStream is = zip.getInputStream( entry );
                OutputStream os = new FileOutputStream( outFile );
                try
                {
                    ByteStreams.copy( is, os );
                } finally
                {
                    is.close();
                    os.close();
                }

                System.out.println( "Extracted: " + outFile );
            }
        } finally
        {
            zip.close();
        }
    }

    public static void clone(String url, File target) throws GitAPIException, IOException
    {
        System.out.println( "Starting clone of " + url + " to " + target );

        CloneCommand cloneCommand = Git.cloneRepository();
        if (credentialsProvider != null) cloneCommand.setCredentialsProvider(credentialsProvider);
        Git result = cloneCommand.setURI( url ).setDirectory( target ).call();

        try
        {
            StoredConfig config = result.getRepository().getConfig();
            config.setBoolean( "core", null, "autocrlf", autocrlf );
            config.save();

            System.out.println( "Cloned git repository " + url + " to " + target.getAbsolutePath() + ". Current HEAD: " + commitHash( result ) );
        } finally
        {
            result.close();
        }
    }

    public static String commitHash(Git repo) throws GitAPIException
    {
        return Iterables.getOnlyElement( repo.log().setMaxCount( 1 ).call() ).getName();
    }

    private static final int BUFFER_SIZE = 4096;

    public static File download(String url, File target, boolean addHeader) throws IOException
    {
        System.out.println( "Starting download of " + url );

        URL uri = new URL(url);
        HttpURLConnection httpConn = (HttpURLConnection) uri.openConnection();
        if (addHeader) Builder.headers.forEach(httpConn::setRequestProperty);
        if (API_KEY != null && addHeader) httpConn.setRequestProperty("X-Api-Token", API_KEY);

        int responseCode = httpConn.getResponseCode();

        // always check HTTP response code first
        if (responseCode == HttpURLConnection.HTTP_OK) {

            // opens input stream from the HTTP connection
            InputStream inputStream = httpConn.getInputStream();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            int bytesRead = -1;
            byte[] buffer = new byte[BUFFER_SIZE];
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            byte[] bytes = outputStream.toByteArray();

            outputStream.close();
            inputStream.close();

            Path file = target.toPath().toAbsolutePath();
            Files.createDirectories(file.getParent());
            Files.write(file, bytes);
            System.out.println( "Downloaded file: " + file + " with md5: " + Hashing.md5().hashBytes( bytes ).toString() );
        } else {
            System.out.println("No file to download. Server replied HTTP code: " + responseCode);
        }

        httpConn.disconnect();

        return target;
    }

    public static void disableHttpsCertificateCheck()
    {
        // This globally disables certificate checking
        // http://stackoverflow.com/questions/19723415/java-overriding-function-to-disable-ssl-certificate-check
        try
        {
            TrustManager[] trustAllCerts = new TrustManager[]
                    {
                            new X509TrustManager()
                            {
                                @Override
                                public X509Certificate[] getAcceptedIssuers()
                                {
                                    return null;
                                }

                                @Override
                                public void checkClientTrusted(X509Certificate[] certs, String authType)
                                {
                                }

                                @Override
                                public void checkServerTrusted(X509Certificate[] certs, String authType)
                                {
                                }
                            }
                    };

            // Trust SSL certs
            SSLContext sc = SSLContext.getInstance( "SSL" );
            sc.init( null, trustAllCerts, new SecureRandom() );
            HttpsURLConnection.setDefaultSSLSocketFactory( sc.getSocketFactory() );

            // Trust host names
            HostnameVerifier allHostsValid = new HostnameVerifier()
            {
                @Override
                public boolean verify(String hostname, SSLSession session)
                {
                    return true;
                }
            };
            HttpsURLConnection.setDefaultHostnameVerifier( allHostsValid );
        } catch ( NoSuchAlgorithmException ex )
        {
            System.out.println( "Failed to disable https certificate check" );
            ex.printStackTrace( System.err );
        } catch ( KeyManagementException ex )
        {
            System.out.println( "Failed to disable https certificate check" );
            ex.printStackTrace( System.err );
        }
    }

    public static void logOutput()
    {
        try
        {
            final OutputStream logOut = new BufferedOutputStream( new FileOutputStream( LOG_FILE ) );

            Runtime.getRuntime().addShutdownHook( new Thread()
            {
                @Override
                public void run()
                {
                    System.setOut( new PrintStream( new FileOutputStream( FileDescriptor.out ) ) );
                    System.setErr( new PrintStream( new FileOutputStream( FileDescriptor.err ) ) );
                    try
                    {
                        logOut.close();
                    } catch ( IOException ex )
                    {
                        // We're shutting the jvm down anyway.
                    }
                }
            } );

            System.setOut( new PrintStream( new TeeOutputStream( System.out, logOut ) ) );
            System.setErr( new PrintStream( new TeeOutputStream( System.err, logOut ) ) );
        } catch ( FileNotFoundException ex )
        {
            System.err.println( "Failed to create log file: " + LOG_FILE );
        }
    }

    public static PluginDescriptionFile getPluginDescription(File file) throws InvalidDescriptionException {
        Validate.notNull(file, "File cannot be null");

        JarFile jar = null;
        InputStream stream = null;

        try {
            jar = new JarFile(file);
            JarEntry entry = jar.getJarEntry("plugin.yml");

            if (entry == null) {
                throw new InvalidDescriptionException(new FileNotFoundException("Jar does not contain plugin.yml"));
            }

            stream = jar.getInputStream(entry);

            return new PluginDescriptionFile(stream);

        } catch (IOException ex) {
            throw new InvalidDescriptionException(ex);
        } catch (YAMLException ex) {
            throw new InvalidDescriptionException(ex);
        } finally {
            if (jar != null) {
                try {
                    jar.close();
                } catch (IOException e) {
                }
            }
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                }
            }
        }
    }
}