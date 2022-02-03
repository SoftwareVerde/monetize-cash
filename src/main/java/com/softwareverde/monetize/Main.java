package com.softwareverde.monetize;

import com.softwareverde.bitcoin.server.configuration.StratumProperties;
import com.softwareverde.monetize.configuration.Configuration;
import com.softwareverde.monetize.configuration.ServerProperties;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.logging.Logger;
import com.softwareverde.logging.log.AnnotatedLog;

import java.io.File;

public class Main {
    protected static void _exitFailure() {
        System.exit(1);
    }

    protected static void _printError(final String errorMessage) {
        System.err.println(errorMessage);
    }

    protected static void _printUsage() {
        _printError("Usage: java -jar " + System.getProperty("java.class.path") + " <configuration-file>");
    }

    protected static Configuration _loadConfigurationFile(final String configurationFilename) {
        final File configurationFile =  new File(configurationFilename);
        if (! configurationFile.isFile()) {
            _printError("[ERROR: Invalid configuration file.]");
            _exitFailure();
        }

        return new Configuration(configurationFile);
    }

    public static void main(final String[] commandLineArguments) {
        Logger.setLog(AnnotatedLog.getInstance());
        Logger.setLogLevel(LogLevel.ON);
        Logger.setLogLevel("com.softwareverde.util", LogLevel.ERROR);
        Logger.setLogLevel("com.softwareverde.bitcoin.rpc.BitcoinVerdeRpcConnector", LogLevel.WARN);

        if (commandLineArguments.length != 1) {
            _printUsage();
            _exitFailure();
        }

        final String configurationFilename = commandLineArguments[0];

        final Configuration configuration = _loadConfigurationFile(configurationFilename);

        final ServerProperties serverProperties = configuration.getServerProperties();
        final StratumProperties stratumProperties = configuration.getStratumProperties();

        Logger.debug("[Starting Web Server]");
        final WebServer webServer = new WebServer(serverProperties, stratumProperties);
        webServer.start();

        while (true) {
            try {
                Thread.sleep(500);
            }
            catch (final Exception exception) {
                break;
            }
        }

        webServer.stop();
    }
}