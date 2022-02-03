package com.softwareverde.monetize.configuration;

import com.softwareverde.database.properties.*;

public class DatabaseProperties implements com.softwareverde.database.properties.DatabaseProperties {
    protected Integer _port;
    protected String _schema;
    protected String _hostname;
    protected DatabaseCredentials _databaseCredentials;

    @Override
    public String getRootPassword() {
        return _databaseCredentials.password;
    }

    @Override
    public String getHostname() {
        return _hostname;
    }

    @Override
    public String getUsername() {
        return _databaseCredentials.username;
    }

    @Override
    public String getPassword() {
        return _databaseCredentials.password;
    }

    @Override
    public String getSchema() {
        return _schema;
    }

    @Override
    public Integer getPort() {
        return _port;
    }

    @Override
    public DatabaseCredentials getRootCredentials() {
        return _databaseCredentials;
    }

    @Override
    public DatabaseCredentials getCredentials() {
        return _databaseCredentials;
    }
}
