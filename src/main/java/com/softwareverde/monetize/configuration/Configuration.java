package com.softwareverde.monetize.configuration;

import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.server.configuration.StratumProperties;
import com.softwareverde.bitcoin.server.configuration.StratumPropertiesLoader;
import com.softwareverde.util.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Configuration {
    protected final Properties _properties;
    protected ServerProperties _serverProperties;
    protected StratumProperties _stratumProperties;

    protected void _loadServerProperties() {
        _serverProperties = new ServerProperties();
        _serverProperties._rootDirectory = _properties.getProperty("server.rootDirectory", "");
        _serverProperties._port = Util.parseInt(_properties.getProperty("server.httpPort", "80"));
        _serverProperties._tlsPort = Util.parseInt(_properties.getProperty("server.tlsPort", "443"));
        _serverProperties._socketPort = Util.parseInt(_properties.getProperty("server.socketPort", "444"));
        _serverProperties._tlsCertificateFile = _properties.getProperty("server.tlsCertificateFile", "");
        _serverProperties._tlsKeyFile = _properties.getProperty("server.tlsKeyFile", "");

        final AddressInflater addressInflater = new AddressInflater();
        final String addressString = _properties.getProperty("server.coinbaseAddress", "");
        _serverProperties._coinbaseAddress = Util.coalesce(addressInflater.fromBase32Check(addressString), addressInflater.fromBase58Check(addressString));
    }

    protected void _loadStratumProperties() {
        _stratumProperties = StratumPropertiesLoader.loadProperties(_properties);
    }

    public Configuration(final File configurationFile) {
        _properties = new Properties();

        try {
            _properties.load(new FileInputStream(configurationFile));
        }
        catch (final IOException exception) { }

        _loadServerProperties();

        _loadStratumProperties();
    }

    public ServerProperties getServerProperties() { return _serverProperties; }
    public StratumProperties getStratumProperties() { return _stratumProperties; }
}
