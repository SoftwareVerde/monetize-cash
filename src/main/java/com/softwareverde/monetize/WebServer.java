package com.softwareverde.monetize;

import com.softwareverde.bitcoin.CoreInflater;
import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.server.configuration.StratumProperties;
import com.softwareverde.bitcoin.server.module.stratum.BitcoinCoreStratumServer;
import com.softwareverde.bitcoin.server.module.stratum.BitcoinVerdeStratumServer;
import com.softwareverde.concurrent.threadpool.CachedThreadPool;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.http.server.HttpServer;
import com.softwareverde.http.server.endpoint.Endpoint;
import com.softwareverde.http.server.servlet.DirectoryServlet;
import com.softwareverde.http.server.servlet.GetWorkApiServlet;
import com.softwareverde.http.server.servlet.MonetizedServlet;
import com.softwareverde.http.server.servlet.Servlet;
import com.softwareverde.http.server.servlet.SubscribeApiServlet;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.logging.Logger;
import com.softwareverde.monetize.configuration.ServerProperties;
import com.softwareverde.util.Util;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

public class WebServer {
    protected final ServerProperties _serverProperties;

    protected final CachedThreadPool _threadPool;
    protected final HttpServer _apiServer = new HttpServer();
    protected final BitcoinCoreStratumServer _stratumServer;
    protected final Address _coinbaseAddress;

    protected <T extends Servlet> void _assignEndpoint(final String path, final T servlet) {
        final Endpoint endpoint = new Endpoint(servlet);
        endpoint.setPath(path);
        endpoint.setStrictPathEnabled(true);
        _apiServer.addEndpoint(endpoint);
    }

    protected Boolean _isSslEnabled() {
        if (_serverProperties.getTlsPort() < 1) {
            return false;
        }

        if (Util.isBlank(_serverProperties.getTlsCertificateFile())) {
            return false;
        }

        if (Util.isBlank(_serverProperties.getTlsKeyFile())) {
            return false;
        }

        return true;
    }

    public WebServer(final ServerProperties serverProperties, final StratumProperties stratumProperties) {
        _serverProperties = serverProperties;

        _threadPool = new CachedThreadPool(12, 30000L);
        _stratumServer = new BitcoinVerdeStratumServer(stratumProperties, _threadPool, new CoreInflater(), null);
        _coinbaseAddress = (new AddressInflater()).fromBase32Check("qqverdefl9xtryyx8y52m6va5j8s2s4eq59fjdn97e");
    }

    public void start() {
        _threadPool.start();
        _apiServer.setPort(_serverProperties.getPort());
        _stratumServer.setCoinbaseAddress(_coinbaseAddress);

        final boolean sslIsEnabled = _isSslEnabled();

        { // Configure SSL/TLS...
            if (sslIsEnabled) {
                _apiServer.setTlsPort(_serverProperties.getTlsPort());
                _apiServer.setCertificate(_serverProperties.getTlsCertificateFile(), _serverProperties.getTlsKeyFile());
            }

            _apiServer.enableEncryption(sslIsEnabled);
            _apiServer.redirectToTls(false);
        }

        { // Static Content
            final File servedDirectory = new File(_serverProperties.getRootDirectory() +"/");
            final MonetizedServlet indexServlet = new MonetizedServlet(servedDirectory, _stratumServer);

            indexServlet.setShouldServeDirectories(true);
            indexServlet.setIndexFile("index.html");
            indexServlet.setErrorHandler(new DirectoryServlet.ErrorHandler() {
                @Override
                public Response onFileNotFound(final Request request) {
                    indexServlet.reIndexFiles();

                    final Response response = new Response();
                    response.setCode(Response.Codes.NOT_FOUND);
                    response.setContent("Not found.");
                    return response;
                }
            });

            indexServlet.addFreeEndpoint("/", true);
            indexServlet.addFreeEndpoint("/index.html", true);
            indexServlet.addFreeEndpoint("/js/http.js", true);
            indexServlet.addFreeEndpoint("/js/libauth.js", true);
            indexServlet.addFreeEndpoint("/js/monetize.js", true);

            final Endpoint endpoint = new Endpoint(indexServlet);
            endpoint.setPath("/");
            endpoint.setStrictPathEnabled(false);
            _apiServer.addEndpoint(endpoint);
        }

        final ConcurrentHashMap<ByteArray, Long> subscriptionMinerIds = new ConcurrentHashMap<>();
        {
            final Endpoint endpoint = new Endpoint(new SubscribeApiServlet(_stratumServer, subscriptionMinerIds));
            endpoint.setPath("/api/v1/monetize/subscribe");
            endpoint.setStrictPathEnabled(true);
            _apiServer.addEndpoint(endpoint);
        }

        {
            final Endpoint endpoint = new Endpoint(new GetWorkApiServlet(_stratumServer, subscriptionMinerIds));
            endpoint.setPath("/api/v1/monetize/get-work");
            endpoint.setStrictPathEnabled(true);
            _apiServer.addEndpoint(endpoint);
        }

        _stratumServer.start();
        _apiServer.start();

        final Integer httpPort = _serverProperties.getPort();
        final Integer tlsPort = _serverProperties.getTlsPort();
        Logger.debug("[Server Listening on " + httpPort + (sslIsEnabled ? (" / " + tlsPort) : "") + "]");
    }

    public void stop() {
        _apiServer.stop();
        _stratumServer.stop();
        _threadPool.stop();
    }
}
