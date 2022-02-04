package com.softwareverde.monetize.configuration;

import com.softwareverde.bitcoin.address.Address;

public class ServerProperties {
    protected String _rootDirectory;
    protected String _tlsCertificateFile;
    protected String _tlsKeyFile;
    protected Integer _port;
    protected Integer _tlsPort;
    protected Integer _socketPort;
    protected Address _coinbaseAddress;

    public String getRootDirectory() { return _rootDirectory; }
    public String getTlsCertificateFile() { return _tlsCertificateFile; }
    public String getTlsKeyFile() { return _tlsKeyFile; }
    public Integer getPort() { return _port; }
    public Integer getTlsPort() { return _tlsPort; }
    public Integer getSocketPort() { return _socketPort; }
    public Address getCoinbaseAddress() { return _coinbaseAddress; }
}

